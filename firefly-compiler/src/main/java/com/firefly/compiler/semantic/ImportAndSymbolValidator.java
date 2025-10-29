package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.Diagnostic;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.*;

/**
 * Professional import and symbol validator for Firefly.
 * 
 * Ensures strict import compliance:
 * - All functions must be imported or defined in current module
 * - All types must be imported or defined in current module  
 * - No implicit imports except primitives and optional std prelude
 * - Clear error messages with suggestions for missing imports
 * 
 * This validator implements the visitor pattern to traverse the entire AST
 * and validate all symbol references.
 */
public class ImportAndSymbolValidator implements AstVisitor<Void> {
    
    private final DiagnosticReporter diagnostics;
    
    // Imported symbols tracking
    private final Set<String> importedFunctions = new HashSet<>();
    private final Set<String> importedTypes = new HashSet<>();
    private final Map<String, String> importedSymbolsFullPath = new HashMap<>();
    
    // Defined symbols in current module
    private final Set<String> definedFunctions = new HashSet<>();
    private final Set<String> definedTypes = new HashSet<>();
    
    // Local scope tracking (for variables)
    private final Stack<Set<String>> localScopes = new Stack<>();
    
    // Primitive types (always available)
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
        "Int", "Float", "String", "Bool", "Char", "Void", "Byte", "Short", "Long", "Double"
    );
    
    // Standard library prelude (auto-imported)
    private static final Set<String> STD_PRELUDE_FUNCTIONS = Set.of(
        "println", "print", "panic", "assert", "debug", "error", "format"
    );
    
    // Builtin language functions
    private static final Set<String> BUILTIN_FUNCTIONS = Set.of(
        "spawn"  // Actor spawning
    );
    
    // Configuration
    private final boolean allowStdPrelude;
    private final boolean strictMode;
    
    // Current context
    private String currentModule = "unnamed";
    
    /**
     * Create validator with default settings (strict mode with std prelude).
     */
    public ImportAndSymbolValidator(DiagnosticReporter diagnostics) {
        this(diagnostics, true, true);
    }
    
    /**
     * Create validator with custom settings.
     * 
     * @param diagnostics Reporter for collecting errors/warnings
     * @param allowStdPrelude Whether to auto-import std prelude (println, etc.)
     * @param strictMode Whether to enforce strict import checking
     */
    public ImportAndSymbolValidator(DiagnosticReporter diagnostics, boolean allowStdPrelude, boolean strictMode) {
        this.diagnostics = diagnostics;
        this.allowStdPrelude = allowStdPrelude;
        this.strictMode = strictMode;
    }
    
    /**
     * Validate a compilation unit.
     * This is the main entry point for validation.
     */
    public void validate(CompilationUnit unit) {
        if (!strictMode) {
            return; // Skip validation if not in strict mode
        }
        
        // Reset state
        importedFunctions.clear();
        importedTypes.clear();
        importedSymbolsFullPath.clear();
        definedFunctions.clear();
        definedTypes.clear();
        localScopes.clear();
        
        // Get module name
        currentModule = unit.getModuleName() != null ? unit.getModuleName() : "unnamed";
        
        // Phase 1: Collect imports
        for (UseDeclaration useDecl : unit.getUses()) {
            collectImport(useDecl);
        }
        
        // Phase 2: Collect top-level declarations
        for (Declaration decl : unit.getDeclarations()) {
            collectDeclaration(decl);
        }
        
        // Phase 3: Validate all references
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
    }
    
    // ========== PHASE 1: Collect Imports ==========
    
    private void collectImport(UseDeclaration useDecl) {
        String modulePath = useDecl.getModulePath();
        
        if (useDecl.isWildcard()) {
            // Wildcard imports - warn but allow
            diagnostics.warning(
                "IMPORT_W001",
                "Wildcard imports make dependencies unclear",
                useDecl.getLocation(),
                "Consider explicit imports: use " + modulePath + "::{Specific, Items}"
            );
            // We can't track wildcard imports precisely, so we'll be lenient
            // In a production compiler, we'd scan the classpath here
        } else if (useDecl.getItems().isEmpty()) {
            // Single import: use std::io::println
            String lastSegment = getLastSegment(modulePath);
            registerImport(lastSegment, modulePath);
        } else {
            // Multiple imports: use std::io::{println, read_line}
            for (String item : useDecl.getItems()) {
                String fullPath = modulePath + "::" + item;
                registerImport(item, fullPath);
            }
        }
    }
    
    private void registerImport(String symbol, String fullPath) {
        importedSymbolsFullPath.put(symbol, fullPath);
        
        if (isTypeSymbol(symbol)) {
            importedTypes.add(symbol);
        } else {
            importedFunctions.add(symbol);
        }
    }
    
    private boolean isTypeSymbol(String name) {
        // Types start with uppercase
        return !name.isEmpty() && Character.isUpperCase(name.charAt(0));
    }
    
    private String getLastSegment(String path) {
        int lastSep = path.lastIndexOf("::");
        return lastSep >= 0 ? path.substring(lastSep + 2) : path;
    }
    
    // ========== PHASE 2: Collect Declarations ==========
    
    private void collectDeclaration(Declaration decl) {
        if (decl instanceof FunctionDecl) {
            definedFunctions.add(((FunctionDecl) decl).getName());
        } else if (decl instanceof ClassDecl) {
            definedTypes.add(((ClassDecl) decl).getName());
        } else if (decl instanceof StructDecl) {
            definedTypes.add(((StructDecl) decl).getName());
        } else if (decl instanceof DataDecl) {
            definedTypes.add(((DataDecl) decl).getName());
        } else if (decl instanceof TraitDecl) {
            definedTypes.add(((TraitDecl) decl).getName());
        } else if (decl instanceof ActorDecl) {
            definedTypes.add(((ActorDecl) decl).getName());
        } else if (decl instanceof InterfaceDecl) {
            definedTypes.add(((InterfaceDecl) decl).getName());
        }
    }
    
    // ========== PHASE 3: Validate References ==========
    
    /**
     * Check if a function is available (imported, defined, or builtin).
     */
    private boolean isFunctionAvailable(String name) {
        // Local variable?
        if (isInLocalScope(name)) {
            return true;
        }
        
        // Defined in current module?
        if (definedFunctions.contains(name)) {
            return true;
        }
        
        // Imported?
        if (importedFunctions.contains(name)) {
            return true;
        }
        
        // Std prelude?
        if (allowStdPrelude && STD_PRELUDE_FUNCTIONS.contains(name)) {
            return true;
        }
        
        // Builtin?
        if (BUILTIN_FUNCTIONS.contains(name)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a type is available (imported, defined, or primitive).
     */
    private boolean isTypeAvailable(String name) {
        // Primitive type?
        if (PRIMITIVE_TYPES.contains(name)) {
            return true;
        }
        
        // Defined in current module?
        if (definedTypes.contains(name)) {
            return true;
        }
        
        // Imported?
        if (importedTypes.contains(name)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Report undefined function with helpful suggestion.
     */
    private void reportUndefinedFunction(String name, SourceLocation location) {
        String suggestion = suggestImportForFunction(name);
        diagnostics.error(
            "UNDEF_E001",
            String.format("Undefined function '%s'", name),
            location,
            suggestion
        );
    }
    
    /**
     * Report undefined type with helpful suggestion.
     */
    private void reportUndefinedType(String name, SourceLocation location) {
        String suggestion = suggestImportForType(name);
        diagnostics.error(
            "UNDEF_E002",
            String.format("Undefined type '%s'", name),
            location,
            suggestion
        );
    }
    
    /**
     * Suggest import for common functions.
     */
    private String suggestImportForFunction(String name) {
        // Common patterns
        Map<String, String> suggestions = Map.ofEntries(
            Map.entry("println", "use std::io::println"),
            Map.entry("print", "use std::io::print"),
            Map.entry("read_line", "use std::io::read_line"),
            Map.entry("panic", "use std::debug::panic"),
            Map.entry("assert", "use std::debug::assert"),
            Map.entry("spawn", "use std::actors::spawn"),
            Map.entry("format", "use std::format::format")
        );
        
        return suggestions.getOrDefault(name, 
            "Add a 'use' declaration to import this function, or define it in the current module");
    }
    
    /**
     * Suggest import for common types.
     */
    private String suggestImportForType(String name) {
        // Common Java types
        Map<String, String> suggestions = Map.ofEntries(
            Map.entry("ArrayList", "use java::util::ArrayList"),
            Map.entry("HashMap", "use java::util::HashMap"),
            Map.entry("HashSet", "use java::util::HashSet"),
            Map.entry("List", "use java::util::List"),
            Map.entry("Map", "use java::util::Map"),
            Map.entry("Set", "use java::util::Set"),
            Map.entry("Optional", "use java::util::Optional"),
            Map.entry("Option", "use std::option::Option"),
            Map.entry("Result", "use std::result::Result"),
            Map.entry("ActorRef", "use std::actors::ActorRef")
        );
        
        return suggestions.getOrDefault(name,
            "Add a 'use' declaration to import this type, or define it in the current module");
    }
    
    // ========== Scope Management ==========
    
    private void enterScope() {
        localScopes.push(new HashSet<>());
    }
    
    private void exitScope() {
        if (!localScopes.isEmpty()) {
            localScopes.pop();
        }
    }
    
    private void addToLocalScope(String name) {
        if (!localScopes.isEmpty()) {
            localScopes.peek().add(name);
        }
    }
    
    private boolean isInLocalScope(String name) {
        for (Set<String> scope : localScopes) {
            if (scope.contains(name)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract variables from a pattern and add them to the current scope.
     */
    private void extractPatternVariables(Pattern pattern) {
        if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            // Typed variable pattern
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern = 
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern;
            addToLocalScope(typedPattern.getName());
        } else if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            // Simple variable pattern
            com.firefly.compiler.ast.pattern.VariablePattern varPattern = 
                (com.firefly.compiler.ast.pattern.VariablePattern) pattern;
            addToLocalScope(varPattern.getName());
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
    
    // ========== AST Visitor Implementation ==========
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Already handled in validate()
        return null;
    }
    
    @Override
    public Void visitUseDeclaration(UseDeclaration decl) {
        // Already handled in collectImport()
        return null;
    }
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        enterScope();
        
        // Add parameters to local scope
        for (FunctionDecl.Parameter param : decl.getParameters()) {
            addToLocalScope(param.getName());
            // Validate parameter type
            validateType(param.getType(), decl.getLocation());
        }
        
        // Validate return type
        if (decl.getReturnType().isPresent()) {
            validateType(decl.getReturnType().get(), decl.getLocation());
        }
        
        // Validate body
        decl.getBody().accept(this);
        
        exitScope();
        return null;
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // Validate fields
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            validateType(field.getType(), decl.getLocation());
        }
        
        // Validate methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            enterScope();
            
            // Add 'self' to scope
            addToLocalScope("self");
            
            // Add parameters
            for (FunctionDecl.Parameter param : method.getParameters()) {
                addToLocalScope(param.getName());
                validateType(param.getType(), decl.getLocation());
            }
            
            // Validate return type
            if (method.getReturnType().isPresent()) {
                validateType(method.getReturnType().get(), decl.getLocation());
            }
            
            // Validate body
            method.getBody().accept(this);
            
            exitScope();
        }
        
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Interfaces only have signatures - validate types in signatures
        for (TraitDecl.FunctionSignature method : decl.getMethods()) {
            // Validate parameter types
            // Note: method parameters available through reflection on FunctionSignature
            // Full implementation would inspect FunctionSignature structure
        }
        return null;
    }
    
    @Override
    public Void visitActorDecl(ActorDecl decl) {
        // Validate actor fields
        for (FieldDecl field : decl.getFields()) {
            validateType(field.getType(), field.getLocation());
        }
        
        // Validate init block
        if (decl.getInitBlock() != null) {
            decl.getInitBlock().accept(this);
        }
        
        // Validate receive cases
        for (ActorDecl.ReceiveCase receiveCase : decl.getReceiveCases()) {
            receiveCase.getExpression().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitStructDecl(StructDecl decl) {
        // Validate field types
        for (StructDecl.Field field : decl.getFields()) {
            validateType(field.getType(), decl.getLocation());
        }
        return null;
    }
    
    @Override
    public Void visitSparkDecl(SparkDecl decl) {
        // Validate spark field types
        for (SparkDecl.SparkField field : decl.getFields()) {
            validateType(field.getType(), decl.getLocation());
        }
        return null;
    }
    
    @Override
    public Void visitDataDecl(DataDecl decl) {
        // Validate variant types
        // Data types are sum types - we'll validate them when we have better AST access
        // For now, skip detailed validation
        return null;
    }
    
    @Override
    public Void visitTraitDecl(TraitDecl decl) {
        // Traits have method signatures - validate types in each signature
        for (TraitDecl.FunctionSignature method : decl.getMembers()) {
            // Validate parameter types
            // Full implementation would inspect FunctionSignature structure
        }
        return null;
    }
    
    @Override
    public Void visitImplDecl(ImplDecl decl) {
        // Validate trait implementations
        // Visit methods in impl block
        for (FunctionDecl method : decl.getMethods()) {
            enterScope();
            
            // Add parameters to scope
            for (FunctionDecl.Parameter param : method.getParameters()) {
                addToLocalScope(param.getName());
                validateType(param.getType(), decl.getLocation());
            }
            
            // Validate return type
            if (method.getReturnType().isPresent()) {
                validateType(method.getReturnType().get(), decl.getLocation());
            }
            
            // Validate body
            method.getBody().accept(this);
            
            exitScope();
        }
        return null;
    }
    
    // ========== Statements ==========
    
    @Override
    public Void visitLetStatement(LetStatement stmt) {
        // Validate initializer first
        if (stmt.getInitializer().isPresent()) {
            stmt.getInitializer().get().accept(this);
        }
        
        // Add pattern-bound variables to scope
        Pattern pattern = stmt.getPattern();
        extractPatternVariables(pattern);
        
        return null;
    }
    
    @Override
    public Void visitExprStatement(ExprStatement stmt) {
        stmt.getExpression().accept(this);
        return null;
    }
    
    // ========== Expressions ==========
    
    @Override
    public Void visitCallExpr(CallExpr expr) {
        // Validate function being called
        Expression function = expr.getFunction();
        
        if (function instanceof IdentifierExpr) {
            String funcName = ((IdentifierExpr) function).getName();
            if (!isFunctionAvailable(funcName)) {
                reportUndefinedFunction(funcName, expr.getLocation());
            }
        } else {
            // Method call or complex expression
            function.accept(this);
        }
        
        // Validate arguments
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitIdentifierExpr(IdentifierExpr expr) {
        // Identifiers are validated in context (e.g., CallExpr)
        // Standalone identifiers should be variables in scope
        String name = expr.getName();
        
        if (!isInLocalScope(name) && !definedFunctions.contains(name) && !definedTypes.contains(name)) {
            // Could be undefined, but we'll let type checker handle this
            // to avoid double reporting
        }
        
        return null;
    }
    
    @Override
    public Void visitNewExpr(NewExpr expr) {
        // Validate type being instantiated
        validateType(expr.getType(), expr.getLocation());
        
        // Validate constructor arguments
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        
        return null;
    }
    
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
    public Void visitFieldAccessExpr(FieldAccessExpr expr) {
        expr.getObject().accept(this);
        return null;
    }
    
    @Override
    public Void visitTupleAccessExpr(TupleAccessExpr expr) {
        expr.getTuple().accept(this);
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
        // Literals don't reference symbols
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
    public Void visitMatchExpr(MatchExpr expr) {
        expr.getValue().accept(this);
        for (MatchExpr.MatchArm arm : expr.getArms()) {
            enterScope();
            // Pattern might introduce bindings
            arm.getBody().accept(this);
            exitScope();
        }
        return null;
    }
    
    @Override
    public Void visitBlockExpr(BlockExpr expr) {
        enterScope();
        
        for (Statement stmt : expr.getStatements()) {
            stmt.accept(this);
        }
        
        if (expr.getFinalExpression().isPresent()) {
            expr.getFinalExpression().get().accept(this);
        }
        
        exitScope();
        return null;
    }
    
    @Override
    public Void visitLambdaExpr(LambdaExpr expr) {
        enterScope();
        
        // Add lambda parameters to scope (LambdaExpr has List<String> parameters)
        for (String param : expr.getParameters()) {
            addToLocalScope(param);
        }
        
        expr.getBody().accept(this);
        
        exitScope();
        return null;
    }
    
    @Override
    public Void visitForExpr(ForExpr expr) {
        expr.getIterable().accept(this);
        
        enterScope();
        // Pattern in 'for' introduces binding - just skip pattern validation for now
        // as we don't have access to Pattern structure
        expr.getBody().accept(this);
        exitScope();
        
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
    
    @Override
    public Void visitBreakExpr(BreakExpr expr) {
        return null;
    }
    
    @Override
    public Void visitContinueExpr(ContinueExpr expr) {
        return null;
    }
    
    @Override
    public Void visitTryExpr(TryExpr expr) {
        expr.getTryBlock().accept(this);
        
        for (TryExpr.CatchClause clause : expr.getCatchClauses()) {
            enterScope();
            // Exception variable in scope
            if (clause.getVariableName().isPresent()) {
                addToLocalScope(clause.getVariableName().get());
            }
            if (clause.getExceptionType().isPresent()) {
                validateType(clause.getExceptionType().get(), expr.getLocation());
            }
            clause.getHandler().accept(this);
            exitScope();
        }
        
        if (expr.getFinallyBlock().isPresent()) {
            expr.getFinallyBlock().get().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitThrowExpr(ThrowExpr expr) {
        expr.getException().accept(this);
        return null;
    }
    
    @Override
    public Void visitConcurrentExpr(ConcurrentExpr expr) {
        // Validate concurrent bindings
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
        expr.getValue().accept(this);
        return null;
    }
    
    @Override
    public Void visitArrayLiteralExpr(ArrayLiteralExpr expr) {
        for (Expression element : expr.getElements()) {
            element.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitTupleLiteralExpr(TupleLiteralExpr expr) {
        for (Expression element : expr.getElements()) {
            element.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitStructLiteralExpr(StructLiteralExpr expr) {
        for (StructLiteralExpr.FieldInit field : expr.getFieldInits()) {
            field.getValue().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitMapLiteralExpr(MapLiteralExpr expr) {
        for (var entry : expr.getEntries().entrySet()) {
            entry.getKey().accept(this);
            entry.getValue().accept(this);
        }
        return null;
    }
    
    // ========== Patterns ==========
    
    @Override
    public Void visitPattern(Pattern pattern) {
        // Patterns are handled in context
        return null;
    }
    
    @Override
    public Void visitAwaitExpr(com.firefly.compiler.ast.expr.AwaitExpr expr) {
        expr.getFuture().accept(this);
        return null;
    }
    
    // ========== Types ==========
    
    /**
     * Validate that a type is available (imported or defined).
     */
    private void validateType(Type type, SourceLocation location) {
        if (type instanceof PrimitiveType) {
            // Primitives are always valid
            return;
        } else if (type instanceof NamedType) {
            String typeName = ((NamedType) type).getName();
            if (!isTypeAvailable(typeName)) {
                reportUndefinedType(typeName, location);
            }
        } else if (type instanceof GenericType) {
            GenericType genericType = (GenericType) type;
            // Validate base name
            String baseName = genericType.getBaseName();
            if (!isTypeAvailable(baseName)) {
                reportUndefinedType(baseName, location);
            }
            // Validate type arguments
            for (Type typeArg : genericType.getTypeArguments()) {
                validateType(typeArg, location);
            }
        } else if (type instanceof ArrayType) {
            validateType(((ArrayType) type).getElementType(), location);
        } else if (type instanceof OptionalType) {
            validateType(((OptionalType) type).getInnerType(), location);
        } else if (type instanceof FunctionType) {
            FunctionType funcType = (FunctionType) type;
            for (Type paramType : funcType.getParamTypes()) {
                validateType(paramType, location);
            }
            validateType(funcType.getReturnType(), location);
        } else if (type instanceof TupleType) {
            for (Type elementType : ((TupleType) type).getElementTypes()) {
                validateType(elementType, location);
            }
        }
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
    
    @Override
    public Void visitGenericType(GenericType type) {
        return null;
    }
    
    @Override
    public Void visitTypeParameter(TypeParameter type) {
        return null;
    }
    
    @Override
    public Void visitTupleType(TupleType type) {
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
