package com.firefly.repl;

import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import com.firefly.compiler.ast.AstNode;
import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.codegen.BytecodeGenerator;
import com.firefly.compiler.codegen.TypeResolver;
import com.firefly.compiler.semantic.SemanticException;
import com.firefly.compiler.semantics.SemanticAnalyzer;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Core REPL engine for Firefly language.
 * 
 * <p>Handles compilation and execution of Firefly code snippets in an interactive environment.
 * Maintains state between evaluations and provides a seamless REPL experience.</p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Incremental compilation of code snippets</li>
 *   <li>Dynamic class loading and execution</li>
 *   <li>State preservation between evaluations</li>
 *   <li>Expression evaluation with result display</li>
 *   <li>Variable and function definitions</li>
 *   <li>Import management</li>
 * </ul>
 * 
 * @version 1.0-Alpha
 */
public class ReplEngine {
    
    private final TypeResolver typeResolver;
    private final Map<String, Object> variables;
    private final List<String> imports;
    private final List<String> definitions;
    private final List<FunctionInfo> functions;
    private final List<ClassInfo> classes;
    private final ReplClassLoader classLoader;
    private int snippetCounter;
    
    /**
     * Custom class loader for dynamically loading compiled REPL code.
     */
    private static class ReplClassLoader extends ClassLoader {
        private final Map<String, byte[]> classBytes = new HashMap<>();
        
        public ReplClassLoader() {
            super(ReplClassLoader.class.getClassLoader());
        }
        
        public void defineClass(String name, byte[] bytes) {
            classBytes.put(name, bytes);
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
    
    /**
     * Information about a defined function.
     */
    public static class FunctionInfo {
        private final String name;
        private final String signature;
        private final String returnType;

        public FunctionInfo(String name, String signature, String returnType) {
            this.name = name;
            this.signature = signature;
            this.returnType = returnType;
        }

        public String getName() { return name; }
        public String getSignature() { return signature; }
        public String getReturnType() { return returnType; }
    }

    /**
     * Information about a defined class.
     */
    public static class ClassInfo {
        private final String name;

        public ClassInfo(String name) {
            this.name = name;
        }

        public String getName() { return name; }
    }

    /**
     * Result of evaluating a REPL input.
     */
    public static class EvalResult {
        private final boolean success;
        private final Object value;
        private final String type;
        private final String error;
        private final ErrorType errorType;
        private final Integer errorLine;
        private final Integer errorColumn;
        private final String suggestion;

        public enum ErrorType {
            SYNTAX,      // Parse/syntax errors
            SEMANTIC,    // Type errors, undefined variables, etc.
            RUNTIME,     // Runtime exceptions
            COMPILATION  // Bytecode generation errors
        }

        private EvalResult(boolean success, Object value, String type, String error,
                          ErrorType errorType, Integer errorLine, Integer errorColumn, String suggestion) {
            this.success = success;
            this.value = value;
            this.type = type;
            this.error = error;
            this.errorType = errorType;
            this.errorLine = errorLine;
            this.errorColumn = errorColumn;
            this.suggestion = suggestion;
        }

        public static EvalResult success(Object value, String type) {
            return new EvalResult(true, value, type, null, null, null, null, null);
        }

        public static EvalResult error(String error, ErrorType errorType) {
            return new EvalResult(false, null, null, error, errorType, null, null, null);
        }

        public static EvalResult error(String error, ErrorType errorType, int line, int column, String suggestion) {
            return new EvalResult(false, null, null, error, errorType, line, column, suggestion);
        }

        public boolean isSuccess() {
            return success;
        }

        public Object getValue() {
            return value;
        }

        public String getType() {
            return type;
        }

        public String getError() {
            return error;
        }

        public ErrorType getErrorType() {
            return errorType;
        }

        public Integer getErrorLine() {
            return errorLine;
        }

        public Integer getErrorColumn() {
            return errorColumn;
        }

        public String getSuggestion() {
            return suggestion;
        }
    }
    
    /**
     * Creates a new REPL engine.
     */
    public ReplEngine() {
        this.typeResolver = new TypeResolver();
        this.variables = new HashMap<>();
        this.imports = new ArrayList<>();
        this.definitions = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.classes = new ArrayList<>();
        this.classLoader = new ReplClassLoader();
        this.snippetCounter = 0;

        // Add default imports
        addDefaultImports();
    }

    /**
     * Gets the list of imports.
     */
    public List<String> getImports() {
        return new ArrayList<>(imports);
    }

    /**
     * Gets the list of defined functions.
     */
    public List<FunctionInfo> getFunctions() {
        return new ArrayList<>(functions);
    }

    /**
     * Gets the list of defined classes.
     */
    public List<ClassInfo> getClasses() {
        return new ArrayList<>(classes);
    }

    /**
     * Gets the map of variables.
     */
    public Map<String, Object> getVariables() {
        return new HashMap<>(variables);
    }
    
    /**
     * Adds default imports that are always available in the REPL.
     */
    private void addDefaultImports() {
        imports.add("use firefly::std::option::*");
        imports.add("use firefly::std::result::*");
        imports.add("use firefly::std::collections::*");
    }
    
    /**
     * Evaluates a line of Firefly code.
     *
     * @param input The Firefly code to evaluate
     * @return The result of evaluation
     */
    public EvalResult eval(String input) {
        if (input == null || input.trim().isEmpty()) {
            return EvalResult.success(null, "Void");
        }

        // Ignore comments
        String trimmed = input.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            return EvalResult.success(null, "Void");
        }

        try {
            // Evaluate the input (handles definitions, expressions, and statements)
            return evalExpression(input);

        } catch (Exception e) {
            return handleException(e, input);
        }
    }

