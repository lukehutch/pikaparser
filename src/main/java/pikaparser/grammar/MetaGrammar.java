package pikaparser.grammar;

import static java.util.Map.entry;
import static pikaparser.clause.ClauseFactory.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import pikaparser.clause.ASTNodeLabel;
import pikaparser.clause.Clause;
import pikaparser.clause.First;
import pikaparser.clause.FollowedBy;
import pikaparser.clause.Longest;
import pikaparser.clause.NotFollowedBy;
import pikaparser.clause.OneOrMore;
import pikaparser.clause.RuleRef;
import pikaparser.clause.Seq;
import pikaparser.clause.Terminal;
import pikaparser.grammar.Rule.Associativity;
import pikaparser.parser.ASTNode;
import pikaparser.parser.ParserInfo;

public class MetaGrammar {
    // Rule names:

    private static final String GRAMMAR = "GRAMMAR";
    private static final String LEX = "LEX";
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
    private static final String LONGEST_AST = "LongestAST";
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
                    // Treat RuleRef as a terminal for string interning purposes
                    entry(RuleRef.class, 7), //
                    entry(OneOrMore.class, 6), //
                    // ZeroOrMore is not present in final grammar
                    entry(NotFollowedBy.class, 5), //
                    entry(FollowedBy.class, 5), //
                    // Optional is not present in final grammar
                    entry(ASTNodeLabel.class, 3), //
                    entry(Seq.class, 2), //
                    entry(First.class, 1), //
                    entry(Longest.class, 0) //
            );

    // Metagrammar:

    public static Grammar grammar = new Grammar(LEX, Arrays.asList(//
            rule(GRAMMAR, //
                    seq(start(), r(WSC), oneOrMore(r(RULE)))), //

            rule(RULE, //
                    ast(RULE_AST, seq(r(IDENT), r(WSC), //
                            optional(r(PREC)), //
                            str("<-"), r(WSC), //
                            r(CLAUSE), r(WSC), c(';'), r(WSC)))), //

            // Define precedence order for clause sequences

            // Parens
            rule(CLAUSE, 8, /* associativity = */ null, seq(c('('), r(WSC), r(CLAUSE), r(WSC), c(')'))), //

            // Terminals
            rule(CLAUSE, 7, /* associativity = */ null, //
                    first( //
                            r(IDENT), //
                            r(QUOTED_STRING), //
                            r(CHAR_SET), //
                            r(NOTHING), //
                            r(START))), //

            // OneOrMore / ZeroOrMore
            rule(CLAUSE, 6, /* associativity = */ null, //
                    first( //
                            seq(ast(ONE_OR_MORE_AST, r(CLAUSE)), r(WSC), c("+")),
                            seq(ast(ZERO_OR_MORE_AST, r(CLAUSE)), r(WSC), c('*')))), //

            // FollowedBy / NotFollowedBy
            rule(CLAUSE, 5, /* associativity = */ null, //
                    first( //
                            seq(c('&'), ast(FOLLOWED_BY_AST, r(CLAUSE))), //
                            seq(c('!'), ast(NOT_FOLLOWED_BY_AST, r(CLAUSE))))), //

            // Optional
            rule(CLAUSE, 4, /* associativity = */ null, //
                    seq(ast(OPTIONAL_AST, r(CLAUSE)), r(WSC), c('?'))), //

            // ASTNodeLabel
            rule(CLAUSE, 3, /* associativity = */ null, //
                    ast(LABEL_AST,
                            seq(ast(LABEL_NAME_AST, r(IDENT)), r(WSC), c(':'), r(WSC),
                                    ast(LABEL_CLAUSE_AST, r(CLAUSE)), r(WSC)))), //

            // Seq
            rule(CLAUSE, 2, /* associativity = */ null, //
                    ast(SEQ_AST, seq(r(CLAUSE), r(WSC), oneOrMore(seq(r(CLAUSE), r(WSC)))))),

            // First
            rule(CLAUSE, 1, /* associativity = */ null, //
                    ast(FIRST_AST, seq(r(CLAUSE), r(WSC), oneOrMore(seq(c('/'), r(WSC), r(CLAUSE), r(WSC)))))),

            // Longest
            rule(CLAUSE, 0, /* associativity = */ null, //
                    ast(LONGEST_AST, seq(r(CLAUSE), r(WSC), oneOrMore(seq(c('|'), r(WSC), r(CLAUSE), r(WSC)))))),

            // Lex rule for preprocessing

            rule(LEX, //
                    first( //
                            c('('), //
                            c(')'), //
                            c(';'), //
                            c(':'), //
                            c('^'), //
                            c('*'), //
                            c('+'), //
                            c('?'), //
                            c('|'), //
                            c('/'), //
                            c('^'), //
                            c('-'), //
                            str("<-"), //
                            // Match both CHAR_SET and PREC, since PREC looks like a CHAR_SET
                            longest(r(PREC), r(CHAR_SET)), //
                            r(IDENT), //
                            r(NUM), //
                            r(QUOTED_STRING), //

                            // WS/comment has to come last, since it can match Nothing
                            r(WSC))), //

            rule(WSC, //
                    zeroOrMore(first(c(" \n\r\t"), r(COMMENT)))),

            rule(COMMENT, //
                    seq(c('#'), zeroOrMore(c('\n', /* invert = */ true)))),

            rule(IDENT, //
                    ast(IDENT_AST, seq(r(NAME_CHAR), zeroOrMore(first(r(NAME_CHAR), c('0', '9')))))), //

            rule(NUM, //
                    oneOrMore(c('0', '9'))), //

            rule(NAME_CHAR, //
                    c(c('a', 'z'), c('A', 'Z'), c("_-"))),

            rule(PREC, //
                    seq(c('['), r(WSC), //
                            ast(PREC_AST, r(NUM)), r(WSC), //
                            optional(seq(c(','), r(WSC), //
                                    first(ast(R_ASSOC_AST, first(c('r'), c('R'))),
                                            ast(L_ASSOC_AST, first(c('l'), c('L')))),
                                    r(WSC))), //
                            c(']'), r(WSC))), //

            rule(CHAR_SET, //
                    first( //
                            seq(c('\''), ast(SINGLE_QUOTED_CHAR_AST, r(SINGLE_QUOTED_CHAR)), c('\'')), //
                            seq(c('['), //
                                    ast(CHAR_RANGE_AST, seq(optional(c('^')), //
                                            oneOrMore(first( //
                                                    r(CHAR_RANGE), //
                                                    r(CHAR_RANGE_CHAR))))),
                                    c(']')))), //

            rule(SINGLE_QUOTED_CHAR, //
                    first( //
                            r(ESCAPED_CTRL_CHAR), //
                            c("\'\\").invert())), //

            rule(HEX, c(c('0', '9'), c('a', 'f'), c('A', 'F'))), //

            rule(CHAR_RANGE, //
                    seq(r(CHAR_RANGE_CHAR), c('-'), r(CHAR_RANGE_CHAR))), //

            rule(CHAR_RANGE_CHAR, //
                    first( //
                            c('\\', ']').invert(), //
                            r(ESCAPED_CTRL_CHAR), //
                            str("\\\\"), //
                            str("\\]"), //
                            str("\\^"))),

            rule(QUOTED_STRING, //
                    seq(c('"'), ast(QUOTED_STRING_AST, zeroOrMore(r(STR_QUOTED_CHAR))), c('"'))), //

            rule(STR_QUOTED_CHAR, //
                    first( //
                            r(ESCAPED_CTRL_CHAR), //
                            c("\"\\").invert() //
                    )), //

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
                            seq(str("\\u"), r(HEX), r(HEX), r(HEX), r(HEX)))), //

            rule(NOTHING, //
                    ast(NOTHING_AST, seq(c('('), r(WSC), c(')')))),

            rule(START, ast(START_AST, c('^'))) //
    ));

    public static boolean addParensAroundSubClause(Clause parentClause, Clause subClause) {
        int clausePrec = parentClause instanceof Terminal ? clauseTypeToPrecedence.get(Terminal.class)
                : clauseTypeToPrecedence.get(parentClause.getClass());
        int subClausePrec = subClause instanceof Terminal ? clauseTypeToPrecedence.get(Terminal.class)
                : clauseTypeToPrecedence.get(subClause.getClass());
        // Always parenthesize Seq inside First for clarity, even though Seq has higher precedence
        return parentClause instanceof First && subClause instanceof Seq
                // Add parentheses around subclauses that are lower or equal precedence to parent clause
                || subClausePrec <= clausePrec;
    }

    public static boolean addParensAroundASTNodeLabel(Clause subClause) {
        int astNodeLabelPrec = clauseTypeToPrecedence.get(ASTNodeLabel.class);
        int subClausePrec = subClause instanceof Terminal ? clauseTypeToPrecedence.get(Terminal.class)
                : clauseTypeToPrecedence.get(subClause.getClass());
        return subClausePrec < astNodeLabelPrec;
    }

    private static int hexDigitToInt(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'a' && c <= 'f') {
            return c - 'a';
        } else if (c >= 'A' && c <= 'F') {
            return c - 'F';
        }
        throw new IllegalArgumentException("Illegal Unicode hex char: " + c);
    }

    private static char unescapeChar(String escapedChar) {
        if (escapedChar.length() == 0) {
            throw new IllegalArgumentException("Empty char string");
        } else if (escapedChar.length() == 1) {
            return escapedChar.charAt(0);
        }
        switch (escapedChar) {
        case "\\t":
            return '\t';
        case "\\b":
            return '\b';
        case "\\n":
            return '\n';
        case "\\r":
            return '\r';
        case "\\f":
            return '\f';
        case "\\'":
            return '\'';
        case "\\\"":
            return '"';
        case "\\\\":
            return '\\';
        default:
            if (escapedChar.startsWith("\\u") && escapedChar.length() == 6) {
                int c0 = hexDigitToInt(escapedChar.charAt(2));
                int c1 = hexDigitToInt(escapedChar.charAt(3));
                int c2 = hexDigitToInt(escapedChar.charAt(4));
                int c3 = hexDigitToInt(escapedChar.charAt(5));
                return (char) ((c0 << 24) | (c1 << 16) | (c2 << 8) | c3);
            } else {
                throw new IllegalArgumentException("Invalid character: " + escapedChar);
            }
        }
    }

    private static String unescapeString(String str) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\') {
                if (i == str.length() - 1) {
                    // Should not happen
                    throw new IllegalArgumentException("Got backslash at end of quoted string");
                }
                buf.append(unescapeChar(str.substring(i, i + 2)));
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }

    private static Clause expectOne(List<Clause> clauses) {
        if (clauses.size() != 1) {
            throw new IllegalArgumentException("Expected one clause, got " + clauses.size());
        }
        return clauses.get(0);
    }

    private static List<Clause> parseASTNodes(List<ASTNode> astNodes) {
        List<Clause> clauses = new ArrayList<>(astNodes.size());
        String nextNodeLabel = null;
        for (int i = 0; i < astNodes.size(); i++) {
            var astNode = astNodes.get(i);
            // Create a Clause from the ASTNode
            var clause = parseASTNode(astNode);
            if (nextNodeLabel != null) {
                // Label the Clause with the preceding label, if present
                clause = ast(nextNodeLabel, clause);
                nextNodeLabel = null;
            }
            clauses.add(clause);
        }
        return clauses;
    }

    private static Clause parseASTNode(ASTNode astNode) {
        Clause clause;
        switch (astNode.label) {
        case SEQ_AST:
            clause = seq(parseASTNodes(astNode.children));
            break;
        case FIRST_AST:
            clause = first(parseASTNodes(astNode.children));
            break;
        case LONGEST_AST:
            clause = longest(parseASTNodes(astNode.children));
            break;
        case ONE_OR_MORE_AST:
            clause = oneOrMore(expectOne(parseASTNodes(astNode.children)));
            break;
        case ZERO_OR_MORE_AST:
            clause = zeroOrMore(expectOne(parseASTNodes(astNode.children)));
            break;
        case OPTIONAL_AST:
            clause = optional(expectOne(parseASTNodes(astNode.children)));
            break;
        case FOLLOWED_BY_AST:
            clause = followedBy(expectOne(parseASTNodes(astNode.children)));
            break;
        case NOT_FOLLOWED_BY_AST:
            clause = notFollowedBy(expectOne(parseASTNodes(astNode.children)));
            break;
        case LABEL_AST:
            clause = ast(astNode.getFirstChild().getText(), parseASTNode(astNode.getSecondChild().getFirstChild()));
            break;
        case IDENT_AST:
            clause = r(astNode.getText()); // Rule name ref
            break;
        case QUOTED_STRING_AST: // Doesn't include surrounding quotes
            clause = str(unescapeString(astNode.getText()));
            break;
        case SINGLE_QUOTED_CHAR_AST:
            clause = c(unescapeChar(astNode.getText()));
            break;
        case START_AST:
            clause = start();
            break;
        case NOTHING_AST:
            clause = nothing();
            break;
        case CHAR_RANGE_AST:
            String text = unescapeString(astNode.getText());
            boolean invert = text.startsWith("^");
            if (invert) {
                text = text.substring(1);
            }
            clause = invert ? cRange(text).invert() : cRange(text);
            break;
        default:
            // Keep recursing for parens (the only type of AST node that doesn't have a label)
            clause = expectOne(parseASTNodes(astNode.children));
            break;
        }
        return clause;
    }

    private static Rule parseRule(ASTNode ruleNode, String input) {
        String ruleName = ruleNode.getFirstChild().getText();
        var hasPrecedence = ruleNode.children.size() > 2;
        var associativity = ruleNode.children.size() < 4 ? null
                : ((ruleNode.getThirdChild().label.equals(L_ASSOC_AST) ? Associativity.LEFT
                        : ruleNode.getThirdChild().label.equals(R_ASSOC_AST) ? Associativity.RIGHT : null));
        int precedence = hasPrecedence ? Integer.parseInt(ruleNode.getSecondChild().getText()) : -1;
        if (hasPrecedence && precedence < 0) {
            throw new IllegalArgumentException("Precedence needs to be zero or positive (rule " + ruleName
                    + " has precence level " + precedence + ")");
        }
        var astNode = ruleNode.getChild(ruleNode.children.size() - 1);
        Clause clause = parseASTNode(astNode);
        return rule(ruleName, precedence, associativity, clause);
    }

    public static Grammar parse(String input) {
        var memoTable = grammar.parse(input);

        //        ParserInfo.printParseResult("GRAMMAR", grammar, memoTable, input,
        //                new String[] { "GRAMMAR", "RULE", "CLAUSE[0]" }, /* showAllMatches = */ false);

        //        System.out.println("\nParsed grammar:");
        //        for (var clause : MetaGrammar.grammar.allClauses) {
        //            System.out.println("    " + clause.toStringWithRuleNames());
        //        }

        var syntaxErrors = grammar.getSyntaxErrors(memoTable, input, new String[] { GRAMMAR, RULE, CLAUSE });
        if (syntaxErrors.isEmpty()) {
            ParserInfo.printSyntaxErrors(syntaxErrors);
        }

        var topLevelMatches = grammar.getNonOverlappingMatches(GRAMMAR, memoTable);
        if (topLevelMatches.isEmpty()) {
            throw new IllegalArgumentException("Toplevel rule \"" + GRAMMAR + "\" did not match");
        } else if (topLevelMatches.size() > 1) {
            throw new IllegalArgumentException("Multiple toplevel matches");
        }
        var topLevelASTNode = topLevelMatches.get(0).toAST("<root>", input);

        // System.out.println(topLevelASTNode);

        List<Rule> rules = new ArrayList<>();
        String lexRuleName = null;
        for (ASTNode astNode : topLevelASTNode.children) {
            if (!astNode.label.equals(RULE_AST)) {
                throw new IllegalArgumentException("Wrong node type");
            }
            Rule rule = parseRule(astNode, input);
            rules.add(rule);
            if (rule.ruleName != null && rule.ruleName.equals("Lex")) {
                // If a rule is named "Lex", then use that as the toplevel lex rule
                lexRuleName = rule.ruleName;
            }
        }
        return new Grammar(lexRuleName, rules);
    }
}
