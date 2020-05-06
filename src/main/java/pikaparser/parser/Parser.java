package pikaparser.parser;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import pikaparser.clause.Clause.MatchDirection;
import pikaparser.clause.Nothing;
import pikaparser.clause.Start;
import pikaparser.clause.Terminal;
import pikaparser.grammar.Grammar;
import pikaparser.memotable.MemoEntry;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class Parser {
    public final Grammar grammar;

    public String input;

    public final MemoTable memoTable = new MemoTable();

    private static final boolean PARALLELIZE = true;

    public static boolean DEBUG = false;

    public Parser(Grammar grammar) {
        this.grammar = grammar;
    }

    public void parse(String input) {
        if (this.input != null) {
            throw new IllegalArgumentException(
                    "Can only call parse(String input) once per " + Parser.class.getSimpleName() + " instance");
        }
        this.input = input;

        // A set of MemoKey instances for entries that need matching
        var priorityQueue = new PriorityBlockingQueue<MemoKey>();

        // Memo table entries for which new matches were found in the current iteration
        var updatedEntries = Collections.newSetFromMap(new ConcurrentHashMap<MemoEntry, Boolean>());

        // Always match Start at the first position, if any clause depends upon it
        for (var clause : grammar.allClauses) {
            if (clause instanceof Start) {
                priorityQueue.add(new MemoKey(clause, 0));
                // Because clauses are interned, can stop after one instance of Start clause is found
                break;
            }
        }

        // If a lex rule was specified, seed the bottom-up parsing by running the lex rule top-down
        if (grammar.lexClause != null) {
            // Run lex preprocessing step, top-down, from each character position, skipping to end of each
            // subsequent match
            for (int startPos = 0; startPos < input.length();) {
                var memoKey = new MemoKey(grammar.lexClause, startPos);
                // Match the lex rule top-down, populating the memo table for subclause matches
                var match = grammar.lexClause.match(MatchDirection.TOP_DOWN, memoTable, memoKey, input,
                        updatedEntries);
                var matchLen = match != null ? match.len : 0;
                if (match != null) {
                    if (Parser.DEBUG) {
                        System.out.println("Lex match: " + match.toStringWithRuleNames());
                    }
                    // Memoize the subtree of matches, once a lex rule matches 
                    memoTable.addMatchRecursive(match, updatedEntries);
                } else {
                    if (Parser.DEBUG) {
                        System.out.println("Lex rule did not match at input position " + startPos);
                    }
                }
                startPos += Math.max(1, matchLen);
            }
        } else {
            // Find positions that all terminals match, and create the initial active set from parents of terminals,
            // without adding memo table entries for terminals that do not match (no non-matching placeholder needs
            // to be added to the memo table, because the match status of a given terminal at a given position will
            // never change).
            (PARALLELIZE ? grammar.allClauses.parallelStream() : grammar.allClauses.stream())
                    .filter(clause -> clause instanceof Terminal
                            // Don't match Nothing everywhere -- it always matches
                            && !(clause instanceof Nothing))
                    .forEach(clause -> {
                        // Terminals are matched top down
                        for (int startPos = 0; startPos < input.length(); startPos++) {
                            var memoKey = new MemoKey(clause, startPos);
                            var match = clause.match(MatchDirection.TOP_DOWN, memoTable, memoKey, input,
                                    updatedEntries);
                            if (match != null) {
                                if (Parser.DEBUG) {
                                    System.out.println("Initial terminal match: " + match.toStringWithRuleNames());
                                }
                                memoTable.addMatch(match, updatedEntries);
                            }
                            if (clause instanceof Start) {
                                // Only match Start in the first position
                                break;
                            }
                        }
                    });
        }

        // Main parsing loop
        // (need to check if (!updatedEntries.isEmpty()) in the while condition, even though updatedEntries.clear()
        // is called at the end of the loop, because updatedEntries can be populated by lex preprocessing)
        while (!priorityQueue.isEmpty() || !updatedEntries.isEmpty()) {
            // For each updated entry, replace current best match with new best match, and add parent memo keys
            // to the priority queue
            (PARALLELIZE ? updatedEntries.parallelStream() : updatedEntries.stream()).forEach(memoEntry -> {
                memoEntry.updateBestMatch(input, priorityQueue, memoTable.numMatchObjectsMemoized);
            });
            updatedEntries.clear();

            if (!priorityQueue.isEmpty()) {
                // Remove a MemoKey from priority queue (which is ordered from the end of the input to the beginning
                // and from lowest clauses to toplevel clauses), and try matching the MemoKey
                var memoKey = priorityQueue.remove();
//                System.out.println(memoKey.toStringWithRuleNames() + "\t"
//                        + (memoKey.startPos < input.length() ? input.charAt(memoKey.startPos) : "X"));
                memoKey.clause.match(MatchDirection.BOTTOM_UP, memoTable, memoKey, input, updatedEntries);
            }
        }
    }
}
