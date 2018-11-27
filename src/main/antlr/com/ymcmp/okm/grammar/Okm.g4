grammar Okm;

WHITESPACE: [ \t\r\n] -> Channel(HIDDEN);
S_COMMENT: '#' ~[\r\n]* -> Channel(HIDDEN);
M_COMMENT: '<#' .*? '#>' -> Channel(HIDDEN);

IMPORT: 'import';
NATIVE: 'native';
IF: 'if';
ELSE: 'else';
WHILE: 'while';

PUBLIC: 'public';
PROTECTED: 'protected';
INTERNAL: 'internal';
PRIVATE: 'private';

BYTE: 'byte';
CHAR: 'char';
SHORT: 'short';
INT: 'int';
LONG: 'long';
FLOAT: 'float';
DOUBLE: 'double';
UNIT: 'unit';
BOOL: 'bool';

ENUM: 'enum';
STRUCT: 'struct';

RETURN: 'return';
BREAK: 'break';
CONTINUE: 'continue';
NEW: 'new';

TRUE: 'true';
FALSE: 'false';

IDENT: [a-zA-Z_$][a-zA-Z0-9_$]*;

NUMBER: ('0' | [1-9][0-9]*) (
        [bBsSiIlLfFdD]?
        | '.' [0-9]+ [fFdD]?
    );

EQL: '==';
NEQ: '!=';
GE: '>=';
LE: '<=';
GT: '>';
LT: '<';

DOT: '.';
SET: '=';
INF: ':=';
SEMI: ';';
COLON: ':';
COMMA: ',';
LPAREN: '(';
RPAREN: ')';
LBRACKET: '{';
RBRACKET: '}';
AMP: '&';
ADD: '+';
SUB: '-';
MUL: '*';
DIV: '/';
MOD: '%';
TILDA: '~';

program: decls*;

decls:
    empty = SEMI
    | accMod = (PUBLIC | PROTECTED | INTERNAL | PRIVATE)? (
        importDecl
        | functionDecl
        | variableDecl
        | enumDecl
        | structDecl
    );

symbolName:
    IDENT                                               # symVariable
    | COLON IDENT LPAREN ((IDENT COMMA)* IDENT)? RPAREN # symFunction;

importList: (symbolName COMMA)* symbolName;
importSymb: LPAREN syms = importList RPAREN;
importPath: unshift += DOT* (IDENT DOT)* IDENT;
importDecl: IMPORT path = importPath sym = importSymb?;

stmts:
    ignore = SEMI
    | variableDecl
    | returnStmt
    | assign = assignStmt
    | infSetStmt
    | ifStmt
    | loopStmt
    | breakStmt
    | contStmt
    | fcallStmt
    | rcallStmt
    | block;

block: LBRACKET body += stmts* RBRACKET;

type:
    AMP inner = type
    | BYTE
    | CHAR
    | SHORT
    | INT
    | LONG
    | FLOAT
    | DOUBLE
    | BOOL
    | UNIT
    | IDENT;

parameter: (IDENT COMMA)* IDENT COLON t = type;
paramList: (parameter COMMA)* parameter;
functionDecl:
    ret = type base = IDENT LPAREN params = paramList? RPAREN (
        bodyBlock = block
        | SET bodyExpr = expr
        | SET NATIVE nativeFFI = IDENT
    );

variableDecl: p = parameter;

structList: (variableDecl COMMA)* variableDecl;
structDecl:
    STRUCT name = IDENT LPAREN list = structList? RPAREN;

enumList: (IDENT COMMA)* IDENT;
enumDecl: ENUM name = IDENT LPAREN list = enumList? RPAREN;

returnStmt: RETURN value = expr?;

assignStmt: dest = expr SET value = expr;

infSetStmt: name = IDENT INF value = expr;

ifStmt: IF cond = expr brTrue = stmts (ELSE brFalse = stmts)?;

loopStmt: WHILE cond = expr body = stmts;

breakStmt: BREAK;
contStmt: CONTINUE;

fArgument: name = IDENT COLON value = expr;
fArgsList: (fArgument COMMA)* fArgument;
fcallStmt: base = IDENT LPAREN exprs = fArgsList? RPAREN;

rArgsList: (expr COMMA)* expr;
rcallTail: LPAREN exprs = rArgsList? RPAREN;
rcallStmt: base = expr tail = rcallTail;

expr:
    NUMBER                                             # exprNumber
    | (TRUE | FALSE)                                   # exprBool
    | NEW t = IDENT                                    # exprAllocStruct
    | base = expr tail = rcallTail                     # exprRefCall
    | fcallStmt                                        # exprFuncCall
    | symbolName                                       # exprSymbol
    | base = expr DOT attr = IDENT                     # exprAccess
    | dest = expr SET value = expr                     # exprAssign
    | op = (ADD | SUB | TILDA | AMP) rhs = expr        # exprUnary
    | lhs = expr op = (MUL | DIV | MOD) rhs = expr     # exprMulDivMod
    | lhs = expr op = (ADD | SUB) rhs = expr           # exprAddSub
    | lhs = expr op = (GT | GE | LE | LT) rhs = expr   # exprRelCmp
    | lhs = expr op = (EQL | NEQ) rhs = expr           # exprRelEql
    | IF cond = expr brTrue = expr ELSE brFalse = expr # exprIfElse
    | LPAREN inner = expr RPAREN                       # exprParenthesis;