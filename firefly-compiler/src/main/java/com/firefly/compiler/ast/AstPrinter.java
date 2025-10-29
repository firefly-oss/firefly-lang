package com.firefly.compiler.ast;

import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;

/**
 * Pretty printer for Firefly AST.
 */
public class AstPrinter implements AstVisitor<String> {
    
    @Override
    public String visitCompilationUnit(CompilationUnit unit) {
        return "CompilationUnit";
    }
    
    @Override
    public String visitUseDeclaration(UseDeclaration decl) {
        return "Import: " + decl.getModulePath();
    }
    
    @Override
    public String visitFunctionDecl(FunctionDecl decl) {
        return "FunctionDecl: " + decl.getName();
    }
    
    @Override
    public String visitClassDecl(ClassDecl decl) {
        return "ClassDecl: " + decl.getName();
    }
    
    @Override
    public String visitInterfaceDecl(InterfaceDecl decl) {
        return "InterfaceDecl: " + decl.getName();
    }
    
    @Override
    public String visitActorDecl(ActorDecl decl) {
        return "ActorDecl: " + decl.getName();
    }
    
    @Override
    public String visitStructDecl(StructDecl decl) {
        return "StructDecl";
    }
    
    @Override
    public String visitSparkDecl(SparkDecl decl) {
        return "SparkDecl: " + decl.getName();
    }
    
    @Override
    public String visitDataDecl(DataDecl decl) {
        return "DataDecl";
    }
    
    @Override
    public String visitTraitDecl(TraitDecl decl) {
        return "TraitDecl";
    }
    
    @Override
    public String visitImplDecl(ImplDecl decl) {
        return "ImplDecl";
    }
    
    @Override
    public String visitLetStatement(LetStatement stmt) {
        return "LetStatement";
    }
    
    @Override
    public String visitExprStatement(ExprStatement stmt) {
        return "ExprStatement";
    }
    
    @Override
    public String visitBinaryExpr(BinaryExpr expr) {
        return "BinaryExpr: " + expr.getOperator();
    }
    
    @Override
    public String visitUnaryExpr(UnaryExpr expr) {
        return "UnaryExpr: " + expr.getOperator();
    }
    
    @Override
    public String visitCallExpr(CallExpr expr) {
        return "CallExpr";
    }
    
    @Override
    public String visitFieldAccessExpr(FieldAccessExpr expr) {
        return "FieldAccess: " + expr.getFieldName();
    }

    @Override
    public String visitTupleAccessExpr(TupleAccessExpr expr) {
        return "TupleAccess: ." + expr.getIndex();
    }

    @Override
    public String visitIndexAccessExpr(IndexAccessExpr expr) {
        return "IndexAccess";
    }
    
    @Override
    public String visitLiteralExpr(LiteralExpr expr) {
        return "Literal: " + expr.getValue();
    }
    
    @Override
    public String visitIdentifierExpr(IdentifierExpr expr) {
        return "Identifier: " + expr.getName();
    }
    
    @Override
    public String visitIfExpr(IfExpr expr) {
        return "IfExpr";
    }
    
    @Override
    public String visitMatchExpr(MatchExpr expr) {
        return "MatchExpr";
    }
    
    @Override
    public String visitBlockExpr(BlockExpr expr) {
        return "BlockExpr";
    }
    
    @Override
    public String visitLambdaExpr(LambdaExpr expr) {
        StringBuilder sb = new StringBuilder("lambda (");
        sb.append(String.join(", ", expr.getParameters()));
        sb.append(") -> ");
        sb.append(expr.getBody().accept(this));
        return sb.toString();
    }
    
    @Override
    public String visitForExpr(ForExpr expr) {
        return "ForExpr";
    }
    
    @Override
    public String visitWhileExpr(WhileExpr expr) {
        return "WhileExpr";
    }
    
    @Override
    public String visitReturnExpr(ReturnExpr expr) {
        return "ReturnExpr";
    }
    
    @Override
    public String visitBreakExpr(BreakExpr expr) {
        return "BreakExpr";
    }
    
