package pikaparser.clause;

import java.util.Set;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoEntry;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.Parser;

public class OneOrMore extends Clause {
    OneOrMore(Clause subClause) {
        super(new Clause[] { subClause });
    }

    public OneOrMore(Clause[] subClauses) {
        super(subClauses);
    }

    @Override
    public void testWhetherCanMatchZeroChars() {
        if (subClauses[0].canMatchZeroChars) {
            canMatchZeroChars = true;
        }
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input,
            Set<MemoEntry> updatedEntries) {
        var subClause = subClauses[0];
        var subClauseMemoKey = new MemoKey(subClause, memoKey.startPos);
        var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                ? subClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input, updatedEntries)
                // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                : memoTable.lookUpBestMatch(subClauseMemoKey, input, memoKey, updatedEntries);
        if (subClauseMatch == null) {
            // Zero matches
            if (Parser.DEBUG) {
                System.out.println("Zero matches at position " + memoKey.startPos + ": " + memoKey);
            }
            return null;
        }
        
        // Perform right-recursive match of the same OneOrMore clause, so that the memo table doesn't
        // fill up with O(N^2) entries in the number of subclause matches N.
        // If there are two or more matches, tailMatch will be non-null.
        var tailMatchMemoKey = new MemoKey(this, memoKey.startPos + subClauseMatch.len);
        var tailMatch = matchDirection == MatchDirection.TOP_DOWN
                ? this.match(MatchDirection.TOP_DOWN, memoTable, tailMatchMemoKey, input, updatedEntries)
                : memoTable.lookUpBestMatch(tailMatchMemoKey, input, memoKey, updatedEntries);

        // Return a new (right-recursive) match
        return memoTable.addNonTerminalMatch(memoKey, /* firstMatchingSubClauseIdx = */ 0,
                tailMatch == null ? new Match[] { subClauseMatch } : new Match[] { subClauseMatch, tailMatch },
                updatedEntries);
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            buf.append('(');
            if (subClauseASTNodeLabels != null && subClauseASTNodeLabels[0] != null) {
                buf.append(subClauseASTNodeLabels[0]);
                buf.append(':');
            }
            buf.append(subClauses[0].toString());
            buf.append(")+");
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
