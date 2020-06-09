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

import static java.util.Map.entry;
import static pikaparser.parser.utils.ClauseFactory.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import pikaparser.ast.ASTNode;
import pikaparser.clause.Clause;
import pikaparser.clause.aux.ASTNodeLabel;
import pikaparser.clause.aux.RuleRef;
import pikaparser.clause.nonterminal.First;
import pikaparser.clause.nonterminal.FollowedBy;
import pikaparser.clause.nonterminal.NotFollowedBy;
import pikaparser.clause.nonterminal.OneOrMore;
import pikaparser.clause.nonterminal.Seq;
import pikaparser.clause.terminal.Terminal;
import pikaparser.grammar.Rule.Associativity;
import pikaparser.parser.utils.ParserInfo;
import pikaparser.parser.utils.StringUtils;

/**
 * A "meta-grammar" that produces a runtime parser generator, allowing a grammar to be defined using ASCII notation.
 */
public class MetaGrammar {

    // Rule names:

    private static final String GRAMMAR = "GRAMMAR";
    private static final String WSC = "WSC";
    private static final String COMMENT = "COMMENT";
    private static final String RULE = "RULE";
    private static final String CLAUSE = "CLAUSE";
    private static final String IDENT = "IDENT";
    private static final String PREC = "PREC";
    private static final String NUM = "NUM";
    private static final String NAME_CHAR = "NAME_CHAR";
    private static final String CHAR_SET = "CHARSET";
    private static final String HEX = "Hex";
    private static final String CHAR_RANGE = "CHAR_RANGE";
    private static final String CHAR_RANGE_CHAR = "CHAR_RANGE_CHAR";
    private static final String QUOTED_STRING = "QUOTED_STR";
    private static final String ESCAPED_CTRL_CHAR = "ESCAPED_CTRL_CHAR";
    private static final String SINGLE_QUOTED_CHAR = "SINGLE_QUOTED_CHAR";
    private static final String STR_QUOTED_CHAR = "STR_QUOTED_CHAR";
    private static final String NOTHING = "NOTHING";
    private static final String START = "START";

    // AST node names:

    private static final String RULE_AST = "RuleAST";
    private static final String PREC_AST = "PrecAST";
    private static final String R_ASSOC_AST = "RAssocAST";
    private static final String L_ASSOC_AST = "LAssocAST";
    private static final String IDENT_AST = "IdentAST";
    private static final String LABEL_AST = "LabelAST";
    private static final String LABEL_NAME_AST = "LabelNameAST";
    private static final String LABEL_CLAUSE_AST = "LabelClauseAST";
    private static final String SEQ_AST = "SeqAST";
    private static final String FIRST_AST = "FirstAST";
    private static final String FOLLOWED_BY_AST = "FollowedByAST";
    private static final String NOT_FOLLOWED_BY_AST = "NotFollowedByAST";
    private static final String ONE_OR_MORE_AST = "OneOrMoreAST";
    private static final String ZERO_OR_MORE_AST = "ZeroOrMoreAST";
    private static final String OPTIONAL_AST = "OptionalAST";
    private static final String SINGLE_QUOTED_CHAR_AST = "SingleQuotedCharAST";
    private static final String CHAR_RANGE_AST = "CharRangeAST";
    private static final String QUOTED_STRING_AST = "QuotedStringAST";
    private static final String START_AST = "StartAST";
    private static final String NOTHING_AST = "NothingAST";

    // Precedence levels (should correspond to levels in grammar below):

    private static Map<Class<? extends Clause>, Integer> clauseTypeToPrecedence = //
            Map.ofEntries( //
                    entry(Terminal.class, 7), //
                    // Treat RuleRef as having the same precedence as a terminal for string interning purposes
                    entry(RuleRef.class, 7), //
                    entry(OneOrMore.class, 6), //
                    // ZeroOrMore is not present in the final grammar, so it is skipped here
                    entry(NotFollowedBy.class, 5), //
                    entry(FollowedBy.class, 5), //
                    // Optional is not present in final grammar, so it is skipped here
                    entry(ASTNodeLabel.class, 3), //
                    entry(Seq.class, 2), //
                    entry(First.class, 1) //
            );

    // Metagrammar:

