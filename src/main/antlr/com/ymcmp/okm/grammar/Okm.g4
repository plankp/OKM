grammar Okm;

WHITESPACE: [ \t\r\n] -> Channel(HIDDEN);
S_COMMENT: '#' ~[\r\n]* -> Channel(HIDDEN);
M_COMMENT: '<#' .*? '#>' -> Channel(HIDDEN);

IMPORT: 'import';
IF: 'if';
ELSE: 'else';

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

RETURN: 'return';

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
ADD: '+';
SUB: '-';
MUL: '*';
DIV: '/';
MOD: '%';
NOT: '!';
TILDA: '~';

program: decls*;

decls:
    empty = SEMI
    | accMod = (PUBLIC | PROTECTED | INTERNAL | PRIVATE)? (
        importDecl
        | functionDecl
        | variableDecl
    );

symbolName:
    IDENT                                               # symVariable
    | COLON IDENT LPAREN ((IDENT COMMA)* IDENT)? RPAREN # symFunction;

importSymb: LPAREN names += symbolName* RPAREN;
importPath: unshift += DOT* (IDENT DOT)* IDENT;
importDecl: IMPORT path = importPath sym = importSymb?;

stmts:
    SEMI
    | variableDecl
    | returnStmt
    | assignStmt
    | infSetStmt
    | ifStmt
    | fcallStmt
    | rcallStmt
    | block;

block: LBRACKET body += stmts* RBRACKET;

type:
    BYTE
    | CHAR
    | SHORT
    | INT
    | LONG
    | FLOAT
    | DOUBLE
    | UNIT
    | BOOL
    | IDENT;

parameter: (IDENT COMMA)* IDENT COLON t = type;
paramList: (parameter COMMA)* parameter;
functionDecl:
    ret = type base = IDENT LPAREN params = paramList? RPAREN (
        bodyBlock = block
        | SET bodyExpr = expr
    );

variableDecl: p = parameter;

returnStmt: RETURN value = expr?;

assignStmt: name = IDENT SET value = expr;

infSetStmt: name = IDENT INF value = expr;

ifStmt: IF cond = expr brTrue = stmts (ELSE brFalse = stmts)?;

fArgument: name = IDENT COLON value = expr;
fArgsList: (fArgument COMMA)* fArgument;
fcallStmt: base = IDENT LPAREN exprs = fArgsList? RPAREN;

rArgsList: (expr COMMA)* expr;
rcallTail: LPAREN exprs = rArgsList? RPAREN;
rcallStmt: base = expr tail = rcallTail;

expr:
    NUMBER                                             # exprNumber
    | (TRUE | FALSE)                                   # exprBool
    | base = expr tail = rcallTail                     # exprRefCall
    | fcallStmt                                        # exprFuncCall
    | symbolName                                       # exprSymbol
    | op = (ADD | SUB | NOT | TILDA) rhs = expr        # exprUnary
    | lhs = expr op = (MUL | DIV | MOD) rhs = expr     # exprMulDivMod
    | lhs = expr op = (ADD | SUB) rhs = expr           # exprAddSub
    | lhs = expr op = (GT | GE | LE | LT) rhs = expr   # exprRelCmp
    | lhs = expr op = (EQL | NEQ) rhs = expr           # exprRelEql
    | IF cond = expr brTrue = expr ELSE brFalse = expr # exprIfElse
    | assignStmt                                       # exprAssign
    | LPAREN inner = expr RPAREN                       # exprParenthesis;