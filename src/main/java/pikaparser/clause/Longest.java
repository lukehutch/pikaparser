package pikaparser.clause;

import java.util.concurrent.PriorityBlockingQueue;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.Parser;

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
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input,
            PriorityBlockingQueue<MemoKey> priorityQueue) {
        Match longestSubClauseMatch = null;
        int longestSubClauseMatchIdx = 0;
        for (int subClauseIdx = 0; subClauseIdx < subClauses.length; subClauseIdx++) {
            var subClause = subClauses[subClauseIdx];
            var subClauseMemoKey = new MemoKey(subClause, memoKey.startPos);
            var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                    // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                    ? subClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input, priorityQueue)
                    // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                    : memoTable.lookUpBestMatch(subClauseMemoKey, input, memoKey);
            if (subClauseMatch != null
                    && (longestSubClauseMatch == null || longestSubClauseMatch.len < subClauseMatch.len)) {
                longestSubClauseMatch = subClauseMatch;
                longestSubClauseMatchIdx = subClauseIdx;
            }
        }
        if (longestSubClauseMatch != null) {
            return memoTable.addNonTerminalMatch(memoKey, longestSubClauseMatchIdx,
                    new Match[] { longestSubClauseMatch }, priorityQueue);
        } else {
            if (Parser.DEBUG) {
                System.out.println("All subclauses failed to match at position " + memoKey.startPos + ": "
                        + memoKey.toStringWithRuleNames());
            }
            return null;
        }
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
