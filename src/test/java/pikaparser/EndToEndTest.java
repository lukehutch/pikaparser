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
package pikaparser;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static pikaparser.TestUtils.loadResourceFile;

import java.io.IOException;
import java.net.URISyntaxException;
//        import pikaparser.parser.utils.ParserInfo;

import org.junit.Test;

import parboiled.ParboiledJavaGrammar;
import pikaparser.clause.Clause;
import pikaparser.grammar.MetaGrammar;
import pikaparser.memotable.Match;
import pikaparser.parser.utils.ParserInfo;

public class EndToEndTest {

    @Test
    public void can_parse_arithmetic_example() throws IOException, URISyntaxException {
        final var grammarSpec = loadResourceFile("arithmetic.grammar");
        final var grammar = MetaGrammar.parse(grammarSpec);

        final var input = loadResourceFile("arithmetic.input");
        final var memoTable = grammar.parse(input);

        // final var topRuleName = "Program";
        // final String[] recoveryRuleNames = { topRuleName, "Statement" };
        // ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false);

        final var allClauses = memoTable.grammar.allClauses;
        assertThat(allClauses.size(), is(26));

        var firstClause = allClauses.get(0);
        var matches = memoTable.getAllMatches(firstClause);
        assertThat(matches.size(), is(16));

        final var firstMatch = matches.get(0);
        assertThat(firstMatch.len, is(1));
        assertThat(firstMatch.memoKey.startPos, is(0));
        assertThat(firstMatch.memoKey.toStringWithRuleNames(), is("[a-z] : 0"));

        final var sixteenthMatch = matches.get(15);
        assertThat(sixteenthMatch.len, is(1));
        assertThat(sixteenthMatch.memoKey.startPos, is(21));
        assertThat(sixteenthMatch.memoKey.toStringWithRuleNames(), is("[a-z] : 21"));

        final Clause lastClause = allClauses.get(25);
        matches = memoTable.getAllMatches(lastClause);

        final Match topLevelMatch = matches.get(0);
        assertThat(topLevelMatch.len, is(23));
        assertThat(topLevelMatch.memoKey.startPos, is(0));
        assertThat(topLevelMatch.toStringWithRuleNames(), is("Program <- Statement+ : 0+23"));

        assertThat(topLevelMatch.getSubClauseMatches().size(), is(1));

        final var firstSubclauseMatchOfLastMatch = topLevelMatch.getSubClauseMatches().get(0);
        assertThat(firstSubclauseMatchOfLastMatch.getKey(), nullValue());

        final String subClauseString = firstSubclauseMatchOfLastMatch.getValue().toStringWithRuleNames();
        assertThat(subClauseString, is("Statement <- var:[a-z]+ '=' E ';' : 0+23"));
    }

    @Test
    public void can_parse_java_example() throws IOException, URISyntaxException {
        final var grammarSpec = loadResourceFile("Java.1.8.peg");
        final var grammar = MetaGrammar.parse(grammarSpec);

        final var input = loadResourceFile("GrammarUtils.java");
        final var memoTable = grammar.parse(input);

        final var topRuleName = "Compilation";
        final String[] recoveryRuleNames = { topRuleName, "CompilationUnit" };

        // Huge output; only do this if you have a big buffer
        // ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false);

        // ParserInfo.printParseTreeInMemoTableForm(memoTable);

        final var syntaxErrors = memoTable.getSyntaxErrors(recoveryRuleNames);
        if (!syntaxErrors.isEmpty()) {
            ParserInfo.printSyntaxErrors(syntaxErrors);
        }
        assertThat(syntaxErrors.size(), is(0));
    }

    @Test
    public void can_parse_java_example_parboiled() throws IOException, URISyntaxException {
        final var grammar = ParboiledJavaGrammar.grammar;

        var input = loadResourceFile("GrammarUtils.java");

        // Java 6 doesn't support diamond operator or lambdas
        input = input.replaceAll("<>", "");

        final var memoTable = grammar.parse(input);

        final var topRuleName = "CompilationUnit";
        final String[] recoveryRuleNames = { topRuleName };

        // Huge output; only do this if you have a big buffer
        //ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false);

        // ParserInfo.printParseTreeInMemoTableForm(memoTable);

        final var syntaxErrors = memoTable.getSyntaxErrors(recoveryRuleNames);
        if (!syntaxErrors.isEmpty()) {
            ParserInfo.printSyntaxErrors(syntaxErrors);
        }
        assertThat(syntaxErrors.size(), is(0));
    }

}
