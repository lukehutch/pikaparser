package pikaparser.clause.nonterminal;

import pikaparser.clause.Clause;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class OneOrMore extends Clause {
    public OneOrMore(Clause subClause) {
        super(new Clause[] { subClause });
    }

    public OneOrMore(Clause[] subClauses) {
        super(subClauses);
    }

    @Override
    public void determineWhetherCanMatchZeroChars() {
        if (labeledSubClauses[0].clause.canMatchZeroChars) {
            canMatchZeroChars = true;
        }
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        var labeledSubClause = labeledSubClauses[0].clause;
        var subClauseMemoKey = new MemoKey(labeledSubClause, memoKey.startPos);
        var subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey);
        if (subClauseMatch == null) {
            return null;
        }

        // Perform right-recursive match of the same OneOrMore clause, so that the memo table doesn't
        // fill up with O(N^2) entries in the number of subclause matches N.
        // If there are two or more matches, tailMatch will be non-null.
        var tailMatchMemoKey = new MemoKey(this, memoKey.startPos + subClauseMatch.len);
        var tailMatch = memoTable.lookUpBestMatch(tailMatchMemoKey);

        // Return a new (right-recursive) match
        return tailMatch == null // 
                ? new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0, /* len = */ subClauseMatch.len,
                        new Match[] { subClauseMatch })
                : new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0,
                        /* len = */ subClauseMatch.len + tailMatch.len, new Match[] { subClauseMatch, tailMatch });
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = onlySubClauseToStringWithASTNodeLabel() + "+";
        }
        return toStringCached;
    }
}
