package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a generic/parameterized type like List<T>, Option<String>, Map<K, V>.
 * This is used for type instantiation with concrete type arguments.
 */
public class GenericType implements Type {
    private final String name;
    private final List<Type> typeArguments;
    private final SourceLocation location;
    
    public GenericType(String name, List<Type> typeArguments, SourceLocation location) {
        this.name = name;
        this.typeArguments = typeArguments;
        this.location = location;
    }
    
    public List<Type> getTypeArguments() {
        return typeArguments;
    }
    
    @Override
    public String getName() {
        if (typeArguments.isEmpty()) {
            return name;
        }
        String args = typeArguments.stream()
            .map(Type::getName)
            .collect(Collectors.joining(", "));
        return name + "<" + args + ">";
    }
    
    /**
     * Get the base name without type arguments (e.g., "List" from "List<T>")
     */
    public String getBaseName() {
        return name;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitGenericType(this);
    }
    
    @Override
    public String toString() {
        return getName();
    }
}

