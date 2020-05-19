package pikaparser.grammar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;

import pikaparser.clause.Clause;
import pikaparser.clause.aux.RuleRef;
import pikaparser.clause.terminal.Nothing;
import pikaparser.clause.terminal.Start;
import pikaparser.clause.terminal.Terminal;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoEntry;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.utils.GrammarUtils;

public class Grammar {
    public final List<Rule> allRules;
    public final List<Clause> allClauses;
    public Map<String, Rule> ruleNameWithPrecedenceToRule;

    public static boolean DEBUG = true;

    public Grammar(List<Rule> rules) {
        if (rules.size() == 0) {
            throw new IllegalArgumentException("Grammar must consist of at least one rule");
        }

        // Group rules by name
        Map<String, List<Rule>> ruleNameToRules = new HashMap<>();
        for (var rule : rules) {
            if (rule.ruleName == null) {
                throw new IllegalArgumentException("All rules must be named");
            }
            if (rule.labeledClause.clause instanceof RuleRef
                    && ((RuleRef) rule.labeledClause.clause).refdRuleName.equals(rule.ruleName)) {
                // Make sure rule doesn't refer only to itself
                throw new IllegalArgumentException(
                        "Rule cannot refer to only itself: " + rule.ruleName + "[" + rule.precedence + "]");
            }
            var rulesWithName = ruleNameToRules.get(rule.ruleName);
            if (rulesWithName == null) {
                ruleNameToRules.put(rule.ruleName, rulesWithName = new ArrayList<>());
            }
            rulesWithName.add(rule);

            // Make sure there are no cycles in the grammar before RuleRef instances have been replaced
            // with direct references (checking once up front simplifies other recursive routines, so that
            // they don't have to check for infinite recursion)
            GrammarUtils.checkNoRefCycles(rule.labeledClause.clause, rule.ruleName, new HashSet<Clause>());
        }
        allRules = new ArrayList<>(rules);
        var ruleNameToLowestPrecedenceLevelRuleName = new HashMap<String, String>();
        var lowestPrecedenceClauses = new ArrayList<Clause>();
        for (var ent : ruleNameToRules.entrySet()) {
            // Rewrite rules that have multiple precedence levels, as described in the paper
            var rulesWithName = ent.getValue();
            if (rulesWithName.size() > 1) {
                var ruleName = ent.getKey();
                GrammarUtils.handlePrecedence(ruleName, rulesWithName, lowestPrecedenceClauses,
                        ruleNameToLowestPrecedenceLevelRuleName);
            }
        }

        // If there is more than one precedence level for a rule, the handlePrecedence call above modifies
        // rule names to include a precedence suffix, and also adds an all-precedence selector clause with the
        // original rule name. All rule names should now be unique.
        ruleNameWithPrecedenceToRule = new HashMap<>();
        for (var rule : allRules) {
            // The handlePrecedence call above added the precedence to the rule name as a suffix
            if (ruleNameWithPrecedenceToRule.put(rule.ruleName, rule) != null) {
                // Should not happen
                throw new IllegalArgumentException("Duplicate rule name " + rule.ruleName);
            }
        }

        // Register each rule with its toplevel clause (used in the clause's toString() method)
        for (var rule : allRules) {
            rule.labeledClause.clause.registerRule(rule);
        }

        // Intern clauses based on their toString() value, coalescing shared sub-clauses into a DAG, so that
        // effort is not wasted parsing different instances of the same clause multiple times, and so that
        // when a subclause matches, all parent clauses will be added to the active set in the next iteration.
        // Also causes the toString() values to be cached, so that after RuleRefs are replaced with direct
        // Clause references, toString() doesn't get stuck in an infinite loop.
        Map<String, Clause> toStringToClause = new HashMap<>();
        for (var rule : allRules) {
            rule.labeledClause.clause = GrammarUtils.intern(rule.labeledClause.clause, toStringToClause);
        }

        // Resolve each RuleRef into a direct reference to the referenced clause
        Set<Clause> clausesVisitedResolveRuleRefs = new HashSet<>();
        for (var rule : allRules) {
            GrammarUtils.resolveRuleRefs(rule.labeledClause, ruleNameWithPrecedenceToRule,
                    ruleNameToLowestPrecedenceLevelRuleName, clausesVisitedResolveRuleRefs);
        }

        // Topologically sort clauses, bottom-up, placing the result in allClauses
        allClauses = GrammarUtils.findClauseTopoSortOrder(allRules, lowestPrecedenceClauses);

        // Find clauses that always match zero or more characters, e.g. FirstMatch(X | Nothing).
        // Importantly, allClauses is in reverse topological order, i.e. traversal is bottom-up.
        for (Clause clause : allClauses) {
            clause.determineWhetherCanMatchZeroChars();
        }

        // Find seed parent clauses (in the case of Seq, this depends upon alwaysMatches being set in the prev step)
        for (var clause : allClauses) {
            clause.addAsSeedParentClause();
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Main parsing method. */
    public MemoTable parse(String input) {
        // The memo table
        var memoTable = new MemoTable();

        // A set of MemoKey instances for entries that need matching.
        // Uses the concurrent PriorityBlockingQueue, since memo table initialization is parallelized.
        var priorityQueue = new PriorityBlockingQueue<MemoKey>();

        // Always match Start at the first position, if any clause depends upon it
        for (var clause : allClauses) {
            if (clause instanceof Start) {
                priorityQueue.add(new MemoKey(clause, 0));
                // Because clauses are interned, can stop after one instance of Start clause is found
                break;
            }
        }

        // Find positions that all terminals match, and create the initial active set from parents of terminals,
        // without adding memo table entries for terminals that do not match (no non-matching placeholder needs
        // to be added to the memo table, because the match status of a given terminal at a given position will
        // never change).
        allClauses.parallelStream() //
                .filter(clause -> clause instanceof Terminal
                        // Don't match Nothing everywhere -- it always matches
                        && !(clause instanceof Nothing))
                .forEach(clause -> {
                    for (int startPos = 0; startPos < input.length(); startPos++) {
                        // Terminals ignore the MatchDirection parameter
                        var memoKey = new MemoKey(clause, startPos);
                        var match = clause.match(memoTable, memoKey, input);
                        memoTable.addMatch(memoKey, match, priorityQueue);
                    }
                });

        // Main parsing loop
        while (!priorityQueue.isEmpty()) {
            // Remove a MemoKey from priority queue (which is ordered from the end of the input to the beginning
            // and from lowest clauses to toplevel clauses), and try matching the MemoKey bottom-up
            var memoKey = priorityQueue.remove();
            var match = memoKey.clause.match(memoTable, memoKey, input);
            memoTable.addMatch(memoKey, match, priorityQueue);
        }
        return memoTable;
    }

    // -------------------------------------------------------------------------------------------------------------

    public Rule getRule(String ruleNameWithPrecedence) {
        var rule = ruleNameWithPrecedenceToRule.get(ruleNameWithPrecedence);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown rule name: " + ruleNameWithPrecedence);
        }
        return rule;
    }

    /**
     * Get the {@link Match} entries for all nonoverlapping matches of the named rule, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    public List<Match> getNonOverlappingMatches(String ruleName, MemoTable memoTable) {
        return memoTable.getNonOverlappingMatches(getRule(ruleName).labeledClause.clause);
    }

    /**
     * Get the {@link Match} entries for all postions where a match was queried for the named rule, but there was no
     * match.
     */
    public List<Integer> getNonMatches(String ruleName, MemoTable memoTable) {
        return memoTable.getNonMatchPositions(getRule(ruleName).labeledClause.clause);
    }

    /** Get all {@link MemoEntry} entries for the given clause, indexed by start position. */
    public NavigableMap<Integer, MemoEntry> getNavigableMatches(String ruleName, MemoTable memoTable) {
        return memoTable.getNavigableMatches(getRule(ruleName).labeledClause.clause);
    }
}
