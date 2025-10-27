grammar Firefly;

// Parser Rules

compilationUnit
    : packageDeclaration? importDeclaration* topLevelDeclaration* EOF
    ;

packageDeclaration
    : 'package' qualifiedName
    ;

importDeclaration
    : 'import' importPath ('::' importItems)?
    ;

importItems
    : TYPE_IDENTIFIER                                           # SingleImport
    | '{' importItem (',' importItem)* ','? '}'                # MultipleImports
    | '*'                                                       # WildcardImport
    ;

importItem
    : TYPE_IDENTIFIER ('as' TYPE_IDENTIFIER)?                  # TypeImportItem
    | IDENTIFIER ('as' IDENTIFIER)?                            # FunctionImportItem
    ;

topLevelDeclaration
    : annotation* ( functionDeclaration
                  | classDeclaration
                  | interfaceDeclaration
                  | dataDeclaration
                  | structDeclaration
                  | traitDeclaration
                  | implDeclaration
                  | typeAliasDeclaration
                  | protocolDeclaration
                  | extendDeclaration
                  | contextDeclaration
                  | supervisorDeclaration
                  | flowDeclaration
                  | macroDeclaration
                  )
    ;

// Class declaration (for Java interop and Spring Boot)
classDeclaration
    : 'class' TYPE_IDENTIFIER typeParameters? ('extends' type)? ('implements' typeList)? '{' classMember* '}'
    ;

// Interface declaration (for Java interop)
interfaceDeclaration
    : 'interface' TYPE_IDENTIFIER typeParameters? ('extends' typeList)? '{' interfaceMember* '}'
    ;

interfaceMember
    : functionSignature
    ;

classMember
    : fieldDeclaration
    | methodDeclaration
    | constructorDeclaration
    ;

fieldDeclaration
    : annotation* 'let' 'mut'? IDENTIFIER ':' type ('=' expression)? ';'
    ;

methodDeclaration
    : annotation* 'async'? 'fn' IDENTIFIER typeParameters? '(' parameterList? ')' ('->' type)? blockExpression
    ;

constructorDeclaration
    : annotation* 'init' '(' parameterList? ')' blockExpression
    ;

// Struct declaration (product types)
structDeclaration
    : 'struct' TYPE_IDENTIFIER typeParameters? '{' structField* '}'
    ;

structField
    : annotation* IDENTIFIER ':' type ('=' expression)? ','?
    ;

// Data declaration (algebraic data types / sum types)
dataDeclaration
    : 'data' TYPE_IDENTIFIER typeParameters? '{' dataVariant (',' dataVariant)* ','? '}'
    ;

dataVariant
    : TYPE_IDENTIFIER ('(' fieldList ')')?
    ;

fieldList
    : field (',' field)*
    ;

field
    : IDENTIFIER ':' type
    | type  // Unnamed field
    ;

// Trait declaration (like Rust traits)
traitDeclaration
    : 'trait' TYPE_IDENTIFIER typeParameters? '{' traitMember* '}'
    ;

traitMember
    : functionSignature
    ;

functionSignature
    : 'fn' IDENTIFIER typeParameters? '(' parameterList? ')' ('->' type)? ';'?
    ;

// Implementation block
implDeclaration
    : 'impl' typeParameters? TYPE_IDENTIFIER ('for' type)? '{' implMember* '}'
    ;

implMember
    : functionDeclaration
    ;

// Type alias
typeAliasDeclaration
    : 'type' TYPE_IDENTIFIER typeParameters? '=' type
    ;

// Protocol declaration (like Swift protocols)
protocolDeclaration
    : 'protocol' TYPE_IDENTIFIER typeParameters? (':' traitBounds)? '{' protocolMember* '}'
    ;

protocolMember
    : functionSignature
    ;

traitBounds
    : TYPE_IDENTIFIER ('+' TYPE_IDENTIFIER)*
    ;

// Extend existing types with protocols
extendDeclaration
    : 'extend' TYPE_IDENTIFIER 'for' type '{' implMember* '}'
    ;

// Context declaration (implicit parameters)
contextDeclaration
    : 'context' TYPE_IDENTIFIER '{' contextMember* '}'
    ;

