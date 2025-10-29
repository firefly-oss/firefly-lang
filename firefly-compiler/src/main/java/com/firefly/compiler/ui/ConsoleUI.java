package com.firefly.compiler.ui;

/**
 * Professional console UI utilities for the Flylang compiler.
 * Handles dynamic box drawing, progress indicators, and error formatting.
 */
public class ConsoleUI {
    
    // ANSI color codes
    public static class Colors {
        public static final String RESET = "\u001B[0m";
        public static final String BOLD = "\u001B[1m";
        public static final String DIM = "\u001B[2m";
        
        public static final String RED = "\u001B[31m";
        public static final String GREEN = "\u001B[32m";
        public static final String YELLOW = "\u001B[33m";
        public static final String BLUE = "\u001B[34m";
        public static final String CYAN = "\u001B[36m";
        
        public static final String BRIGHT_RED = "\u001B[91m";
        public static final String BRIGHT_GREEN = "\u001B[92m";
        public static final String BRIGHT_YELLOW = "\u001B[93m";
        public static final String BRIGHT_BLUE = "\u001B[94m";
        public static final String BRIGHT_CYAN = "\u001B[96m";
        public static final String BRIGHT_WHITE = "\u001B[97m";
    }
    
    // Box drawing characters
    private static final String TOP_LEFT = "┌";
    private static final String TOP_RIGHT = "┐";
    private static final String BOTTOM_LEFT = "└";
    private static final String BOTTOM_RIGHT = "┘";
    private static final String HORIZONTAL = "─";
    private static final String VERTICAL = "│";
    
    /**
     * Strip ANSI color codes to get actual text length
     */
    private static String stripAnsi(String text) {
        return text.replaceAll("\u001B\\[[;\\d]*m", "");
    }
    
    /**
     * Get the visible length of text (without ANSI codes)
     */
    private static int visibleLength(String text) {
        return stripAnsi(text).length();
    }
    
    /**
     * Draw a box with dynamic width based on content
     */
    public static void printBox(String content, String color) {
        int contentLength = visibleLength(content);
        int boxWidth = contentLength + 2; // 1 space padding on each side
        
        // Top border
        System.out.println(Colors.BOLD + color + TOP_LEFT + HORIZONTAL.repeat(boxWidth) + TOP_RIGHT + Colors.RESET);
        
        // Content line
        System.out.println(Colors.BOLD + color + VERTICAL + Colors.RESET + 
                          " " + content + " " + 
                          Colors.BOLD + color + VERTICAL + Colors.RESET);
        
        // Bottom border
        System.out.println(Colors.BOLD + color + BOTTOM_LEFT + HORIZONTAL.repeat(boxWidth) + BOTTOM_RIGHT + Colors.RESET);
    }
    
    /**
     * Draw a success box
     */
    public static void printSuccessBox(String message) {
        // Strip any ANSI codes from the message first
        String cleanMessage = stripAnsi(message);
        String content = Colors.BRIGHT_GREEN + "Success!" + Colors.RESET + " " + cleanMessage;
        printBox(content, Colors.BRIGHT_GREEN);
    }

    /**
     * Draw an error box
     */
    public static void printErrorBox(String message) {
        String cleanMessage = stripAnsi(message);
        String content = Colors.BRIGHT_RED + "Error!" + Colors.RESET + " " + cleanMessage;
        printBox(content, Colors.BRIGHT_RED);
    }

    /**
     * Draw a warning box
     */
    public static void printWarningBox(String message) {
        String cleanMessage = stripAnsi(message);
        String content = Colors.BRIGHT_YELLOW + "Warning!" + Colors.RESET + " " + cleanMessage;
        printBox(content, Colors.BRIGHT_YELLOW);
    }

    /**
     * Draw an info box
     */
    public static void printInfoBox(String message) {
        String cleanMessage = stripAnsi(message);
        String content = Colors.BRIGHT_CYAN + "Info:" + Colors.RESET + " " + cleanMessage;
        printBox(content, Colors.BRIGHT_CYAN);
    }
    
    /**
     * Print a phase header (e.g., "[1/4] Parsing...")
     */
    public static void printPhase(int current, int total, String phaseName) {
        System.out.println(Colors.BRIGHT_BLUE + "[" + current + "/" + total + "]" + Colors.RESET + 
                          " " + Colors.BOLD + phaseName + Colors.RESET + Colors.DIM + "..." + Colors.RESET);
    }
    
    /**
     * Print a success checkmark with message
     */
    public static void printSuccess(String message) {
        System.out.println("  " + Colors.BRIGHT_GREEN + "✓" + Colors.RESET + " " + message);
    }
    
