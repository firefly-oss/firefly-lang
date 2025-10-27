package com.firefly.compiler.ast;

/**
 * Represents a statement in a block.
 * Can be a let binding or an expression statement.
 */
public abstract class Statement implements AstNode {
    
    private final SourceLocation location;
    
    protected Statement(SourceLocation location) {
        this.location = location;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
}
