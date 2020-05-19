package pikaparser.clause.terminal;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.utils.StringUtils;

public class CharSeq extends Terminal {
    public final String str;
    public final boolean ignoreCase;

    public CharSeq(String str, boolean ignoreCase) {
        super();
        this.str = str;
        this.ignoreCase = ignoreCase;
    }

    @Override
    public void determineWhetherCanMatchZeroChars() {
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        if (memoKey.startPos <= input.length() - str.length()
                && input.regionMatches(ignoreCase, memoKey.startPos, str, 0, str.length())) {
            // Terminals are not memoized (i.e. don't look in the memo table)
            return new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0, /* len = */ str.length(),
                    Match.NO_SUBCLAUSE_MATCHES);
        }
        return null;
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = '"' + StringUtils.escapeString(str) + '"';
        }
        return toStringCached;
    }
}
