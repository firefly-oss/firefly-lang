package com.firefly.repl;

import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.util.List;
import java.util.Map;

/**
 * Context panel that displays the current REPL state.
 * Shows imports, functions, classes, and variables in a sidebar.
 */
public class ContextPanel {
    
    private final Terminal terminal;
    private final ReplEngine engine;
    private static final int PANEL_WIDTH = 35;
    
    public ContextPanel(Terminal terminal, ReplEngine engine) {
        this.terminal = terminal;
        this.engine = engine;
    }

    /**
     * Gets the engine.
     */
    public ReplEngine getEngine() {
        return engine;
    }
    
    /**
     * Displays a comprehensive context panel with detailed information.
     */
    public void display() {
        terminal.writer().println();

        // Main Header
        printSectionDivider("═");
        terminal.writer().println(ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_CYAN +
                "║" + centerText("🔥 FIREFLY REPL - CONTEXT OVERVIEW 🔥", 79) +
                ReplUI.Colors.BRIGHT_CYAN + "║" + ReplUI.Colors.RESET);
        printSectionDivider("═");
        terminal.writer().println();

        // Collect statistics
        List<String> imports = engine.getImports();
        List<ReplEngine.FunctionInfo> functions = engine.getFunctions();
        List<ReplEngine.ClassInfo> classes = engine.getClasses();
        Map<String, Object> variables = engine.getVariableValues();

        int importCount = imports.size();
        int funcCount = functions.size();
        int classCount = classes.size();
        int varCount = variables.size();
        int totalDefinitions = importCount + funcCount + classCount + varCount;

        // Table of Contents
        terminal.writer().println(ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE +
                "  📑 TABLE OF CONTENTS" + ReplUI.Colors.RESET);
        printLineDivider();

        if (totalDefinitions == 0) {
            terminal.writer().println(ReplUI.Colors.DIM + "    No definitions available yet." + ReplUI.Colors.RESET);
        } else {
            if (importCount > 0) {
                terminal.writer().println(ReplUI.Colors.BRIGHT_CYAN + "    [1] " + ReplUI.Colors.RESET +
                        "📦 Imports" + ReplUI.Colors.DIM + " ...................... " +
                        ReplUI.Colors.BRIGHT_WHITE + importCount + " module" + (importCount != 1 ? "s" : "") + ReplUI.Colors.RESET);
            }
            if (funcCount > 0) {
                terminal.writer().println(ReplUI.Colors.BRIGHT_YELLOW + "    [2] " + ReplUI.Colors.RESET +
                        "🔧 Functions" + ReplUI.Colors.DIM + " .................... " +
                        ReplUI.Colors.BRIGHT_WHITE + funcCount + " function" + (funcCount != 1 ? "s" : "") + ReplUI.Colors.RESET);
            }
            if (classCount > 0) {
                terminal.writer().println(ReplUI.Colors.BRIGHT_MAGENTA + "    [3] " + ReplUI.Colors.RESET +
                        "📝 Classes" + ReplUI.Colors.DIM + " ...................... " +
                        ReplUI.Colors.BRIGHT_WHITE + classCount + " class" + (classCount != 1 ? "es" : "") + ReplUI.Colors.RESET);
            }
            if (varCount > 0) {
                terminal.writer().println(ReplUI.Colors.BRIGHT_GREEN + "    [4] " + ReplUI.Colors.RESET +
                        "📊 Variables" + ReplUI.Colors.DIM + " .................... " +
                        ReplUI.Colors.BRIGHT_WHITE + varCount + " variable" + (varCount != 1 ? "s" : "") + ReplUI.Colors.RESET);
            }
        }
        terminal.writer().println();

        // Statistics Dashboard
        terminal.writer().println(ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE +
                "  📊 STATISTICS DASHBOARD" + ReplUI.Colors.RESET);
        printLineDivider();

        // Statistics table
        terminal.writer().println(ReplUI.Colors.DIM + "  ┌─────────────────────────┬──────────┬─────────────────────────────────────┐" + ReplUI.Colors.RESET);
        terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE +
                "Category                " + ReplUI.Colors.RESET + ReplUI.Colors.DIM + "│ " +
                ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Count    " + ReplUI.Colors.RESET + ReplUI.Colors.DIM + "│ " +
                ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Status                              " +
                ReplUI.Colors.RESET + ReplUI.Colors.DIM + "│" + ReplUI.Colors.RESET);
        terminal.writer().println(ReplUI.Colors.DIM + "  ├─────────────────────────┼──────────┼─────────────────────────────────────┤" + ReplUI.Colors.RESET);

