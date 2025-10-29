package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.type.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles type parameter substitution for generic types.
 * When instantiating a generic function or class, this replaces type parameters
 * with concrete types.
 * 
 * Example: identity<Int>(5) substitutes T -> Int in fn identity<T>(x: T) -> T
 */
public class TypeSubstitution {
    
    private final Map<String, Type> substitutions;
    
    public TypeSubstitution() {
        this.substitutions = new HashMap<>();
    }
    
    public TypeSubstitution(Map<String, Type> substitutions) {
        this.substitutions = new HashMap<>(substitutions);
    }
    
    /**
     * Add a substitution mapping from type parameter name to concrete type
     */
    public void addSubstitution(String typeParamName, Type concreteType) {
        substitutions.put(typeParamName, concreteType);
    }
    
    /**
     * Create substitutions from type parameters and type arguments
     */
    public static TypeSubstitution fromLists(List<String> typeParams, List<Type> typeArgs) {
        TypeSubstitution sub = new TypeSubstitution();
        
        if (typeParams.size() != typeArgs.size()) {
            throw new IllegalArgumentException(
                String.format("Type parameter count mismatch: expected %d, got %d",
                    typeParams.size(), typeArgs.size()));
        }
        
        for (int i = 0; i < typeParams.size(); i++) {
            sub.addSubstitution(typeParams.get(i), typeArgs.get(i));
        }
        
        return sub;
    }
    
    /**
     * Apply substitutions to a type, replacing type parameters with concrete types
     */
    public Type substitute(Type type) {
        if (type instanceof TypeParameter) {
            TypeParameter typeParam = (TypeParameter) type;
            Type substituted = substitutions.get(typeParam.getName());
            
            if (substituted != null) {
                return substituted;
            }
            
            // If no substitution found, return the type parameter as-is
            return type;
            
        } else if (type instanceof GenericType) {
            GenericType genericType = (GenericType) type;
            
            // Recursively substitute type arguments
            List<Type> substitutedArgs = genericType.getTypeArguments().stream()
                .map(this::substitute)
                .collect(Collectors.toList());
            
            return new GenericType(
                genericType.getBaseName(),
                substitutedArgs,
                genericType.getLocation()
            );
            
        } else if (type instanceof OptionalType) {
            OptionalType optType = (OptionalType) type;
            Type substitutedInner = substitute(optType.getInnerType());
            return new OptionalType(substitutedInner);
            
        } else if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            Type substitutedElement = substitute(arrayType.getElementType());
            return new ArrayType(substitutedElement);
            
        } else if (type instanceof FunctionType) {
            FunctionType funcType = (FunctionType) type;

            List<Type> substitutedParams = funcType.getParamTypes().stream()
                .map(this::substitute)
                .collect(Collectors.toList());

            Type substitutedReturn = substitute(funcType.getReturnType());

            return new FunctionType(substitutedParams, substitutedReturn);
            
        } else {
            // PrimitiveType, NamedType, etc. - return as-is
            return type;
        }
    }
    
    /**
     * Check if a substitution exists for a type parameter
     */
    public boolean hasSubstitution(String typeParamName) {
        return substitutions.containsKey(typeParamName);
    }
    
    /**
     * Get the substitution for a type parameter
     */
    public Type getSubstitution(String typeParamName) {
        return substitutions.get(typeParamName);
    }
    
    /**
     * Get all substitutions
     */
    public Map<String, Type> getSubstitutions() {
        return new HashMap<>(substitutions);
    }
    
    @Override
    public String toString() {
        return substitutions.entrySet().stream()
            .map(e -> e.getKey() + " -> " + e.getValue().getName())
            .collect(Collectors.joining(", ", "{", "}"));
    }
}

