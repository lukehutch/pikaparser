package pikaparser.clause;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import pikaparser.clause.aux.ASTNodeLabel;
import pikaparser.clause.terminal.Nothing;
import pikaparser.clause.util.LabeledClause;
import pikaparser.grammar.MetaGrammar;
import pikaparser.grammar.Rule;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public abstract class Clause {
    public LabeledClause[] labeledSubClauses;

    /** Rules this clause is a toplevel clause of */
    public List<Rule> rules;

    /** The parent clauses to seed when this clause's match memo at a given position changes. */
    public final Set<Clause> seedParentClauses = new HashSet<>();

    /** If true, the clause can match zero characters. */
    public boolean canMatchZeroChars;

    /** Index in the topological sort order of clauses, bottom-up. */
    public int clauseIdx;

    public String toStringCached;
    public String toStringWithRuleNameCached;

    // -------------------------------------------------------------------------------------------------------------

    public Clause(Clause... subClauses) {
        if (subClauses.length > 0 && subClauses[0] instanceof Nothing) {
            // Nothing can't be the first subclause, since we don't trigger upwards expansion of the DP wavefront
            // by seeding the memo table by matching Nothing at every input position, to keep the memo table small
            throw new IllegalArgumentException(
                    Nothing.class.getSimpleName() + " cannot be the first subclause of any clause");
        }
        this.labeledSubClauses = new LabeledClause[subClauses.length];
        for (int i = 0; i < subClauses.length; i++) {
            var subClause = subClauses[i];
            String astNodeLabel = null;
            if (subClause instanceof ASTNodeLabel) {
                // Transfer ASTNodeLabel.astNodeLabel to NamedClause.astNodeLabel field
                astNodeLabel = ((ASTNodeLabel) subClause).astNodeLabel;
                // skip over ASTNodeLabel node when adding subClause to subClauses array
                subClause = subClause.labeledSubClauses[0].clause;
            }
            this.labeledSubClauses[i] = new LabeledClause(subClause, astNodeLabel);
        }
    }

    public void registerRule(Rule rule) {
        if (rules == null) {
            rules = new ArrayList<>();
        }
        rules.add(rule);
    }

    public void unregisterRule(Rule rule) {
        if (rules != null) {
            rules.remove(rule);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Find which subclauses need to add this clause as a "seed parent clause". */
    public void addAsSeedParentClause() {
        // Default implementation: all subcluses will seed this parent clause. Overridden by Seq.
        for (var labeledSubClause : labeledSubClauses) {
            labeledSubClause.clause.seedParentClauses.add(this);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Sets {@link #canMatchZeroChars} to true if this clause can match zero characters, i.e. always matches at any
     * input position.
     * 
     * <p>
     * Overridden in subclasses.
     */
    public void determineWhetherCanMatchZeroChars() {
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Match a clause top-down (recursively) or bottom-up (looking in the memo-table just one level top-down) at a
     * given start position.
     */
    public abstract Match match(MemoTable memoTable, MemoKey memoKey, String input);

    // -------------------------------------------------------------------------------------------------------------

    public String getRuleNames() {
        return rules == null ? ""
                : String.join(", ",
                        rules.stream().map(rule -> rule.ruleName).sorted().collect(Collectors.toList()));
    }

    protected void subClauseToStringWithASTNodeLabel(int subClauseIdx, StringBuilder buf) {
        var labeledSubClause = labeledSubClauses[subClauseIdx];
        var subClause = labeledSubClause.clause;
        var addParens = MetaGrammar.addParensAroundSubClause(this, subClause, subClauseIdx);
        if (labeledSubClause.astNodeLabel != null) {
            buf.append(labeledSubClause.astNodeLabel);
            buf.append(':');
            addParens |= MetaGrammar.addParensAroundASTNodeLabel(subClause);
        }
        if (addParens) {
            buf.append('(');
        }
        buf.append(labeledSubClauses[subClauseIdx].clause.toString());
        if (addParens) {
            buf.append(')');
        }
    }

    protected String onlySubClauseToStringWithASTNodeLabel() {
        var buf = new StringBuilder();
        subClauseToStringWithASTNodeLabel(0, buf);
        return buf.toString();
    }

    public String toStringWithRuleNames() {
        if (toStringWithRuleNameCached == null) {
            if (rules != null) {
                StringBuilder buf = new StringBuilder();
                // Add rule names
                buf.append(getRuleNames());
                buf.append(" <- ");
                // Add any AST node labels
                for (int i = 0; i < rules.size(); i++) {
                    var rule = rules.get(i);
                    if (rule.labeledClause.astNodeLabel != null) {
                        buf.append(rule.labeledClause.astNodeLabel + ":");
                    }
                }
                buf.append(toString());
                toStringWithRuleNameCached = buf.toString();
            } else {
                toStringWithRuleNameCached = toString();
            }
        }
        return toStringWithRuleNameCached;
    }
}