    public static Grammar grammar = new Grammar(Arrays.asList(//
            rule(GRAMMAR, //
                    seq(start(), ruleRef(WSC), oneOrMore(ruleRef(RULE)))), //

            rule(RULE, //
                    ast(RULE_AST, seq(ruleRef(IDENT), ruleRef(WSC), //
                            optional(ruleRef(PREC)), //
                            str("<-"), ruleRef(WSC), //
                            ruleRef(CLAUSE), ruleRef(WSC), c(';'), ruleRef(WSC)))), //

            // Define precedence order for clause sequences

            // Parens
            rule(CLAUSE, 8, /* associativity = */ null, //
                    seq(c('('), ruleRef(WSC), ruleRef(CLAUSE), ruleRef(WSC), c(')'))), //

            // Terminals
            rule(CLAUSE, 7, /* associativity = */ null, //
                    first( //
                            ruleRef(IDENT), //
                            ruleRef(QUOTED_STRING), //
                            ruleRef(CHAR_SET), //
                            ruleRef(NOTHING), //
                            ruleRef(START))), //

            // OneOrMore / ZeroOrMore
            rule(CLAUSE, 6, /* associativity = */ null, //
                    first( //
                            seq(ast(ONE_OR_MORE_AST, ruleRef(CLAUSE)), ruleRef(WSC), c('+')),
                            seq(ast(ZERO_OR_MORE_AST, ruleRef(CLAUSE)), ruleRef(WSC), c('*')))), //

            // FollowedBy / NotFollowedBy
            rule(CLAUSE, 5, /* associativity = */ null, //
                    first( //
                            seq(c('&'), ast(FOLLOWED_BY_AST, ruleRef(CLAUSE))), //
                            seq(c('!'), ast(NOT_FOLLOWED_BY_AST, ruleRef(CLAUSE))))), //

            // Optional
            rule(CLAUSE, 4, /* associativity = */ null, //
                    seq(ast(OPTIONAL_AST, ruleRef(CLAUSE)), ruleRef(WSC), c('?'))), //

            // ASTNodeLabel
            rule(CLAUSE, 3, /* associativity = */ null, //
                    ast(LABEL_AST,
                            seq(ast(LABEL_NAME_AST, ruleRef(IDENT)), ruleRef(WSC), c(':'), ruleRef(WSC),
                                    ast(LABEL_CLAUSE_AST, ruleRef(CLAUSE)), ruleRef(WSC)))), //

            // Seq
            rule(CLAUSE, 2, /* associativity = */ null, //
                    ast(SEQ_AST,
                            seq(ruleRef(CLAUSE), ruleRef(WSC), oneOrMore(seq(ruleRef(CLAUSE), ruleRef(WSC)))))),

            // First
            rule(CLAUSE, 1, /* associativity = */ null, //
                    ast(FIRST_AST,
                            seq(ruleRef(CLAUSE), ruleRef(WSC),
                                    oneOrMore(seq(c('/'), ruleRef(WSC), ruleRef(CLAUSE), ruleRef(WSC)))))),

            // Whitespace or comment
            rule(WSC, //
                    zeroOrMore(first(c(' ', '\n', '\r', '\t'), ruleRef(COMMENT)))),

            // Comment
            rule(COMMENT, //
                    seq(c('#'), zeroOrMore(c('\n').invert()))),

            // Identifier
            rule(IDENT, //
                    ast(IDENT_AST,
                            seq(ruleRef(NAME_CHAR), zeroOrMore(first(ruleRef(NAME_CHAR), cRange('0', '9')))))), //

            // Number
            rule(NUM, //
                    oneOrMore(cRange('0', '9'))), //

            // Name character
            rule(NAME_CHAR, //
                    c(cRange('a', 'z'), cRange('A', 'Z'), c('_', '-'))),

            // Precedence and optional associativity modifiers for rule name
            rule(PREC, //
                    seq(c('['), ruleRef(WSC), //
                            ast(PREC_AST, ruleRef(NUM)), ruleRef(WSC), //
                            optional(seq(c(','), ruleRef(WSC), //
                                    first(ast(R_ASSOC_AST, first(c('r'), c('R'))),
                                            ast(L_ASSOC_AST, first(c('l'), c('L')))),
                                    ruleRef(WSC))), //
                            c(']'), ruleRef(WSC))), //

            // Character set
            rule(CHAR_SET, //
                    first( //
                            seq(c('\''), ast(SINGLE_QUOTED_CHAR_AST, ruleRef(SINGLE_QUOTED_CHAR)), c('\'')), //
                            seq(c('['), //
                                    ast(CHAR_RANGE_AST, seq(optional(c('^')), //
                                            oneOrMore(first( //
                                                    ruleRef(CHAR_RANGE), //
                                                    ruleRef(CHAR_RANGE_CHAR))))),
                                    c(']')))), //

            // Single quoted character
            rule(SINGLE_QUOTED_CHAR, //
                    first( //
                            ruleRef(ESCAPED_CTRL_CHAR), //
                            c('\'').invert())), // TODO: replace invert() with NotFollowedBy

            // Char range
            rule(CHAR_RANGE, //
                    seq(ruleRef(CHAR_RANGE_CHAR), c('-'), ruleRef(CHAR_RANGE_CHAR))), //

            // Char range character
            rule(CHAR_RANGE_CHAR, //
                    first( //
                            c('\\', ']').invert(), //
                            ruleRef(ESCAPED_CTRL_CHAR), //
                            str("\\\\"), //
                            str("\\]"), //
                            str("\\^"))),

            // Quoted string
            rule(QUOTED_STRING, //
                    seq(c('"'), ast(QUOTED_STRING_AST, zeroOrMore(ruleRef(STR_QUOTED_CHAR))), c('"'))), //

            // Character within quoted string
            rule(STR_QUOTED_CHAR, //
                    first( //
                            ruleRef(ESCAPED_CTRL_CHAR), //
                            c('"', '\\').invert() //
                    )), //

            // Hex digit
            rule(HEX, c(cRange('0', '9'), cRange('a', 'f'), cRange('A', 'F'))), //

            // Escaped control character
            rule(ESCAPED_CTRL_CHAR, //
                    first( //
                            str("\\t"), //
                            str("\\b"), //
                            str("\\n"), //
                            str("\\r"), //
                            str("\\f"), //
                            str("\\'"), //
                            str("\\\""), //
                            str("\\\\"), //
                            seq(str("\\u"), ruleRef(HEX), ruleRef(HEX), ruleRef(HEX), ruleRef(HEX)))), //

            // Nothing (empty string match)
            rule(NOTHING, //
                    ast(NOTHING_AST, seq(c('('), ruleRef(WSC), c(')')))),

            // Match start position
            rule(START, ast(START_AST, c('^'))) //
    ));

