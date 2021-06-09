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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.function.Function;

import org.junit.Test;

import pikaparser.grammar.MetaGrammar;
import pikaparser.memotable.MemoTable;

public class Benchmark {

    @Test
    public void arithmetic_example_benchmark() throws IOException, URISyntaxException {
        final var grammarSpec = TestUtils.loadResourceFile("arithmetic.grammar");
        final var toBeParsed = TestUtils.loadResourceFile("arithmetic.input");

        final Function<String, MemoTable> parseGrammarAndParseInput = (String input) -> {
            final var grammar = MetaGrammar.parse(grammarSpec);
            return grammar.parse(input);
        };
        executeInTimedLoop(parseGrammarAndParseInput, toBeParsed, "arithmetic");
    }

    @Test
    public void grammar_loading_benchmark() throws IOException, URISyntaxException {
        final var grammarSpec = TestUtils.loadResourceFile("Java.1.8.peg");
        executeInTimedLoop(MetaGrammar::parse, grammarSpec, "java-grammar");
    }

    @Test
    public void java_parsing_benchmark() throws IOException, URISyntaxException {
        final var grammarSpec = TestUtils.loadResourceFile("Java.1.8.peg");
        final var toBeParsed = TestUtils.loadResourceFile("GrammarUtils.java");

        final var grammar = MetaGrammar.parse(grammarSpec);

        executeInTimedLoop(grammar::parse, toBeParsed, "java-parse");
    }

    private static <T> void executeInTimedLoop(Function<String, T> toExecute, String input, String benchmarkName) {
        final long[] results = new long[100];
        for (int i = 0; i < 100; i++) {
            final long start = System.nanoTime();
            toExecute.apply(input);
            results[i] = System.nanoTime() - start;
        }

        System.out.println("\n\n\n===================== RESULTS FOR " + benchmarkName + "=====================");
        System.out.println(Arrays.stream(results).mapToDouble(nano -> nano / 1_000_000_000.0).summaryStatistics());
    }
}
