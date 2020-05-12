package pikaparser.clause.util;

import pikaparser.clause.Clause;

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
            return astNodeLabel + ":" + clause.toString();  // TODO: check precedence
        }
    }
}
