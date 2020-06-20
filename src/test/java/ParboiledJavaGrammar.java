

import static pikaparser.parser.utils.ClauseFactory.*;

import java.util.Arrays;

import pikaparser.clause.Clause;
import pikaparser.grammar.Grammar;

public class ParboiledJavaGrammar {

    //-------------------------------------------------------------------------
    //  JLS 3.11-12  Separators, Operators
    //-------------------------------------------------------------------------

    final static Clause AT = terminal("@");
    final static Clause AND = terminal("&", cInStr("=&"));
    final static Clause ANDAND = terminal("&&");
    final static Clause ANDEQU = terminal("&=");
    final static Clause BANG = terminal("!", c('='));
    final static Clause BSR = terminal(">>>", c('='));
    final static Clause BSREQU = terminal(">>>=");
    final static Clause COLON = terminal(":");
    final static Clause COMMA = terminal(",");
    final static Clause DEC = terminal("--");
    final static Clause DIV = terminal("/", c('='));
    final static Clause DIVEQU = terminal("/=");
    final static Clause DOT = terminal(".");
    final static Clause ELLIPSIS = terminal("...");
    final static Clause EQU = terminal("=", c('='));
    final static Clause EQUAL = terminal("==");
    final static Clause GE = terminal(">=");
    final static Clause GT = terminal(">", cInStr("=>"));
    final static Clause HAT = terminal("^", c('='));
    final static Clause HATEQU = terminal("^=");
    final static Clause INC = terminal("++");
    final static Clause LBRK = terminal("[");
    final static Clause LE = terminal("<=");
    final static Clause LPAR = terminal("(");
    final static Clause LPOINT = terminal("<");
    final static Clause LT = terminal("<", cInStr("=<"));
    final static Clause LWING = terminal("{");
    final static Clause MINUS = terminal("-", cInStr("=-"));
    final static Clause MINUSEQU = terminal("-=");
    final static Clause MOD = terminal("%", c('='));
    final static Clause MODEQU = terminal("%=");
    final static Clause NOTEQUAL = terminal("!=");
    final static Clause OR = terminal("|", cInStr("=|"));
    final static Clause OREQU = terminal("|=");
    final static Clause OROR = terminal("||");
    final static Clause PLUS = terminal("+", cInStr("=+"));
    final static Clause PLUSEQU = terminal("+=");
    final static Clause QUERY = terminal("?");
    final static Clause RBRK = terminal("]");
    final static Clause RPAR = terminal(")");
    final static Clause RPOINT = terminal(">");
    final static Clause RWING = terminal("}");
    final static Clause SEMI = terminal(";");
    final static Clause SL = terminal("<<", c('='));
    final static Clause SLEQU = terminal("<<=");
    final static Clause SR = terminal(">>", cInStr("=>"));
    final static Clause SREQU = terminal(">>=");
    final static Clause STAR = terminal("*", c('='));
    final static Clause STAREQU = terminal("*=");
    final static Clause TILDA = terminal("~");
    
    final static Clause ANY = cRange('\0', '~');

    public final static Clause ASSERT = Keyword("assert");
    public final static Clause BREAK = Keyword("break");
    public final static Clause CASE = Keyword("case");
    public final static Clause CATCH = Keyword("catch");
    public final static Clause CLASS = Keyword("class");
    public final static Clause CONTINUE = Keyword("continue");
    public final static Clause DEFAULT = Keyword("default");
    public final static Clause DO = Keyword("do");
    public final static Clause ELSE = Keyword("else");
    public final static Clause ENUM = Keyword("enum");
    public final static Clause EXTENDS = Keyword("extends");
    public final static Clause FINALLY = Keyword("finally");
    public final static Clause FINAL = Keyword("final");
    public final static Clause FOR = Keyword("for");
    public final static Clause IF = Keyword("if");
    public final static Clause IMPLEMENTS = Keyword("implements");
    public final static Clause IMPORT = Keyword("import");
    public final static Clause INTERFACE = Keyword("interface");
    public final static Clause INSTANCEOF = Keyword("instanceof");
    public final static Clause NEW = Keyword("new");
    public final static Clause PACKAGE = Keyword("package");
    public final static Clause RETURN = Keyword("return");
    public final static Clause STATIC = Keyword("static");
    public final static Clause SUPER = Keyword("super");
    public final static Clause SWITCH = Keyword("switch");
    public final static Clause SYNCHRONIZED = Keyword("synchronized");
    public final static Clause THIS = Keyword("this");
    public final static Clause THROWS = Keyword("throws");
    public final static Clause THROW = Keyword("throw");
    public final static Clause TRY = Keyword("try");
    public final static Clause VOID = Keyword("void");
    public final static Clause WHILE = Keyword("while");

    static Clause Keyword(String keyword) {
        return terminal(keyword, ruleRef("LetterOrDigit"));
    }

