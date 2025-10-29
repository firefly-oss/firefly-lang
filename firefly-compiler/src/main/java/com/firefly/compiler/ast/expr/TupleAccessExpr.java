package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Tuple field access expression: tuple.0, tuple.1, etc.
 * 
 * Examples:
 * - pair.0  // First element
 * - triple.1  // Second element
 * - nested.0.1  // Nested access
 */
public class TupleAccessExpr extends Expression {
    private final Expression tuple;
    private final int index;
    
    public TupleAccessExpr(Expression tuple, int index, SourceLocation location) {
        super(location);
        this.tuple = tuple;
        this.index = index;
    }
    
    public Expression getTuple() {
        return tuple;
    }
    
    public int getIndex() {
        return index;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitTupleAccessExpr(this);
    }
}

