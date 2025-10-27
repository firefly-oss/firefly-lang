package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

/**
 * Builds the symbol table by traversing the AST and collecting declarations.
 * This is the first pass before type checking.
 */
public class SymbolTableBuilder implements AstVisitor<Void> {
    
    private SymbolTable currentScope;
    private final SymbolTable globalScope;
    private final DiagnosticReporter reporter;
    
    public SymbolTableBuilder(DiagnosticReporter reporter) {
        this.reporter = reporter;
        this.globalScope = new SymbolTable();
        this.currentScope = globalScope;
    }
    
    /**
     * Build symbol table for a compilation unit.
     */
    public SymbolTable build(CompilationUnit unit) {
        unit.accept(this);
        return globalScope;
    }
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        // First pass: collect all top-level declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitImportDeclaration(ImportDeclaration decl) {
        // TODO: Resolve imports and add to symbol table
        return null;
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // TODO: Implement class symbol table building for v0.3.0
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Interfaces don't have implementation, just signatures
        return null;
    }
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        // Define function in current scope
        Type returnType = decl.getReturnType().orElse(new PrimitiveType("Unit"));
        
        try {
            currentScope.define(
                decl.getName(),
                returnType,
                SymbolTable.SymbolKind.FUNCTION,
                false,
                decl.isAsync()
            );
        } catch (SemanticException e) {
            reporter.error("STB001",
                "Function '" + decl.getName() + "' is already defined in this scope",
                decl.getLocation(),
                "Rename this function or remove the duplicate");
        }
        
        // Enter function scope for parameters and body
        currentScope = currentScope.enterScope();
        
        // Add parameters to function scope
        for (FunctionDecl.Parameter param : decl.getParameters()) {
            try {
                currentScope.define(
                    param.getName(),
                    param.getType(),
                    SymbolTable.SymbolKind.PARAMETER,
                    param.isMutable()
                );
            } catch (SemanticException e) {
                reporter.error("STB002",
                    "Parameter '" + param.getName() + "' is already defined",
                    decl.getLocation());
            }
        }
        
        // Visit function body
        decl.getBody().accept(this);
        
        // Exit function scope
        currentScope = currentScope.exitScope();
        return null;
    }
    
    @Override
    public Void visitStructDecl(StructDecl decl) {
        try {
            currentScope.define(
                decl.getName(),
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.STRUCT,
                false
            );
        } catch (SemanticException e) {
            reporter.error("STB003",
                "Type '" + decl.getName() + "' is already defined in this scope",
                decl.getLocation());
        }
        return null;
    }
    
    @Override
    public Void visitDataDecl(DataDecl decl) {
        try {
            currentScope.define(
                decl.getName(),
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.DATA,
                false
            );
        } catch (SemanticException e) {
            reporter.error("STB004",
                "Type '" + decl.getName() + "' is already defined in this scope",
                decl.getLocation());
        }
        return null;
    }
    
    @Override
    public Void visitTraitDecl(TraitDecl decl) {
        try {
            currentScope.define(
                decl.getName(),
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.TRAIT,
                false
            );
        } catch (SemanticException e) {
            reporter.error("STB005",
                "Trait '" + decl.getName() + "' is already defined in this scope",
                decl.getLocation());
        }
        return null;
    }
    
    @Override
    public Void visitImplDecl(ImplDecl decl) {
        // Visit methods in impl block
        for (FunctionDecl method : decl.getMethods()) {
            method.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitLetStatement(LetStatement stmt) {
        // Define variable in current scope
        if (stmt.getInitializer().isPresent()) {
            // Visit initializer first
            stmt.getInitializer().get().accept(this);
            
            // Try to infer type from initializer
            // For now, use Unit as placeholder
            Type varType = new PrimitiveType("Unit");
            
            try {
                String varName = extractPatternName(stmt.getPattern());
                if (varName != null) {
                    currentScope.define(
                        varName,
                        varType,
                        SymbolTable.SymbolKind.VARIABLE,
                        stmt.isMutable()
                    );
                }
            } catch (SemanticException e) {
                reporter.error("STB006",
                    "Variable is already defined in this scope",
                    stmt.getLocation());
            }
        }
        return null;
    }
    
    @Override
    public Void visitExprStatement(ExprStatement stmt) {
        stmt.getExpression().accept(this);
        return null;
    }
    
    // Expression visitors - traverse to find nested declarations
    
    @Override
    public Void visitBinaryExpr(BinaryExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        return null;
    }
    
    @Override
    public Void visitUnaryExpr(UnaryExpr expr) {
        expr.getOperand().accept(this);
        return null;
    }
    
    @Override
    public Void visitCallExpr(CallExpr expr) {
        expr.getFunction().accept(this);
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitFieldAccessExpr(FieldAccessExpr expr) {
        expr.getObject().accept(this);
        return null;
    }
    
    @Override
    public Void visitIndexAccessExpr(IndexAccessExpr expr) {
        expr.getObject().accept(this);
        expr.getIndex().accept(this);
        return null;
    }
    
    @Override
    public Void visitLiteralExpr(LiteralExpr expr) {
        return null;
    }
    
    @Override
    public Void visitIdentifierExpr(IdentifierExpr expr) {
        return null;
    }
    
    @Override
    public Void visitIfExpr(IfExpr expr) {
        expr.getCondition().accept(this);
        
        // Enter new scope for then branch
        currentScope = currentScope.enterScope();
        expr.getThenBranch().accept(this);
        currentScope = currentScope.exitScope();
        
        // Else-if branches
        for (IfExpr.ElseIfBranch elseIf : expr.getElseIfBranches()) {
            elseIf.getCondition().accept(this);
            currentScope = currentScope.enterScope();
            elseIf.getBody().accept(this);
            currentScope = currentScope.exitScope();
        }
        
        // Else branch
        if (expr.getElseBranch().isPresent()) {
            currentScope = currentScope.enterScope();
            expr.getElseBranch().get().accept(this);
            currentScope = currentScope.exitScope();
        }
        
        return null;
    }
    
    @Override
    public Void visitMatchExpr(MatchExpr expr) {
        expr.getValue().accept(this);
        
        for (MatchExpr.MatchArm arm : expr.getArms()) {
            // Each arm gets its own scope for pattern bindings
            currentScope = currentScope.enterScope();
            
            if (arm.getGuard() != null) {
                arm.getGuard().accept(this);
            }
            arm.getBody().accept(this);
            
            currentScope = currentScope.exitScope();
        }
        
        return null;
    }
    
    @Override
    public Void visitBlockExpr(BlockExpr expr) {
        // Block creates new scope
        currentScope = currentScope.enterScope();
        
        for (Statement stmt : expr.getStatements()) {
            stmt.accept(this);
        }
        
        if (expr.getFinalExpression().isPresent()) {
            expr.getFinalExpression().get().accept(this);
        }
        
        currentScope = currentScope.exitScope();
        return null;
    }
    
    @Override
    public Void visitLambdaExpr(LambdaExpr expr) {
        // Lambda creates new scope for parameters
        currentScope = currentScope.enterScope();
        
        // Add parameters (simplified - just names for now)
        for (String param : expr.getParameters()) {
            try {
                currentScope.define(
                    param,
                    new PrimitiveType("Unit"), // Placeholder
                    SymbolTable.SymbolKind.PARAMETER,
                    false
                );
            } catch (SemanticException e) {
                // Ignore duplicate parameter errors in lambda
            }
        }
        
        expr.getBody().accept(this);
        
        currentScope = currentScope.exitScope();
        return null;
    }
    
    @Override
    public Void visitForExpr(ForExpr expr) {
        expr.getIterable().accept(this);
        
        // For body creates new scope with loop variable
        currentScope = currentScope.enterScope();
        expr.getBody().accept(this);
        currentScope = currentScope.exitScope();
        
        return null;
    }
    
    @Override
    public Void visitWhileExpr(WhileExpr expr) {
        expr.getCondition().accept(this);
        
        currentScope = currentScope.enterScope();
        expr.getBody().accept(this);
        currentScope = currentScope.exitScope();
        
        return null;
    }
    
    @Override
    public Void visitReturnExpr(ReturnExpr expr) {
        if (expr.getValue().isPresent()) {
            expr.getValue().get().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitBreakExpr(BreakExpr expr) {
        return null;
    }
    
    @Override
    public Void visitContinueExpr(ContinueExpr expr) {
        return null;
    }
    
    @Override
    public Void visitConcurrentExpr(ConcurrentExpr expr) {
        // Visit all binding expressions
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            binding.getExpression().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitRaceExpr(RaceExpr expr) {
        expr.getBody().accept(this);
        return null;
    }
    
    @Override
    public Void visitTimeoutExpr(TimeoutExpr expr) {
        expr.getDuration().accept(this);
        expr.getBody().accept(this);
        return null;
    }
    
    @Override
    public Void visitCoalesceExpr(CoalesceExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        return null;
    }
    
    @Override
    public Void visitAssignmentExpr(AssignmentExpr expr) {
        expr.getTarget().accept(this);
        expr.getValue().accept(this);
        return null;
    }
    
    public Void visitNewExpr(com.firefly.compiler.ast.expr.NewExpr expr) {
        // Visit constructor arguments
        for (com.firefly.compiler.ast.expr.Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return null;
    }
    
    public Void visitArrayLiteralExpr(com.firefly.compiler.ast.expr.ArrayLiteralExpr expr) {
        for (com.firefly.compiler.ast.expr.Expression element : expr.getElements()) {
            element.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitPattern(Pattern pattern) {
        return null;
    }
    
    @Override
    public Void visitPrimitiveType(PrimitiveType type) {
        return null;
    }
    
    @Override
    public Void visitNamedType(NamedType type) {
        return null;
    }
    
    @Override
    public Void visitOptionalType(OptionalType type) {
        return null;
    }
    
    @Override
    public Void visitArrayType(ArrayType type) {
        return null;
    }
    
    @Override
    public Void visitFunctionType(FunctionType type) {
        return null;
    }
    
    // Helper methods
    
    private String extractPatternName(Pattern pattern) {
        // Simplified pattern name extraction
        // TODO: Handle complex patterns
        if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            return ((com.firefly.compiler.ast.pattern.VariablePattern) pattern).getName();
        }
        return null;
    }
}
