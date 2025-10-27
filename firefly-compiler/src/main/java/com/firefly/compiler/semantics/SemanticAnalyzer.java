package com.firefly.compiler.semantics;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.codegen.TypeResolver;
import com.firefly.compiler.diagnostics.CompilerDiagnostic;

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
    private CompilationUnit currentUnit;
    private boolean inClassContext = false;
    private String currentClassName = null;
    
    public SemanticAnalyzer(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
        this.diagnostics = new ArrayList<>();
        this.symbolTable = new HashMap<>();
        this.scopeStack = new Stack<>();
    }
    
    public List<CompilerDiagnostic> analyze(CompilationUnit unit) {
        diagnostics.clear();
        symbolTable.clear();
        scopeStack.clear();
        currentUnit = unit;
        
        unit.accept(this);
        return diagnostics;
    }
    
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(CompilerDiagnostic::isError);
    }
    
    // ============ Compilation Unit ============
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Phase 1: Validate all imports
        for (ImportDeclaration importDecl : unit.getImports()) {
            validateImport(importDecl);
        }
        
        // Phase 2: Collect top-level declarations
        for (Declaration decl : unit.getDeclarations()) {
            if (decl instanceof FunctionDecl) {
                FunctionDecl funcDecl = (FunctionDecl) decl;
                symbolTable.put(funcDecl.getName(), new SymbolInfo(funcDecl.getName(), SymbolKind.FUNCTION));
            } else if (decl instanceof ClassDecl) {
                ClassDecl classDecl = (ClassDecl) decl;
                symbolTable.put(classDecl.getName(), new SymbolInfo(classDecl.getName(), SymbolKind.CLASS));
            }
        }
        
        // Phase 3: Analyze declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        
        return null;
    }
    
    private void validateImport(ImportDeclaration importDecl) {
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
    public Void visitImportDeclaration(ImportDeclaration decl) {
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
        
        // Analyze class members
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            pushScope();
            
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
    
    @Override public Void visitStructDecl(StructDecl decl) { return null; }
    @Override public Void visitDataDecl(DataDecl decl) { return null; }
    @Override public Void visitTraitDecl(TraitDecl decl) { return null; }
    @Override public Void visitImplDecl(ImplDecl decl) { return null; }
    
    // ============ Statements ============
    
    @Override
    public Void visitLetStatement(LetStatement stmt) {
        // Analyze initializer first
        if (stmt.getInitializer().isPresent()) {
            stmt.getInitializer().get().accept(this);
        }
        
        // Register variable in current scope
        if (stmt.getPattern() instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            com.firefly.compiler.ast.pattern.VariablePattern varPattern =
                (com.firefly.compiler.ast.pattern.VariablePattern) stmt.getPattern();
            addSymbol(varPattern.getName(), SymbolKind.VARIABLE, stmt.getLocation());
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
        return null;
    }
    
    @Override
    public Void visitCallExpr(CallExpr expr) {
        // Special handling for static method calls (ClassName.method())
        if (expr.getFunction() instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expr.getFunction();
            
            if (fieldAccess.getObject() instanceof IdentifierExpr) {
                String className = ((IdentifierExpr) fieldAccess.getObject()).getName();
                
                // Check if it's a class name (static call)
                Optional<String> resolvedClass = typeResolver.resolveClassName(className);
                if (resolvedClass.isPresent()) {
                    // This is a static method call - don't visit the class name as a variable
                    // Just analyze the arguments
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
            stmt.accept(this);
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
        if (expr.getPattern() instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
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
    @Override public Void visitAssignmentExpr(com.firefly.compiler.ast.expr.AssignmentExpr expr) { return null; }
    @Override public Void visitPattern(com.firefly.compiler.ast.Pattern pattern) { return null; }
    
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
        if (!scopeStack.isEmpty()) {
            Map<String, SymbolInfo> currentScope = scopeStack.peek();
            if (currentScope.containsKey(name)) {
                diagnostics.add(CompilerDiagnostic.warning(
                    CompilerDiagnostic.Phase.SEMANTIC,
                    "Symbol '" + name + "' shadows previous declaration",
                    location
                ));
            }
            currentScope.put(name, new SymbolInfo(name, kind));
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
    
    // ============ Symbol Information ============
    
    private enum SymbolKind {
        VARIABLE, FUNCTION, CLASS, PARAMETER
    }
    
    private static class SymbolInfo {
        final String name;
        final SymbolKind kind;
        
        SymbolInfo(String name, SymbolKind kind) {
            this.name = name;
            this.kind = kind;
        }
    }
}
