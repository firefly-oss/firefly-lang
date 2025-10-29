package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.*;

/**
 * Validates trait declarations and implementations.
 */
public class TraitChecker {
    
    private final DiagnosticReporter reporter;
    private final Map<String, TraitDecl> traits = new HashMap<>();
    private final Map<String, List<ImplDecl>> implementations = new HashMap<>();
    
    public TraitChecker(DiagnosticReporter reporter) {
        this.reporter = reporter;
    }
    
    /**
     * Register a trait declaration.
     */
    public void registerTrait(TraitDecl trait) {
        String traitName = trait.getName();
        
        if (traits.containsKey(traitName)) {
            reporter.error("TRAIT001",
                "Trait '" + traitName + "' is already defined",
                trait.getLocation());
            return;
        }
        
        traits.put(traitName, trait);
        
        // Validate trait members
        validateTraitMembers(trait);
    }
    
    /**
     * Register an implementation.
     */
    public void registerImpl(ImplDecl impl) {
        String traitName = impl.getName();
        
        // Check if trait exists
        if (!traits.containsKey(traitName)) {
            reporter.error("TRAIT002",
                "Cannot implement undefined trait '" + traitName + "'",
                impl.getLocation());
            return;
        }
        
        // Add to implementations list
        implementations.computeIfAbsent(traitName, k -> new ArrayList<>()).add(impl);
        
        // Validate implementation
        validateImpl(impl);
    }
    
    /**
     * Validate trait members (function signatures).
     */
    private void validateTraitMembers(TraitDecl trait) {
        Set<String> memberNames = new HashSet<>();
        
        for (TraitDecl.FunctionSignature member : trait.getMembers()) {
            String memberName = member.getName();
            
            // Check for duplicate members
            if (memberNames.contains(memberName)) {
                reporter.error("TRAIT003",
                    "Duplicate member '" + memberName + "' in trait '" + trait.getName() + "'",
                    trait.getLocation());
            } else {
                memberNames.add(memberName);
            }
            
            // Validate return type is specified
            if (member.getReturnType() == null) {
                reporter.error("TRAIT004",
                    "Trait method '" + memberName + "' must have a return type",
                    trait.getLocation());
            }
        }
    }
    
    /**
     * Validate an implementation against its trait.
     */
    private void validateImpl(ImplDecl impl) {
        String traitName = impl.getName();
        TraitDecl trait = traits.get(traitName);
        
        if (trait == null) {
            return; // Error already reported
        }
        
        // Check that all trait methods are implemented
        Set<String> implementedMethods = new HashSet<>();
        for (FunctionDecl method : impl.getMethods()) {
            implementedMethods.add(method.getName());
        }
        
        for (TraitDecl.FunctionSignature signature : trait.getMembers()) {
            if (!implementedMethods.contains(signature.getName())) {
                reporter.error("TRAIT005",
                    "Missing implementation for trait method '" + signature.getName() + "'",
                    impl.getLocation());
            }
        }
        
        // Check that implemented methods match trait signatures
        for (FunctionDecl method : impl.getMethods()) {
            validateMethodSignature(trait, method, impl);
        }
    }
    
    /**
     * Validate that a method matches its trait signature.
     */
    private void validateMethodSignature(TraitDecl trait, FunctionDecl method, ImplDecl impl) {
        String methodName = method.getName();
        
        // Find corresponding trait signature
        TraitDecl.FunctionSignature signature = null;
        for (TraitDecl.FunctionSignature sig : trait.getMembers()) {
            if (sig.getName().equals(methodName)) {
                signature = sig;
                break;
            }
        }
        
        if (signature == null) {
            reporter.error("TRAIT006",
                "Method '" + methodName + "' is not part of trait '" + trait.getName() + "'",
                method.getLocation());
            return;
        }
        
        // Check parameter count
        if (method.getParameters().size() != signature.getParameters().size()) {
            reporter.error("TRAIT007",
                "Method '" + methodName + "' has " + method.getParameters().size() + 
                " parameters, but trait signature requires " + signature.getParameters().size(),
                method.getLocation());
        }
        
        // Check parameter types (simplified - full implementation would need type unification)
        for (int i = 0; i < Math.min(method.getParameters().size(), signature.getParameters().size()); i++) {
            Type methodParamType = method.getParameters().get(i).getType();
            Type signatureParamType = signature.getParameters().get(i).getType();
            
            if (!typesMatch(methodParamType, signatureParamType)) {
                reporter.error("TRAIT008",
                    "Parameter " + (i + 1) + " of method '" + methodName + 
                    "' has type " + methodParamType.getName() + 
                    ", but trait signature requires " + signatureParamType.getName(),
                    method.getLocation());
            }
        }
        
        // Check return type
        if (method.getReturnType().isPresent() && signature.getReturnType() != null) {
            Type methodReturnType = method.getReturnType().get();
            Type signatureReturnType = signature.getReturnType();
            
            if (!typesMatch(methodReturnType, signatureReturnType)) {
                reporter.error("TRAIT009",
                    "Method '" + methodName + "' returns " + methodReturnType.getName() + 
                    ", but trait signature requires " + signatureReturnType.getName(),
                    method.getLocation());
            }
        }
    }
    
    /**
     * Check if two types match (simplified version).
     */
    private boolean typesMatch(Type t1, Type t2) {
        // Simplified type matching - full implementation would handle generics, etc.
        return t1.getName().equals(t2.getName());
    }
    
    /**
     * Get all implementations of a trait.
     */
    public List<ImplDecl> getImplementations(String traitName) {
        return implementations.getOrDefault(traitName, Collections.emptyList());
    }
    
    /**
     * Check if a type implements a trait.
     */
    public boolean typeImplementsTrait(Type type, String traitName) {
        List<ImplDecl> impls = getImplementations(traitName);
        
        for (ImplDecl impl : impls) {
            if (impl.getForType().isPresent()) {
                Type implType = impl.getForType().get();
                if (typesMatch(type, implType)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get a trait by name.
     */
    public TraitDecl getTrait(String name) {
        return traits.get(name);
    }
}

