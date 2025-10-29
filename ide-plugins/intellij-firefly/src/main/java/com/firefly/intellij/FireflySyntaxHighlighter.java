package com.firefly.intellij;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Syntax highlighter for Firefly language.
 */
public class FireflySyntaxHighlighter extends SyntaxHighlighterBase {
    
    // Keywords
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("FIREFLY_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey KEYWORD_CONTROL =
            createTextAttributesKey("FIREFLY_KEYWORD_CONTROL", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey KEYWORD_DECLARATION =
            createTextAttributesKey("FIREFLY_KEYWORD_DECLARATION", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey KEYWORD_MODIFIER =
            createTextAttributesKey("FIREFLY_KEYWORD_MODIFIER", DefaultLanguageHighlighterColors.KEYWORD);

    // Literals
    public static final TextAttributesKey STRING =
            createTextAttributesKey("FIREFLY_STRING", DefaultLanguageHighlighterColors.STRING);

    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("FIREFLY_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

    public static final TextAttributesKey BOOLEAN =
            createTextAttributesKey("FIREFLY_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey NULL =
            createTextAttributesKey("FIREFLY_NULL", DefaultLanguageHighlighterColors.KEYWORD);

    // Comments
    public static final TextAttributesKey LINE_COMMENT =
            createTextAttributesKey("FIREFLY_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

    public static final TextAttributesKey BLOCK_COMMENT =
            createTextAttributesKey("FIREFLY_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);

    // Operators
    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("FIREFLY_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);

    // Identifiers
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("FIREFLY_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

    public static final TextAttributesKey TYPE_IDENTIFIER =
            createTextAttributesKey("FIREFLY_TYPE_IDENTIFIER", DefaultLanguageHighlighterColors.CLASS_NAME);

    public static final TextAttributesKey FUNCTION_DECLARATION =
            createTextAttributesKey("FIREFLY_FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);

    public static final TextAttributesKey FUNCTION_CALL =
            createTextAttributesKey("FIREFLY_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);

    public static final TextAttributesKey PARAMETER =
            createTextAttributesKey("FIREFLY_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);

    public static final TextAttributesKey ANNOTATION =
            createTextAttributesKey("FIREFLY_ANNOTATION", DefaultLanguageHighlighterColors.METADATA);

    // Punctuation
    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("FIREFLY_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);

    public static final TextAttributesKey BRACES =
            createTextAttributesKey("FIREFLY_BRACES", DefaultLanguageHighlighterColors.BRACES);

    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("FIREFLY_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);

    public static final TextAttributesKey COMMA =
            createTextAttributesKey("FIREFLY_COMMA", DefaultLanguageHighlighterColors.COMMA);

    public static final TextAttributesKey SEMICOLON =
            createTextAttributesKey("FIREFLY_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);

    public static final TextAttributesKey DOT =
            createTextAttributesKey("FIREFLY_DOT", DefaultLanguageHighlighterColors.DOT);

    public static final TextAttributesKey BAD_CHARACTER =
            createTextAttributesKey("FIREFLY_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);
    
    private static final TextAttributesKey[] KEYWORD_KEYS = new TextAttributesKey[]{KEYWORD};
    private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[]{STRING};
    private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};
    private static final TextAttributesKey[] BOOLEAN_KEYS = new TextAttributesKey[]{BOOLEAN};
    private static final TextAttributesKey[] NULL_KEYS = new TextAttributesKey[]{NULL};
    private static final TextAttributesKey[] LINE_COMMENT_KEYS = new TextAttributesKey[]{LINE_COMMENT};
    private static final TextAttributesKey[] BLOCK_COMMENT_KEYS = new TextAttributesKey[]{BLOCK_COMMENT};
    private static final TextAttributesKey[] OPERATOR_KEYS = new TextAttributesKey[]{OPERATOR};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};
    private static final TextAttributesKey[] TYPE_IDENTIFIER_KEYS = new TextAttributesKey[]{TYPE_IDENTIFIER};
    private static final TextAttributesKey[] ANNOTATION_KEYS = new TextAttributesKey[]{ANNOTATION};
    private static final TextAttributesKey[] PARENTHESES_KEYS = new TextAttributesKey[]{PARENTHESES};
    private static final TextAttributesKey[] BRACES_KEYS = new TextAttributesKey[]{BRACES};
    private static final TextAttributesKey[] BRACKETS_KEYS = new TextAttributesKey[]{BRACKETS};
    private static final TextAttributesKey[] COMMA_KEYS = new TextAttributesKey[]{COMMA};
    private static final TextAttributesKey[] SEMICOLON_KEYS = new TextAttributesKey[]{SEMICOLON};
    private static final TextAttributesKey[] DOT_KEYS = new TextAttributesKey[]{DOT};
    private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[]{BAD_CHARACTER};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];
    
    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new FireflyLexerAdapter();
    }
    
    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        // Keywords
        if (FireflyTokenTypes.KEYWORDS.contains(tokenType)) {
            return KEYWORD_KEYS;
        }

        // Literals
        if (tokenType.equals(FireflyTokenTypes.STRING_LITERAL) ||
            tokenType.equals(FireflyTokenTypes.CHAR_LITERAL)) {
            return STRING_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.INTEGER_LITERAL) ||
            tokenType.equals(FireflyTokenTypes.FLOAT_LITERAL)) {
            return NUMBER_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.TRUE) ||
            tokenType.equals(FireflyTokenTypes.FALSE)) {
            return BOOLEAN_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.NULL)) {
            return NULL_KEYS;
        }

        // Comments
        if (tokenType.equals(FireflyTokenTypes.LINE_COMMENT)) {
            return LINE_COMMENT_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.BLOCK_COMMENT)) {
            return BLOCK_COMMENT_KEYS;
        }

        // Operators
        if (FireflyTokenTypes.OPERATORS.contains(tokenType)) {
            return OPERATOR_KEYS;
        }

        // Identifiers
        if (tokenType.equals(FireflyTokenTypes.IDENTIFIER)) {
            return IDENTIFIER_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.TYPE_IDENTIFIER)) {
            return TYPE_IDENTIFIER_KEYS;
        }

        // Annotations
        if (tokenType.equals(FireflyTokenTypes.AT)) {
            return ANNOTATION_KEYS;
        }

        // Punctuation
        if (tokenType.equals(FireflyTokenTypes.LPAREN) ||
            tokenType.equals(FireflyTokenTypes.RPAREN)) {
            return PARENTHESES_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.LBRACE) ||
            tokenType.equals(FireflyTokenTypes.RBRACE)) {
            return BRACES_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.LBRACKET) ||
            tokenType.equals(FireflyTokenTypes.RBRACKET)) {
            return BRACKETS_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.COMMA)) {
            return COMMA_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.SEMICOLON)) {
            return SEMICOLON_KEYS;
        }
        if (tokenType.equals(FireflyTokenTypes.DOT)) {
            return DOT_KEYS;
        }

        // Bad characters
        if (tokenType.equals(TokenType.BAD_CHARACTER)) {
            return BAD_CHAR_KEYS;
        }

        return EMPTY_KEYS;
    }
}

