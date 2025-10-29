package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

/**
 * Typed variable pattern: binds a value to a variable name with explicit type annotation
 */
public class TypedVariablePattern extends Pattern {
    
    private final String name;
    private final Type type;
    private final boolean isMutable;
    
    public TypedVariablePattern(String name, Type type, boolean isMutable, SourceLocation location) {
        super(location);
        this.name = name;
        this.type = type;
        this.isMutable = isMutable;
    }
    
    public String getName() {
        return name;
    }
    
    public Type getType() {
        return type;
    }
    
    public boolean isMutable() {
        return isMutable;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}
