package com.firefly.intellij.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Editor for Firefly run configuration settings.
 */
public class FireflyRunConfigurationEditor extends SettingsEditor<FireflyRunConfiguration> {
    
    private JPanel panel;
    private TextFieldWithBrowseButton filePathField;
    private JBTextField programArgumentsField;
    private TextFieldWithBrowseButton workingDirectoryField;
    
    @Override
    protected void resetEditorFrom(@NotNull FireflyRunConfiguration configuration) {
        filePathField.setText(configuration.getFilePath());
        programArgumentsField.setText(configuration.getProgramArguments());
        workingDirectoryField.setText(configuration.getWorkingDirectory());
    }
    
    @Override
    protected void applyEditorTo(@NotNull FireflyRunConfiguration configuration) {
        configuration.setFilePath(filePathField.getText());
        configuration.setProgramArguments(programArgumentsField.getText());
        configuration.setWorkingDirectory(workingDirectoryField.getText());
    }
    
    @NotNull
    @Override
    protected JComponent createEditor() {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        // File path
        JLabel filePathLabel = new JLabel("Firefly file:");
        filePathField = new TextFieldWithBrowseButton();
        filePathField.addBrowseFolderListener(
            "Select Firefly File",
            "Select the Firefly file to run",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("fly")
        );
        
        // Program arguments
        JLabel programArgumentsLabel = new JLabel("Program arguments:");
        programArgumentsField = new JBTextField();
        
        // Working directory
        JLabel workingDirectoryLabel = new JLabel("Working directory:");
        workingDirectoryField = new TextFieldWithBrowseButton();
        workingDirectoryField.addBrowseFolderListener(
            "Select Working Directory",
            "Select the working directory",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        );
        
        panel.add(filePathLabel);
        panel.add(filePathField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(programArgumentsLabel);
        panel.add(programArgumentsField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(workingDirectoryLabel);
        panel.add(workingDirectoryField);
        
        return panel;
    }
}

