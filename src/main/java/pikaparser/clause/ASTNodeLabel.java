package pikaparser.clause;

import java.util.Set;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoEntry;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class ASTNodeLabel extends Clause {
    public final String astNodeLabel;

    public ASTNodeLabel(String astNodeLabel, Clause clause) {
        super(clause);
        this.astNodeLabel = astNodeLabel;
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input,
            Set<MemoEntry> updatedEntries) {
        throw new IllegalArgumentException(getClass().getSimpleName() + " node should not be in final grammar");
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = astNodeLabel + ":(" + subClauses[0] + ")";
        }
        return toStringCached;
    }
}