        printStatRow("📦 Imports", importCount, getStatusBar(importCount, 10));
        printStatRow("🔧 Functions", funcCount, getStatusBar(funcCount, 10));
        printStatRow("📝 Classes", classCount, getStatusBar(classCount, 5));
        printStatRow("📊 Variables", varCount, getStatusBar(varCount, 10));

        terminal.writer().println(ReplUI.Colors.DIM + "  ├─────────────────────────┼──────────┼─────────────────────────────────────┤" + ReplUI.Colors.RESET);
        printStatRow("🎯 TOTAL DEFINITIONS", totalDefinitions,
                ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_CYAN + "●".repeat(Math.min(totalDefinitions, 20)) + ReplUI.Colors.RESET);
        terminal.writer().println(ReplUI.Colors.DIM + "  └─────────────────────────┴──────────┴─────────────────────────────────────┘" + ReplUI.Colors.RESET);
        terminal.writer().println();

        // Imports Section
        if (importCount > 0) {
            printSectionHeader("[1] 📦 IMPORTS", "Loaded Modules & Packages");

            // Categorize imports
            List<String> stdImports = new java.util.ArrayList<>();
            List<String> javaImports = new java.util.ArrayList<>();
            List<String> otherImports = new java.util.ArrayList<>();

            for (String imp : imports) {
                if (imp.startsWith("use firefly::std::") || imp.startsWith("firefly::std::")) {
                    stdImports.add(imp);
                } else if (imp.startsWith("use java::") || imp.startsWith("java::")) {
                    javaImports.add(imp);
                } else {
                    otherImports.add(imp);
                }
            }

            // Display imports in a detailed table
            terminal.writer().println(ReplUI.Colors.DIM + "  ┌────┬──────────────┬────────────────────────────────────────────────────────┐" + ReplUI.Colors.RESET);
            terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE +
                    "#  " + ReplUI.Colors.RESET + ReplUI.Colors.DIM + "│ " +
                    ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Category     " +
                    ReplUI.Colors.RESET + ReplUI.Colors.DIM + "│ " +
                    ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Module Path                                            " +
                    ReplUI.Colors.RESET + ReplUI.Colors.DIM + "│" + ReplUI.Colors.RESET);
            terminal.writer().println(ReplUI.Colors.DIM + "  ├────┼──────────────┼────────────────────────────────────────────────────────┤" + ReplUI.Colors.RESET);

            int idx = 1;

            // Display standard library imports
            for (String imp : stdImports) {
                String cleanImp = imp.replace("use ", "");
                printImportRow(idx++, "Std Lib", cleanImp);
            }

            // Display Java interop imports
            for (String imp : javaImports) {
                String cleanImp = imp.replace("use ", "");
                printImportRow(idx++, "Java", cleanImp);
            }

            // Display other imports
            for (String imp : otherImports) {
                String cleanImp = imp.replace("use ", "");
                printImportRow(idx++, "User", cleanImp);
            }

