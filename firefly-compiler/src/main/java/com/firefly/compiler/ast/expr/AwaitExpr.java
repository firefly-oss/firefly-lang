package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Represents an await expression in the AST.
 * Grammar: expression '.await'
 * 
 * This expression suspends execution until the Future completes and returns its value.
 * Can only be used inside async functions.
 */
public class AwaitExpr extends Expression {
    
    private final Expression future;
    
    public AwaitExpr(Expression future, SourceLocation location) {
        super(location);
        this.future = future;
    }
    
    public Expression getFuture() {
        return future;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitAwaitExpr(this);
    }
}
