package com.firefly.repl;

import java.io.IOException;

/**
 * Main entry point for the Firefly REPL (Read-Eval-Print Loop).
 * 
 * <p>The Firefly REPL provides an interactive programming environment for
 * experimenting with Firefly code, testing functions, and learning the language.</p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Interactive code evaluation with immediate feedback</li>
 *   <li>Syntax highlighting and colored output</li>
 *   <li>Command history with persistence across sessions</li>
 *   <li>Multi-line input support for complex expressions</li>
 *   <li>Tab completion for common keywords</li>
 *   <li>Special commands for managing the REPL session</li>
 *   <li>File loading and execution</li>
 *   <li>Type inspection</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * # Start the REPL
 * java -jar firefly-repl.jar
 * 
 * # Or via CLI
 * fly repl
 * </pre>
 * 
 * <h2>REPL Commands</h2>
 * <ul>
 *   <li><b>:help</b> - Show help message</li>
 *   <li><b>:quit</b> - Exit the REPL</li>
 *   <li><b>:reset</b> - Reset REPL state</li>
 *   <li><b>:imports</b> - Show current imports</li>
 *   <li><b>:definitions</b> - Show defined functions and types</li>
 *   <li><b>:clear</b> - Clear the screen</li>
 *   <li><b>:load &lt;file&gt;</b> - Load and execute a file</li>
 *   <li><b>:type &lt;expr&gt;</b> - Show the type of an expression</li>
 * </ul>
 * 
 * @version 1.0-Alpha
 */
public class FireflyRepl {
    
    private final ReplEngine engine;
    private final ReplUI ui;
    private final ReplCommand commandHandler;
    private final ContextPanel contextPanel;

    /**
     * Creates a new Firefly REPL instance.
     */
    public FireflyRepl() throws IOException {
        this.engine = new ReplEngine();
        this.ui = new ReplUI();
        this.ui.setEngine(engine);
        this.contextPanel = new ContextPanel(ui.getTerminal(), engine);
        this.ui.setContextPanel(contextPanel);
        this.commandHandler = new ReplCommand(engine, ui);
    }
    
    /**
     * Displays a detailed error message based on the evaluation result.
     */
    private void displayError(ReplEngine.EvalResult result, String input) {
        String message = result.getError();
        if (message == null) {
            message = "Unknown error";
        }
        
        // Check if it's a runtime error with cause
        if (result.getCause() != null) {
            ui.printError("Error: " + message);
            if (System.getenv("DEBUG") != null) {
                result.getCause().printStackTrace();
            }
        } else {
            ui.printError(message);
        }
    }

    /**
     * Load startup script from ~/.firefly_repl.fly if it exists.
     */
    private void loadStartupScript() {
        try {
            String home = System.getProperty("user.home");
            java.nio.file.Path scriptPath = java.nio.file.Paths.get(home, ".firefly_repl.fly");
            
            if (java.nio.file.Files.exists(scriptPath)) {
                String content = java.nio.file.Files.readString(scriptPath);
                ui.printInfo("Loading startup script: " + scriptPath);
                
                // Execute the startup script silently
                for (String line : content.split("\n")) {
                    if (!line.trim().isEmpty() && !line.trim().startsWith("//")) {
                        engine.eval(line);
                    }
                }
            }
        } catch (Exception e) {
            ui.printWarning("Failed to load startup script: " + e.getMessage());
        }
    }
    
    /**
     * Starts the REPL loop.
     */
    public void run() {
        ui.printBanner();
        loadStartupScript();
        
        while (true) {
            try {
                String input = ui.readLine();
                
                // Check for EOF or interrupt
                if (input == null) {
                    ui.println();
                    ui.printInfo("Goodbye! " + ReplUI.Colors.BRIGHT_YELLOW + "🔥" + ReplUI.Colors.RESET);
                    ui.println();
                    break;
                }
                
                // Skip empty lines
                if (input.trim().isEmpty()) {
                    continue;
                }
                
                // Handle commands
                if (commandHandler.isCommand(input)) {
                    ReplCommand.CommandResult result = commandHandler.execute(input);
                    if (result.shouldExit()) {
                        break;
                    }
                    continue;
                }
                
                // Evaluate the input
                ReplEngine.EvalResult result = engine.eval(input);

                if (result.isSuccess()) {
                    // Success - value already printed by engine if needed
                    // Just show definition confirmation if it's a definition
                    if (result.getValue() instanceof String) {
                        String value = (String) result.getValue();
                        if (value.endsWith(" added") || value.endsWith(" defined")) {
                            // It's a definition confirmation, show it quietly
                        }
                    }
                } else {
                    // Display error
                    displayError(result, input);
                }
                
            } catch (Exception e) {
                ui.printError("Unexpected error: " + e.getMessage());
                if (System.getenv("DEBUG") != null) {
                    e.printStackTrace();
                }
            }
        }
        
        // Cleanup
        try {
            ui.close();
        } catch (IOException e) {
            System.err.println("Error closing terminal: " + e.getMessage());
        }
    }
    
    /**
     * Main entry point.
     * 
     * @param args Command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        try {
            FireflyRepl repl = new FireflyRepl();
            repl.run();
        } catch (IOException e) {
            System.err.println("Failed to initialize REPL: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
}

