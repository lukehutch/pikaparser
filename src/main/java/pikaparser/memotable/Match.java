package pikaparser.memotable;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import pikaparser.clause.Clause;
import pikaparser.clause.nonterminal.First;
import pikaparser.clause.nonterminal.OneOrMore;
import pikaparser.clause.nonterminal.Seq;
import pikaparser.grammar.MetaGrammar;
import pikaparser.parser.ASTNode;

/** A complete match of a {@link Clause} at a given start position. */
public class Match {
    /** The {@link MemoKey}. */
    public final MemoKey memoKey;

    /** The length of the match. */
    public final int len;

    /** The subclause matches. */
    private final Match[] subClauseMatches;

    /**
     * The subclause index of the first matching subclause (will be 0 unless {@link #labeledClause} is a
     * {@link First}, and the matching clause was not the first subclause).
     */
    private int firstMatchingSubClauseIdx;

    /** There are no subclause matches for terminals. */
    public static final Match[] NO_SUBCLAUSE_MATCHES = new Match[0];

    public Match(MemoKey memoKey, int firstMatchingSubClauseIdx, int len, Match[] subClauseMatches) {
        this.memoKey = memoKey;
        this.firstMatchingSubClauseIdx = firstMatchingSubClauseIdx;
        this.len = len;
        this.subClauseMatches = subClauseMatches;
    }

    /**
     * Get subclause matches. Automatically flattens the right-recursive structure of {@link OneOrMore} and
     * {@link Seq} nodes, collecting the subclause matches into a single array of (AST node label, match) tuples.
     */
    public List<Entry<String, Match>> getSubClauseMatches() {
        if (subClauseMatches.length == 0) {
            // For terminals, or empty matches triggered by clauses that can match zero characters --
            // see MemoTable.lookUpBestMatch
            return Collections.emptyList();
        }
        if (memoKey.clause instanceof OneOrMore) {
            // Flatten right-recursive structure of OneOrMore parse subtree
            var subClauseMatchesToUse = new ArrayList<Entry<String, Match>>();
            for (Match curr = this; curr.subClauseMatches.length > 0; curr = curr.subClauseMatches[1]) {
                subClauseMatchesToUse.add(new SimpleEntry<>(curr.memoKey.clause.labeledSubClauses[0].astNodeLabel,
                        curr.subClauseMatches[0]));
                if (curr.subClauseMatches.length == 1) {
                    // Reached end of right-recursive matches
                    break;
                }
            }
            return subClauseMatchesToUse;
        } else if (memoKey.clause instanceof First) {
            // For First, pair the appropriate AST node label with the one and only subclause match
            return Arrays.asList(new SimpleEntry<>(
                    memoKey.clause.labeledSubClauses[firstMatchingSubClauseIdx].astNodeLabel, subClauseMatches[0]));
        } else {
            // For other clause types, return labeled subclause matches
            var numSubClauses = memoKey.clause.labeledSubClauses.length;
            var subClauseMatchesToUse = new ArrayList<Entry<String, Match>>(numSubClauses);
            for (int i = 0; i < numSubClauses; i++) {
                subClauseMatchesToUse.add(
                        new SimpleEntry<>(memoKey.clause.labeledSubClauses[i].astNodeLabel, subClauseMatches[i]));
            }
            return subClauseMatchesToUse;
        }
    }

    /**
     * Get subclause matches, without flattening the right-recursive structure of {@link OneOrMore} nodes.
     */
    public Match[] getSubClauseMatchesRaw() {
        return subClauseMatches;
    }

    /**
     * Compare this {@link Match} to another {@link Match} of the same {@link Clause} type and start position.
     * 
     * @return true if this {@link Match} is a better match than the other {@link Match}.
     */
    public boolean isBetterThan(Match other) {
        if (other == this) {
            return false;
        }

        // An earlier subclause match in a First clause is better than a later subclause match
        // A longer match (i.e. a match that spans more characters in the input) is better than a shorter match
        return (memoKey.clause instanceof First // 
                && this.firstMatchingSubClauseIdx < other.firstMatchingSubClauseIdx) //
                || this.len > other.len;
    }

    public String getText(String input) {
        return input.substring(memoKey.startPos, memoKey.startPos + len);
    }

