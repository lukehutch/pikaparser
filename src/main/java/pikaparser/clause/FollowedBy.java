package pikaparser.clause;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class FollowedBy extends Clause {

    public FollowedBy(Clause subClause) {
        super(new Clause[] { subClause });
    }

    public FollowedBy(Clause[] subClauses) {
        super(subClauses);
    }

    @Override
    public void testWhetherCanMatchZeroChars() {
        if (subClauses[0].canMatchZeroChars) {
            canMatchZeroChars = true;
        }
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input) {
        var subClause = subClauses[0];
        var subClauseMemoKey = new MemoKey(subClause, memoKey.startPos);
        var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                ? subClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input)
                // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                : memoTable.lookUpBestMatch(subClauseMemoKey);
        // Replace any valid subclause match with a zero-char-consuming match
        if (subClauseMatch != null) {
            return new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0, /* len = */ 0,
                    new Match[] { subClauseMatch });
        }
        return null;
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            buf.append("&(");
            if (subClauseASTNodeLabels != null && subClauseASTNodeLabels[0] != null) {
                buf.append(subClauseASTNodeLabels[0]);
                buf.append(':');
            }
            buf.append(subClauses[0].toString());
            buf.append(')');
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