    @Override
    public String visitContinueExpr(ContinueExpr expr) {
        return "ContinueExpr";
    }

    @Override
    public String visitTryExpr(TryExpr expr) {
        return "TryExpr";
    }

    @Override
    public String visitThrowExpr(ThrowExpr expr) {
        return "ThrowExpr";
    }

    @Override
    public String visitConcurrentExpr(ConcurrentExpr expr) {
        return "ConcurrentExpr";
    }
    
    @Override
    public String visitRaceExpr(RaceExpr expr) {
        return "RaceExpr";
    }
    
    @Override
    public String visitTimeoutExpr(TimeoutExpr expr) {
        return "TimeoutExpr";
    }
    
    @Override
    public String visitAwaitExpr(AwaitExpr expr) {
        return "AwaitExpr";
    }
    
    @Override
    public String visitCoalesceExpr(CoalesceExpr expr) {
        return "CoalesceExpr";
    }
    
    @Override
    public String visitSafeAccessExpr(SafeAccessExpr expr) {
        return "SafeAccess: " + expr.getObject().accept(this) + "?." + expr.getFieldName();
    }
    
    @Override
    public String visitForceUnwrapExpr(ForceUnwrapExpr expr) {
        return "ForceUnwrap: " + expr.getExpression().accept(this) + "!!";
    }
    
    @Override
    public String visitAssignmentExpr(AssignmentExpr expr) {
        return "AssignmentExpr";
    }
    
    @Override
    public String visitNewExpr(com.firefly.compiler.ast.expr.NewExpr expr) {
        return "NewExpr: " + expr.getType().getName();
    }
    
    @Override
    public String visitArrayLiteralExpr(com.firefly.compiler.ast.expr.ArrayLiteralExpr expr) {
        return "ArrayLiteral[" + expr.getElements().size() + "]";
    }

    @Override
    public String visitTupleLiteralExpr(com.firefly.compiler.ast.expr.TupleLiteralExpr expr) {
        return "TupleLiteral[" + expr.getElements().size() + "]";
    }
    
    @Override
    public String visitStructLiteralExpr(com.firefly.compiler.ast.expr.StructLiteralExpr expr) {
        return "StructLiteral:" + expr.getStructName() + "[" + expr.getFieldInits().size() + " fields]";
    }
    
    @Override
    public String visitMapLiteralExpr(com.firefly.compiler.ast.expr.MapLiteralExpr expr) {
        return "MapLiteral[" + expr.getEntries().size() + " entries]";
    }

    @Override
    public String visitPattern(Pattern pattern) {
        return "Pattern";
    }
    
    @Override
    public String visitPrimitiveType(PrimitiveType type) {
        return "PrimitiveType: " + type.getName();
    }
    
    @Override
    public String visitNamedType(NamedType type) {
        return "NamedType: " + type.getName();
    }
    
    @Override
    public String visitOptionalType(OptionalType type) {
        return "OptionalType";
    }
    
    @Override
    public String visitArrayType(ArrayType type) {
        return "ArrayType";
    }
    
    @Override
    public String visitFunctionType(FunctionType type) {
        return "FunctionType";
    }

    @Override
    public String visitGenericType(GenericType type) {
        return "GenericType(" + type.getName() + ")";
    }

    @Override
    public String visitTypeParameter(TypeParameter type) {
        return "TypeParameter(" + type.getName() + ")";
    }

    @Override
    public String visitTupleType(com.firefly.compiler.ast.type.TupleType type) {
        return "TupleType[" + type.getArity() + "]";
    }
    
    @Override
    public String visitTypeAliasDecl(com.firefly.compiler.ast.decl.TypeAliasDecl decl) {
        return "TypeAlias: " + decl.getName() + " = " + decl.getTargetType().getName();
    }
    
    @Override
    public String visitExceptionDecl(com.firefly.compiler.ast.decl.ExceptionDecl decl) {
        String superClass = decl.getSuperException().orElse("FlyException");
        return "Exception: " + decl.getName() + " extends " + superClass;
    }
}
