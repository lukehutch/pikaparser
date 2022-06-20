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
package pikaparser.clause.nonterminal;

import pikaparser.clause.Clause;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class OneOrMore extends Clause {
    public OneOrMore(Clause subClause) {
        super(new Clause[] { subClause });
    }

    @Override
    public boolean determineWhetherCanMatchZeroChars() {
        boolean oldCanMatchZeroChars = canMatchZeroChars;
        if (labeledSubClauses[0].clause.canMatchZeroChars) {
            canMatchZeroChars = true;
        }
        return !oldCanMatchZeroChars && canMatchZeroChars;
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        var labeledSubClause = labeledSubClauses[0].clause;
        var subClauseMemoKey = new MemoKey(labeledSubClause, memoKey.startPos);
        var subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey);
        if (subClauseMatch == null) {
            // Zero matches at memoKey.startPos
            return null;
        }

        // Perform right-recursive match of the same OneOrMore clause, so that the memo table doesn't
        // fill up with O(M^2) entries in the number of subclause matches M.
        // If there are two or more matches, tailMatch will be non-null.
        var tailMatchMemoKey = new MemoKey(this, memoKey.startPos + subClauseMatch.len);
        var tailMatch = memoTable.lookUpBestMatch(tailMatchMemoKey);

        // Return a new (right-recursive) match
        return tailMatch == null //
                // There is only one match => match has only one subclause
                ? new Match(memoKey, /* len = */ subClauseMatch.len, //
                        new Match[] { subClauseMatch })
                // There are two or more matches => match has two subclauses (head, tail)
                : new Match(memoKey, /* len = */ subClauseMatch.len + tailMatch.len, //
                        new Match[] { subClauseMatch, tailMatch });
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = labeledSubClauses[0].toStringWithASTNodeLabel(this) + "+";
        }
        return toStringCached;
    }
}
