package com.firefly.compiler.semantics;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.codegen.TypeResolver;
import com.firefly.compiler.diagnostics.CompilerDiagnostic;
import com.firefly.compiler.semantic.ImportAndSymbolValidator;
import com.firefly.compiler.semantic.SymbolTable;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.*;

/**
 * Professional semantic analyzer for Firefly.
 * Performs validation before code generation:
 * - Import resolution
 * - Symbol resolution
 * - Scope checking
 * - Basic type validation
 */
public class SemanticAnalyzer implements AstVisitor<Void> {
    
    private final TypeResolver typeResolver;
    private final List<CompilerDiagnostic> diagnostics;
    private final Map<String, SymbolInfo> symbolTable;
    private final Stack<Map<String, SymbolInfo>> scopeStack;
    private final ImportAndSymbolValidator importValidator;
    private final DiagnosticReporter diagnosticReporter;
    private CompilationUnit currentUnit;
    private boolean inClassContext = false;
    private String currentClassName = null;
    private boolean strictImportMode = true; // Enable strict import validation
    
    public SemanticAnalyzer(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
        this.diagnostics = new ArrayList<>();
        this.symbolTable = new HashMap<>();
        this.scopeStack = new Stack<>();
        this.diagnosticReporter = new DiagnosticReporter();
        this.importValidator = new ImportAndSymbolValidator(diagnosticReporter, true, true);
    }
    
    /**
     * Set strict import mode.
     * When true, all symbols must be explicitly imported or defined.
     */
    public void setStrictImportMode(boolean enabled) {
        this.strictImportMode = enabled;
    }
    
    public List<CompilerDiagnostic> analyze(CompilationUnit unit) {
        diagnostics.clear();
        symbolTable.clear();
        scopeStack.clear();
        diagnosticReporter.clear();
        currentUnit = unit;
        
        // Initialize built-in functions (prelude)
        initializeBuiltins();
        
        // Phase 1: Standard semantic analysis
        unit.accept(this);
        
        // Phase 2: Strict import validation (if enabled)
        if (strictImportMode) {
            importValidator.validate(unit);
            
            // Convert DiagnosticReporter errors to CompilerDiagnostics
            for (var diagnostic : diagnosticReporter.getDiagnostics()) {
                if (diagnostic.isError()) {
                    diagnostics.add(CompilerDiagnostic.error(
                        CompilerDiagnostic.Phase.SEMANTIC,
                        diagnostic.getMessage(),
                        diagnostic.getLocation(),
                        diagnostic.getHint()
                    ));
                } else if (diagnostic.isWarning()) {
                    diagnostics.add(CompilerDiagnostic.warning(
                        CompilerDiagnostic.Phase.SEMANTIC,
                        diagnostic.getMessage(),
                        diagnostic.getLocation(),
                        diagnostic.getHint()
                    ));
                }
            }
        }
        
        return diagnostics;
    }
    
