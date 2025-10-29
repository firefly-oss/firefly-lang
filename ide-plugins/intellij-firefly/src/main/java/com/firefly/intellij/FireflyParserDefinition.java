package com.firefly.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Parser definition for Firefly language.
 * Integrates the ANTLR-based parser with IntelliJ's PSI system.
 */
public class FireflyParserDefinition implements ParserDefinition {
    
    public static final IFileElementType FILE = new IFileElementType(FireflyLanguage.INSTANCE);
    
    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new FireflyLexerAdapter();
    }
    
    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new FireflyParser();
    }
    
    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }
    
    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return FireflyTokenTypes.COMMENTS;
    }
    
    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return FireflyTokenTypes.STRINGS;
    }
    
    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return FireflyTokenTypes.Factory.createElement(node);
    }
    
    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new FireflyFile(viewProvider);
    }
}

