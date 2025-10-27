package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Assignment expression: target = value
 * Only valid for mutable variables.
 */
public class AssignmentExpr extends Expression {
    private final Expression target;  // Usually an IdentifierExpr
    private final Expression value;
    
    public AssignmentExpr(Expression target, Expression value, SourceLocation location) {
        super(location);
        this.target = target;
        this.value = value;
    }
    
    public Expression getTarget() {
        return target;
    }
    
    public Expression getValue() {
        return value;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitAssignmentExpr(this);
    }
}
