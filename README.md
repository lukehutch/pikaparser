# The Pika Parser reference implementation

This is the reference implementation of the pika parsing algorithm. Pika parsing is the inverse of packrat parsing: instead of parsing top-down, left to right, pika parsing parses right to left, bottom-up, using dynamic programming. This reversed parsing order allows the parser to directly handle left-recursive grammars, and allows the parser to optimally recover from syntax errors.

## Example usage

# Parsing code

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

# `arithmetic.grammar`:

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

# `arithmetic.input`:

```
discriminant=b*b-4*a*c;
```

# Generated parse tree:

```
└─Program <- Statement+ : 0+23 : "discriminant=b*b-4*a*c;"
  └─Statement <- var:[a-z]+ '=' E ';' : 0+23 : "discriminant=b*b-4*a*c;"
    ├─var:[a-z]+ : 0+12 : "discriminant"
    │ ├─[a-z] : 0+1 : "d"
    │ ├─[a-z] : 1+1 : "i"
    │ ├─[a-z] : 2+1 : "s"
    │ ├─[a-z] : 3+1 : "c"
    │ ├─[a-z] : 4+1 : "r"
    │ ├─[a-z] : 5+1 : "i"
    │ ├─[a-z] : 6+1 : "m"
    │ ├─[a-z] : 7+1 : "i"
    │ ├─[a-z] : 8+1 : "n"
    │ ├─[a-z] : 9+1 : "a"
    │ ├─[a-z] : 10+1 : "n"
    │ └─[a-z] : 11+1 : "t"
    ├─'=' : 12+1 : "="
    ├─E[0] <- arith:(E[0] op:('+' / '-') E[1]) / E[1] : 13+9 : "b*b-4*a*c"
    │ └─arith:(E[0] op:('+' / '-') E[1]) : 13+9 : "b*b-4*a*c"
    │   ├─E[0] <- arith:(E[0] op:('+' / '-') E[1]) / E[1] : 13+3 : "b*b"
    │   │ └─E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2] : 13+3 : "b*b"
    │   │   └─arith:(E[1] op:('*' / '/') E[2]) : 13+3 : "b*b"
    │   │     ├─E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2] : 13+1 : "b"
    │   │     │ └─E[2] <- arith:(op:'-' (E[2] / E[3])) / E[3] : 13+1 : "b"
    │   │     │   └─E[3] <- (num:[0-9]+ / sym:[a-z]+) / E[4] : 13+1 : "b"
    │   │     │     └─num:[0-9]+ / sym:[a-z]+ : 13+1 : "b"
    │   │     │       └─sym:[a-z]+ : 13+1 : "b"
    │   │     │         └─[a-z] : 13+1 : "b"
    │   │     ├─op:('*' / '/') : 14+1 : "*"
    │   │     │ └─'*' : 14+1 : "*"
    │   │     └─E[2] <- arith:(op:'-' (E[2] / E[3])) / E[3] : 15+1 : "b"
    │   │       └─E[3] <- (num:[0-9]+ / sym:[a-z]+) / E[4] : 15+1 : "b"
    │   │         └─num:[0-9]+ / sym:[a-z]+ : 15+1 : "b"
    │   │           └─sym:[a-z]+ : 15+1 : "b"
    │   │             └─[a-z] : 15+1 : "b"
    │   ├─op:('+' / '-') : 16+1 : "-"
    │   │ └─'-' : 16+1 : "-"
    │   └─E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2] : 17+5 : "4*a*c"
    │     └─arith:(E[1] op:('*' / '/') E[2]) : 17+5 : "4*a*c"
    │       ├─E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2] : 17+3 : "4*a"
    │       │ └─arith:(E[1] op:('*' / '/') E[2]) : 17+3 : "4*a"
    │       │   ├─E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2] : 17+1 : "4"
    │       │   │ └─E[2] <- arith:(op:'-' (E[2] / E[3])) / E[3] : 17+1 : "4"
    │       │   │   └─E[3] <- (num:[0-9]+ / sym:[a-z]+) / E[4] : 17+1 : "4"
    │       │   │     └─num:[0-9]+ / sym:[a-z]+ : 17+1 : "4"
    │       │   │       └─num:[0-9]+ : 17+1 : "4"
    │       │   │         └─[0-9] : 17+1 : "4"
    │       │   ├─op:('*' / '/') : 18+1 : "*"
    │       │   │ └─'*' : 18+1 : "*"
    │       │   └─E[2] <- arith:(op:'-' (E[2] / E[3])) / E[3] : 19+1 : "a"
    │       │     └─E[3] <- (num:[0-9]+ / sym:[a-z]+) / E[4] : 19+1 : "a"
    │       │       └─num:[0-9]+ / sym:[a-z]+ : 19+1 : "a"
    │       │         └─sym:[a-z]+ : 19+1 : "a"
    │       │           └─[a-z] : 19+1 : "a"
    │       ├─op:('*' / '/') : 20+1 : "*"
    │       │ └─'*' : 20+1 : "*"
    │       └─E[2] <- arith:(op:'-' (E[2] / E[3])) / E[3] : 21+1 : "c"
    │         └─E[3] <- (num:[0-9]+ / sym:[a-z]+) / E[4] : 21+1 : "c"
    │           └─num:[0-9]+ / sym:[a-z]+ : 21+1 : "c"
    │             └─sym:[a-z]+ : 21+1 : "c"
    │               └─[a-z] : 21+1 : "c"
    └─';' : 22+1 : ";"
```

# Alternative view of generated parse tree:

```
                                                       ┌─────────────────────────────────────────────┐  
                            0 : Program <- Statement+  │d i s c r i m i n a n t = b * b - 4 * a * c ;│  
                                                       ├─────────────────────────────────────────────┤  
                1 : Statement <- var:[a-z]+ '=' E ';'  │d i s c r i m i n a n t = b * b - 4 * a * c ;│  
                                                       │                         ┌─────────────────┐ │  
  3 : E[0] <- arith:(E[0] op:('+' / '-') E[1]) / E[1]  │                         │b * b - 4 * a * c│ │  
                                                       │                         ├─────────────────┤ │  
                         4 : E[0] op:('+' / '-') E[1]  │                         │b * b - 4 * a * c│ │  
                                                       │                         │       ┌─────────┤ │  
  5 : E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2]  │                         │       │4 * a * c│ │  
                                                       │                         │       ├─────────┤ │  
                         6 : E[1] op:('*' / '/') E[2]  │                         │       │4 * a * c│ │  
                                                       │                         ├─────┐ │         │ │  
  3 : E[0] <- arith:(E[0] op:('+' / '-') E[1]) / E[1]  │                         │b * b│ │         │ │  
                                                       │                         ├─────┤ ├─────┐   │ │  
  5 : E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2]  │                         │b * b│ │4 * a│   │ │  
                                                       │                         ├─────┤ ├─────┤   │ │  
                         6 : E[1] op:('*' / '/') E[2]  │                         │b * b│ │4 * a│   │ │  
                                                       │                         │     │ │     │   ├─┤  
                                   2 : [terminal] ';'  │                         │     │ │     │   │;│  
                                                       │                         ├─┐   │ ├─┐   │   │ │  
  5 : E[1] <- arith:(E[1] op:('*' / '/') E[2]) / E[2]  │                         │b│   │ │4│   │   │ │  
                                                       │                         ├─┤ ┌─┤ ├─┤ ┌─┤ ┌─┤ │  
      7 : E[2] <- arith:(op:'-' (E[2] / E[3])) / E[3]  │                         │b│ │b│ │4│ │a│ │c│ │  
                                                       │                         ├─┤ ├─┤ ├─┤ ├─┤ ├─┤ │  
        10 : E[3] <- (num:[0-9]+ / sym:[a-z]+) / E[4]  │                         │b│ │b│ │4│ │a│ │c│ │  
                                                       │                         ├─┤ ├─┤ ├─┤ ├─┤ ├─┤ │  
                         15 : num:[0-9]+ / sym:[a-z]+  │                         │b│ │b│ │4│ │a│ │c│ │  
                                                       │                         │ │ │ │ ├─┤ │ │ │ │ │  
                                          16 : [0-9]+  │                         │ │ │ │ │4│ │ │ │ │ │  
                                                       │                         │ │ │ │ ├─┤ │ │ │ │ │  
                                17 : [terminal] [0-9]  │                         │ │ │ │ │4│ │ │ │ │ │  
                                                       │                         │ ├─┤ │ │ ├─┤ ├─┤ │ │  
                                       18 : '*' / '/'  │                         │ │*│ │ │ │*│ │*│ │ │  
                                                       │                         │ ├─┤ │ │ ├─┤ ├─┤ │ │  
                                  20 : [terminal] '*'  │                         │ │*│ │ │ │*│ │*│ │ │  
                                                       │                         │ │ │ ├─┤ │ │ │ │ │ │  
                                       21 : '+' / '-'  │                         │ │ │ │-│ │ │ │ │ │ │  
                                                       │                         │ │ │ ├─┤ │ │ │ │ │ │  
                                  22 : [terminal] '-'  │                         │ │ │ │-│ │ │ │ │ │ │  
                                                       │                       ┌─┤ │ │ │ │ │ │ │ │ │ │  
                                  24 : [terminal] '='  │                       │=│ │ │ │ │ │ │ │ │ │ │  
                                                       ├───────────────────────┤ ├─┤ ├─┤ │ │ ├─┤ ├─┤ │  
                                          25 : [a-z]+  │d i s c r i m i n a n t│ │b│ │b│ │ │ │a│ │c│ │  
                                                       ├─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤ ├─┤ ├─┤ │ │ ├─┤ ├─┤ │  
                                26 : [terminal] [a-z]  │d│i│s│c│r│i│m│i│n│a│n│t│ │b│ │b│ │ │ │a│ │c│ │  
                                                        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 
                                                        d i s c r i m i n a n t = b * b - 4 * a * c ; 
```

# Generated Abstract Syntax Tree (AST):

```
└─<root> : 0+23 : "discriminant=b*b-4*a*c;"
  ├─var : 0+12 : "discriminant"
  └─arith : 13+9 : "b*b-4*a*c"
    ├─arith : 13+3 : "b*b"
    │ ├─sym : 13+1 : "b"
    │ ├─op : 14+1 : "*"
    │ └─sym : 15+1 : "b"
    ├─op : 16+1 : "-"
    └─arith : 17+5 : "4*a*c"
      ├─arith : 17+3 : "4*a"
      │ ├─num : 17+1 : "4"
      │ ├─op : 18+1 : "*"
      │ └─sym : 19+1 : "a"
      ├─op : 20+1 : "*"
      └─sym : 21+1 : "c"
```

# The use of a `Lex` preprocessing rule

If the grammar contains a rule named `Lex` of the form

```
Lex = [0-9]+ / [a-z]+ / '=' / '+' / '*' / '/';
```

then lex preprocessing is applied, wherein the above rule is matched repeatedly until all input is consumed. This can dramatically reduce the size of the memo table due to avoidance of spurious matches -- see the paper for more information.

# Error recovery

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
