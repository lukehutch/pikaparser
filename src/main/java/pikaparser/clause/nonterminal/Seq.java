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

import java.util.HashSet;

import pikaparser.clause.Clause;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

/** The Seq (sequence) PEG operator. */
public class Seq extends Clause {
    public Seq(Clause... subClauses) {
        super(subClauses);
        if (subClauses.length < 2) {
            throw new IllegalArgumentException(Seq.class.getSimpleName() + " expects 2 or more subclauses");
        }
    }

    @Override
    public void determineWhetherCanMatchZeroChars() {
        // For Seq, all subclauses must be able to match zero characters for the whole clause to
        // be able to match zero characters
        canMatchZeroChars = true;
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            if (!labeledSubClauses[subClauseIdx].clause.canMatchZeroChars) {
                canMatchZeroChars = false;
                break;
            }
        }
    }

    @Override
    public void addAsSeedParentClause() {
        // All sub-clauses up to and including the first clause that matches one or more characters
        // needs to seed its parent clause if there is a subclause match
        var added = new HashSet<>();
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            var subClause = labeledSubClauses[subClauseIdx].clause;
            // Don't duplicate seed parent clauses in the subclause
            if (added.add(subClause)) {
                subClause.seedParentClauses.add(this);
            }
            if (!subClause.canMatchZeroChars) {
                // Don't need to any subsequent subclauses to seed this parent clause
                break;
            }
        }
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        Match[] subClauseMatches = null;
        var currStartPos = memoKey.startPos;
        for (int subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
            var subClause = labeledSubClauses[subClauseIdx].clause;
            var subClauseMemoKey = new MemoKey(subClause, currStartPos);
            var subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey);
            if (subClauseMatch == null) {
                // Fail after first subclause fails to match
                return null;
            }
            if (subClauseMatches == null) {
                subClauseMatches = new Match[labeledSubClauses.length];
            }
            subClauseMatches[subClauseIdx] = subClauseMatch;
            currStartPos += subClauseMatch.len;
        }
        // All subclauses matched, so the Seq clause matches
        return new Match(memoKey, /* len = */ currStartPos - memoKey.startPos, subClauseMatches);
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            for (int i = 0; i < labeledSubClauses.length; i++) {
                if (i > 0) {
                    buf.append(" ");
                }
                buf.append(labeledSubClauses[i].toStringWithASTNodeLabel(this));
            }
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
