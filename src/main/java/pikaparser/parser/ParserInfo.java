package pikaparser.parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import pikaparser.clause.Clause;
import pikaparser.clause.Nothing;
import pikaparser.clause.Terminal;
import pikaparser.grammar.Grammar;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoTable;

public class ParserInfo {

    private static final char NON_ASCII_CHAR = '■';

    private static void getConsumedChars(Match match, BitSet consumedChars) {
        for (int i = match.memoKey.startPos, ii = match.memoKey.startPos + match.len; i < ii; i++) {
            consumedChars.set(i);
        }
        Match[] subClauseMatches = match.subClauseMatches;
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

    static int printMemoTable(List<Clause> allClauses, MemoTable memoTable, String input, BitSet consumedChars) {
        StringBuilder[] buf = new StringBuilder[allClauses.size()];
        int marginWidth = 0;
        for (int i = 0; i < allClauses.size(); i++) {
            buf[i] = new StringBuilder();
            int clauseIdx = allClauses.size() - 1 - i;
            buf[i].append(String.format("%3d", clauseIdx) + " : ");
            Clause clause = allClauses.get(clauseIdx);
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
            int clauseIdx = allClauses.size() - 1 - i;
            Clause clause = allClauses.get(clauseIdx);
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

        if (consumedChars != null) {
            for (int i = 0; i < marginWidth; i++) {
                System.out.print(' ');
            }
            for (int i = 0; i < input.length(); i++) {
                System.out.print(consumedChars.get(i) ? " " : "~");
            }
            System.out.println();
        }

        //        // Highlight any syntax errors
        //        if (syntaxErrors != null && syntaxErrors.size() > 0) {
        //            StringBuilder buf = new StringBuilder();
        //            for (int i = 0; i < input.length(); i++) {
        //                buf.append(' ');
        //            }
        //            for (Entry<Integer, Integer> ent : syntaxErrors.entrySet()) {
        //                int startIdx = ent.getKey(), endIdx = ent.getValue();
        //                for (int i = startIdx; i < endIdx; i++) {
        //                    buf.setCharAt(i, '^');
        //                }
        //            }
        //            System.out.println(indent + buf);
        //        }

        return marginWidth;
    }

    private static void findReverseTopoOrder(Match match, Set<Match> visited, List<Match> reverseTopoOrderOut) {
        if (visited.add(match)) {
            for (var subClauseMatch : match.subClauseMatches) {
                findReverseTopoOrder(subClauseMatch, visited, reverseTopoOrderOut);
            }
            reverseTopoOrderOut.add(match);
        }
    }

    private static void printParseTree(List<Clause> allClauses, MemoTable memoTable, String input,
            String linePrefix) {
        // Find all root matches (matches that are not a subclause match of another match)
        Set<Match> allRootMatches = new HashSet<>();
        Set<Match> allSubClauseMatches = new HashSet<>();
        for (var parentClause : allClauses) {
            for (var parentMatch : memoTable.getAllMatches(parentClause)) {
                allRootMatches.add(parentMatch);
                for (var subClauseMatch : parentMatch.subClauseMatches) {
                    allSubClauseMatches.add(subClauseMatch);
                }
            }
        }
        allRootMatches.removeAll(allSubClauseMatches);

        // Find reverse of topological order of DAG from root nodes to terminals
        // (i.e. the final list of matches is topologically ordered from terminals to root nodes) 
        List<Match> topoOrder = new ArrayList<>();
        Set<Match> visited = new HashSet<>();
        for (var rootMatch : allRootMatches) {
            findReverseTopoOrder(rootMatch, visited, topoOrder);
        }

        // Find depth of each match, which is the max of the depth of all subclause matches plus one
        Map<Match, Integer> matchToDepth = new HashMap<>();
        TreeMap<Integer, List<Match>> depthToMatches = new TreeMap<>();
        var maxDepth = 0;
        for (int i = 0; i < topoOrder.size(); i++) {
            var match = topoOrder.get(i);
            int depth;
            if (match.subClauseMatches.length == 0) {
                depth = 0;
            } else {
                int maxSubClauseMatchDepth = 0;
                for (var subClauseMatch : match.subClauseMatches) {
                    var subClauseMatchDepth = matchToDepth.get(subClauseMatch);
                    if (subClauseMatchDepth == null) {
                        // Should not happen
                        throw new IllegalArgumentException("Could not find subclause match depth");
                    }
                    maxSubClauseMatchDepth = Math.max(maxSubClauseMatchDepth, subClauseMatchDepth);
                }
                depth = maxSubClauseMatchDepth + 1;
                maxDepth = Math.max(maxDepth, depth);
            }
            matchToDepth.put(match, depth);
            var matchesForDepth = depthToMatches.get(depth);
            if (matchesForDepth == null) {
                depthToMatches.put(depth, matchesForDepth = new ArrayList<>());
            }
            matchesForDepth.add(match);
        }

        // Iterate in decreasing order of match depth, finding the ranges of the input sequence that are spanned
        // by matches
        var matchesInIncreasingOrderOfDepth = new ArrayList<>(depthToMatches.entrySet());
        var nonOverlappedMatches = new HashSet<Match>();
        for (int i = matchesInIncreasingOrderOfDepth.size() - 1; i >= 0; --i) {
            var ent = matchesInIncreasingOrderOfDepth.get(i);
            var matchesForDepth = ent.getValue();
            // Sort matches into increasing order of startPos (if matches overlap, the earier match will
            // clobber the later match)
            Collections.sort(matchesForDepth, Comparator.comparing(match -> match.memoKey.startPos));
            // Find matches that are not overlapped by an earlier match at the same depth
            var minNextStartPos = 0;
            for (var matchForDepth : matchesForDepth) {
                if (matchForDepth.memoKey.startPos >= minNextStartPos) {
                    minNextStartPos = matchForDepth.memoKey.startPos + matchForDepth.len;
                    nonOverlappedMatches.add(matchForDepth);
                }
            }
        }

        // Index clauses
        var clauseToClauseIdx = new HashMap<>();
        var numClauses = allClauses.size();
        for (int i = 0; i < numClauses; i++) {
            var clause = allClauses.get(i);
            // Don't display Nothing matches, they match everywhere
            if (!(clause instanceof Nothing)) {
                clauseToClauseIdx.put(clause, i);
            }
        }

        // Create character grid
        var gridHeight = (maxDepth + 1) * 2;
        StringBuilder[] grid = new StringBuilder[gridHeight];
        for (int i = 0; i < gridHeight; i++) {
            grid[i] = new StringBuilder(input.length() + 1);
            if (i < gridHeight - 1) {
                for (int j = 0; j <= input.length(); j++) {
                    grid[i].append(' ');
                }
            } else {
                // Put input sequence on the last row (terminals do not have any edges below them)
                replaceNonASCII(input, grid[i]);
            }
        }

        // Populate grid from maxDepth down to 0
        for (int depth = matchesInIncreasingOrderOfDepth.size() - 1; depth >= 0; --depth) {
            int gridRow = (matchesInIncreasingOrderOfDepth.size() - 1 - depth) * 2;
            var ent = matchesInIncreasingOrderOfDepth.get(depth);
            var matchesForDepth = ent.getValue();
            for (var match : matchesForDepth) {
                // Only include earliest of any overlapping matches, and skip Nothing
                var clause = match.memoKey.clause;
                if (nonOverlappedMatches.contains(match) && !(clause instanceof Nothing)) {
                    var startIdx = match.memoKey.startPos;
                    var endIdx = startIdx + match.len;
                    var clauseRuleNames = clause.rules == null ? null
                            : clause.rules.stream().map(rule -> rule.ruleName).sorted()
                                    .collect(Collectors.toList());
                    var clauseRuleName = clauseRuleNames != null && !clauseRuleNames.isEmpty()
                            ? clauseRuleNames.get(0)
                            : null;
                    // Determine label for clause, which is rule name, if clause is the toplevel
                    // clause of a rule, or (clauseIdx + ":" + clause.toString()).
                    var clauseIdx = clauseToClauseIdx.get(clause);
                    if (clauseIdx == null) {
                        // Should not happen
                        throw new IllegalArgumentException("Clause not indexed: " + clause);
                    }
                    var clauseIdxStr = clauseIdx.toString();
                    String clauseLabel;
                    if (clauseRuleName != null) {
                        clauseLabel = clauseRuleName;
                    } else if (clauseIdxStr.length() <= endIdx - startIdx - 2) {
                        clauseLabel = clauseIdxStr + ":" + clause.toString();
                    } else {
                        // Don't truncate clauseIdx, if it can't fit in the available space
                        clauseLabel = "";
                    }
                    // Truncate clause label to fit into available space
                    if (clauseLabel.length() > endIdx - startIdx - 2) {
                        clauseLabel = clauseLabel.substring(0, Math.max(0, endIdx - startIdx - 2));
                    }
                    if (endIdx - startIdx < 2) {
                        // For 0 or 1-char match, show '#'
                        grid[gridRow].setCharAt(startIdx, '▯');
                    } else {
                        // Draw a horizontal line at the top of the span
                        for (int j = startIdx; j < endIdx; j++) {
                            grid[gridRow].setCharAt(j, '─');
                        }
                        // Add vertical lines connecting match's span to subclause matches
                        if (match.subClauseMatches.length > 0) {
                            for (int j = 0; j <= match.subClauseMatches.length; j++) {
                                int subClauseMatchDepth;
                                int subClauseMatchStartPos;
                                int prevSubClauseMatchDepth = 0;
                                if (j < match.subClauseMatches.length) {
                                    var subClauseMatch = match.subClauseMatches[j];
                                    subClauseMatchStartPos = subClauseMatch.memoKey.startPos;
                                    var subClauseMatchDepthI = matchToDepth.get(subClauseMatch);
                                    if (subClauseMatchDepthI == null) {
                                        // Should not happen
                                        throw new IllegalArgumentException("Could not find subclause match depth");
                                    }
                                    subClauseMatchDepth = subClauseMatchDepthI;
                                } else {
                                    // Also drop a vertical line down at the very end of the match,
                                    // at the position of the last character of the last subclause match
                                    var lastSubClauseMatch = match.subClauseMatches[match.subClauseMatches.length
                                            - 1];
                                    subClauseMatchStartPos = lastSubClauseMatch.memoKey.startPos
                                            + Math.max(0, lastSubClauseMatch.len - 1);
                                    subClauseMatchDepth = prevSubClauseMatchDepth;
                                }
                                if (subClauseMatchDepth > depth) {
                                    // Should not happen
                                    throw new IllegalArgumentException("Subclause match depths out of order");
                                }
                                int subClauseMatchGridRow = (matchesInIncreasingOrderOfDepth.size() - 1
                                        - subClauseMatchDepth) * 2;
                                grid[gridRow].setCharAt(subClauseMatchStartPos,
                                        subClauseMatchStartPos == startIdx ? '┌'
                                                : subClauseMatchStartPos < endIdx - 1 ? '┬' : '┐');
                                for (int k = gridRow + 1; k < subClauseMatchGridRow; k++) {
                                    grid[k].setCharAt(subClauseMatchStartPos, '│');
                                }
                                prevSubClauseMatchDepth = subClauseMatchDepth;
                            }
                        }
                        // Write over the match span with the label
                        if (!clauseLabel.isEmpty()) {
                            for (int j = startIdx + 1, jj = Math.min(startIdx + 1 + clauseLabel.length(),
                                    endIdx - 1); j < jj; j++) {
                                grid[gridRow].setCharAt(j, clauseLabel.charAt(j - startIdx - 1));
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < gridHeight; i++) {
            System.out.print(linePrefix);
            System.out.println(grid[i]);
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

    public static List<Clause> getClauseOrder(Grammar grammar) {
        var clauseOrder = new ArrayList<Clause>();
        List<Clause> allClauses = grammar.allClauses;
        for (int i = 0; i < allClauses.size(); i++) {
            Clause clause = allClauses.get(allClauses.size() - 1 - i);
            if (!(clause instanceof Terminal)) {
                // First show nonterminals
                clauseOrder.add(clause);
            }
        }
        for (int i = 0; i < allClauses.size(); i++) {
            Clause clause = allClauses.get(allClauses.size() - 1 - i);
            if (clause instanceof Terminal && !(clause instanceof Nothing)) {
                // Then show terminals
                clauseOrder.add(clause);
            }
        }
        return clauseOrder;
    }

    public static void printParseResult(Grammar grammar, MemoTable memoTable, String input, String topLevelRuleName,
            String[] syntaxCoverageRuleNames, boolean showAllMatches) {
        // Print parse tree, and find which characters were consumed and which weren't
        BitSet consumedChars = new BitSet(input.length() + 1);

        // Find reachable clauses, by reversing topological order of clauses, and putting terminals last 
        var clauseOrder = getClauseOrder(grammar);

        // Print memo table
        System.out.println();
        System.out.println("Memo table:");
        var marginWidth = printMemoTable(clauseOrder, memoTable, input, consumedChars);

        // Print parse tree
        System.out.println("Parse tree:");
        StringBuilder indentBuf = new StringBuilder(marginWidth);
        for (int i = 0; i < marginWidth; i++) {
            indentBuf.append(' ');
        }
        printParseTree(clauseOrder, memoTable, input, indentBuf.toString());

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
                        if (rule.astNodeLabel != null) {
                            if (!astNodeLabel.isEmpty()) {
                                astNodeLabel += ",";
                            }
                            astNodeLabel += rule.astNodeLabel;
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
                        System.out.println();
                        match.printTreeView(input, indent, astNodeLabel.isEmpty() ? null : astNodeLabel, true);
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
            var topLevelRuleClause = topLevelRule.clause;
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
                var topLevelASTNodeName = topLevelRule.astNodeLabel;
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