    //-------------------------------------------------------------------------
    //  helper methods
    //-------------------------------------------------------------------------

    static Clause terminal(String string) {
        return seq(str(string), ruleRef("Spacing"));//.label('\'' + string + '\'')
    }

    static Clause terminal(String string, Clause mustNotFollow) {
        return seq(str(string), notFollowedBy(mustNotFollow), ruleRef("Spacing"));//.label('\'' + string + '\'')
    }

    //-------------------------------------------------------------------------
    //  Compilation Unit
    //-------------------------------------------------------------------------

    public static String topLevelRuleName = "CompilationUnit";
    
    public static Grammar grammar = new Grammar(Arrays.asList(

            rule("CompilationUnit", seq(ruleRef("Spacing"), optional(ruleRef("PackageDeclaration")),
                    zeroOrMore(ruleRef("ImportDeclaration")), zeroOrMore(ruleRef("TypeDeclaration")) //,
            //EOI
            )),

            rule("PackageDeclaration",
                    seq(zeroOrMore(ruleRef("Annotation")), seq(PACKAGE, ruleRef("QualifiedIdentifier"), SEMI))),

            rule("ImportDeclaration",
                    seq(IMPORT, optional(STATIC), ruleRef("QualifiedIdentifier"), optional(seq(DOT, STAR)), SEMI)),

            rule("TypeDeclaration",
                    first(seq(zeroOrMore(ruleRef("Modifier")),
                            first(ruleRef("ClassDeclaration"), ruleRef("EnumDeclaration"),
                                    ruleRef("InterfaceDeclaration"), ruleRef("AnnotationTypeDeclaration"))),
                            SEMI)),

            //-------------------------------------------------------------------------
            //  Class Declaration
            //-------------------------------------------------------------------------

            rule("ClassDeclaration",
                    seq(CLASS, ruleRef("Identifier"), optional(ruleRef("TypeParameters")),
                            optional(seq(EXTENDS, ruleRef("ClassType"))),
                            optional(seq(IMPLEMENTS, ruleRef("ClassTypeList"))), ruleRef("ClassBody"))),

            rule("ClassBody", seq(LWING, zeroOrMore(ruleRef("ClassBodyDeclaration")), RWING)),

            rule("ClassBodyDeclaration",
                    first(SEMI, seq(optional(STATIC), ruleRef("Block")),
                            seq(zeroOrMore(ruleRef("Modifier")), ruleRef("MemberDecl")))),

            rule("MemberDecl",
                    first(seq(ruleRef("TypeParameters"), ruleRef("GenericMethodOrConstructorRest")),
                            seq(ruleRef("Type"), ruleRef("Identifier"), ruleRef("MethodDeclaratorRest")),
                            seq(ruleRef("Type"), ruleRef("VariableDeclarators"), SEMI),
                            seq(VOID, ruleRef("Identifier"), ruleRef("VoidMethodDeclaratorRest")),
                            seq(ruleRef("Identifier"), ruleRef("ConstructorDeclaratorRest")),
                            ruleRef("InterfaceDeclaration"), ruleRef("ClassDeclaration"),
                            ruleRef("EnumDeclaration"), ruleRef("AnnotationTypeDeclaration"))),

            rule("GenericMethodOrConstructorRest",
                    first(seq(first(ruleRef("Type"), VOID), ruleRef("Identifier"), ruleRef("MethodDeclaratorRest")),
                            seq(ruleRef("Identifier"), ruleRef("ConstructorDeclaratorRest")))),

            rule("MethodDeclaratorRest",
                    seq(ruleRef("FormalParameters"), zeroOrMore(ruleRef("Dim")),
                            optional(seq(THROWS, ruleRef("ClassTypeList"))), first(ruleRef("MethodBody"), SEMI))),

            rule("VoidMethodDeclaratorRest",
                    seq(ruleRef("FormalParameters"), optional(seq(THROWS, ruleRef("ClassTypeList"))),
                            first(ruleRef("MethodBody"), SEMI))),

            rule("ConstructorDeclaratorRest",
                    seq(ruleRef("FormalParameters"), optional(seq(THROWS, ruleRef("ClassTypeList"))),
                            ruleRef("MethodBody"))),

            rule("MethodBody", ruleRef("Block")),

            //-------------------------------------------------------------------------
            //  Interface Declaration
            //-------------------------------------------------------------------------

            rule("InterfaceDeclaration",
                    seq(INTERFACE, ruleRef("Identifier"), optional(ruleRef("TypeParameters")),
                            optional(seq(EXTENDS, ruleRef("ClassTypeList"))), ruleRef("InterfaceBody"))),

            rule("InterfaceBody", seq(LWING, zeroOrMore(ruleRef("InterfaceBodyDeclaration")), RWING)),

            rule("InterfaceBodyDeclaration",
                    first(seq(zeroOrMore(ruleRef("Modifier")), ruleRef("InterfaceMemberDecl")), SEMI)),

            rule("InterfaceMemberDecl",
                    first(ruleRef("InterfaceMethodOrFieldDecl"), ruleRef("InterfaceGenericMethodDecl"),
                            seq(VOID, ruleRef("Identifier"), ruleRef("VoidInterfaceMethodDeclaratorsRest")),
                            ruleRef("InterfaceDeclaration"), ruleRef("AnnotationTypeDeclaration"),
                            ruleRef("ClassDeclaration"), ruleRef("EnumDeclaration"))),

            rule("InterfaceMethodOrFieldDecl",
                    seq(seq(ruleRef("Type"), ruleRef("Identifier")), ruleRef("InterfaceMethodOrFieldRest"))),

            rule("InterfaceMethodOrFieldRest",
                    first(seq(ruleRef("ConstantDeclaratorsRest"), SEMI), ruleRef("InterfaceMethodDeclaratorRest"))),

            rule("InterfaceMethodDeclaratorRest",
                    seq(ruleRef("FormalParameters"), zeroOrMore(ruleRef("Dim")),
                            optional(seq(THROWS, ruleRef("ClassTypeList"))), SEMI)),

            rule("InterfaceGenericMethodDecl",
                    seq(ruleRef("TypeParameters"), first(ruleRef("Type"), VOID), ruleRef("Identifier"),
                            ruleRef("InterfaceMethodDeclaratorRest"))),

            rule("VoidInterfaceMethodDeclaratorsRest",
                    seq(ruleRef("FormalParameters"), optional(seq(THROWS, ruleRef("ClassTypeList"))), SEMI)),

            rule("ConstantDeclaratorsRest",
                    seq(ruleRef("ConstantDeclaratorRest"), zeroOrMore(seq(COMMA, ruleRef("ConstantDeclarator"))))),

            rule("ConstantDeclarator", seq(ruleRef("Identifier"), ruleRef("ConstantDeclaratorRest"))),

            rule("ConstantDeclaratorRest", seq(zeroOrMore(ruleRef("Dim")), EQU, ruleRef("VariableInitializer"))),

            //-------------------------------------------------------------------------
            //  Enum Declaration
            //-------------------------------------------------------------------------

            rule("EnumDeclaration",
                    seq(ENUM, ruleRef("Identifier"), optional(seq(IMPLEMENTS, ruleRef("ClassTypeList"))),
                            ruleRef("EnumBody"))),

            rule("EnumBody",
                    seq(LWING, optional(ruleRef("EnumConstants")), optional(COMMA),
                            optional(ruleRef("EnumBodyDeclarations")), RWING)),

            rule("EnumConstants", seq(ruleRef("EnumConstant"), zeroOrMore(seq(COMMA, ruleRef("EnumConstant"))))),

            rule("EnumConstant",
                    seq(zeroOrMore(ruleRef("Annotation")), ruleRef("Identifier"), optional(ruleRef("Arguments")),
                            optional(ruleRef("ClassBody")))),

            rule("EnumBodyDeclarations", seq(SEMI, zeroOrMore(ruleRef("ClassBodyDeclaration")))),

            //-------------------------------------------------------------------------
            //  Variable Declarations
            //-------------------------------------------------------------------------    

            rule("LocalVariableDeclarationStatement",
                    seq(zeroOrMore(first(FINAL, ruleRef("Annotation"))), ruleRef("Type"),
                            ruleRef("VariableDeclarators"), SEMI)),

            rule("VariableDeclarators",
                    seq(ruleRef("VariableDeclarator"), zeroOrMore(seq(COMMA, ruleRef("VariableDeclarator"))))),

            rule("VariableDeclarator",
                    seq(ruleRef("Identifier"), zeroOrMore(ruleRef("Dim")),
                            optional(seq(EQU, ruleRef("VariableInitializer"))))),

            //-------------------------------------------------------------------------
            //  Formal Parameters
            //-------------------------------------------------------------------------

            rule("FormalParameters", seq(LPAR, optional(ruleRef("FormalParameterDecls")), RPAR)),

            rule("FormalParameter",
                    seq(zeroOrMore(first(FINAL, ruleRef("Annotation"))), ruleRef("Type"),
                            ruleRef("VariableDeclaratorId"))),

            rule("FormalParameterDecls",
                    seq(zeroOrMore(first(FINAL, ruleRef("Annotation"))), ruleRef("Type"),
                            ruleRef("FormalParameterDeclsRest"))),

            rule("FormalParameterDeclsRest",
                    first(seq(ruleRef("VariableDeclaratorId"),
                            optional(seq(COMMA, ruleRef("FormalParameterDecls")))),
                            seq(ELLIPSIS, ruleRef("VariableDeclaratorId")))),

            rule("VariableDeclaratorId", seq(ruleRef("Identifier"), zeroOrMore(ruleRef("Dim")))),

            //-------------------------------------------------------------------------
            //  Statements
            //-------------------------------------------------------------------------    

            rule("Block", seq(LWING, ruleRef("BlockStatements"), RWING)),

            rule("BlockStatements", zeroOrMore(ruleRef("BlockStatement"))),

            rule("BlockStatement", first(ruleRef("LocalVariableDeclarationStatement"),
                    seq(zeroOrMore(ruleRef("Modifier")),
                            first(ruleRef("ClassDeclaration"), ruleRef("EnumDeclaration"))),
                    ruleRef("Statement"))),

            rule("Statement", first(ruleRef("Block"),
                    seq(ASSERT, ruleRef("Expression"), optional(seq(COLON, ruleRef("Expression"))), SEMI),
                    seq(IF, ruleRef("ParExpression"), ruleRef("Statement"),
                            optional(seq(ELSE, ruleRef("Statement")))),
                    seq(FOR, LPAR, optional(ruleRef("ForInit")), SEMI, optional(ruleRef("Expression")), SEMI,
                            optional(ruleRef("ForUpdate")), RPAR, ruleRef("Statement")),
                    seq(FOR, LPAR, ruleRef("FormalParameter"), COLON, ruleRef("Expression"), RPAR,
                            ruleRef("Statement")),
                    seq(WHILE, ruleRef("ParExpression"), ruleRef("Statement")),
                    seq(DO, ruleRef("Statement"), WHILE, ruleRef("ParExpression"), SEMI),
                    seq(TRY, ruleRef("Block"),
                            first(seq(oneOrMore(ruleRef("Catch_")), optional(ruleRef("Finally_"))),
                                    ruleRef("Finally_"))),
                    seq(SWITCH, ruleRef("ParExpression"), LWING, ruleRef("SwitchBlockStatementGroups"), RWING),
                    seq(SYNCHRONIZED, ruleRef("ParExpression"), ruleRef("Block")),
                    seq(RETURN, optional(ruleRef("Expression")), SEMI), seq(THROW, ruleRef("Expression"), SEMI),
                    seq(BREAK, optional(ruleRef("Identifier")), SEMI),
                    seq(CONTINUE, optional(ruleRef("Identifier")), SEMI),
                    seq(seq(ruleRef("Identifier"), COLON), ruleRef("Statement")),
                    seq(ruleRef("StatementExpression"), SEMI), SEMI)),

            rule("Catch_", seq(CATCH, LPAR, ruleRef("FormalParameter"), RPAR, ruleRef("Block"))),

            rule("Finally_", seq(FINALLY, ruleRef("Block"))),

            rule("SwitchBlockStatementGroups", zeroOrMore(ruleRef("SwitchBlockStatementGroup"))),

            rule("SwitchBlockStatementGroup", seq(ruleRef("SwitchLabel"), ruleRef("BlockStatements"))),

            rule("SwitchLabel",
                    first(seq(CASE, ruleRef("ConstantExpression"), COLON),
                            seq(CASE, ruleRef("EnumConstantName"), COLON), seq(DEFAULT, COLON))),

            rule("ForInit", first(
                    seq(zeroOrMore(first(FINAL, ruleRef("Annotation"))), ruleRef("Type"),
                            ruleRef("VariableDeclarators")),
                    seq(ruleRef("StatementExpression"), zeroOrMore(seq(COMMA, ruleRef("StatementExpression")))))),

            rule("ForUpdate",
                    seq(ruleRef("StatementExpression"), zeroOrMore(seq(COMMA, ruleRef("StatementExpression"))))),

            rule("EnumConstantName", ruleRef("Identifier")),

            //-------------------------------------------------------------------------
            //  Expressions
            //-------------------------------------------------------------------------

            // The following is more generous than the definition in section 14.8,
            // which allows only specific forms of Expression.

            rule("StatementExpression", ruleRef("Expression")),

            rule("ConstantExpression", ruleRef("Expression")),

            // The following definition is part of the modification in JLS Chapter 18
            // to minimize look ahead. In JLS Chapter 15.27, Expression is defined
            // as AssignmentExpression, which is effectively defined as
            // (LeftHandSide AssignmentOperator)* ConditionalExpression.
            // The following is obtained by allowing ANY ConditionalExpression
            // as LeftHandSide, which results in accepting statements like 5 = a.

            rule("Expression",
                    seq(ruleRef("ConditionalExpression"),
                            zeroOrMore(seq(ruleRef("AssignmentOperator"), ruleRef("ConditionalExpression"))))),

            rule("AssignmentOperator",
                    first(EQU, PLUSEQU, MINUSEQU, STAREQU, DIVEQU, ANDEQU, OREQU, HATEQU, MODEQU, SLEQU, SREQU,
                            BSREQU)),

            rule("ConditionalExpression",
                    seq(ruleRef("ConditionalOrExpression"),
                            zeroOrMore(
                                    seq(QUERY, ruleRef("Expression"), COLON, ruleRef("ConditionalOrExpression"))))),

            rule("ConditionalOrExpression",
                    seq(ruleRef("ConditionalAndExpression"),
                            zeroOrMore(seq(OROR, ruleRef("ConditionalAndExpression"))))),

            rule("ConditionalAndExpression",
                    seq(ruleRef("InclusiveOrExpression"),
                            zeroOrMore(seq(ANDAND, ruleRef("InclusiveOrExpression"))))),

            rule("InclusiveOrExpression",
                    seq(ruleRef("ExclusiveOrExpression"), zeroOrMore(seq(OR, ruleRef("ExclusiveOrExpression"))))),

            rule("ExclusiveOrExpression",
                    seq(ruleRef("AndExpression"), zeroOrMore(seq(HAT, ruleRef("AndExpression"))))),

            rule("AndExpression",
                    seq(ruleRef("EqualityExpression"), zeroOrMore(seq(AND, ruleRef("EqualityExpression"))))),

            rule("EqualityExpression",
                    seq(ruleRef("RelationalExpression"),
                            zeroOrMore(seq(first(EQUAL, NOTEQUAL), ruleRef("RelationalExpression"))))),

            rule("RelationalExpression",
                    seq(ruleRef("ShiftExpression"),
                            zeroOrMore(first(seq(first(LE, GE, LT, GT), ruleRef("ShiftExpression")),
                                    seq(INSTANCEOF, ruleRef("ReferenceType")))))),

            rule("ShiftExpression",
                    seq(ruleRef("AdditiveExpression"),
                            zeroOrMore(seq(first(SL, SR, BSR), ruleRef("AdditiveExpression"))))),

            rule("AdditiveExpression",
                    seq(ruleRef("MultiplicativeExpression"),
                            zeroOrMore(seq(first(PLUS, MINUS), ruleRef("MultiplicativeExpression"))))),

            rule("MultiplicativeExpression",
                    seq(ruleRef("UnaryExpression"),
                            zeroOrMore(seq(first(STAR, DIV, MOD), ruleRef("UnaryExpression"))))),

            rule("UnaryExpression",
                    first(seq(ruleRef("PrefixOp"), ruleRef("UnaryExpression")),
                            seq(LPAR, ruleRef("Type"), RPAR, ruleRef("UnaryExpression")),
                            seq(ruleRef("Primary"), zeroOrMore(ruleRef("Selector")),
                                    zeroOrMore(ruleRef("PostFixOp"))))),

            rule("Primary", first(ruleRef("ParExpression"),
                    seq(ruleRef("NonWildcardTypeArguments"),
                            first(ruleRef("ExplicitGenericInvocationSuffix"), seq(THIS, ruleRef("Arguments")))),
                    seq(THIS, optional(ruleRef("Arguments"))), seq(SUPER, ruleRef("SuperSuffix")),
                    ruleRef("Literal"), seq(NEW, ruleRef("Creator")),
                    seq(ruleRef("QualifiedIdentifier"), optional(ruleRef("IdentifierSuffix"))),
                    seq(ruleRef("BasicType"), zeroOrMore(ruleRef("Dim")), DOT, CLASS), seq(VOID, DOT, CLASS))),

            rule("IdentifierSuffix", first(
                    seq(LBRK, first(
                            seq(RBRK, zeroOrMore(ruleRef("Dim")), DOT, CLASS), seq(ruleRef("Expression"), RBRK))),
                    ruleRef("Arguments"),
                    seq(DOT, first(CLASS, ruleRef("ExplicitGenericInvocation"), THIS,
                            seq(SUPER, ruleRef("Arguments")),
                            seq(NEW, optional(ruleRef("NonWildcardTypeArguments")), ruleRef("InnerCreator")))))),

            rule("ExplicitGenericInvocation",
                    seq(ruleRef("NonWildcardTypeArguments"), ruleRef("ExplicitGenericInvocationSuffix"))),

            rule("NonWildcardTypeArguments",
                    seq(LPOINT, ruleRef("ReferenceType"), zeroOrMore(seq(COMMA, ruleRef("ReferenceType"))),
                            RPOINT)),

            rule("ExplicitGenericInvocationSuffix",
                    first(seq(SUPER, ruleRef("SuperSuffix")), seq(ruleRef("Identifier"), ruleRef("Arguments")))),

            rule("PrefixOp", first(INC, DEC, BANG, TILDA, PLUS, MINUS)),

            rule("PostFixOp", first(INC, DEC)),

            rule("Selector",
                    first(seq(DOT, ruleRef("Identifier"), optional(ruleRef("Arguments"))),
                            seq(DOT, ruleRef("ExplicitGenericInvocation")), seq(DOT, THIS),
                            seq(DOT, SUPER, ruleRef("SuperSuffix")),
                            seq(DOT, NEW, optional(ruleRef("NonWildcardTypeArguments")), ruleRef("InnerCreator")),
                            ruleRef("DimExpr"))),

            rule("SuperSuffix",
                    first(ruleRef("Arguments"), seq(DOT, ruleRef("Identifier"), optional(ruleRef("Arguments"))))),

            //@MemoMismatches
            rule("BasicType",
                    seq(first(str("byte"), str("short"), str("char"), str("int"), str("long"), str("float"),
                            str("double"), str("boolean")), notFollowedBy(ruleRef("LetterOrDigit")),
                            ruleRef("Spacing"))),

            rule("Arguments",
                    seq(LPAR, optional(seq(ruleRef("Expression"), zeroOrMore(seq(COMMA, ruleRef("Expression"))))),
                            RPAR)),

            rule("Creator", first(
                    seq(optional(ruleRef("NonWildcardTypeArguments")), ruleRef("CreatedName"),
                            ruleRef("ClassCreatorRest")),
                    seq(optional(ruleRef("NonWildcardTypeArguments")),
                            first(ruleRef("ClassType"), ruleRef("BasicType")), ruleRef("ArrayCreatorRest")))),

            rule("CreatedName", seq(ruleRef("Identifier"), optional(ruleRef("NonWildcardTypeArguments")),
                    zeroOrMore(seq(DOT, ruleRef("Identifier"), optional(ruleRef("NonWildcardTypeArguments")))))),

            rule("InnerCreator", seq(ruleRef("Identifier"), ruleRef("ClassCreatorRest"))),

            // The following is more generous than JLS 15.10. According to that definition,
            // BasicType must be followed by at least one DimExpr or by ArrayInitializer.
            rule("ArrayCreatorRest",
                    seq(LBRK,
                            first(seq(RBRK, zeroOrMore(ruleRef("Dim")), ruleRef("ArrayInitializer")),
                                    seq(ruleRef("Expression"), RBRK, zeroOrMore(ruleRef("DimExpr")),
                                            zeroOrMore(ruleRef("Dim")))))),

            rule("ClassCreatorRest", seq(ruleRef("Arguments"), optional(ruleRef("ClassBody")))),

            rule("ArrayInitializer", seq(LWING,
                    optional(seq(ruleRef("VariableInitializer"),
                            zeroOrMore(seq(COMMA, ruleRef("VariableInitializer"))))),
                    optional(COMMA), RWING)),

            rule("VariableInitializer", first(ruleRef("ArrayInitializer"), ruleRef("Expression"))),

            rule("ParExpression", seq(LPAR, ruleRef("Expression"), RPAR)),

            rule("QualifiedIdentifier", seq(ruleRef("Identifier"), zeroOrMore(seq(DOT, ruleRef("Identifier"))))),

            rule("Dim", seq(LBRK, RBRK)),

            rule("DimExpr", seq(LBRK, ruleRef("Expression"), RBRK)),

            //-------------------------------------------------------------------------
            //  Types and Modifiers
            //-------------------------------------------------------------------------

            rule("Type", seq(first(ruleRef("BasicType"), ruleRef("ClassType")), zeroOrMore(ruleRef("Dim")))),

            rule("ReferenceType",
                    first(seq(ruleRef("BasicType"), oneOrMore(ruleRef("Dim"))),
                            seq(ruleRef("ClassType"), zeroOrMore(ruleRef("Dim"))))),

            rule("ClassType",
                    seq(ruleRef("Identifier"), optional(ruleRef("TypeArguments")),
                            zeroOrMore(seq(DOT, ruleRef("Identifier"), optional(ruleRef("TypeArguments")))))),

            rule("ClassTypeList", seq(ruleRef("ClassType"), zeroOrMore(seq(COMMA, ruleRef("ClassType"))))),

            rule("TypeArguments",
                    seq(LPOINT, ruleRef("TypeArgument"), zeroOrMore(seq(COMMA, ruleRef("TypeArgument"))), RPOINT)),

            rule("TypeArgument",
                    first(ruleRef("ReferenceType"),
                            seq(QUERY, optional(seq(first(EXTENDS, SUPER), ruleRef("ReferenceType")))))),

            rule("TypeParameters",
                    seq(LPOINT, ruleRef("TypeParameter"), zeroOrMore(seq(COMMA, ruleRef("TypeParameter"))),
                            RPOINT)),

            rule("TypeParameter", seq(ruleRef("Identifier"), optional(seq(EXTENDS, ruleRef("Bound"))))),

            rule("Bound", seq(ruleRef("ClassType"), zeroOrMore(seq(AND, ruleRef("ClassType"))))),

            // the following common definition of Modifier is part of the modification
            // in JLS Chapter 18 to minimize look ahead. The main body of JLS has
            // different lists of modifiers for different language elements.
            rule("Modifier", first(ruleRef("Annotation"),
                    seq(first(str("public"), str("protected"), str("private"), str("static"), str("abstract"),
                            str("final"), str("native"), str("synchronized"), str("transient"), str("volatile"),
                            str("strictfp")), notFollowedBy(ruleRef("LetterOrDigit")), ruleRef("Spacing")))),

            //-------------------------------------------------------------------------
            //  Annotations
            //-------------------------------------------------------------------------    

            rule("AnnotationTypeDeclaration",
                    seq(AT, INTERFACE, ruleRef("Identifier"), ruleRef("AnnotationTypeBody"))),

            rule("AnnotationTypeBody", seq(LWING, zeroOrMore(ruleRef("AnnotationTypeElementDeclaration")), RWING)),

            rule("AnnotationTypeElementDeclaration",
                    first(seq(zeroOrMore(ruleRef("Modifier")), ruleRef("AnnotationTypeElementRest")), SEMI)),

            rule("AnnotationTypeElementRest",
                    first(seq(ruleRef("Type"), ruleRef("AnnotationMethodOrConstantRest"), SEMI),
                            ruleRef("ClassDeclaration"), ruleRef("EnumDeclaration"),
                            ruleRef("InterfaceDeclaration"), ruleRef("AnnotationTypeDeclaration"))),

            rule("AnnotationMethodOrConstantRest",
                    first(ruleRef("AnnotationMethodRest"), ruleRef("AnnotationConstantRest"))),

            rule("AnnotationMethodRest", seq(ruleRef("Identifier"), LPAR, RPAR, optional(ruleRef("DefaultValue")))),

            rule("AnnotationConstantRest", ruleRef("VariableDeclarators")),

            rule("DefaultValue", seq(DEFAULT, ruleRef("ElementValue"))),

            //@MemoMismatches
            rule("Annotation", seq(AT, ruleRef("QualifiedIdentifier"), optional(ruleRef("AnnotationRest")))),

            rule("AnnotationRest", first(ruleRef("NormalAnnotationRest"), ruleRef("SingleElementAnnotationRest"))),

            rule("NormalAnnotationRest", seq(LPAR, optional(ruleRef("ElementValuePairs")), RPAR)),

            rule("ElementValuePairs",
                    seq(ruleRef("ElementValuePair"), zeroOrMore(seq(COMMA, ruleRef("ElementValuePair"))))),

            rule("ElementValuePair", seq(ruleRef("Identifier"), EQU, ruleRef("ElementValue"))),

            rule("ElementValue",
                    first(ruleRef("ConditionalExpression"), ruleRef("Annotation"),
                            ruleRef("ElementValueArrayInitializer"))),

            rule("ElementValueArrayInitializer",
                    seq(LWING, optional(ruleRef("ElementValues")), optional(COMMA), RWING)),

            rule("ElementValues", seq(ruleRef("ElementValue"), zeroOrMore(seq(COMMA, ruleRef("ElementValue"))))),

            rule("SingleElementAnnotationRest", seq(LPAR, ruleRef("ElementValue"), RPAR)),

            //-------------------------------------------------------------------------
            //  JLS 3.6-7  Spacing
            //-------------------------------------------------------------------------

            //@SuppressNode
            rule("Spacing", zeroOrMore(first(

                    // whitespace
                    oneOrMore(cInStr(" \t\r\n\f")),

                    // traditional comment
                    seq(str("/*"), zeroOrMore(seq(notFollowedBy(str("*/")), ANY)), str("*/")),

                    // end of line comment
                    seq(str("//"), zeroOrMore(seq(notFollowedBy(cInStr("\r\n")), ANY)),
                            first(str("\r\n"), c('\r'), c('\n') /* , EOI */))))),

            //-------------------------------------------------------------------------
            //  JLS 3.8  Identifiers
            //-------------------------------------------------------------------------

            //@SuppressSubnodes
            //@MemoMismatches
            rule("Identifier",
                    seq(notFollowedBy(ruleRef("Keyword")), ruleRef("Letter"), zeroOrMore(ruleRef("LetterOrDigit")),
                            ruleRef("Spacing"))),

            // JLS defines letters and digits as Unicode characters recognized
            // as such by special Java procedures.

            rule("Letter",
                    // switch to this "reduced" character space version for a ~10% parser performance speedup
                    first(cRange('a', 'z'), cRange('A', 'Z'), c('_'), c('$'))
            //return first(seq(c('\\'), ruleRef("UnicodeEscape")), new ruleRef("JavaLetterMatcher")),
            ),

            rule("LetterOrDigit",
                    // switch to this "reduced" character space version for a ~10% parser performance speedup
                    first(cRange('a', 'z'), cRange('A', 'Z'), cRange('0', '9'), c('_'), c('$'))
            //return first(seq('\\', ruleRef("UnicodeEscape")), new ruleRef("JavaLetterOrDigitMatcher")),
            ),

            //-------------------------------------------------------------------------
            //  JLS 3.9  Keywords
            //-------------------------------------------------------------------------

            rule("Keyword",
                    seq(first(str("assert"), str("break"), str("case"), str("catch"), str("class"), str("const"),
                            str("continue"), str("default"), str("do"), str("else"), str("enum"), str("extends"),
                            str("finally"), str("final"), str("for"), str("goto"), str("if"), str("implements"),
                            str("import"), str("interface"), str("instanceof"), str("new"), str("package"),
                            str("return"), str("static"), str("super"), str("switch"), str("synchronized"),
                            str("this"), str("throws"), str("throw"), str("try"), str("void"), str("while")),
                            notFollowedBy(ruleRef("LetterOrDigit")))),

            //-------------------------------------------------------------------------
            //  JLS 3.10  Literals
            //-------------------------------------------------------------------------

            rule("Literal",
                    seq(first(ruleRef("FloatLiteral"), ruleRef("IntegerLiteral"), ruleRef("CharLiteral"),
                            ruleRef("StringLiteral"), seq(str("true"), notFollowedBy(ruleRef("LetterOrDigit"))),
                            seq(str("false"), notFollowedBy(ruleRef("LetterOrDigit"))),
                            seq(str("null"), notFollowedBy(ruleRef("LetterOrDigit")))), ruleRef("Spacing"))),

            //@SuppressSubnodes
            rule("IntegerLiteral",
                    seq(first(ruleRef("HexNumeral"), ruleRef("OctalNumeral"), ruleRef("DecimalNumeral")),
                            optional(cInStr("lL")))),

            //@SuppressSubnodes
            rule("DecimalNumeral", first(c('0'), seq(cRange('1', '9'), zeroOrMore(ruleRef("Digit"))))),

            //@SuppressSubnodes

            //@MemoMismatches
            rule("HexNumeral", seq(c('0'), c('x', 'X'), oneOrMore(ruleRef("HexDigit")))),

            rule("HexDigit", first(cRange('a', 'f'), cRange('A', 'F'), cRange('0', '9'))),

            //@SuppressSubnodes
            rule("OctalNumeral", seq(c('0'), oneOrMore(cRange('0', '7')))),

            rule("FloatLiteral", first(ruleRef("HexFloat"), ruleRef("DecimalFloat"))),

            //@SuppressSubnodes
            rule("DecimalFloat",
                    first(seq(oneOrMore(ruleRef("Digit")), c('.'), zeroOrMore(ruleRef("Digit")),
                            optional(ruleRef("Exponent")), optional(cInStr("fFdD"))),
                            seq(c('.'), oneOrMore(ruleRef("Digit")), optional(ruleRef("Exponent")),
                                    optional(cInStr("fFdD"))),
                            seq(oneOrMore(ruleRef("Digit")), ruleRef("Exponent"), optional(cInStr("fFdD"))),
                            seq(oneOrMore(ruleRef("Digit")), optional(ruleRef("Exponent")), cInStr("fFdD")))),

            rule("Exponent", seq(cInStr("eE"), optional(cInStr("+-")), oneOrMore(ruleRef("Digit")))),

            rule("Digit", cRange('0', '9')),

            //@SuppressSubnodes
            rule("HexFloat", seq(ruleRef("HexSignificant"), ruleRef("BinaryExponent"), optional(cInStr("fFdD")))),

            rule("HexSignificant",
                    first(seq(first(str("0x"), str("0X")), zeroOrMore(ruleRef("HexDigit")), c('.'),
                            oneOrMore(ruleRef("HexDigit"))), seq(ruleRef("HexNumeral"), optional(c('.'))))),

            rule("BinaryExponent", seq(cInStr("pP"), optional(cInStr("+-")), oneOrMore(ruleRef("Digit")))),

            rule("CharLiteral", seq(c('\''), first(ruleRef("Escape"), seq(notFollowedBy(cInStr("'\\")), ANY)), //.ruleRef("suppressSubnodes"),
                    c('\''))),

            rule("StringLiteral",
                    seq(c('"'), zeroOrMore(first(ruleRef("Escape"), seq(notFollowedBy(cInStr("\r\n\"\\")), ANY))), //.ruleRef("suppressSubnodes"),
                            c('"'))),

            rule("Escape",
                    seq(c('\\'), first(cInStr("btnfr\"\'\\"), ruleRef("OctalEscape"), ruleRef("UnicodeEscape")))),

            rule("OctalEscape",
                    first(seq(cRange('0', '3'), cRange('0', '7'), cRange('0', '7')),
                            seq(cRange('0', '7'), cRange('0', '7')), cRange('0', '7'))),

            rule("UnicodeEscape", seq(oneOrMore(c('u')), ruleRef("HexDigit"), ruleRef("HexDigit"),
                    ruleRef("HexDigit"), ruleRef("HexDigit")))

    ));
}