    /**
     * Return true if subClause precedence is less than or equal to parentClause precedence (or if subclause is a
     * {@link Seq} clause and parentClause is a {@link First} clause, for clarity, even though parens are not needed
     * because Seq has higher prrecedence).
     */
    public static boolean needToAddParensAroundSubClause(Clause parentClause, Clause subClause) {
        int clausePrec = parentClause instanceof Terminal ? clauseTypeToPrecedence.get(Terminal.class)
                : clauseTypeToPrecedence.get(parentClause.getClass());
        int subClausePrec = subClause instanceof Terminal ? clauseTypeToPrecedence.get(Terminal.class)
                : clauseTypeToPrecedence.get(subClause.getClass());
        // Always parenthesize Seq inside First for clarity, even though Seq has higher precedence
        return ((parentClause instanceof First && subClause instanceof Seq)
                // Add parentheses around subclauses that are lower or equal precedence to parent clause
                || subClausePrec <= clausePrec);
    }

    /** Return true if subclause has lower precedence than an AST node label. */
    public static boolean needToAddParensAroundASTNodeLabel(Clause subClause) {
        int astNodeLabelPrec = clauseTypeToPrecedence.get(ASTNodeLabel.class);
        int subClausePrec = subClause instanceof Terminal ? clauseTypeToPrecedence.get(Terminal.class)
                : clauseTypeToPrecedence.get(subClause.getClass());
        return subClausePrec < astNodeLabelPrec;
    }

    /**
     * Expect just a single clause in the list of clauses, and return it, or throw an exception if the length of the
     * list of clauses is not 1.
     */
    private static Clause expectOne(List<Clause> clauses, ASTNode astNode) {
        if (clauses.size() != 1) {
            throw new IllegalArgumentException("Expected one subclause, got " + clauses.size() + ": " + astNode);
        }
        return clauses.get(0);
    }

    /** Recursively convert a list of AST nodes into a list of Clauses. */
    private static List<Clause> parseASTNodes(List<ASTNode> astNodes) {
        List<Clause> clauses = new ArrayList<>(astNodes.size());
        for (ASTNode astNode : astNodes) {
            clauses.add(parseASTNode(astNode));
        }
        return clauses;
    }

