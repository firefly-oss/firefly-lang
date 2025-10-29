package com.firefly.intellij.actions;

import com.firefly.intellij.FireflyFileType;
import com.firefly.intellij.FireflyIcons;
import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * Action to create a new Firefly file.
 */
public class NewFireflyFileAction extends CreateFileFromTemplateAction {
    
    public NewFireflyFileAction() {
        super("Firefly File", "Create new Firefly file", FireflyIcons.FILE);
    }
    
    @Override
    protected void buildDialog(@NotNull Project project,
                               @NotNull PsiDirectory directory,
                               @NotNull CreateFileFromTemplateDialog.Builder builder) {
        builder
                .setTitle("New Firefly File")
                .addKind("Empty File", FireflyIcons.FILE, "Firefly File")
                .addKind("Main Function", FireflyIcons.FILE, "Firefly Main")
                .addKind("Class", FireflyIcons.FILE, "Firefly Class")
                .addKind("Interface", FireflyIcons.FILE, "Firefly Interface")
                .addKind("Struct", FireflyIcons.FILE, "Firefly Struct")
                .addKind("Data Type", FireflyIcons.FILE, "Firefly Data")
                .addKind("Test", FireflyIcons.FILE, "Firefly Test")
                .addKind("Actor", FireflyIcons.FILE, "Firefly Actor");
    }
    
    @Override
    protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return "Create Firefly File: " + newName;
    }
}

