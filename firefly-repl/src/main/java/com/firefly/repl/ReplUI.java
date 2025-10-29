package com.firefly.repl;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Professional terminal UI for the Firefly REPL.
 *
 * <p>Provides an interactive, user-friendly interface with:</p>
 * <ul>
 *   <li>Syntax highlighting</li>
 *   <li>Command history with persistence</li>
 *   <li>Multi-line input support</li>
 *   <li>Tab completion</li>
 *   <li>Beautiful colored output</li>
 * </ul>
 *
 * @version 1.0-Alpha
 */
public class ReplUI {

    /**
     * ANSI color codes for beautiful output.
     */
    public static class Colors {
        public static final String RESET = "\u001B[0m";
        public static final String BOLD = "\u001B[1m";
        public static final String DIM = "\u001B[2m";

        public static final String RED = "\u001B[31m";
        public static final String GREEN = "\u001B[32m";
        public static final String YELLOW = "\u001B[33m";
        public static final String BLUE = "\u001B[34m";
        public static final String MAGENTA = "\u001B[35m";
        public static final String CYAN = "\u001B[36m";

        public static final String BRIGHT_RED = "\u001B[91m";
        public static final String BRIGHT_GREEN = "\u001B[92m";
        public static final String BRIGHT_YELLOW = "\u001B[93m";
        public static final String BRIGHT_BLUE = "\u001B[94m";
        public static final String BRIGHT_MAGENTA = "\u001B[95m";
        public static final String BRIGHT_CYAN = "\u001B[96m";
        public static final String BRIGHT_WHITE = "\u001B[97m";
    }

    private static final String FIREFLY_EMOJI = "🔥";
    private static final String VERSION = "1.0-Alpha";

    private final Terminal terminal;
    private final LineReader reader;
    private ContextPanel contextPanel;

