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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import pikaparser.ast.LabeledClause;
import pikaparser.clause.Clause;
import pikaparser.clause.aux.RuleRef;
import pikaparser.clause.nonterminal.First;
import pikaparser.clause.terminal.Terminal;
import pikaparser.grammar.Rule;
import pikaparser.grammar.Rule.Associativity;

/** Grammar utils. */
public class GrammarUtils {
    /** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position. */
    private static void findTerminals(Clause clause, HashSet<Clause> visited, List<Clause> terminalsOut) {
        if (visited.add(clause)) {
            if (clause instanceof Terminal) {
                terminalsOut.add(clause);
            } else {
                for (var labeledSubClause : clause.labeledSubClauses) {
                    var subClause = labeledSubClause.clause;
                    findTerminals(subClause, visited, terminalsOut);
                }
            }
        }
    }

    /** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position. */
    private static void findReachableClauses(Clause clause, HashSet<Clause> visited, List<Clause> revTopoOrderOut) {
        if (visited.add(clause)) {
            for (var labeledSubClause : clause.labeledSubClauses) {
                var subClause = labeledSubClause.clause;
                findReachableClauses(subClause, visited, revTopoOrderOut);
            }
            revTopoOrderOut.add(clause);
        }
    }

    /** Find the {@link Clause} nodes that complete a cycle in the grammar. */
    private static void findCycleHeadClauses(Clause clause, Set<Clause> discovered, Set<Clause> finished,
            Set<Clause> cycleHeadClausesOut) {
        if (clause instanceof RuleRef) {
            throw new IllegalArgumentException(
                    "There should not be any " + RuleRef.class.getSimpleName() + " nodes left in grammar");
        }
        discovered.add(clause);
        for (var labeledSubClause : clause.labeledSubClauses) {
            var subClause = labeledSubClause.clause;
            if (discovered.contains(subClause)) {
                // Reached a cycle
                cycleHeadClausesOut.add(subClause);
            } else if (!finished.contains(subClause)) {
                findCycleHeadClauses(subClause, discovered, finished, cycleHeadClausesOut);
            }
        }
        discovered.remove(clause);
        finished.add(clause);
    }

