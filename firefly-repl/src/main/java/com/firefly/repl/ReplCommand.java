package com.firefly.repl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handles REPL commands (commands starting with ':').
 * 
 * <p>Provides various utility commands for managing the REPL session:</p>
 * <ul>
 *   <li>:help - Show help message</li>
 *   <li>:quit/:exit - Exit the REPL</li>
 *   <li>:reset - Reset REPL state</li>
 *   <li>:imports - Show current imports</li>
 *   <li>:definitions - Show defined functions and types</li>
 *   <li>:clear - Clear the screen</li>
 *   <li>:load - Load and execute a file</li>
 *   <li>:save - Save session history to a file</li>
 * </ul>
 * 
 * @version 1.0-Alpha
 */
public class ReplCommand {
    
    private final ReplEngine engine;
    private final ReplUI ui;
    
    /**
     * Result of executing a command.
     */
    public static class CommandResult {
        private final boolean shouldExit;
        private final boolean handled;
        
        private CommandResult(boolean shouldExit, boolean handled) {
            this.shouldExit = shouldExit;
            this.handled = handled;
        }
        
        public static CommandResult exit() {
            return new CommandResult(true, true);
        }
        
        public static CommandResult handled() {
            return new CommandResult(false, true);
        }
        
        public static CommandResult notHandled() {
            return new CommandResult(false, false);
        }
        
        public boolean shouldExit() {
            return shouldExit;
        }
        
        public boolean isHandled() {
            return handled;
        }
    }
    
    /**
     * Creates a new command handler.
     */
    public ReplCommand(ReplEngine engine, ReplUI ui) {
        this.engine = engine;
        this.ui = ui;
    }
    
    /**
     * Checks if the input is a command.
     */
    public boolean isCommand(String input) {
        return input != null && input.trim().startsWith(":");
    }
    
    /**
     * Executes a REPL command.
     * 
     * @param input The command input
     * @return The command result
     */
    public CommandResult execute(String input) {
        String command = input.trim().toLowerCase();
        
        // Extract command and arguments
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1] : "";
        
        return switch (cmd) {
            case ":help", ":h", ":?" -> {
                ui.printHelp();
                yield CommandResult.handled();
            }
            case ":quit", ":exit", ":q" -> {
                ui.println();
                ui.printInfo("Goodbye! " + ReplUI.Colors.BRIGHT_YELLOW + "🔥" + ReplUI.Colors.RESET);
                ui.println();
                yield CommandResult.exit();
            }
            case ":reset" -> {
                engine.reset();
                ui.printSuccess("REPL state reset");
                yield CommandResult.handled();
            }
            case ":imports" -> {
                showImports();
                yield CommandResult.handled();
            }
            case ":definitions", ":defs" -> {
                showDefinitions();
                yield CommandResult.handled();
            }
            case ":clear", ":cls" -> {
                ui.clearScreen();
                yield CommandResult.handled();
            }
            case ":load" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :load <filename>");
                } else {
                    loadFile(args);
                }
                yield CommandResult.handled();
            }
            case ":type" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :type <expression>");
                } else {
                    showType(args);
                }
                yield CommandResult.handled();
            }
            case ":context", ":ctx" -> {
                ui.showContext();
                yield CommandResult.handled();
            }
            default -> {
                ui.printError("Unknown command: " + cmd);
                ui.printInfo("Type :help for available commands");
                yield CommandResult.handled();
            }
        };
    }
    
    /**
     * Shows current imports.
     */
    private void showImports() {
        List<String> imports = engine.getImports();
        
        if (imports.isEmpty()) {
            ui.printInfo("No imports");
            return;
        }
        
        ui.printHeader("Current Imports");
        for (String imp : imports) {
            ui.println(ReplUI.Colors.BRIGHT_GREEN + imp + ReplUI.Colors.RESET);
        }
        ui.println();
    }
    
    /**
     * Shows current definitions.
     */
    private void showDefinitions() {
        List<String> definitions = engine.getDefinitions();
        
        if (definitions.isEmpty()) {
            ui.printInfo("No definitions");
            return;
        }
        
        ui.printHeader("Current Definitions");
        for (String def : definitions) {
            // Show first line of each definition
            String firstLine = def.split("\n")[0];
            if (firstLine.length() > 70) {
                firstLine = firstLine.substring(0, 67) + "...";
            }
            ui.println(ReplUI.Colors.BRIGHT_GREEN + firstLine + ReplUI.Colors.RESET);
        }
        ui.println();
    }
    
    /**
     * Loads and executes a Firefly file.
     */
    private void loadFile(String filename) {
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                ui.printError("File not found: " + filename);
                return;
            }
            
            String content = Files.readString(path);
            ui.printInfo("Loading " + filename + "...");
            
            // Split into lines and execute each
            String[] lines = content.split("\n");
            int successCount = 0;
            int errorCount = 0;
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                
                ReplEngine.EvalResult result = engine.eval(line);
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    errorCount++;
                    ui.printError("Line: " + line);
                    ui.printError(result.getError());
                }
            }
            
            ui.printSuccess("Loaded " + successCount + " line(s)" + 
                    (errorCount > 0 ? " (" + errorCount + " error(s))" : ""));
            
        } catch (IOException e) {
            ui.printError("Failed to read file: " + e.getMessage());
        }
    }
    
    /**
     * Shows the type of an expression.
     */
    private void showType(String expression) {
        ReplEngine.EvalResult result = engine.eval(expression);
        if (result.isSuccess()) {
            ui.printInfo("Type: " + ReplUI.Colors.BRIGHT_CYAN + result.getType() + ReplUI.Colors.RESET);
        } else {
            ui.printError(result.getError());
        }
    }
}

