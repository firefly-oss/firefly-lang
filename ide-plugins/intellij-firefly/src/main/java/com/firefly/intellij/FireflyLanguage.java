package com.firefly.intellij;

import com.intellij.lang.Language;

/**
 * Firefly Language definition for IntelliJ IDEA.
 */
public class FireflyLanguage extends Language {
    
    public static final FireflyLanguage INSTANCE = new FireflyLanguage();

    private FireflyLanguage() {
        super("Firefly");
    }
}

