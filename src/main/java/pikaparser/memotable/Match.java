package pikaparser.memotable;

import pikaparser.clause.Clause;
import pikaparser.clause.First;
import pikaparser.clause.OneOrMore;
import pikaparser.parser.ASTNode;

/** A complete match of a {@link Clause} at a given start position. */
public class Match implements Comparable<Match> {
    /** The {@link MemoKey}. */
    public final MemoKey memoKey;

    /** The length of the match. */
    public final int len;

    /** The subclause matches. */
    public final Match[] subClauseMatches;

    /**
     * The subclause index of the first matching subclause (will be 0 unless {@link #clause} is a {@link First}, and
     * the matching clause was not the first subclause).
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
     * Compare this {@link Match} to another {@link Match} of the same {@link Clause} type.
     * 
     * @return a negative value if this {@link Match} is a better match than the other {@link Match}, or a positive
     *         value if the other {@link Match} is a better match than this one.
     */
    @Override
    public int compareTo(Match o) {
        if (o == this) {
            // Fast path to stop recursive comparison when subclause matches are identical
            return 0;
        }

        // An earlier subclause match in a First clause wins over a later match
        if (this.memoKey.clause instanceof First) {
            var diff0 = this.firstMatchingSubClauseIdx - o.firstMatchingSubClauseIdx;
            if (diff0 != 0) {
                return diff0;
            }
        }

        // A longer match (i.e. a match that spans more characters in the input) wins over a shorter match
        var diff1 = o.len - this.len;
        if (diff1 != 0) {
            return diff1;
        }
        var minSubclauses = Math.min(this.subClauseMatches.length, o.subClauseMatches.length);
        for (int i = 0; i < minSubclauses; i++) {
            var diff2 = o.subClauseMatches[i].len - this.subClauseMatches[i].len;
            if (diff2 != 0) {
                return diff2;
            }
        }

        // A longer list of OneOrMore subclause matches wins over a shorter list
        return o.subClauseMatches.length - this.subClauseMatches.length;
    }

    public String getText(String input) {
        return input.substring(memoKey.startPos, memoKey.startPos + len);
    }

    private String getSubClauseASTNodeLabel(int subClauseMatchIdx) {
        // For OneOrMore clauses, there's only one subclause (and therefore only one subclause label),
        // no matter how many matches. For First and Longest clauses, firstMatchingSubClauseIdx gives the
        // index of the matching clause (for other clause types, firstMatchingSubClauseIdx is zero).
        // For Seq, subClauseMatchIdx pairs subclause labels with subclauses.
        var subClauseLabelIdx = memoKey.clause instanceof OneOrMore ? 0
                : firstMatchingSubClauseIdx + subClauseMatchIdx;
        var subClauseASTNodeLabel = memoKey.clause.subClauseASTNodeLabels == null ? null
                : memoKey.clause.subClauseASTNodeLabels[subClauseLabelIdx];
        return subClauseASTNodeLabel;
    }

    private void toAST(ASTNode parent, String input) {
        // Recurse to descendants
        for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatches.length; subClauseMatchIdx++) {
            var subClauseMatch = subClauseMatches[subClauseMatchIdx];
            var subClauseASTNodeLabel = getSubClauseASTNodeLabel(subClauseMatchIdx);
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

    public void printTreeView(String input, String indentStr, String astNodeLabel, boolean isLastChild) {
        int inpLen = 80;
        String inp = input.substring(memoKey.startPos,
                Math.min(input.length(), memoKey.startPos + Math.min(len, inpLen)));
        if (inp.length() == inpLen) {
            inp += "...";
        }
        inp = inp.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
        System.out.println(indentStr + "│   ");
        System.out.println(indentStr + (isLastChild ? "└─ " : "├─ ")
                + (astNodeLabel == null ? "" : astNodeLabel + ":(") + memoKey.toStringWithRuleNames()
                + (astNodeLabel == null ? "" : ")") + "+" + len + " \"" + inp + "\"");
        if (subClauseMatches != null) {
            for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatches.length; subClauseMatchIdx++) {
                var subClauseMatch = subClauseMatches[subClauseMatchIdx];
                var subClauseASTNodeLabel = getSubClauseASTNodeLabel(subClauseMatchIdx);
                subClauseMatch.printTreeView(input, indentStr + (isLastChild ? "   " : "│  "),
                        subClauseASTNodeLabel, subClauseMatchIdx == subClauseMatches.length - 1);
            }
        }
    }

    public void printTreeView(String input) {
        printTreeView(input, "", null, true);
    }

    public String toStringWithRuleNames() {
        StringBuilder buf = new StringBuilder();
        buf.append(memoKey.toStringWithRuleNames() + "+" + len + " => [ ");
        for (int i = 0; i < subClauseMatches.length; i++) {
            var s = subClauseMatches[i];
            if (i > 0) {
                buf.append(" ; ");
            }
            buf.append(s.toStringWithRuleNames());
        }
        buf.append(" ]");
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(memoKey + "+" + len + " => [ ");
        for (int i = 0; i < subClauseMatches.length; i++) {
            var s = subClauseMatches[i];
            if (i > 0) {
                buf.append(" ; ");
            }
            buf.append(s.toString());
        }
        buf.append(" ]");
        return buf.toString();
    }
}
