package pikaparser.clause;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class Longest extends Clause {

    public Longest(Clause... subClauses) {
        super(subClauses);
        if (subClauses.length < 2) {
            throw new IllegalArgumentException(Longest.class.getSimpleName() + " expects 2 or more subclauses");
        }
    }

    @Override
    public void testWhetherCanMatchZeroChars() {
        for (int i = 0; i < subClauses.length; i++) {
            Clause subClause = subClauses[i];
            if (subClause.canMatchZeroChars) {
                canMatchZeroChars = true;
                break;
            }
        }
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input) {
        Match longestSubClauseMatch = null;
        int longestSubClauseMatchIdx = 0;
        for (int subClauseIdx = 0; subClauseIdx < subClauses.length; subClauseIdx++) {
            var subClause = subClauses[subClauseIdx];
            var subClauseMemoKey = new MemoKey(subClause, memoKey.startPos);
            var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                    // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                    ? subClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input)
                    // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                    : memoTable.lookUpBestMatch(subClauseMemoKey, input, memoKey);
            if (subClauseMatch != null
                    && (longestSubClauseMatch == null || longestSubClauseMatch.len < subClauseMatch.len)) {
                longestSubClauseMatch = subClauseMatch;
                longestSubClauseMatchIdx = subClauseIdx;
            }
        }
        if (longestSubClauseMatch != null) {
            return new Match(memoKey, /* firstMatchingSubClauseIdx = */ longestSubClauseMatchIdx,
                    /* len = */ longestSubClauseMatch.len, new Match[] { longestSubClauseMatch });
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
                    buf.append(" | ");
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
