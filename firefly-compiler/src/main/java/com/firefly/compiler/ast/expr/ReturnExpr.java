package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.Optional;

/**
 * Return expression: return expr
 */
public class ReturnExpr extends Expression {
    private final Expression value; // May be null for bare return
    
    public ReturnExpr(Expression value, SourceLocation location) {
        super(location);
        this.value = value;
    }
    
    public Optional<Expression> getValue() {
        return Optional.ofNullable(value);
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitReturnExpr(this);
    }
}
