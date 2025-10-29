package com.firefly.intellij;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * Base PSI element for Firefly language.
 */
public class FireflyPsiElement extends ASTWrapperPsiElement {
    
    public FireflyPsiElement(@NotNull ASTNode node) {
        super(node);
    }
}

