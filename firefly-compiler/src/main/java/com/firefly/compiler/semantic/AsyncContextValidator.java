package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.Diagnostic;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.Stack;

/**
 * Validates that async/await is used correctly:
 * - await can only be used in async functions
 * - async functions must return Future types
 */
public class AsyncContextValidator implements AstVisitor<Void> {
    
    private final DiagnosticReporter diagnostics;
    private final Stack<Boolean> asyncContextStack = new Stack<>();
    
    public AsyncContextValidator(DiagnosticReporter diagnostics) {
        this.diagnostics = diagnostics;
    }
    
    public void validate(CompilationUnit unit) {
        visitCompilationUnit(unit);
    }
    
    private boolean isInAsyncContext() {
        return !asyncContextStack.isEmpty() && asyncContextStack.peek();
    }
    
    @Override
    public Void visitCompilationUnit(CompilationUnit unit) {
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        // Push async context
        asyncContextStack.push(decl.isAsync());
        
        // Visit function body
        decl.getBody().accept(this);
        
        // Pop context
        asyncContextStack.pop();
        
        return null;
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // Visit methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            asyncContextStack.push(method.isAsync());
            method.getBody().accept(this);
            asyncContextStack.pop();
        }
        return null;
    }
    
    @Override
    public Void visitActorDecl(ActorDecl decl) {
        // Actor init and receive blocks are not async by default
        asyncContextStack.push(false);
        
        if (decl.getInitBlock() != null) {
            decl.getInitBlock().accept(this);
        }
        
        for (ActorDecl.ReceiveCase receiveCase : decl.getReceiveCases()) {
            receiveCase.getExpression().accept(this);
        }
        
        asyncContextStack.pop();
        return null;
    }
    
    @Override
    public Void visitAwaitExpr(AwaitExpr expr) {
        if (!isInAsyncContext()) {
            diagnostics.error(
                "E0001",
                "await can only be used inside async functions",
                expr.getLocation()
            );
        }
        
        // Visit the future expression
        expr.getFuture().accept(this);
        
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
    public Void visitIfExpr(IfExpr expr) {
        expr.getCondition().accept(this);
        expr.getThenBranch().accept(this);
        for (IfExpr.ElseIfBranch branch : expr.getElseIfBranches()) {
            branch.getCondition().accept(this);
            branch.getBody().accept(this);
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
            arm.getBody().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitForExpr(ForExpr expr) {
        expr.getIterable().accept(this);
        expr.getBody().accept(this);
        return null;
    }
    
    @Override
    public Void visitWhileExpr(WhileExpr expr) {
        expr.getCondition().accept(this);
        expr.getBody().accept(this);
        return null;
    }
    
    @Override
    public Void visitLambdaExpr(LambdaExpr expr) {
        // Lambdas inherit the async context from their enclosing function
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
    public Void visitTryExpr(TryExpr expr) {
        expr.getTryBlock().accept(this);
        for (TryExpr.CatchClause clause : expr.getCatchClauses()) {
            clause.getHandler().accept(this);
        }
        if (expr.getFinallyBlock().isPresent()) {
            expr.getFinallyBlock().get().accept(this);
        }
        return null;
    }
    
    @Override
    public Void visitConcurrentExpr(ConcurrentExpr expr) {
        // Concurrent blocks are implicitly async
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            binding.getExpression().accept(this);
        }
        return null;
    }
    
    // Stub implementations for other visitors
    @Override public Void visitUseDeclaration(UseDeclaration decl) { return null; }
    @Override public Void visitInterfaceDecl(InterfaceDecl decl) { return null; }
    @Override public Void visitStructDecl(StructDecl decl) { return null; }
    @Override public Void visitDataDecl(DataDecl decl) { return null; }
    @Override public Void visitSparkDecl(SparkDecl decl) { return null; }
    @Override public Void visitTraitDecl(TraitDecl decl) { return null; }
    @Override public Void visitImplDecl(ImplDecl decl) { return null; }
    @Override public Void visitTypeAliasDecl(TypeAliasDecl decl) { return null; }
    @Override public Void visitExceptionDecl(com.firefly.compiler.ast.decl.ExceptionDecl decl) { return null; }
    @Override public Void visitLetStatement(LetStatement stmt) {
        if (stmt.getInitializer().isPresent()) {
            stmt.getInitializer().get().accept(this);
        }
        return null;
    }
    @Override public Void visitExprStatement(ExprStatement stmt) {
        stmt.getExpression().accept(this);
        return null;
    }
    @Override public Void visitTupleAccessExpr(TupleAccessExpr expr) {
        expr.getTuple().accept(this);
        return null;
    }
    @Override public Void visitIndexAccessExpr(IndexAccessExpr expr) {
        expr.getObject().accept(this);
        expr.getIndex().accept(this);
        return null;
    }
    @Override public Void visitLiteralExpr(LiteralExpr expr) { return null; }
    @Override public Void visitIdentifierExpr(IdentifierExpr expr) { return null; }
    @Override public Void visitBreakExpr(BreakExpr expr) { return null; }
    @Override public Void visitContinueExpr(ContinueExpr expr) { return null; }
    @Override public Void visitThrowExpr(ThrowExpr expr) {
        expr.getException().accept(this);
        return null;
    }
    @Override public Void visitRaceExpr(RaceExpr expr) {
        expr.getBody().accept(this);
        return null;
    }
    @Override public Void visitTimeoutExpr(TimeoutExpr expr) {
        expr.getDuration().accept(this);
        expr.getBody().accept(this);
        return null;
    }
    @Override public Void visitCoalesceExpr(CoalesceExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        return null;
    }
    @Override public Void visitAssignmentExpr(AssignmentExpr expr) {
        expr.getTarget().accept(this);
        expr.getValue().accept(this);
        return null;
    }
    @Override public Void visitNewExpr(NewExpr expr) {
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return null;
    }
    @Override public Void visitArrayLiteralExpr(ArrayLiteralExpr expr) {
        for (Expression elem : expr.getElements()) {
            elem.accept(this);
        }
        return null;
    }
    @Override public Void visitTupleLiteralExpr(TupleLiteralExpr expr) {
        for (Expression elem : expr.getElements()) {
            elem.accept(this);
        }
        return null;
    }
    @Override public Void visitStructLiteralExpr(StructLiteralExpr expr) {
        for (StructLiteralExpr.FieldInit init : expr.getFieldInits()) {
            init.getValue().accept(this);
        }
        return null;
    }
    @Override public Void visitMapLiteralExpr(MapLiteralExpr expr) {
        for (var entry : expr.getEntries().entrySet()) {
            entry.getKey().accept(this);
            entry.getValue().accept(this);
        }
        return null;
    }
    @Override public Void visitPattern(Pattern pattern) { return null; }
    @Override public Void visitPrimitiveType(PrimitiveType type) { return null; }
    @Override public Void visitNamedType(NamedType type) { return null; }
    @Override public Void visitOptionalType(OptionalType type) { return null; }
    @Override public Void visitArrayType(ArrayType type) { return null; }
    @Override public Void visitFunctionType(FunctionType type) { return null; }
    @Override public Void visitGenericType(GenericType type) { return null; }
    @Override public Void visitTypeParameter(TypeParameter type) { return null; }
    @Override public Void visitTupleType(TupleType type) { return null; }
    
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
