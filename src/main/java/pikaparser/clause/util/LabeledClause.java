package pikaparser.clause.util;

import pikaparser.clause.Clause;
import pikaparser.grammar.MetaGrammar;

public class LabeledClause {
    public Clause clause;
    public String astNodeLabel;

    public LabeledClause(Clause clause, String astNodeLabel) {
        this.clause = clause;
        this.astNodeLabel = astNodeLabel;
    }

    @Override
    public String toString() {
        if (astNodeLabel == null) {
            return clause.toString();
        } else {
            var addParens = MetaGrammar.addParensAroundASTNodeLabel(clause);
            return astNodeLabel + ":" + (addParens ? "(" : "") + clause.toString() + (addParens ? ")" : "");
        }
    }
}
