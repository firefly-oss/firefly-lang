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
    private final SymbolTable rootSymbolTable;
    private final ImportResolver importResolver;
    private boolean inAsyncContext;
    private int asyncDepth;

    // Inference contexts
    private TypeInference globalInference;
    private TypeInference currentInference; // set per class/method when needed
    
    public TypeChecker(DiagnosticReporter reporter, SymbolTable symbolTable) {
        this.reporter = reporter;
        this.rootSymbolTable = symbolTable;
        this.globalInference = new TypeInference(symbolTable, reporter);
        this.importResolver = new ImportResolver();
        this.inAsyncContext = false;
        this.asyncDepth = 0;
        this.currentInference = null;
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

    private TypeInference inf() {
        return currentInference != null ? currentInference : globalInference;
    }
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Process imports first
        for (UseDeclaration importDecl : unit.getImports()) {
            importDecl.accept(this);
        }
        
        // Then process declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitUseDeclaration(UseDeclaration decl) {
        // Process import and add to resolver
        importResolver.addImport(decl);
        return null;
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // Build a class-level scope chained to the root for type inference
        SymbolTable classScope = new SymbolTable(rootSymbolTable);

        // Predeclare fields in class scope
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            if (field.getType() == null) {
                reporter.error("TC011",
                    "Class field '" + field.getName() + "' must have an explicit type",
                    null);
            }
            try {
                classScope.define(field.getName(), field.getType(), SymbolTable.SymbolKind.FIELD, field.isMutable());
            } catch (SemanticException e) {
                // ignore duplicate here
            }
        }
        // Predeclare methods in class scope for resolution within bodies
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            Type rt = method.getReturnType().orElse(new PrimitiveType("Void"));
            try {
                classScope.define(method.getName(), rt, SymbolTable.SymbolKind.FUNCTION, false, method.isAsync());
            } catch (SemanticException e) {
                // ignore
            }
        }

        // Check field initializers with class scope inference
        TypeInference savedInf = currentInference;
        currentInference = new TypeInference(classScope, reporter);
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            if (field.getInitializer().isPresent()) {
                field.getInitializer().get().accept(this);
                Type initType = inf().inferType(field.getInitializer().get());
                if (!typesCompatible(field.getType(), initType)) {
                    reporter.error("TC012",
                        String.format("Field '%s' type mismatch: declared %s, but initialized with %s",
                            field.getName(), field.getType().getName(), initType.getName()),
                        null);
                }
            }
        }

        // Type check methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            boolean wasAsync = inAsyncContext;
            if (method.isAsync()) {
                inAsyncContext = true;
                asyncDepth++;
            }

            // Method scope extends class scope
            SymbolTable methodScope = new SymbolTable(classScope);
            for (FunctionDecl.Parameter p : method.getParameters()) {
                try {
                    methodScope.define(p.getName(), p.getType(), SymbolTable.SymbolKind.PARAMETER, p.isMutable());
                } catch (SemanticException e) {
                    // ignore
                }
            }
            // Set inference for this method
            currentInference = new TypeInference(methodScope, reporter);

            // Type check method body
            method.getBody().accept(this);

            // Validate return type if specified
            if (method.getReturnType().isPresent()) {
                Type bodyType = inf().inferType(method.getBody());
                if (!typesCompatible(method.getReturnType().get(), bodyType)) {
                    if (method.isAsync()) {
                        // In Alpha, be lenient with async return mismatches (treat as warning)
                        reporter.warning("TC013",
                            String.format("Method '%s' return type mismatch: declared %s, but returns %s",
                                method.getName(),
                                method.getReturnType().get().getName(),
                                bodyType.getName()),
                            method.getBody().getLocation());
                    } else {
                        reporter.error("TC013",
                            String.format("Method '%s' return type mismatch: declared %s, but returns %s",
                                method.getName(),
                                method.getReturnType().get().getName(),
                                bodyType.getName()),
                            method.getBody().getLocation());
                    }
                }
            }

            if (method.isAsync()) {
                asyncDepth--;
            }
            inAsyncContext = wasAsync;
        }

        // Restore inference
        currentInference = savedInf;

        // Type check constructor if present
        if (decl.getConstructor().isPresent()) {
            ClassDecl.ConstructorDecl constructor = decl.getConstructor().get();
            constructor.getBody().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Type check interface method signatures
        return null;
    }
    @Override
    public Void visitActorDecl(ActorDecl decl) {
        // Type check fields
        for (FieldDecl field : decl.getFields()) {
            // Fields should have types declared
            if (field.getType() == null) {
                reporter.error("TC010",
                    "Actor field '" + field.getName() + "' must have an explicit type",
                    field.getLocation());
            }
        }
        
        // Type check init block
        if (decl.getInitBlock() != null) {
            decl.getInitBlock().accept(this);
        }
        
        // Type check receive cases
        for (ActorDecl.ReceiveCase receiveCase : decl.getReceiveCases()) {
            // Type check the handler expression
            receiveCase.getExpression().accept(this);
            
            // Validate that message patterns match declared message types
            Pattern pattern = receiveCase.getPattern();
            Type patternType = inferPatternType(pattern);
            
            // For now, just verify pattern is valid
            // Full implementation would check against actor's declared message types
            if (patternType == null) {
                reporter.warning("TC014",
                    "Cannot infer message pattern type",
                    receiveCase.getLocation());
            }
        }
        
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
Type inferredType = globalInference.inferFunctionType(decl);
        
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
    @Override public Void visitSparkDecl(SparkDecl decl) { return null; }
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
                Type exprType = inf().inferType(binding.getExpression());
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
        Type durationType = inf().inferType(expr.getDuration());
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
    
    @Override public Void visitSafeAccessExpr(SafeAccessExpr expr) {
        // Type check the object
        expr.getObject().accept(this);
        Type objectType = inf().inferType(expr.getObject());
        
        // Safe access should only be used on optional types
        // For now, we allow it on any type (runtime null check)
        // Full implementation would check if type is Optional<T>
        
        return null;
    }
    
    @Override public Void visitForceUnwrapExpr(ForceUnwrapExpr expr) {
        // Type check the expression
        expr.getExpression().accept(this);
        Type exprType = inf().inferType(expr.getExpression());
        
        // Force unwrap should only be used on optional types
        // Warning if used on non-optional type
        if (!isOptionalType(exprType)) {
            reporter.warning("TC020",
                "Force unwrap (!!) used on non-optional type " + exprType.getName(),
                expr.getLocation());
        }
        
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
    
    @Override public Void visitMapLiteralExpr(com.firefly.compiler.ast.expr.MapLiteralExpr expr) {
        for (var entry : expr.getEntries().entrySet()) {
            entry.getKey().accept(this);
            entry.getValue().accept(this);
        }
        return null;
    }
    
    @Override public Void visitAssignmentExpr(AssignmentExpr expr) {
        // Type check assignment
        expr.getTarget().accept(this);
        expr.getValue().accept(this);
        
        // Verify target is mutable
        if (expr.getTarget() instanceof IdentifierExpr) {
            String targetName = ((IdentifierExpr) expr.getTarget()).getName();
            
            // Look up variable in type inference's symbol table
            // For now, we'll just warn - full implementation would track mutability
            // through the symbol table
        } else if (expr.getTarget() instanceof FieldAccessExpr) {
            // Field assignments should check if field is mutable
            // Full implementation would query struct/class metadata
        }
        
        // Validate type compatibility
        Type targetType = inf().inferType(expr.getTarget());
        Type valueType = inf().inferType(expr.getValue());
        
        if (!typesCompatible(targetType, valueType)) {
            reporter.error("TC015",
                String.format("Assignment type mismatch: cannot assign %s to %s",
                    valueType.getName(), targetType.getName()),
                expr.getLocation());
        }
        
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
        // Simplified type compatibility check with Any as top type
        if (expected == null || actual == null) return true;
        String e = expected.getName();
        String a = actual.getName();
        if ("Any".equals(e) || "Any".equals(a)) return true;
        return e.equals(a);
    }
    
    private boolean isNumericType(Type type) {
        String name = type.getName();
        return name.equals("Int") || name.equals("Float");
    }
    
    private boolean isOptionalType(Type type) {
        return type instanceof OptionalType || type.getName().endsWith("?");
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
    
    /**
     * Infer the type of a pattern (for actor message validation).
     */
    private Type inferPatternType(Pattern pattern) {
        if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            // Variable patterns can match any type
            return new PrimitiveType("Any");
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.StructPattern) {
            // Struct pattern type is the type name
            com.firefly.compiler.ast.pattern.StructPattern structPattern = 
                (com.firefly.compiler.ast.pattern.StructPattern) pattern;
            return new NamedType(structPattern.getTypeName());
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.LiteralPattern) {
            // Literal patterns have specific types
            com.firefly.compiler.ast.pattern.LiteralPattern litPattern = 
                (com.firefly.compiler.ast.pattern.LiteralPattern) pattern;
            LiteralExpr literal = litPattern.getLiteral();
            Object value = literal.getValue();
            if (value instanceof Integer) {
                return new PrimitiveType("Int");
            } else if (value instanceof Double) {
                return new PrimitiveType("Float");
            } else if (value instanceof String) {
                return new PrimitiveType("String");
            } else if (value instanceof Boolean) {
                return new PrimitiveType("Bool");
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.TuplePattern) {
            // Tuple patterns - would need to infer element types
            return new PrimitiveType("Tuple");
        }
        return null;
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
    public Void visitStructLiteralExpr(StructLiteralExpr expr) {
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
    public Void visitTypeAliasDecl(com.firefly.compiler.ast.decl.TypeAliasDecl decl) {
        return null;
    }
    
    @Override
    public Void visitExceptionDecl(com.firefly.compiler.ast.decl.ExceptionDecl decl) {
        return null;
    }
    
    @Override
    public Void visitAwaitExpr(com.firefly.compiler.ast.expr.AwaitExpr expr) {
        // Validate await usage
        if (!inAsyncContext) {
            reporter.error("TC002",
                "await can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the enclosing function");
        }
        
        expr.getFuture().accept(this);
        return null;
    }
}
