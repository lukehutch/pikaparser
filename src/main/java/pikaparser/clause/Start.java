package pikaparser.clause;

import java.util.Set;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoEntry;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.Parser;

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
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input,
            Set<MemoEntry> updatedEntries) {
        // Terminals always add matches to the memo table if they match
        // Match zero characters at beginning of input
        if (memoKey.startPos == 0) {
            return memoTable.addTerminalMatch(memoKey, /* terminalLen = */ 0, updatedEntries);
        }
        if (Parser.DEBUG) {
            System.out.println(getClass().getSimpleName() + " failed to match at position " + memoKey.startPos
                    + ": " + memoKey.toStringWithRuleNames());
        }
        // Don't call MemoTable.addTerminalMatch for terminals that don't match, to limit size of memo table
        return null;
    }

    @Override
    protected Clause duplicate(Set<Clause> visited) {
        return new Start();
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = START_STR;
        }
        return toStringCached;
    }
}
