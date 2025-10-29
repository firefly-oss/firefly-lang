package com.firefly.intellij.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating Firefly run configurations.
 */
public class FireflyConfigurationFactory extends ConfigurationFactory {
    
    protected FireflyConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }
    
    @NotNull
    @Override
    public String getId() {
        return "Firefly";
    }
    
    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new FireflyRunConfiguration(project, this, "Firefly");
    }
}

