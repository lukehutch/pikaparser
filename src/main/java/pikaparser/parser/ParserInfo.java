package pikaparser.parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import pikaparser.clause.Clause;
import pikaparser.clause.terminal.Terminal;
import pikaparser.grammar.Grammar;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoTable;

public class ParserInfo {

    private static final char NON_ASCII_CHAR = '■';

    private static void getConsumedChars(Match match, BitSet consumedChars) {
        for (int i = match.memoKey.startPos, ii = match.memoKey.startPos + match.len; i < ii; i++) {
            consumedChars.set(i);
        }
        Match[] subClauseMatches = match.getSubClauseMatchesRaw();
        if (subClauseMatches != null) {
            for (int i = 0; i < subClauseMatches.length; i++) {
                Match subClauseMatch = subClauseMatches[i];
                getConsumedChars(subClauseMatch, consumedChars);
            }
        }
    }

    private static void replaceNonASCII(String str, StringBuilder buf) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            buf.append(c < 32 || c > 126 ? NON_ASCII_CHAR : c);
        }
    }

    private static String replaceNonASCII(String str) {
        StringBuilder buf = new StringBuilder();
        replaceNonASCII(str, buf);
        return buf.toString();
    }

    public static void printClauses(Grammar grammar) {
        for (int i = grammar.allClauses.size() - 1; i >= 0; --i) {
            var clause = grammar.allClauses.get(i);
            System.out.println(
                    String.format("%3d : %s", grammar.allClauses.size() - 1 - i, clause.toStringWithRuleNames()));
        }
    }

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
        var matchesForDepth = cycleDepthToMatches.get(cycleDepth);
        if (matchesForDepth == null) {
            matchesForDepth = new TreeMap<>(Collections.reverseOrder());
            cycleDepthToMatches.put(cycleDepth, matchesForDepth);
        }
        var matchesForClauseIdx = matchesForDepth.get(match.memoKey.clause.clauseIdx);
        if (matchesForClauseIdx == null) {
            matchesForClauseIdx = new TreeMap<>();
            matchesForDepth.put(match.memoKey.clause.clauseIdx, matchesForClauseIdx);
        }
        matchesForClauseIdx.put(match.memoKey.startPos, match);
        return cycleDepth;
    }


    public static void printMemoTable(List<Clause> allClauses, MemoTable memoTable, String input) {
        StringBuilder[] buf = new StringBuilder[allClauses.size()];
        int marginWidth = 0;
        for (int i = 0; i < allClauses.size(); i++) {
            buf[i] = new StringBuilder();
            buf[i].append(String.format("%3d", i) + " : ");
            Clause clause = allClauses.get(allClauses.size() - 1 - i);
            if (clause instanceof Terminal) {
                buf[i].append("[terminal] ");
            }
            if (clause.canMatchZeroChars) {
                buf[i].append("[canMatchZeroChars] ");
            }
            buf[i].append(clause.toStringWithRuleNames());
            marginWidth = Math.max(marginWidth, buf[i].length() + 2);
        }
        int tableWidth = marginWidth + input.length() + 1;
        for (int i = 0; i < allClauses.size(); i++) {
            while (buf[i].length() < marginWidth) {
                buf[i].append(' ');
            }
            while (buf[i].length() < tableWidth) {
                buf[i].append('-');
            }
        }
        for (int i = 0; i < allClauses.size(); i++) {
            Clause clause = allClauses.get(allClauses.size() - 1 - i);
            // Render non-matches
            for (var startPos : memoTable.getNonMatchPositions(clause)) {
                if (startPos <= input.length()) {
                    buf[i].setCharAt(marginWidth + startPos, 'x');
                }
            }
            // Render matches
            for (var match : memoTable.getNonOverlappingMatches(clause)) {
                if (match.memoKey.startPos <= input.length()) {
                    buf[i].setCharAt(marginWidth + match.memoKey.startPos, '#');
                    for (int j = match.memoKey.startPos + 1; j < match.memoKey.startPos + match.len; j++) {
                        if (j <= input.length()) {
                            buf[i].setCharAt(marginWidth + j, '=');
                        }
                    }
                }
            }
            System.out.println(buf[i]);
        }

        for (int j = 0; j < marginWidth; j++) {
            System.out.print(' ');
        }
        for (int i = 0; i < input.length(); i++) {
            System.out.print(i % 10);
        }
        System.out.println();

        for (int i = 0; i < marginWidth; i++) {
            System.out.print(' ');
        }
        System.out.println(replaceNonASCII(input));
    }

    
    public static void printMatchTree(String toplevelRuleName, Grammar grammar, MemoTable memoTable, String input,
            BitSet consumedChars) {
        if (grammar.allClauses.size() == 0) {
            throw new IllegalArgumentException("Grammar is empty");
        }

        // Get all nonoverlapping matches of the toplevel rule
        Map<Integer, Map<Integer, Map<Integer, Match>>> cycleDepthToMatches = new TreeMap<>(
                Collections.reverseOrder());
        var maxCycleDepth = 0;
        for (var topLevelMatch : grammar.getNonOverlappingMatches(toplevelRuleName, memoTable)) {
            // Pack matches into the lowest cycle they will fit into
            var cycleDepth = findCycleDepth(topLevelMatch, cycleDepthToMatches);
            maxCycleDepth = Math.max(maxCycleDepth, cycleDepth);
        }

        // Assign matches to rows
        List<Map<Integer, Match>> matchesForRow = new ArrayList<>();
        List<Clause> clauseForRow = new ArrayList<>();
        for (var matchesForDepth : cycleDepthToMatches.values()) {
            for (var matchesForClauseIdxEnt : matchesForDepth.entrySet()) {
                clauseForRow.add(grammar.allClauses.get(matchesForClauseIdxEnt.getKey()));
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
            rowLabel[i].append(String.format("%3d", grammar.allClauses.size() - 1 - clauseIdx) + " : ");
            rowLabel[i].append(label);
        }
        var emptyRowLabel = new StringBuilder();
        for (int i = 0, ii = rowLabelMaxWidth + 6; i < ii; i++) {
            emptyRowLabel.append(' ');
        }
        var edgeMarkers = new StringBuilder();
        for (int i = 0, ii = (input.length() + 1) * 2 + 1; i < ii; i++) {
            edgeMarkers.append(' ');
        }

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
            }
            rowTreeChars.setLength(0);
            rowTreeChars.append(edgeMarkers);
            for (var ent : matches.entrySet()) {
                var match = ent.getValue();
                var startIdx = match.memoKey.startPos;
                var endIdx = startIdx + match.len;
                for (int i = startIdx; i < endIdx; i++) {
                    rowTreeChars.setCharAt(i * 2 + 1, input.charAt(i));
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
        for (int i = 0; i < input.length(); i++) {
            System.out.print(i % 10);
            System.out.print(' ');
        }
        System.out.println();

        // Print input string
        for (int i = 0; i < rowLabelMaxWidth + 6; i++) {
            System.out.print(' ');
        }
        System.out.print(' ');
        var str = replaceNonASCII(input);
        for (int i = 0; i < input.length(); i++) {
            System.out.print(str.charAt(i));
            System.out.print(' ');
        }
        System.out.println();

        // Show consumed chars
        if (consumedChars != null) {
            for (int i = 0; i < rowLabelMaxWidth; i++) {
                System.out.print(' ');
            }
            for (int i = 0; i < input.length(); i++) {
                System.out.print(consumedChars.get(i) ? "^" : " ");
            }
            System.out.println();
        }
    }

    /** Print syntax errors obtained from {@link Grammar#getSyntaxErrors(MemoTable, String, String...)}. */
    public static void printSyntaxErrors(TreeMap<Integer, Entry<Integer, String>> syntaxErrors) {
        if (!syntaxErrors.isEmpty()) {
            System.out.println("\nSYNTAX ERRORS:\n");
            for (var ent : syntaxErrors.entrySet()) {
                var startPos = ent.getKey();
                var endPos = ent.getValue().getKey();
                var syntaxErrStr = ent.getValue().getValue();
                // TODO: show line numbers
                System.out.println(startPos + "+" + (endPos - startPos) + " : " + replaceNonASCII(syntaxErrStr));
            }
        }
    }

    public static void printParseResult(String topLevelRuleName, Grammar grammar, MemoTable memoTable, String input,
            String[] syntaxCoverageRuleNames, boolean showAllMatches) {
        // Print parse tree, and find which characters were consumed and which weren't
        BitSet consumedChars = new BitSet(input.length() + 1);

        System.out.println();
        System.out.println("Clauses:");
        printClauses(grammar);
        
        System.out.println();
        System.out.println("Memo Table:");
        printMemoTable(grammar.allClauses, memoTable, input);        

        // Print memo table
        System.out.println();
        System.out.println("Match tree for rule " + topLevelRuleName + ":");
        printMatchTree(topLevelRuleName, grammar, memoTable, input, consumedChars);

        // Print all matches for each clause
        for (Clause clause : grammar.allClauses) {
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
                                astNodeLabel += ",";
                            }
                            astNodeLabel += rule.labeledClause.astNodeLabel;
                        }
                    }
                }
                var prevEndPos = -1;
                for (int i = 0; i < matches.size(); i++) {
                    var match = matches.get(i);
                    // Indent matches that overlap with previous longest match
                    var overlapsPrevMatch = match.memoKey.startPos < prevEndPos;
                    if (!overlapsPrevMatch || showAllMatches) {
                        var indent = overlapsPrevMatch ? "    " : "";
                        var buf = new StringBuilder();
                        match.renderTreeView(input, indent, astNodeLabel.isEmpty() ? null : astNodeLabel, true,
                                buf);
                        System.out.println(buf.toString());
                    }
                    int newEndPos = match.memoKey.startPos + match.len;
                    if (newEndPos > prevEndPos) {
                        prevEndPos = newEndPos;
                    }
                }
            }
        }

        var topLevelRule = grammar.ruleNameWithPrecedenceToRule.get(topLevelRuleName);
        if (topLevelRule != null) {
            var topLevelRuleClause = topLevelRule.labeledClause.clause;
            var topLevelMatches = memoTable.getNonOverlappingMatches(topLevelRuleClause);
            if (!topLevelMatches.isEmpty()) {
                for (int i = 0; i < topLevelMatches.size(); i++) {
                    var topLevelMatch = topLevelMatches.get(i);
                    getConsumedChars(topLevelMatch, consumedChars);
                }
            }
            if (!topLevelMatches.isEmpty()) {
                System.out.println("\n====================================\n\nFinal AST for rule \""
                        + topLevelRuleName + "\":");
                var topLevelASTNodeName = topLevelRule.labeledClause.astNodeLabel;
                if (topLevelASTNodeName == null) {
                    topLevelASTNodeName = "<root>";
                }
                for (int i = 0; i < topLevelMatches.size(); i++) {
                    var topLevelMatch = topLevelMatches.get(i);
                    var ast = topLevelMatch.toAST(topLevelASTNodeName, input);
                    if (ast != null) {
                        System.out.println();
                        System.out.println(ast.toString());
                    }
                }
            } else {
                System.out.println("\nToplevel rule \"" + topLevelRuleName + "\" did not match anything");
            }
        } else {
            System.out.println("\nToplevel rule \"" + topLevelRuleName + "\" does not exist");
        }

        var syntaxErrors = grammar.getSyntaxErrors(memoTable, input, syntaxCoverageRuleNames);
        if (!syntaxErrors.isEmpty()) {
            printSyntaxErrors(syntaxErrors);
        }

        System.out.println("\nNum match objects created: " + memoTable.numMatchObjectsCreated);
        System.out.println("Num match objects memoized:  " + memoTable.numMatchObjectsMemoized);
    }
}
