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
package pikaparser.parser.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import pikaparser.ast.ASTNode;
import pikaparser.clause.Clause;
import pikaparser.clause.nonterminal.Seq;
import pikaparser.clause.terminal.Terminal;
import pikaparser.grammar.Grammar;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

/** Utility methods for printing information about the result of a parse. */
public class ParserInfo {
    /** Print all the clauses in a grammar. */
    public static void printClauses(Grammar grammar) {
        for (int i = grammar.allClauses.size() - 1; i >= 0; --i) {
            var clause = grammar.allClauses.get(i);
            System.out.println(String.format("%3d : %s", i, clause.toStringWithRuleNames()));
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Print the memo table. */
    public static void printMemoTable(MemoTable memoTable) {
        StringBuilder[] buf = new StringBuilder[memoTable.grammar.allClauses.size()];
        int marginWidth = 0;
        for (int i = 0; i < memoTable.grammar.allClauses.size(); i++) {
            buf[i] = new StringBuilder();
            buf[i].append(String.format("%3d", memoTable.grammar.allClauses.size() - 1 - i) + " : ");
            Clause clause = memoTable.grammar.allClauses.get(memoTable.grammar.allClauses.size() - 1 - i);
            if (clause instanceof Terminal) {
                buf[i].append("[terminal] ");
            }
            if (clause.canMatchZeroChars) {
                buf[i].append("[canMatchZeroChars] ");
            }
            buf[i].append(clause.toStringWithRuleNames());
            marginWidth = Math.max(marginWidth, buf[i].length() + 2);
        }
        int tableWidth = marginWidth + memoTable.input.length() + 1;
        for (int i = 0; i < memoTable.grammar.allClauses.size(); i++) {
            while (buf[i].length() < marginWidth) {
                buf[i].append(' ');
            }
            while (buf[i].length() < tableWidth) {
                buf[i].append('-');
            }
        }

        var nonOverlappingMatches = memoTable.getAllNonOverlappingMatches();
        for (var clauseIdx = memoTable.grammar.allClauses.size() - 1; clauseIdx >= 0; --clauseIdx) {
            var row = memoTable.grammar.allClauses.size() - 1 - clauseIdx;
            var clause = memoTable.grammar.allClauses.get(clauseIdx);
            var matchesForClause = nonOverlappingMatches.get(clause);
            if (matchesForClause != null) {
                for (var matchEnt : matchesForClause.entrySet()) {
                    var match = matchEnt.getValue();
                    var matchStartPos = match.memoKey.startPos;
                    var matchEndPos = matchStartPos + match.len;
                    if (matchStartPos <= memoTable.input.length()) {
                        buf[row].setCharAt(marginWidth + matchStartPos, '#');
                        for (int j = matchStartPos + 1; j < matchEndPos; j++) {
                            if (j <= memoTable.input.length()) {
                                buf[row].setCharAt(marginWidth + j, '=');
                            }
                        }
                    }
                }
            }
            System.out.println(buf[row]);
        }

        for (int j = 0; j < marginWidth; j++) {
            System.out.print(' ');
        }
        for (int i = 0; i < memoTable.input.length(); i++) {
            System.out.print(i % 10);
        }
        System.out.println();

        for (int i = 0; i < marginWidth; i++) {
            System.out.print(' ');
        }
        System.out.println(StringUtils.replaceNonASCII(memoTable.input));
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Find the cycle depth of a given match (the maximum number of grammar cycles in any path between the match and
     * any descendant terminal match).
     */
    private static int findCycleDepth(Match match,
            Map<Integer, Map<Integer, Map<Integer, Match>>> cycleDepthToMatches) {
        var cycleDepth = 0;
        for (var subClauseMatchEnt : match.getSubClauseMatches()) {
            var subClauseMatch = subClauseMatchEnt.getValue();
            var subClauseIsInDifferentCycle = //
                    match.memoKey.clause.clauseIdx <= subClauseMatch.memoKey.clause.clauseIdx;
            var subClauseMatchDepth = findCycleDepth(subClauseMatch, cycleDepthToMatches);
            cycleDepth = Math.max(cycleDepth,
                    subClauseIsInDifferentCycle ? subClauseMatchDepth + 1 : subClauseMatchDepth);
        }
        var matchesForDepth = cycleDepthToMatches.computeIfAbsent(cycleDepth,
            k -> new TreeMap<>(Collections.reverseOrder())
        );

        var matchesForClauseIdx = matchesForDepth.computeIfAbsent(match.memoKey.clause.clauseIdx,
            k -> new TreeMap<>()
        );

        matchesForClauseIdx.put(match.memoKey.startPos, match);
        return cycleDepth;
    }

    /** Print the parse tree in memo table form. */
    public static void printParseTreeInMemoTableForm(MemoTable memoTable) {
        if (memoTable.grammar.allClauses.size() == 0) {
            throw new IllegalArgumentException("Grammar is empty");
        }

        // Map from cycle depth (sorted in decreasing order) -> clauseIdx -> startPos -> match
        var cycleDepthToMatches = new TreeMap<Integer, Map<Integer, Map<Integer, Match>>>(
                Collections.reverseOrder());

        // Input spanned by matches found so far
        var inputSpanned = new IntervalUnion();

        // Get all nonoverlapping matches rules, top-down.
        var nonOverlappingMatches = memoTable.getAllNonOverlappingMatches();
        var maxCycleDepth = 0;
        for (var clauseIdx = memoTable.grammar.allClauses.size() - 1; clauseIdx >= 0; --clauseIdx) {
            var clause = memoTable.grammar.allClauses.get(clauseIdx);
            var matchesForClause = nonOverlappingMatches.get(clause);
            if (matchesForClause != null) {
                for (var matchEnt : matchesForClause.entrySet()) {
                    var match = matchEnt.getValue();
                    var matchStartPos = match.memoKey.startPos;
                    var matchEndPos = matchStartPos + match.len;
                    // Only add parse tree to chart if it doesn't overlap with input spanned by a higher-level match
                    if (!inputSpanned.rangeOverlaps(matchStartPos, matchEndPos)) {
                        // Pack matches into the lowest cycle they will fit into
                        var cycleDepth = findCycleDepth(match, cycleDepthToMatches);
                        maxCycleDepth = Math.max(maxCycleDepth, cycleDepth);
                        // Add the range spanned by this match
                        inputSpanned.addRange(matchStartPos, matchEndPos);
                    }
                }
            }
        }

        // Assign matches to rows
        List<Map<Integer, Match>> matchesForRow = new ArrayList<>();
        List<Clause> clauseForRow = new ArrayList<>();
        for (var matchesForDepth : cycleDepthToMatches.values()) {
            for (var matchesForClauseIdxEnt : matchesForDepth.entrySet()) {
                clauseForRow.add(memoTable.grammar.allClauses.get(matchesForClauseIdxEnt.getKey()));
                matchesForRow.add(matchesForClauseIdxEnt.getValue());
            }
        }

        // Set up row labels
        var rowLabel = new StringBuilder[clauseForRow.size()];
        var rowLabelMaxWidth = 0;
        for (var i = 0; i < clauseForRow.size(); i++) {
            var clause = clauseForRow.get(i);
            rowLabel[i] = new StringBuilder();
            if (clause instanceof Terminal) {
                rowLabel[i].append("[terminal] ");
            }
            if (clause.canMatchZeroChars) {
                rowLabel[i].append("[canMatchZeroChars] ");
            }
            rowLabel[i].append(clause.toStringWithRuleNames());
            rowLabel[i].append("  ");
            rowLabelMaxWidth = Math.max(rowLabelMaxWidth, rowLabel[i].length());
        }
        for (var i = 0; i < clauseForRow.size(); i++) {
            var clause = clauseForRow.get(i);
            var clauseIdx = clause.clauseIdx;
            // Right-justify the row label
            String label = rowLabel[i].toString();
            rowLabel[i].setLength(0);
            for (int j = 0, jj = rowLabelMaxWidth - label.length(); j < jj; j++) {
                rowLabel[i].append(' ');
            }
            rowLabel[i].append(String.format("%3d", clauseIdx) + " : ");
            rowLabel[i].append(label);
        }
        var emptyRowLabel = new StringBuilder();
        for (int i = 0, ii = rowLabelMaxWidth + 6; i < ii; i++) {
            emptyRowLabel.append(' ');
        }
        var edgeMarkers = new StringBuilder();
        edgeMarkers.append(' ');
        for (int i = 1, ii = memoTable.input.length() * 2; i < ii; i++) {
            edgeMarkers.append('\u2591');
        }
        // Append one char for last column boundary, and two extra chars for zero-length matches past end of string
        edgeMarkers.append("   ");

        // Add tree structure to right of row label
        for (var row = 0; row < clauseForRow.size(); row++) {
            var matches = matchesForRow.get(row);

            StringBuilder rowTreeChars = new StringBuilder();
            rowTreeChars.append(edgeMarkers);
            var zeroLenMatchIdxs = new ArrayList<Integer>();
            for (var ent : matches.entrySet()) {
                var match = ent.getValue();
                var startIdx = match.memoKey.startPos;
                var endIdx = startIdx + match.len;

                if (startIdx == endIdx) {
                    // Zero-length match
                    zeroLenMatchIdxs.add(startIdx);
                } else {
                    // Match consumes 1 or more characters
                    for (var i = startIdx; i <= endIdx; i++) {
                        char chrLeft = rowTreeChars.charAt(i * 2);
                        rowTreeChars.setCharAt(i * 2,
                                i == startIdx
                                        ? (chrLeft == '│' ? '├' : chrLeft == '┤' ? '┼' : chrLeft == '┐' ? '┬' : '┌')
                                        : i == endIdx ? (chrLeft == '│' ? '┤' : '┐') : '─');
                        if (i < endIdx) {
                            rowTreeChars.setCharAt(i * 2 + 1, '─');
                        }
                    }
                }
            }
            System.out.print(emptyRowLabel);
            System.out.println(rowTreeChars);

            for (var ent : matches.entrySet()) {
                var match = ent.getValue();
                var startIdx = match.memoKey.startPos;
                var endIdx = startIdx + match.len;
                edgeMarkers.setCharAt(startIdx * 2, '│');
                edgeMarkers.setCharAt(endIdx * 2, '│');
                for (int i = startIdx * 2 + 1, ii = endIdx * 2; i < ii; i++) {
                    var c = edgeMarkers.charAt(i);
                    if (c == '░' || c == '│') {
                        edgeMarkers.setCharAt(i, ' ');
                    }
                }
            }
            rowTreeChars.setLength(0);
            rowTreeChars.append(edgeMarkers);
            for (var ent : matches.entrySet()) {
                var match = ent.getValue();
                var startIdx = match.memoKey.startPos;
                var endIdx = startIdx + match.len;
                for (int i = startIdx; i < endIdx; i++) {
                    rowTreeChars.setCharAt(i * 2 + 1, StringUtils.replaceNonASCII(memoTable.input.charAt(i)));
                }
            }
            for (var zeroLenMatchIdx : zeroLenMatchIdxs) {
                rowTreeChars.setCharAt(zeroLenMatchIdx * 2, '▮');
            }
            System.out.print(rowLabel[row]);
            System.out.println(rowTreeChars);
        }

        // Print input index digits
        for (int j = 0; j < rowLabelMaxWidth + 6; j++) {
            System.out.print(' ');
        }
        System.out.print(' ');
        for (int i = 0; i < memoTable.input.length(); i++) {
            System.out.print(i % 10);
            System.out.print(' ');
        }
        System.out.println();

        // Print input string
        for (int i = 0; i < rowLabelMaxWidth + 6; i++) {
            System.out.print(' ');
        }
        System.out.print(' ');
        for (int i = 0; i < memoTable.input.length(); i++) {
            System.out.print(StringUtils.replaceNonASCII(memoTable.input.charAt(i)));
            System.out.print(' ');
        }
        System.out.println();
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Print syntax errors obtained from {@link MemoTable#getSyntaxErrors(String...)}. */
    public static void printSyntaxErrors(NavigableMap<Integer, Entry<Integer, String>> syntaxErrors) {
        if (!syntaxErrors.isEmpty()) {
            System.out.println("\nSYNTAX ERRORS:\n");
            for (var ent : syntaxErrors.entrySet()) {
                var startPos = ent.getKey();
                var endPos = ent.getValue().getKey();
                var syntaxErrStr = ent.getValue().getValue();
                // TODO: show line numbers
                System.out.println(
                        startPos + "+" + (endPos - startPos) + " : " + StringUtils.replaceNonASCII(syntaxErrStr));
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Print matches in the memo table for a given clause. */
    public static void printMatches(Clause clause, MemoTable memoTable, boolean showAllMatches) {
        var matches = memoTable.getAllMatches(clause);
        if (!matches.isEmpty()) {
            System.out.println("\n====================================\n\nMatches for "
                    + clause.toStringWithRuleNames() + " :");
            // Get toplevel AST node label(s), if present
            String astNodeLabel = "";
            if (clause.rules != null) {
                for (var rule : clause.rules) {
                    if (rule.labeledClause.astNodeLabel != null) {
                        if (!astNodeLabel.isEmpty()) {
                            astNodeLabel += ":";
                        }
                        astNodeLabel += rule.labeledClause.astNodeLabel;
                    }
                }
            }
            var prevEndPos = -1;
            for (int j = 0; j < matches.size(); j++) {
                var match = matches.get(j);
                // Indent matches that overlap with previous longest match
                var overlapsPrevMatch = match.memoKey.startPos < prevEndPos;
                if (!overlapsPrevMatch || showAllMatches) {
                    var indent = overlapsPrevMatch ? "    " : "";
                    var buf = new StringBuilder();
                    TreeUtils.renderTreeView(match, astNodeLabel.isEmpty() ? null : astNodeLabel, memoTable.input,
                            indent, true, buf);
                    System.out.println(buf.toString());
                }
                int newEndPos = match.memoKey.startPos + match.len;
                if (newEndPos > prevEndPos) {
                    prevEndPos = newEndPos;
                }
            }
        } else {
            System.out.println(
                    "\n====================================\n\nNo matches for " + clause.toStringWithRuleNames());
        }
    }

    /** Print matches in the memo table for a given clause and its subclauses. */
    public static void printMatchesAndSubClauseMatches(Clause clause, MemoTable memoTable) {
        printMatches(clause, memoTable, true);
        for (int i = 0; i < clause.labeledSubClauses.length; i++) {
            printMatches(clause.labeledSubClauses[i].clause, memoTable, true);
        }
    }

    /**
     * Print matches in the memo table for a given Seq clause and its subclauses, including partial matches of the
     * Seq.
     */
    public static void printMatchesAndPartialMatches(Seq seqClause, MemoTable memoTable) {
        var numSubClauses = seqClause.labeledSubClauses.length;
        for (var subClause0Match : memoTable.getAllMatches(seqClause.labeledSubClauses[0].clause)) {
            var subClauseMatches = new ArrayList<Match>();
            subClauseMatches.add(subClause0Match);
            var currStartPos = subClause0Match.memoKey.startPos + subClause0Match.len;
            for (var i = 1; i < numSubClauses; i++) {
                var subClauseIMatch = memoTable
                        .lookUpBestMatch(new MemoKey(seqClause.labeledSubClauses[i].clause, currStartPos));
                if (subClauseIMatch == null) {
                    break;
                }
                subClauseMatches.add(subClauseIMatch);
            }
            System.out.println("\n====================================\n\nMatched "
                    + (subClauseMatches.size() == numSubClauses ? "all subclauses"
                            : subClauseMatches.size() + " out of " + numSubClauses + " subclauses")
                    + " of clause (" + seqClause + ") at start pos " + subClause0Match.memoKey.startPos);
            System.out.println();
            for (int i = 0; i < subClauseMatches.size(); i++) {
                var subClauseMatch = subClauseMatches.get(i);
                var buf = new StringBuilder();
                TreeUtils.renderTreeView(subClauseMatch, seqClause.labeledSubClauses[i].astNodeLabel,
                        memoTable.input, "", true, buf);
                System.out.println(buf.toString());
            }
        }
    }

    /** Print the AST for a given clause. */
    public static void printAST(String astNodeLabel, Clause clause, MemoTable memoTable) {
        var matches = memoTable.getNonOverlappingMatches(clause);
        for (int i = 0; i < matches.size(); i++) {
            var match = matches.get(i);
            var ast = new ASTNode(astNodeLabel, match, memoTable.input);
            System.out.println(ast.toString());
        }
    }

    /** Summarize a parsing result. */
    public static void printParseResult(String topLevelRuleName, MemoTable memoTable,
            String[] syntaxCoverageRuleNames, boolean showAllMatches) {
        System.out.println();
        System.out.println("Clauses:");
        printClauses(memoTable.grammar);

        System.out.println();
        System.out.println("Memo Table:");
        printMemoTable(memoTable);

        // Print memo table
        System.out.println();
        System.out.println("Match tree for rule " + topLevelRuleName + ":");
        printParseTreeInMemoTableForm(memoTable);

        // Print all matches for each clause
        for (var i = memoTable.grammar.allClauses.size() - 1; i >= 0; --i) {
            var clause = memoTable.grammar.allClauses.get(i);
            printMatches(clause, memoTable, showAllMatches);
        }

        var rule = memoTable.grammar.ruleNameWithPrecedenceToRule.get(topLevelRuleName);
        if (rule != null) {
            System.out.println(
                    "\n====================================\n\nAST for rule \"" + topLevelRuleName + "\":\n");
            var ruleClause = rule.labeledClause.clause;
            printAST(topLevelRuleName, ruleClause, memoTable);
        } else {
            System.out.println("\nRule \"" + topLevelRuleName + "\" does not exist");
        }

        var syntaxErrors = memoTable.getSyntaxErrors(syntaxCoverageRuleNames);
        if (!syntaxErrors.isEmpty()) {
            printSyntaxErrors(syntaxErrors);
        }

        System.out.println("\nNum match objects created: " + memoTable.numMatchObjectsCreated);
        System.out.println("Num match objects memoized:  " + memoTable.numMatchObjectsMemoized);
    }
}
