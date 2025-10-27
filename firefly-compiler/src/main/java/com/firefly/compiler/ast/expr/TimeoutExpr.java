package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Timeout expression - executes a block with a timeout.
 * Grammar: timeout(expression) blockExpression
 * 
 * This is a unique Firefly feature for time-bounded operations.
 * If the operation doesn't complete within the timeout, it returns an error.
 */
public class TimeoutExpr extends Expression {
    
    private final Expression duration;
    private final BlockExpr body;
    
    public TimeoutExpr(Expression duration, BlockExpr body, SourceLocation location) {
        super(location);
        this.duration = duration;
        this.body = body;
    }
    
    public Expression getDuration() {
        return duration;
    }
    
    public BlockExpr getBody() {
        return body;
    }
    
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitTimeoutExpr(this);
    }
}
