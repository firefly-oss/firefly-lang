package com.firefly.intellij.run;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;

/**
 * Options for Firefly run configuration.
 */
public class FireflyRunConfigurationOptions extends RunConfigurationOptions {
    
    private final StoredProperty<String> filePath = 
        string("").provideDelegate(this, "filePath");
    
    private final StoredProperty<String> programArguments = 
        string("").provideDelegate(this, "programArguments");
    
    private final StoredProperty<String> workingDirectory = 
        string("").provideDelegate(this, "workingDirectory");
    
    public String getFilePath() {
        return filePath.getValue(this);
    }
    
    public void setFilePath(String value) {
        filePath.setValue(this, value);
    }
    
    public String getProgramArguments() {
        return programArguments.getValue(this);
    }
    
    public void setProgramArguments(String value) {
        programArguments.setValue(this, value);
    }
    
    public String getWorkingDirectory() {
        return workingDirectory.getValue(this);
    }
    
    public void setWorkingDirectory(String value) {
        workingDirectory.setValue(this, value);
    }
}