    private void toAST(ASTNode parent, String input) {
        // Recurse to descendants
        var subClauseMatchesToUse = getSubClauseMatches();
        for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatchesToUse.size(); subClauseMatchIdx++) {
            var subClauseMatchEnt = subClauseMatchesToUse.get(subClauseMatchIdx);
            var subClauseASTNodeLabel = subClauseMatchEnt.getKey();
            var subClauseMatch = subClauseMatchEnt.getValue();
            ASTNode parentOfSubclause = parent;
            if (subClauseASTNodeLabel != null) {
                // Create an AST node for any labeled sub-clauses
                var newASTNode = new ASTNode(subClauseASTNodeLabel, subClauseMatch.memoKey.clause,
                        subClauseMatch.memoKey.startPos, subClauseMatch.len, input);
                parent.addChild(newASTNode);
                parentOfSubclause = newASTNode;
            }
            subClauseMatch.toAST(parentOfSubclause, input);
        }
    }

    public ASTNode toAST(String rootNodeLabel, String input) {
        var root = new ASTNode(rootNodeLabel, memoKey.clause, memoKey.startPos, len, input);
        toAST(root, input);
        return root;
    }

    public void renderTreeView(String input, String indentStr, String astNodeLabel, boolean isLastChild,
            StringBuilder buf) {
        int inpLen = 80;
        String inp = input.substring(memoKey.startPos,
                Math.min(input.length(), memoKey.startPos + Math.min(len, inpLen)));
        if (inp.length() == inpLen) {
            inp += "...";
        }
        inp = inp.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");

        // Uncomment for double-spaced rows
        // buf.append(indentStr + "│\n");

        var ruleNames = memoKey.clause.getRuleNames();
        var toStr = memoKey.clause.toString();
        var astNodeLabelNeedsParens = MetaGrammar.addParensAroundASTNodeLabel(memoKey.clause);
        buf.append(indentStr + (isLastChild ? "└─" : "├─") //
                + (ruleNames.isEmpty() ? "" : ruleNames + " <- ") //
                + (astNodeLabel == null ? "" : (astNodeLabel + ":" + (astNodeLabelNeedsParens ? "(" : ""))) //
                + toStr //
                + (astNodeLabel == null || !astNodeLabelNeedsParens ? "" : ")") //
                + " : " + memoKey.startPos + "+" + len //
                + " : \"" + inp + "\"\n");

        // Recurse to descendants
        var subClauseMatchesToUse = getSubClauseMatches();
        for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatchesToUse.size(); subClauseMatchIdx++) {
            var subClauseMatchEnt = subClauseMatchesToUse.get(subClauseMatchIdx);
            var subClauseASTNodeLabel = subClauseMatchEnt.getKey();
            var subClauseMatch = subClauseMatchEnt.getValue();
            subClauseMatch.renderTreeView(input, indentStr + (isLastChild ? "  " : "│ "), subClauseASTNodeLabel,
                    subClauseMatchIdx == subClauseMatchesToUse.size() - 1, buf);
        }
    }

    public void printTreeView(String input) {
        var buf = new StringBuilder();
        renderTreeView(input, "", null, true, buf);
        System.out.println(buf.toString());
    }

    public String toStringWithRuleNames() {
        StringBuilder buf = new StringBuilder();
        buf.append(memoKey.toStringWithRuleNames() + "+" + len + " => [ ");
        var subClauseMatchesToUse = getSubClauseMatches();
        for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatchesToUse.size(); subClauseMatchIdx++) {
            var subClauseMatchEnt = subClauseMatchesToUse.get(subClauseMatchIdx);
            var subClauseMatch = subClauseMatchEnt.getValue();
            if (subClauseMatchIdx > 0) {
                buf.append(" ; ");
            }
            buf.append(subClauseMatch.toStringWithRuleNames());
        }
        buf.append(" ]");
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(memoKey + "+" + len);
        //        buf.append(" => [ ");
        //        var subClauseMatchesToUse = getSubClauseMatches();
        //        for (int i = 0; i < subClauseMatchesToUse.length; i++) {
        //            var s = subClauseMatchesToUse[i];
        //            if (i > 0) {
        //                buf.append(" ; ");
        //            }
        //            buf.append(s.toString());
        //        }
        //        buf.append(" ]");
        return buf.toString();
    }
}
