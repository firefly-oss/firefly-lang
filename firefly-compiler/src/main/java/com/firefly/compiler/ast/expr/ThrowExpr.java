package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Throw expression: throw exception
 * 
 * Examples:
 * - throw new IOException("File not found")
 * - throw error
 * - throw RuntimeException("Invalid state")
 */
public class ThrowExpr extends Expression {
    
    private final Expression exception;
    
    public ThrowExpr(Expression exception, SourceLocation location) {
        super(location);
        this.exception = exception;
    }
    
    public Expression getException() {
        return exception;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitThrowExpr(this);
    }
    
    @Override
    public String toString() {
        return "ThrowExpr(" + exception + ")";
    }
}

