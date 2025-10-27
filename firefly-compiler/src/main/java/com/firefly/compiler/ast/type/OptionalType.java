package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

public class OptionalType implements Type {
    private final Type innerType;
    
    public OptionalType(Type innerType) {
        this.innerType = innerType;
    }
    
    public Type getInnerType() {
        return innerType;
    }
    
    @Override
    public String getName() {
        return innerType.getName() + "?";
    }
    
    @Override
    public SourceLocation getLocation() {
        return SourceLocation.unknown();
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitOptionalType(this);
    }
}
