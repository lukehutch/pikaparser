package pikaparser.clause.nonterminal;

import pikaparser.clause.Clause;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class NotFollowedBy extends Clause {

    public NotFollowedBy(Clause subClause) {
        super(new Clause[] { subClause });
    }

    public NotFollowedBy(Clause[] subClauses) {
        super(subClauses);
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input) {
        var labeledSubClause = labeledSubClauses[0].clause;
        var subClauseMemoKey = new MemoKey(labeledSubClause, memoKey.startPos);
        var subClauseMatch = matchDirection == MatchDirection.TOP_DOWN
                // Match lex rules top-down, which avoids creating memo entries for unused terminals.
                ? labeledSubClause.match(MatchDirection.TOP_DOWN, memoTable, subClauseMemoKey, input)
                // Otherwise matching bottom-up -- just look in the memo table for subclause matches
                : memoTable.lookUpBestMatch(subClauseMemoKey);
        // Replace any invalid subclause match with a zero-char-consuming match
        if (subClauseMatch == null) {
            return new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0, /* len = */ 0,
                    new Match[] { subClauseMatch });
        }
        return null;
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = "!" + onlySubClauseToStringWithASTNodeLabel();
        }
        return toStringCached;
    }
}
