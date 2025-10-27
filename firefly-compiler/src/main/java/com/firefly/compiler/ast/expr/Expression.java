package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstNode;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

/**
 * Base class for all expressions in Firefly.
 * Expressions produce values and can be used anywhere a value is expected.
 */
public abstract class Expression implements AstNode {
    
    private final SourceLocation location;
    
    /**
     * The inferred or declared type of this expression.
     * Null until type checking phase.
     */
    private Type type;
    
    protected Expression(SourceLocation location) {
        this.location = location;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    public Type getType() {
        return type;
    }
    
    public void setType(Type type) {
        this.type = type;
    }
}
