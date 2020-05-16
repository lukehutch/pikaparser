package pikaparser.grammar;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.PriorityBlockingQueue;

import pikaparser.clause.Clause;
import pikaparser.clause.aux.RuleRef;
import pikaparser.clause.nonterminal.First;
import pikaparser.clause.terminal.Nothing;
import pikaparser.clause.terminal.Start;
import pikaparser.clause.terminal.Terminal;
import pikaparser.clause.util.LabeledClause;
import pikaparser.grammar.Rule.Associativity;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoEntry;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class Grammar {
    public final List<Rule> allRules;
    public final List<Clause> allClauses;
    public Map<String, Rule> ruleNameWithPrecedenceToRule;

    public static boolean DEBUG = false;

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
            checkNoRefCycles(rule.labeledClause.clause, rule.ruleName, new HashSet<Clause>());
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
            rule.labeledClause.clause.registerRule(rule);
        }

        // Intern clauses based on their toString() value, coalescing shared sub-clauses into a DAG, so that
        // effort is not wasted parsing different instances of the same clause multiple times, and so that
        // when a subclause matches, all parent clauses will be added to the active set in the next iteration.
        // Also causes the toString() values to be cached, so that after RuleRefs are replaced with direct
        // Clause references, toString() doesn't get stuck in an infinite loop.
        Map<String, Clause> toStringToClause = new HashMap<>();
        for (var rule : allRules) {
            rule.labeledClause.clause = intern(rule.labeledClause.clause, toStringToClause);
        }

        // Resolve each RuleRef into a direct reference to the referenced clause
        Set<Clause> clausesVisitedResolveRuleRefs = new HashSet<>();
        for (var rule : allRules) {
            resolveRuleRefs(rule.labeledClause, ruleNameWithPrecedenceToRule,
                    ruleNameToLowestPrecedenceLevelRuleName, clausesVisitedResolveRuleRefs);
        }

        // Find toplevel clauses (clauses that are not a subclause of any other clause)
        var allClausesUnordered = new ArrayList<Clause>();
        var visited1 = new HashSet<Clause>();
        for (var rule : allRules) {
            findReachableClauses(rule.labeledClause.clause, visited1, allClausesUnordered);
        }
        var topLevelClauses = new HashSet<>(allClausesUnordered);
        for (var clause : allClausesUnordered) {
            for (var labeledSubClause : clause.labeledSubClauses) {
                topLevelClauses.remove(labeledSubClause.clause);
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
            findCycleHeadClauses(rule.labeledClause.clause, discovered, finished, cycleHeadClauses);
        }
        topLevelClausesOrdered.addAll(cycleHeadClauses);

        // Topologically sort all clauses into bottom-up order, starting with terminals (so that terminals are
        // all grouped together at the beginning of the list)
        var visited = new HashSet<Clause>();
        var terminals = new ArrayList<Clause>();
        for (var rule : allRules) {
            findTerminals(rule.labeledClause.clause, visited, terminals);
        }
        allClauses = new ArrayList<Clause>(terminals);
        visited.clear();
        visited.addAll(terminals);
        for (var topLevelClause : topLevelClausesOrdered) {
            findReachableClauses(topLevelClause, visited, allClauses);
        }

        // Give each clause an index in the topological sort order, bottom-up
        for (int i = 0; i < allClauses.size(); i++) {
            allClauses.get(i).clauseIdx = i;
        }

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

    // -------------------------------------------------------------------------------------------------------------

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

    private static void checkNoRefCycles(Clause clause, String selfRefRuleName, Set<Clause> visited) {
        if (visited.add(clause)) {
            for (var labeledSubClause : clause.labeledSubClauses) {
                var subClause = labeledSubClause.clause;
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
            for (var labeledSubClause : clause.labeledSubClauses) {
                var subClause = labeledSubClause.clause;
                numSelfRefs += countRuleSelfReferences(subClause, ruleNameWithoutPrecedence);
            }
            return numSelfRefs;
        }
    }

    private static int rewriteSelfReferences(Clause clause, Associativity associativity, int numSelfRefsSoFar,
            int numSelfRefs, String selfRefRuleName, String currPrecRuleName, String nextHighestPrecRuleName) {
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
                            // Move subclause (and its AST node label, if any) inside a First clause that
                            // climbs precedence to the next level:
                            // E[i] <- X E Y  =>  E[i] <- X (E[i] / E[(i+1)%N]) Y
                            ((RuleRef) subClause).refdRuleName = currPrecRuleName;
                            clause.labeledSubClauses[i].clause = new First(subClause,
                                    new RuleRef(nextHighestPrecRuleName));
                        }
                        numSelfRefsSoFar++;
                    }
                    // Else don't rewrite the RuleRef, it is not a self-ref
                } else {
                    numSelfRefsSoFar = rewriteSelfReferences(subClause, associativity, numSelfRefsSoFar,
                            numSelfRefs, selfRefRuleName, currPrecRuleName, nextHighestPrecRuleName);
                }
            }
        }
        return numSelfRefsSoFar;
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

        // Transform grammar rule to handle precence
        for (int precedenceIdx = 0; precedenceIdx < numPrecedenceLevels; precedenceIdx++) {
            var rule = precedenceOrder.get(precedenceIdx);

            // Count the number of self-reference operands
            var numSelfRefs = countRuleSelfReferences(rule.labeledClause.clause, ruleNameWithoutPrecedence);

            var currPrecRuleName = rule.ruleName;
            var nextHighestPrecRuleName = precedenceOrder.get((precedenceIdx + 1) % numPrecedenceLevels).ruleName;

            // If a rule has 2+ self-references, and rule is associative, need rewrite rule for associativity
            if (numSelfRefs >= 1) {
                // Rewrite self-references to higher precedence or left- and right-recursive forms.
                // (the toplevel clause of the rule, rule.labeledClause.clause, can't be a self-reference,
                // since we already checked for that, and IllegalArgumentException would have been thrown.)
                rewriteSelfReferences(rule.labeledClause.clause, rule.associativity, 0, numSelfRefs,
                        ruleNameWithoutPrecedence, currPrecRuleName, nextHighestPrecRuleName);
            }

            // Defer to next highest level of precedence if the rule doesn't match, except at the highest level of
            // precedence, which is assumed to be a precedence-breaking pattern (like parentheses), so should not
            // defer back to the lowest precedence level unless the pattern itself matches
            if (precedenceIdx < numPrecedenceLevels - 1) {
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
    private static Clause intern(Clause clause, Map<String, Clause> toStringToClause) {
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
    private static void resolveRuleRefs(LabeledClause labeledClause, Map<String, Rule> ruleNameToRule,
            Map<String, String> ruleNameToLowestPrecedenceLevelRuleName, Set<Clause> visited) {
        if (labeledClause.clause instanceof RuleRef) {
            // Look up rule from name in RuleRef
            String refdRuleName = ((RuleRef) labeledClause.clause).refdRuleName;

            // Check if the rule is the reference to the lowest precedence rule of a precedence hierarchy
            var lowestPrecRuleName = ruleNameToLowestPrecedenceLevelRuleName.get(refdRuleName);

            // Look up Rule based on rule name
            var refdRule = ruleNameToRule.get(lowestPrecRuleName == null ? refdRuleName : lowestPrecRuleName);
            if (refdRule == null) {
                throw new IllegalArgumentException("Unknown rule name: " + refdRuleName);
            }

            // Set current clause to a direct reference to the referenced rule
            labeledClause.clause = refdRule.labeledClause.clause;

            // Copy across AST node label, if any
            if (labeledClause.astNodeLabel == null) {
                labeledClause.astNodeLabel = refdRule.labeledClause.astNodeLabel;
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
            var rule = ruleNameWithPrecedenceToRule.get(coverageRuleName);
            if (rule != null) {
                for (var match : memoTable.getNonOverlappingMatches(rule.labeledClause.clause)) {
                    addRange(match.memoKey.startPos, match.memoKey.startPos + match.len, input, parsedRanges);
                }
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

    private static Match matchAndMemoize(MemoKey memoKey, MemoTable memoTable, String input,
            PriorityBlockingQueue<MemoKey> priorityQueue) {
        var match = memoKey.clause.match(memoTable, memoKey, input);
        if (match != null) {
            // Memoize any new match, and schedule parent clauses for evaluation in the priority queue
            memoTable.addMatch(match, priorityQueue);

            if (DEBUG) {
                System.out.println("Matched: " + memoKey.toStringWithRuleNames());
            }
        } else if (DEBUG) {
            System.out.println("Failed to match: " + memoKey.toStringWithRuleNames());
        }
        return match;
    }

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
                        matchAndMemoize(new MemoKey(clause, startPos), memoTable, input, priorityQueue);
                    }
                });

        // Main parsing loop
        while (!priorityQueue.isEmpty()) {
            // Remove a MemoKey from priority queue (which is ordered from the end of the input to the beginning
            // and from lowest clauses to toplevel clauses), and try matching the MemoKey bottom-up
            matchAndMemoize(priorityQueue.remove(), memoTable, input, priorityQueue);
        }
        return memoTable;
    }
}
