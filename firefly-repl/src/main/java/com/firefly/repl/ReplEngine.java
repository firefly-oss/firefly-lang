package com.firefly.repl;

import com.firefly.compiler.FireflyCompiler;
import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.codegen.BytecodeGenerator;
import com.firefly.compiler.codegen.TypeResolver;
import com.firefly.compiler.diagnostic.DiagnosticReporter;
import com.firefly.compiler.semantics.SemanticAnalyzer;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Robust REPL engine with proper incremental compilation.
 * 
 * Architecture:
 * - Maintains full session state (variables, functions, imports)
 * - Each evaluation generates a complete, valid Firefly program
 * - Uses real parser/compiler, no string hacking
 * - Proper variable persistence across evaluations
 */
public class ReplEngine {
    
    // Session state
    private final List<String> imports;
    private final Map<String, VariableInfo> variables;  // name -> info
    private final List<String> topLevelDecls;  // classes, functions, etc.
    private final TypeResolver typeResolver;
    private final ReplClassLoader classLoader;
    
    // Execution state
    private Object currentInstance;
    private int evalCounter = 0;
    
    /**
     * Variable metadata.
     */
    public static class VariableInfo {
        final String name;
        final String type;
        final boolean mutable;
        Object value;  // Current value
        
        VariableInfo(String name, String type, boolean mutable) {
            this.name = name;
            this.type = type;
            this.mutable = mutable;
        }
    }
    
    /**
     * Evaluation result.
     */
    public static class EvalResult {
        final boolean success;
        final Object value;
        final String error;
        final Throwable cause;
        
        private EvalResult(boolean success, Object value, String error, Throwable cause) {
            this.success = success;
            this.value = value;
            this.error = error;
            this.cause = cause;
        }
        
        public static EvalResult success(Object value) {
            return new EvalResult(true, value, null, null);
        }
        
        public static EvalResult error(String error) {
            return new EvalResult(false, null, error, null);
        }
        
        public static EvalResult error(String error, Throwable cause) {
            return new EvalResult(false, null, error, cause);
        }
        
        public boolean isSuccess() { return success; }
        public Object getValue() { return value; }
        public String getError() { return error; }
        public Throwable getCause() { return cause; }
    }
    
    /**
     * Custom classloader for REPL-generated classes.
     * Also has access to stdlib classes via parent classloader.
     */
    private static class ReplClassLoader extends ClassLoader {
        private final Map<String, byte[]> classBytes = new HashMap<>();
        
        ReplClassLoader() {
            // Use system classloader as parent to access stdlib JAR
            super(Thread.currentThread().getContextClassLoader());
        }
        
        void defineClass(String name, byte[] bytes) {
            classBytes.put(name, bytes);
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            // Parent will handle stdlib and other JARs
            return super.findClass(name);
        }
    }
    
    public ReplEngine() {
        this.imports = new ArrayList<>();
        this.variables = new LinkedHashMap<>();  // Preserve order
        this.topLevelDecls = new ArrayList<>();
        this.typeResolver = new TypeResolver();
        this.classLoader = new ReplClassLoader();
        
        // Add default imports
        imports.add("use firefly::std::io::{println, print}");
    }
    
    /**
     * Evaluate a line of input.
     */
    public EvalResult eval(String input) {
        if (input == null || input.trim().isEmpty()) {
            return EvalResult.success(null);
        }
        
        String trimmed = input.trim();
        
        // Handle comments
        if (trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            return EvalResult.success(null);
        }
        
        try {
            return evalInternal(trimmed);
        } catch (Exception e) {
            return EvalResult.error("Error: " + e.getMessage(), e);
        }
    }
    
    private EvalResult evalInternal(String input) {
        evalCounter++;
        
        // Try to parse as different constructs
        ParseResult parseResult = parseInput(input);
        
        if (!parseResult.success) {
            return EvalResult.error(parseResult.error);
        }
        
        // Handle based on what we parsed
        switch (parseResult.type) {
            case IMPORT:
                handleImport(input);
                return EvalResult.success("Import added");
                
            case TOP_LEVEL_DECL:
                handleTopLevelDecl(input);
                return EvalResult.success("Declaration added");
                
            case LET_STATEMENT:
                return handleLetStatement(parseResult);
                
            case ASSIGNMENT:
                return handleAssignment(parseResult);
                
            case EXPRESSION:
                return handleExpression(parseResult);
                
            case STATEMENT:
                return handleStatement(parseResult);
                
            default:
                return EvalResult.error("Unknown input type");
        }
    }
    
