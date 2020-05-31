//
// This file is part of the pika parser reference implementation:
//
//     https://github.com/lukehutch/pikaparser
//
// The pika parsing algorithm is described in the following paper: 
//
//     Pika parsing: parsing in reverse solves the left recursion and error recovery problems
//     Luke A. D. Hutchison, May 2020
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

/** The First (ordered choice) PEG operator. */
public class First extends Clause {
    public First(Clause... subClauses) {
        super(subClauses);
        if (subClauses.length < 2) {
            throw new IllegalArgumentException(First.class.getSimpleName() + " expects 2 or more subclauses");
        }
    }

    @Override
    public void determineWhetherCanMatchZeroChars() {
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            // Up to one subclause of a First clause can match zero characters, and if present,
            // the subclause that can match zero characters must be the last subclause
            if (labeledSubClauses[subClauseIdx].clause.canMatchZeroChars) {
                canMatchZeroChars = true;
                if (subClauseIdx < labeledSubClauses.length - 1) {
                    throw new IllegalArgumentException(
                            "Subclause " + subClauseIdx + " of " + First.class.getSimpleName()
                                    + " can match zero characters, which means subsequent subclauses will never be "
                                    + "matched: " + this);
                }
                break;
            }
        }
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            var subClause = labeledSubClauses[subClauseIdx].clause;
            var subClauseMemoKey = new MemoKey(subClause, memoKey.startPos);
            var subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey);
            if (subClauseMatch != null) {
                // Return a match for the first matching subclause
                return new Match(memoKey, /* len = */ subClauseMatch.len,
                        /* firstMatchingSubclauseIdx = */ subClauseIdx, new Match[] { subClauseMatch });
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
                buf.append(labeledSubClauses[i].toStringWithASTNodeLabel(this));
            }
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
