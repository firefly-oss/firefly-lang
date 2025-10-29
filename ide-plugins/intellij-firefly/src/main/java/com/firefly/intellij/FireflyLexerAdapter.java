package com.firefly.intellij;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lexer adapter for Firefly language.
 * This is a simple lexer that tokenizes Firefly source code.
 */
public class FireflyLexerAdapter extends LexerBase {
    
    private CharSequence buffer;
    private int startOffset;
    private int endOffset;
    private int currentOffset;
    private IElementType currentTokenType;
    private int currentTokenStart;
    private int currentTokenEnd;
    
    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.currentOffset = startOffset;
        advance();
    }
    
    @Override
    public int getState() {
        return 0;
    }
    
    @Nullable
    @Override
    public IElementType getTokenType() {
        return currentTokenType;
    }
    
    @Override
    public int getTokenStart() {
        return currentTokenStart;
    }
    
    @Override
    public int getTokenEnd() {
        return currentTokenEnd;
    }
    
    @Override
    public void advance() {
        if (currentOffset >= endOffset) {
            currentTokenType = null;
            return;
        }
        
        currentTokenStart = currentOffset;
        
        char c = buffer.charAt(currentOffset);
        
        // Skip whitespace
        if (Character.isWhitespace(c)) {
            while (currentOffset < endOffset && Character.isWhitespace(buffer.charAt(currentOffset))) {
                currentOffset++;
            }
            currentTokenEnd = currentOffset;
            currentTokenType = com.intellij.psi.TokenType.WHITE_SPACE;
            return;
        }
        
        // Line comment
        if (c == '/' && currentOffset + 1 < endOffset && buffer.charAt(currentOffset + 1) == '/') {
            currentOffset += 2;
            while (currentOffset < endOffset && buffer.charAt(currentOffset) != '\n') {
                currentOffset++;
            }
            currentTokenEnd = currentOffset;
            currentTokenType = FireflyTokenTypes.LINE_COMMENT;
            return;
        }
        
        // Block comment
        if (c == '/' && currentOffset + 1 < endOffset && buffer.charAt(currentOffset + 1) == '*') {
            currentOffset += 2;
            while (currentOffset + 1 < endOffset) {
                if (buffer.charAt(currentOffset) == '*' && buffer.charAt(currentOffset + 1) == '/') {
                    currentOffset += 2;
                    break;
                }
                currentOffset++;
            }
            currentTokenEnd = currentOffset;
            currentTokenType = FireflyTokenTypes.BLOCK_COMMENT;
            return;
        }
        
        // String literal
        if (c == '"') {
            currentOffset++;
            while (currentOffset < endOffset) {
                char ch = buffer.charAt(currentOffset);
                if (ch == '"') {
                    currentOffset++;
                    break;
                }
                if (ch == '\\' && currentOffset + 1 < endOffset) {
                    currentOffset += 2;
                } else {
                    currentOffset++;
                }
            }
            currentTokenEnd = currentOffset;
            currentTokenType = FireflyTokenTypes.STRING_LITERAL;
            return;
        }
        
        // Char literal
        if (c == '\'') {
            currentOffset++;
            while (currentOffset < endOffset) {
                char ch = buffer.charAt(currentOffset);
                if (ch == '\'') {
                    currentOffset++;
                    break;
                }
                if (ch == '\\' && currentOffset + 1 < endOffset) {
                    currentOffset += 2;
                } else {
                    currentOffset++;
                }
            }
            currentTokenEnd = currentOffset;
            currentTokenType = FireflyTokenTypes.CHAR_LITERAL;
            return;
        }
        
        // Number
        if (Character.isDigit(c)) {
            boolean isFloat = false;
            while (currentOffset < endOffset) {
                char ch = buffer.charAt(currentOffset);
                if (Character.isDigit(ch)) {
                    currentOffset++;
                } else if (ch == '.' && !isFloat) {
                    isFloat = true;
                    currentOffset++;
                } else {
                    break;
                }
            }
            currentTokenEnd = currentOffset;
            currentTokenType = isFloat ? FireflyTokenTypes.FLOAT_LITERAL : FireflyTokenTypes.INTEGER_LITERAL;
            return;
        }
        
        // Identifier or keyword
        if (Character.isJavaIdentifierStart(c)) {
            while (currentOffset < endOffset && Character.isJavaIdentifierPart(buffer.charAt(currentOffset))) {
                currentOffset++;
            }
            currentTokenEnd = currentOffset;
            String text = buffer.subSequence(currentTokenStart, currentTokenEnd).toString();
            currentTokenType = getKeywordOrIdentifier(text);
            return;
        }
        
        // Operators and delimiters
        currentTokenType = getOperatorOrDelimiter(c);
        if (currentTokenType != null) {
            currentOffset++;
            // Check for two-character operators
            if (currentOffset < endOffset) {
                String twoChar = "" + c + buffer.charAt(currentOffset);
                IElementType twoCharType = getTwoCharOperator(twoChar);
                if (twoCharType != null) {
                    currentTokenType = twoCharType;
                    currentOffset++;
                }
            }
            currentTokenEnd = currentOffset;
            return;
        }
        
        // Unknown character
        currentOffset++;
        currentTokenEnd = currentOffset;
        currentTokenType = com.intellij.psi.TokenType.BAD_CHARACTER;
    }
    
    private IElementType getKeywordOrIdentifier(String text) {
        return switch (text) {
            case "package" -> FireflyTokenTypes.PACKAGE;
            case "import" -> FireflyTokenTypes.IMPORT;
            case "fn" -> FireflyTokenTypes.FN;
            case "class" -> FireflyTokenTypes.CLASS;
            case "interface" -> FireflyTokenTypes.INTERFACE;
            case "struct" -> FireflyTokenTypes.STRUCT;
            case "data" -> FireflyTokenTypes.DATA;
            case "trait" -> FireflyTokenTypes.TRAIT;
            case "impl" -> FireflyTokenTypes.IMPL;
            case "let" -> FireflyTokenTypes.LET;
            case "mut" -> FireflyTokenTypes.MUT;
            case "if" -> FireflyTokenTypes.IF;
            case "else" -> FireflyTokenTypes.ELSE;
            case "match" -> FireflyTokenTypes.MATCH;
            case "for" -> FireflyTokenTypes.FOR;
            case "while" -> FireflyTokenTypes.WHILE;
            case "return" -> FireflyTokenTypes.RETURN;
            case "new" -> FireflyTokenTypes.NEW;
            case "async" -> FireflyTokenTypes.ASYNC;
            case "await" -> FireflyTokenTypes.AWAIT;
            case "try" -> FireflyTokenTypes.TRY;
            case "catch" -> FireflyTokenTypes.CATCH;
            case "finally" -> FireflyTokenTypes.FINALLY;
            case "throw" -> FireflyTokenTypes.THROW;
            case "extends" -> FireflyTokenTypes.EXTENDS;
            case "implements" -> FireflyTokenTypes.IMPLEMENTS;
            case "as" -> FireflyTokenTypes.AS;
            case "in" -> FireflyTokenTypes.IN;
            case "is" -> FireflyTokenTypes.IS;
            case "true" -> FireflyTokenTypes.TRUE;
            case "false" -> FireflyTokenTypes.FALSE;
            case "null" -> FireflyTokenTypes.NULL;
            default -> Character.isUpperCase(text.charAt(0)) ? 
                       FireflyTokenTypes.TYPE_IDENTIFIER : FireflyTokenTypes.IDENTIFIER;
        };
    }
    
    private IElementType getOperatorOrDelimiter(char c) {
        return switch (c) {
            case '+' -> FireflyTokenTypes.PLUS;
            case '-' -> FireflyTokenTypes.MINUS;
            case '*' -> FireflyTokenTypes.STAR;
            case '/' -> FireflyTokenTypes.SLASH;
            case '%' -> FireflyTokenTypes.PERCENT;
            case '=' -> FireflyTokenTypes.ASSIGN;
            case '<' -> FireflyTokenTypes.LT;
            case '>' -> FireflyTokenTypes.GT;
            case '!' -> FireflyTokenTypes.NOT;
            case '&' -> FireflyTokenTypes.AND;
            case '|' -> FireflyTokenTypes.OR;
            case '(' -> FireflyTokenTypes.LPAREN;
            case ')' -> FireflyTokenTypes.RPAREN;
            case '{' -> FireflyTokenTypes.LBRACE;
            case '}' -> FireflyTokenTypes.RBRACE;
            case '[' -> FireflyTokenTypes.LBRACKET;
            case ']' -> FireflyTokenTypes.RBRACKET;
            case ';' -> FireflyTokenTypes.SEMICOLON;
            case ':' -> FireflyTokenTypes.COLON;
            case ',' -> FireflyTokenTypes.COMMA;
            case '.' -> FireflyTokenTypes.DOT;
            case '@' -> FireflyTokenTypes.AT;
            default -> null;
        };
    }
    
    private IElementType getTwoCharOperator(String op) {
        return switch (op) {
            case "==" -> FireflyTokenTypes.EQ;
            case "!=" -> FireflyTokenTypes.NE;
            case "<=" -> FireflyTokenTypes.LE;
            case ">=" -> FireflyTokenTypes.GE;
            case "&&" -> FireflyTokenTypes.AND;
            case "||" -> FireflyTokenTypes.OR;
            case "->" -> FireflyTokenTypes.ARROW;
            case "::" -> FireflyTokenTypes.DOUBLE_COLON;
            default -> null;
        };
    }
    
    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }
    
    @Override
    public int getBufferEnd() {
        return endOffset;
    }
}