    /**
     * Parse input to determine what it is.
     */
    private ParseResult parseInput(String input) {
        // Quick checks first
        if (input.startsWith("use ")) {
            return ParseResult.success(InputType.IMPORT, input);
        }
        
        if (input.startsWith("class ") || input.startsWith("struct ") || 
            input.startsWith("data ") || input.startsWith("spark ") ||
            input.startsWith("fn ")) {
            return ParseResult.success(InputType.TOP_LEVEL_DECL, input);
        }
        
        // Parse as statement/expression
        try {
            // Wrap in minimal valid program to parse
            String wrapped = "module repl\n\nclass Main {\n  pub fn snippet() -> Void {\n    " + 
                           input + (input.endsWith(";") ? "" : ";") + "\n  }\n  pub fn fly(args: [String]) -> Void {}\n}\n";
            
            CharStream chars = CharStreams.fromString(wrapped);
            FireflyLexer lexer = new FireflyLexer(chars);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            FireflyParser parser = new FireflyParser(tokens);
            
            // Disable error reporting to stderr
            parser.removeErrorListeners();
            lexer.removeErrorListeners();
            
            ParseTree tree = parser.compilationUnit();
            
            // Build AST
            AstBuilder astBuilder = new AstBuilder("<repl>");
            AstNode ast = astBuilder.visit(tree);
            
            if (!(ast instanceof CompilationUnit)) {
                return ParseResult.error("Failed to parse");
            }
            
            CompilationUnit cu = (CompilationUnit) ast;
            
            // Extract the statement from Main.snippet
            for (Declaration decl : cu.getDeclarations()) {
                if (decl instanceof ClassDecl) {
                    ClassDecl classDecl = (ClassDecl) decl;
                    for (ClassDecl.MethodDecl method : classDecl.getMethods()) {
                        if ("snippet".equals(method.getName())) {
                            if (method.getBody() instanceof BlockExpr) {
                                BlockExpr block = (BlockExpr) method.getBody();
                                if (!block.getStatements().isEmpty()) {
                                    Statement stmt = block.getStatements().get(0);
                                    return classifyStatement(stmt, input);
                                }
                            }
                        }
                    }
                }
            }
            
            return ParseResult.error("Could not extract statement");
            
        } catch (Exception e) {
            return ParseResult.error("Parse error: " + e.getMessage());
        }
    }
    
    private ParseResult classifyStatement(Statement stmt, String originalInput) {
        if (stmt instanceof LetStatement) {
            LetStatement letStmt = (LetStatement) stmt;
            return ParseResult.letStatement(originalInput, letStmt);
        }
        
        if (stmt instanceof ExprStatement) {
            ExprStatement exprStmt = (ExprStatement) stmt;
            Expression expr = exprStmt.getExpression();
            
            if (expr instanceof AssignmentExpr) {
                return ParseResult.assignment(originalInput, (AssignmentExpr) expr);
            }
            
            // Detect side-effect functions (println, print, etc.) - treat as statements
            if (expr instanceof CallExpr) {
                CallExpr call = (CallExpr) expr;
                Expression function = call.getFunction();
                if (function instanceof IdentifierExpr) {
                    String funcName = ((IdentifierExpr) function).getName();
                    if ("println".equals(funcName) || "print".equals(funcName)) {
                        return ParseResult.success(InputType.STATEMENT, originalInput);
                    }
                }
            }
            
            // Other expressions - treat as expressions
            return ParseResult.expression(originalInput, expr);
        }
        
        return ParseResult.success(InputType.STATEMENT, originalInput);
    }
    
    private void handleImport(String input) {
        if (!imports.contains(input)) {
            imports.add(input);
        }
    }
    
    private void handleTopLevelDecl(String input) {
        if (!topLevelDecls.contains(input)) {
            topLevelDecls.add(input);
        }
    }
    
