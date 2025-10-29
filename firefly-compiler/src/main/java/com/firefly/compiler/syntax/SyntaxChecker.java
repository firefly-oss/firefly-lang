package com.firefly.compiler.syntax;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates AST syntax and structure, reporting errors and warnings.
 * This is Firefly's syntax checking phase.
 */
public class SyntaxChecker implements AstVisitor<Void> {
    
    private final DiagnosticReporter reporter;
    private final Set<String> declaredFunctions;
    private final Set<String> declaredTypes;
    private boolean inAsyncContext;
    private boolean inLoopContext;
    
    public SyntaxChecker(DiagnosticReporter reporter) {
        this.reporter = reporter;
        this.declaredFunctions = new HashSet<>();
        this.declaredTypes = new HashSet<>();
        this.inAsyncContext = false;
        this.inLoopContext = false;
    }
    
    /**
     * Check the entire compilation unit.
     */
    public void check(CompilationUnit unit) {
        unit.accept(this);
    }
    
    // ============ Compilation Unit ============
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Check imports
        for (UseDeclaration imp : unit.getImports()) {
            imp.accept(this);
        }
        
        // Check declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitUseDeclaration(UseDeclaration decl) {
        // Validate import path
        if (decl.getModulePath().isEmpty()) {
            reporter.error("FF001", "Import path cannot be empty", decl.getLocation());
        }
        
