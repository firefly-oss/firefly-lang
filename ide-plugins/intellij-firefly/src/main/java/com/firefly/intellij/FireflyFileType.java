package com.firefly.intellij;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Firefly file type (.fly files).
 */
public class FireflyFileType extends LanguageFileType {
    
    public static final FireflyFileType INSTANCE = new FireflyFileType();

    private FireflyFileType() {
        super(FireflyLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Firefly";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Firefly source file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "fly";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return FireflyIcons.FILE;
    }
}

