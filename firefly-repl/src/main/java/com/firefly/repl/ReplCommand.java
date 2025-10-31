package com.firefly.repl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
 *   <li>:edit - Open external editor to write multi-line buffer and execute</li>
 *   <li>:history - Show recent commands</li>
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
        return input != null && (input.trim().startsWith(":") || input.trim().startsWith("!"));
    }
    
    /**
     * Checks if the input is a shell command.
     */
    public boolean isShellCommand(String input) {
        return input != null && input.trim().startsWith("!");
    }
    
    /**
     * Executes a REPL command.
     * 
     * @param input The command input
     * @return The command result
     */
    public CommandResult execute(String input) {
        // Handle shell commands
        if (isShellCommand(input)) {
            executeShellCommand(input.trim().substring(1));
            return CommandResult.handled();
        }
        
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
            case ":save" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :save <filename>");
                } else {
                    ui.saveHistory(args);
                }
                yield CommandResult.handled();
            }
            case ":history" -> {
                int n = 20;
                if (!args.isEmpty()) {
                    try { n = Math.max(1, Integer.parseInt(args)); } catch (NumberFormatException ignore) {}
                }
                ui.showHistory(n);
                yield CommandResult.handled();
            }
            case ":edit" -> {
                String edited = ui.openEditor("");
                if (edited != null && !edited.trim().isEmpty()) {
                    executeBuffer(edited);
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
            case ":doc" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :doc <symbol>");
                    ui.printInfo("Examples: :doc Option, :doc println, :doc List");
                } else {
                    showDocumentation(args);
                }
                yield CommandResult.handled();
            }
            case ":time" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :time <expression>");
                } else {
                    timeExpression(args, 1);
                }
                yield CommandResult.handled();
            }
            case ":timeit" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :timeit <expression>");
                } else {
                    timeExpression(args, 10);
                }
                yield CommandResult.handled();
            }
            case ":profile" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :profile <expression>");
                } else {
                    profileExpression(args);
                }
                yield CommandResult.handled();
            }
            case ":memprofile" -> {
                if (args.isEmpty()) {
                    ui.printError("Usage: :memprofile <expression>");
                } else {
                    memoryProfile(args);
                }
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
            executeBuffer(content);
        } catch (IOException e) {
            ui.printError("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Execute a multi-line buffer by splitting into balanced snippets.
     */
    private void executeBuffer(String content) {
        List<String> snippets = splitIntoSnippets(content);
        int ok = 0, err = 0;
        for (String sn : snippets) {
            if (sn.trim().isEmpty()) continue;
            ReplEngine.EvalResult result = engine.eval(sn);
            if (result.isSuccess()) ok++; else { err++; ui.printError(result.getError()); }
        }
        ui.printSuccess("Executed " + ok + " snippet(s)" + (err > 0 ? " (" + err + " error(s))" : ""));
    }

    /**
     * Split content into balanced code snippets by tracking braces, parentheses, brackets and strings.
     */
    private List<String> splitIntoSnippets(String content) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int braces = 0, parens = 0, brackets = 0;
        boolean inStr = false, inChar = false, inLineComment = false, inBlockComment = false;
        char prev = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : 0;
            // Handle comments
            if (!inStr && !inChar) {
                if (!inBlockComment && !inLineComment && c == '/' && next == '/') { inLineComment = true; }
                if (!inBlockComment && !inLineComment && c == '/' && next == '*') { inBlockComment = true; }
                if (inLineComment && c == '\n') { inLineComment = false; }
                if (inBlockComment && prev == '*' && c == '/') { inBlockComment = false; }
            }
            if (inLineComment || inBlockComment) { cur.append(c); prev = c; continue; }
            // Strings/char
            if (!inChar && c == '"' && prev != '\\') inStr = !inStr;
            if (!inStr && c == '\'' && prev != '\\') inChar = !inChar;
            if (!inStr && !inChar) {
                if (c == '{') braces++;
                else if (c == '}') braces--;
                else if (c == '(') parens++;
                else if (c == ')') parens--;
                else if (c == '[') brackets++;
                else if (c == ']') brackets--;
            }
            cur.append(c);
            // End of snippet when all balanced and at newline or semicolon
            if (!inStr && !inChar && braces == 0 && parens == 0 && brackets == 0 && (c == '\n' || c == ';' || i == content.length() - 1)) {
                String snippet = cur.toString().trim();
                if (!snippet.isEmpty()) out.add(snippet);
                cur.setLength(0);
            }
            prev = c;
        }
        if (cur.length() > 0) {
            String rest = cur.toString().trim();
            if (!rest.isEmpty()) out.add(rest);
        }
        return out;
    }
    
    /**
     * Shows the type of an expression.
     */
    private void showType(String expression) {
        String t = engine.inferTypeOf(expression);
        if (t != null) {
            ui.printInfo("Type: " + ReplUI.Colors.BRIGHT_CYAN + t + ReplUI.Colors.RESET);
        } else {
            ui.printWarning("Could not infer type");
        }
    }
    
    /**
     * Show documentation for stdlib symbols.
     */
    private void showDocumentation(String symbol) {
        String doc = getDocumentation(symbol);
        if (doc != null) {
            ui.println(doc);
        } else {
            ui.printWarning("No documentation found for: " + symbol);
            ui.printInfo("Available: Option, Result, List, Map, Set, println, print, readLine, readFile");
        }
    }
    
    /**
     * Get documentation for common stdlib symbols.
     */
    private String getDocumentation(String symbol) {
        return switch(symbol) {
            case "Option" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "Option<T>" + ReplUI.Colors.RESET + " - Represents an optional value\n" +
                   "═══════════════════════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::option::{Option, Some, None}" + ReplUI.Colors.RESET + "\n\n" +
                   "Variants:\n" +
                   "  " + ReplUI.Colors.BRIGHT_GREEN + "Some(T)" + ReplUI.Colors.RESET + " - Contains a value\n" +
                   "  " + ReplUI.Colors.BRIGHT_GREEN + "None" + ReplUI.Colors.RESET + "    - No value present\n\n" +
                   "Example:\n" +
                   "  let value: Option<Int> = Some(42);\n" +
                   "  let empty: Option<Int> = None;\n";
                   
            case "Result" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "Result<T, E>" + ReplUI.Colors.RESET + " - Result of an operation that can fail\n" +
                   "═══════════════════════════════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::result::{Result, Ok, Err}" + ReplUI.Colors.RESET + "\n\n" +
                   "Variants:\n" +
                   "  " + ReplUI.Colors.BRIGHT_GREEN + "Ok(T)" + ReplUI.Colors.RESET + "  - Success with value\n" +
                   "  " + ReplUI.Colors.BRIGHT_RED + "Err(E)" + ReplUI.Colors.RESET + " - Failure with error\n\n" +
                   "Example:\n" +
                   "  let result: Result<Int, String> = Ok(42);\n" +
                   "  let error: Result<Int, String> = Err(\"failed\");\n";
                   
            case "List" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "List<T>" + ReplUI.Colors.RESET + " - Dynamic array\n" +
                   "══════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::collections::List" + ReplUI.Colors.RESET + "\n\n" +
                   "Methods:\n" +
                   "  add(T)         - Add element\n" +
                   "  get(Int)       - Get element at index\n" +
                   "  size()         - Get size\n" +
                   "  isEmpty()      - Check if empty\n\n" +
                   "Example:\n" +
                   "  let list = List.of(1, 2, 3);\n" +
                   "  list.add(4);\n";
                   
            case "Map" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "Map<K, V>" + ReplUI.Colors.RESET + " - Key-value dictionary\n" +
                   "════════════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::collections::Map" + ReplUI.Colors.RESET + "\n\n" +
                   "Methods:\n" +
                   "  put(K, V)      - Insert key-value\n" +
                   "  get(K)         - Get value by key\n" +
                   "  containsKey(K) - Check key exists\n" +
                   "  size()         - Get size\n\n" +
                   "Example:\n" +
                   "  let map = Map.of(\"key\", \"value\");\n" +
                   "  map.put(\"foo\", \"bar\");\n";
                   
            case "println" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "println(String) -> Void" + ReplUI.Colors.RESET + "\n" +
                   "═══════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::io::println" + ReplUI.Colors.RESET + "\n\n" +
                   "Print a line to stdout with newline.\n\n" +
                   "Example:\n" +
                   "  println(\"Hello, World!\");\n" +
                   "  println(\"Value: \" + x);\n";
                   
            case "print" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "print(String) -> Void" + ReplUI.Colors.RESET + "\n" +
                   "════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::io::print" + ReplUI.Colors.RESET + "\n\n" +
                   "Print to stdout without newline.\n\n" +
                   "Example:\n" +
                   "  print(\"Loading\");\n" +
                   "  print(\"...\");\n";
                   
            case "readLine" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "readLine() -> Result<String, String>" + ReplUI.Colors.RESET + "\n" +
                   "════════════════════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::io::readLine" + ReplUI.Colors.RESET + "\n\n" +
                   "Read a line from stdin.\n\n" +
                   "Example:\n" +
                   "  match readLine() {\n" +
                   "    Ok(line) => println(line),\n" +
                   "    Err(e) => eprintln(e)\n" +
                   "  }\n";
                   
            case "readFile" -> "\n" + ReplUI.Colors.BRIGHT_CYAN + "readFile(String) -> Result<String, String>" + ReplUI.Colors.RESET + "\n" +
                   "══════════════════════════════════════════════════════\n" +
                   ReplUI.Colors.DIM + "use firefly::std::io::readFile" + ReplUI.Colors.RESET + "\n\n" +
                   "Read entire file contents as string.\n\n" +
                   "Example:\n" +
                   "  match readFile(\"config.txt\") {\n" +
                   "    Ok(content) => println(content),\n" +
                   "    Err(e) => eprintln(\"Error: \" + e)\n" +
                   "  }\n";
                   
            default -> null;
        };
    }
    
    /**
     * Time expression execution (IPython-style).
     */
    private void timeExpression(String expression, int iterations) {
        if (iterations == 1) {
            // Single execution with timing
            long start = System.nanoTime();
            ReplEngine.EvalResult result = engine.eval(expression);
            long end = System.nanoTime();
            double ms = (end - start) / 1_000_000.0;
            
            if (result.isSuccess()) {
                if (result.getValue() != null) {
                    ui.println(PrettyPrinter.prettyPrint(result.getValue()));
                }
                ui.printInfo(String.format("Execution time: %.3f ms", ms));
            } else {
                ui.printError(result.getError());
            }
        } else {
            // Multiple iterations for accurate timing
            ui.printInfo("Warming up...");
            // Warmup (2 iterations)
            for (int i = 0; i < 2; i++) {
                engine.eval(expression);
            }
            
            ui.printInfo("Benchmarking " + iterations + " iterations...");
            long[] times = new long[iterations];
            boolean success = true;
            
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                ReplEngine.EvalResult result = engine.eval(expression);
                long end = System.nanoTime();
                times[i] = end - start;
                
                if (!result.isSuccess()) {
                    ui.printError(result.getError());
                    success = false;
                    break;
                }
            }
            
            if (success) {
                // Calculate statistics
                java.util.Arrays.sort(times);
                double min = times[0] / 1_000_000.0;
                double max = times[iterations - 1] / 1_000_000.0;
                double avg = java.util.Arrays.stream(times).average().orElse(0) / 1_000_000.0;
                double median = times[iterations / 2] / 1_000_000.0;
                
                ui.println("");
                ui.println(ReplUI.Colors.BRIGHT_CYAN + "Benchmark Results:" + ReplUI.Colors.RESET);
                ui.println(String.format("  Min:    %.3f ms", min));
                ui.println(String.format("  Median: %.3f ms", median));
                ui.println(String.format("  Avg:    %.3f ms", avg));
                ui.println(String.format("  Max:    %.3f ms", max));
                ui.println(String.format("  Total:  %.3f ms (" + iterations + " runs)", avg * iterations));
                ui.println("");
            }
        }
    }
    
    /**
     * Execute a shell command (IPython-style ! escape).
     */
    private void executeShellCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            
            // Detect OS and set shell
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("/bin/sh", "-c", command);
            }
            
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                ui.printWarning("Command exited with code: " + exitCode);
            }
        } catch (IOException e) {
            ui.printError("Failed to execute command: " + e.getMessage());
        } catch (InterruptedException e) {
            ui.printError("Command interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Profile expression execution with detailed metrics.
     */
    private void profileExpression(String expression) {
        ui.printHeader("Profiling Expression");
        ui.println(ReplUI.Colors.DIM + expression + ReplUI.Colors.RESET);
        ui.println("");
        
        // Warm up
        engine.eval(expression);
        
        // Profile with multiple runs
        int runs = 100;
        long[] times = new long[runs];
        long[] cpuTimes = new long[runs];
        
        java.lang.management.ThreadMXBean threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().getId();
        
        for (int i = 0; i < runs; i++) {
            long startCpu = threadMXBean.getThreadCpuTime(threadId);
            long startTime = System.nanoTime();
            
            engine.eval(expression);
            
            long endTime = System.nanoTime();
            long endCpu = threadMXBean.getThreadCpuTime(threadId);
            
            times[i] = endTime - startTime;
            cpuTimes[i] = endCpu - startCpu;
        }
        
        // Calculate statistics
        java.util.Arrays.sort(times);
        java.util.Arrays.sort(cpuTimes);
        
        double wallMin = times[0] / 1_000_000.0;
        double wallMedian = times[runs / 2] / 1_000_000.0;
        double wallAvg = java.util.Arrays.stream(times).average().orElse(0) / 1_000_000.0;
        double wallMax = times[runs - 1] / 1_000_000.0;
        
        double cpuMin = cpuTimes[0] / 1_000_000.0;
        double cpuMedian = cpuTimes[runs / 2] / 1_000_000.0;
        double cpuAvg = java.util.Arrays.stream(cpuTimes).average().orElse(0) / 1_000_000.0;
        double cpuMax = cpuTimes[runs - 1] / 1_000_000.0;
        
        // Display results
        ui.println(ReplUI.Colors.BRIGHT_CYAN + "Wall Time:" + ReplUI.Colors.RESET);
        ui.println(String.format("  Min:    %.3f ms", wallMin));
        ui.println(String.format("  Median: %.3f ms", wallMedian));
        ui.println(String.format("  Avg:    %.3f ms ± %.3f", wallAvg, (wallMax - wallMin) / 2));
        ui.println(String.format("  Max:    %.3f ms", wallMax));
        ui.println("");
        
        ui.println(ReplUI.Colors.BRIGHT_CYAN + "CPU Time:" + ReplUI.Colors.RESET);
        ui.println(String.format("  Min:    %.3f ms", cpuMin));
        ui.println(String.format("  Median: %.3f ms", cpuMedian));
        ui.println(String.format("  Avg:    %.3f ms ± %.3f", cpuAvg, (cpuMax - cpuMin) / 2));
        ui.println(String.format("  Max:    %.3f ms", cpuMax));
        ui.println("");
        
        ui.println(ReplUI.Colors.BRIGHT_YELLOW + "Throughput:" + ReplUI.Colors.RESET);
        double throughput = 1000.0 / wallAvg; // ops per second
        ui.println(String.format("  %.2f ops/sec", throughput));
        ui.println("");
    }
    
    /**
     * Profile memory usage.
     */
    private void memoryProfile(String expression) {
        ui.printHeader("Memory Profiling");
        ui.println(ReplUI.Colors.DIM + expression + ReplUI.Colors.RESET);
        ui.println("");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC before measurement
        System.gc();
        Thread.yield();
        try { Thread.sleep(100); } catch (InterruptedException e) { }
        
        long beforeUsed = runtime.totalMemory() - runtime.freeMemory();
        
        // Execute expression
        ReplEngine.EvalResult result = engine.eval(expression);
        
        // Force GC to see retained memory
        System.gc();
        Thread.yield();
        try { Thread.sleep(100); } catch (InterruptedException e) { }
        
        long afterUsed = runtime.totalMemory() - runtime.freeMemory();
        long memDelta = afterUsed - beforeUsed;
        
        if (result.isSuccess()) {
            ui.println(ReplUI.Colors.BRIGHT_CYAN + "Memory Usage:" + ReplUI.Colors.RESET);
            ui.println(String.format("  Before:     %,d bytes (%.2f MB)", beforeUsed, beforeUsed / 1024.0 / 1024.0));
            ui.println(String.format("  After:      %,d bytes (%.2f MB)", afterUsed, afterUsed / 1024.0 / 1024.0));
            ui.println(String.format("  Delta:      %,d bytes (%.2f MB)", memDelta, memDelta / 1024.0 / 1024.0));
            ui.println("");
            
            ui.println(ReplUI.Colors.BRIGHT_CYAN + "Heap Status:" + ReplUI.Colors.RESET);
            ui.println(String.format("  Total:      %,d bytes (%.2f MB)", runtime.totalMemory(), runtime.totalMemory() / 1024.0 / 1024.0));
            ui.println(String.format("  Free:       %,d bytes (%.2f MB)", runtime.freeMemory(), runtime.freeMemory() / 1024.0 / 1024.0));
            ui.println(String.format("  Max:        %,d bytes (%.2f MB)", runtime.maxMemory(), runtime.maxMemory() / 1024.0 / 1024.0));
            ui.println("");
            
            if (memDelta > 0) {
                ui.printWarning(String.format("Expression allocated %.2f MB", memDelta / 1024.0 / 1024.0));
            } else {
                ui.printSuccess("No significant memory increase detected");
            }
        } else {
            ui.printError(result.getError());
        }
    }
}

