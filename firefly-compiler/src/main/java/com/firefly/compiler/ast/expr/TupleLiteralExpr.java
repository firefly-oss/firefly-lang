package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Tuple literal expression: (expr1, expr2, ...)
 * 
 * Examples:
 * - (1, 2)
 * - ("hello", 42, true)
 * - (user, "active")
 */
public class TupleLiteralExpr extends Expression {
    
    private final List<Expression> elements;
    
    public TupleLiteralExpr(List<Expression> elements, SourceLocation location) {
        super(location);
        this.elements = elements;
    }
    
    public List<Expression> getElements() {
        return elements;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitTupleLiteralExpr(this);
    }
}