    /**
     * Handles exceptions and creates detailed error results.
     */
    private EvalResult handleException(Exception e, String input) {
        // Syntax errors from ANTLR
        if (e instanceof RecognitionException) {
            RecognitionException re = (RecognitionException) e;
            Token token = re.getOffendingToken();
            int line = token != null ? token.getLine() : 0;
            int column = token != null ? token.getCharPositionInLine() : 0;

            String message = "Syntax error";
            String suggestion = getSyntaxSuggestion(e.getMessage(), token);

            if (token != null) {
                message = "Unexpected token '" + token.getText() + "'";
            }

            return EvalResult.error(message, EvalResult.ErrorType.SYNTAX, line, column, suggestion);
        }

        // Semantic errors
        if (e instanceof SemanticException) {
            SemanticException se = (SemanticException) e;
            String message = se.getMessage();
            String suggestion = getSemanticSuggestion(message);

            return EvalResult.error(message, EvalResult.ErrorType.SEMANTIC, 0, 0, suggestion);
        }

        // Runtime errors
        if (e instanceof InvocationTargetException) {
            Throwable cause = e.getCause();
            String message = cause != null ? cause.getClass().getSimpleName() : "Runtime error";
            String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : "";

            if (!detail.isEmpty()) {
                message += ": " + detail;
            }

            String suggestion = getRuntimeSuggestion(cause);
            return EvalResult.error(message, EvalResult.ErrorType.RUNTIME, 0, 0, suggestion);
        }

        // Generic compilation errors
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String suggestion = getGenericSuggestion(message, input);

        return EvalResult.error(message, EvalResult.ErrorType.COMPILATION, 0, 0, suggestion);
    }

    /**
     * Provides helpful suggestions for syntax errors.
     */
    private String getSyntaxSuggestion(String errorMsg, Token token) {
        if (errorMsg == null) return null;

        String msg = errorMsg.toLowerCase();

        if (msg.contains("missing '}'")) {
            return "Did you forget to close a code block with '}'?";
        }
        if (msg.contains("missing ')'")) {
            return "Did you forget to close a parenthesis with ')'?";
        }
        if (msg.contains("missing ';'")) {
            return "Try adding a semicolon ';' at the end of the statement";
        }
        if (msg.contains("extraneous input")) {
            return "There seems to be unexpected syntax. Check for typos or missing operators";
        }
        if (token != null && token.getText().equals("=")) {
            return "Use 'let' or 'mut' to declare variables: 'let x = 42'";
        }

        return "Check the Firefly syntax documentation for correct usage";
    }