contextMember
    : functionSignature
    ;

// Supervisor declaration (OTP-style)
supervisorDeclaration
    : 'supervisor' TYPE_IDENTIFIER '{' supervisorConfig '}'
    ;

supervisorConfig
    : supervisorField (',' supervisorField)*
    ;

supervisorField
    : IDENTIFIER ':' expression
    ;

// Flow declaration (reactive streams)
flowDeclaration
    : 'flow' TYPE_IDENTIFIER '{' flowMember* '}'
    ;

flowMember
    : IDENTIFIER ':' type
    | 'stage' IDENTIFIER blockExpression
    ;

// Macro declaration
macroDeclaration
    : 'macro' IDENTIFIER '(' parameterList? ')' blockExpression
    ;

// Function declaration (expression-oriented)
functionDeclaration
    : 'async'? 'fn' IDENTIFIER typeParameters? '(' parameterList? ')' ('->' type)? effectClause? requiresClause? '=' expression
    | 'async'? 'fn' IDENTIFIER typeParameters? '(' parameterList? ')' ('->' type)? effectClause? requiresClause? blockExpression
    ;

// Effect clause (compile-time effect tracking)
effectClause
    : 'with' IDENTIFIER (',' IDENTIFIER)*
    ;

// Requires clause (smart constructors)
requiresClause
    : 'requires' expression
    ;

parameterList
    : parameter (',' parameter)* ','?
    ;

parameter
    : annotation* IDENTIFIER ':' type ('=' expression)?
    | annotation* 'mut' IDENTIFIER ':' type ('=' expression)?
    | 'using' IDENTIFIER
    ;

// Type alias
typeList
    : type (',' type)*
    ;

// Types (modern, expressive)
type
    : primitiveType
    | TYPE_IDENTIFIER typeArguments?
    | '&' type                                      // Reference type
    | '&' 'mut' type                                // Mutable reference
    | '[' type ']'                                  // Array/List type
    | '[' type ':' type ']'                         // Map type
    | type '?'                                      // Optional/Nullable type
    | '(' typeList ')' '->' type                    // Function type
    | '(' type ')'
    | tupleType
    ;

primitiveType
    : 'Int'
    | 'String'
    | 'Bool'
    | 'Float'
    | 'Unit'
    ;

tupleType
    : '(' type ',' type (',' type)* ')'
    ;

typeArguments
    : '<' type (',' type)* '>'
    ;

typeParameters
    : '<' TYPE_IDENTIFIER (',' TYPE_IDENTIFIER)* '>'
    ;

// Block expression (Rust-style)
blockExpression
    : '{' statement* expression? '}'
    ;

statement
    : 'let' pattern ('=' expression)? ';'               # LetStmt
    | 'let' 'mut' pattern ('=' expression)? ';'         # LetMutStmt
    | expression '.' IDENTIFIER '=' expression ';'      # FieldAssignmentStmt
    | IDENTIFIER '=' expression ';'                     # AssignmentStmt
    | expression ';'                                    # ExprStmt
    ;

