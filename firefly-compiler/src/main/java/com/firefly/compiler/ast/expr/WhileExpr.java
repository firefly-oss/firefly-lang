package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * While loop expression: while condition { body }
 */
public class WhileExpr extends Expression {
    private final Expression condition;
    private final BlockExpr body;
    
    public WhileExpr(Expression condition, BlockExpr body, SourceLocation location) {
        super(location);
        this.condition = condition;
        this.body = body;
    }
    
    public Expression getCondition() {
        return condition;
    }
    
    public BlockExpr getBody() {
        return body;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitWhileExpr(this);
    }
}
