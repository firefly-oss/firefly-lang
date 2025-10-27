package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.HashSet;
import java.util.Set;

/**
 * Comprehensive type checker with concurrency validation.
 * Validates type correctness and async context usage.
 */
public class TypeChecker implements AstVisitor<Void> {
    
    private final DiagnosticReporter reporter;
    private final TypeInference typeInference;
    private final ImportResolver importResolver;
    private boolean inAsyncContext;
    private int asyncDepth;
    
    public TypeChecker(DiagnosticReporter reporter, SymbolTable symbolTable) {
        this.reporter = reporter;
        this.typeInference = new TypeInference(symbolTable, reporter);
        this.importResolver = new ImportResolver();
        this.inAsyncContext = false;
        this.asyncDepth = 0;
    }
    
    /**
     * Get the import resolver for use in bytecode generation
     */
    public ImportResolver getImportResolver() {
        return importResolver;
    }
    
    /**
     * Type check the entire compilation unit.
     */
    public void check(CompilationUnit unit) {
        unit.accept(this);
    }
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Process imports first
        for (ImportDeclaration importDecl : unit.getImports()) {
            importDecl.accept(this);
        }
        
        // Then process declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitImportDeclaration(ImportDeclaration decl) {
        // Process import and add to resolver
        importResolver.addImport(decl);
        return null;
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // TODO: Implement class type checking for v0.3.0
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Type check interface method signatures
        return null;
    }
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        boolean wasAsync = inAsyncContext;
        if (decl.isAsync()) {
            inAsyncContext = true;
            asyncDepth++;
        }
        
        // Infer type of entire function declaration (which handles parameter scopes)
        Type inferredType = typeInference.inferFunctionType(decl);
        
        // Type check body (for validation, not type inference)
        decl.getBody().accept(this);
        
        // Validate return type matches
        if (decl.getReturnType().isPresent()) {
            Type declaredType = decl.getReturnType().get();
            
            if (!typesCompatible(declaredType, inferredType)) {
                reporter.error("TC001",
                    String.format("Function return type mismatch: declared %s, but body returns %s",
                        declaredType.getName(), inferredType.getName()),
                    decl.getLocation());
            }
        }
        
        if (decl.isAsync()) {
            asyncDepth--;
        }
        inAsyncContext = wasAsync;
        return null;
    }
    
    @Override public Void visitStructDecl(StructDecl decl) { return null; }
    @Override public Void visitDataDecl(DataDecl decl) { return null; }
    @Override public Void visitTraitDecl(TraitDecl decl) { return null; }
    @Override public Void visitImplDecl(ImplDecl decl) { return null; }
    @Override public Void visitLetStatement(LetStatement stmt) { return null; }
    @Override public Void visitExprStatement(ExprStatement stmt) {
        stmt.getExpression().accept(this);
        return null;
    }
    
    @Override public Void visitBinaryExpr(BinaryExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        return null;
    }
    
    @Override public Void visitUnaryExpr(UnaryExpr expr) {
        expr.getOperand().accept(this);
        
        // Validate await usage
        if (expr.getOperator() == UnaryExpr.UnaryOp.AWAIT && !inAsyncContext) {
            reporter.error("TC002",
                "await can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the enclosing function");
        }
        
        return null;
    }
    
    @Override public Void visitCallExpr(CallExpr expr) {
        expr.getFunction().accept(this);
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return null;
    }
    
    @Override public Void visitFieldAccessExpr(FieldAccessExpr expr) {
        expr.getObject().accept(this);
        return null;
    }
    
    @Override public Void visitIndexAccessExpr(IndexAccessExpr expr) {
        expr.getObject().accept(this);
        expr.getIndex().accept(this);
        return null;
    }
    
    @Override public Void visitLiteralExpr(LiteralExpr expr) { return null; }
    @Override public Void visitIdentifierExpr(IdentifierExpr expr) { return null; }
    
    @Override public Void visitIfExpr(IfExpr expr) {
        expr.getCondition().accept(this);
        expr.getThenBranch().accept(this);
        
        for (IfExpr.ElseIfBranch elseIf : expr.getElseIfBranches()) {
            elseIf.getCondition().accept(this);
            elseIf.getBody().accept(this);
        }
        
        if (expr.getElseBranch().isPresent()) {
            expr.getElseBranch().get().accept(this);
        }
        
        return null;
    }
    
    @Override public Void visitMatchExpr(MatchExpr expr) {
        expr.getValue().accept(this);
        
        for (MatchExpr.MatchArm arm : expr.getArms()) {
            if (arm.getGuard() != null) {
                arm.getGuard().accept(this);
            }
            arm.getBody().accept(this);
        }
        
        return null;
    }
    
    @Override public Void visitBlockExpr(BlockExpr expr) {
        for (Statement stmt : expr.getStatements()) {
            stmt.accept(this);
        }
        
        if (expr.getFinalExpression().isPresent()) {
            expr.getFinalExpression().get().accept(this);
        }
        
        return null;
    }
    
    @Override public Void visitLambdaExpr(LambdaExpr expr) {
        expr.getBody().accept(this);
        return null;
    }
    
    @Override public Void visitForExpr(ForExpr expr) {
        expr.getIterable().accept(this);
        expr.getBody().accept(this);
        return null;
    }
    
    @Override public Void visitWhileExpr(WhileExpr expr) {
        expr.getCondition().accept(this);
        expr.getBody().accept(this);
        return null;
    }
    
    @Override public Void visitReturnExpr(ReturnExpr expr) {
        if (expr.getValue().isPresent()) {
            expr.getValue().get().accept(this);
        }
        return null;
    }
    
    @Override public Void visitBreakExpr(BreakExpr expr) {
        // Break validation is done in codegen
        return null;
    }
    
    @Override public Void visitContinueExpr(ContinueExpr expr) {
        // Continue validation is done in codegen
        return null;
    }
    
    // ============ Concurrency Expressions ============
    
    @Override
    public Void visitConcurrentExpr(ConcurrentExpr expr) {
        if (!inAsyncContext) {
            reporter.error("TC003",
                "concurrent expression can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the enclosing function");
        }
        
        // Validate all bindings
        Set<String> bindingNames = new HashSet<>();
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            // Check for duplicate names
            if (bindingNames.contains(binding.getName())) {
                reporter.error("TC004",
                    "Duplicate binding name in concurrent block: " + binding.getName(),
                    expr.getLocation());
            }
            bindingNames.add(binding.getName());
            
            // Type check the expression
            binding.getExpression().accept(this);
            
            // Validate it's an awaitable expression
            Type exprType = typeInference.inferType(binding.getExpression());
            validateAwaitableExpression(binding.getExpression(), exprType);
        }
        
        return null;
    }
    
    @Override
    public Void visitRaceExpr(RaceExpr expr) {
        if (!inAsyncContext) {
            reporter.error("TC005",
                "race expression can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the enclosing function");
        }
        
        // Type check the body
        expr.getBody().accept(this);
        
        // Validate that all expressions in the body are awaitable
        BlockExpr body = expr.getBody();
        validateRaceBody(body);
        
        return null;
    }
    
    @Override
    public Void visitTimeoutExpr(TimeoutExpr expr) {
        if (!inAsyncContext) {
            reporter.error("TC006",
                "timeout expression can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the enclosing function");
        }
        
        // Check duration is numeric
        expr.getDuration().accept(this);
        Type durationType = typeInference.inferType(expr.getDuration());
        if (!isNumericType(durationType)) {
            reporter.error("TC007",
                "timeout duration must be a numeric type (Int), got " + durationType.getName(),
                expr.getDuration().getLocation());
        }
        
        // Type check body
        expr.getBody().accept(this);
        
        return null;
    }
    
    @Override public Void visitCoalesceExpr(CoalesceExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        return null;
    }
    
    @Override public Void visitNewExpr(com.firefly.compiler.ast.expr.NewExpr expr) {
        // Visit constructor arguments
        for (com.firefly.compiler.ast.expr.Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return null;
    }
    
    @Override public Void visitArrayLiteralExpr(com.firefly.compiler.ast.expr.ArrayLiteralExpr expr) {
        for (com.firefly.compiler.ast.expr.Expression element : expr.getElements()) {
            element.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitAssignmentExpr(AssignmentExpr expr) {
        // Type check assignment
        expr.getTarget().accept(this);
        expr.getValue().accept(this);
        // TODO: Verify target is mutable
        return null;
    }
    
    @Override public Void visitPattern(Pattern pattern) { return null; }
    @Override public Void visitPrimitiveType(PrimitiveType type) { return null; }
    @Override public Void visitNamedType(NamedType type) { return null; }
    @Override public Void visitOptionalType(OptionalType type) { return null; }
    @Override public Void visitArrayType(ArrayType type) { return null; }
    @Override public Void visitFunctionType(FunctionType type) { return null; }
    
    // ============ Helper Methods ============
    
    private boolean typesCompatible(Type expected, Type actual) {
        // Simplified type compatibility check
        return expected.getName().equals(actual.getName());
    }
    
    private boolean isNumericType(Type type) {
        String name = type.getName();
        return name.equals("Int") || name.equals("Float");
    }
    
    private void validateAwaitableExpression(Expression expr, Type type) {
        // For now, just check that it's an await expression
        // In full implementation, would check for Future/Promise types
        if (!(expr instanceof UnaryExpr)) {
            reporter.warning("TC010",
                "Expression in concurrent block should end with .await",
                expr.getLocation());
        }
    }
    
    private void validateRaceBody(BlockExpr body) {
        // Ensure all expressions in race body are async operations
        for (Statement stmt : body.getStatements()) {
            if (stmt instanceof ExprStatement) {
                Expression expr = ((ExprStatement) stmt).getExpression();
                // Should be an await expression
                if (!(expr instanceof UnaryExpr && 
                      ((UnaryExpr) expr).getOperator() == UnaryExpr.UnaryOp.AWAIT)) {
                    reporter.warning("TC011",
                        "Expressions in race block should be async operations with .await",
                        expr.getLocation());
                }
            }
        }
    }
}
