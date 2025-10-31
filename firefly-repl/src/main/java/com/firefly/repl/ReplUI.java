package com.firefly.repl;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final java.io.BufferedReader directReader;
    private final boolean isDumbTerminal;
    private ContextPanel contextPanel;
    private ReplEngine engine;

    /**
     * Creates a new REPL UI.
     */
    public ReplUI() throws IOException {
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        // Detect if we're in dumb terminal mode (piped input)
        this.isDumbTerminal = terminal.getType().equals("dumb");

        if (isDumbTerminal) {
            // For piped input, use direct BufferedReader to read line-by-line
            this.directReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            this.reader = null;
        } else {
            // Configure line reader with history, completion, and highlighting for interactive mode
            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(new ReplParser())
                    .highlighter(new ReplHighlighter())
                    .completer(new ReplCompleter())
                    .history(new DefaultHistory())
                    .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".firefly_repl_history"))
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    // Enable bracketed paste mode for safe multi-line paste
                    .option(LineReader.Option.BRACKETED_PASTE, true)
                    // Enable history search with Ctrl+R
                    .option(LineReader.Option.HISTORY_INCREMENTAL, true)
                    .build();
            // Bind Ctrl+R to reverse-i-search
            reader.getKeyMaps().get("main").bind(new org.jline.reader.Reference("history-incremental-search-backward"), "\u0012"); // Ctrl+R
            this.directReader = null;
        }
    }

    /**
     * Custom parser for multi-line input support with smart indentation.
     * In dumb terminal mode (piped input), accept each line as-is without checking balance.
     */
    private static class ReplParser extends DefaultParser {
        @Override
        public ParsedLine parse(String line, int cursor, ParseContext context) {
            // Only enable multi-line parsing in ACCEPT_LINE context
            // This prevents accumulation of input when reading from pipes/files
            if (context == ParseContext.ACCEPT_LINE && !line.contains("\n")) {
                int openBraces = countChar(line, '{') - countChar(line, '}');
                int openParens = countChar(line, '(') - countChar(line, ')');
                int openBrackets = countChar(line, '[') - countChar(line, ']');

                // Only request continuation if significantly unbalanced and no newline present
                if ((openBraces > 0 || openParens > 0 || openBrackets > 0)) {
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
     * Simple syntax highlighter using regex-based tokenization.
     */
    private static class ReplHighlighter implements Highlighter {
        private static final List<String> KEYWORDS = Arrays.asList(
                "fn","class","struct","data","trait","impl","actor","use","let","mut","pub",
                "async","await","match","if","else","for","while","loop","return","break","continue",
                "true","false","null"
        );
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "\"(\\\\.|[^\\\"])*\"|'(\\\\.|[^\\'])*'|//.*$|[A-Za-z_][A-Za-z0-9_]*|[0-9]+(\\.[0-9]+)?|\\S",
                Pattern.MULTILINE
        );
        @Override
        public AttributedString highlight(LineReader reader, String buffer) {
            AttributedStringBuilder asb = new AttributedStringBuilder(buffer.length() + 16);
            Matcher m = TOKEN_PATTERN.matcher(buffer);
            int idx = 0;
            while (m.find()) {
                if (m.start() > idx) {
                    asb.append(buffer, idx, m.start());
                }
                String tok = m.group();
                AttributedStyle style = AttributedStyle.DEFAULT;
                if (tok.startsWith("//") || tok.startsWith("/*")) {
                    style = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).italic();
                } else if (tok.startsWith("\"") || tok.startsWith("'")) {
                    style = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
                } else if (KEYWORDS.contains(tok)) {
                    style = AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA).bold();
                } else if (tok.matches("[0-9].*")) {
                    style = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
                } else if ("::".equals(tok) || "->".equals(tok)) {
                    style = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT);
                }
                asb.append(tok, style);
                idx = m.end();
            }
            if (idx < buffer.length()) asb.append(buffer.substring(idx));
            return asb.toAttributedString();
        }

        @Override
        public void setErrorPattern(Pattern errorPattern) { }

        @Override
        public void setErrorIndex(int errorIndex) { }
    }

    /**
     * Best-in-class context-aware completer with smart suggestions.
     */
    private class ReplCompleter implements Completer {
        private final List<String> COMMANDS = Arrays.asList(
                ":help",":h",":?",
                ":quit",":exit",":q",
                ":reset",
                ":imports",
                ":definitions",":defs",
                ":clear",":cls",
                ":load",
                ":type",
                ":context",":ctx",
                ":save",
                ":edit",
                ":history"
        );
        
        // Common stdlib modules and their popular functions
        private final java.util.Map<String, String[]> STDLIB_FUNCTIONS = new java.util.HashMap<String, String[]>() {{
            put("io", new String[]{"println", "print", "eprint", "eprintln", "readLine", "readInt", "readFile", "writeFile"});
            put("collections", new String[]{"List", "Map", "Set", "Vector", "HashMap", "HashSet"});
            put("option", new String[]{"Option", "Some", "None"});
            put("result", new String[]{"Result", "Ok", "Err"});
            put("string", new String[]{"split", "trim", "toLowerCase", "toUpperCase", "contains", "startsWith", "endsWith"});
            put("math", new String[]{"abs", "sqrt", "pow", "sin", "cos", "tan", "min", "max", "floor", "ceil"});
            put("time", new String[]{"now", "sleep", "Duration", "Instant"});
            put("json", new String[]{"parse", "stringify", "toJson", "fromJson"});
            put("http", new String[]{"get", "post", "put", "delete", "Client", "Request", "Response"});
            put("fs", new String[]{"readFile", "writeFile", "exists", "delete", "copy", "move", "listDir"});
        }};
        
        // Common types that suggest imports
        private final java.util.Map<String, String> TYPE_TO_IMPORT = new java.util.HashMap<String, String>() {{
            put("Option", "firefly::std::option::{Option, Some, None}");
            put("Some", "firefly::std::option::{Option, Some, None}");
            put("None", "firefly::std::option::{Option, Some, None}");
            put("Result", "firefly::std::result::{Result, Ok, Err}");
            put("Ok", "firefly::std::result::{Result, Ok, Err}");
            put("Err", "firefly::std::result::{Result, Ok, Err}");
            put("List", "firefly::std::collections::List");
            put("Map", "firefly::std::collections::Map");
            put("Set", "firefly::std::collections::Set");
            put("Vector", "firefly::std::collections::Vector");
            put("println", "firefly::std::io::println");
            put("readLine", "firefly::std::io::readLine");
        }};
        
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            String lowerWord = word.toLowerCase(Locale.ROOT);
            String fullLine = line.line();
            int cursor = line.cursor();
            
            // REPL Commands
            if (fullLine.trim().startsWith(":")) {
                for (String c : COMMANDS) {
                    if (c.startsWith(lowerWord)) {
                        String desc = getCommandDescription(c);
                        candidates.add(new Candidate(c, c, null, desc, null, null, true));
                    }
                }
                return;
            }
            
            // Detect context: after "use " suggest imports
            if (fullLine.matches(".*\\buse\\s+[a-zA-Z:]*$")) {
                addStdlibImportSuggestions(word, candidates);
                return;
            }
            
            // Detect method/field access: suggest members after "."
            if (cursor > 0 && cursor <= fullLine.length() && 
                fullLine.substring(0, cursor).matches(".*\\w+\\.\\w*$")) {
                addMemberSuggestions(fullLine, word, candidates);
                return;
            }
            
            // Language Keywords with descriptions
            for (String kw : ReplHighlighter.KEYWORDS) {
                if (kw.startsWith(lowerWord)) {
                    String desc = getKeywordDescription(kw);
                    candidates.add(new Candidate(kw, kw, null, desc, null, null, true));
                }
            }
            
            // User-defined functions with signatures
            if (engine != null) {
                for (ReplEngine.FunctionInfo f : engine.getFunctions()) {
                    if (f.getName().toLowerCase(Locale.ROOT).startsWith(lowerWord)) {
                        String display = f.getName() + "(";
                        String desc = f.getSignature() + " -> " + f.getReturnType();
                        candidates.add(new Candidate(display, display, null, desc, null, null, true));
                    }
                }
                
                // User-defined classes
                for (ReplEngine.ClassInfo c : engine.getClasses()) {
                    if (c.getName().toLowerCase(Locale.ROOT).startsWith(lowerWord)) {
                        candidates.add(new Candidate(c.getName(), c.getName(), null, "class", null, null, true));
                    }
                }
                
                // User-defined variables
                for (String v : engine.getVariables().keySet()) {
                    if (v.toLowerCase(Locale.ROOT).startsWith(lowerWord)) {
                        candidates.add(new Candidate(v, v, null, "variable", null, null, true));
                    }
                }
            }
            
            // Stdlib function suggestions
            addStdlibFunctionSuggestions(lowerWord, candidates);
            
            // Smart type suggestions with import hints
            addTypeSuggestions(word, candidates);
        }
        
        private void addStdlibImportSuggestions(String word, List<Candidate> candidates) {
            String[] modules = {"io", "collections", "option", "result", "string", "math", "time", "json", "http", "fs"};
            for (String mod : modules) {
                String importPath = "firefly::std::" + mod;
                if (importPath.contains(word)) {
                    candidates.add(new Candidate(importPath, importPath, null, "stdlib module", null, null, true));
                }
            }
        }
        
        private void addMemberSuggestions(String line, String word, List<Candidate> candidates) {
            // Extract variable/object before the dot
            String beforeDot = line.substring(0, line.lastIndexOf('.')).trim();
            String[] parts = beforeDot.split("[^a-zA-Z0-9_]");
            if (parts.length == 0) return;
            String varName = parts[parts.length - 1];
            
            // Try runtime introspection if variable exists
            if (engine != null && engine.getVariables().containsKey(varName)) {
                Object obj = engine.getVariables().get(varName);
                if (obj != null) {
                    addRuntimeMembers(obj, word, candidates);
                    return;
                }
            }
            
            // Fallback: common String methods
            String[] stringMethods = {"length", "isEmpty", "toUpperCase", "toLowerCase", "trim", 
                                      "split", "contains", "startsWith", "endsWith", "substring",
                                      "charAt", "indexOf", "lastIndexOf", "replace", "replaceAll"};
            for (String method : stringMethods) {
                if (method.startsWith(word)) {
                    candidates.add(new Candidate(method, method, null, "String method", null, null, true));
                }
            }
        }
        
        private void addRuntimeMembers(Object obj, String word, List<Candidate> candidates) {
            Class<?> clazz = obj.getClass();
            
            // Get all public methods
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                String name = m.getName();
                if (name.startsWith(word) && !name.startsWith("_")) {
                    String params = java.util.Arrays.stream(m.getParameterTypes())
                        .map(Class::getSimpleName)
                        .collect(java.util.stream.Collectors.joining(", "));
                    String returnType = m.getReturnType().getSimpleName();
                    String desc = name + "(" + params + ") -> " + returnType;
                    candidates.add(new Candidate(name, name, null, desc, null, null, true));
                }
            }
            
            // Get all public fields
            for (java.lang.reflect.Field f : clazz.getFields()) {
                String name = f.getName();
                if (name.startsWith(word) && !name.startsWith("_")) {
                    String type = f.getType().getSimpleName();
                    candidates.add(new Candidate(name, name, null, type + " field", null, null, true));
                }
            }
        }
        
        private void addStdlibFunctionSuggestions(String word, List<Candidate> candidates) {
            for (java.util.Map.Entry<String, String[]> entry : STDLIB_FUNCTIONS.entrySet()) {
                String module = entry.getKey();
                for (String func : entry.getValue()) {
                    if (func.toLowerCase().startsWith(word)) {
                        String desc = "firefly::std::" + module + "::" + func;
                        candidates.add(new Candidate(func, func, null, desc, null, null, false));
                    }
                }
            }
        }
        
        private void addTypeSuggestions(String word, List<Candidate> candidates) {
            for (java.util.Map.Entry<String, String> entry : TYPE_TO_IMPORT.entrySet()) {
                String type = entry.getKey();
                if (type.toLowerCase().startsWith(word.toLowerCase())) {
                    String importHint = "💡 use " + entry.getValue();
                    candidates.add(new Candidate(type, type, null, importHint, null, null, false));
                }
            }
        }
        
        private String getCommandDescription(String cmd) {
            switch(cmd) {
                case ":help": case ":h": case ":?": return "Show help and available commands";
                case ":quit": case ":exit": case ":q": return "Exit the REPL";
                case ":reset": return "Reset REPL state (clear all definitions)";
                case ":imports": return "List current imports";
                case ":definitions": case ":defs": return "List defined functions and classes";
                case ":clear": case ":cls": return "Clear the screen";
                case ":load": return "Load and execute a .fly file";
                case ":type": return "Show inferred type of expression";
                case ":context": case ":ctx": return "Show full REPL context";
                case ":save": return "Save session history to file";
                case ":edit": return "Open external editor";
                case ":history": return "Show command history";
                default: return "";
            }
        }
        
        private String getKeywordDescription(String kw) {
            switch(kw) {
                case "fn": return "Define a function";
                case "class": return "Define a class";
                case "struct": return "Define a struct (product type)";
                case "data": return "Define algebraic data type (sum type)";
                case "trait": return "Define a trait (interface)";
                case "impl": return "Implement trait for type";
                case "use": return "Import module or symbol";
                case "let": return "Declare immutable variable";
                case "mut": return "Make variable mutable";
                case "pub": return "Make symbol public";
                case "if": return "Conditional expression";
                case "else": return "Alternative branch";
                case "match": return "Pattern matching";
                case "for": return "For loop";
                case "while": return "While loop";
                case "return": return "Return from function";
                case "async": return "Async function";
                case "await": return "Await async result";
                default: return "keyword";
            }
        }
    }

    /**
     * Gets the terminal instance.
     */
    public Terminal getTerminal() { return terminal; }

    /** Attach engine for context-aware UI (prompt, completion). */
    public void setEngine(ReplEngine engine) { this.engine = engine; }

    /**
     * Sets the context panel for displaying REPL state.
     */
    public void setContextPanel(ContextPanel panel) { this.contextPanel = panel; }

    /**
     * Displays the full context panel.
     */
    public void showContext() { if (contextPanel != null) contextPanel.display(); }

    /**
     * Build a dynamic prompt with small context.
     */
    private String buildPrompt() {
        String base = Colors.BRIGHT_MAGENTA + "fly" + Colors.RESET;
        if (engine == null) return base + Colors.BRIGHT_MAGENTA + "> " + Colors.RESET;
        int im = engine.getImports().size();
        int fn = engine.getFunctions().size();
        int cl = engine.getClasses().size();
        int vr = engine.getVariables().size();
        String ctx = String.format("[%d imp | %d fn | %d cls | %d var]", im, fn, cl, vr);
        return base + Colors.DIM + " " + ctx + Colors.RESET + Colors.BRIGHT_MAGENTA + "> " + Colors.RESET;
    }

    /**
     * Reads a line of input from the user.
     * @return The input line, or null if EOF
     */
    public String readLine() {
        if (isDumbTerminal) {
            try {
                // In dumb mode, print prompt manually and read directly
                terminal.writer().print("flylang> ");
                terminal.writer().flush();
                return directReader.readLine();
            } catch (IOException e) {
                return null;
            }
        } else {
            try {
                String prompt = buildPrompt();
                return reader.readLine(prompt);
            } catch (UserInterruptException | EndOfFileException e) {
                return null;
            }
        }
    }

    /**
     * Reads a multi-line input (for continuation).
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
    /** Save session history to a file. */
    public void saveHistory(String file) {
        if (isDumbTerminal || reader == null) {
            printWarning("History not available in non-interactive mode");
            return;
        }
        try {
            List<String> lines = new ArrayList<>();
            for (History.Entry e : reader.getHistory()) {
                lines.add(e.line());
            }
            Files.write(Path.of(file), lines, StandardCharsets.UTF_8);
            printSuccess("History saved to " + file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
    /** Show last N history entries. */
    public void showHistory(int n) {
        if (isDumbTerminal || reader == null) {
            printWarning("History not available in non-interactive mode");
            return;
        }
        List<String> lines = new ArrayList<>();
        for (History.Entry e : reader.getHistory()) lines.add(e.line());
        int start = Math.max(0, lines.size() - n);
        printHeader("History (last " + (lines.size() - start) + ")");
        for (int i = start; i < lines.size(); i++) {
            println(Colors.DIM + String.format("%4d ", i + 1) + Colors.RESET + lines.get(i));
        }
        println();
    }

    /** Open external editor and return edited content. */
    public String openEditor(String initialContents) {
        try {
            Path tmp = Files.createTempFile("firefly-repl-", ".fly");
            if (initialContents != null && !initialContents.isEmpty()) {
                Files.writeString(tmp, initialContents);
            }
            String editor = System.getenv().getOrDefault("EDITOR", "vi");
            ProcessBuilder pb = new ProcessBuilder(editor, tmp.toString());
            pb.inheritIO();
            Process p = pb.start();
            int ec = p.waitFor();
            if (ec != 0) {
                printWarning("Editor exited with code " + ec);
            }
            return Files.readString(tmp);
        } catch (Exception e) {
            printError("Failed to open editor: " + e.getMessage());
            return "";
        }
    }

    /**
     * Prints a success message with the result value.
     */
    public void printResult(Object value, String type) {
        if (value != null) {
            terminal.writer().println(Colors.DIM + "  => " + Colors.RESET +
                    Colors.BRIGHT_GREEN + formatValue(value) + Colors.RESET +
                    (type != null ? Colors.DIM + " : " + type + Colors.RESET : ""));
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
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value + "\"";
        return value.toString();
    }

    /** Print an error message. */
    public void printError(String message) {
        terminal.writer().println(Colors.BRIGHT_RED + "  ✗ Error: " + Colors.RESET + message);
        terminal.flush();
    }

    /** Detailed error with type, location, and suggestion. */
    public void printDetailedError(String errorType, String message, Integer line, Integer column, String suggestion) {
        terminal.writer().println();
        terminal.writer().println(Colors.BRIGHT_RED + "  ╭─ " + Colors.BOLD + errorType + " Error" + Colors.RESET);
        if (line != null && column != null && line > 0) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET +
                    Colors.DIM + "  at line " + line + ", column " + column + Colors.RESET);
        }
        terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_RED + "  │  " + Colors.RESET + Colors.BRIGHT_WHITE + message + Colors.RESET);
        if (suggestion != null && !suggestion.isEmpty()) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
            terminal.writer().println(Colors.BRIGHT_YELLOW + "  │  💡 " + Colors.RESET + Colors.YELLOW + suggestion + Colors.RESET);
        }
        terminal.writer().println(Colors.BRIGHT_RED + "  ╰─" + Colors.RESET);
        terminal.writer().println();
        terminal.flush();
    }

    /** Error with code snippet highlighting. */
    public void printErrorWithCode(String errorType, String message, String code,
                                   Integer line, Integer column, String suggestion) {
        terminal.writer().println();
        terminal.writer().println(Colors.BRIGHT_RED + "  ╭─ " + Colors.BOLD + errorType + " Error" + Colors.RESET);
        if (line != null && column != null && line > 0) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET +
                    Colors.DIM + "  at line " + line + ", column " + column + Colors.RESET);
        }
        terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
        terminal.writer().println(Colors.BRIGHT_RED + "  │  " + Colors.RESET + Colors.BRIGHT_WHITE + message + Colors.RESET);
        if (code != null && !code.isEmpty() && column != null && column >= 0) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
            terminal.writer().println(Colors.BRIGHT_RED + "  │  " + Colors.RESET + Colors.DIM + code + Colors.RESET);
            StringBuilder pointer = new StringBuilder();
            pointer.append(Colors.BRIGHT_RED).append("  │  ");
            for (int i = 0; i < column; i++) pointer.append(" ");
            pointer.append("^").append(Colors.RESET);
            terminal.writer().println(pointer.toString());
        }
        if (suggestion != null && !suggestion.isEmpty()) {
            terminal.writer().println(Colors.BRIGHT_RED + "  │" + Colors.RESET);
            terminal.writer().println(Colors.BRIGHT_YELLOW + "  │  💡 " + Colors.RESET + Colors.YELLOW + suggestion + Colors.RESET);
        }
        terminal.writer().println(Colors.BRIGHT_RED + "  ╰─" + Colors.RESET);
        terminal.writer().println();
        terminal.flush();
    }

    /** Info message. */
    public void printInfo(String message) {
        terminal.writer().println(Colors.BRIGHT_CYAN + "  ℹ " + Colors.RESET + message);
        terminal.flush();
    }

    /** Success message. */
    public void printSuccess(String message) {
        terminal.writer().println(Colors.BRIGHT_GREEN + "  ✓ " + Colors.RESET + message);
        terminal.flush();
    }

    /** Warning message. */
    public void printWarning(String message) {
        terminal.writer().println(Colors.BRIGHT_YELLOW + "  ⚠ " + Colors.RESET + message);
        terminal.flush();
    }

    /** Section header. */
    public void printHeader(String title) {
        terminal.writer().println();
        terminal.writer().println(Colors.BOLD + Colors.BRIGHT_CYAN + "  " + title + Colors.RESET);
        terminal.writer().println(Colors.DIM + "  " + "─".repeat(title.length()) + Colors.RESET);
        terminal.flush();
    }

    /** Print a line. */
    public void println(String message) {
        terminal.writer().println("  " + message);
        terminal.flush();
    }
    
    /** Print with automatic paging for long output. */
    public void printWithPaging(String content) {
        String[] lines = content.split("\n");
        int termHeight = terminal.getHeight();
        
        // If output fits on screen, print directly
        if (lines.length < termHeight - 5) {
            for (String line : lines) {
                println(line);
            }
            return;
        }
        
        // Use JLine's built-in pager
        try {
            terminal.writer().println();
            terminal.puts(org.jline.utils.InfoCmp.Capability.enter_ca_mode);
            
            int currentLine = 0;
            boolean quit = false;
            
            while (currentLine < lines.length && !quit) {
                terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                int displayLines = Math.min(termHeight - 2, lines.length - currentLine);
                
                for (int i = 0; i < displayLines; i++) {
                    terminal.writer().println(lines[currentLine + i]);
                }
                
                // Show prompt
                int remaining = lines.length - currentLine - displayLines;
                String prompt = remaining > 0 
                    ? Colors.BRIGHT_CYAN + "-- More -- (" + remaining + " lines remaining, q to quit, Space/Enter to continue)" + Colors.RESET
                    : Colors.BRIGHT_GREEN + "-- End -- (q to quit)" + Colors.RESET;
                terminal.writer().print(prompt);
                terminal.flush();
                
                // Read key
                int key = terminal.reader().read();
                if (key == 'q' || key == 'Q') {
                    quit = true;
                } else if (key == ' ') {
                    currentLine += displayLines;
                } else {
                    currentLine += 1;
                }
            }
            
            terminal.puts(org.jline.utils.InfoCmp.Capability.exit_ca_mode);
            terminal.flush();
        } catch (Exception e) {
            // Fallback to regular printing
            for (String line : lines) {
                println(line);
            }
        }
    }

    /** Print a blank line. */
    public void println() {
        terminal.writer().println();
        terminal.flush();
    }

    /** Help message. */
    public void printHelp() {
        printHeader("REPL Commands");
        println(Colors.BRIGHT_CYAN + ":help" + Colors.RESET + "              Show this help message");
        println(Colors.BRIGHT_CYAN + ":quit" + Colors.RESET + " or " + Colors.BRIGHT_CYAN + ":exit" + Colors.RESET + "    Exit the REPL");
        println(Colors.BRIGHT_CYAN + ":reset" + Colors.RESET + "             Reset the REPL state");
        println(Colors.BRIGHT_CYAN + ":context" + Colors.RESET + "           Show current context (imports, functions, classes)");
        println(Colors.BRIGHT_CYAN + ":imports" + Colors.RESET + "           Show current imports");
        println(Colors.BRIGHT_CYAN + ":definitions" + Colors.RESET + "       Show defined functions and types");
        println(Colors.BRIGHT_CYAN + ":clear" + Colors.RESET + "             Clear the screen");
        println(Colors.BRIGHT_CYAN + ":load <file>" + Colors.RESET + "        Load and execute a file (multi-line aware)");
        println(Colors.BRIGHT_CYAN + ":save <file>" + Colors.RESET + "        Save session history to file");
        println(Colors.BRIGHT_CYAN + ":history [n]" + Colors.RESET + "       Show last n commands (default 20)");
        println(Colors.BRIGHT_CYAN + ":edit" + Colors.RESET + "               Open external editor, then execute buffer");
        println(Colors.BRIGHT_CYAN + ":type <expr>" + Colors.RESET + "        Show inferred type of expression");
        println(Colors.BRIGHT_CYAN + ":doc <symbol>" + Colors.RESET + "       Show documentation for stdlib symbol");
        println();
        printHeader("Magic Commands (IPython-style)");
        println(Colors.BRIGHT_MAGENTA + ":time <expr>" + Colors.RESET + "        Time single execution of expression");
        println(Colors.BRIGHT_MAGENTA + ":timeit <expr>" + Colors.RESET + "      Benchmark expression (10 iterations)");
        println(Colors.BRIGHT_MAGENTA + ":profile <expr>" + Colors.RESET + "     Full profiling with CPU time & throughput");
        println(Colors.BRIGHT_MAGENTA + ":memprofile <expr>" + Colors.RESET + "  Memory profiling with heap stats");
        println(Colors.BRIGHT_MAGENTA + "!<command>" + Colors.RESET + "           Execute shell command");
        println();
        printHeader("Keyboard Shortcuts");
        println(Colors.DIM + "Ctrl+R" + Colors.RESET + "             Reverse history search");
        println(Colors.DIM + "Ctrl+C" + Colors.RESET + "             Interrupt current input");
        println(Colors.DIM + "Ctrl+D" + Colors.RESET + "             Exit REPL (when line is empty)");
        println(Colors.DIM + "Tab" + Colors.RESET + "                Auto-complete");
        println(Colors.DIM + "↑/↓" + Colors.RESET + "                Navigate history");
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

    /** Clear screen. */
    public void clearScreen() {
        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
        terminal.flush();
    }

    /** Close terminal. */
    public void close() throws IOException { terminal.close(); }

    /** Print the welcome banner. */
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
}


