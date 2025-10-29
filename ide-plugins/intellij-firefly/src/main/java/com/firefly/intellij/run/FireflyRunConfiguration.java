package com.firefly.intellij.run;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration for Firefly programs.
 */
public class FireflyRunConfiguration extends RunConfigurationBase<FireflyRunConfigurationOptions> {
    
    protected FireflyRunConfiguration(@NotNull Project project, 
                                     @NotNull ConfigurationFactory factory, 
                                     @NotNull String name) {
        super(project, factory, name);
    }
    
    @NotNull
    @Override
    protected FireflyRunConfigurationOptions getOptions() {
        return (FireflyRunConfigurationOptions) super.getOptions();
    }
    
    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new FireflyRunConfigurationEditor();
    }
    
    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
        return new FireflyRunProfileState(environment, this);
    }
    
    /**
     * Get the Firefly file path.
     */
    public String getFilePath() {
        return getOptions().getFilePath();
    }
    
    /**
     * Set the Firefly file path.
     */
    public void setFilePath(String filePath) {
        getOptions().setFilePath(filePath);
    }
    
    /**
     * Get program arguments.
     */
    public String getProgramArguments() {
        return getOptions().getProgramArguments();
    }
    
    /**
     * Set program arguments.
     */
    public void setProgramArguments(String arguments) {
        getOptions().setProgramArguments(arguments);
    }
    
    /**
     * Get working directory.
     */
    public String getWorkingDirectory() {
        return getOptions().getWorkingDirectory();
    }
    
    /**
     * Set working directory.
     */
    public void setWorkingDirectory(String directory) {
        getOptions().setWorkingDirectory(directory);
    }
}