    /**
     * Provides helpful suggestions for semantic errors.
     */
    private String getSemanticSuggestion(String errorMsg) {
        if (errorMsg == null) return null;

        String msg = errorMsg.toLowerCase();

        if (msg.contains("undefined") || msg.contains("not found")) {
            return "Make sure the variable or function is defined before using it";
        }
        if (msg.contains("type mismatch") || msg.contains("incompatible types")) {
            return "Check that the types match. You may need to cast or convert the value";
        }
        if (msg.contains("cannot assign")) {
            return "Variables declared with 'let' are immutable. Use 'mut' for mutable variables";
        }
        if (msg.contains("duplicate")) {
            return "A variable or function with this name already exists";
        }

        return "Review the type system and variable scoping rules";
    }

    /**
     * Provides helpful suggestions for runtime errors.
     */
    private String getRuntimeSuggestion(Throwable cause) {
        if (cause == null) return null;

        String className = cause.getClass().getSimpleName();

        if (className.contains("NullPointer")) {
            return "A value is null when it shouldn't be. Check for null values before using them";
        }
        if (className.contains("ArrayIndexOutOfBounds") || className.contains("IndexOutOfBounds")) {
            return "Array or list index is out of range. Check your indices";
        }
        if (className.contains("ClassCast")) {
            return "Invalid type conversion. Make sure the types are compatible";
        }
        if (className.contains("ArithmeticException")) {
            return "Arithmetic error (e.g., division by zero)";
        }
        if (className.contains("NumberFormat")) {
            return "Invalid number format. Check the string you're trying to parse";
        }

        return "Check the values and operations in your code";
    }

    /**
     * Provides generic suggestions based on error message and input.
     */
    private String getGenericSuggestion(String errorMsg, String input) {
        if (input.contains("package ")) {
            return "Use 'module' instead of 'package' in Firefly";
        }
        if (input.contains("import ")) {
            return "Use 'use' instead of 'import' in Firefly";
        }
        if (input.contains("function ") || input.contains("def ")) {
            return "Use 'fn' to define functions in Firefly";
        }
        if (input.contains("void")) {
            return "Use 'Void' (capitalized) for the void type in Firefly";
        }

        return null;
    }
    
    /**
     * Checks if the input is a definition (function, class, etc.).
     */
    private boolean isDefinition(String input) {
        String trimmed = input.trim();
        return trimmed.startsWith("fn ") || 
               trimmed.startsWith("class ") || 
               trimmed.startsWith("struct ") ||
               trimmed.startsWith("data ") ||
               trimmed.startsWith("trait ") ||
               trimmed.startsWith("impl ");
    }
    
