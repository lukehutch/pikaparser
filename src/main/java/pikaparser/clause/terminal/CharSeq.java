//
// This file is part of the pika parser reference implementation:
//
//     https://github.com/lukehutch/pikaparser
//
// The pika parsing algorithm is described in the following paper: 
//
//     Pika parsing: reformulating packrat parsing as a dynamic programming algorithm solves the left recursion
//     and error recovery problems. Luke A. D. Hutchison, May 2020.
//     https://arxiv.org/abs/2005.06444
//
// This software is provided under the MIT license:
//
// Copyright 2020 Luke A. D. Hutchison
//  
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions
// of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
// TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
// CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.
//
package pikaparser.clause.terminal;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.utils.StringUtils;

/** Terminal clause that matches a token in the input string. */
public class CharSeq extends Terminal {
    public final String str;
    public final boolean ignoreCase;

    public CharSeq(String str, boolean ignoreCase) {
        super();
        this.str = str;
        this.ignoreCase = ignoreCase;
    }

    @Override
    public boolean determineWhetherCanMatchZeroChars() {
        if (str.isEmpty()) {
            canMatchZeroChars = true;
        }
        return false;
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        if (memoKey.startPos <= input.length() - str.length()
                && input.regionMatches(ignoreCase, memoKey.startPos, str, 0, str.length())) {
            // Terminals are not memoized (i.e. don't look in the memo table)
            return new Match(memoKey, /* len = */ str.length());
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
