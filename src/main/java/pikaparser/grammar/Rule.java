package pikaparser.grammar;

import pikaparser.clause.Clause;
import pikaparser.clause.aux.ASTNodeLabel;
import pikaparser.clause.util.LabeledClause;

public class Rule {
    public String ruleName;
    public final int precedence;
    public final Associativity associativity;
    public LabeledClause labeledClause;

    /** Associativity (or null implies no associativity). */
    public static enum Associativity {
        LEFT, RIGHT;
    }

    public Rule(String ruleName, int precedence, Associativity associativity, Clause clause) {
        this.ruleName = ruleName;
        this.precedence = precedence;
        this.associativity = associativity;

        String astNodeLabel = null;
        var clauseToUse = clause;
        if (clause instanceof ASTNodeLabel) {
            // Transfer ASTNodeLabel.astNodeLabel to astNodeLabel
            astNodeLabel = ((ASTNodeLabel) clause).astNodeLabel;
            // skip over ASTNodeLabel node when adding subClause to subClauses array
            clauseToUse = clause.labeledSubClauses[0].clause;
        }
        this.labeledClause = new LabeledClause(clauseToUse, astNodeLabel);
    }

    public Rule(String ruleName, Clause clause) {
        // Use precedence of -1 for rules that only have one precedence
        // (this causes the precedence number not to be shown in the output of toStringWithRuleNames())
        this(ruleName, -1, /* associativity = */ null, clause);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(ruleName);
        buf.append(" <- ");
        buf.append(labeledClause.toString());
        return buf.toString();
    }
}