        return null;
    }
    
    // ============ Declarations ============
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // Check for duplicate class names
        if (declaredTypes.contains(decl.getName())) {
            reporter.error("FF007",
                "Duplicate class declaration: " + decl.getName(),
                decl.getLocation());
        } else {
            declaredTypes.add(decl.getName());
        }
        
        // Check fields
        Set<String> fieldNames = new HashSet<>();
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            if (fieldNames.contains(field.getName())) {
                reporter.error("FF008",
                    "Duplicate field name: " + field.getName(),
                    decl.getLocation());
            }
            fieldNames.add(field.getName());
            field.getType().accept(this);
        }
        
        // Check methods
        Set<String> methodNames = new HashSet<>();
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            if (methodNames.contains(method.getName())) {
                reporter.error("FF009",
                    "Duplicate method name: " + method.getName(),
                    decl.getLocation());
            }
            methodNames.add(method.getName());
            
            // Check method parameters for duplicates
            Set<String> paramNames = new HashSet<>();
            for (FunctionDecl.Parameter param : method.getParameters()) {
                if (paramNames.contains(param.getName())) {
                    reporter.error("FF010",
                        "Duplicate parameter name: " + param.getName(),
                        decl.getLocation());
                }
                paramNames.add(param.getName());
                param.getType().accept(this);
            }
            
            // Check return type
            if (method.getReturnType().isPresent()) {
                method.getReturnType().get().accept(this);
            }
            
            // Manage async context for method bodies
            boolean wasAsync = inAsyncContext;
            if (method.isAsync()) {
                inAsyncContext = true;
            }
            
            // Check method body
            method.getBody().accept(this);
            
            // Restore async context
            inAsyncContext = wasAsync;
        }
        
        // Check constructor if present
        if (decl.getConstructor().isPresent()) {
            ClassDecl.ConstructorDecl constructor = decl.getConstructor().get();
            
            Set<String> ctorParamNames = new HashSet<>();
            for (FunctionDecl.Parameter param : constructor.getParameters()) {
                if (ctorParamNames.contains(param.getName())) {
                    reporter.error("FF011",
                        "Duplicate constructor parameter name: " + param.getName(),
                        decl.getLocation());
                }
                ctorParamNames.add(param.getName());
                param.getType().accept(this);
            }
            
            constructor.getBody().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Syntax check interface
        return null;
    }
    
    @Override
    public Void visitActorDecl(ActorDecl decl) {
        // Check for duplicate actor names
        if (declaredTypes.contains(decl.getName())) {
            reporter.error("FF012",
                "Duplicate actor declaration: " + decl.getName(),
                decl.getLocation());
        } else {
            declaredTypes.add(decl.getName());
        }
        
        // Check fields
        Set<String> fieldNames = new HashSet<>();
        for (FieldDecl field : decl.getFields()) {
            if (fieldNames.contains(field.getName())) {
                reporter.error("FF013",
                    "Duplicate actor field name: " + field.getName(),
                    decl.getLocation());
            }
            fieldNames.add(field.getName());
            field.getType().accept(this);
        }
        
        // Check init block
        if (decl.getInitBlock() != null) {
            decl.getInitBlock().accept(this);
        }
        
        // Check receive cases
        for (ActorDecl.ReceiveCase receiveCase : decl.getReceiveCases()) {
            receiveCase.getExpression().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        // Check for duplicate function names
        if (declaredFunctions.contains(decl.getName())) {
            reporter.error("FF002", 
                "Duplicate function declaration: " + decl.getName(), 
                decl.getLocation(),
                "Rename this function or remove the duplicate");
        } else {
            declaredFunctions.add(decl.getName());
        }
        
        // Check async context
        boolean wasAsync = inAsyncContext;
        if (decl.isAsync()) {
            inAsyncContext = true;
        }
        
        // Check parameters
        Set<String> paramNames = new HashSet<>();
        for (FunctionDecl.Parameter param : decl.getParameters()) {
            if (paramNames.contains(param.getName())) {
                reporter.error("FF003",
                    "Duplicate parameter name: " + param.getName(),
                    decl.getLocation());
            }
            paramNames.add(param.getName());
            
            // Check parameter type
            param.getType().accept(this);
            
            // Check default value
            if (param.getDefaultValue().isPresent()) {
                param.getDefaultValue().get().accept(this);
            }
        }
        
        // Check return type
        if (decl.getReturnType().isPresent()) {
            decl.getReturnType().get().accept(this);
        }
        
        // Check body
        decl.getBody().accept(this);
        
        inAsyncContext = wasAsync;
        return null;
    }
    
    @Override
    public Void visitStructDecl(StructDecl decl) {
        // Check for duplicate struct names
        if (declaredTypes.contains(decl.getName())) {
            reporter.error("FF004",
                "Duplicate type declaration: " + decl.getName(),
                decl.getLocation(),
                "Rename this type or remove the duplicate");
        } else {
            declaredTypes.add(decl.getName());
        }
        
        // Check fields
        Set<String> fieldNames = new HashSet<>();
        for (StructDecl.Field field : decl.getFields()) {
            if (fieldNames.contains(field.getName())) {
                reporter.error("FF005",
                    "Duplicate field name: " + field.getName(),
                    decl.getLocation());
            }
            fieldNames.add(field.getName());
            
            // Check field type
            field.getType().accept(this);
            
            // Check default value
            if (field.getDefaultValue().isPresent()) {
                field.getDefaultValue().get().accept(this);
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitSparkDecl(SparkDecl decl) {
        // Check for duplicate spark names
        if (declaredTypes.contains(decl.getName())) {
            reporter.error("FF004",
                "Duplicate type declaration: " + decl.getName(),
                decl.getLocation(),
                "Rename this type or remove the duplicate");
        } else {
            declaredTypes.add(decl.getName());
        }
        
        // Check fields
        Set<String> fieldNames = new HashSet<>();
        for (SparkDecl.SparkField field : decl.getFields()) {
            if (fieldNames.contains(field.getName())) {
                reporter.error("FF005",
                    "Duplicate field name: " + field.getName(),
                    decl.getLocation());
            }
            fieldNames.add(field.getName());
            
            // Check field type
            field.getType().accept(this);
            
            // Check default value
            if (field.getDefaultValue().isPresent()) {
                field.getDefaultValue().get().accept(this);
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitDataDecl(DataDecl decl) {
        // Check for duplicate data names
        if (declaredTypes.contains(decl.getName())) {
            reporter.error("FF004",
                "Duplicate type declaration: " + decl.getName(),
                decl.getLocation());
        } else {
            declaredTypes.add(decl.getName());
        }
        
        // Check variants
        Set<String> variantNames = new HashSet<>();
        for (DataDecl.Variant variant : decl.getVariants()) {
            if (variantNames.contains(variant.getName())) {
                reporter.error("FF006",
                    "Duplicate variant name: " + variant.getName(),
                    decl.getLocation());
            }
            variantNames.add(variant.getName());
            
            // Check variant fields
            for (DataDecl.VariantField field : variant.getFields()) {
                field.getType().accept(this);
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitTraitDecl(TraitDecl decl) {
        if (declaredTypes.contains(decl.getName())) {
            reporter.error("FF004",
                "Duplicate type declaration: " + decl.getName(),
                decl.getLocation());
        } else {
            declaredTypes.add(decl.getName());
        }
        
        return null;
    }
    
    @Override
    public Void visitExceptionDecl(com.firefly.compiler.ast.decl.ExceptionDecl decl) {
        if (declaredTypes.contains(decl.getName())) {
            reporter.error("FF004",
                "Duplicate exception declaration: " + decl.getName(),
                null);  // Location not stored in ExceptionDecl yet
        } else {
            declaredTypes.add(decl.getName());
        }
        return null;
    }
    
    @Override
    public Void visitImplDecl(ImplDecl decl) {
        // Check methods
        for (FunctionDecl method : decl.getMethods()) {
            method.accept(this);
        }
        
        return null;
    }
    
    // ============ Statements ============
    
    @Override
    public Void visitLetStatement(LetStatement stmt) {
        // Check initializer
        if (stmt.getInitializer().isPresent()) {
            stmt.getInitializer().get().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitExprStatement(ExprStatement stmt) {
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
    public Void visitUnaryExpr(UnaryExpr expr) {
        // Check await usage
        if (expr.getOperator() == UnaryExpr.UnaryOp.AWAIT && !inAsyncContext) {
            reporter.error("FF007",
                "await can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the function declaration");
        }
        
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
    
    @Override
    public Void visitMatchExpr(MatchExpr expr) {
        expr.getValue().accept(this);
        
        for (MatchExpr.MatchArm arm : expr.getArms()) {
            if (arm.getGuard() != null) {
                arm.getGuard().accept(this);
            }
            arm.getBody().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitBlockExpr(BlockExpr expr) {
        for (Statement stmt : expr.getStatements()) {
            stmt.accept(this);
        }
        
        if (expr.getFinalExpression().isPresent()) {
            expr.getFinalExpression().get().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitLambdaExpr(LambdaExpr expr) {
        // Check for duplicate parameter names
        Set<String> paramNames = new HashSet<>();
        for (String param : expr.getParameters()) {
            if (paramNames.contains(param)) {
                reporter.error("FF003",
                    "Duplicate parameter name: " + param,
                    expr.getLocation());
            }
            paramNames.add(param);
        }
        
        expr.getBody().accept(this);
        return null;
    }
    
    @Override
    public Void visitForExpr(ForExpr expr) {
        boolean wasInLoop = inLoopContext;
        inLoopContext = true;
        
        expr.getIterable().accept(this);
        expr.getBody().accept(this);
        
        inLoopContext = wasInLoop;
        return null;
    }
    
    @Override
    public Void visitWhileExpr(WhileExpr expr) {
        boolean wasInLoop = inLoopContext;
        inLoopContext = true;
        
        expr.getCondition().accept(this);
        expr.getBody().accept(this);
        
        inLoopContext = wasInLoop;
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
        if (!inLoopContext) {
            reporter.error("FF011",
                "break can only be used inside a loop",
                expr.getLocation(),
                "Move break inside a for or while loop");
        }
        return null;
    }
    
    @Override
    public Void visitContinueExpr(ContinueExpr expr) {
        if (!inLoopContext) {
            reporter.error("FF012",
                "continue can only be used inside a loop",
                expr.getLocation(),
                "Move continue inside a for or while loop");
        }
        return null;
    }
    
    @Override
    public Void visitConcurrentExpr(ConcurrentExpr expr) {
        if (!inAsyncContext) {
            reporter.error("FF008",
                "concurrent can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the function declaration");
        }
        
        // Check all bindings
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            binding.getExpression().accept(this);
        }
        
        return null;
    }
    
    @Override
    public Void visitRaceExpr(RaceExpr expr) {
        if (!inAsyncContext) {
            reporter.error("FF009",
                "race can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the function declaration");
        }
        
        expr.getBody().accept(this);
        return null;
    }
    
    @Override
    public Void visitTimeoutExpr(TimeoutExpr expr) {
        if (!inAsyncContext) {
            reporter.error("FF010",
                "timeout can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the function declaration");
        }
        
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
    
    // ============ Patterns ============
    
    @Override
    public Void visitPattern(Pattern pattern) {
        return null;
    }
    
    // ============ Types ============
    
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
        type.getInnerType().accept(this);
        return null;
    }
    
    @Override
    public Void visitArrayType(ArrayType type) {
        type.getElementType().accept(this);
        return null;
    }
    
    @Override
    public Void visitFunctionType(FunctionType type) {
        for (Type paramType : type.getParamTypes()) {
            paramType.accept(this);
        }
        type.getReturnType().accept(this);
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
    public Void visitAwaitExpr(com.firefly.compiler.ast.expr.AwaitExpr expr) {
        // Check await usage
        if (!inAsyncContext) {
            reporter.error("FF007",
                "await can only be used in async functions",
                expr.getLocation(),
                "Add 'async' keyword to the function declaration");
        }
        
        expr.getFuture().accept(this);
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
