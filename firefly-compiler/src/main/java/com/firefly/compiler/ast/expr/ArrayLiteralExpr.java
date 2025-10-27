package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Array literal expression: [1, 2, 3]
 * 
 * Examples:
 * - [1, 2, 3, 4, 5]
 * - ["hello", "world"]
 * - [true, false, true]
 */
public class ArrayLiteralExpr extends Expression {
    
    private final List<Expression> elements;
    
    public ArrayLiteralExpr(List<Expression> elements, SourceLocation location) {
        super(location);
        this.elements = elements;
    }
    
    public List<Expression> getElements() {
        return elements;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitArrayLiteralExpr(this);
    }
}
