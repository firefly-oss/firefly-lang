package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.*;

/**
 * Resolves method calls to their implementations.
 * Handles trait method dispatch and regular method calls.
 */
public class MethodDispatcher {
    
    private final DiagnosticReporter reporter;
    private final TraitChecker traitChecker;
    
    public MethodDispatcher(DiagnosticReporter reporter, TraitChecker traitChecker) {
        this.reporter = reporter;
        this.traitChecker = traitChecker;
    }
    
    /**
     * Resolve a method call on a type.
     * Returns the function declaration for the method, or null if not found.
     */
    public FunctionDecl resolveMethod(Type receiverType, String methodName) {
        // First, try to find a direct implementation
        FunctionDecl directMethod = findDirectMethod(receiverType, methodName);
        if (directMethod != null) {
            return directMethod;
        }
        
        // Then, try to find a trait method
        FunctionDecl traitMethod = findTraitMethod(receiverType, methodName);
        if (traitMethod != null) {
            return traitMethod;
        }
        
        return null;
    }
    
    /**
     * Find a method directly implemented on a type.
     */
    private FunctionDecl findDirectMethod(Type type, String methodName) {
        // Look for impl blocks without trait (direct implementations)
        String typeName = type.getName();
        
        // This is simplified - in a full implementation, we'd have a registry
        // of all impl blocks indexed by type
        return null;
    }
    
    /**
     * Find a method from a trait implementation.
     */
    private FunctionDecl findTraitMethod(Type receiverType, String methodName) {
        // Search all traits to find one that:
        // 1. Has a method with the given name
        // 2. Is implemented for the receiver type
        
        for (String traitName : getAllTraitNames()) {
            TraitDecl trait = traitChecker.getTrait(traitName);
            if (trait == null) continue;
            
            // Check if trait has the method
            boolean hasMethod = trait.getMembers().stream()
                .anyMatch(sig -> sig.getName().equals(methodName));
            
            if (!hasMethod) continue;
            
            // Check if receiver type implements this trait
            if (!traitChecker.typeImplementsTrait(receiverType, traitName)) {
                continue;
            }
            
            // Find the implementation
            List<ImplDecl> impls = traitChecker.getImplementations(traitName);
            for (ImplDecl impl : impls) {
                if (impl.getForType().isPresent()) {
                    Type implType = impl.getForType().get();
                    if (typesMatch(receiverType, implType)) {
                        // Find the method in this impl
                        for (FunctionDecl method : impl.getMethods()) {
                            if (method.getName().equals(methodName)) {
                                return method;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get all registered trait names.
     */
    private Set<String> getAllTraitNames() {
        // This would be provided by TraitChecker in a full implementation
        return new HashSet<>();
    }
    
    /**
     * Check if two types match.
     */
    private boolean typesMatch(Type t1, Type t2) {
        return t1.getName().equals(t2.getName());
    }
    
    /**
     * Resolve a trait method call with explicit trait qualification.
     * Example: Printable::print(value)
     */
    public FunctionDecl resolveTraitMethod(String traitName, String methodName, Type receiverType) {
        TraitDecl trait = traitChecker.getTrait(traitName);
        if (trait == null) {
            reporter.error("DISPATCH001",
                "Trait '" + traitName + "' is not defined",
                null);
            return null;
        }
        
        // Check if trait has the method
        boolean hasMethod = trait.getMembers().stream()
            .anyMatch(sig -> sig.getName().equals(methodName));
        
        if (!hasMethod) {
            reporter.error("DISPATCH002",
                "Trait '" + traitName + "' does not have method '" + methodName + "'",
                null);
            return null;
        }
        
        // Check if receiver type implements the trait
        if (!traitChecker.typeImplementsTrait(receiverType, traitName)) {
            reporter.error("DISPATCH003",
                "Type '" + receiverType.getName() + "' does not implement trait '" + traitName + "'",
                null);
            return null;
        }
        
        // Find the implementation
        List<ImplDecl> impls = traitChecker.getImplementations(traitName);
        for (ImplDecl impl : impls) {
            if (impl.getForType().isPresent()) {
                Type implType = impl.getForType().get();
                if (typesMatch(receiverType, implType)) {
                    for (FunctionDecl method : impl.getMethods()) {
                        if (method.getName().equals(methodName)) {
                            return method;
                        }
                    }
                }
            }
        }
        
        reporter.error("DISPATCH004",
            "No implementation found for " + traitName + "::" + methodName + 
            " on type " + receiverType.getName(),
            null);
        return null;
    }
    
    /**
     * Check if a method call is ambiguous (multiple trait implementations).
     */
    public boolean isAmbiguous(Type receiverType, String methodName) {
        List<FunctionDecl> candidates = findAllCandidates(receiverType, methodName);
        return candidates.size() > 1;
    }
    
    /**
     * Find all possible method implementations for a call.
     */
    private List<FunctionDecl> findAllCandidates(Type receiverType, String methodName) {
        List<FunctionDecl> candidates = new ArrayList<>();
        
        // Add direct method if exists
        FunctionDecl directMethod = findDirectMethod(receiverType, methodName);
        if (directMethod != null) {
            candidates.add(directMethod);
        }
        
        // Add all trait methods
        for (String traitName : getAllTraitNames()) {
            TraitDecl trait = traitChecker.getTrait(traitName);
            if (trait == null) continue;
            
            boolean hasMethod = trait.getMembers().stream()
                .anyMatch(sig -> sig.getName().equals(methodName));
            
            if (hasMethod && traitChecker.typeImplementsTrait(receiverType, traitName)) {
                List<ImplDecl> impls = traitChecker.getImplementations(traitName);
                for (ImplDecl impl : impls) {
                    if (impl.getForType().isPresent() && 
                        typesMatch(receiverType, impl.getForType().get())) {
                        for (FunctionDecl method : impl.getMethods()) {
                            if (method.getName().equals(methodName)) {
                                candidates.add(method);
                            }
                        }
                    }
                }
            }
        }
        
        return candidates;
    }
}

