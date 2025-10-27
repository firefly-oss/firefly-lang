package com.firefly.compiler.ast;

/**
 * Base class for all pattern nodes in the AST.
 * Used in match expressions, let bindings, function parameters, etc.
 */
public abstract class Pattern implements AstNode {
    
    private final SourceLocation location;
    
    protected Pattern(SourceLocation location) {
        this.location = location;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public abstract <T> T accept(AstVisitor<T> visitor);
}