    /** Topologically sort all clauses into bottom-up order, from terminals up to the toplevel clause. */
    public static List<Clause> findClauseTopoSortOrder(Rule topLevelRule, List<Rule> allRules,
            List<Clause> lowestPrecedenceClauses) {
        var allClausesUnordered = new ArrayList<Clause>();
        var topLevelVisited = new HashSet<Clause>();
        
        // Add toplevel rule
        if (topLevelRule != null) {
            allClausesUnordered.add(topLevelRule.labeledClause.clause);
            topLevelVisited.add(topLevelRule.labeledClause.clause);
        }
        
        // Find any other toplevel clauses (clauses that are not a subclause of any other clause)
        for (var rule : allRules) {
            findReachableClauses(rule.labeledClause.clause, topLevelVisited, allClausesUnordered);
        }
        var topLevelClauses = new HashSet<>(allClausesUnordered);
        for (var clause : allClausesUnordered) {
            for (var labeledSubClause : clause.labeledSubClauses) {
                topLevelClauses.remove(labeledSubClause.clause);
            }
        }
        var dfsRoots = new ArrayList<>(topLevelClauses);

        // Add to the end of the list of toplevel clauses all lowest-precedence clauses, since
        // top-down precedence climbing should start at each lowest-precedence clause
        dfsRoots.addAll(lowestPrecedenceClauses);

        // Finally, in case there are cycles in the grammar that are not part of a precedence
        // hierarchy, add to the end of the list of toplevel clauses the set of all "head clauses"
        // of cycles (the set of all clauses reached twice in some path through the grammar)
        var cycleDiscovered = new HashSet<Clause>();
        var cycleFinished = new HashSet<Clause>();
        var cycleHeadClauses = new HashSet<Clause>();
        for (var clause : topLevelClauses) {
            findCycleHeadClauses(clause, cycleDiscovered, cycleFinished, cycleHeadClauses);
        }
        for (var rule : allRules) {
            findCycleHeadClauses(rule.labeledClause.clause, cycleDiscovered, cycleFinished, cycleHeadClauses);
        }
        dfsRoots.addAll(cycleHeadClauses);

        // Topologically sort all clauses into bottom-up order, starting with terminals (so that terminals are
        // all grouped together at the beginning of the list)
        var terminalsVisited = new HashSet<Clause>();
        var terminals = new ArrayList<Clause>();
        for (var rule : allRules) {
            findTerminals(rule.labeledClause.clause, terminalsVisited, terminals);
        }
        var allClauses = new ArrayList<Clause>(terminals);
        var reachableVisited = new HashSet<Clause>(terminals);
        for (var topLevelClause : dfsRoots) {
            findReachableClauses(topLevelClause, reachableVisited, allClauses);
        }

        // Give each clause an index in the topological sort order, bottom-up
        for (int i = 0; i < allClauses.size(); i++) {
            allClauses.get(i).clauseIdx = i;
        }
        return allClauses;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Check there are no cycles in the clauses of rules, before {@link RuleRef} instances are resolved to direct
     * references.
     */
    public static void checkNoRefCycles(Clause clause, String selfRefRuleName, Set<Clause> visited) {
        if (visited.add(clause)) {
            for (var labeledSubClause : clause.labeledSubClauses) {
                var subClause = labeledSubClause.clause;
                checkNoRefCycles(subClause, selfRefRuleName, visited);
            }
        } else {
            throw new IllegalArgumentException(
                    "Rules should not contain cycles when they are created: " + selfRefRuleName);
        }
        visited.remove(clause);
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Count number of self-references among descendant clauses. */
    private static int countRuleSelfReferences(Clause clause, String ruleNameWithoutPrecedence) {
        if (clause instanceof RuleRef && ((RuleRef) clause).refdRuleName.equals(ruleNameWithoutPrecedence)) {
            return 1;
        } else {
            var numSelfRefs = 0;
            for (var labeledSubClause : clause.labeledSubClauses) {
                var subClause = labeledSubClause.clause;
                numSelfRefs += countRuleSelfReferences(subClause, ruleNameWithoutPrecedence);
            }
            return numSelfRefs;
        }
    }

    /** Rewrite self-references into precedence-climbing form. */
    private static int rewriteSelfReferences(Clause clause, Associativity associativity, int numSelfRefsSoFar,
            int numSelfRefs, String selfRefRuleName, boolean isHighestPrec, String currPrecRuleName,
            String nextHighestPrecRuleName) {
        // Terminate recursion when all self-refs have been replaced
        if (numSelfRefsSoFar < numSelfRefs) {
            for (int i = 0; i < clause.labeledSubClauses.length; i++) {
                var labeledSubClause = clause.labeledSubClauses[i];
                var subClause = labeledSubClause.clause;
                if (subClause instanceof RuleRef) {
                    if (((RuleRef) subClause).refdRuleName.equals(selfRefRuleName)) {
                        if (numSelfRefs >= 2) {
                            // Change name of self-references to implement precedence climbing:
                            // For leftmost operand of left-recursive rule:
                            // E[i] <- E X E  =>  E[i] = E[i] X E[i+1]
                            // For rightmost operand of right-recursive rule:
                            // E[i] <- E X E  =>  E[i] = E[i+1] X E[i]
                            // For non-associative rule:
                            // E[i] = E E  =>  E[i] = E[i+1] E[i+1]
                            clause.labeledSubClauses[i].clause = new RuleRef( //
                                    (associativity == Associativity.LEFT && numSelfRefsSoFar == 0)
                                            || (associativity == Associativity.RIGHT
                                                    && numSelfRefsSoFar == numSelfRefs - 1) //
                                                            ? currPrecRuleName
                                                            : nextHighestPrecRuleName);
                        } else /* numSelfRefs == 1 */ {
                            if (!isHighestPrec) {
                                // Move subclause (and its AST node label, if any) inside a First clause that
                                // climbs precedence to the next level:
                                // E[i] <- X E Y  =>  E[i] <- X (E[i] / E[(i+1)%N]) Y
                                ((RuleRef) subClause).refdRuleName = currPrecRuleName;
                                clause.labeledSubClauses[i].clause = new First(subClause,
                                        new RuleRef(nextHighestPrecRuleName));
                            } else {
                                // Except for highest precedence, just defer back to lowest-prec level:
                                // E[N-1] <- '(' E ')'  =>  E[N-1] <- '(' E[0] ')'        
                                ((RuleRef) subClause).refdRuleName = nextHighestPrecRuleName;
                            }
                        }
                        numSelfRefsSoFar++;
                    }
                    // Else don't rewrite the RuleRef, it is not a self-ref
                } else {
                    numSelfRefsSoFar = rewriteSelfReferences(subClause, associativity, numSelfRefsSoFar,
                            numSelfRefs, selfRefRuleName, isHighestPrec, currPrecRuleName, nextHighestPrecRuleName);
                }
                subClause.toStringCached = null;
            }
        }
        return numSelfRefsSoFar;
    }

    /** Rewrite self-references in a precedence hierarchy into precedence-climbing form. */
    public static void handlePrecedence(String ruleNameWithoutPrecedence, List<Rule> rules,
            ArrayList<Clause> lowestPrecedenceClauses,
            Map<String, String> ruleNameToLowestPrecedenceLevelRuleName) {
        // Rewrite rules
        // 
        // For all but the highest precedence level:
        //
        // E[0] <- E (Op E)+  =>  E[0] <- (E[1] (Op E[1])+) / E[1] 
        // E[0,L] <- E Op E   =>  E[0] <- (E[0] Op E[1]) / E[1] 
        // E[0,R] <- E Op E   =>  E[0] <- (E[1] Op E[0]) / E[1]
        // E[3] <- '-' E      =>  E[3] <- '-' (E[3] / E[4]) / E[4]
        //
        // For highest precedence level, next highest precedence wraps back to lowest precedence level:
        //
        // E[5] <- '(' E ')'  =>  E[5] <- '(' E[0] ')'

        // Check there are no duplicate precedence levels
        var precedenceToRule = new TreeMap<Integer, Rule>();
        for (var rule : rules) {
            if (precedenceToRule.put(rule.precedence, rule) != null) {
                throw new IllegalArgumentException("Multiple rules with name " + ruleNameWithoutPrecedence
                        + (rule.precedence == -1 ? "" : " and precedence " + rule.precedence));
            }
        }
        // Get rules in ascending order of precedence
        var precedenceOrder = new ArrayList<>(precedenceToRule.values());

        // Rename rules to include precedence level
        var numPrecedenceLevels = rules.size();
        for (int precedenceIdx = 0; precedenceIdx < numPrecedenceLevels; precedenceIdx++) {
            // Since there is more than one precedence level, update rule name to include precedence
            var rule = precedenceOrder.get(precedenceIdx);
            rule.ruleName += "[" + rule.precedence + "]";
        }

        // Transform grammar rule to handle precence
        for (int precedenceIdx = 0; precedenceIdx < numPrecedenceLevels; precedenceIdx++) {
            var rule = precedenceOrder.get(precedenceIdx);

            // Count the number of self-reference operands
            var numSelfRefs = countRuleSelfReferences(rule.labeledClause.clause, ruleNameWithoutPrecedence);

            var currPrecRuleName = rule.ruleName;
            var nextHighestPrecRuleName = precedenceOrder.get((precedenceIdx + 1) % numPrecedenceLevels).ruleName;

            // If a rule has 2+ self-references, and rule is associative, need rewrite rule for associativity
            var isHighestPrec = precedenceIdx == numPrecedenceLevels - 1;
            if (numSelfRefs >= 1) {
                // Rewrite self-references to higher precedence or left- and right-recursive forms.
                // (the toplevel clause of the rule, rule.labeledClause.clause, can't be a self-reference,
                // since we already checked for that, and IllegalArgumentException would have been thrown.)
                rewriteSelfReferences(rule.labeledClause.clause, rule.associativity, 0, numSelfRefs,
                        ruleNameWithoutPrecedence, isHighestPrec, currPrecRuleName, nextHighestPrecRuleName);
            }

            // Defer to next highest level of precedence if the rule doesn't match, except at the highest level of
            // precedence, which is assumed to be a precedence-breaking pattern (like parentheses), so should not
            // defer back to the lowest precedence level unless the pattern itself matches
            if (!isHighestPrec) {
                // Move rule's toplevel clause (and any AST node label it has) into the first subclause of
                // a First clause that fails over to the next highest precedence level
                var first = new First(rule.labeledClause.clause, new RuleRef(nextHighestPrecRuleName));
                // Move any AST node label down into first subclause of new First clause, so that label doesn't
                // apply to the final failover rule reference
                first.labeledSubClauses[0].astNodeLabel = rule.labeledClause.astNodeLabel;
                rule.labeledClause.astNodeLabel = null;
                // Replace rule clause with new First clause
                rule.labeledClause.clause = first;
            }
        }

        // Map the bare rule name (without precedence suffix) to the lowest precedence level rule name
        var lowestPrecRule = precedenceOrder.get(0);
        lowestPrecedenceClauses.add(lowestPrecRule.labeledClause.clause);
        ruleNameToLowestPrecedenceLevelRuleName.put(ruleNameWithoutPrecedence, lowestPrecRule.ruleName);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Recursively call toString() on the clause tree for this {@link Rule}, so that toString() values are cached
     * before {@link RuleRef} objects are replaced with direct references, and so that shared subclauses are only
     * matched once.
     */
    public static Clause intern(Clause clause, Map<String, Clause> toStringToClause) {
        // Call toString() on (and intern) subclauses, bottom-up
        for (int i = 0; i < clause.labeledSubClauses.length; i++) {
            clause.labeledSubClauses[i].clause = intern(clause.labeledSubClauses[i].clause, toStringToClause);
        }
        // Call toString after recursing to child nodes
        var toStr = clause.toString();

        // Intern the clause based on the toString value
        var prevInternedClause = toStringToClause.putIfAbsent(toStr, clause);

        // Return the previously-interned clause, if present, otherwise the clause, if it was just interned
        return prevInternedClause != null ? prevInternedClause : clause;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Resolve {@link RuleRef} clauses to a reference to the named rule. */
    public static void resolveRuleRefs(LabeledClause labeledClause, Map<String, Rule> ruleNameToRule,
            Map<String, String> ruleNameToLowestPrecedenceLevelRuleName, Set<Clause> visited) {
        if (labeledClause.clause instanceof RuleRef) {
            // Follow a chain of from name in RuleRef objects until a non-RuleRef is reached
            var currLabeledClause = labeledClause;
            var visitedClauses = new HashSet<Clause>();
            while (currLabeledClause.clause instanceof RuleRef) {
                if (!visitedClauses.add(currLabeledClause.clause)) {
                    throw new IllegalArgumentException(
                            "Reached toplevel RuleRef cycle: " + currLabeledClause.clause);
                }
                // Follow a chain of from name in RuleRef objects until a non-RuleRef is reached
                var refdRuleName = ((RuleRef) currLabeledClause.clause).refdRuleName;

                // Check if the rule is the reference to the lowest precedence rule of a precedence hierarchy
                var lowestPrecRuleName = ruleNameToLowestPrecedenceLevelRuleName.get(refdRuleName);

                // Look up Rule based on rule name
                var refdRule = ruleNameToRule.get(lowestPrecRuleName == null ? refdRuleName : lowestPrecRuleName);
                if (refdRule == null) {
                    throw new IllegalArgumentException("Unknown rule name: " + refdRuleName);
                }
                currLabeledClause = refdRule.labeledClause;
            }

            // Set current clause to a direct reference to the referenced rule
            labeledClause.clause = currLabeledClause.clause;

            // Copy across AST node label, if any
            if (labeledClause.astNodeLabel == null) {
                labeledClause.astNodeLabel = currLabeledClause.astNodeLabel;
            }
            // Stop recursing at RuleRef
        } else {
            if (visited.add(labeledClause.clause)) {
                var labeledSubClauses = labeledClause.clause.labeledSubClauses;
                for (var subClauseIdx = 0; subClauseIdx < labeledSubClauses.length; subClauseIdx++) {
                    var labeledSubClause = labeledSubClauses[subClauseIdx];
                    // Recurse through subclause tree if subclause was not a RuleRef 
                    resolveRuleRefs(labeledSubClause, ruleNameToRule, ruleNameToLowestPrecedenceLevelRuleName,
                            visited);
                }
            }
        }
    }
}
