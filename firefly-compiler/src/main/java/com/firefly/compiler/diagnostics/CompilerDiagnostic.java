package com.firefly.compiler.diagnostics;

import com.firefly.compiler.ast.SourceLocation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Professional compiler diagnostic system.
 * Reports errors, warnings, and information with precise location and phase information.
 */
public class CompilerDiagnostic {
    
    private final Level level;
    private final Phase phase;
    private final String message;
    private final SourceLocation location;
    private final String hint;
    
    public enum Level {
        ERROR("✗", "\u001B[31m"),      // Red
        WARNING("⚠", "\u001B[33m"),    // Yellow
        INFO("ℹ", "\u001B[34m");       // Blue
        
        private final String symbol;
        private final String color;
        
        Level(String symbol, String color) {
            this.symbol = symbol;
            this.color = color;
        }
        
        public String getSymbol() { return symbol; }
        public String getColor() { return color; }
    }
    
    public enum Phase {
        LEXING("Lexical Analysis"),
        PARSING("Syntax Analysis"),
        SEMANTIC("Semantic Analysis"),
        TYPE_CHECKING("Type Checking"),
        CODE_GENERATION("Code Generation"),
        OPTIMIZATION("Optimization");
        
        private final String displayName;
        
        Phase(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    private CompilerDiagnostic(Level level, Phase phase, String message, SourceLocation location, String hint) {
        this.level = level;
        this.phase = phase;
        this.message = message;
        this.location = location;
        this.hint = hint;
    }
    
    public static CompilerDiagnostic error(Phase phase, String message, SourceLocation location) {
        return new CompilerDiagnostic(Level.ERROR, phase, message, location, null);
    }
    
    public static CompilerDiagnostic error(Phase phase, String message, SourceLocation location, String hint) {
        return new CompilerDiagnostic(Level.ERROR, phase, message, location, hint);
    }
    
    public static CompilerDiagnostic warning(Phase phase, String message, SourceLocation location) {
        return new CompilerDiagnostic(Level.WARNING, phase, message, location, null);
    }
    
    public static CompilerDiagnostic warning(Phase phase, String message, SourceLocation location, String hint) {
        return new CompilerDiagnostic(Level.WARNING, phase, message, location, hint);
    }
    
    public static CompilerDiagnostic info(Phase phase, String message) {
        return new CompilerDiagnostic(Level.INFO, phase, message, null, null);
    }
    
    public Level getLevel() { return level; }
    public Phase getPhase() { return phase; }
    public String getMessage() { return message; }
    public SourceLocation getLocation() { return location; }
    public String getHint() { return hint; }
    
    public boolean isError() { return level == Level.ERROR; }
    
    /**
     * Format diagnostic for console output with colors and location
     */
    public String format(boolean useColors) {
        StringBuilder sb = new StringBuilder();

        // If we have location info, show source snippet
        if (location != null && level == Level.ERROR) {
            return formatWithSnippet(useColors);
        }

        // Color prefix
        if (useColors) {
            sb.append(level.getColor());
        }

        // Level symbol and phase
        sb.append(level.getSymbol()).append(" ");
        sb.append("[").append(phase.getDisplayName()).append("] ");

        // Location if available
        if (location != null) {
            sb.append(location.fileName()).append(":");
            sb.append(location.line()).append(":").append(location.column());
            sb.append(" - ");
        }

        // Message
        sb.append(message);

        // Reset color
        if (useColors) {
            sb.append("\u001B[0m");
        }

        // Hint on next line if available
        if (hint != null) {
            sb.append("\n  ");
            if (useColors) {
                sb.append("\u001B[36m"); // Cyan for hints
            }
            sb.append("Hint: ").append(hint);
            if (useColors) {
                sb.append("\u001B[0m");
            }
        }

        return sb.toString();
    }

    /**
     * Format diagnostic with source code snippet
     */
    private String formatWithSnippet(boolean useColors) {
        StringBuilder sb = new StringBuilder();

        // ANSI colors
        String RESET = useColors ? "\u001B[0m" : "";
        String BOLD = useColors ? "\u001B[1m" : "";
        String DIM = useColors ? "\u001B[2m" : "";
        String RED = useColors ? "\u001B[31m" : "";
        String BLUE = useColors ? "\u001B[34m" : "";
        String CYAN = useColors ? "\u001B[36m" : "";
        String BRIGHT_RED = useColors ? "\u001B[91m" : "";
        String BRIGHT_BLUE = useColors ? "\u001B[94m" : "";

        sb.append("\n");

        // Header
        sb.append(BOLD).append(BRIGHT_RED).append("┌─ Error").append(RESET).append("\n");
        sb.append(BOLD).append(BLUE).append("│").append(RESET);
        sb.append(" ").append(BOLD).append(location.fileName()).append(":");
        sb.append(location.line()).append(":").append(location.column()).append(RESET).append("\n");
        sb.append(BOLD).append(BLUE).append("│").append(RESET).append("\n");

        // Try to read source file and show snippet
        try {
            Path sourcePath = Paths.get(location.fileName());
            if (Files.exists(sourcePath)) {
                String[] lines = Files.readString(sourcePath).split("\n");
                int targetLine = location.line();
                int column = location.column();

                // Show 2 lines before and 2 lines after
                int startLine = Math.max(0, targetLine - 3);
                int endLine = Math.min(lines.length - 1, targetLine + 1);
                int lineNumWidth = String.valueOf(endLine + 1).length();

                for (int i = startLine; i <= endLine; i++) {
                    boolean isErrorLine = (i == targetLine - 1);
                    String lineNum = String.format("%" + lineNumWidth + "d", i + 1);
                    String lineContent = i < lines.length ? lines[i] : "";

                    if (isErrorLine) {
                        sb.append(BOLD).append(BLUE).append("│").append(RESET);
                        sb.append(" ").append(BOLD).append(BRIGHT_RED).append(lineNum).append(RESET);
                        sb.append(" ").append(BOLD).append(BLUE).append("│").append(RESET);
                        sb.append(" ").append(lineContent).append("\n");

                        // Caret pointing to error
                        sb.append(BOLD).append(BLUE).append("│").append(RESET);
                        sb.append(" ".repeat(lineNumWidth + 1));
                        sb.append(BOLD).append(BLUE).append("│").append(RESET);
                        sb.append(" ".repeat(Math.max(0, column - 1)));
                        sb.append(BOLD).append(BRIGHT_RED).append("^").append(RESET).append("\n");
                    } else {
                        sb.append(BOLD).append(BLUE).append("│").append(RESET);
                        sb.append(" ").append(DIM).append(lineNum).append(RESET);
                        sb.append(" ").append(BOLD).append(BLUE).append("│").append(RESET);
                        sb.append(" ").append(DIM).append(lineContent).append(RESET).append("\n");
                    }
                }
            }
        } catch (IOException e) {
            // If we can't read the file, just show the location
        }

        sb.append(BOLD).append(BLUE).append("│").append(RESET).append("\n");

        // Error message
        sb.append(BOLD).append(BRIGHT_RED).append("│ ").append(message).append(RESET).append("\n");

        // Hint if available
        if (hint != null) {
            sb.append(BOLD).append(CYAN).append("│ Hint: ").append(hint).append(RESET).append("\n");
        }

        sb.append(BOLD).append(BLUE).append("└").append("─".repeat(79)).append(RESET).append("\n");

        return sb.toString();
    }
    
    @Override
    public String toString() {
        return format(false);
    }
}
