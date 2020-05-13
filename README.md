# The Pika Parser reference implementation

This is the reference implementation of the pika parsing algorithm. Pika parsing is the inverse of packrat parsing: instead of parsing top-down, left to right, pika parsing parses right to left, bottom-up, using dynamic programming. This reversed parsing order allows the parser to directly handle left-recursive grammars, and allows the parser to optimally recover from syntax errors.

## Example usage

### Parsing code

```java
String grammarSpecFilename = "arithmetic.grammar";
String inputFilename = "arithmetic.input";
String topRuleName = "S";
String[] recoveryRuleNames = { topRuleName };

String grammarSpec = Files.readString(Paths.get(grammarSpecFilename));

Grammar grammar = MetaGrammar.parse(grammarSpecInput);

String input = Files.readString(Paths.get(inputFilename));

MemoTable memoTable = metaGrammar.parse(srcStr);

ParserInfo.printParseResult(topRuleName, metaGrammar, memoTable, input, recoveryRuleNames, false);
```

### `arithmetic.grammar`:

```
Program <- Statement+;
Statement <- var:[a-z]+ '=' E ';';
E[4] <- '(' E ')';
E[3] <- num:[0-9]+ / sym:[a-z]+;
E[2] <- arith:(op:'-' E);
E[1,L] <- arith:(E op:('*' / '/') E);
E[0,L] <- arith:(E op:('+' / '-') E);
```

The rules are of the form `RuleName <- [ASTNodeLabel:]Clause;`

Clauses can be of the form:

* `X Y Z` for a sequence of matches (`X` should match, followed by `Y`, followed by `Z`), i.e. `Seq`
* `X / Y / Z` for ordered choice (`X` should match, or if it doesn't, `Y` should match, or if it doesn't' `Z` should match) , i.e. `First`
* `X+` to indicate that `X` must match one or more times, i.e. `OneOrMore`
* `X*` to indicate that `X` must match zero or more times, i.e. `ZeroOrMore`
* `X?` to indicate that `X` may optionally match, i.e. `Optional`
* `&X` to look ahead and require `X` to match without consuming characters, i.e. `FollowedBy`
* `!X` to look ahead and require that there is no match (the logical negation of `&X`), i.e. `NotFollowedBy`

The number in the optional square brackets after the rule name is the precedence, followed by an optional associativity modifier (`,L` or `,R`). 

### `arithmetic.input`:

```
discriminant=b*b-4*a*c;
```

### Generated parse tree:

<img alt="Parse tree" height="625" width = "919" src="https://raw.githubusercontent.com/lukehutch/pikaparser/master/docs/ParseTree1.png">

### Alternative view of generated parse tree:

<img alt="Alternative view of parse tree" height="810" width = "723" src="https://raw.githubusercontent.com/lukehutch/pikaparser/master/docs/ParseTree2.png">

### Generated Abstract Syntax Tree (AST):

<img alt="Alternative view of parse tree" height="344" width = "229" src="https://raw.githubusercontent.com/lukehutch/pikaparser/master/docs/AST.png">

### The use of a `Lex` preprocessing rule

If the grammar contains a rule named `Lex` of the form

```
Lex = [0-9]+ / [a-z]+ / '=' / '+' / '*' / '/';
```

then lex preprocessing is applied, wherein the above rule is matched repeatedly until all input is consumed. This can dramatically reduce the size of the memo table due to avoidance of spurious matches -- see the paper for more information.

### Error recovery

You can recover from syntax errors by finding the next match of any grammar rule of interest. For example:

```
NavigableMap<Integer, MemoEntry> programEntries = grammar.getNavigableMatches("Program", memoTable);
int matchEndPosition = 0;
if (!programEntries.isEmpty()) {
    Match bestMatch = programEntries.firstEntry().getValue().bestMatch;
    if (bestMatch != null) {
        int startPos = bestMatch.memoKey.startPos;
        int len = bestMatch.len;
        matchEndPosition = startPos + len;
    }    
}
if (matchEndPosition < input.length()) {
    NavigableMap<Integer, MemoEntry> statementEntries = grammar.getNavigableMatches("Statement", memoTable);
    MemoEntry nextStatement = statementEntries.ceilingEntry(matchEndPosition);
    if (nextStatement != null) {
        Match nextStatementMatch = nextStatement.bestMatch;
        if (nextStatementMatch != null) {
            int nextStatementStartPosition = nextStatement.bestMatch.memoKey.startPos;
            // ...
        }
    }
}
```
