package pikaparser.clause.aux;

import pikaparser.clause.Clause;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class ASTNodeLabel extends Clause {
    public final String astNodeLabel;

    public ASTNodeLabel(String astNodeLabel, Clause clause) {
        super(clause);
        this.astNodeLabel = astNodeLabel;
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        throw new IllegalArgumentException(getClass().getSimpleName() + " node should not be in final grammar");
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = astNodeLabel + ":(" + labeledSubClauses[0] + ")";
        }
        return toStringCached;
    }
}
