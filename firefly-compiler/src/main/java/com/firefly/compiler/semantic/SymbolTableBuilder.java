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
    public Void visitUseDeclaration(UseDeclaration decl) {
        // Imports are processed separately during compilation
        // This is mainly for documentation purposes
        // In full implementation, would track import aliases
        return null;
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // Define class type in current scope
        try {
            currentScope.define(
                decl.getName(),
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.STRUCT,
                false
            );
        } catch (SemanticException e) {
            reporter.error("STB009",
                "Class '" + decl.getName() + "' is already defined in this scope",
                decl.getLocation());
        }
        
        // Enter class scope for methods and fields
        currentScope = currentScope.enterScope();
        
        // Add 'this' reference (implicit in Java/OOP)
        try {
            currentScope.define(
                "this",
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.PARAMETER,
                false
            );
        } catch (SemanticException e) {
            // this is always implicitly available
        }
        
        // Add fields to scope
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            try {
                currentScope.define(
                    field.getName(),
                    field.getType(),
                    SymbolTable.SymbolKind.FIELD,
                    field.isMutable()
                );
            } catch (SemanticException e) {
                reporter.error("STB010",
                    "Field '" + field.getName() + "' is already defined",
                    decl.getLocation());
            }
        }
        
        // Pre-declare methods in class scope for intra-class calls and recursion
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            Type returnType = method.getReturnType().orElse(new PrimitiveType("Void"));
            try {
                currentScope.define(
                    method.getName(),
                    returnType,
                    SymbolTable.SymbolKind.FUNCTION,
                    false,
                    method.isAsync()
                );
            } catch (SemanticException e) {
                reporter.error("STB015",
                    "Method '" + method.getName() + "' is already defined",
                    decl.getLocation());
            }
        }
        
        // Visit methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            // Enter method scope
            currentScope = currentScope.enterScope();
            
            // Add 'this' reference in method scope
            try {
                currentScope.define(
                    "this",
                    new NamedType(decl.getName()),
                    SymbolTable.SymbolKind.PARAMETER,
                    false
                );
            } catch (SemanticException e) {
                // this is always implicitly available
            }
            
            // Add method parameters
            for (FunctionDecl.Parameter param : method.getParameters()) {
                try {
                    currentScope.define(
                        param.getName(),
                        param.getType(),
                        SymbolTable.SymbolKind.PARAMETER,
                        param.isMutable()
                    );
                } catch (SemanticException e) {
                    reporter.error("STB011",
                        "Parameter '" + param.getName() + "' is already defined",
                        decl.getLocation());
                }
            }
            
            // Visit method body
            method.getBody().accept(this);
            
            // Exit method scope
            currentScope = currentScope.exitScope();
        }
        
        // Visit constructor if present
        if (decl.getConstructor().isPresent()) {
            ClassDecl.ConstructorDecl constructor = decl.getConstructor().get();
            
            // Enter constructor scope
            currentScope = currentScope.enterScope();
            
            // Add constructor parameters
            for (FunctionDecl.Parameter param : constructor.getParameters()) {
                try {
                    currentScope.define(
                        param.getName(),
                        param.getType(),
                        SymbolTable.SymbolKind.PARAMETER,
                        param.isMutable()
                    );
                } catch (SemanticException e) {
                    reporter.error("STB012",
                        "Parameter '" + param.getName() + "' is already defined",
                        decl.getLocation());
                }
            }
            
            // Visit constructor body
            constructor.getBody().accept(this);
            
            // Exit constructor scope
            currentScope = currentScope.exitScope();
        }
        
        // Exit class scope
        currentScope = currentScope.exitScope();
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Interfaces don't have implementation, just signatures
        return null;
    }
    
    @Override
    public Void visitActorDecl(ActorDecl decl) {
        // Define actor type in current scope
        try {
            currentScope.define(
                decl.getName(),
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.STRUCT,
                false
            );
        } catch (SemanticException e) {
            reporter.error("STB013",
                "Actor '" + decl.getName() + "' is already defined in this scope",
                decl.getLocation());
        }
        
        // Enter actor scope for fields and receive block
        currentScope = currentScope.enterScope();
        
        // Add 'self' reference (implicit in actor)
        try {
            currentScope.define(
                "self",
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.PARAMETER,
                false
            );
        } catch (SemanticException e) {
            // self is always implicitly available
        }
        
        // Add actor fields to scope
        for (FieldDecl field : decl.getFields()) {
            try {
                currentScope.define(
                    field.getName(),
                    field.getType(),
                    SymbolTable.SymbolKind.FIELD,
                    field.isMutable()
                );
            } catch (SemanticException e) {
                reporter.error("STB014",
                    "Field '" + field.getName() + "' is already defined",
                    field.getLocation());
            }
        }
        
        // Visit init block
        if (decl.getInitBlock() != null) {
            decl.getInitBlock().accept(this);
        }
        
        // Visit receive cases
        for (ActorDecl.ReceiveCase receiveCase : decl.getReceiveCases()) {
            // Each case gets its own scope for pattern bindings
            currentScope = currentScope.enterScope();
            
            // Extract pattern variables
            extractPatternVariables(receiveCase.getPattern());
            
            // Visit case body
            receiveCase.getExpression().accept(this);
            
            // Exit case scope
            currentScope = currentScope.exitScope();
        }
        
        // Exit actor scope
        currentScope = currentScope.exitScope();
        return null;
    }
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        // Define function in current scope
        Type returnType = decl.getReturnType().orElse(new PrimitiveType("Void"));
        
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
    public Void visitSparkDecl(SparkDecl decl) {
        // Spark is like a struct but immutable with superpowers
        try {
            currentScope.define(
                decl.getName(),
                new NamedType(decl.getName()),
                SymbolTable.SymbolKind.STRUCT, // Use STRUCT kind for now
                false
            );
        } catch (SemanticException e) {
            reporter.error("STB008",
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
            Type varType = new PrimitiveType("Void");
            
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
                    new PrimitiveType("Void"), // Placeholder
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
        if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            return ((com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern).getName();
        }
        if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            return ((com.firefly.compiler.ast.pattern.VariablePattern) pattern).getName();
        }
        return null;
    }
    
    /**
     * Extract variables from a pattern and add them to the current scope.
     */
    private void extractPatternVariables(Pattern pattern) {
        if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            // Typed variable pattern
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern = 
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern;
            try {
                currentScope.define(
                    typedPattern.getName(),
                    typedPattern.getType(),
                    SymbolTable.SymbolKind.VARIABLE,
                    typedPattern.isMutable()
                );
            } catch (SemanticException e) {
                // Variable already defined, skip
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            // Simple variable pattern
            com.firefly.compiler.ast.pattern.VariablePattern varPattern = 
                (com.firefly.compiler.ast.pattern.VariablePattern) pattern;
            try {
                currentScope.define(
                    varPattern.getName(),
                    new PrimitiveType("Any"),
                    SymbolTable.SymbolKind.VARIABLE,
                    false
                );
            } catch (SemanticException e) {
                // Variable already defined, skip
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.StructPattern) {
            // Struct pattern: extract from field patterns
            com.firefly.compiler.ast.pattern.StructPattern structPattern = 
                (com.firefly.compiler.ast.pattern.StructPattern) pattern;
            for (com.firefly.compiler.ast.pattern.StructPattern.FieldPattern fieldPattern : structPattern.getFields()) {
                if (fieldPattern.getPattern() != null) {
                    extractPatternVariables(fieldPattern.getPattern());
                }
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.TuplePattern) {
            // Tuple pattern: extract from each element
            com.firefly.compiler.ast.pattern.TuplePattern tuplePattern = 
                (com.firefly.compiler.ast.pattern.TuplePattern) pattern;
            for (Pattern elementPattern : tuplePattern.getElements()) {
                extractPatternVariables(elementPattern);
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.ArrayPattern) {
            // Array pattern: extract from each element
            com.firefly.compiler.ast.pattern.ArrayPattern arrayPattern = 
                (com.firefly.compiler.ast.pattern.ArrayPattern) pattern;
            for (Pattern elementPattern : arrayPattern.getElements()) {
                extractPatternVariables(elementPattern);
            }
        }
        // Literal patterns don't introduce variables
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
    public Void visitStructLiteralExpr(com.firefly.compiler.ast.expr.StructLiteralExpr expr) {
        for (com.firefly.compiler.ast.expr.StructLiteralExpr.FieldInit field : expr.getFieldInits()) {
            field.getValue().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitMapLiteralExpr(com.firefly.compiler.ast.expr.MapLiteralExpr expr) {
        for (var entry : expr.getEntries().entrySet()) {
            entry.getKey().accept(this);
            entry.getValue().accept(this);
        }
        return null;
    }


    
    @Override
    public Void visitTypeAliasDecl(com.firefly.compiler.ast.decl.TypeAliasDecl decl) {
        return null;
    }
    
    @Override
    public Void visitExceptionDecl(com.firefly.compiler.ast.decl.ExceptionDecl decl) {
        // Exception declarations register as types
        // Just like class/struct, don't need special symbol table handling here
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
