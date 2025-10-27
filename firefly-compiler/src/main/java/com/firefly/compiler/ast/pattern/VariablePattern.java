package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Variable pattern: binds a value to a variable name
 */
public class VariablePattern extends Pattern {
    
    private final String name;
    private final boolean isMutable;
    
    public VariablePattern(String name, boolean isMutable, SourceLocation location) {
        super(location);
        this.name = name;
        this.isMutable = isMutable;
    }
    
    public String getName() {
        return name;
    }
    
    public boolean isMutable() {
        return isMutable;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}