// Expressions (expression-oriented language)
expression
    : primaryExpression                                     # PrimaryExpr
    | blockExpression                                       # BlockExpr
    | ifExpression                                          # IfExpr
    | matchExpression                                       # MatchExpr
    | forExpression                                         # ForExpr
    | whileExpression                                       # WhileExpr
    | lambdaExpression                                      # LambdaExpr
    | concurrentExpression                                  # ConcurrentExpr
    | raceExpression                                        # RaceExpr
    | timeoutExpression                                     # TimeoutExpr
    | withExpression                                        # WithExpr
    | expression '.' IDENTIFIER                             # FieldAccessExpr
    | expression '.' 'class'                                # ClassLiteralExpr
    | expression '?.' IDENTIFIER                            # SafeAccessExpr
    | expression '[' expression ']'                         # IndexAccessExpr
    | expression '(' argumentList? ')'                      # CallExpr
    | expression '.await'                                   # AwaitExpr
    | expression '?'                                        # UnwrapExpr
    | expression '!!'                                       # ForceUnwrapExpr
    | expression '?:' expression                            # ElvisExpr
    | '!' expression                                        # NotExpr
    | '-' expression                                        # UnaryMinusExpr
    | '&' expression                                        # RefExpr
    | '&' 'mut' expression                                  # MutRefExpr
    | '*' expression                                        # DerefExpr
    | expression op=('*' | '/' | '%') expression            # MultiplicativeExpr
    | expression op=('+' | '-') expression                  # AdditiveExpr
    | expression op=('==' | '!=' | '<' | '>' | '<=' | '>=') expression  # ComparisonExpr
    | expression '&&' expression                            # LogicalAndExpr
    | expression '||' expression                            # LogicalOrExpr
    | expression '>>' expression                            # SendMessageExpr
    | expression '??' expression                            # CoalesceExpr
    | expression '..' expression                            # RangeExpr
    | expression '..=' expression                           # RangeInclusiveExpr
    | expression '.' IDENTIFIER '=' expression              # FieldAssignmentExpr
    | IDENTIFIER '=' expression                             # AssignmentExpr
    | 'return' expression?                                  # ReturnExpr
    | 'break'                                               # BreakExpr
    | 'continue'                                            # ContinueExpr
    ;

ifExpression
    : 'if' expression blockExpression ('else' 'if' expression blockExpression)* ('else' blockExpression)?
    ;

matchExpression
    : 'match' expression '{' matchArm (',' matchArm)* ','? '}'
    ;

matchArm
    : pattern ('when' expression)? '=>' expression
    ;

forExpression
    : 'for' pattern 'in' expression blockExpression
    ;

// Concurrent execution
concurrentExpression
    : 'concurrent' '{' concurrentBinding (',' concurrentBinding)* ','? '}'
    ;

concurrentBinding
    : 'let' IDENTIFIER '=' expression '.await'
    ;

// Race expression
raceExpression
    : 'race' blockExpression
    ;

// Timeout expression
timeoutExpression
    : 'timeout' '(' expression ')' blockExpression
    ;

// With expression (contextual parameters)
withExpression
    : 'with' argumentList blockExpression
    ;

whileExpression
    : 'while' expression blockExpression
    ;

lambdaExpression
    : '|' parameterList? '|' expression
    | '|' parameterList? '|' blockExpression
    ;

primaryExpression
    : literal
    | IDENTIFIER
    | TYPE_IDENTIFIER
    | arrayLiteral
    | mapLiteral
    | tupleLiteral
    | structLiteral
    | '(' expression ')'
    | 'self'
    | 'new' TYPE_IDENTIFIER '(' argumentList? ')'
    ;

pattern
    : literal                                               # LiteralPattern
    | IDENTIFIER                                            # VariablePattern
    | 'mut' IDENTIFIER                                      # MutableVariablePattern
    | TYPE_IDENTIFIER '{' fieldPattern (',' fieldPattern)* ','? '}'  # StructPattern
    | TYPE_IDENTIFIER '(' pattern (',' pattern)* ','? ')'   # TupleStructPattern
    | '(' pattern (',' pattern)+ ')'                        # TuplePattern
    | '[' pattern (',' pattern)* ','? ']'                   # ArrayPattern
    | '[' pattern (',' '..')? ']'                           # ArrayRestPattern
    | '_'                                                   # WildcardPattern
    | pattern '|' pattern                                   # OrPattern
    ;

fieldPattern
    : IDENTIFIER
    | IDENTIFIER ':' pattern
    ;

// Literals
literal
    : INTEGER_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | CHAR_LITERAL
    | BOOLEAN_LITERAL
    | 'none'
    ;

arrayLiteral
    : '[' (expression (',' expression)* ','?)? ']'
    ;

mapLiteral
    : '[' mapEntry (',' mapEntry)* ','? ']'
    | '[' ':' ']'  // Empty map
    ;

mapEntry
    : expression ':' expression
    ;

tupleLiteral
    : '(' expression ',' (expression (',' expression)*)? ','? ')'
    ;

structLiteral
    : TYPE_IDENTIFIER '{' (structLiteralField (',' structLiteralField)* ','?)? '}'
    ;

structLiteralField
    : IDENTIFIER ':' expression
    | IDENTIFIER  // Shorthand when variable name matches field name
    ;

