package pikaparser.clause;

import java.util.ArrayList;
import java.util.List;
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
        List<Match> subClauseMatches = null;
        var currStartPos = memoKey.startPos;
        for (;;) {
            var subClauseMemoKey = new MemoKey(subClause, currStartPos);
            var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                    // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                    ? subClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input, updatedEntries)
                    // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                    : memoTable.lookUpBestMatch(subClauseMemoKey, input, memoKey, updatedEntries);
            if (subClauseMatch == null) {
                break;
            }
            if (subClauseMatches == null) {
                subClauseMatches = new ArrayList<>();
            }
            subClauseMatches.add(subClauseMatch);
            if (subClauseMatch.len == 0) {
                // Prevent infinite loop -- if match consumed zero characters, can only match it once
                // (i.e. OneOrMore(Nothing) will match exactly one Nothing)
                break;
            }
            currStartPos += subClauseMatch.len;
        }
        if (subClauseMatches != null) {
            return memoTable.addNonTerminalMatch(memoKey, /* firstMatchingSubClauseIdx = */ 0,
                    subClauseMatches.toArray(Match.NO_SUBCLAUSE_MATCHES), updatedEntries);
        } else {
            if (Parser.DEBUG) {
                System.out.println("Zero matches at position " + memoKey.startPos + ": " + memoKey);
            }
            return null;
        }
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
