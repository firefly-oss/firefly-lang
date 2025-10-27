package com.firefly.compiler.ast;

import com.firefly.compiler.ast.expr.Expression;

/**
 * Expression statement: expression;
 */
public class ExprStatement extends Statement {
    
    private final Expression expression;
    
    public ExprStatement(Expression expression, SourceLocation location) {
        super(location);
        this.expression = expression;
    }
    
    public Expression getExpression() {
        return expression;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitExprStatement(this);
    }
}