    /**
     * Evaluates an expression or statement.
     */
    private EvalResult evalExpression(String input) throws Exception {
        snippetCounter++;
        // Use camelCase for function names (Firefly convention)
        String snippetName = "replSnippet" + snippetCounter;

        // Create a new ClassLoader for each evaluation to avoid conflicts
        // This allows us to redefine the Main class each time
        ReplClassLoader evalClassLoader = new ReplClassLoader();

        // Build complete source code
        StringBuilder sourceBuilder = new StringBuilder();
        sourceBuilder.append("module repl\n\n");

        // Add imports
        for (String imp : imports) {
            sourceBuilder.append(imp).append("\n");
        }
        sourceBuilder.append("\n");

        // Add previous definitions
        for (String def : definitions) {
            sourceBuilder.append(def).append("\n\n");
        }

        // Check if input is a function/class definition
        String trimmedInput = input.trim();
        boolean isDefinition = trimmedInput.startsWith("fn ") ||
                               trimmedInput.startsWith("class ") ||
                               trimmedInput.startsWith("use ");

        if (isDefinition) {
            // For definitions, compile them to validate syntax but don't execute
            // Just add them to the definitions list for future use

            // First, validate the definition by trying to compile it
            sourceBuilder.append(trimmedInput).append("\n\n");

            // Add a dummy fly() function to make it a valid compilation unit
            sourceBuilder.append("fn fly() -> Void {\n");
            sourceBuilder.append("    // Placeholder\n");
            sourceBuilder.append("}\n");

            String source = sourceBuilder.toString();

            // Try to compile to validate syntax
            try {
                CharStream charStream = CharStreams.fromString(source);
                FireflyLexer lexer = new FireflyLexer(charStream);
                SyntaxErrorListener errorListener = new SyntaxErrorListener();
                lexer.removeErrorListeners();
                lexer.addErrorListener(errorListener);

                CommonTokenStream tokens = new CommonTokenStream(lexer);
                FireflyParser parser = new FireflyParser(tokens);
                parser.removeErrorListeners();
                parser.addErrorListener(errorListener);

                ParseTree tree = parser.compilationUnit();

                // Check for syntax errors
                if (errorListener.hasErrors()) {
                    SyntaxErrorListener.SyntaxError error = errorListener.getFirstError();
                    String suggestion = getSyntaxSuggestion(error.getMessage(), null);
                    return EvalResult.error(
                        error.getMessage(),
                        EvalResult.ErrorType.SYNTAX,
                        error.getLine(),
                        error.getColumn(),
                        suggestion
                    );
                }

                // Build AST to validate semantics
                AstBuilder astBuilder = new AstBuilder("<repl>");
                AstNode ast = astBuilder.visit(tree);

                if (!(ast instanceof CompilationUnit)) {
                    return EvalResult.error("Failed to build AST", EvalResult.ErrorType.COMPILATION);
                }

                // Semantic analysis
                SemanticAnalyzer analyzer = new SemanticAnalyzer(typeResolver);
                analyzer.analyze((CompilationUnit) ast);

            } catch (Exception e) {
                return handleException(e, trimmedInput);
            }

            // If validation passed, add to definitions
            if (!definitions.contains(trimmedInput)) {
                definitions.add(trimmedInput);
            }

            // Extract the name of what was defined for the confirmation message
            String definitionType = "Definition";
            String definitionName = "";

            if (trimmedInput.startsWith("fn ")) {
                definitionType = "Function";
                // Extract function name: "fn name(...)" -> "name"
                int nameStart = 3; // after "fn "
                int nameEnd = trimmedInput.indexOf('(', nameStart);
                if (nameEnd > nameStart) {
                    definitionName = trimmedInput.substring(nameStart, nameEnd).trim();

                    // Extract signature and return type
                    int arrowPos = trimmedInput.indexOf("->", nameEnd);
                    String signature = "";
                    String returnType = "Void";

                    if (arrowPos > 0) {
                        signature = trimmedInput.substring(nameStart, arrowPos).trim();
                        int bracePos = trimmedInput.indexOf('{', arrowPos);
                        if (bracePos > arrowPos) {
                            returnType = trimmedInput.substring(arrowPos + 2, bracePos).trim();
                        }
                    } else {
                        signature = trimmedInput.substring(nameStart, trimmedInput.indexOf('{', nameEnd)).trim();
                    }

                    // Add to functions list
                    functions.add(new FunctionInfo(definitionName, signature, returnType));
                }
            } else if (trimmedInput.startsWith("class ")) {
                definitionType = "Class";
                int nameStart = 6; // after "class "
                int nameEnd = trimmedInput.indexOf(' ', nameStart);
                if (nameEnd == -1) nameEnd = trimmedInput.indexOf('{', nameStart);
                if (nameEnd > nameStart) {
                    definitionName = trimmedInput.substring(nameStart, nameEnd).trim();

                    // Add to classes list
                    classes.add(new ClassInfo(definitionName));
                }
            } else if (trimmedInput.startsWith("use ")) {
                definitionType = "Import";
                definitionName = trimmedInput.substring(4).trim();
                // Also add to imports list
                if (!imports.contains(definitionName)) {
                    imports.add(definitionName);
                }
            }

            // Return success with a confirmation message
            String message = definitionName.isEmpty() ?
                definitionType + " added" :
                definitionType + " '" + definitionName + "' defined";
            return EvalResult.success(message, "Definition");
        } else {
            // Wrap input in a function
            sourceBuilder.append("fn ").append(snippetName).append("() -> Void {\n");

            // For expressions (no semicolon), try to print the result
            // For statements (with semicolon), just execute
            if (!trimmedInput.endsWith(";")) {
                // Only wrap in println() if it's a simple expression (no function call)
                // Function calls might return Void, which can't be printed
                boolean isFunctionCall = trimmedInput.contains("(") && trimmedInput.contains(")");

                if (isFunctionCall) {
                    // Just execute - if it prints something, we'll see it
                    sourceBuilder.append("    ").append(trimmedInput).append(";\n");
                } else {
                    // Simple expression - wrap in println to show the result
                    sourceBuilder.append("    println(").append(trimmedInput).append(");\n");
                }
            } else {
                sourceBuilder.append("    ").append(trimmedInput).append("\n");
            }

            sourceBuilder.append("}\n\n");
        }

        // Add the fly() entry point that calls our snippet
        sourceBuilder.append("fn fly() -> Void {\n");
        sourceBuilder.append("    ").append(snippetName).append("();\n");
        sourceBuilder.append("}\n");

        String source = sourceBuilder.toString();

        // Compile the source with error listener
        CharStream charStream = CharStreams.fromString(source);
        FireflyLexer lexer = new FireflyLexer(charStream);

        // Custom error listener to capture syntax errors
        SyntaxErrorListener errorListener = new SyntaxErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        ParseTree tree = parser.compilationUnit();

        // Check for syntax errors
        if (errorListener.hasErrors()) {
            SyntaxErrorListener.SyntaxError error = errorListener.getFirstError();
            String suggestion = getSyntaxSuggestion(error.getMessage(), null);
            return EvalResult.error(
                error.getMessage(),
                EvalResult.ErrorType.SYNTAX,
                error.getLine(),
                error.getColumn(),
                suggestion
            );
        }

        // Build AST
        AstBuilder astBuilder = new AstBuilder("<repl>");
        AstNode ast = astBuilder.visit(tree);

        if (!(ast instanceof CompilationUnit)) {
            return EvalResult.error("Failed to build AST", EvalResult.ErrorType.COMPILATION);
        }

        // Semantic analysis
        SemanticAnalyzer analyzer = new SemanticAnalyzer(typeResolver);
        analyzer.analyze((CompilationUnit) ast);

        // Generate bytecode
        BytecodeGenerator generator = new BytecodeGenerator(typeResolver);
        Map<String, byte[]> classes = generator.generate((CompilationUnit) ast);

        // Load classes using the evaluation-specific ClassLoader
        String mainClassName = null;
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            // Convert path format (repl/Main) to class name format (repl.Main)
            String className = entry.getKey().replace('/', '.');
            evalClassLoader.defineClass(className, entry.getValue());
            // Remember the main class name
            if (entry.getKey().endsWith("/Main") || entry.getKey().equals("Main")) {
                mainClassName = className;
            }
        }

