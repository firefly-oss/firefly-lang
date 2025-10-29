package com.firefly.intellij;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * Token types for Firefly language.
 * Maps ANTLR tokens to IntelliJ token types.
 */
public interface FireflyTokenTypes {
    
    // Keywords - Declarations
    IElementType PACKAGE = new FireflyTokenType("PACKAGE");
    IElementType IMPORT = new FireflyTokenType("IMPORT");
    IElementType FN = new FireflyTokenType("FN");
    IElementType CLASS = new FireflyTokenType("CLASS");
    IElementType INTERFACE = new FireflyTokenType("INTERFACE");
    IElementType STRUCT = new FireflyTokenType("STRUCT");
    IElementType DATA = new FireflyTokenType("DATA");
    IElementType TRAIT = new FireflyTokenType("TRAIT");
    IElementType IMPL = new FireflyTokenType("IMPL");
    IElementType TYPE = new FireflyTokenType("TYPE");
    IElementType ACTOR = new FireflyTokenType("ACTOR");
    IElementType PROTOCOL = new FireflyTokenType("PROTOCOL");
    IElementType EXTEND = new FireflyTokenType("EXTEND");
    IElementType CONTEXT = new FireflyTokenType("CONTEXT");
    IElementType SUPERVISOR = new FireflyTokenType("SUPERVISOR");
    IElementType FLOW = new FireflyTokenType("FLOW");
    IElementType STAGE = new FireflyTokenType("STAGE");
    IElementType MACRO = new FireflyTokenType("MACRO");

    // Keywords - Variables and Modifiers
    IElementType LET = new FireflyTokenType("LET");
    IElementType MUT = new FireflyTokenType("MUT");
    IElementType PUB = new FireflyTokenType("PUB");
    IElementType PRIV = new FireflyTokenType("PRIV");
    IElementType INIT = new FireflyTokenType("INIT");
    IElementType NEW = new FireflyTokenType("NEW");
    IElementType SELF = new FireflyTokenType("SELF");

    // Keywords - Control Flow
    IElementType IF = new FireflyTokenType("IF");
    IElementType ELSE = new FireflyTokenType("ELSE");
    IElementType MATCH = new FireflyTokenType("MATCH");
    IElementType CASE = new FireflyTokenType("CASE");
    IElementType FOR = new FireflyTokenType("FOR");
    IElementType WHILE = new FireflyTokenType("WHILE");
    IElementType RETURN = new FireflyTokenType("RETURN");
    IElementType BREAK = new FireflyTokenType("BREAK");
    IElementType CONTINUE = new FireflyTokenType("CONTINUE");

    // Keywords - Async/Concurrency
    IElementType ASYNC = new FireflyTokenType("ASYNC");
    IElementType AWAIT = new FireflyTokenType("AWAIT");
    IElementType CONCURRENT = new FireflyTokenType("CONCURRENT");
    IElementType RACE = new FireflyTokenType("RACE");
    IElementType TIMEOUT = new FireflyTokenType("TIMEOUT");
    IElementType RECEIVE = new FireflyTokenType("RECEIVE");
    IElementType SPAWN = new FireflyTokenType("SPAWN");

    // Keywords - Error Handling
    IElementType TRY = new FireflyTokenType("TRY");
    IElementType CATCH = new FireflyTokenType("CATCH");
    IElementType FINALLY = new FireflyTokenType("FINALLY");
    IElementType THROW = new FireflyTokenType("THROW");

    // Keywords - Other
    IElementType EXTENDS = new FireflyTokenType("EXTENDS");
    IElementType IMPLEMENTS = new FireflyTokenType("IMPLEMENTS");
    IElementType AS = new FireflyTokenType("AS");
    IElementType IN = new FireflyTokenType("IN");
    IElementType IS = new FireflyTokenType("IS");
    IElementType WITH = new FireflyTokenType("WITH");
    IElementType USING = new FireflyTokenType("USING");
    IElementType WHEN = new FireflyTokenType("WHEN");
    IElementType REQUIRES = new FireflyTokenType("REQUIRES");

    // Literals
    IElementType TRUE = new FireflyTokenType("TRUE");
    IElementType FALSE = new FireflyTokenType("FALSE");
    IElementType NULL = new FireflyTokenType("NULL");
    
    // Literals
    IElementType IDENTIFIER = new FireflyTokenType("IDENTIFIER");
    IElementType TYPE_IDENTIFIER = new FireflyTokenType("TYPE_IDENTIFIER");
    IElementType INTEGER_LITERAL = new FireflyTokenType("INTEGER_LITERAL");
    IElementType FLOAT_LITERAL = new FireflyTokenType("FLOAT_LITERAL");
    IElementType STRING_LITERAL = new FireflyTokenType("STRING_LITERAL");
    IElementType CHAR_LITERAL = new FireflyTokenType("CHAR_LITERAL");
    
    // Comments
    IElementType LINE_COMMENT = new FireflyTokenType("LINE_COMMENT");
    IElementType BLOCK_COMMENT = new FireflyTokenType("BLOCK_COMMENT");
    
    // Operators - Arithmetic
    IElementType PLUS = new FireflyTokenType("PLUS");
    IElementType MINUS = new FireflyTokenType("MINUS");
    IElementType STAR = new FireflyTokenType("STAR");
    IElementType SLASH = new FireflyTokenType("SLASH");
    IElementType PERCENT = new FireflyTokenType("PERCENT");
    IElementType STAR_STAR = new FireflyTokenType("STAR_STAR");
    IElementType PLUS_PLUS = new FireflyTokenType("PLUS_PLUS");
    IElementType MINUS_MINUS = new FireflyTokenType("MINUS_MINUS");

