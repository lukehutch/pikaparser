package pikaparser;

import pikaparser.grammar.Grammar;
import pikaparser.grammar.MetaGrammar;
import pikaparser.parser.utils.ParserInfo;

public class TestArithmetic {

    //	public static void main(String[] args) throws IOException, URISyntaxException {
    //		final var grammar = MetaGrammar.parse("Program <- Statement+;\n" //
    //				+ "Statement <- var:[a-z]+ '=' E ';';\n" //
    //				+ "E[4] <- '(' E ')';\n" //
    //				+ "E[3] <- num:[0-9]+ / sym:[a-z]+;\n" //
    //				+ "E[2] <- arith:(op:'-' E);\n" //
    //				+ "E[1,R] <- arith:(E op:('*' / '/') E);\n" //
    //				+ "E[0,R] <- arith:(E op:('+' / '-') E);");
    //
    //		final var memoTable = grammar.parse("x=a+b-c;");
    //		ParserInfo.printParseResult("Program", memoTable, new String[] { "Statement" }, false);
    //	}

    private static void tryParsing(Grammar grammar, String topRuleName, String[] syntaxErrCoverageRules,
            String input) {
        final var memoTable = grammar.parse(input);
        ParserInfo.printParseResult(topRuleName, memoTable, syntaxErrCoverageRules, false);
    }

    public static void main(String[] args) {
        final var grammar1 = MetaGrammar.parse("Program <- Statement+;\n" //
                + "Statement <- var:[a-z]+ '=' Sum ';';\n" //
                + "Sum <- add:(Sum '+' Term) / sub:(Sum '-' Term) / term:Term;\n" //
                + "Term <- num:[0-9]+ / sym:[a-z]+;\n");
        tryParsing(grammar1, "Program", new String[] { "Statement" }, "x=a+b-c;"); // Doesn't work
        tryParsing(grammar1, "Program", new String[] { "Statement" }, "x=a-b+c;"); // Works

        var grammar2 = MetaGrammar.parse("E <- sum:(E op:'+' E) / N;\n" // R assoc (ambiguous)
                + "N <- num:[0-9]+;\n");
        tryParsing(grammar2, "E", new String[] { "E", "N" }, "0+1+2+3;");

        var grammar3 = MetaGrammar.parse("E <- sum:(E op:'+' N) / N;\n" // L assoc
                + "N <- num:[0-9]+;\n");
        tryParsing(grammar3, "E", new String[] { "E", "N" }, "0+1+2+3;");

        // Pika parser can't parse this, because it memoizes at the level of individual clauses, not rules:
        // https://github.com/lukehutch/pikaparser/issues/32#issuecomment-861895166
        var grammar4 = MetaGrammar.parse("A <- a:(B / 'x'); B <- b:(A 'y' / A 'x');");
        tryParsing(grammar4, "A", new String[] { "A", "B" }, "xxyx");

        var grammar5 = MetaGrammar.parse("A <- \"a \" B \"monkeyapples\"; B <- \"million \" / \"million monkey\";");
        tryParsing(grammar5, "A", new String[] { "A", "B" }, "a million monkeyapples");

    }

}
