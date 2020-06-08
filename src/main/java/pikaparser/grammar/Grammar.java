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
package pikaparser.grammar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import pikaparser.clause.Clause;
import pikaparser.clause.aux.RuleRef;
import pikaparser.clause.terminal.Nothing;
import pikaparser.clause.terminal.Terminal;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.utils.GrammarUtils;

/** A grammar. The {@link #parse(String)} method runs the parser on the provided input string. */
public class Grammar {
    /** All rules in the grammar. */
    public final List<Rule> allRules;

    /** A mapping from rule name (with any precedence suffix) to the corresponding {@link Rule}. */
    public Map<String, Rule> ruleNameWithPrecedenceToRule;

    /** All clausesin the grammar. */
    public final List<Clause> allClauses;

    /** If true, print verbose debug output. */
    public static boolean DEBUG = false;

    /** Construct a grammar from a set of rules. */
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
        var priorityQueue = new PriorityQueue<Clause>((c1, c2) -> c1.clauseIdx - c2.clauseIdx);

        var memoTable = new MemoTable(this, input);

        var terminals = allClauses.stream().filter(clause -> clause instanceof Terminal
                // Don't match Nothing everywhere -- it always matches
                && !(clause instanceof Nothing)) //
                .collect(Collectors.toList());

        // Main parsing loop
        for (int startPos = input.length() - 1; startPos >= 0; --startPos) {
            priorityQueue.addAll(terminals);
            while (!priorityQueue.isEmpty()) {
                // Remove a clause from the priority queue (ordered from terminals to toplevel clauses)
                var clause = priorityQueue.remove();
                var memoKey = new MemoKey(clause, startPos);
                var match = clause.match(memoTable, memoKey, input);
                memoTable.addMatch(memoKey, match, priorityQueue);
            }
        }
        return memoTable;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Get a rule by name. */
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

    /** Get all {@link Match} entries for the given clause, indexed by start position. */
    public NavigableMap<Integer, Match> getNavigableMatches(String ruleName, MemoTable memoTable) {
        return memoTable.getNavigableMatches(getRule(ruleName).labeledClause.clause);
    }
}