    // Operators - Comparison
    IElementType EQ = new FireflyTokenType("EQ");
    IElementType NE = new FireflyTokenType("NE");
    IElementType EQ_EQ_EQ = new FireflyTokenType("EQ_EQ_EQ");
    IElementType NE_EQ_EQ = new FireflyTokenType("NE_EQ_EQ");
    IElementType LT = new FireflyTokenType("LT");
    IElementType LE = new FireflyTokenType("LE");
    IElementType GT = new FireflyTokenType("GT");
    IElementType GE = new FireflyTokenType("GE");

    // Operators - Logical
    IElementType AND = new FireflyTokenType("AND");
    IElementType OR = new FireflyTokenType("OR");
    IElementType NOT = new FireflyTokenType("NOT");

    // Operators - Bitwise
    IElementType BIT_AND = new FireflyTokenType("BIT_AND");
    IElementType BIT_OR = new FireflyTokenType("BIT_OR");
    IElementType BIT_XOR = new FireflyTokenType("BIT_XOR");
    IElementType BIT_NOT = new FireflyTokenType("BIT_NOT");
    IElementType LSHIFT = new FireflyTokenType("LSHIFT");
    IElementType RSHIFT = new FireflyTokenType("RSHIFT");

    // Operators - Special
    IElementType ARROW = new FireflyTokenType("ARROW");
    IElementType FAT_ARROW = new FireflyTokenType("FAT_ARROW");
    IElementType DOUBLE_COLON = new FireflyTokenType("DOUBLE_COLON");
    IElementType QUESTION = new FireflyTokenType("QUESTION");
    IElementType DOUBLE_QUESTION = new FireflyTokenType("DOUBLE_QUESTION");
    IElementType SAFE_ACCESS = new FireflyTokenType("SAFE_ACCESS");
    IElementType FORCE_UNWRAP = new FireflyTokenType("FORCE_UNWRAP");
    IElementType ELVIS = new FireflyTokenType("ELVIS");
    IElementType SEND = new FireflyTokenType("SEND");
    IElementType RANGE = new FireflyTokenType("RANGE");
    IElementType RANGE_INCL = new FireflyTokenType("RANGE_INCL");
    IElementType PIPE = new FireflyTokenType("PIPE");
    
    // Delimiters
    IElementType LPAREN = new FireflyTokenType("LPAREN");
    IElementType RPAREN = new FireflyTokenType("RPAREN");
    IElementType LBRACE = new FireflyTokenType("LBRACE");
    IElementType RBRACE = new FireflyTokenType("RBRACE");
    IElementType LBRACKET = new FireflyTokenType("LBRACKET");
    IElementType RBRACKET = new FireflyTokenType("RBRACKET");
    IElementType SEMICOLON = new FireflyTokenType("SEMICOLON");
    IElementType COLON = new FireflyTokenType("COLON");
    IElementType COMMA = new FireflyTokenType("COMMA");
    IElementType DOT = new FireflyTokenType("DOT");
    IElementType ASSIGN = new FireflyTokenType("ASSIGN");
    IElementType AT = new FireflyTokenType("AT");
    
    // Token Sets
    TokenSet KEYWORDS = TokenSet.create(
        // Declarations
        PACKAGE, IMPORT, FN, CLASS, INTERFACE, STRUCT, DATA, TRAIT, IMPL, TYPE,
        ACTOR, PROTOCOL, EXTEND, CONTEXT, SUPERVISOR, FLOW, STAGE, MACRO,
        // Variables and Modifiers
        LET, MUT, PUB, PRIV, INIT, NEW, SELF,
        // Control Flow
        IF, ELSE, MATCH, CASE, FOR, WHILE, RETURN, BREAK, CONTINUE,
        // Async/Concurrency
        ASYNC, AWAIT, CONCURRENT, RACE, TIMEOUT, RECEIVE, SPAWN,
        // Error Handling
        TRY, CATCH, FINALLY, THROW,
        // Other
        EXTENDS, IMPLEMENTS, AS, IN, IS, WITH, USING, WHEN, REQUIRES,
        // Literals
        TRUE, FALSE, NULL
    );

    TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);

    TokenSet STRINGS = TokenSet.create(STRING_LITERAL, CHAR_LITERAL);

    TokenSet LITERALS = TokenSet.create(
        INTEGER_LITERAL, FLOAT_LITERAL, STRING_LITERAL, CHAR_LITERAL,
        TRUE, FALSE, NULL
    );

    TokenSet OPERATORS = TokenSet.create(
        // Arithmetic
        PLUS, MINUS, STAR, SLASH, PERCENT, STAR_STAR, PLUS_PLUS, MINUS_MINUS,
        // Comparison
        EQ, NE, EQ_EQ_EQ, NE_EQ_EQ, LT, LE, GT, GE,
        // Logical
        AND, OR, NOT,
        // Bitwise
        BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, LSHIFT, RSHIFT,
        // Special
        ARROW, FAT_ARROW, DOUBLE_COLON, QUESTION, DOUBLE_QUESTION,
        SAFE_ACCESS, FORCE_UNWRAP, ELVIS, SEND, RANGE, RANGE_INCL, PIPE
    );
    
    /**
     * Factory for creating PSI elements from AST nodes.
     */
    class Factory {
        public static PsiElement createElement(ASTNode node) {
            return new FireflyPsiElement(node);
        }
    }
}

