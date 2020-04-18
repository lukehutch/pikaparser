package pikaparser.grammar;

import static pikaparser.clause.ClauseFactory.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import pikaparser.clause.Clause;
import pikaparser.clause.RuleRef;
import pikaparser.grammar.Rule.Associativity;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoTable;

public class Grammar {
    public final List<Clause> allClauses;
    public Clause lexClause;
    public Map<String, Rule> ruleNameWithPrecedenceToRule;

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
            var rulesWithName = ruleNameToRules.get(rule.ruleName);
            if (rulesWithName == null) {
                ruleNameToRules.put(rule.ruleName, rulesWithName = new ArrayList<>());
            }
            rulesWithName.add(rule);
        }
        List<Rule> allRules = new ArrayList<>(rules);
        for (var ent : ruleNameToRules.entrySet()) {
            // Rewrite rules that have multiple precedence levels
            var rulesWithName = ent.getValue();
            if (rulesWithName.size() > 1) {
                // Add rules for higher precedence selectors, and rewrite rule self-references to select
                // higher precedence. e.g. given max precedence of 5:
                // (R at prec 3 = '+' R) is replaced with (R[3] = '+' R[4-5])
                // (R at prec 5 = '(' R ')') is replaced with (R[5] = '(' R[0-5] ')')
                var ruleName = ent.getKey();
                handlePrecedence(ruleName, rulesWithName, allRules);
            }
        }

        // If there is more than one precedence level for a rule, the handlePrecedence call above modifies
        // rule names to include a precedence suffix, and also adds an all-precedence selector clause with the
        // orgininal rule name. All rule names should now be unique.
        ruleNameWithPrecedenceToRule = new HashMap<>();
        for (var rule : allRules) {
            if (ruleNameWithPrecedenceToRule.put(rule.ruleName, rule) != null) {
                // Should not happen
                throw new IllegalArgumentException("Duplicate rule name");
            }
        }

        // Intern clauses based on their toString() value, coalescing shared sub-clauses into a DAG, so that
        // effort is not wasted parsing different instances of the same clause multiple times, and so that
        // when a subclause matches, all parent clauses will be added to the active set in the next iteration.
        // Also causes the toString() values to be cached, so that after RuleRefs are replaced with direct
        // Clause references, toString() doesn't get stuck in an infinite loop.
        Map<String, Clause> toStringToClause = new HashMap<>();
        Set<Clause> internVisited = new HashSet<>();
        for (var rule : allRules) {
            rule.intern(toStringToClause, internVisited);
        }

        // Resolve each RuleRef into a direct reference to the referenced clause
        Set<Clause> ruleClausesVisited = new HashSet<>();
        for (var rule : allRules) {
            rule.resolveRuleRefs(ruleNameWithPrecedenceToRule, ruleClausesVisited);
        }

        if (lexRuleName != null) {
            // Find the toplevel lex rule, if lexRuleName is specified
            var lexRule = ruleNameWithPrecedenceToRule.get(lexRuleName);
            if (lexRule == null) {
                throw new IllegalArgumentException("Unknown lex rule name: " + lexRuleName);
            }
            // Check the lex rule does not contain any cycles
            lexRule.checkNoCycles();
            lexClause = lexRule.clause;
        }

        // Find clauses reachable from the toplevel clause, in reverse topological order.
        // Clauses can form a DAG structure, via RuleRef.
        allClauses = new ArrayList<Clause>();
        HashSet<Clause> allClausesVisited = new HashSet<Clause>();
        for (var rule : allRules) {
            rule.findReachableClauses(allClausesVisited, allClauses);
        }

        // Find clauses that always match zero or more characters, e.g. FirstMatch(X | Nothing).
        // allClauses is in reverse topological order, i.e. bottom-up
        for (Clause clause : allClauses) {
            clause.testWhetherCanMatchZeroChars();
        }

        // Find seed parent clauses (in the case of Seq, this depends upon alwaysMatches being set in the prev step)
        for (var clause : allClauses) {
            clause.backlinkToSeedParentClauses();
        }
    }

    /** Resolve {@link RuleRef} clauses that reference the rule with a precedence selector reference. */
    private static void rewriteSelfRefs(String ruleName, Associativity ruleAssociativity,
            Associativity childAssociativityPosition, Clause clause, String higherPrecedenceSelectorRuleName,
            String higherPrecedenceSelectorRuleNameAssociative, Set<String> higherPrecedenceRuleNamesUsed,
            Set<Clause> visited) {
        if (visited.add(clause)) {
            if (clause instanceof RuleRef && ((RuleRef) clause).refdRuleName.equals(ruleName)) {
                // Replace rule self-ref with precedence selector, e.g. (given max precedence 5):
                // e.g. R[3] <- '+' R is replaced with R[3] <- '+' R[4-5].
                // Choose associative version of precedence selector, if rule is associative,
                // e.g. R[3] <- '+' R is replaced with R[3] <- '+' R[4-5/3].
                var precSelectorName = ruleAssociativity == null || childAssociativityPosition != ruleAssociativity
                        ? higherPrecedenceSelectorRuleName
                        : higherPrecedenceSelectorRuleNameAssociative;
                ((RuleRef) clause).refdRuleName = precSelectorName;
                ((RuleRef) clause).toStringCached = null;
                // Mark precedence selector as used, so that only used selectors result in new rules
                higherPrecedenceRuleNamesUsed.add(precSelectorName);
            } else {
                // Recurse through subclause tree if toplevel clause was not a RuleRef
                for (int i = 0; i < clause.subClauses.length; i++) {
                    rewriteSelfRefs(ruleName, ruleAssociativity,
                            // Apply left associativity to first self-referencing subclause; right associativity
                            // to last self-referencing subclause
                            i == 0 ? Associativity.LEFT
                                    : i == clause.subClauses.length - 1 ? Associativity.RIGHT : null,
                            clause.subClauses[i], higherPrecedenceSelectorRuleName,
                            higherPrecedenceSelectorRuleNameAssociative, higherPrecedenceRuleNamesUsed, visited);
                }
            }
        }
    }

    private static void handlePrecedence(String ruleName, List<Rule> rules, List<Rule> allRulesOut) {
        // There's nothing to do if there's only one precedence level
        // Check there are no duplicate precedence levels
        var precedenceToRule = new TreeMap<Integer, Rule>();
        for (var rule : rules) {
            if (precedenceToRule.put(rule.precedence, rule) != null) {
                throw new IllegalArgumentException(
                        "Multiple rules with name " + ruleName + " and precedence " + rule.precedence);
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

        // Create precedence selector clauses
        var precedenceSelectorRuleNameOrder = new ArrayList<String>();
        var precedenceSelectorRuleNameToClause = new HashMap<String, Clause>();
        for (int precedenceIdx = 0; precedenceIdx <= numPrecedenceLevels; precedenceIdx++) {
            // Each precedence selector starts at precedenceIdx, and runs to the highest precedence,
            // i.e. for precedence levels [P0, P1, P2, P3, P4], the precedence selectors are:
            // 0: P0 / P1 / P2 / P3 / P4
            // 1: P1 / P2 / P3 / P4
            // 2: P2 / P3 / P4
            // 3: P3 / P4
            // 4: P4
            // Then add one more precedence level, in case the highest precedence level is L or R associative:
            // 5: P4 / P1 / P2 / P3
            // (i.e. for the last clause, it doesn't run all the way to P4 again, because that would duplicate P4).
            var precedenceSelectorSubClauses = new ArrayList<Clause>();
            var startPrecedenceIdx = precedenceIdx < numPrecedenceLevels ? precedenceIdx : numPrecedenceLevels - 1;
            var endPrecedenceIdxIncl = precedenceIdx < numPrecedenceLevels ? numPrecedenceLevels - 1
                    : numPrecedenceLevels - 2;
            var startPrecedence = precedenceOrder.get(startPrecedenceIdx).precedence;
            var endPrecedenceIncl = precedenceOrder.get(endPrecedenceIdxIncl).precedence;
            for (int j = startPrecedenceIdx;; j = (j + 1) % numPrecedenceLevels) {
                precedenceSelectorSubClauses.add(r(ruleName + "[" + precedenceOrder.get(j).precedence + "]"));
                if (j == endPrecedenceIdxIncl) {
                    break;
                }
            }

            // Create and name precedence selector clauses.
            // The final set of names will be ordered as follows for precence levels 0-5:
            // Expr[0-5], Expr[1-5], Expr[2-5], Expr[3-5], Expr[4-5], Expr[5], Expr[5,0-4]
            // Where the last selector, Expr[5,0-4], is only used if the highest precedence level is
            // L or R associative.
            var hasOnlyOneSubClause = precedenceSelectorSubClauses.size() == 1;
            if (hasOnlyOneSubClause) {
                // Create rule name, but there's no need to wrap the subclause in a First(...) selector,
                // or to store precedence selector rule, since the self-referencing RuleRef will be rewritten
                // to point directly to the single referenced precedence level, e.g. "Expr[3]".
                var precedenceSelectorRuleName = ruleName + "[" + startPrecedence + "]";
                precedenceSelectorRuleNameOrder.add(precedenceSelectorRuleName);
            } else {
                // Create a First(...) selector clause for all the selected precedence levels
                var precedenceSelectorClause = first(precedenceSelectorSubClauses);

                // Create rule name, e.g. "Expr[3-5]", or "Expr[5,0-4]" for the associative precedence selector
                // for the highest precedence level.
                var precedenceSelectorRuleName = startPrecedence <= endPrecedenceIdxIncl
                        ? ruleName + "[" + startPrecedence + "-" + endPrecedenceIncl + "]"
                        : ruleName + "[" + startPrecedence + "," + precedenceOrder.get(0).precedence + "-"
                                + endPrecedenceIncl + "]";
                precedenceSelectorRuleNameOrder.add(precedenceSelectorRuleName);

                // Store precedence selector rule as a new rule that needs to be added
                precedenceSelectorRuleNameToClause.put(precedenceSelectorRuleName, precedenceSelectorClause);

                // The first precedence selector clause selects all precedence levels.
                // Create a new rule without the precedence level suffix that uses this clause, so that if
                // other rules refer to this one, all precedence levels will be selected in precedence order.
                if (precedenceIdx == 0) {
                    allRulesOut.add(rule(ruleName, precedenceSelectorClause));
                }
            }
        }

        // Replace rule self-references with precedence selector rule references
        var precedenceSelectorPrecedenceRuleNamesUsed = new HashSet<String>();
        for (int precedenceIdx = 0; precedenceIdx < numPrecedenceLevels; precedenceIdx++) {
            // Since there is more than one precedence level, update rule name to include precedence
            var rule = precedenceOrder.get(precedenceIdx);

            // A non-associative rule self reference for precedence level i is replaced with a reference to
            // precedence selector (i + 1) % numPrecedenceLevels
            var higherPrecedenceSelectorRuleName = precedenceSelectorRuleNameOrder
                    .get((precedenceIdx + 1) % numPrecedenceLevels);

            // A rule self reference in a precedence level that has left or right assocativity for precedence
            // level i is replaced with a reference to precedence selector i, unless i is the index of the highest
            // precedence level, in which case precedence selector i+1 is used, which allows precedence to be
            // applied to the highest precedence level.
            var higherPrecedenceSelectorRuleNameAssociative = precedenceSelectorRuleNameOrder
                    .get(precedenceIdx < numPrecedenceLevels - 1 ? precedenceIdx : precedenceIdx + 1);

            // Replace all self-references in the rule with a reference to the higher precedence clause(s).
            // N.B. this assumes that subclauses are not referentially shared between different rules of the
            // same name with different precedence
            rewriteSelfRefs(ruleName, rule.associativity, /* associativity = */ null,
                    precedenceOrder.get(precedenceIdx).clause, higherPrecedenceSelectorRuleName,
                    higherPrecedenceSelectorRuleNameAssociative, precedenceSelectorPrecedenceRuleNamesUsed,
                    new HashSet<>());
        }

        // Add rules for all higher precedence selectors that were created
        for (var ent : precedenceSelectorRuleNameToClause.entrySet()) {
            if (precedenceSelectorPrecedenceRuleNamesUsed.contains(ent.getKey())) {
                allRulesOut.add(rule(ent.getKey(), ent.getValue()));
            }
        }
    }

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
}
