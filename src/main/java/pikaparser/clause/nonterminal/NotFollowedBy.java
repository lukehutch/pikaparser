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
    public void determineWhetherCanMatchZeroChars() {
        canMatchZeroChars = true;
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        var labeledSubClause = labeledSubClauses[0].clause;
        var subClauseMemoKey = new MemoKey(labeledSubClause, memoKey.startPos);
        var subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey);
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
