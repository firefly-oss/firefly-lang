package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

public class ArrayType implements Type {
    private final Type elementType;
    
    public ArrayType(Type elementType) {
        this.elementType = elementType;
    }
    
    public Type getElementType() {
        return elementType;
    }
    
    @Override
    public String getName() {
        return "[" + elementType.getName() + "]";
    }
    
    @Override
    public SourceLocation getLocation() {
        return SourceLocation.unknown();
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitArrayType(this);
    }
}
