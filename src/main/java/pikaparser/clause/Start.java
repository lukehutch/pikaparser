package pikaparser.clause;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

/**
 * Always use this rule at the start of the toplevel rule -- it will trigger parsing even if the rest of the
 * subclauses of the toplevel clause are topdown.
 * 
 * <p>
 * Without using this, a toplevel rule "G = (WS R+)" will try matching rule R after every whitespace position. Using
 * Start, the toplevel rule "G = (^ WS R+)" will only match R once, after any initial whitespace.
 */
public class Start extends Terminal {
    public static final String START_STR = "^";

    public Start() {
        super();
    }

    @Override
    public void testWhetherCanMatchZeroChars() {
        canMatchZeroChars = true;
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input) {
        if (memoKey.startPos == 0) {
            // Terminals are not memoized (i.e. don't look in the memo table)
            return new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0, /* len = */ 0,
                    Match.NO_SUBCLAUSE_MATCHES);
        }
        return null;
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = START_STR;
        }
        return toStringCached;
    }
}