        if (mainClassName == null) {
            return EvalResult.error("Main class not found in generated bytecode", EvalResult.ErrorType.COMPILATION);
        }

        // Execute the snippet function
        // The function now prints its own result, so we just invoke it
        try {
            Class<?> mainClass = evalClassLoader.loadClass(mainClassName);
            Method method = mainClass.getMethod(snippetName);
            method.invoke(null);

            // Return success with no value (the function already printed the result)
            return EvalResult.success(null, "Void");
        } catch (VerifyError e) {
            // VerifyError usually means bytecode issue - often from wrong number of arguments
            String message = "Bytecode verification error";
            String suggestion = null;

            if (e.getMessage() != null && e.getMessage().contains("Operand stack underflow")) {
                message = "Invalid bytecode: possible argument mismatch in function call";
                suggestion = "Check that function calls have the correct number and types of arguments";
            }

            return EvalResult.error(message, EvalResult.ErrorType.COMPILATION, 0, 0, suggestion);
        } catch (NoSuchMethodException e) {
            return EvalResult.error("Function not found: " + snippetName, EvalResult.ErrorType.COMPILATION);
        }
    }

    /**
     * Custom error listener to capture syntax errors.
     */
    private static class SyntaxErrorListener extends BaseErrorListener {
        private final List<SyntaxError> errors = new ArrayList<>();

        static class SyntaxError {
            private final int line;
            private final int column;
            private final String message;

            SyntaxError(int line, int column, String message) {
                this.line = line;
                this.column = column;
                this.message = message;
            }

            public int getLine() { return line; }
            public int getColumn() { return column; }
            public String getMessage() { return message; }
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                               int line, int charPositionInLine, String msg, RecognitionException e) {
            errors.add(new SyntaxError(line, charPositionInLine, msg));
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public SyntaxError getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
    }
    
    /**
     * Resets the REPL state.
     */
    public void reset() {
        variables.clear();
        imports.clear();
        definitions.clear();
        snippetCounter = 0;
        addDefaultImports();
    }

    /**
     * Gets the current definitions.
     */
    public List<String> getDefinitions() {
        return new ArrayList<>(definitions);
    }
}

