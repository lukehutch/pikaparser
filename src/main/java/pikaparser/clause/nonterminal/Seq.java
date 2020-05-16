package pikaparser.clause.nonterminal;

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
    public void determineWhetherCanMatchZeroChars() {
        // For Seq, all subclauses must be able to match zero characters for the whole clause to
        // be able to match zero characters
        canMatchZeroChars = true;
        for (var subClause : labeledSubClauses) {
            if (!subClause.clause.canMatchZeroChars) {
                canMatchZeroChars = false;
                break;
            }
        }
    }

    @Override
    public void addAsSeedParentClause() {
        // All sub-clauses up to and including the first clause that matches one or more characters
        // needs to seed its parent clause if there is a subclause match
        for (var labeledSubClause : labeledSubClauses) {
            labeledSubClause.clause.seedParentClauses.add(this);
            if (!labeledSubClause.clause.canMatchZeroChars) {
                // Don't need to any subsequent subclauses to seed this parent clause
                break;
            }
        }
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        Match[] subClauseMatches = null;
        var currStartPos = memoKey.startPos;
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            var subClause = labeledSubClauses[subClauseIdx].clause;
            var subClauseMemoKey = new MemoKey(subClause, currStartPos);
            var subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey);
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
