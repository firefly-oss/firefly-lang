package com.firefly.intellij;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Token type for Firefly language.
 */
public class FireflyTokenType extends IElementType {
    
    public FireflyTokenType(@NotNull @NonNls String debugName) {
        super(debugName, FireflyLanguage.INSTANCE);
    }
    
    @Override
    public String toString() {
        return "FireflyTokenType." + super.toString();
    }
}

