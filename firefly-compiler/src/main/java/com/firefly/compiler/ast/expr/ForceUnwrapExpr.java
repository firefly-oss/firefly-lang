package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Force unwrap expression: value!!
 * Unwraps optional value or throws if null.
 */
public class ForceUnwrapExpr extends Expression {
    private final Expression expression;
    
    public ForceUnwrapExpr(Expression expression, SourceLocation location) {
        super(location);
        this.expression = expression;
    }
    
    public Expression getExpression() {
        return expression;
    }
    
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitForceUnwrapExpr(this);
    }
    
    @Override
    public String toString() {
        return expression + "!!";
    }
}