    /** Recursively parse a single AST node. */
    private static Clause parseASTNode(ASTNode astNode) {
        Clause clause;
        switch (astNode.label) {
        case SEQ_AST:
            clause = seq(parseASTNodes(astNode.children).toArray(new Clause[0]));
            break;
        case FIRST_AST:
            clause = first(parseASTNodes(astNode.children).toArray(new Clause[0]));
            break;
        case ONE_OR_MORE_AST:
            clause = oneOrMore(expectOne(parseASTNodes(astNode.children), astNode));
            break;
        case ZERO_OR_MORE_AST:
            clause = zeroOrMore(expectOne(parseASTNodes(astNode.children), astNode));
            break;
        case OPTIONAL_AST:
            clause = optional(expectOne(parseASTNodes(astNode.children), astNode));
            break;
        case FOLLOWED_BY_AST:
            clause = followedBy(expectOne(parseASTNodes(astNode.children), astNode));
            break;
        case NOT_FOLLOWED_BY_AST:
            clause = notFollowedBy(expectOne(parseASTNodes(astNode.children), astNode));
            break;
        case LABEL_AST:
            clause = ast(astNode.getFirstChild().getText(), parseASTNode(astNode.getSecondChild().getFirstChild()));
            break;
        case IDENT_AST:
            clause = ruleRef(astNode.getText()); // Rule name ref
            break;
        case QUOTED_STRING_AST: // Doesn't include surrounding quotes
            clause = str(StringUtils.unescapeString(astNode.getText()));
            break;
        case SINGLE_QUOTED_CHAR_AST:
            clause = c(StringUtils.unescapeChar(astNode.getText()));
            break;
        case START_AST:
            clause = start();
            break;
        case NOTHING_AST:
            clause = nothing();
            break;
        case CHAR_RANGE_AST:
            String text = StringUtils.unescapeString(astNode.getText());
            boolean invert = text.startsWith("^");
            if (invert) {
                text = text.substring(1);
            }
            clause = invert ? cRange(text).invert() : cRange(text);
            break;
        default:
            // Keep recursing for parens (the only type of AST node that doesn't have a label)
            clause = expectOne(parseASTNodes(astNode.children), astNode);
            break;
        }
        return clause;
    }

    /** Parse a rule in the AST, returning a new {@link Rule}. */
    private static Rule parseRule(ASTNode ruleNode) {
        String ruleName = ruleNode.getFirstChild().getText();
        var hasPrecedence = ruleNode.children.size() > 2;
        var associativity = ruleNode.children.size() < 4 ? null
                : ((ruleNode.getThirdChild().label.equals(L_ASSOC_AST) ? Associativity.LEFT
                        : ruleNode.getThirdChild().label.equals(R_ASSOC_AST) ? Associativity.RIGHT : null));
        int precedence = hasPrecedence ? Integer.parseInt(ruleNode.getSecondChild().getText()) : -1;
        if (hasPrecedence && precedence < 0) {
            throw new IllegalArgumentException("Precedence needs to be zero or positive (rule " + ruleName
                    + " has precedence level " + precedence + ")");
        }
        var astNode = ruleNode.getChild(ruleNode.children.size() - 1);
        Clause clause = parseASTNode(astNode);
        return rule(ruleName, precedence, associativity, clause);
    }

    /** Parse a grammar description in an input string, returning a new {@link Grammar} object. */
    public static Grammar parse(String input) {
        var memoTable = grammar.parse(input);

        //        ParserInfo.printParseResult("GRAMMAR", grammar, memoTable, input,
        //                new String[] { "GRAMMAR", "RULE", "CLAUSE[0]" }, /* showAllMatches = */ false);
        //
        //        System.out.println("\nParsed meta-grammar:");
        //        for (var clause : MetaGrammar.grammar.allClauses) {
        //            System.out.println("    " + clause.toStringWithRuleNames());
        //        }

        var syntaxErrors = memoTable.getSyntaxErrors(
            GRAMMAR, RULE, CLAUSE + "[" + clauseTypeToPrecedence.get(First.class) + "]"
        );
        if (! syntaxErrors.isEmpty()) {
            ParserInfo.printSyntaxErrors(syntaxErrors);
        }

        var topLevelRule = grammar.getRule(GRAMMAR);
        var topLevelRuleASTNodeLabel = topLevelRule.labeledClause.astNodeLabel;
        if (topLevelRuleASTNodeLabel == null) {
            topLevelRuleASTNodeLabel = "<root>";
        }
        var topLevelMatches = grammar.getNonOverlappingMatches(GRAMMAR, memoTable);
        if (topLevelMatches.isEmpty()) {
            throw new IllegalArgumentException("Toplevel rule \"" + GRAMMAR + "\" did not match");
        } else if (topLevelMatches.size() > 1) {
            System.out.println("\nMultiple toplevel matches:");
            for (var topLevelMatch : topLevelMatches) {
                var topLevelASTNode = new ASTNode(topLevelRuleASTNodeLabel, topLevelMatch, input);
                System.out.println(topLevelASTNode);
            }
            throw new IllegalArgumentException("Stopping");
        }

        var topLevelASTNode = new ASTNode(topLevelRuleASTNodeLabel, topLevelMatches.get(0), input);

        // System.out.println(topLevelASTNode);

        List<Rule> rules = new ArrayList<>();
        for (ASTNode astNode : topLevelASTNode.children) {
            if (!astNode.label.equals(RULE_AST)) {
                throw new IllegalArgumentException("Wrong node type");
            }
            Rule rule = parseRule(astNode);
            rules.add(rule);
        }
        return new Grammar(rules);
    }
}
