package com.firefly.intellij.run;

import com.firefly.intellij.FireflyIcons;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Configuration type for Firefly run configurations.
 */
public class FireflyConfigurationType implements ConfigurationType {
    
    @NotNull
    @Override
    public String getDisplayName() {
        return "Firefly";
    }
    
    @Nls
    @Override
    public String getConfigurationTypeDescription() {
        return "Firefly run configuration";
    }
    
    @Override
    public Icon getIcon() {
        return FireflyIcons.FILE;
    }
    
    @NotNull
    @Override
    public String getId() {
        return "FireflyRunConfiguration";
    }
    
    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{new FireflyConfigurationFactory(this)};
    }
}

