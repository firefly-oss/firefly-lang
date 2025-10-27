package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Index access expression: array[index]
 */
public class IndexAccessExpr extends Expression {
    private final Expression object;
    private final Expression index;
    
    public IndexAccessExpr(Expression object, Expression index, SourceLocation location) {
        super(location);
        this.object = object;
        this.index = index;
    }
    
    public Expression getObject() {
        return object;
    }
    
    public Expression getIndex() {
        return index;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitIndexAccessExpr(this);
    }
}