    /**
     * Creates a new REPL UI.
     */
    public ReplUI() throws IOException {
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        // Configure line reader with history and completion
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new ReplParser())
                .history(new DefaultHistory())
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".firefly_repl_history"))
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
    }

    /**
     * Custom parser for multi-line input support.
     */
    private static class ReplParser extends DefaultParser {
        @Override
        public ParsedLine parse(String line, int cursor, ParseContext context) {
            // Allow multi-line input for incomplete braces
            if (context == ParseContext.ACCEPT_LINE) {
                int openBraces = countChar(line, '{') - countChar(line, '}');
                int openParens = countChar(line, '(') - countChar(line, ')');
                int openBrackets = countChar(line, '[') - countChar(line, ']');

                if (openBraces > 0 || openParens > 0 || openBrackets > 0) {
                    throw new EOFError(-1, -1, "Incomplete input");
                }
            }
            return super.parse(line, cursor, context);
        }

        private int countChar(String str, char ch) {
            return (int) str.chars().filter(c -> c == ch).count();
        }
    }

    /**
     * Prints the welcome banner.
     */
    public void printBanner() {
        terminal.writer().println();
        terminal.writer().println(Colors.BRIGHT_YELLOW + "  _____.__         .__" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_YELLOW + "_/ ____\\  | ___.__.|  | _____    ____    ____" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_YELLOW + "\\   __\\|  |<   |  ||  | \\__  \\  /    \\  / ___\\" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_YELLOW + " |  |  |  |_\\___  ||  |__/ __ \\|   |  \\/ /_/  >" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_YELLOW + " |__|  |____/ ____||____(____  /___|  /\\___  /" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_YELLOW + "            \\/               \\/     \\//_____/" + Colors.RESET);
        terminal.writer().println(Colors.DIM + "  Version " + VERSION + Colors.RESET);
        terminal.writer().println();
        terminal.writer().println(Colors.BOLD + Colors.BRIGHT_CYAN + "  flylang REPL" + Colors.RESET +
                Colors.DIM + " - Interactive Firefly Programming Environment" + Colors.RESET);
        terminal.writer().println();
        terminal.writer().println(Colors.DIM + "  Type " + Colors.BRIGHT_CYAN + ":help" + Colors.RESET +
                Colors.DIM + " for commands | " + Colors.BRIGHT_CYAN + ":quit" + Colors.RESET +
                Colors.DIM + " to exit" + Colors.RESET);
        terminal.writer().println();
        terminal.flush();
    }

    /**
     * Gets the terminal instance.
     */
    public Terminal getTerminal() {
        return terminal;
    }

    /**
     * Sets the context panel for displaying REPL state.
     */
    public void setContextPanel(ContextPanel panel) {
        this.contextPanel = panel;
    }

    /**
     * Displays the full context panel.
     */
    public void showContext() {
        if (contextPanel != null) {
            contextPanel.display();
        }
    }

    /**
     * Reads a line of input from the user.
     *
     * @return The input line, or null if EOF
     */
    public String readLine() {
        try {
            String prompt = Colors.BRIGHT_MAGENTA + "flylang> " + Colors.RESET;
            return reader.readLine(prompt);
        } catch (UserInterruptException e) {
            return null;
        } catch (EndOfFileException e) {
            return null;
        }
    }

    /**
     * Reads a multi-line input (for continuation).
     *
     * @return The input line, or null if EOF
     */
    public String readContinuation() {
        try {
            String prompt = Colors.DIM + "      ... " + Colors.RESET;
            return reader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }

    /**
     * Prints a success message with the result value.
     */
    public void printResult(Object value, String type) {
        if (value != null) {
            terminal.writer().println(Colors.DIM + "  => " + Colors.RESET +
                    Colors.BRIGHT_GREEN + formatValue(value) + Colors.RESET +
                    Colors.DIM + " : " + type + Colors.RESET);
            terminal.flush();
        }
    }

    /**
     * Prints a confirmation message when a definition is added.
     */
    public void printDefinitionConfirmation(String message) {
        terminal.writer().println(Colors.BRIGHT_CYAN + "  ✓ " + Colors.RESET +
                Colors.DIM + message + Colors.RESET);
        terminal.flush();
    }

    /**
     * Formats a value for display.
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return value.toString();
    }

    /**
     * Prints an error message (simple version).
     */
    public void printError(String message) {
        terminal.writer().println(Colors.BRIGHT_RED + "  ✗ Error: " + Colors.RESET + message);
        terminal.flush();
    }

    /**
     * Prints a detailed error with type, location, and suggestion.
     */
    public void printDetailedError(String errorType, String message, Integer line, Integer column, String suggestion) {
        terminal.writer().println();

        // Error header with type
        terminal.writer().println(Colors.BRIGHT_RED + "  ╭─ " + Colors.BOLD + errorType + " Error" + Colors.RESET);

        // Location if available
        if (line != null && column != null && line > 0) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET +
                    Colors.DIM + "  at line " + line + ", column " + column + Colors.RESET);
        }

        // Error message
        terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_RED + "  │  " + Colors.RESET +
                Colors.BRIGHT_WHITE + message + Colors.RESET);

        // Suggestion
        if (suggestion != null && !suggestion.isEmpty()) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
            terminal.writer().println(Colors.BRIGHT_YELLOW + "  │  💡 " + Colors.RESET +
                    Colors.YELLOW + suggestion + Colors.RESET);
        }

        terminal.writer().println(Colors.BRIGHT_RED + "  ╰─" + Colors.RESET);
        terminal.writer().println();
        terminal.flush();
    }

    /**
     * Prints an error with code snippet highlighting.
     */
    public void printErrorWithCode(String errorType, String message, String code,
                                   Integer line, Integer column, String suggestion) {
        terminal.writer().println();

        // Error header
        terminal.writer().println(Colors.BRIGHT_RED + "  ╭─ " + Colors.BOLD + errorType + " Error" + Colors.RESET);

        // Location
        if (line != null && column != null && line > 0) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET +
                    Colors.DIM + "  at line " + line + ", column " + column + Colors.RESET);
        }

        // Error message
        terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_RED + "  │  " + Colors.RESET +
                Colors.BRIGHT_WHITE + message + Colors.RESET);

        // Code snippet with error pointer
        if (code != null && !code.isEmpty() && column != null && column >= 0) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
            terminal.writer().println(Colors.BRIGHT_RED + "  │  " + Colors.RESET +
                    Colors.DIM + code + Colors.RESET);

            // Error pointer (^)
            StringBuilder pointer = new StringBuilder();
            pointer.append(Colors.BRIGHT_RED).append("  │  ");
            for (int i = 0; i < column; i++) {
                pointer.append(" ");
            }
            pointer.append("^");
            pointer.append(Colors.RESET);
            terminal.writer().println(pointer.toString());
        }

        // Suggestion
        if (suggestion != null && !suggestion.isEmpty()) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
            terminal.writer().println(Colors.BRIGHT_YELLOW + "  │  💡 " + Colors.RESET +
                    Colors.YELLOW + suggestion + Colors.RESET);
        }

        terminal.writer().println(Colors.BRIGHT_RED + "  ╰─" + Colors.RESET);
        terminal.writer().println();
        terminal.flush();
    }


    /**
     * Prints an info message.
     */
    public void printInfo(String message) {
        terminal.writer().println(Colors.BRIGHT_CYAN + "  ℹ " + Colors.RESET + message);
        terminal.flush();
    }

    /**
     * Prints a success message.
     */
    public void printSuccess(String message) {
        terminal.writer().println(Colors.BRIGHT_GREEN + "  ✓ " + Colors.RESET + message);
        terminal.flush();
    }

    /**
     * Prints a warning message.
     */
    public void printWarning(String message) {
        terminal.writer().println(Colors.BRIGHT_YELLOW + "  ⚠ " + Colors.RESET + message);
        terminal.flush();
    }

    /**
     * Prints a section header.
     */
    public void printHeader(String title) {
        terminal.writer().println();
        terminal.writer().println(Colors.BOLD + Colors.BRIGHT_CYAN + "  " + title + Colors.RESET);
        terminal.writer().println(Colors.DIM + "  " + "─".repeat(title.length()) + Colors.RESET);
        terminal.flush();
    }

    /**
     * Prints a plain message.
     */
    public void println(String message) {
        terminal.writer().println("  " + message);
        terminal.flush();
    }

    /**
     * Prints a blank line.
     */
    public void println() {
        terminal.writer().println();
        terminal.flush();
    }

    /**
     * Prints the help message.
     */
    public void printHelp() {
        printHeader("REPL Commands");
        println(Colors.BRIGHT_CYAN + ":help" + Colors.RESET + "              Show this help message");
        println(Colors.BRIGHT_CYAN + ":quit" + Colors.RESET + " or " + Colors.BRIGHT_CYAN + ":exit" + Colors.RESET + "    Exit the REPL");
        println(Colors.BRIGHT_CYAN + ":reset" + Colors.RESET + "             Reset the REPL state");
        println(Colors.BRIGHT_CYAN + ":context" + Colors.RESET + "           Show current context (imports, functions, classes)");
        println(Colors.BRIGHT_CYAN + ":imports" + Colors.RESET + "           Show current imports");
        println(Colors.BRIGHT_CYAN + ":definitions" + Colors.RESET + "       Show defined functions and types");
        println(Colors.BRIGHT_CYAN + ":clear" + Colors.RESET + "             Clear the screen");
        println();
        printHeader("Examples");
        println(Colors.DIM + "// Simple expressions" + Colors.RESET);
        println(Colors.BRIGHT_GREEN + "1 + 2" + Colors.RESET);
        println(Colors.BRIGHT_GREEN + "\"Hello, \" + \"World!\"" + Colors.RESET);
        println();
        println(Colors.DIM + "// Variable declarations" + Colors.RESET);
        println(Colors.BRIGHT_GREEN + "let x = 42" + Colors.RESET);
        println(Colors.BRIGHT_GREEN + "let name = \"Firefly\"" + Colors.RESET);
        println();
        println(Colors.DIM + "// Function definitions" + Colors.RESET);
        println(Colors.BRIGHT_GREEN + "fn add(a: Int, b: Int) -> Int { return a + b; }" + Colors.RESET);
        println();
        println(Colors.DIM + "// Imports" + Colors.RESET);
        println(Colors.BRIGHT_GREEN + "use java::util::ArrayList" + Colors.RESET);
        println();
    }

    /**
     * Clears the screen.
     */
    public void clearScreen() {
        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
        terminal.flush();
    }

    /**
     * Closes the terminal.
     */
    public void close() throws IOException {
        terminal.close();
    }
}


