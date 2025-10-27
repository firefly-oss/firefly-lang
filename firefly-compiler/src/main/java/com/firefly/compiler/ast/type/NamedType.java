package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

public class NamedType implements Type {
    private final String name;
    
    public NamedType(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public SourceLocation getLocation() {
        return SourceLocation.unknown();
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitNamedType(this);
    }
}