            terminal.writer().println(ReplUI.Colors.DIM + "  └────┴──────────────┴────────────────────────────────────────────────────────┘" + ReplUI.Colors.RESET);
            terminal.writer().println();
        }

        // Functions Section
        if (funcCount > 0) {
            printSectionHeader("[2] 🔧 FUNCTIONS", "User-Defined Functions");

            for (int i = 0; i < functions.size(); i++) {
                ReplEngine.FunctionInfo func = functions.get(i);

                // Function header
                terminal.writer().println(ReplUI.Colors.BRIGHT_YELLOW + "  ┌─ Function #" + (i + 1) + " " +
                        ReplUI.Colors.RESET + ReplUI.Colors.DIM + "─".repeat(65) + ReplUI.Colors.RESET);

                // Function name
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Name:        " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_YELLOW + func.getName() + ReplUI.Colors.RESET);

                // Full signature
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Signature:   " + ReplUI.Colors.RESET +
                        ReplUI.Colors.YELLOW + func.getSignature() + ReplUI.Colors.RESET);

                // Parse parameters from signature
                String params = extractParameters(func.getSignature());
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Parameters:  " + ReplUI.Colors.RESET +
                        ReplUI.Colors.CYAN + (params.isEmpty() ? "(none)" : params) + ReplUI.Colors.RESET);

                // Return type
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Returns:     " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_YELLOW + func.getReturnType() + ReplUI.Colors.RESET);

                // Parameter count
                int paramCount = countParameters(func.getSignature());
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Param Count: " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_WHITE + paramCount + ReplUI.Colors.RESET);

                // Full declaration
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Declaration: " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_YELLOW + "fn " + ReplUI.Colors.RESET +
                        ReplUI.Colors.YELLOW + func.getSignature() + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_YELLOW + " -> " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_YELLOW + func.getReturnType() + ReplUI.Colors.RESET);

                terminal.writer().println(ReplUI.Colors.DIM + "  └" + "─".repeat(78) + ReplUI.Colors.RESET);
                terminal.writer().println();
            }
        }

        // Classes Section
        if (classCount > 0) {
            printSectionHeader("[3] 📝 CLASSES", "User-Defined Classes");

            for (int i = 0; i < classes.size(); i++) {
                ReplEngine.ClassInfo cls = classes.get(i);

                // Class header
                terminal.writer().println(ReplUI.Colors.BRIGHT_MAGENTA + "  ┌─ Class #" + (i + 1) + " " +
                        ReplUI.Colors.RESET + ReplUI.Colors.DIM + "─".repeat(67) + ReplUI.Colors.RESET);

                // Class name
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Name:        " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_MAGENTA + cls.getName() + ReplUI.Colors.RESET);

                // Class type
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Type:        " + ReplUI.Colors.RESET +
                        ReplUI.Colors.MAGENTA + "User-defined class" + ReplUI.Colors.RESET);

                // Declaration
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Declaration: " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_MAGENTA + "class " + ReplUI.Colors.RESET +
                        ReplUI.Colors.MAGENTA + cls.getName() + ReplUI.Colors.RESET);

                terminal.writer().println(ReplUI.Colors.DIM + "  └" + "─".repeat(78) + ReplUI.Colors.RESET);
                terminal.writer().println();
            }
        }

        // Variables Section
        if (varCount > 0) {
            printSectionHeader("[4] 📊 VARIABLES", "Runtime Variables");

            int i = 1;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String varName = entry.getKey();
                Object varValue = entry.getValue();
                String valueStr = varValue != null ? varValue.toString() : "null";
                String typeStr = varValue != null ? varValue.getClass().getSimpleName() : "null";
                String fullTypeName = varValue != null ? varValue.getClass().getName() : "null";

                // Variable header
                terminal.writer().println(ReplUI.Colors.BRIGHT_GREEN + "  ┌─ Variable #" + i + " " +
                        ReplUI.Colors.RESET + ReplUI.Colors.DIM + "─".repeat(65) + ReplUI.Colors.RESET);

                // Variable name
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Name:        " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BRIGHT_GREEN + varName + ReplUI.Colors.RESET);

                // Type
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Type:        " + ReplUI.Colors.RESET +
                        ReplUI.Colors.GREEN + typeStr + ReplUI.Colors.RESET);

                // Full type name
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Full Type:   " + ReplUI.Colors.RESET +
                        ReplUI.Colors.DIM + fullTypeName + ReplUI.Colors.RESET);

                // Value (truncate if too long)
                String displayValue = valueStr;
                if (displayValue.length() > 60) {
                    displayValue = displayValue.substring(0, 57) + "...";
                }
                terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                        ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Value:       " + ReplUI.Colors.RESET +
                        ReplUI.Colors.GREEN + displayValue + ReplUI.Colors.RESET);

                // If value was truncated, show full value on next lines
                if (valueStr.length() > 60) {
                    terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                            ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE + "Full Value:  " + ReplUI.Colors.RESET);
                    // Split long value into multiple lines
                    int chunkSize = 60;
                    for (int j = 0; j < valueStr.length(); j += chunkSize) {
                        int end = Math.min(j + chunkSize, valueStr.length());
                        terminal.writer().println(ReplUI.Colors.DIM + "  │              " + ReplUI.Colors.RESET +
                                ReplUI.Colors.GREEN + valueStr.substring(j, end) + ReplUI.Colors.RESET);
                    }
                }

                terminal.writer().println(ReplUI.Colors.DIM + "  └" + "─".repeat(78) + ReplUI.Colors.RESET);
                terminal.writer().println();
                i++;
            }
        }

        // Footer with helpful tips and commands
        printSectionDivider("═");
        terminal.writer().println(ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_CYAN +
                "║" + centerText("💡 QUICK TIPS & COMMANDS", 79) +
                ReplUI.Colors.BRIGHT_CYAN + "║" + ReplUI.Colors.RESET);
        printSectionDivider("═");
        terminal.writer().println();

        if (totalDefinitions == 0) {
            terminal.writer().println(ReplUI.Colors.DIM + "  ▸ No definitions yet. Start by importing modules or defining functions!" + ReplUI.Colors.RESET);
            terminal.writer().println();
        }

        terminal.writer().println(ReplUI.Colors.BRIGHT_CYAN + "  ▸ " + ReplUI.Colors.RESET +
                "Type " + ReplUI.Colors.BRIGHT_WHITE + ":help" + ReplUI.Colors.RESET +
                " to see all available REPL commands");
        terminal.writer().println(ReplUI.Colors.BRIGHT_CYAN + "  ▸ " + ReplUI.Colors.RESET +
                "Type " + ReplUI.Colors.BRIGHT_WHITE + ":type <expr>" + ReplUI.Colors.RESET +
                " to check the type of an expression");
        terminal.writer().println(ReplUI.Colors.BRIGHT_CYAN + "  ▸ " + ReplUI.Colors.RESET +
                "Type " + ReplUI.Colors.BRIGHT_WHITE + ":clear" + ReplUI.Colors.RESET +
                " to reset the REPL context");
        terminal.writer().println(ReplUI.Colors.BRIGHT_CYAN + "  ▸ " + ReplUI.Colors.RESET +
                "Use " + ReplUI.Colors.BRIGHT_WHITE + "Ctrl+D" + ReplUI.Colors.RESET +
                " or " + ReplUI.Colors.BRIGHT_WHITE + ":quit" + ReplUI.Colors.RESET +
                " to exit the REPL");

        terminal.writer().println();
        printSectionDivider("═");
        terminal.writer().println();
        terminal.flush();
    }

    /**
     * Helper method to print a section header.
     */
    private void printSectionHeader(String title, String subtitle) {
        terminal.writer().println(ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_WHITE +
                "  " + title + ReplUI.Colors.RESET);
        if (subtitle != null && !subtitle.isEmpty()) {
            terminal.writer().println(ReplUI.Colors.DIM + "  " + subtitle + ReplUI.Colors.RESET);
        }
        printLineDivider();
    }

    /**
     * Helper method to print a line divider.
     */
    private void printLineDivider() {
        terminal.writer().println(ReplUI.Colors.DIM +
                "  ─────────────────────────────────────────────────────────────────────────────" +
                ReplUI.Colors.RESET);
    }

    /**
     * Helper method to print a section divider.
     */
    private void printSectionDivider(String char_) {
        terminal.writer().println(ReplUI.Colors.BOLD + ReplUI.Colors.BRIGHT_CYAN +
                "╔" + char_.repeat(79) + "╗" + ReplUI.Colors.RESET);
    }

    /**
     * Helper method to center text within a given width.
     */
    private String centerText(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text + " ".repeat(Math.max(0, width - text.length() - padding));
    }

    /**
     * Helper method to print a statistics row.
     */
    private void printStatRow(String category, int count, String statusBar) {
        terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                String.format("%-23s", category) +
                ReplUI.Colors.DIM + " │ " + ReplUI.Colors.RESET +
                ReplUI.Colors.BRIGHT_WHITE + String.format("%-8s", count) + ReplUI.Colors.RESET +
                ReplUI.Colors.DIM + "│ " + ReplUI.Colors.RESET +
                statusBar +
                ReplUI.Colors.DIM + " │" + ReplUI.Colors.RESET);
    }

    /**
     * Helper method to generate a status bar based on count.
     */
    private String getStatusBar(int count, int maxForFull) {
        if (count == 0) {
            return ReplUI.Colors.DIM + "○".repeat(10) + ReplUI.Colors.RESET;
        }

        int filled = Math.min(10, (count * 10) / maxForFull);
        String bar = ReplUI.Colors.BRIGHT_GREEN + "●".repeat(filled) + ReplUI.Colors.RESET;
        if (filled < 10) {
            bar += ReplUI.Colors.DIM + "○".repeat(10 - filled) + ReplUI.Colors.RESET;
        }
        return bar;
    }

    /**
     * Helper method to print an import row in the table.
     */
    private void printImportRow(int num, String category, String modulePath) {
        String numStr = String.format("%-2d", num);
        String catStr = String.format("%-12s", category);

        // Truncate module path if too long
        String pathStr = modulePath;
        if (pathStr.length() > 56) {
            pathStr = pathStr.substring(0, 53) + "...";
        }
        pathStr = String.format("%-56s", pathStr);

        terminal.writer().println(ReplUI.Colors.DIM + "  │ " + ReplUI.Colors.RESET +
                ReplUI.Colors.BRIGHT_WHITE + numStr + ReplUI.Colors.RESET +
                ReplUI.Colors.DIM + " │ " + ReplUI.Colors.RESET +
                ReplUI.Colors.CYAN + catStr + ReplUI.Colors.RESET +
                ReplUI.Colors.DIM + "│ " + ReplUI.Colors.RESET +
                ReplUI.Colors.BRIGHT_CYAN + pathStr + ReplUI.Colors.RESET +
                ReplUI.Colors.DIM + " │" + ReplUI.Colors.RESET);
    }

    /**
     * Helper method to extract parameters from a function signature.
     * Example: "greet(name: String)" -> "name: String"
     */
    private String extractParameters(String signature) {
        int startIdx = signature.indexOf('(');
        int endIdx = signature.lastIndexOf(')');

        if (startIdx == -1 || endIdx == -1 || startIdx >= endIdx) {
            return "";
        }

        String params = signature.substring(startIdx + 1, endIdx).trim();
        return params.isEmpty() ? "" : params;
    }

    /**
     * Helper method to count parameters in a function signature.
     */
    private int countParameters(String signature) {
        String params = extractParameters(signature);
        if (params.isEmpty()) {
            return 0;
        }

        // Count commas + 1 (simple heuristic)
        int count = 1;
        for (char c : params.toCharArray()) {
            if (c == ',') {
                count++;
            }
        }
        return count;
    }
}

