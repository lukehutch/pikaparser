package pikaparser.clause.nonterminal;

import pikaparser.clause.Clause;
import pikaparser.clause.terminal.Nothing;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class First extends Clause {

    public First(Clause... subClauses) {
        super(subClauses);
        if (subClauses.length < 2) {
            throw new IllegalArgumentException(First.class.getSimpleName() + " expects 2 or more subclauses");
        }
    }

    @Override
    public void determineWhetherCanMatchZeroChars() {
        for (int i = 0; i < labeledSubClauses.length; i++) {
            if (labeledSubClauses[i].clause.canMatchZeroChars) {
                canMatchZeroChars = true;
                if (i < labeledSubClauses.length - 1) {
                    throw new IllegalArgumentException("Subclause " + i + " of " + First.class.getSimpleName()
                            + " can evaluate to " + Nothing.class.getSimpleName()
                            + ", which means subsequent subclauses will never be matched: " + this);
                }
                break;
            }
        }
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            var labeledSubClause = labeledSubClauses[subClauseIdx];
            var subClauseMemoKey = new MemoKey(labeledSubClause.clause, memoKey.startPos);
            var subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey);
            if (subClauseMatch != null) {
                return new Match(memoKey, /* firstMatchingSubclauseIdx = */ subClauseIdx,
                        /* len = */ subClauseMatch.len, new Match[] { subClauseMatch });
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            for (int i = 0; i < labeledSubClauses.length; i++) {
                if (i > 0) {
                    buf.append(" / ");
                }
                subClauseToStringWithASTNodeLabel(i, buf);
            }
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
