package pikaparser.clause;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class First extends Clause {

    public First(Clause... subClauses) {
        super(subClauses);
        if (subClauses.length < 2) {
            throw new IllegalArgumentException(First.class.getSimpleName() + " expects 2 or more subclauses");
        }
    }

    @Override
    public void testWhetherCanMatchZeroChars() {
        for (int i = 0; i < subClauses.length; i++) {
            var subClause = subClauses[i];
            if (subClause.canMatchZeroChars) {
                canMatchZeroChars = true;
                if (i < subClauses.length - 1) {
                    throw new IllegalArgumentException("Subclause " + i + " of " + First.class.getSimpleName()
                            + " can evaluate to " + Nothing.class.getSimpleName()
                            + ", which means subsequent subclauses will never be matched: " + this);
                }
                break;
            }
        }
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input) {
        for (int subClauseIdx = 0; subClauseIdx < subClauses.length; subClauseIdx++) {
            var subClause = subClauses[subClauseIdx];
            var subClauseMemoKey = new MemoKey(subClause, memoKey.startPos);
            var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                    // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                    ? subClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input)
                    // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                    : memoTable.lookUpBestMatch(subClauseMemoKey, input, memoKey);
            if (subClauseMatch != null) {
                return new Match(memoKey, /* firstMatchingSubclauseIdx = */ subClauseIdx,
                        /* len = */ subClauseMatch.len, new Match[] { subClauseMatch });
            }
        }
        return null;
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            buf.append('(');
            for (int i = 0; i < subClauses.length; i++) {
                if (i > 0) {
                    buf.append(" / ");
                }
                if (subClauseASTNodeLabels != null && subClauseASTNodeLabels[i] != null) {
                    buf.append(subClauseASTNodeLabels[i]);
                    buf.append(':');
                }
                buf.append(subClauses[i].toString());
            }
            buf.append(')');
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
