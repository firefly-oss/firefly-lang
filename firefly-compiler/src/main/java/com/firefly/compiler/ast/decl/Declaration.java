package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.AstNode;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Base class for all top-level declarations.
 */
public abstract class Declaration implements AstNode {
    
    private final SourceLocation location;
    
    protected Declaration(SourceLocation location) {
        this.location = location;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
}
