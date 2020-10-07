# The Pika Parser reference implementation

This is the reference implementation of the pika parsing algorithm, described in the paper:

[Pika parsing: reformulating packrat parsing as a dynamic programming algorithm solves the left recursion and error recovery problems. Luke A. D. Hutchison, May 2020.](https://arxiv.org/abs/2005.06444)

Pika parsing is the inverse of packrat parsing: instead of parsing top-down, left to right, pika parsing parses bottom-up, right to left, using dynamic programming. This reversed parsing order allows the parser to directly handle left-recursive grammars, and allows the parser to optimally recover from syntax errors.

## Example usage

### Parsing code

```java
String grammarSpecFilename = "arithmetic.grammar";
String inputFilename = "arithmetic.input";
String topLevelRuleName = "Program";
String[] recoveryRuleNames = { topLevelRuleName, "Statement" };

String grammarSpec = Files.readString(Paths.get(grammarSpecFilename));

Grammar grammar = MetaGrammar.parse(grammarSpec);

String input = Files.readString(Paths.get(inputFilename));

MemoTable memoTable = grammar.parse(input);

ParserInfo.printParseResult(topLevelRuleName, memoTable, recoveryRuleNames, false);
```

### Grammar description file: `arithmetic.grammar`

```
Program <- Statement+;
Statement <- var:[a-z]+ '=' E ';';
E[4] <- '(' E ')';
E[3] <- num:[0-9]+ / sym:[a-z]+;
E[2] <- arith:(op:'-' E);
E[1,L] <- arith:(E op:('*' / '/') E);
E[0,L] <- arith:(E op:('+' / '-') E);
```

The rules are of the form `RuleName <- Clause;`. AST node labels may be specified in the form `RuleName <- ASTNodeLabel:Clause;`. The rule name may be followed by optional square brackets containing the precedence of the rule (as an integer), optionally followed by an associativity modifier (`,L` or `,R`). 

Nonterminal clauses can be specified using the following notation:

* `X Y Z` for a sequence of matches (`X` should match, followed by `Y`, followed by `Z`), i.e. `Seq`
* `X / Y / Z` for ordered choice (`X` should match, or if it doesn't, `Y` should match, or if it doesn't' `Z` should match) , i.e. `First`
* `X+` to indicate that `X` must match one or more times, i.e. `OneOrMore`
* `X*` to indicate that `X` must match zero or more times, i.e. `ZeroOrMore`
* `X?` to indicate that `X` may optionally match, i.e. `Optional`
* `&X` to look ahead and require `X` to match without consuming characters, i.e. `FollowedBy`
* `!X` to look ahead and require that there is no match (the logical negation of `&X`), i.e. `NotFollowedBy`

Terminal clauses can be specified using the following notation. Standard Java-style character escaping is supported, including for Unicode codepoints.

* `'['` for individual characters
* `"import"` for strings of characters
* `[0-9]` for character ranges
* `[+\-*/]` for character sets (note `-` is escaped)
* `[^\n]` for negated character sets (note that this will slow down the parser, since a negated matching rule will spuriously match in many more places)

### Input string to parse: `arithmetic.input`

```
discriminant=b*b-4*a*c;
```

### Generated parse tree:

<p align="center">
<img alt="Parse tree" width="625" height="919" src="https://raw.githubusercontent.com/lukehutch/pikaparser/master/docs/ParseTree1.png">
</p>

### Alternative view of generated parse tree:

<p align="center">
<img alt="Alternative view of parse tree" width="801" height="720" src="https://raw.githubusercontent.com/lukehutch/pikaparser/master/docs/ParseTree2.png">
</p>

### Generated Abstract Syntax Tree (AST):

<p align="center">
<img alt="Alternative view of parse tree" width="344" height="229" src="https://raw.githubusercontent.com/lukehutch/pikaparser/master/docs/AST.png">
</p>

### Printing syntax errors

To find syntax errors, call:

```
NavigableMap<Integer, Entry<Integer, String>> syntaxErrors =
        memoTable.getSyntaxErrors(grammar, input, "Program", "Statement", "Expr");
```

or similar (list the names of all all the grammar rules that should span all of the input in the last varargs parameter). Any character range that is not spanned by a match of one of the named rules is returned in the result as a syntax error. You can print out the characters in those ranges as syntax errors. The entries in the returned `NavigableMap` have as the key the start position of a syntax error (a zero-indexed character position from the beginning of the string), and as the value an entry consisting of the end position of the syntax error and the span of the input between the start position and the end position.  


### Error recovery

You can recover from syntax errors by finding the next match of any grammar rule of interest after the syntax error (i.e. after the end of the last character matched by a previous grammar rule). For example:

```
NavigableMap<Integer, Match> programEntries = grammar.getNavigableMatches("Program", memoTable);
int matchEndPosition = 0;
if (!programEntries.isEmpty()) {
    Match programMatch = programEntries.firstEntry().getValue();
    if (programMatch != null) {
        int startPos = programMatch.memoKey.startPos;
        int len = programMatch.len;
        matchEndPosition = startPos + len;
    }
}
if (matchEndPosition < input.length()) {
    NavigableMap<Integer, Match> statementEntries = grammar.getNavigableMatches("Statement", memoTable);
    Entry<Integer, Match> nextStatementEntry = statementEntries.ceilingEntry(matchEndPosition);
    if (nextStatementEntry!= null) {
        Match nextStatementMatch = nextStatementEntry.getValue();
        // ...
    }
}
```
