package com.firefly.compiler.diagnostic;

import com.firefly.compiler.ast.SourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects and reports compilation diagnostics (errors, warnings, hints).
 */
public class DiagnosticReporter {
    
    private final List<Diagnostic> diagnostics;
    private final boolean colorOutput;
    private final Map<String, List<String>> sourceCache;
    
    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    
    public DiagnosticReporter() {
        this(true);
    }
    
    public DiagnosticReporter(boolean colorOutput) {
        this.diagnostics = new ArrayList<>();
        this.colorOutput = colorOutput;
        this.sourceCache = new HashMap<>();
    }
    
    /**
     * Report an error.
     */
    public void error(String code, String message, SourceLocation location) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, code, message, location));
    }
    
    public void error(String code, String message, SourceLocation location, String suggestion) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, code, message, location, suggestion));
    }
    
    /**
     * Report a warning.
     */
    public void warning(String code, String message, SourceLocation location) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, code, message, location));
    }
    
    public void warning(String code, String message, SourceLocation location, String suggestion) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, code, message, location, suggestion));
    }
    
    /**
     * Report an info message.
     */
    public void info(String code, String message, SourceLocation location) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.INFO, code, message, location));
    }
    
    /**
     * Report a hint.
     */
    public void hint(String code, String message, SourceLocation location, String suggestion) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.HINT, code, message, location, suggestion));
    }
    
    /**
     * Check if any errors have been reported.
     */
    public boolean hasErrors() {
        return diagnostics.stream()
            .anyMatch(d -> d.getSeverity() == Diagnostic.Severity.ERROR);
    }
    
    /**
     * Check if any warnings have been reported.
     */
    public boolean hasWarnings() {
        return diagnostics.stream()
            .anyMatch(d -> d.getSeverity() == Diagnostic.Severity.WARNING);
    }
    
    /**
     * Get count of errors.
     */
    public int getErrorCount() {
        return (int) diagnostics.stream()
            .filter(d -> d.getSeverity() == Diagnostic.Severity.ERROR)
            .count();
    }
    
    /**
     * Get count of warnings.
     */
    public int getWarningCount() {
        return (int) diagnostics.stream()
            .filter(d -> d.getSeverity() == Diagnostic.Severity.WARNING)
            .count();
    }
    
    /**
     * Get all diagnostics.
     */
    public List<Diagnostic> getDiagnostics() {
        return new ArrayList<>(diagnostics);
    }
    
    /**
     * Clear all diagnostics.
     */
    public void clear() {
        diagnostics.clear();
    }
    
    /**
     * Print all diagnostics to stdout.
     */
    public void print() {
        for (Diagnostic diagnostic : diagnostics) {
            System.out.println(format(diagnostic));
        }
        
        // Print summary
        if (!diagnostics.isEmpty()) {
            System.out.println();
            printSummary();
        }
    }
    
    /**
     * Print summary of diagnostics.
     */
    public void printSummary() {
        int errors = getErrorCount();
        int warnings = getWarningCount();
        
        if (errors > 0 || warnings > 0) {
            StringBuilder summary = new StringBuilder();
            
            if (errors > 0) {
                String color = colorOutput ? RED : "";
                summary.append(color).append(errors).append(" error");
                if (errors > 1) summary.append("s");
                summary.append(colorOutput ? RESET : "");
            }
            
            if (errors > 0 && warnings > 0) {
                summary.append(", ");
            }
            
            if (warnings > 0) {
                String color = colorOutput ? YELLOW : "";
                summary.append(color).append(warnings).append(" warning");
                if (warnings > 1) summary.append("s");
                summary.append(colorOutput ? RESET : "");
            }
            
            System.out.println("Compilation finished with " + summary.toString());
        } else {
            String color = colorOutput ? CYAN : "";
            System.out.println(color + "✓ Compilation successful!" + (colorOutput ? RESET : ""));
        }
    }
    
    /**
     * Format a single diagnostic for display with source context.
     */
    private String format(Diagnostic diagnostic) {
        StringBuilder sb = new StringBuilder();
        
        // Location and severity header
        if (diagnostic.hasLocation()) {
            SourceLocation loc = diagnostic.getLocation();
            
            // Header line: file:line:column
            if (colorOutput) {
                sb.append(BOLD).append(BLUE);
            }
            sb.append("┌─ ");
            sb.append(loc.fileName())
              .append(":").append(loc.line())
              .append(":").append(loc.column());
            if (colorOutput) {
                sb.append(RESET);
            }
            sb.append("\n");
            
            // Source code snippet with context
            String snippet = getSourceSnippet(loc);
            if (snippet != null) {
                sb.append(snippet);
            }
        }
        
        // Error message with severity
        String severityColor = getSeverityColor(diagnostic.getSeverity());
        String severityText = getSeverityText(diagnostic.getSeverity());
        
        if (colorOutput) {
            sb.append(severityColor).append(BOLD);
        }
        sb.append(severityText).append("[").append(diagnostic.getCode()).append("]");
        if (colorOutput) {
            sb.append(RESET);
        }
        sb.append(": ").append(diagnostic.getMessage());
        
        // Suggestion
        if (diagnostic.hasSuggestion()) {
            sb.append("\n");
            if (colorOutput) {
                sb.append(CYAN).append("  ╰─▶ ");
            } else {
                sb.append("  └─> ");
            }
            sb.append(diagnostic.getSuggestion());
            if (colorOutput) {
                sb.append(RESET);
            }
        }
        
        return sb.toString();
    }
    
    private String getSeverityColor(Diagnostic.Severity severity) {
        switch (severity) {
            case ERROR: return RED;
            case WARNING: return YELLOW;
            case INFO: return BLUE;
            case HINT: return CYAN;
            default: return "";
        }
    }
    
    private String getSeverityText(Diagnostic.Severity severity) {
        switch (severity) {
            case ERROR: return "error";
            case WARNING: return "warning";
            case INFO: return "info";
            case HINT: return "hint";
            default: return "unknown";
        }
    }
    
    /**
     * Get source code snippet with error context.
     */
    private String getSourceSnippet(SourceLocation loc) {
        List<String> lines = loadSourceFile(loc.fileName());
        if (lines == null || loc.line() < 1 || loc.line() > lines.size()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        int targetLine = loc.line();
        int column = loc.column();
        
        // Show 2 lines before, the error line, and 1 line after
        int startLine = Math.max(1, targetLine - 2);
        int endLine = Math.min(lines.size(), targetLine + 1);
        
        // Calculate max line number width for alignment
        int lineNumWidth = String.valueOf(endLine).length();
        
        for (int i = startLine; i <= endLine; i++) {
            String line = lines.get(i - 1); // 0-indexed
            boolean isErrorLine = (i == targetLine);
            
            // Line number with prefix
            if (colorOutput) {
                sb.append(BLUE);
            }
            sb.append("│ ");
            if (colorOutput) {
                sb.append(RESET);
            }
            
            // Highlight error line number
            if (isErrorLine && colorOutput) {
                sb.append(BOLD).append(RED);
            }
            sb.append(String.format("%" + lineNumWidth + "d", i));
            if (isErrorLine && colorOutput) {
                sb.append(RESET);
            }
            
            sb.append(" │ ").append(line).append("\n");
            
            // Error indicator with caret (^) pointing to the column
            if (isErrorLine) {
                if (colorOutput) {
                    sb.append(BLUE);
                }
                sb.append("│ ");
                if (colorOutput) {
                    sb.append(RESET);
                }
                
                // Padding before caret
                sb.append(" ".repeat(lineNumWidth + 3)); // line num width + " │ "
                
                if (column > 0 && column <= line.length() + 1) {
                    sb.append(" ".repeat(column - 1));
                }
                
                if (colorOutput) {
                    sb.append(RED).append(BOLD);
                }
                sb.append("^");
                if (colorOutput) {
                    sb.append(RESET);
                }
                sb.append("\n");
            }
        }
        
        if (colorOutput) {
            sb.append(BLUE);
        }
        sb.append("│\n");
        if (colorOutput) {
            sb.append(RESET);
        }
        
        return sb.toString();
    }
    
    /**
     * Load source file into cache.
     */
    private List<String> loadSourceFile(String fileName) {
        if (sourceCache.containsKey(fileName)) {
            return sourceCache.get(fileName);
        }
        
        try {
            Path path = Paths.get(fileName);
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                sourceCache.put(fileName, lines);
                return lines;
            }
        } catch (IOException e) {
            // Silently fail - error formatting shouldn't crash compilation
        }
        
        return null;
    }
}
