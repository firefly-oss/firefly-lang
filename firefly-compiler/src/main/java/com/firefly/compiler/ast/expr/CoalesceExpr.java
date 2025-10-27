package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Null-coalescing expression - returns the left side if non-null, otherwise the right.
 * Grammar: expression '??' expression
 * 
 * This is similar to ?? in C#, Kotlin, and Swift.
 * Example: user.name ?? "Unknown"
 */
public class CoalesceExpr extends Expression {
    
    private final Expression left;
    private final Expression right;
    
    public CoalesceExpr(Expression left, Expression right, SourceLocation location) {
        super(location);
        this.left = left;
        this.right = right;
    }
    
    public Expression getLeft() {
        return left;
    }
    
    public Expression getRight() {
        return right;
    }
    
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitCoalesceExpr(this);
    }
}
