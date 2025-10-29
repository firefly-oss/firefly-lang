package com.firefly.intellij;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Brace matcher for Firefly language.
 * Matches (), {}, and [].
 */
public class FireflyBraceMatcher implements PairedBraceMatcher {
    
    private static final BracePair[] PAIRS = new BracePair[]{
            new BracePair(FireflyTokenTypes.LPAREN, FireflyTokenTypes.RPAREN, false),
            new BracePair(FireflyTokenTypes.LBRACE, FireflyTokenTypes.RBRACE, true),
            new BracePair(FireflyTokenTypes.LBRACKET, FireflyTokenTypes.RBRACKET, false),
    };
    
    @NotNull
    @Override
    public BracePair[] getPairs() {
        return PAIRS;
    }
    
    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }
    
    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}

