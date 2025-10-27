package com.firefly.compiler.ast;

import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.type.*;

/**
 * Visitor pattern for traversing the AST.
 * 
 * @param <T> Return type of visit methods
 */
public interface AstVisitor<T> {
    
    // Top-level nodes
    T visitCompilationUnit(CompilationUnit unit);
    T visitImportDeclaration(ImportDeclaration decl);
    
    // Declarations
    T visitFunctionDecl(FunctionDecl decl);
    T visitClassDecl(ClassDecl decl);
    T visitInterfaceDecl(InterfaceDecl decl);
    T visitStructDecl(StructDecl decl);
    T visitDataDecl(DataDecl decl);
    T visitTraitDecl(TraitDecl decl);
    T visitImplDecl(ImplDecl decl);
    
    // Statements
    T visitLetStatement(LetStatement stmt);
    T visitExprStatement(ExprStatement stmt);
    
    // Expressions
    T visitBinaryExpr(BinaryExpr expr);
    T visitUnaryExpr(UnaryExpr expr);
    T visitCallExpr(CallExpr expr);
    T visitFieldAccessExpr(FieldAccessExpr expr);
    T visitIndexAccessExpr(IndexAccessExpr expr);
    T visitLiteralExpr(LiteralExpr expr);
    T visitIdentifierExpr(IdentifierExpr expr);
    T visitIfExpr(IfExpr expr);
    T visitMatchExpr(MatchExpr expr);
    T visitBlockExpr(BlockExpr expr);
    T visitLambdaExpr(LambdaExpr expr);
    T visitForExpr(ForExpr expr);
    T visitWhileExpr(WhileExpr expr);
    T visitReturnExpr(ReturnExpr expr);
    T visitBreakExpr(BreakExpr expr);
    T visitContinueExpr(ContinueExpr expr);
    T visitConcurrentExpr(ConcurrentExpr expr);
    T visitRaceExpr(RaceExpr expr);
    T visitTimeoutExpr(TimeoutExpr expr);
    T visitCoalesceExpr(CoalesceExpr expr);
    T visitAssignmentExpr(AssignmentExpr expr);
    T visitNewExpr(NewExpr expr);
    T visitArrayLiteralExpr(ArrayLiteralExpr expr);
    
    // Patterns
    T visitPattern(Pattern pattern);
    
    // Types
    T visitPrimitiveType(PrimitiveType type);
    T visitNamedType(NamedType type);
    T visitOptionalType(OptionalType type);
    T visitArrayType(ArrayType type);
    T visitFunctionType(FunctionType type);
}
