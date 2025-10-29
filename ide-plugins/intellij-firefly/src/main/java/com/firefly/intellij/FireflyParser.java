package com.firefly.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Parser for Firefly language.
 * This is a simple recursive descent parser.
 */
public class FireflyParser implements PsiParser {
    
    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker rootMarker = builder.mark();
        
        while (!builder.eof()) {
            parseTopLevel(builder);
        }
        
        rootMarker.done(root);
        return builder.getTreeBuilt();
    }
    
    private void parseTopLevel(PsiBuilder builder) {
        IElementType tokenType = builder.getTokenType();
        
        if (tokenType == null) {
            builder.advanceLexer();
            return;
        }
        
        // Package declaration
        if (tokenType == FireflyTokenTypes.PACKAGE) {
            parsePackageDeclaration(builder);
            return;
        }
        
        // Import declaration
        if (tokenType == FireflyTokenTypes.IMPORT) {
            parseImportDeclaration(builder);
            return;
        }
        
        // Annotations
        if (tokenType == FireflyTokenTypes.AT) {
            parseAnnotation(builder);
            return;
        }
        
        // Function declaration
        if (tokenType == FireflyTokenTypes.FN || tokenType == FireflyTokenTypes.ASYNC) {
            parseFunctionDeclaration(builder);
            return;
        }
        
        // Class declaration
        if (tokenType == FireflyTokenTypes.CLASS) {
            parseClassDeclaration(builder);
            return;
        }
        
        // Interface declaration
        if (tokenType == FireflyTokenTypes.INTERFACE) {
            parseInterfaceDeclaration(builder);
            return;
        }
        
        // Struct declaration
        if (tokenType == FireflyTokenTypes.STRUCT) {
            parseStructDeclaration(builder);
            return;
        }
        
        // Data declaration
        if (tokenType == FireflyTokenTypes.DATA) {
            parseDataDeclaration(builder);
            return;
        }
        
        // Skip unknown tokens
        builder.advanceLexer();
    }
    
    private void parsePackageDeclaration(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer(); // 'package'
        
        // Parse qualified name
        while (builder.getTokenType() == FireflyTokenTypes.IDENTIFIER || 
               builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER ||
               builder.getTokenType() == FireflyTokenTypes.DOUBLE_COLON) {
            builder.advanceLexer();
        }
        
        marker.done(FireflyTokenTypes.PACKAGE);
    }
    
    private void parseImportDeclaration(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer(); // 'import'
        
        // Parse import path
        while (builder.getTokenType() == FireflyTokenTypes.IDENTIFIER || 
               builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER ||
               builder.getTokenType() == FireflyTokenTypes.DOUBLE_COLON ||
               builder.getTokenType() == FireflyTokenTypes.STAR ||
               builder.getTokenType() == FireflyTokenTypes.LBRACE ||
               builder.getTokenType() == FireflyTokenTypes.RBRACE ||
               builder.getTokenType() == FireflyTokenTypes.COMMA) {
            builder.advanceLexer();
        }
        
        marker.done(FireflyTokenTypes.IMPORT);
    }
    
    private void parseAnnotation(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer(); // '@'
        
        if (builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER) {
            builder.advanceLexer();
        }
        
        // Parse annotation arguments if present
        if (builder.getTokenType() == FireflyTokenTypes.LPAREN) {
            parseParenthesizedExpression(builder);
        }
        
        marker.done(FireflyTokenTypes.AT);
    }
    
    private void parseFunctionDeclaration(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        
        // async?
        if (builder.getTokenType() == FireflyTokenTypes.ASYNC) {
            builder.advanceLexer();
        }
        
        // 'fn'
        if (builder.getTokenType() == FireflyTokenTypes.FN) {
            builder.advanceLexer();
        }
        
        // Function name
        if (builder.getTokenType() == FireflyTokenTypes.IDENTIFIER) {
            builder.advanceLexer();
        }
        
        // Type parameters
        if (builder.getTokenType() == FireflyTokenTypes.LT) {
            parseTypeParameters(builder);
        }
        
        // Parameters
        if (builder.getTokenType() == FireflyTokenTypes.LPAREN) {
            parseParameters(builder);
        }
        
        // Return type
        if (builder.getTokenType() == FireflyTokenTypes.ARROW) {
            builder.advanceLexer();
            parseType(builder);
        }
        
        // Body
        if (builder.getTokenType() == FireflyTokenTypes.LBRACE) {
            parseBlock(builder);
        }
        
        marker.done(FireflyTokenTypes.FN);
    }
    
    private void parseClassDeclaration(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer(); // 'class'
        
        // Class name
        if (builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER) {
            builder.advanceLexer();
        }
        
        // Type parameters
        if (builder.getTokenType() == FireflyTokenTypes.LT) {
            parseTypeParameters(builder);
        }
        
        // Extends
        if (builder.getTokenType() == FireflyTokenTypes.EXTENDS) {
            builder.advanceLexer();
            parseType(builder);
        }
        
        // Implements
        if (builder.getTokenType() == FireflyTokenTypes.IMPLEMENTS) {
            builder.advanceLexer();
            parseTypeList(builder);
        }
        
        // Body
        if (builder.getTokenType() == FireflyTokenTypes.LBRACE) {
            parseClassBody(builder);
        }
        
        marker.done(FireflyTokenTypes.CLASS);
    }
    
    private void parseInterfaceDeclaration(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer(); // 'interface'
        
        // Interface name
        if (builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER) {
            builder.advanceLexer();
        }
        
        // Type parameters
        if (builder.getTokenType() == FireflyTokenTypes.LT) {
            parseTypeParameters(builder);
        }
        
        // Extends
        if (builder.getTokenType() == FireflyTokenTypes.EXTENDS) {
            builder.advanceLexer();
            parseTypeList(builder);
        }
        
        // Body
        if (builder.getTokenType() == FireflyTokenTypes.LBRACE) {
            parseInterfaceBody(builder);
        }
        
        marker.done(FireflyTokenTypes.INTERFACE);
    }
    
    private void parseStructDeclaration(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer(); // 'struct'
        
        // Struct name
        if (builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER) {
            builder.advanceLexer();
        }
        
        // Type parameters
        if (builder.getTokenType() == FireflyTokenTypes.LT) {
            parseTypeParameters(builder);
        }
        
        // Body
        if (builder.getTokenType() == FireflyTokenTypes.LBRACE) {
            parseStructBody(builder);
        }
        
        marker.done(FireflyTokenTypes.STRUCT);
    }
    
    private void parseDataDeclaration(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer(); // 'data'
        
        // Data name
        if (builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER) {
            builder.advanceLexer();
        }
        
        // Type parameters
        if (builder.getTokenType() == FireflyTokenTypes.LT) {
            parseTypeParameters(builder);
        }
        
        // Body
        if (builder.getTokenType() == FireflyTokenTypes.LBRACE) {
            parseDataBody(builder);
        }
        
        marker.done(FireflyTokenTypes.DATA);
    }
    
    private void parseTypeParameters(PsiBuilder builder) {
        builder.advanceLexer(); // '<'
        while (builder.getTokenType() != FireflyTokenTypes.GT && !builder.eof()) {
            builder.advanceLexer();
        }
        if (builder.getTokenType() == FireflyTokenTypes.GT) {
            builder.advanceLexer();
        }
    }
    
    private void parseParameters(PsiBuilder builder) {
        builder.advanceLexer(); // '('
        while (builder.getTokenType() != FireflyTokenTypes.RPAREN && !builder.eof()) {
            builder.advanceLexer();
        }
        if (builder.getTokenType() == FireflyTokenTypes.RPAREN) {
            builder.advanceLexer();
        }
    }
    
    private void parseType(PsiBuilder builder) {
        while (builder.getTokenType() == FireflyTokenTypes.TYPE_IDENTIFIER ||
               builder.getTokenType() == FireflyTokenTypes.IDENTIFIER ||
               builder.getTokenType() == FireflyTokenTypes.LT ||
               builder.getTokenType() == FireflyTokenTypes.GT ||
               builder.getTokenType() == FireflyTokenTypes.COMMA ||
               builder.getTokenType() == FireflyTokenTypes.DOUBLE_COLON) {
            builder.advanceLexer();
        }
    }
    
    private void parseTypeList(PsiBuilder builder) {
        parseType(builder);
        while (builder.getTokenType() == FireflyTokenTypes.COMMA) {
            builder.advanceLexer();
            parseType(builder);
        }
    }
    
    private void parseBlock(PsiBuilder builder) {
        builder.advanceLexer(); // '{'
        int depth = 1;
        while (depth > 0 && !builder.eof()) {
            if (builder.getTokenType() == FireflyTokenTypes.LBRACE) {
                depth++;
            } else if (builder.getTokenType() == FireflyTokenTypes.RBRACE) {
                depth--;
            }
            builder.advanceLexer();
        }
    }
    
    private void parseClassBody(PsiBuilder builder) {
        parseBlock(builder);
    }
    
    private void parseInterfaceBody(PsiBuilder builder) {
        parseBlock(builder);
    }
    
    private void parseStructBody(PsiBuilder builder) {
        parseBlock(builder);
    }
    
    private void parseDataBody(PsiBuilder builder) {
        parseBlock(builder);
    }
    
    private void parseParenthesizedExpression(PsiBuilder builder) {
        builder.advanceLexer(); // '('
        int depth = 1;
        while (depth > 0 && !builder.eof()) {
            if (builder.getTokenType() == FireflyTokenTypes.LPAREN) {
                depth++;
            } else if (builder.getTokenType() == FireflyTokenTypes.RPAREN) {
                depth--;
            }
            builder.advanceLexer();
        }
    }
}

