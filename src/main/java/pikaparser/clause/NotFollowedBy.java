package pikaparser.clause;

import java.util.concurrent.PriorityBlockingQueue;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.Parser;

public class NotFollowedBy extends Clause {

    public NotFollowedBy(Clause subClause) {
        super(new Clause[] { subClause });
    }

    public NotFollowedBy(Clause[] subClauses) {
        super(subClauses);
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input,
            PriorityBlockingQueue<MemoKey> priorityQueue) {
        var subClause = subClauses[0];
        var subClauseMemoKey = new MemoKey(subClause, memoKey.startPos);
        var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                ? subClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input, priorityQueue)
                // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                : memoTable.lookUpBestMatch(subClauseMemoKey, input, memoKey);
        // Replace any invalid subclause match with a zero-char-consuming match
        if (subClauseMatch == null) {
            return memoTable.addNonTerminalMatch(memoKey, /* firstMatchingSubClauseIdx = */ 0,
                    new Match[] { subClauseMatch }, priorityQueue);
        }
        if (Parser.DEBUG) {
            System.out.println(
                    "Failed to match at position " + memoKey.startPos + ": " + memoKey.toStringWithRuleNames());
        }
        return null;
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            buf.append("!(");
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