    private EvalResult handleLetStatement(ParseResult result) {
        LetStatement letStmt = result.letStatement;
        String input = result.input;
        
        // Extract variable info
        Pattern pattern = letStmt.getPattern();
        String varName = null;
        String varType = "Object";
        
        if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            var tvp = (com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern;
            varName = tvp.getName();
            varType = tvp.getType().getName();
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            var vp = (com.firefly.compiler.ast.pattern.VariablePattern) pattern;
            varName = vp.getName();
        }
        
        if (varName == null) {
            return EvalResult.error("Could not extract variable name");
        }
        
        boolean mutable = letStmt.isMutable();
        
        // Register variable
        variables.put(varName, new VariableInfo(varName, varType, mutable));
        
        // Extract initialization value from input
        String initValue = extractInitValue(input);
        
        // Compile and execute to get the value
        return executeWithVariables(varName + " = " + initValue + ";", true);
    }
    
    private String extractInitValue(String input) {
        int eqPos = input.indexOf('=');
        if (eqPos > 0) {
            String value = input.substring(eqPos + 1).trim();
            if (value.endsWith(";")) {
                value = value.substring(0, value.length() - 1).trim();
            }
            return value;
        }
        return "0";  // default
    }
    
    private EvalResult handleAssignment(ParseResult result) {
        AssignmentExpr assignment = result.assignment;
        String input = result.input;
        
        // Extract variable name
        Expression target = assignment.getTarget();
        String varName = null;
        if (target instanceof IdentifierExpr) {
            varName = ((IdentifierExpr) target).getName();
        }
        
        if (varName == null || !variables.containsKey(varName)) {
            return EvalResult.error("Variable not found: " + varName);
        }
        
        VariableInfo varInfo = variables.get(varName);
        if (!varInfo.mutable) {
            return EvalResult.error("Cannot assign to immutable variable: " + varName);
        }
        
        // Execute assignment
        String stmt = input.endsWith(";") ? input : input + ";";
        return executeWithVariables(stmt, true);
    }
    
    private EvalResult handleExpression(ParseResult result) {
        String input = result.input;
        // For expressions, DON'T add semicolon - it should be the return value
        String code = input.endsWith(";") ? input.substring(0, input.length() - 1) : input;
        return executeWithVariables(code, false);
    }
    
    private EvalResult handleStatement(ParseResult result) {
        String input = result.input;
        String stmt = input.endsWith(";") ? input : input + ";";
        return executeWithVariables(stmt, true);
    }
    