    /**
     * Print an error X with message
     */
    public static void printError(String message) {
        System.out.println("  " + Colors.BRIGHT_RED + "✗" + Colors.RESET + " " + message);
    }
    
    /**
     * Print a warning symbol with message
     */
    public static void printWarning(String message) {
        System.out.println("  " + Colors.BRIGHT_YELLOW + "⚠" + Colors.RESET + " " + message);
    }
    
    /**
     * Print an info symbol with message
     */
    public static void printInfo(String message) {
        System.out.println("  " + Colors.BRIGHT_CYAN + "ℹ" + Colors.RESET + " " + message);
    }
    
    /**
     * Print a dimmed detail message
     */
    public static void printDetail(String message) {
        System.out.println("    " + Colors.DIM + message + Colors.RESET);
    }
    
    /**
     * Print a horizontal divider
     */
    public static void printDivider() {
        System.out.println(Colors.DIM + HORIZONTAL.repeat(80) + Colors.RESET);
    }
    
    /**
     * Format a file path with highlighting
     */
    public static String formatPath(String path) {
        return Colors.BOLD + Colors.BRIGHT_WHITE + path + Colors.RESET;
    }
    
    /**
     * Format a duration in milliseconds
     */
    public static String formatDuration(long ms) {
        return Colors.DIM + "(" + ms + "ms)" + Colors.RESET;
    }
    
    /**
     * Format a count (e.g., "5 files", "1 error")
     */
    public static String formatCount(int count, String singular, String plural) {
        String word = count == 1 ? singular : plural;
        return count + " " + word;
    }
    
    /**
     * Print error with source code snippet
     */
    public static void printErrorWithSnippet(String file, int line, int column, 
                                            String errorMessage, String[] sourceLines) {
        System.out.println();
        System.out.println(Colors.BOLD + Colors.BRIGHT_RED + "┌─ Error" + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BLUE + "│" + Colors.RESET + 
                          " " + Colors.BOLD + file + ":" + line + ":" + column + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BLUE + "│" + Colors.RESET);
        
        // Print source context (3 lines before and after)
        int startLine = Math.max(0, line - 3);
        int endLine = Math.min(sourceLines.length - 1, line + 2);
        int lineNumWidth = String.valueOf(endLine + 1).length();
        
        for (int i = startLine; i <= endLine; i++) {
            boolean isErrorLine = (i == line - 1);
            String lineNum = String.format("%" + lineNumWidth + "d", i + 1);
            
            if (isErrorLine) {
                System.out.println(Colors.BOLD + Colors.BLUE + "│" + Colors.RESET + 
                                  " " + Colors.BOLD + Colors.BRIGHT_RED + lineNum + Colors.RESET + 
                                  " " + Colors.BOLD + Colors.BLUE + "│" + Colors.RESET + 
                                  " " + sourceLines[i]);
                
                // Print caret pointing to error column
                System.out.println(Colors.BOLD + Colors.BLUE + "│" + Colors.RESET + 
                                  " " + " ".repeat(lineNumWidth) + 
                                  " " + Colors.BOLD + Colors.BLUE + "│" + Colors.RESET + 
                                  " " + " ".repeat(column - 1) + 
                                  Colors.BOLD + Colors.BRIGHT_RED + "^" + Colors.RESET);
            } else {
                System.out.println(Colors.BOLD + Colors.BLUE + "│" + Colors.RESET + 
                                  " " + Colors.DIM + lineNum + Colors.RESET + 
                                  " " + Colors.BOLD + Colors.BLUE + "│" + Colors.RESET + 
                                  " " + Colors.DIM + sourceLines[i] + Colors.RESET);
            }
        }
        
        System.out.println(Colors.BOLD + Colors.BLUE + "│" + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BRIGHT_RED + "│ " + errorMessage + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BLUE + "└" + HORIZONTAL.repeat(79) + Colors.RESET);
        System.out.println();
    }
    
    /**
     * Print compilation summary
     */
    public static void printCompilationSummary(boolean success, long duration, 
                                              int linesOfCode, int filesCompiled) {
        System.out.println();
        
        if (success) {
            String message = "Compilation completed " + 
                           Colors.DIM + "(" + duration + "ms, " + 
                           formatCount(filesCompiled, "file", "files") + ", " +
                           formatCount(linesOfCode, "line", "lines") + ")" + Colors.RESET;
            printSuccessBox(message);
        } else {
            String message = "Compilation failed " + 
                           Colors.DIM + "(" + duration + "ms)" + Colors.RESET;
            printErrorBox(message);
        }
    }
    
    /**
     * Print a header for a compilation file
     */
    public static void printFileHeader(String filename) {
        String content = "Compiling: " + Colors.BOLD + filename + Colors.RESET;
        printBox(content, Colors.BRIGHT_CYAN);
        System.out.println();
    }
}

