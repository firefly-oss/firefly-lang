package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Type inference engine for Firefly.
 * Implements Hindley-Milner style type inference.
 */
public class TypeInference implements AstVisitor<Type> {
    
    private final SymbolTable globalScope;
    private SymbolTable currentScope;
    private final Map<AstNode, Type> typeMap;
    private final DiagnosticReporter reporter;
    
    public TypeInference(SymbolTable symbolTable, DiagnosticReporter reporter) {
        this.globalScope = symbolTable;
        this.currentScope = symbolTable;
        this.typeMap = new HashMap<>();
        this.reporter = reporter;
    }
    
    /**
     * Infer the type of an expression.
     */
    public Type inferType(Expression expr) {
        Type type = expr.accept(this);
        typeMap.put(expr, type);
        return type;
    }
    
    /**
     * Infer the type of a function (handles parameter scoping).
     */
    public Type inferFunctionType(FunctionDecl decl) {
        return decl.accept(this);
    }
    
    /**
     * Get the inferred type for a node (if available).
     */
    public Optional<Type> getInferredType(AstNode node) {
        return Optional.ofNullable(typeMap.get(node));
    }
    
    // ============ Compilation Unit ============
    
    @Override
    public Type visitCompilationUnit(CompilationUnit unit) {
        // Process declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitImportDeclaration(ImportDeclaration decl) {
        return new PrimitiveType("Unit");
    }
    
    // ============ Declarations ============
    
