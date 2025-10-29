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
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitUseDeclaration(UseDeclaration decl) {
        return new PrimitiveType("Void");
    }
    
    // ============ Declarations ============
    
    @Override
    public Type visitClassDecl(ClassDecl decl) {
        // Enter class scope
        currentScope = currentScope.enterScope();
        
        // Add fields to scope
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            try {
                currentScope.define(
                    field.getName(),
                    field.getType(),
                    SymbolTable.SymbolKind.FIELD,
                    field.isMutable()
                );
                
                // If field has initializer, infer its type
                if (field.getInitializer().isPresent()) {
                    Type initType = field.getInitializer().get().accept(this);
                    typeMap.put(field.getInitializer().get(), initType);
                }
            } catch (SemanticException e) {
                reporter.error("TI101", "Field already defined: " + field.getName(), null);
            }
        }
        
        // Predeclare methods in class scope so they can be referenced within the class
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
                // Ignore duplicate method definitions in inference phase
            }
        }
        
        // Infer types for methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            // Enter method scope
            currentScope = currentScope.enterScope();
            
            // Add method parameters to scope
            for (FunctionDecl.Parameter param : method.getParameters()) {
                try {
                    currentScope.define(
                        param.getName(),
                        param.getType(),
                        SymbolTable.SymbolKind.PARAMETER,
                        param.isMutable()
                    );
                } catch (SemanticException e) {
                    // Parameter already defined, skip
                }
            }
            
            // Infer method body type
            Type bodyType = method.getBody().accept(this);
            typeMap.put(method.getBody(), bodyType);
            
            // Exit method scope
            currentScope = currentScope.exitScope();
        }
        
        // Infer constructor if present
        if (decl.getConstructor().isPresent()) {
            ClassDecl.ConstructorDecl constructor = decl.getConstructor().get();
            
            // Enter constructor scope
            currentScope = currentScope.enterScope();
            
            // Add constructor parameters to scope
            for (FunctionDecl.Parameter param : constructor.getParameters()) {
                try {
                    currentScope.define(
                        param.getName(),
                        param.getType(),
                        SymbolTable.SymbolKind.PARAMETER,
                        param.isMutable()
                    );
                } catch (SemanticException e) {
                    // Parameter already defined, skip
                }
            }
            
            // Infer constructor body type
            Type bodyType = constructor.getBody().accept(this);
            typeMap.put(constructor.getBody(), bodyType);
            
            // Exit constructor scope
            currentScope = currentScope.exitScope();
        }
        
        // Exit class scope
        currentScope = currentScope.exitScope();
        
        return new NamedType(decl.getName());
    }
    
    @Override
    public Type visitInterfaceDecl(InterfaceDecl decl) {
        return new PrimitiveType("Void");
    }
    @Override
    public Type visitActorDecl(ActorDecl decl) {
        // Enter actor scope
        currentScope = currentScope.enterScope();
        
        // Add actor fields to scope
        for (FieldDecl field : decl.getFields()) {
            try {
                currentScope.define(
                    field.getName(),
                    field.getType(),
                    SymbolTable.SymbolKind.FIELD,
                    field.isMutable()
                );
                
                // If field has initializer, infer its type
                if (field.getInitializer().isPresent()) {
                    Type initType = field.getInitializer().get().accept(this);
                    typeMap.put(field.getInitializer().get(), initType);
                }
            } catch (SemanticException e) {
                reporter.error("TI102", "Actor field already defined: " + field.getName(), null);
            }
        }
        
        // Infer type of init block
        if (decl.getInitBlock() != null) {
            Type initType = decl.getInitBlock().accept(this);
            typeMap.put(decl.getInitBlock(), initType);
        }
        
        // Infer types for receive cases
        for (ActorDecl.ReceiveCase receiveCase : decl.getReceiveCases()) {
            // Enter receive case scope
            currentScope = currentScope.enterScope();
            
            // Extract variables from pattern and add to scope
            extractPatternVariables(receiveCase.getPattern(), currentScope);
            
            // Infer type of case expression
            Type caseType = receiveCase.getExpression().accept(this);
            typeMap.put(receiveCase.getExpression(), caseType);
            
            // Exit receive case scope
            currentScope = currentScope.exitScope();
        }
        
        // Exit actor scope
        currentScope = currentScope.exitScope();
        
        return new NamedType(decl.getName());
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
    public Type visitSparkDecl(SparkDecl decl) {
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
        return new PrimitiveType("Void");
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
        return new PrimitiveType("Void");
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
            case POWER:
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
                return new PrimitiveType("Void");
                
            case BIT_AND:
            case BIT_OR:
            case BIT_XOR:
            case BIT_LEFT_SHIFT:
            case BIT_RIGHT_SHIFT:
                // Bitwise operators: require Int types
                if (leftType.getName().equals("Int") && rightType.getName().equals("Int")) {
                    return new PrimitiveType("Int");
                }
                reporter.error(
                    "TI003",
                    String.format("Bitwise operator '%s' requires Int types, got %s and %s",
                        expr.getOperator(), leftType.getName(), rightType.getName()),
                    expr.getLocation(),
                    "Bitwise operations are only supported on Int types"
                );
                return new PrimitiveType("Void");
                
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
        // If calling a function by name, check for builtins first
        if (expr.getFunction() instanceof IdentifierExpr) {
            String funcName = ((IdentifierExpr) expr.getFunction()).getName();
            
            // Handle builtin functions
            if (funcName.equals("println") || funcName.equals("print")) {
                // println/print returns Void
                return new PrimitiveType("Void");
            }
            if (funcName.equals("format")) {
                // format returns String
                return new PrimitiveType("String");
            }
            if (funcName.equals("spawn")) {
                // spawn returns ActorRef (treat as Object for now)
                return new NamedType("ActorRef");
            }
            
            // Look up in symbol table
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
        String fieldName = expr.getFieldName();
        
        // Look up field type in struct/spark/class definition
        if (objectType instanceof NamedType) {
            String typeName = ((NamedType) objectType).getName();
            
            // Look up the type definition in global scope
            Optional<SymbolTable.Symbol> typeSymbol = globalScope.lookup(typeName);
            if (typeSymbol.isPresent()) {
                SymbolTable.Symbol symbol = typeSymbol.get();
                
                // If it's a struct or spark, look up field type
                if (symbol.getKind() == SymbolTable.SymbolKind.STRUCT ||
                    symbol.getKind() == SymbolTable.SymbolKind.SPARK) {
                    // Enter the type's scope to access fields
                    // For now, return Any - full implementation would require
                    // storing field metadata in symbol table
                    return new PrimitiveType("Any");
                }
            }
        }
        
        // Fallback: return Any for unknown fields
        return new PrimitiveType("Any");
    }
    
    @Override
    public Type visitIndexAccessExpr(IndexAccessExpr expr) {
        Type objectType = expr.getObject().accept(this);
        
        if (objectType instanceof ArrayType) {
            return ((ArrayType) objectType).getElementType();
        }
        
        return new PrimitiveType("Void");
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
                return new OptionalType(new PrimitiveType("Void"));
            default:
                throw new SemanticException("Unknown literal kind: " + expr.getKind());
        }
    }
    
    @Override
    public Type visitIdentifierExpr(IdentifierExpr expr) {
        String name = expr.getName();
        
        // Check for builtin functions
        if (name.equals("println") || name.equals("print") || 
            name.equals("format") || name.equals("spawn")) {
            // Builtins are valid identifiers, return a generic function type
            return new PrimitiveType("Void");
        }
        
        Optional<SymbolTable.Symbol> symbol = currentScope.lookup(name);
        if (symbol.isPresent()) {
            return symbol.get().getType();
        }
        
        // Report error with location info
        reporter.error(
            "TI001",
            "Undefined identifier: '" + name + "'",
            expr.getLocation(),
            "Make sure the variable or function is declared before use"
        );
        
        // Return Unit as fallback to continue type checking
        return new PrimitiveType("Void");
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
            return new PrimitiveType("Void");
        }
        
        // No else branch, if is a statement, returns Unit
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitMatchExpr(MatchExpr expr) {
        // Infer type from first arm (simplified)
        if (!expr.getArms().isEmpty()) {
            return expr.getArms().get(0).getBody().accept(this);
        }
        return new PrimitiveType("Void");
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
        Type resultType = new PrimitiveType("Void");
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
                    new PrimitiveType("Void"),
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
        
        // Extract variable name from pattern and add to scope
        // Pattern is bound to each element from the iterable
        String patternName = extractPatternName(expr.getPattern());
        if (patternName != null) {
            try {
                // Infer the element type from the iterable
                Type iterableType = expr.getIterable().accept(this);
                Type elementType = iterableType; // Simplified - would need to extract generic type
                
                currentScope.define(
                    patternName,
                    elementType,
                    SymbolTable.SymbolKind.VARIABLE,
                    false
                );
            } catch (SemanticException e) {
                // Variable already defined, skip
            }
        }
        
        expr.getIterable().accept(this);
        expr.getBody().accept(this);
        
        // Exit for loop scope
        currentScope = currentScope.exitScope();
        
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitWhileExpr(WhileExpr expr) {
        Type conditionType = expr.getCondition().accept(this);
        if (!conditionType.getName().equals("Bool")) {
            throw new SemanticException("While condition must be Bool, got " + conditionType.getName());
        }
        expr.getBody().accept(this);
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitReturnExpr(ReturnExpr expr) {
        Optional<Expression> value = expr.getValue();
        if (value.isPresent()) {
            return value.get().accept(this);
        }
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitBreakExpr(BreakExpr expr) {
        // Break is a control flow expression with bottom type (never returns)
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitContinueExpr(ContinueExpr expr) {
        // Continue is a control flow expression with bottom type (never returns)
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitConcurrentExpr(ConcurrentExpr expr) {
        // Process all bindings and infer their types
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            binding.getExpression().accept(this);
        }
        // concurrent { } returns Unit for now (could be a tuple of results)
        return new PrimitiveType("Void");
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
        // Prefer the right-hand type when the left is optional (fallback value)
        if (leftType instanceof OptionalType) {
            return rightType;
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
    
    public Type visitMapLiteralExpr(com.firefly.compiler.ast.expr.MapLiteralExpr expr) {
        // Return Map type (we generate HashMap bytecode)
        return new com.firefly.compiler.ast.type.NamedType("Map");
    }
    
    // ============ Patterns ============
    
    @Override
    public Type visitPattern(Pattern pattern) {
        // Pattern type inference is context-dependent
        return new PrimitiveType("Void");
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
        if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            return ((com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern).getName();
        }
        if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            return ((com.firefly.compiler.ast.pattern.VariablePattern) pattern).getName();
        }
        return null;
    }
    
    /**
     * Extract variables from a pattern and add them to the given scope.
     * Handles complex patterns including constructors, tuples, and literals.
     */
    private void extractPatternVariables(com.firefly.compiler.ast.Pattern pattern, SymbolTable scope) {
        if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            // Typed variable pattern: add to scope with explicit type
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern = 
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern;
            try {
                scope.define(
                    typedPattern.getName(),
                    typedPattern.getType(),
                    SymbolTable.SymbolKind.VARIABLE,
                    typedPattern.isMutable()
                );
            } catch (SemanticException e) {
                // Variable already defined, skip
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            // Simple variable pattern: add to scope with inferred type
            com.firefly.compiler.ast.pattern.VariablePattern varPattern = 
                (com.firefly.compiler.ast.pattern.VariablePattern) pattern;
            try {
                // Infer type as Any for now - would be refined by unification
                scope.define(
                    varPattern.getName(),
                    new PrimitiveType("Any"),
                    SymbolTable.SymbolKind.VARIABLE,
                    false
                );
            } catch (SemanticException e) {
                // Variable already defined, skip
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.StructPattern) {
            // Struct pattern: extract variables from field patterns
            com.firefly.compiler.ast.pattern.StructPattern structPattern = 
                (com.firefly.compiler.ast.pattern.StructPattern) pattern;
            for (com.firefly.compiler.ast.pattern.StructPattern.FieldPattern fieldPattern : structPattern.getFields()) {
                if (fieldPattern.getPattern() != null) {
                    extractPatternVariables(fieldPattern.getPattern(), scope);
                }
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.TuplePattern) {
            // Tuple pattern: extract variables from each element
            com.firefly.compiler.ast.pattern.TuplePattern tuplePattern = 
                (com.firefly.compiler.ast.pattern.TuplePattern) pattern;
            for (com.firefly.compiler.ast.Pattern elementPattern : tuplePattern.getElements()) {
                extractPatternVariables(elementPattern, scope);
            }
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.ArrayPattern) {
            // Array pattern: extract variables from each element
            com.firefly.compiler.ast.pattern.ArrayPattern arrayPattern = 
                (com.firefly.compiler.ast.pattern.ArrayPattern) pattern;
            for (com.firefly.compiler.ast.Pattern elementPattern : arrayPattern.getElements()) {
                extractPatternVariables(elementPattern, scope);
            }
        }
        // Literal patterns don't introduce variables, so nothing to do
    }

    @Override
    public Type visitTupleType(TupleType type) {
        return type;
    }


    @Override
    public Type visitTypeParameter(TypeParameter type) {
        return type;
    }


    @Override
    public Type visitGenericType(GenericType type) {
        return type;
    }


    @Override
    public Type visitTupleLiteralExpr(TupleLiteralExpr expr) {
        return new TupleType(expr.getElements().stream()
            .map(e -> e.accept(this))
            .collect(java.util.stream.Collectors.toList()), expr.getLocation());
    }


    @Override
    public Type visitThrowExpr(ThrowExpr expr) {
        expr.getException().accept(this);
        return new PrimitiveType("Void");
    }


    @Override
    public Type visitTryExpr(TryExpr expr) {
        return new PrimitiveType("Void");
    }


    @Override
    public Type visitTupleAccessExpr(TupleAccessExpr expr) {
        return new PrimitiveType("Void");
    }
    
    @Override
    public Type visitStructLiteralExpr(com.firefly.compiler.ast.expr.StructLiteralExpr expr) {
        // Return the struct type
        return new com.firefly.compiler.ast.type.NamedType(expr.getStructName());
    }


    
    @Override
    public Type visitTypeAliasDecl(com.firefly.compiler.ast.decl.TypeAliasDecl decl) {
        // Type alias resolves to its target type
        return decl.getTargetType();
    }
    
    @Override
    public Type visitAwaitExpr(com.firefly.compiler.ast.expr.AwaitExpr expr) {
        // Await unwraps Future<T> to T
        // For now, return the type of the future expression
        return expr.getFuture().accept(this);
    }
    
    @Override
    public Type visitSafeAccessExpr(com.firefly.compiler.ast.expr.SafeAccessExpr expr) {
        // Safe access returns an optional value; we cannot resolve field types yet,
        // so approximate as Optional<Any>
        expr.getObject().accept(this);
        return new OptionalType(new NamedType("Any"));
    }
    
    @Override
    public Type visitForceUnwrapExpr(com.firefly.compiler.ast.expr.ForceUnwrapExpr expr) {
        // Force unwrap removes optional wrapper
        Type exprType = expr.getExpression().accept(this);
        if (exprType instanceof OptionalType) {
            return ((OptionalType) exprType).getInnerType();
        }
        return exprType;
    }
    
    @Override
    public Type visitExceptionDecl(com.firefly.compiler.ast.decl.ExceptionDecl decl) {
        // Exception declaration returns the exception type
        return new NamedType(decl.getName());
    }
}