    /**
     * Initialize built-in functions that are available without imports (prelude).
     */
    private void initializeBuiltins() {
        // Built-in print functions
        symbolTable.put("println", new SymbolInfo("println", SymbolKind.FUNCTION));
        symbolTable.put("print", new SymbolInfo("print", SymbolKind.FUNCTION));
        
        // Built-in utility functions
        symbolTable.put("toString", new SymbolInfo("toString", SymbolKind.FUNCTION));
        symbolTable.put("error", new SymbolInfo("error", SymbolKind.FUNCTION));
        symbolTable.put("panic", new SymbolInfo("panic", SymbolKind.FUNCTION));
        
        // Built-in string formatting
        symbolTable.put("format", new SymbolInfo("format", SymbolKind.FUNCTION));
    }
    
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(CompilerDiagnostic::isError);
    }
    
    // ============ Compilation Unit ============
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Phase 0: Enforce class-based structure - no top-level functions allowed
        for (Declaration decl : unit.getDeclarations()) {
            if (decl instanceof FunctionDecl) {
                FunctionDecl funcDecl = (FunctionDecl) decl;
                diagnostics.add(CompilerDiagnostic.error(
                    CompilerDiagnostic.Phase.SEMANTIC,
                    "Top-level functions are not allowed. Function '" + funcDecl.getName() + "' must be inside a class.",
                    decl.getLocation(),
                    "Move this function into a class declaration"
                ));
            }
        }
        
        // Phase 1: Validate all imports
        for (UseDeclaration importDecl : unit.getImports()) {
            validateImport(importDecl);
        }
        
        // Phase 2: Collect top-level declarations
        for (Declaration decl : unit.getDeclarations()) {
            if (decl instanceof ClassDecl) {
                ClassDecl classDecl = (ClassDecl) decl;
                symbolTable.put(classDecl.getName(), new SymbolInfo(classDecl.getName(), SymbolKind.CLASS));
            } else if (decl instanceof ActorDecl) {
                ActorDecl actorDecl = (ActorDecl) decl;
                symbolTable.put(actorDecl.getName(), new SymbolInfo(actorDecl.getName(), SymbolKind.CLASS));
            } else if (decl instanceof StructDecl) {
                StructDecl structDecl = (StructDecl) decl;
                symbolTable.put(structDecl.getName(), new SymbolInfo(structDecl.getName(), SymbolKind.CLASS));
            } else if (decl instanceof DataDecl) {
                DataDecl dataDecl = (DataDecl) decl;
                symbolTable.put(dataDecl.getName(), new SymbolInfo(dataDecl.getName(), SymbolKind.CLASS));
            } else if (decl instanceof InterfaceDecl) {
                InterfaceDecl ifaceDecl = (InterfaceDecl) decl;
                symbolTable.put(ifaceDecl.getName(), new SymbolInfo(ifaceDecl.getName(), SymbolKind.CLASS));
            } else if (decl instanceof TraitDecl) {
                TraitDecl traitDecl = (TraitDecl) decl;
                symbolTable.put(traitDecl.getName(), new SymbolInfo(traitDecl.getName(), SymbolKind.CLASS));
            }
        }
        
        // Phase 3: Analyze declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        
        return null;
    }
    
    private void validateImport(UseDeclaration importDecl) {
        String modulePath = importDecl.getModulePath();
        
        if (importDecl.isWildcard()) {
            // For wildcard imports, we can't validate at compile time without scanning the package
            // Just trust it exists
            diagnostics.add(CompilerDiagnostic.info(
                CompilerDiagnostic.Phase.SEMANTIC,
                "Wildcard import: " + modulePath + ".*"
            ));
        } else {
            // Validate each specific import
            for (String item : importDecl.getItems()) {
                Optional<String> resolved = typeResolver.resolveClassName(item);
                if (!resolved.isPresent()) {
                    diagnostics.add(CompilerDiagnostic.error(
                        CompilerDiagnostic.Phase.SEMANTIC,
                        "Cannot resolve import: " + item + " from " + modulePath,
                        importDecl.getLocation(),
                        "Make sure the class exists in the classpath and the import path is correct"
                    ));
                } else {
                    diagnostics.add(CompilerDiagnostic.info(
                        CompilerDiagnostic.Phase.SEMANTIC,
                        "✓ Resolved import: " + item + " -> " + resolved.get()
                    ));
                }
            }
        }
    }
    
    @Override
    public Void visitUseDeclaration(UseDeclaration decl) {
        // Already handled in visitCompilationUnit
        return null;
    }
    
    // ============ Declarations ============
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        // Push new scope for function
        pushScope();
        
        // Register parameters in scope
        for (FunctionDecl.Parameter param : decl.getParameters()) {
            addSymbol(param.getName(), SymbolKind.VARIABLE, decl.getLocation());
        }
        
        // Analyze body
        decl.getBody().accept(this);
        
        popScope();
        return null;
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        boolean wasInClass = inClassContext;
        String oldClassName = currentClassName;
        
        inClassContext = true;
        currentClassName = decl.getName();
        
        // Register class as a symbol (so it can be constructed)
        symbolTable.put(decl.getName(), new SymbolInfo(decl.getName(), SymbolKind.CLASS));
        
        // Validate fly declaration if present
        if (decl.getFlyDeclaration().isPresent()) {
            ClassDecl.FlyDecl flyDecl = decl.getFlyDeclaration().get();
            
            // Validate fly function signature
            if (flyDecl.getParameters().size() != 1) {
                diagnostics.add(CompilerDiagnostic.error(
                    CompilerDiagnostic.Phase.SEMANTIC,
                    "fly() function must have exactly one parameter: args: [String]",
                    decl.getLocation(),
                    "Change signature to: fn fly(args: [String]) -> Void"
                ));
            } else {
                FunctionDecl.Parameter param = flyDecl.getParameters().get(0);
                if (!"args".equals(param.getName())) {
                    diagnostics.add(CompilerDiagnostic.error(
                        CompilerDiagnostic.Phase.SEMANTIC,
                        "fly() function parameter must be named 'args'",
                        decl.getLocation(),
                        "Change parameter name to 'args'"
                    ));
                }
                
                // Check parameter type is [String]
                if (!(param.getType() instanceof ArrayType)) {
                    diagnostics.add(CompilerDiagnostic.error(
                        CompilerDiagnostic.Phase.SEMANTIC,
                        "fly() function parameter must be of type [String]",
                        decl.getLocation(),
                        "Change parameter type to [String]"
                    ));
                } else {
                    ArrayType arrayType = (ArrayType) param.getType();
                    if (!(arrayType.getElementType() instanceof PrimitiveType) ||
                        !"String".equals(((PrimitiveType) arrayType.getElementType()).getName())) {
                        diagnostics.add(CompilerDiagnostic.error(
                            CompilerDiagnostic.Phase.SEMANTIC,
                            "fly() function parameter must be of type [String]",
                            decl.getLocation(),
                            "Change parameter type to [String]"
                        ));
                    }
                }
            }
            
            // Analyze fly function body
            pushScope();
            
            // fly() is now an INSTANCE method (not static), so 'self' is available
            addSymbol("self", SymbolKind.VARIABLE, decl.getLocation());
            
            // Register args parameter
            for (FunctionDecl.Parameter param : flyDecl.getParameters()) {
                addSymbol(param.getName(), SymbolKind.VARIABLE, decl.getLocation());
            }
            
            flyDecl.getBody().accept(this);
            popScope();
        }
        
        // Analyze constructor if present
        if (decl.getConstructor().isPresent()) {
            ClassDecl.ConstructorDecl constructor = decl.getConstructor().get();
            pushScope();
            
            // "self" is available in constructor
            addSymbol("self", SymbolKind.VARIABLE, decl.getLocation());
            
            // Register parameters
            for (FunctionDecl.Parameter param : constructor.getParameters()) {
                addSymbol(param.getName(), SymbolKind.VARIABLE, decl.getLocation());
            }
            
            // Analyze constructor body (it's an Expression, not Statement)
            if (constructor.getBody() != null) {
                constructor.getBody().accept(this);
            }
            
            popScope();
        }
        
        // Analyze class methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            pushScope();
            
            // "self" is available in instance methods
            addSymbol("self", SymbolKind.VARIABLE, decl.getLocation());
            
            // Register parameters
            for (FunctionDecl.Parameter param : method.getParameters()) {
                addSymbol(param.getName(), SymbolKind.VARIABLE, decl.getLocation());
            }
            
            // Analyze method body
            method.getBody().accept(this);
            
            popScope();
        }
        
        inClassContext = wasInClass;
        currentClassName = oldClassName;
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Interfaces only have signatures, no implementation to analyze
        return null;
    }
    @Override
    public Void visitActorDecl(ActorDecl decl) {
        boolean wasInClass = inClassContext;
        String oldClassName = currentClassName;
        
        inClassContext = true;
        currentClassName = decl.getName();
        
        // Register actor as a symbol
        symbolTable.put(decl.getName(), new SymbolInfo(decl.getName(), SymbolKind.CLASS));
        
        // Push scope for actor members
        pushScope();
        
        // Register fields in actor scope
        for (FieldDecl field : decl.getFields()) {
            addSymbol(field.getName(), SymbolKind.VARIABLE, field.getLocation());
        }
        
        // Analyze init block
        if (decl.getInitBlock() != null) {
            pushScope();
            // "self" is available in init block
            addSymbol("self", SymbolKind.VARIABLE, decl.getLocation());
            decl.getInitBlock().accept(this);
            popScope();
        }
        
        // Analyze receive cases
        for (ActorDecl.ReceiveCase receiveCase : decl.getReceiveCases()) {
            pushScope();
            // "self" is available in receive handlers
            addSymbol("self", SymbolKind.VARIABLE, decl.getLocation());
            
            // Register pattern variables if any
            if (receiveCase.getPattern() instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
                com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern =
                    (com.firefly.compiler.ast.pattern.TypedVariablePattern) receiveCase.getPattern();
                addSymbol(typedPattern.getName(), SymbolKind.VARIABLE, receiveCase.getLocation());
            } else if (receiveCase.getPattern() instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
                com.firefly.compiler.ast.pattern.VariablePattern varPattern =
                    (com.firefly.compiler.ast.pattern.VariablePattern) receiveCase.getPattern();
                addSymbol(varPattern.getName(), SymbolKind.VARIABLE, receiveCase.getLocation());
            }
            
            // Analyze the handler expression
            receiveCase.getExpression().accept(this);
            popScope();
        }
        
        popScope();
        
        inClassContext = wasInClass;
        currentClassName = oldClassName;
        return null;
    }

    
    @Override
    public Void visitStructDecl(StructDecl decl) {
        // Structs don't have direct annotations in current AST, so this is a no-op for now
        // In future, if we add annotation support to StructDecl, validate here
        return null;
    }
    
    @Override
    public Void visitSparkDecl(SparkDecl decl) {
        // Validate spark-specific annotations
        List<String> sparkOnlyAnnotations = Arrays.asList(
            "travelable", "events", "observable", "derive"
        );
        
        for (Annotation annotation : decl.getAnnotations()) {
            String annotationName = annotation.getName();
            // Check if this is a spark-only annotation
            if (sparkOnlyAnnotations.contains(annotationName)) {
                // This is OK - spark can use these annotations
                diagnostics.add(CompilerDiagnostic.info(
                    CompilerDiagnostic.Phase.SEMANTIC,
                    "Spark " + decl.getName() + " uses @" + annotationName
                ));
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitDataDecl(DataDecl decl) {
        // Data types don't have direct annotations in current AST, so this is a no-op for now
        // In future, if we add annotation support to DataDecl, validate here
        return null;
    }
    @Override
    public Void visitTraitDecl(TraitDecl decl) {
        // Traits don't have direct annotations in current AST, so this is a no-op for now
        // In future, if we add annotation support to TraitDecl, validate here
        return null;
    }
    @Override public Void visitImplDecl(ImplDecl decl) { return null; }
    
    // ============ Statements ============
    
    @Override
    public Void visitLetStatement(LetStatement stmt) {
        // Analyze initializer first
        if (stmt.getInitializer().isPresent()) {
            stmt.getInitializer().get().accept(this);
        }
        
        // Enforce explicit type on let bindings (strong typing)
        if (stmt.getPattern() instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern =
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) stmt.getPattern();
            addSymbol(typedPattern.getName(), SymbolKind.VARIABLE, stmt.isMutable(), stmt.getLocation());
        } else {
            // Any untyped let is an error
            diagnostics.add(CompilerDiagnostic.error(
                CompilerDiagnostic.Phase.SEMANTIC,
                "Explicit type required in let binding (e.g., 'let x: Int = ...')",
                stmt.getLocation(),
                "Add a type annotation to this variable"
            ));
        }
        
        return null;
    }
    
    @Override
    public Void visitExprStatement(com.firefly.compiler.ast.ExprStatement stmt) {
        stmt.getExpression().accept(this);
        return null;
    }
    
    // ============ Expressions ============
    
    @Override
    public Void visitBinaryExpr(BinaryExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        
        // Check for string concatenation with + operator (not supported)
        if (expr.getOperator() == BinaryExpr.BinaryOp.ADD) {
            // We'd need type inference to know if operands are strings
            // For now, we'll let this through and handle it in codegen
            // A more sophisticated approach would require full type inference here
        }
        
        return null;
    }
    
    @Override
    public Void visitCallExpr(CallExpr expr) {
        // Enforce '::' for method invocations (forbid '.'-style calls)
        if (expr.getFunction() instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expr.getFunction();
            
            // If this FieldAccess was not produced by a '::' parse, flag it
            if (!fieldAccess.isFromDoubleColon()) {
                diagnostics.add(CompilerDiagnostic.error(
                    CompilerDiagnostic.Phase.SEMANTIC,
                    "Use '::' for method calls (e.g., obj::method(args))",
                    fieldAccess.getLocation(),
                    "Replace '.' with '::'"
                ));
            }
            
            // Static calls via ClassName::member are allowed and parsed with '::'
            if (fieldAccess.getObject() instanceof IdentifierExpr) {
                String className = ((IdentifierExpr) fieldAccess.getObject()).getName();
                // Accept if it's a resolvable Java class OR a top-level type declared in this module (struct/data/class/trait)
                Optional<String> resolvedClass = typeResolver.resolveClassName(className);
                if (resolvedClass.isPresent() || symbolTable.containsKey(className)) {
                    for (Expression arg : expr.getArguments()) {
                        arg.accept(this);
                    }
                    return null;
                }
            }
        }
        
        // Regular function call or instance method
        expr.getFunction().accept(this);
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitFieldAccessExpr(FieldAccessExpr expr) {
        // Check if this is a static member access (ClassName.member)
        if (expr.getObject() instanceof IdentifierExpr) {
            String name = ((IdentifierExpr) expr.getObject()).getName();
            
            // Check if it's a class name
            Optional<String> className = typeResolver.resolveClassName(name);
            if (className.isPresent()) {
                // This is static access (e.g., SpringApplication.run)
                // Don't visit the object as a variable
                return null;
            }
        }
        
        // Regular instance field access - visit the object
        expr.getObject().accept(this);
        return null;
    }
    
    @Override
    public Void visitIdentifierExpr(IdentifierExpr expr) {
        String name = expr.getName();
        
        // Check if it's a builtin function
        if (name.equals("println") || name.equals("print") || 
            name.equals("format") || name.equals("spawn")) {
            return null; // Builtins are always valid
        }
        
        // Check if it's a local variable
        if (isSymbolInScope(name)) {
            return null;
        }
        
        // Check if it's a class name (for static calls or .class literals)
        Optional<String> className = typeResolver.resolveClassName(name);
        if (className.isPresent()) {
            return null;
        }
        
        // Check if it's a top-level symbol
        if (symbolTable.containsKey(name)) {
            return null;
        }
        
        // Undefined symbol
        diagnostics.add(CompilerDiagnostic.error(
            CompilerDiagnostic.Phase.SEMANTIC,
            "Undefined symbol: " + name,
            expr.getLocation(),
            "Did you forget to declare this variable or import the class?"
        ));
        
        return null;
    }
    
    @Override
    public Void visitIfExpr(IfExpr expr) {
        expr.getCondition().accept(this);
        expr.getThenBranch().accept(this);
        if (expr.getElseBranch().isPresent()) {
            expr.getElseBranch().get().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitBlockExpr(BlockExpr expr) {
        pushScope();
        
        for (Statement stmt : expr.getStatements()) {
            if (stmt != null) {  // Guard against null statements from parser
                stmt.accept(this);
            }
        }
        
        if (expr.getFinalExpression().isPresent()) {
            expr.getFinalExpression().get().accept(this);
        }
        
        popScope();
        return null;
    }
    
    @Override
    public Void visitForExpr(ForExpr expr) {
        pushScope();
        
        expr.getIterable().accept(this);
        
        // Register loop variable
        if (expr.getPattern() instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern =
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) expr.getPattern();
            addSymbol(typedPattern.getName(), SymbolKind.VARIABLE, expr.getLocation());
        } else if (expr.getPattern() instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            com.firefly.compiler.ast.pattern.VariablePattern varPattern =
                (com.firefly.compiler.ast.pattern.VariablePattern) expr.getPattern();
            addSymbol(varPattern.getName(), SymbolKind.VARIABLE, expr.getLocation());
        }
        
        expr.getBody().accept(this);
        
        popScope();
        return null;
    }
    
    @Override
    public Void visitWhileExpr(WhileExpr expr) {
        expr.getCondition().accept(this);
        expr.getBody().accept(this);
        return null;
    }
    
    @Override
    public Void visitReturnExpr(ReturnExpr expr) {
        if (expr.getValue().isPresent()) {
            expr.getValue().get().accept(this);
        }
        return null;
    }
    
    // Trivial visitors
    @Override public Void visitLiteralExpr(LiteralExpr expr) { return null; }
    @Override public Void visitArrayLiteralExpr(ArrayLiteralExpr expr) {
        for (Expression elem : expr.getElements()) {
            elem.accept(this);
        }
        return null;
    }
    @Override public Void visitStructLiteralExpr(com.firefly.compiler.ast.expr.StructLiteralExpr expr) {
        for (com.firefly.compiler.ast.expr.StructLiteralExpr.FieldInit field : expr.getFieldInits()) {
            field.getValue().accept(this);
        }
        return null;
    }
    @Override public Void visitMapLiteralExpr(com.firefly.compiler.ast.expr.MapLiteralExpr expr) {
        for (var entry : expr.getEntries().entrySet()) {
            entry.getKey().accept(this);
            entry.getValue().accept(this);
        }
        return null;
    }
    @Override public Void visitUnaryExpr(UnaryExpr expr) {
        expr.getOperand().accept(this);
        return null;
    }
    @Override public Void visitIndexAccessExpr(IndexAccessExpr expr) { return null; }
    @Override public Void visitMatchExpr(MatchExpr expr) { return null; }
    @Override public Void visitLambdaExpr(LambdaExpr expr) { return null; }
    @Override public Void visitFunctionType(com.firefly.compiler.ast.type.FunctionType type) { return null; }
    @Override public Void visitPrimitiveType(com.firefly.compiler.ast.type.PrimitiveType type) { return null; }
    @Override public Void visitNamedType(com.firefly.compiler.ast.type.NamedType type) { return null; }
    @Override public Void visitArrayType(com.firefly.compiler.ast.type.ArrayType type) { return null; }
    @Override public Void visitOptionalType(com.firefly.compiler.ast.type.OptionalType type) { return null; }
    @Override public Void visitCoalesceExpr(com.firefly.compiler.ast.expr.CoalesceExpr expr) { return null; }
    @Override public Void visitConcurrentExpr(com.firefly.compiler.ast.expr.ConcurrentExpr expr) { return null; }
    @Override public Void visitRaceExpr(com.firefly.compiler.ast.expr.RaceExpr expr) { return null; }
    @Override public Void visitTimeoutExpr(com.firefly.compiler.ast.expr.TimeoutExpr expr) { return null; }
    @Override public Void visitNewExpr(com.firefly.compiler.ast.expr.NewExpr expr) { return null; }
    @Override public Void visitBreakExpr(com.firefly.compiler.ast.expr.BreakExpr expr) { return null; }
    @Override public Void visitContinueExpr(com.firefly.compiler.ast.expr.ContinueExpr expr) { return null; }
    @Override 
    public Void visitAssignmentExpr(com.firefly.compiler.ast.expr.AssignmentExpr expr) {
        // Analyze the value being assigned
        expr.getValue().accept(this);
        
        // Check if target is an identifier (variable assignment)
        if (expr.getTarget() instanceof IdentifierExpr) {
            String varName = ((IdentifierExpr) expr.getTarget()).getName();
            
            // Look up the variable to check if it's mutable
            SymbolInfo symbol = findSymbol(varName);
            if (symbol != null) {
                if (!symbol.isMutable) {
                    diagnostics.add(CompilerDiagnostic.error(
                        CompilerDiagnostic.Phase.SEMANTIC,
                        "Cannot assign to immutable variable: " + varName,
                        expr.getLocation(),
                        "Declare the variable with 'let mut' if you need to modify it"
                    ));
                }
            } else {
                // Variable not found - will be caught by visitIdentifierExpr
                expr.getTarget().accept(this);
            }
        } else {
            // For field access or index access, just visit the target
            expr.getTarget().accept(this);
        }
        
        return null;
    }
    @Override public Void visitPattern(com.firefly.compiler.ast.Pattern pattern) { return null; }
    @Override public Void visitTypeAliasDecl(com.firefly.compiler.ast.decl.TypeAliasDecl decl) { 
        // Type aliases are registered but don't need semantic analysis
        return null; 
    }
    
    @Override public Void visitExceptionDecl(com.firefly.compiler.ast.decl.ExceptionDecl decl) {
        // Register exception type
        symbolTable.put(decl.getName(), new SymbolInfo(decl.getName(), SymbolKind.CLASS));
        return null;
    }
    
    // ============ Scope Management ============
    
    private void pushScope() {
        scopeStack.push(new HashMap<>());
    }
    
    private void popScope() {
        if (!scopeStack.isEmpty()) {
            scopeStack.pop();
        }
    }
    
    private void addSymbol(String name, SymbolKind kind, SourceLocation location) {
        addSymbol(name, kind, false, location);
    }
    
    private void addSymbol(String name, SymbolKind kind, boolean isMutable, SourceLocation location) {
        if (!scopeStack.isEmpty()) {
            Map<String, SymbolInfo> currentScope = scopeStack.peek();
            if (currentScope.containsKey(name)) {
                diagnostics.add(CompilerDiagnostic.warning(
                    CompilerDiagnostic.Phase.SEMANTIC,
                    "Symbol '" + name + "' shadows previous declaration",
                    location
                ));
            }
            currentScope.put(name, new SymbolInfo(name, kind, isMutable));
        }
    }
    
    private boolean isSymbolInScope(String name) {
        // Check all scopes from innermost to outermost
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            if (scopeStack.get(i).containsKey(name)) {
                return true;
            }
        }
        return false;
    }
    
    private SymbolInfo findSymbol(String name) {
        // Check all scopes from innermost to outermost
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            if (scopeStack.get(i).containsKey(name)) {
                return scopeStack.get(i).get(name);
            }
        }
        // Check top-level symbols
        return symbolTable.get(name);
    }
    
    // ============ Symbol Information ============
    
    private enum SymbolKind {
        VARIABLE, FUNCTION, CLASS, PARAMETER
    }
    
    private static class SymbolInfo {
        final String name;
        final SymbolKind kind;
        final boolean isMutable;
        
        SymbolInfo(String name, SymbolKind kind) {
            this(name, kind, false);
        }
        
        SymbolInfo(String name, SymbolKind kind, boolean isMutable) {
            this.name = name;
            this.kind = kind;
            this.isMutable = isMutable;
        }
    }

    @Override
    public Void visitTupleType(TupleType type) {
        return null;
    }


    @Override
    public Void visitTypeParameter(TypeParameter type) {
        return null;
    }


    @Override
    public Void visitGenericType(GenericType type) {
        return null;
    }


    @Override
    public Void visitTupleLiteralExpr(TupleLiteralExpr expr) {
        return null;
    }


    @Override
    public Void visitThrowExpr(ThrowExpr expr) {
        return null;
    }


    @Override
    public Void visitTryExpr(TryExpr expr) {
        return null;
    }


    @Override
    public Void visitTupleAccessExpr(TupleAccessExpr expr) {
        return null;
    }
    
    @Override
    public Void visitAwaitExpr(com.firefly.compiler.ast.expr.AwaitExpr expr) {
        return null;
    }
    
    @Override
    public Void visitSafeAccessExpr(com.firefly.compiler.ast.expr.SafeAccessExpr expr) {
        expr.getObject().accept(this);
        return null;
    }
    
    @Override
    public Void visitForceUnwrapExpr(com.firefly.compiler.ast.expr.ForceUnwrapExpr expr) {
        expr.getExpression().accept(this);
        return null;
    }
}
