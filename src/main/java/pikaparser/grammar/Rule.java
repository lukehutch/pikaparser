package pikaparser.grammar;

import pikaparser.clause.Clause;

public class Rule {
    public String ruleName;
    public final int precedence;
    public final Associativity associativity;
    public Clause clause;
    public String astNodeLabel;

    /** Associativity (or null implies no associativity). */
    public static enum Associativity {
        LEFT, RIGHT;
    }

    public Rule(String ruleName, int precedence, Associativity associativity, Clause clause) {
        this.ruleName = ruleName;
        this.precedence = precedence;
        this.associativity = associativity;
        this.clause = clause;
    }

    public Rule(String ruleName, Clause clause) {
        // Use precedence of -1 for rules that only have one precedence
        // (this causes the precedence number not to be shown in the output of toStringWithRuleNames())
        this(ruleName, -1, /* associativity = */ null, clause);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        buf.append(ruleName);
        buf.append(" <- ");
        buf.append(clause.toString());
        buf.append(')');
        return buf.toString();
    }
}
