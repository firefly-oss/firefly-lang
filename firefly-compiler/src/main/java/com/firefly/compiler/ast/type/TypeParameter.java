package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Represents a type parameter like T in fn identity<T>(value: T) -> T.
 * Can have bounds like T: Printable or T: Printable + Comparable.
 */
public class TypeParameter implements Type {
    private final String name;
    private final List<Type> bounds; // Trait bounds
    private final SourceLocation location;
    
    public TypeParameter(String name, SourceLocation location) {
        this(name, List.of(), location);
    }
    
    public TypeParameter(String name, List<Type> bounds, SourceLocation location) {
        this.name = name;
        this.bounds = bounds;
        this.location = location;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public List<Type> getBounds() {
        return bounds;
    }
    
    public boolean hasBounds() {
        return !bounds.isEmpty();
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitTypeParameter(this);
    }
    
    @Override
    public String toString() {
        if (bounds.isEmpty()) {
            return name;
        }
        return name + ": " + String.join(" + ", bounds.stream().map(Type::getName).toList());
    }
}