argumentList
    : expression (',' expression)*
    ;

qualifiedName
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

qualifiedTypeName
    : (IDENTIFIER | TYPE_IDENTIFIER) ('.' (IDENTIFIER | TYPE_IDENTIFIER))*
    ;

importPath
    : pathSegment ('::' pathSegment)*
    ;

pathSegment
    : IDENTIFIER
    | TYPE_IDENTIFIER
    | keyword  // Allow keywords in import paths (e.g., org.springframework.context)
    ;

// Keywords that can be used as identifiers in specific contexts
keyword
    : 'context' | 'data' | 'type' | 'trait' | 'impl' | 'struct'
    | 'for' | 'in' | 'match' | 'if' | 'else' | 'while'
    | 'let' | 'fn' | 'class' | 'interface' | 'new'
    | 'async' | 'await' | 'with' | 'using' | 'when'
    | 'protocol' | 'extend' | 'supervisor' | 'flow' | 'stage'
    | 'macro' | 'break' | 'continue' | 'return'
    | 'pub' | 'priv' | 'mut' | 'self'
    | 'requires' | 'concurrent' | 'race' | 'timeout'
    | 'extends' | 'implements' | 'init'
    ;

// Annotations (for Spring Boot integration)
annotation
    : '@' qualifiedTypeName ('(' annotationArguments? ')')?
    ;

annotationArguments
    : annotationArgument (',' annotationArgument)*
    ;

annotationArgument
    : IDENTIFIER '=' literal
    | literal
    ;

// Lexer Rules

// Keywords
PACKAGE     : 'package';
IMPORT      : 'import';
FN          : 'fn';
CLASS       : 'class';
INTERFACE   : 'interface';
INIT        : 'init';
NEW         : 'new';
BREAK       : 'break';
CONTINUE    : 'continue';
EXTENDS     : 'extends';
IMPLEMENTS  : 'implements';
STRUCT      : 'struct';
DATA        : 'data';
TRAIT       : 'trait';
IMPL        : 'impl';
TYPE        : 'type';
PROTOCOL    : 'protocol';
EXTEND      : 'extend';
CONTEXT     : 'context';
SUPERVISOR  : 'supervisor';
FLOW        : 'flow';
STAGE       : 'stage';
MACRO       : 'macro';
FOR         : 'for';
MATCH       : 'match';
IF          : 'if';
ELSE        : 'else';
WHILE       : 'while';
IN          : 'in';
LET         : 'let';
MUT         : 'mut';
RETURN      : 'return';
SELF        : 'self';
PUB         : 'pub';
PRIV        : 'priv';
ASYNC       : 'async';
AWAIT       : 'await';
WITH        : 'with';
USING       : 'using';
WHEN        : 'when';
REQUIRES    : 'requires';
CONCURRENT  : 'concurrent';
RACE        : 'race';
TIMEOUT     : 'timeout';

// Literals
BOOLEAN_LITERAL : 'true' | 'false';
INTEGER_LITERAL : [0-9]+ ('_' [0-9]+)* | '0x'[0-9a-fA-F]+ | '0b'[01]+ | '0o'[0-7]+;
FLOAT_LITERAL   : [0-9]+ '.' [0-9]+ ([eE][+-]?[0-9]+)?;
STRING_LITERAL  : '"' (~["\r\n\\] | '\\' .)* '"';
CHAR_LITERAL    : '\'' (~['\r\n\\] | '\\' .) '\'';

// Identifiers
IDENTIFIER      : [a-z_][a-zA-Z0-9_]*;
TYPE_IDENTIFIER : [A-Z][a-zA-Z0-9_]*;

// Operators
ARROW           : '=>';
FAT_ARROW       : '->';
DOUBLE_COLON    : '::';
QUESTION        : '?';
DOUBLE_QUESTION : '??';
SAFE_ACCESS     : '?.';
FORCE_UNWRAP    : '!!';
SEND            : '>>';
RANGE           : '..';
RANGE_INCL      : '..=';
ELVIS           : '?:';

// Whitespace and comments
WS              : [ \t\r\n]+ -> skip;
LINE_COMMENT    : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip;