    @Override
    public Type visitClassDecl(ClassDecl decl) {
        // TODO: Implement class type inference for v0.3.0
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitInterfaceDecl(InterfaceDecl decl) {
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitFunctionDecl(FunctionDecl decl) {
        // Enter function scope to access parameters
        currentScope = currentScope.enterScope();
        
        // Add parameters to scope (same as SymbolTableBuilder)
        for (FunctionDecl.Parameter param : decl.getParameters()) {
            try {
                currentScope.define(
                    param.getName(),
                    param.getType(),
                    SymbolTable.SymbolKind.PARAMETER,
                    param.isMutable()
                );
            } catch (SemanticException e) {
                // Parameter already defined, skip (error reported by SymbolTableBuilder)
            }
        }
        
        // Infer type from body in this scope
        Type bodyType = decl.getBody().accept(this);
        typeMap.put(decl, bodyType);
        
        // Exit function scope
        currentScope = currentScope.exitScope();
        
        // If return type is explicitly specified, use it
        if (decl.getReturnType().isPresent()) {
            return decl.getReturnType().get();
        }
        
        return bodyType;
    }
    
    @Override
    public Type visitStructDecl(StructDecl decl) {
        return new NamedType(decl.getName());
    }
    
    @Override
    public Type visitDataDecl(DataDecl decl) {
        return new NamedType(decl.getName());
    }
    
    @Override
    public Type visitTraitDecl(TraitDecl decl) {
        return new NamedType(decl.getName());
    }
    
    @Override
    public Type visitImplDecl(ImplDecl decl) {
        return new PrimitiveType("Unit");
    }
    
    // ============ Statements ============
    
    @Override
    public Type visitLetStatement(LetStatement stmt) {
        Optional<Expression> init = stmt.getInitializer();
        if (init.isPresent()) {
            Type initType = init.get().accept(this);
            typeMap.put(stmt, initType);
            
            // Add variable to current scope
            String varName = extractPatternName(stmt.getPattern());
            if (varName != null) {
                try {
                    currentScope.define(
                        varName,
                        initType,
                        SymbolTable.SymbolKind.VARIABLE,
                        stmt.isMutable()
                    );
                } catch (SemanticException e) {
                    // Variable already defined, error would be reported by SymbolTableBuilder
                }
            }
            
            return initType;
        }
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitExprStatement(ExprStatement stmt) {
        return stmt.getExpression().accept(this);
    }
    
    // ============ Expressions ============
    
    @Override
    public Type visitBinaryExpr(BinaryExpr expr) {
        Type leftType = expr.getLeft().accept(this);
        Type rightType = expr.getRight().accept(this);
        
        switch (expr.getOperator()) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case MODULO:
                // Arithmetic operators: require numeric types
                if (isNumericType(leftType) && isNumericType(rightType)) {
                    return promoteNumericType(leftType, rightType);
                }
                reporter.error(
                    "TI002",
                    String.format("Arithmetic operator '%s' requires numeric types, got %s and %s",
                        expr.getOperator(), leftType.getName(), rightType.getName()),
                    expr.getLocation(),
                    "Expected Int or Float types for arithmetic operations"
                );
                return new PrimitiveType("Unit");
                
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_EQUAL:
            case GREATER_THAN:
            case GREATER_EQUAL:
                // Comparison operators: return Bool
                return new PrimitiveType("Bool");
                
            case AND:
            case OR:
                // Logical operators: require Bool, return Bool
                if (leftType.getName().equals("Bool") && rightType.getName().equals("Bool")) {
                    return new PrimitiveType("Bool");
                }
                throw new SemanticException(
                    String.format("Logical operator requires Bool types, got %s and %s",
                        leftType.getName(), rightType.getName())
                );
                
            default:
                throw new SemanticException("Unknown binary operator: " + expr.getOperator());
        }
    }
    
    @Override
    public Type visitUnaryExpr(UnaryExpr expr) {
        Type operandType = expr.getOperand().accept(this);
        
        switch (expr.getOperator()) {
            case MINUS:
                if (isNumericType(operandType)) {
                    return operandType;
                }
                throw new SemanticException("Unary minus requires numeric type, got " + operandType.getName());
                
            case NOT:
                if (operandType.getName().equals("Bool")) {
                    return new PrimitiveType("Bool");
                }
                throw new SemanticException("Logical NOT requires Bool type, got " + operandType.getName());
                
            case UNWRAP:
                if (operandType instanceof OptionalType) {
                    return ((OptionalType) operandType).getInnerType();
                }
                throw new SemanticException("Cannot unwrap non-optional type: " + operandType.getName());
                
            default:
                return operandType;
        }
    }
    
    @Override
    public Type visitCallExpr(CallExpr expr) {
        // If calling a function by name, look it up in the symbol table
        if (expr.getFunction() instanceof IdentifierExpr) {
            String funcName = ((IdentifierExpr) expr.getFunction()).getName();
            Optional<SymbolTable.Symbol> symbol = currentScope.lookup(funcName);
            if (symbol.isPresent() && symbol.get().getKind() == SymbolTable.SymbolKind.FUNCTION) {
                // Return the function's declared return type
                return symbol.get().getType();
            }
        }
        
        // Otherwise, infer from the function expression
        Type funcType = expr.getFunction().accept(this);
        
        if (funcType instanceof FunctionType) {
            FunctionType ft = (FunctionType) funcType;
            return ft.getReturnType();
        }
        
        // Default: return the function type itself (might be return type already)
        return funcType;
    }
    
    @Override
    public Type visitFieldAccessExpr(FieldAccessExpr expr) {
        Type objectType = expr.getObject().accept(this);
        // TODO: Look up field type in struct definition
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitIndexAccessExpr(IndexAccessExpr expr) {
        Type objectType = expr.getObject().accept(this);
        
        if (objectType instanceof ArrayType) {
            return ((ArrayType) objectType).getElementType();
        }
        
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitLiteralExpr(LiteralExpr expr) {
        switch (expr.getKind()) {
            case INTEGER:
                return new PrimitiveType("Int");
            case FLOAT:
                return new PrimitiveType("Float");
            case STRING:
                return new PrimitiveType("String");
            case CHAR:
                return new PrimitiveType("Char");
            case BOOLEAN:
                return new PrimitiveType("Bool");
            case NONE:
                return new OptionalType(new PrimitiveType("Unit"));
            default:
                throw new SemanticException("Unknown literal kind: " + expr.getKind());
        }
    }
    
    @Override
    public Type visitIdentifierExpr(IdentifierExpr expr) {
        Optional<SymbolTable.Symbol> symbol = currentScope.lookup(expr.getName());
        if (symbol.isPresent()) {
            return symbol.get().getType();
        }
        
        // Report error with location info
        reporter.error(
            "TI001",
            "Undefined identifier: '" + expr.getName() + "'",
            expr.getLocation(),
            "Make sure the variable or function is declared before use"
        );
        
        // Return Unit as fallback to continue type checking
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitIfExpr(IfExpr expr) {
        // Condition must be Bool
        Type conditionType = expr.getCondition().accept(this);
        if (!conditionType.getName().equals("Bool")) {
            throw new SemanticException("If condition must be Bool, got " + conditionType.getName());
        }
        
        // Then branch type
        Type thenType = expr.getThenBranch().accept(this);
        
        // If there's an else branch, both branches must have the same type
        Optional<BlockExpr> elseBranch = expr.getElseBranch();
        if (elseBranch.isPresent()) {
            Type elseType = elseBranch.get().accept(this);
            if (thenType.getName().equals(elseType.getName())) {
                return thenType;
            }
            // For now, return Unit if types don't match (should be union type)
            return new PrimitiveType("Unit");
        }
        
        // No else branch, if is a statement, returns Unit
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitMatchExpr(MatchExpr expr) {
        // Infer type from first arm (simplified)
        if (!expr.getArms().isEmpty()) {
            return expr.getArms().get(0).getBody().accept(this);
        }
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitBlockExpr(BlockExpr expr) {
        // Enter block scope
        currentScope = currentScope.enterScope();
        
        // Process statements
        for (Statement stmt : expr.getStatements()) {
            stmt.accept(this);
        }
        
        // Block type is the type of the final expression
        Optional<Expression> finalExpr = expr.getFinalExpression();
        Type resultType = new PrimitiveType("Unit");
        if (finalExpr.isPresent()) {
            resultType = finalExpr.get().accept(this);
        }
        
        // Exit block scope
        currentScope = currentScope.exitScope();
        
        return resultType;
    }
    
    @Override
    public Type visitLambdaExpr(LambdaExpr expr) {
        // Enter lambda scope
        currentScope = currentScope.enterScope();
        
        // Add parameters to scope
        for (String paramName : expr.getParameters()) {
            try {
                // For now, use Unit as parameter type (type inference would determine actual type)
                currentScope.define(
                    paramName,
                    new PrimitiveType("Unit"),
                    SymbolTable.SymbolKind.PARAMETER,
                    false
                );
            } catch (SemanticException e) {
                // Parameter already defined, skip
            }
        }
        
        // Infer return type from body
        Type returnType = expr.getBody().accept(this);
        
        // Exit lambda scope
        currentScope = currentScope.exitScope();
        
        // Create function type with parameter types
        return new FunctionType(java.util.Collections.emptyList(), returnType);
    }
    
    @Override
    public Type visitForExpr(ForExpr expr) {
        // Enter for loop scope
        currentScope = currentScope.enterScope();
        
        // Add loop variable to scope
        // TODO: Extract variable name from pattern and add to scope
        
        expr.getIterable().accept(this);
        expr.getBody().accept(this);
        
        // Exit for loop scope
        currentScope = currentScope.exitScope();
        
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitWhileExpr(WhileExpr expr) {
        Type conditionType = expr.getCondition().accept(this);
        if (!conditionType.getName().equals("Bool")) {
            throw new SemanticException("While condition must be Bool, got " + conditionType.getName());
        }
        expr.getBody().accept(this);
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitReturnExpr(ReturnExpr expr) {
        Optional<Expression> value = expr.getValue();
        if (value.isPresent()) {
            return value.get().accept(this);
        }
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitBreakExpr(BreakExpr expr) {
        // Break is a control flow expression with bottom type (never returns)
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitContinueExpr(ContinueExpr expr) {
        // Continue is a control flow expression with bottom type (never returns)
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitConcurrentExpr(ConcurrentExpr expr) {
        // Process all bindings and infer their types
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            binding.getExpression().accept(this);
        }
        // concurrent { } returns Unit for now (could be a tuple of results)
        return new PrimitiveType("Unit");
    }
    
    @Override
    public Type visitRaceExpr(RaceExpr expr) {
        // The race expression returns the type of the first completed operation
        // For now, infer from the body
        return expr.getBody().accept(this);
    }
    
    @Override
    public Type visitTimeoutExpr(TimeoutExpr expr) {
        // Timeout wraps the body result in an Optional (may timeout)
        Type bodyType = expr.getBody().accept(this);
        return new OptionalType(bodyType);
    }
    
    @Override
    public Type visitCoalesceExpr(CoalesceExpr expr) {
        Type leftType = expr.getLeft().accept(this);
        Type rightType = expr.getRight().accept(this);
        // Coalesce returns the non-null type
        if (leftType instanceof OptionalType) {
            return ((OptionalType) leftType).getInnerType();
        }
        return leftType;
    }
    
    @Override
    public Type visitAssignmentExpr(AssignmentExpr expr) {
        // Assignment returns the type of the value being assigned
        return expr.getValue().accept(this);
    }
    
    public Type visitNewExpr(com.firefly.compiler.ast.expr.NewExpr expr) {
        // Return the type being instantiated
        return expr.getType();
    }
    
    public Type visitArrayLiteralExpr(com.firefly.compiler.ast.expr.ArrayLiteralExpr expr) {
        // Return List type (we generate ArrayList bytecode)
        return new com.firefly.compiler.ast.type.NamedType("List");
    }
    
    // ============ Patterns ============
    
    @Override
    public Type visitPattern(Pattern pattern) {
        // Pattern type inference is context-dependent
        return new PrimitiveType("Unit");
    }
    
    // ============ Types ============
    
    @Override
    public Type visitPrimitiveType(PrimitiveType type) {
        return type;
    }
    
    @Override
    public Type visitNamedType(NamedType type) {
        return type;
    }
    
    @Override
    public Type visitOptionalType(OptionalType type) {
        return type;
    }
    
    @Override
    public Type visitArrayType(ArrayType type) {
        return type;
    }
    
    @Override
    public Type visitFunctionType(FunctionType type) {
        return type;
    }
    
    // ============ Helper Methods ============
    
    private boolean isNumericType(Type type) {
        String name = type.getName();
        return name.equals("Int") || name.equals("Float");
    }
    
    private Type promoteNumericType(Type t1, Type t2) {
        // Float takes precedence over Int
        if (t1.getName().equals("Float") || t2.getName().equals("Float")) {
            return new PrimitiveType("Float");
        }
        return new PrimitiveType("Int");
    }
    
    private String extractPatternName(com.firefly.compiler.ast.Pattern pattern) {
        // Simplified pattern name extraction
        // TODO: Handle complex patterns
        if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            return ((com.firefly.compiler.ast.pattern.VariablePattern) pattern).getName();
        }
        return null;
    }
}
