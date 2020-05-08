package pikaparser.grammar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.PriorityBlockingQueue;

import pikaparser.clause.ASTNodeLabel;
import pikaparser.clause.Clause;
import pikaparser.clause.First;
import pikaparser.clause.Nothing;
import pikaparser.clause.RuleRef;
import pikaparser.clause.Start;
import pikaparser.clause.Terminal;
import pikaparser.clause.Clause.MatchDirection;
import pikaparser.grammar.Rule.Associativity;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class Grammar {
    public final List<Rule> allRules;
    public final List<Clause> allClauses;
    public Clause lexClause;
    public Map<String, Rule> ruleNameWithPrecedenceToRule;

    public static boolean DEBUG = false;

    public Grammar(List<Rule> rules) {
        this(/* lexRuleName = */ null, rules);
    }

    public Grammar(String lexRuleName, List<Rule> rules) {
        if (rules.size() == 0) {
            throw new IllegalArgumentException("Grammar must consist of at least one rule");
        }

        // Group rules by name
        Map<String, List<Rule>> ruleNameToRules = new HashMap<>();
        for (var rule : rules) {
            if (rule.ruleName == null) {
                throw new IllegalArgumentException("All rules must be named");
            }
            if (rule.clause instanceof RuleRef && ((RuleRef) rule.clause).refdRuleName.equals(rule.ruleName)) {
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
            checkNoRefCycles(rule.clause, rule.ruleName, new HashSet<Clause>());
        }
        allRules = new ArrayList<>(rules);
        var ruleNameToLowestPrecedenceLevelRuleName = new HashMap<String, String>();
        var lowestPrecedenceClauses = new ArrayList<Clause>();
        for (var ent : ruleNameToRules.entrySet()) {
            // Rewrite rules that have multiple precedence levels, as described in the paper
            var rulesWithName = ent.getValue();
            if (rulesWithName.size() > 1) {
                var ruleName = ent.getKey();
                handlePrecedence(ruleName, rulesWithName, lowestPrecedenceClauses,
                        ruleNameToLowestPrecedenceLevelRuleName);
            }
        }

        // Lift AST node labels into their parent clause' subClauseASTNodeLabel array, or into the rule
        for (var rule : allRules) {
            // Lift AST node label from clause into astNodeLabel field in rule
            while (rule.clause instanceof ASTNodeLabel) {
                if (rule.astNodeLabel == null) {
                    rule.astNodeLabel = ((ASTNodeLabel) rule.clause).astNodeLabel;
                }
                // Drop the ASTNodeLabel node
                rule.clause = rule.clause.subClauses[0];
            }

            // Lift AST node labels from subclauses into subClauseASTNodeLabels array in parent
            liftASTNodeLabels(rule.clause);
        }

        // If there is more than one precedence level for a rule, the handlePrecedence call above modifies
        // rule names to include a precedence suffix, and also adds an all-precedence selector clause with the
        // orgininal rule name. All rule names should now be unique.
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
            rule.clause.registerRule(rule);
        }

        // Intern clauses based on their toString() value, coalescing shared sub-clauses into a DAG, so that
        // effort is not wasted parsing different instances of the same clause multiple times, and so that
        // when a subclause matches, all parent clauses will be added to the active set in the next iteration.
        // Also causes the toString() values to be cached, so that after RuleRefs are replaced with direct
        // Clause references, toString() doesn't get stuck in an infinite loop.
        Map<String, Clause> toStringToClause = new HashMap<>();
        for (var rule : allRules) {
            rule.clause = intern(rule.clause, toStringToClause);
        }

        // Resolve each RuleRef into a direct reference to the referenced clause
        Set<Clause> clausesVisitedResolveRuleRefs = new HashSet<>();
        for (var rule : allRules) {
            resolveRuleRefs(rule, ruleNameWithPrecedenceToRule, ruleNameToLowestPrecedenceLevelRuleName,
                    clausesVisitedResolveRuleRefs);
        }

        if (lexRuleName != null) {
            // Find the toplevel lex rule, if lexRuleName is specified
            var lexRule = ruleNameWithPrecedenceToRule.get(lexRuleName);
            if (lexRule == null) {
                throw new IllegalArgumentException("Unknown lex rule name: " + lexRuleName);
            }
            // Check the lex rule does not contain any cycles
            checkNoDAGCycles(lexRule.clause);
            lexClause = lexRule.clause;
        }

        // Find toplevel clauses (clauses that are not a subclause of any other clause)
        var allClausesUnordered = new ArrayList<Clause>();
        var visited1 = new HashSet<Clause>();
        for (var rule : allRules) {
            findReachableClauses(rule.clause, visited1, allClausesUnordered);
        }
        var topLevelClauses = new HashSet<>(allClausesUnordered);
        for (var clause : allClausesUnordered) {
            for (var subClause : clause.subClauses) {
                topLevelClauses.remove(subClause);
            }
        }
        var topLevelClausesOrdered = new ArrayList<>(topLevelClauses);

        // Add to the end of the list of toplevel clauses all lowest-precedence clauses, since
        // top-down precedence climbing should start at each lowest-precedence clause
        topLevelClausesOrdered.addAll(lowestPrecedenceClauses);

        // Finally, in case there are cycles in the grammar that are not part of a precedence
        // hierarchy, add to the end of the list of toplevel clauses the set of all "head clauses"
        // of cycles, which is the first clause reached in a path around a grammar cycle.
        var discovered = new HashSet<Clause>();
        var finished = new HashSet<Clause>();
        var cycleHeadClauses = new HashSet<Clause>();
        for (var clause : topLevelClauses) {
            findCycleHeadClauses(clause, discovered, finished, cycleHeadClauses);
        }
        for (var rule : allRules) {
            findCycleHeadClauses(rule.clause, discovered, finished, cycleHeadClauses);
        }
        topLevelClausesOrdered.addAll(cycleHeadClauses);

        // Topologically sort all clauses into bottom-up order
        allClauses = new ArrayList<Clause>();
        var visited2 = new HashSet<Clause>();
        for (var topLevelClause : topLevelClausesOrdered) {
            findReachableClauses(topLevelClause, visited2, allClauses);
        }

        // Give each clause an index in the topological sort order, bottom-up
        for (int i = 0; i < allClauses.size(); i++) {
            allClauses.get(i).clauseIdx = i;
        }

        // Find clauses that always match zero or more characters, e.g. FirstMatch(X | Nothing).
        // Importantly, allClauses is in reverse topological order, i.e. traversal is bottom-up.
        for (Clause clause : allClauses) {
            clause.testWhetherCanMatchZeroChars();
        }

        // Find seed parent clauses (in the case of Seq, this depends upon alwaysMatches being set in the prev step)
        for (var clause : allClauses) {
            clause.backlinkToSeedParentClauses();
        }
    }

    /** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position. */
    private static void findReachableClauses(Clause clause, HashSet<Clause> visited, List<Clause> revTopoOrderOut) {
        if (visited.add(clause)) {
            for (var subClause : clause.subClauses) {
                findReachableClauses(subClause, visited, revTopoOrderOut);
            }
            revTopoOrderOut.add(clause);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Label subclause positions with the AST node label from any {@link CreateASTNode} nodes in each subclause
     * position.
     */
    private static void liftASTNodeLabels(Clause clause) {
        for (int subClauseIdx = 0; subClauseIdx < clause.subClauses.length; subClauseIdx++) {
            Clause subClause = clause.subClauses[subClauseIdx];
            if (subClause instanceof ASTNodeLabel) {
                // Copy any AST node labels from subclause node to subClauseASTNodeLabels array within the parent
                var subClauseASTNodeLabel = ((ASTNodeLabel) subClause).astNodeLabel;
                if (subClauseASTNodeLabel != null) {
                    if (clause.subClauseASTNodeLabels == null) {
                        // Alloc array for subclause node labels, if not already done
                        clause.subClauseASTNodeLabels = new String[clause.subClauses.length];
                    }
                    if (clause.subClauseASTNodeLabels[subClauseIdx] == null) {
                        // Update subclause label, if it hasn't already been labeled
                        clause.subClauseASTNodeLabels[subClauseIdx] = subClauseASTNodeLabel;
                    }
                } else {
                    throw new IllegalArgumentException(ASTNodeLabel.class.getSimpleName() + " is null");
                }
                // Remove the ASTNodeLabel node 
                clause.subClauses[subClauseIdx] = subClause.subClauses[0];
            }
            // Recurse
            liftASTNodeLabels(subClause);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Find the {@link Clause} nodes that complete a cycle in the grammar. */
    private static void findCycleHeadClauses(Clause clause, Set<Clause> discovered, Set<Clause> finished,
            Set<Clause> cycleHeadClausesOut) {
        if (clause instanceof RuleRef) {
            throw new IllegalArgumentException(
                    "There should not be any " + RuleRef.class.getSimpleName() + " nodes left in grammar");
        }
        discovered.add(clause);
        for (var subClause : clause.subClauses) {
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

    /**
     * Check a {@link Clause} tree does not contain any cycles after RuleRef instances have been replaced by direct
     * clause references (needed to ensure top-down lexing terminates, since lexing is not memoized).
     */
    private static void checkNoDAGCycles(Clause clause, Set<Clause> discovered, Set<Clause> finished) {
        if (clause instanceof RuleRef) {
            throw new IllegalArgumentException(
                    "There should not be any " + RuleRef.class.getSimpleName() + " nodes left in grammar");
        }
        discovered.add(clause);
        for (var subClause : clause.subClauses) {
            if (discovered.contains(subClause)) {
                throw new IllegalArgumentException("Lex rule's clause tree contains a cycle at " + subClause);
            }
            if (!finished.contains(subClause)) {
                checkNoDAGCycles(subClause, discovered, finished);
            }
        }
        discovered.remove(clause);
        finished.add(clause);
    }

    /** Check a {@link Clause} tree does not contain any cycles (needed for top-down lex). */
    private static void checkNoDAGCycles(Clause clause) {
        checkNoDAGCycles(clause, new HashSet<Clause>(), new HashSet<Clause>());
    }

    private static void checkNoRefCycles(Clause clause, String selfRefRuleName, Set<Clause> visited) {
        if (visited.add(clause)) {
            for (Clause subClause : clause.subClauses) {
                checkNoRefCycles(subClause, selfRefRuleName, visited);
            }
        } else {
            throw new IllegalArgumentException(
                    "Rules should not contain cycles when they are created: " + selfRefRuleName);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Count number of self-references among descendant clauses. */
    private static int countRuleSelfReferences(Clause clause, String ruleNameWithoutPrecedence) {
        if (clause instanceof RuleRef && ((RuleRef) clause).refdRuleName.equals(ruleNameWithoutPrecedence)) {
            return 1;
        } else {
            var numSelfRefs = 0;
            for (var subClause : clause.subClauses) {
                numSelfRefs += countRuleSelfReferences(subClause, ruleNameWithoutPrecedence);
            }
            return numSelfRefs;
        }
    }

    private static int rewriteSelfReferences(Clause clause, Associativity associativity, int numSelfRefsSoFar,
            int numSelfRefs, String selfRefRuleName, String currPrecRuleName, String nextHighestPrecRuleName) {
        if (clause instanceof RuleRef && ((RuleRef) clause).refdRuleName.equals(selfRefRuleName)) {
            // For leftmost self-ref of a left-associative rule, or rightmost self-ref of a right-associative rule,
            // replace self-reference with a reference to the same precedence level; for all other self-references
            // and when there is no specified precedence, replace self-references with a reference to the next
            // highest precedence level.
            var referToCurrPrecLevel = associativity == Associativity.LEFT && numSelfRefsSoFar == 0
                    || associativity == Associativity.RIGHT && numSelfRefsSoFar == numSelfRefs - 1;
            ((RuleRef) clause).refdRuleName = referToCurrPrecLevel ? currPrecRuleName : nextHighestPrecRuleName;
            return numSelfRefsSoFar + 1;
        } else {
            var numSelfRefsCumul = numSelfRefsSoFar;
            for (var subClause : clause.subClauses) {
                numSelfRefsCumul = rewriteSelfReferences(subClause, associativity, numSelfRefsCumul, numSelfRefs,
                        selfRefRuleName, currPrecRuleName, nextHighestPrecRuleName);
            }
            return numSelfRefsCumul;
        }
    }

    private static boolean rewriteSelfReference(Clause clause, String selfRefRuleName, String currPrecRuleName,
            String nextHighestPrecRuleName) {
        for (int i = 0; i < clause.subClauses.length; i++) {
            var subClause = clause.subClauses[i];
            if (subClause instanceof RuleRef && ((RuleRef) subClause).refdRuleName.equals(selfRefRuleName)) {
                // E[i] <- X E Y  =>  E[i] <- X (E[i] / E[(i+1)%N]) Y
                clause.subClauses[i] = new First(new RuleRef(currPrecRuleName),
                        new RuleRef(nextHighestPrecRuleName));
                // Break out of recursion, since there is only one self-reference
                return true;
            } else {
                if (rewriteSelfReference(subClause, selfRefRuleName, currPrecRuleName, nextHighestPrecRuleName)) {
                    // Break out of recursion
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Rewrite precedence levels.
     */
    private static void handlePrecedence(String ruleNameWithoutPrecedence, List<Rule> rules,
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
        // E[5] <- '(' E ')'  =>  E[5] <- '(' (E[5] / E[0]) ')'

        // Check there are no duplicate precedence levels
        var precedenceToRule = new TreeMap<Integer, Rule>();
        for (var rule : rules) {
            if (precedenceToRule.put(rule.precedence, rule) != null) {
                throw new IllegalArgumentException("Multiple rules with name " + ruleNameWithoutPrecedence
                        + " and precedence " + rule.precedence);
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

        // Make precedence levels do not just contain a self-reference and nothing else
        for (int precedenceIdx = 0; precedenceIdx < numPrecedenceLevels; precedenceIdx++) {
            var rule = precedenceOrder.get(precedenceIdx);
            if (rule.clause instanceof RuleRef
                    && ((RuleRef) rule.clause).refdRuleName.equals(ruleNameWithoutPrecedence)) {
                throw new IllegalArgumentException(
                        "Rule " + rule.ruleName + " should not contain only a self-reference to "
                                + ruleNameWithoutPrecedence + " and nothing else");
            }
        }

        // Transform grammar rule to handle precence
        for (int precedenceIdx = 0; precedenceIdx < numPrecedenceLevels; precedenceIdx++) {
            var rule = precedenceOrder.get(precedenceIdx);

            // Count the number of self-reference operands
            var numSelfRefs = countRuleSelfReferences(rule.clause, ruleNameWithoutPrecedence);

            var currPrecRuleName = rule.ruleName;
            var nextHighestPrecRuleName = precedenceOrder.get((precedenceIdx + 1) % numPrecedenceLevels).ruleName;

            // If a rule has 2+ self-references, and rule is associative, need rewrite rule for associativity
            if (numSelfRefs >= 2) {
                // Rewrite self-references to higher precedence or left- and right-recursive forms
                rewriteSelfReferences(rule.clause, rule.associativity, 0, numSelfRefs, ruleNameWithoutPrecedence,
                        currPrecRuleName, nextHighestPrecRuleName);
            } else if (numSelfRefs == 1) {
                // If there is only one self-reference, replace it with a reference to the next highest
                // level of precedence
                rewriteSelfReference(rule.clause, ruleNameWithoutPrecedence, currPrecRuleName,
                        nextHighestPrecRuleName);
            }

            // Defer to next highest level of precedence if the rule doesn't match, except at the highest level of
            // precedence, which is assumed to be a precedence-breaking pattern (like parentheses), so should not
            // defer back to the lowest precedence level unless the pattern itself matches
            if (precedenceIdx < numPrecedenceLevels - 1) {
                rule.clause = new First(rule.clause, new RuleRef(nextHighestPrecRuleName));
            }
        }

        // Map the bare rule name (without precedence suffix) to the lowest precedence level rule name
        var lowestPrecRule = precedenceOrder.get(0);
        lowestPrecedenceClauses.add(lowestPrecRule.clause);
        ruleNameToLowestPrecedenceLevelRuleName.put(ruleNameWithoutPrecedence, lowestPrecRule.ruleName);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Recursively call toString() on the clause tree for this {@link Rule}, so that toString() values are cached
     * before {@link RuleRef} objects are replaced with direct references, and so that shared subclauses are only
     * matched once.
     */
    private static Clause intern(Clause clause, Map<String, Clause> toStringToClause) {
        // Call toString() on (and intern) subclauses, bottom-up
        for (int i = 0; i < clause.subClauses.length; i++) {
            clause.subClauses[i] = intern(clause.subClauses[i], toStringToClause);
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
    private static void resolveRuleRefs(Clause clause, Map<String, Rule> ruleNameToRule,
            Map<String, String> ruleNameToLowestPrecedenceLevelRuleName, Set<Clause> visited) {
        if (visited.add(clause)) {
            for (int subClauseIdx = 0; subClauseIdx < clause.subClauses.length; subClauseIdx++) {
                Clause subClause = clause.subClauses[subClauseIdx];
                if (subClause instanceof RuleRef) {
                    // Look up rule from name in RuleRef
                    String refdRuleName = ((RuleRef) subClause).refdRuleName;

                    // Set current clause to a direct reference to the referenced rule
                    var lowestPrecRuleName = ruleNameToLowestPrecedenceLevelRuleName.get(refdRuleName);
                    var refdRule = ruleNameToRule
                            .get(lowestPrecRuleName != null ? lowestPrecRuleName : refdRuleName);
                    if (refdRule == null) {
                        throw new IllegalArgumentException("Unknown rule name: " + refdRuleName);
                    }
                    clause.subClauses[subClauseIdx] = refdRule.clause;

                    // Copy across AST node label, if any
                    if (refdRule.astNodeLabel != null) {
                        if (clause.subClauseASTNodeLabels == null) {
                            // Alloc array for subclause node labels, if not already done
                            clause.subClauseASTNodeLabels = new String[clause.subClauses.length];
                        }
                        if (clause.subClauseASTNodeLabels[subClauseIdx] == null) {
                            // Update subclause label, if it hasn't already been labeled
                            clause.subClauseASTNodeLabels[subClauseIdx] = refdRule.astNodeLabel;
                        }
                    }
                    // Stop recursing at RuleRef
                } else {
                    // Recurse through subclause tree if subclause was not a RuleRef 
                    resolveRuleRefs(subClause, ruleNameToRule, ruleNameToLowestPrecedenceLevelRuleName, visited);
                }
            }
        }
    }

    /** Resolve {@link RuleRef} clauses to a reference to the named rule. */
    private static void resolveRuleRefs(Rule rule, Map<String, Rule> ruleNameToRule,
            Map<String, String> ruleNameToLowestPrecedenceLevelRuleName, Set<Clause> visited) {
        if (rule.clause instanceof RuleRef) {
            // Follow a chain of toplevel RuleRef instances
            Set<Clause> chainVisited = new HashSet<>();
            var currClause = rule.clause;
            while (currClause instanceof RuleRef) {
                if (!chainVisited.add(currClause)) {
                    throw new IllegalArgumentException(
                            "Cycle in " + RuleRef.class.getSimpleName() + " references for rule " + rule.ruleName);
                }
                // Look up rule clause from name in RuleRef
                String refdRuleName = ((RuleRef) currClause).refdRuleName;

                // Referenced rule group is the same as the current rule group, and there is only one level
                // of precedence for rule group: R <- R
                if (refdRuleName.equals(rule.ruleName)) {
                    throw new IllegalArgumentException("Rule references only itself: " + rule.ruleName);
                }

                // Use lowest precedence level for rule, if rule refers to a rule with multiple precedence levels
                var lowestPrecRuleName = ruleNameToLowestPrecedenceLevelRuleName.get(refdRuleName);
                var refdRule = ruleNameToRule.get(lowestPrecRuleName != null ? lowestPrecRuleName : refdRuleName);
                if (refdRule == null) {
                    throw new IllegalArgumentException("Unknown rule name: " + refdRuleName);
                }

                // Else Set current clause to the base clause of the referenced rule
                currClause = refdRule.clause;

                // If the referenced rule creates an AST node, add the AST node label to the rule
                if (rule.astNodeLabel == null) {
                    rule.astNodeLabel = refdRule.astNodeLabel;
                }

                // Record rule name in the rule's toplevel clause, for toString
                currClause.registerRule(rule);
            }

            // Overwrite RuleRef with direct reference to the named rule 
            rule.clause = currClause;

        } else {
            // Recurse through subclause tree if toplevel clause was not a RuleRef 
            resolveRuleRefs(rule.clause, ruleNameToRule, ruleNameToLowestPrecedenceLevelRuleName, visited);
        }
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
    public List<Match> getNonOverlappingMatches(MemoTable memoTable, String ruleName, int precedence) {
        var clause = getRule(ruleName).clause;
        return memoTable.getNonOverlappingMatches(clause);
    }

    /**
     * Get the {@link Match} entries for all nonoverlapping matches of the named rule, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    public List<Match> getNonOverlappingMatches(MemoTable memoTable, String ruleName) {
        return getNonOverlappingMatches(memoTable, ruleName, 0);
    }

    /**
     * Get the {@link Match} entries for all postions where a match was queried for the named rule, but there was no
     * match.
     */
    public List<Integer> getNonMatches(MemoTable memoTable, String ruleName, int precedence) {
        var clause = getRule(ruleName).clause;
        return memoTable.getNonMatchPositions(clause);
    }

    /**
     * Get the {@link Match} entries for all postions where a match was queried for the named rule, but there was no
     * match.
     */
    public List<Integer> getNonMatches(MemoTable memoTable, String ruleName) {
        return getNonMatches(memoTable, ruleName, 0);
    }

    private static void addRange(int startPos, int endPos, String input,
            TreeMap<Integer, Entry<Integer, String>> ranges) {
        // Try merging new range with floor entry in TreeMap
        var floorEntry = ranges.floorEntry(startPos);
        var floorEntryStart = floorEntry == null ? null : floorEntry.getKey();
        var floorEntryEnd = floorEntry == null ? null : floorEntry.getValue().getKey();
        int newEntryRangeStart;
        int newEntryRangeEnd;
        if (floorEntryStart == null || floorEntryEnd < startPos) {
            // There is no startFloorEntry, or startFloorEntry ends before startPos -- add a new entry
            newEntryRangeStart = startPos;
            newEntryRangeEnd = endPos;
        } else {
            // startFloorEntry overlaps with range -- extend startFloorEntry
            newEntryRangeStart = floorEntryStart;
            newEntryRangeEnd = Math.max(floorEntryEnd, endPos);
        }
        var newEntryMatchStr = input.substring(startPos, endPos);
        ranges.put(newEntryRangeStart, new SimpleEntry<>(newEntryRangeEnd, newEntryMatchStr));

        // Try merging new range with the following entry in TreeMap
        var higherEntry = ranges.higherEntry(newEntryRangeStart);
        var higherEntryStart = higherEntry == null ? null : higherEntry.getKey();
        var higherEntryEnd = higherEntry == null ? null : higherEntry.getValue().getKey();
        if (higherEntryStart != null && higherEntryStart <= newEntryRangeEnd) {
            // Expanded-range entry overlaps with the following entry -- collapse them into one
            ranges.remove(higherEntryStart);
            var expandedRangeEnd = Math.max(newEntryRangeEnd, higherEntryEnd);
            var expandedRangeMatchStr = input.substring(newEntryRangeStart, expandedRangeEnd);
            ranges.put(newEntryRangeStart, new SimpleEntry<>(expandedRangeEnd, expandedRangeMatchStr));
        }
    }

    /**
     * Get any syntax errors in the parse, as a map from start position to a tuple, (end position, span of input
     * string between start position and end position).
     */
    public TreeMap<Integer, Entry<Integer, String>> getSyntaxErrors(MemoTable memoTable, String input,
            String... syntaxCoverageRuleNames) {
        // Find the range of characters spanned by matches for each of the coverageRuleNames
        var parsedRanges = new TreeMap<Integer, Entry<Integer, String>>();
        for (var coverageRuleName : syntaxCoverageRuleNames) {
            for (var match : getNonOverlappingMatches(memoTable, coverageRuleName)) {
                addRange(match.memoKey.startPos, match.memoKey.startPos + match.len, input, parsedRanges);
            }
        }
        // Find the inverse of the spanned ranges -- these are the syntax errors
        var syntaxErrorRanges = new TreeMap<Integer, Entry<Integer, String>>();
        int prevEndPos = 0;
        for (var ent : parsedRanges.entrySet()) {
            var currStartPos = ent.getKey();
            var currEndPos = ent.getValue().getKey();
            if (currStartPos > prevEndPos) {
                syntaxErrorRanges.put(prevEndPos,
                        new SimpleEntry<>(currStartPos, input.substring(prevEndPos, currStartPos)));
            }
            prevEndPos = currEndPos;
        }
        if (!parsedRanges.isEmpty()) {
            var lastEnt = parsedRanges.lastEntry();
            var lastEntEndPos = lastEnt.getValue().getKey();
            if (lastEntEndPos < input.length()) {
                // Add final syntax error range, if there is one
                syntaxErrorRanges.put(lastEntEndPos,
                        new SimpleEntry<>(input.length(), input.substring(lastEntEndPos, input.length())));
            }
        }
        return syntaxErrorRanges;
    }

    // -------------------------------------------------------------------------------------------------------------

    public MemoTable parse(String input) {
        // The memo table
        var memoTable = new MemoTable();

        // A set of MemoKey instances for entries that need matching.
        // Use PriorityBlockingQueue, since memo table initialization is parallelized,
        // and multiple threads will concurrently add parent matches to the priority queue.
        var priorityQueue = new PriorityBlockingQueue<MemoKey>();

        // Always match Start at the first position, if any clause depends upon it
        for (var clause : allClauses) {
            if (clause instanceof Start) {
                priorityQueue.add(new MemoKey(clause, 0));
                // Because clauses are interned, can stop after one instance of Start clause is found
                break;
            }
        }

        // If a lex rule was specified, seed the bottom-up parsing by running the lex rule top-down
        if (lexClause != null) {
            // Run lex preprocessing step, top-down, from each character position, skipping to end of each
            // subsequent match
            for (int startPos = 0; startPos < input.length();) {
                var memoKey = new MemoKey(lexClause, startPos);
                // Match the lex rule top-down, populating the memo table for subclause matches
                var match = lexClause.match(MatchDirection.TOP_DOWN, memoTable, memoKey, input);
                var matchLen = match != null ? match.len : 0;
                if (match != null) {
                    if (DEBUG) {
                        System.out.println("Lex match: " + match.toStringWithRuleNames());
                    }
                    // Memoize the subtree of matches, once a lex rule matches 
                    memoTable.addMatchRecursive(match, priorityQueue);
                } else {
                    if (DEBUG) {
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
            allClauses.parallelStream() //
                    .filter(clause -> clause instanceof Terminal
                            // Don't match Nothing everywhere -- it always matches
                            && !(clause instanceof Nothing))
                    .forEach(clause -> {
                        // Terminals are matched top down
                        for (int startPos = 0; startPos < input.length(); startPos++) {
                            var memoKey = new MemoKey(clause, startPos);
                            var match = clause.match(MatchDirection.TOP_DOWN, memoTable, memoKey, input);
                            if (match != null) {
                                if (DEBUG) {
                                    System.out.println("Initial terminal match: " + match.toStringWithRuleNames());
                                }
                                memoTable.addMatch(match, priorityQueue);
                            }
                            if (clause instanceof Start) {
                                // Only match Start in the first position
                                break;
                            }
                        }
                    });
        }

        // Main parsing loop
        while (!priorityQueue.isEmpty()) {
            // Remove a MemoKey from priority queue (which is ordered from the end of the input to the beginning
            // and from lowest clauses to toplevel clauses), and try matching the MemoKey bottom-up
            var memoKey = priorityQueue.remove();
            var match = memoKey.clause.match(MatchDirection.BOTTOM_UP, memoTable, memoKey, input);
            if (match != null) {
                // Memoize any new match, and schedule parent clauses for evaluation in the priority queue
                memoTable.addMatch(match, priorityQueue);

                if (DEBUG) {
                    System.out.println("Matched: " + memoKey.toStringWithRuleNames());
                }
            } else if (DEBUG) {
                System.out.println("Failed to match: " + memoKey.toStringWithRuleNames());
            }
        }
        return memoTable;
    }
}
