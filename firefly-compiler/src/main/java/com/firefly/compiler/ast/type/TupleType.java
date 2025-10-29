package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a tuple type: (T1, T2, ..., Tn)
 * 
 * Examples:
 * - (Int, String)
 * - (String, Int, Boolean)
 * - (User, Option<String>)
 */
public class TupleType implements Type {
    private final List<Type> elementTypes;
    private final SourceLocation location;
    
    public TupleType(List<Type> elementTypes, SourceLocation location) {
        this.elementTypes = elementTypes;
        this.location = location;
    }
    
    public List<Type> getElementTypes() {
        return elementTypes;
    }
    
    public int getArity() {
        return elementTypes.size();
    }
    
    @Override
    public String getName() {
        return "(" + elementTypes.stream()
            .map(Type::getName)
            .collect(Collectors.joining(", ")) + ")";
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitTupleType(this);
    }
}

