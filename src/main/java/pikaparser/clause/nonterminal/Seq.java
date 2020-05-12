package pikaparser.clause.nonterminal;

import java.util.ArrayList;
import java.util.List;

import pikaparser.clause.Clause;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class Seq extends Clause {

    public Seq(Clause... subClauses) {
        super(subClauses);
        if (subClauses.length < 2) {
            throw new IllegalArgumentException(Seq.class.getSimpleName() + " expects 2 or more subclauses");
        }
    }

    public Seq(List<Clause> subClauses) {
        this(subClauses.toArray(new Clause[0]));
    }

    @Override
    public void testWhetherCanMatchZeroChars() {
        // For Seq, all subclauses must always match for the whole clause to always match
        canMatchZeroChars = true;
        for (var subClause : labeledSubClauses) {
            if (!subClause.clause.canMatchZeroChars) {
                canMatchZeroChars = false;
                break;
            }
        }
    }

    @Override
    public List<Clause> getSeedSubClauses() {
        // Any sub-clause up to and including the first clause that requires a non-zero-char match could be
        // the matching clause.
        List<Clause> seedSubClauses = new ArrayList<>(labeledSubClauses.length);
        for (int i = 0; i < labeledSubClauses.length; i++) {
            seedSubClauses.add(labeledSubClauses[i].clause);
            if (!labeledSubClauses[i].clause.canMatchZeroChars) {
                // Don't need to seed any subsequent subclauses
                break;
            }
        }
        return seedSubClauses;
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input) {
        Match[] subClauseMatches = null;
        var currStartPos = memoKey.startPos;
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            var labeledSubClause = labeledSubClauses[subClauseIdx];
            var subClauseMemoKey = new MemoKey(labeledSubClause.clause, currStartPos);
            var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                    // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                    ? labeledSubClause.clause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input)
                    // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                    : memoTable.lookUpBestMatch(subClauseMemoKey);
            if (subClauseMatch == null) {
                // Fail after first subclause fails to match
                return null;
            }
            if (subClauseMatches == null) {
                subClauseMatches = new Match[labeledSubClauses.length];
            }
            subClauseMatches[subClauseIdx] = subClauseMatch;
            currStartPos += subClauseMatch.len;
        }
        return new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0, /* len = */ currStartPos - memoKey.startPos,
                subClauseMatches);
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            for (int i = 0; i < labeledSubClauses.length; i++) {
                if (i > 0) {
                    buf.append(" ");
                }
                subClauseToStringWithASTNodeLabel(i, buf);
            }
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