    /**
     * Execute code with all current variables in scope.
     */
    private EvalResult executeWithVariables(String code, boolean returnsVoid) {
        try {
            // Build complete program
            String moduleName = "repl" + evalCounter;
            String methodName = "eval" + evalCounter;
            
            StringBuilder source = new StringBuilder();
            source.append("module ").append(moduleName).append("\n\n");
            
            // Add imports
            for (String imp : imports) {
                source.append(imp).append("\n");
            }
            source.append("\n");
            
            // Add top-level declarations
            for (String decl : topLevelDecls) {
                source.append(decl).append("\n\n");
            }
            
            // Build Main class with all variables as fields
            source.append("class Main {\n");
            
            // Declare all variables as fields (nullable for custom types)
            for (VariableInfo var : variables.values()) {
                // Check if it's a primitive type
                boolean isPrimitive = isPrimitiveType(var.type);
                source.append("  pub let mut ").append(var.name)
                      .append(": ").append(var.type);
                // Add ? for non-primitive types to make them nullable
                if (!isPrimitive) {
                    source.append("?");
                }
                source.append(";\n");
            }
            
            if (!variables.isEmpty()) {
                source.append("\n");
            }
            
            // Constructor
            if (!variables.isEmpty()) {
                source.append("  pub init() {\n");
                for (VariableInfo var : variables.values()) {
                    boolean isPrimitive = isPrimitiveType(var.type);
                    String defaultValue = isPrimitive ? getDefaultValue(var.type) : "none";
                    source.append("    self.").append(var.name)
                          .append(" = ").append(defaultValue).append(";\n");
                }
                source.append("  }\n\n");
            }
            
            // Evaluation method
            String returnType = returnsVoid ? "Void" : "Object";
            source.append("  pub fn ").append(methodName)
                  .append("() -> ").append(returnType).append(" {\n");
            
            // Load all variables
            for (VariableInfo var : variables.values()) {
                boolean isPrimitive = isPrimitiveType(var.type);
                source.append("    let mut ").append(var.name)
                      .append(": ").append(var.type);
                // Add ? for non-primitive types
                if (!isPrimitive) {
                    source.append("?");
                }
                source.append(" = self.").append(var.name).append(";\n");
            }
            
            if (!variables.isEmpty()) {
                source.append("\n");
            }
            
            // Execute user code
            if (returnsVoid) {
                // For statements, add semicolon
                source.append("    ").append(code);
                if (!code.endsWith(";")) {
                    source.append(";");
                }
                source.append("\n");
            } else {
                // For expressions, save variables BEFORE the expression
                // and make expression the final return value
                if (!variables.isEmpty()) {
                    for (VariableInfo var : variables.values()) {
                        source.append("    self.").append(var.name)
                              .append(" = ").append(var.name).append(";\n");
                    }
                    source.append("\n");
                }
                // Final expression without semicolon (return value)
                source.append("    ").append(code).append("\n");
            }
            
            // Save variables for statements only (expressions handled above)
            if (returnsVoid && !variables.isEmpty()) {
                source.append("\n");
                for (VariableInfo var : variables.values()) {
                    source.append("    self.").append(var.name)
                          .append(" = ").append(var.name).append(";\n");
                }
            }
            
            source.append("  }\n\n");
            
            // fly method
            source.append("  pub fn fly(args: [String]) -> Void {\n");
            source.append("    self::").append(methodName).append("();\n");
            source.append("  }\n");
            
            source.append("}\n");
            
            String sourceCode = source.toString();
            
            if (System.getenv("REPL_DEBUG") != null) {
                System.err.println("\n=== GENERATED ===");
                System.err.println(sourceCode);
                System.err.println("=== END ===\n");
            }
            
            // Compile
            CharStream chars = CharStreams.fromString(sourceCode);
            FireflyLexer lexer = new FireflyLexer(chars);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            FireflyParser parser = new FireflyParser(tokens);
            
            ParseTree tree = parser.compilationUnit();
            AstBuilder astBuilder = new AstBuilder("<repl>");
            AstNode ast = astBuilder.visit(tree);
            
            if (!(ast instanceof CompilationUnit)) {
                return EvalResult.error("Failed to build AST");
            }
            
            CompilationUnit cu = (CompilationUnit) ast;
            
            // Semantic analysis
            SemanticAnalyzer analyzer = new SemanticAnalyzer(typeResolver);
            analyzer.analyze(cu);
            
            // Generate bytecode
            BytecodeGenerator generator = new BytecodeGenerator(typeResolver);
            Map<String, byte[]> classes = generator.generate(cu);
            
            // Load classes
            String mainClassName = null;
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                String className = entry.getKey().replace('/', '.');
                classLoader.defineClass(className, entry.getValue());
                if (entry.getKey().endsWith("/Main")) {
                    mainClassName = className;
                }
            }
            
            if (mainClassName == null) {
                return EvalResult.error("Main class not found");
            }
            
            // Execute
            Class<?> mainClass = classLoader.loadClass(mainClassName);
            
            // Always create new instance (different class each time)
            currentInstance = mainClass.getDeclaredConstructor().newInstance();
            
            // Restore variable values from previous evaluation
            for (VariableInfo var : variables.values()) {
                try {
                    var field = mainClass.getDeclaredField(var.name);
                    field.setAccessible(true);
                    // Set from saved value, or default if null
                    if (var.value != null) {
                        field.set(currentInstance, var.value);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            // Call eval method
            Method evalMethod = mainClass.getMethod(methodName);
            Object result = evalMethod.invoke(currentInstance);
            
            // Save variable values for next evaluation
            for (VariableInfo var : variables.values()) {
                try {
                    var field = mainClass.getDeclaredField(var.name);
                    field.setAccessible(true);
                    var.value = field.get(currentInstance);
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            // Print non-void results
            if (!returnsVoid && result != null) {
                System.out.println(result);
            }
            
            return EvalResult.success(result);
            
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            return EvalResult.error(cause != null ? cause.getMessage() : "Execution error", cause);
        } catch (Exception e) {
            return EvalResult.error(e.getMessage(), e);
        }
    }
    
    private boolean isPrimitiveType(String type) {
        return switch (type) {
            case "Int", "Long", "Float", "Double", "Bool", "String" -> true;
            default -> false;
        };
    }
    
    private String getDefaultValue(String type) {
        return switch (type) {
            case "Int" -> "0";
            case "Long" -> "0";
            case "Float", "Double" -> "0.0";
            case "Bool" -> "false";
            case "String" -> "\"\"";
            default -> "none";  // Use 'none' for custom types (Option, Date, etc.)
        };
    }
    
    public void reset() {
        imports.clear();
        variables.clear();
        topLevelDecls.clear();
        currentInstance = null;
        evalCounter = 0;
        
        // Re-add defaults
        imports.add("use firefly::std::io::{println, print}");
    }
    
    public Map<String, VariableInfo> getVariables() {
        return new LinkedHashMap<>(variables);
    }
    
    public Map<String, Object> getVariableValues() {
        // For UI compatibility - return map of name -> value
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, VariableInfo> entry : variables.entrySet()) {
            result.put(entry.getKey(), entry.getValue().value);
        }
        return result;
    }
    
    public List<String> getImports() {
        return new ArrayList<>(imports);
    }
    
    public List<String> getDefinitions() {
        return new ArrayList<>(topLevelDecls);
    }
    
    public List<FunctionInfo> getFunctions() {
        // Extract functions from topLevelDecls
        List<FunctionInfo> result = new ArrayList<>();
        for (String decl : topLevelDecls) {
            if (decl.trim().startsWith("fn ")) {
                // Extract function name
                String trimmed = decl.trim().substring(3).trim();
                int parenPos = trimmed.indexOf('(');
                if (parenPos > 0) {
                    String name = trimmed.substring(0, parenPos).trim();
                    result.add(new FunctionInfo(name, name + "(...)", "Unknown"));
                }
            }
        }
        return result;
    }
    
    public List<ClassInfo> getClasses() {
        // Extract classes from topLevelDecls
        List<ClassInfo> result = new ArrayList<>();
        for (String decl : topLevelDecls) {
            if (decl.trim().startsWith("class ")) {
                String trimmed = decl.trim().substring(6).trim();
                int spacePos = trimmed.indexOf(' ');
                int bracePos = trimmed.indexOf('{');
                int endPos = spacePos > 0 && spacePos < bracePos ? spacePos : bracePos;
                if (endPos > 0) {
                    String name = trimmed.substring(0, endPos).trim();
                    result.add(new ClassInfo(name));
                }
            }
        }
        return result;
    }
    
    public String inferTypeOf(String expression) {
        // Simple type inference - just evaluate and get type
        try {
            EvalResult result = eval(expression);
            if (result.isSuccess() && result.getValue() != null) {
                return result.getValue().getClass().getSimpleName();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown";
    }
    
    // Helper classes and nested types for UI compatibility
    
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
    
    public static class ClassInfo {
        private final String name;
        
        public ClassInfo(String name) {
            this.name = name;
        }
        
        public String getName() { return name; }
    }
    
    private enum InputType {
        IMPORT, TOP_LEVEL_DECL, LET_STATEMENT, ASSIGNMENT, EXPRESSION, STATEMENT
    }
    
    private static class ParseResult {
        final boolean success;
        final InputType type;
        final String input;
        final String error;
        final LetStatement letStatement;
        final AssignmentExpr assignment;
        final Expression expression;
        
        private ParseResult(boolean success, InputType type, String input, String error,
                           LetStatement letStatement, AssignmentExpr assignment, Expression expression) {
            this.success = success;
            this.type = type;
            this.input = input;
            this.error = error;
            this.letStatement = letStatement;
            this.assignment = assignment;
            this.expression = expression;
        }
        
        static ParseResult success(InputType type, String input) {
            return new ParseResult(true, type, input, null, null, null, null);
        }
        
        static ParseResult letStatement(String input, LetStatement stmt) {
            return new ParseResult(true, InputType.LET_STATEMENT, input, null, stmt, null, null);
        }
        
        static ParseResult assignment(String input, AssignmentExpr expr) {
            return new ParseResult(true, InputType.ASSIGNMENT, input, null, null, expr, null);
        }
        
        static ParseResult expression(String input, Expression expr) {
            return new ParseResult(true, InputType.EXPRESSION, input, null, null, null, expr);
        }
        
        static ParseResult error(String error) {
            return new ParseResult(false, null, null, error, null, null, null);
        }
    }
}
