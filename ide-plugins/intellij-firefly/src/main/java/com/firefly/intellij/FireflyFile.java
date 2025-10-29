package com.firefly.intellij;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * PSI file for Firefly language.
 */
public class FireflyFile extends PsiFileBase {
    
    public FireflyFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, FireflyLanguage.INSTANCE);
    }
    
    @NotNull
    @Override
    public FileType getFileType() {
        return FireflyFileType.INSTANCE;
    }
    
    @Override
    public String toString() {
        return "Firefly File";
    }
}

