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

    /** Call {@link #toString()}, prepending any AST node label. */
    public String toStringWithASTNodeLabel(Clause parentClause) {
        var addParens = parentClause != null && MetaGrammar.needToAddParensAroundSubClause(parentClause, clause);
        if (astNodeLabel == null && !addParens) {
            // Fast path
            return clause.toString();
        }
        var buf = new StringBuilder();
        if (astNodeLabel != null) {
            buf.append(astNodeLabel);
            buf.append(':');
            addParens |= MetaGrammar.needToAddParensAroundASTNodeLabel(clause);
        }
        if (addParens) {
            buf.append('(');
        }
        buf.append(clause.toString());
        if (addParens) {
            buf.append(')');
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return toStringWithASTNodeLabel(null);
    }
}
