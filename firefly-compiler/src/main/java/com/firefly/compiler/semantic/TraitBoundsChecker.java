package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.decl.FunctionDecl;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.*;

/**
 * Validates trait bounds on type parameters.
 * Ensures that types satisfy their trait bounds.
 */
public class TraitBoundsChecker {
    
    private final DiagnosticReporter reporter;
    private final TraitChecker traitChecker;
    
    public TraitBoundsChecker(DiagnosticReporter reporter, TraitChecker traitChecker) {
        this.reporter = reporter;
        this.traitChecker = traitChecker;
    }
    
    /**
     * Check that a type satisfies the bounds of a type parameter.
     */
    public boolean checkBounds(Type actualType, TypeParameter typeParam) {
        if (!typeParam.hasBounds()) {
            return true; // No bounds to check
        }
        
        for (Type bound : typeParam.getBounds()) {
            if (!satisfiesBound(actualType, bound)) {
                reporter.error("BOUNDS001",
                    "Type " + actualType.getName() + " does not satisfy bound " + bound.getName(),
                    actualType.getLocation());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if a type satisfies a single bound.
     */
    private boolean satisfiesBound(Type type, Type bound) {
        // If bound is a trait, check if type implements it
        if (bound instanceof NamedType) {
            String boundName = bound.getName();
            return traitChecker.typeImplementsTrait(type, boundName);
        }
        
        // For other bounds, do simple name matching (simplified)
        return type.getName().equals(bound.getName());
    }
    
    /**
     * Validate all type parameters in a function declaration.
     * Checks that all trait bounds are valid and that the bounds are satisfied.
     * Example: fn foo<T: Printable + Comparable>(x: T) -> T
     */
    public void validateFunctionTypeParameters(FunctionDecl func) {
        for (TypeParameter typeParam : func.getTypeParameters()) {
            validateTypeParameter(typeParam);
        }
    }

    /**
     * Validate a single type parameter.
     */
    private void validateTypeParameter(TypeParameter typeParam) {
        // Check that all bounds are valid traits
        for (Type bound : typeParam.getBounds()) {
            if (bound instanceof NamedType) {
                String boundName = bound.getName();
                if (traitChecker.getTrait(boundName) == null) {
                    reporter.error("BOUNDS002",
                        "Trait bound '" + boundName + "' is not defined",
                        bound.getLocation());
                }
            }
        }
    }
    
    /**
     * Check that type arguments satisfy bounds when instantiating a generic type.
     */
    public boolean checkTypeArgumentBounds(
        List<TypeParameter> typeParams,
        List<Type> typeArgs
    ) {
        if (typeParams.size() != typeArgs.size()) {
            return false; // Arity mismatch
        }
        
        boolean allSatisfied = true;
        for (int i = 0; i < typeParams.size(); i++) {
            TypeParameter param = typeParams.get(i);
            Type arg = typeArgs.get(i);
            
            if (!checkBounds(arg, param)) {
                allSatisfied = false;
            }
        }
        
        return allSatisfied;
    }
    
    /**
     * Infer trait bounds from usage context.
     * This is used for better error messages.
     */
    public Set<String> inferRequiredTraits(Type type) {
        Set<String> requiredTraits = new HashSet<>();
        
        if (type instanceof TypeParameter) {
            TypeParameter typeParam = (TypeParameter) type;
            for (Type bound : typeParam.getBounds()) {
                if (bound instanceof NamedType) {
                    requiredTraits.add(bound.getName());
                }
            }
        }
        
        return requiredTraits;
    }
}

