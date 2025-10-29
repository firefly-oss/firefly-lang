package com.firefly.intellij.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Run profile state for Firefly programs.
 */
public class FireflyRunProfileState extends CommandLineState {
    
    private final FireflyRunConfiguration configuration;
    
    protected FireflyRunProfileState(ExecutionEnvironment environment, FireflyRunConfiguration configuration) {
        super(environment);
        this.configuration = configuration;
    }
    
    @NotNull
    @Override
    protected ProcessHandler startProcess() throws ExecutionException {
        String filePath = configuration.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            throw new ExecutionException("Firefly file path is not specified");
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            throw new ExecutionException("Firefly file does not exist: " + filePath);
        }
        
        // Find firefly CLI
        String fireflyPath = findFireflyCLI();
        if (fireflyPath == null) {
            throw new ExecutionException("Firefly CLI not found. Please ensure firefly is in your PATH or in the project root.");
        }
        
        // Build command line
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(fireflyPath);
        commandLine.addParameter("run");
        commandLine.addParameter(filePath);
        
        // Add program arguments
        String programArguments = configuration.getProgramArguments();
        if (programArguments != null && !programArguments.isEmpty()) {
            String[] args = programArguments.split("\\s+");
            for (String arg : args) {
                if (!arg.isEmpty()) {
                    commandLine.addParameter(arg);
                }
            }
        }
        
        // Set working directory
        String workingDirectory = configuration.getWorkingDirectory();
        if (workingDirectory != null && !workingDirectory.isEmpty()) {
            commandLine.setWorkDirectory(workingDirectory);
        } else {
            commandLine.setWorkDirectory(file.getParent());
        }
        
        // Create process handler
        OSProcessHandler processHandler = new OSProcessHandler(commandLine);
        ProcessTerminatedListener.attach(processHandler);
        
        return processHandler;
    }
    
    /**
     * Find the Firefly CLI executable.
     */
    private String findFireflyCLI() {
        // Check project root
        String projectPath = configuration.getProject().getBasePath();
        if (projectPath != null) {
            File projectFirefly = new File(projectPath, "firefly");
            if (projectFirefly.exists() && projectFirefly.canExecute()) {
                return projectFirefly.getAbsolutePath();
            }
        }
        
        // Check PATH
        String path = System.getenv("PATH");
        if (path != null) {
            String[] paths = path.split(File.pathSeparator);
            for (String p : paths) {
                File fireflyExe = new File(p, "firefly");
                if (fireflyExe.exists() && fireflyExe.canExecute()) {
                    return fireflyExe.getAbsolutePath();
                }
            }
        }
        
        return null;
    }
}

