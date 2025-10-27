package com.firefly.compiler.semantic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a lexical scope in the program.
 * Scopes can be nested (e.g., function scope -> block scope).
 */
public class Scope {
    
    private final Scope parent;
    private final Map<String, Symbol> symbols;
    private final ScopeKind kind;
    
    public enum ScopeKind {
        GLOBAL,
        FUNCTION,
        BLOCK,
        LOOP
    }
    
    public Scope(ScopeKind kind, Scope parent) {
        this.kind = kind;
        this.parent = parent;
        this.symbols = new HashMap<>();
    }
    
    /**
     * Define a new symbol in this scope.
     * @throws SemanticException if symbol already exists
     */
    public void define(Symbol symbol) {
        if (symbols.containsKey(symbol.getName())) {
            throw new SemanticException(
                String.format("Symbol '%s' is already defined in this scope at %s", 
                    symbol.getName(), symbol.getLocation())
            );
        }
        symbols.put(symbol.getName(), symbol);
    }
    
    /**
     * Look up a symbol in this scope or any parent scope.
     */
    public Optional<Symbol> lookup(String name) {
        if (symbols.containsKey(name)) {
            return Optional.of(symbols.get(name));
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return Optional.empty();
    }
    
    /**
     * Look up a symbol only in this scope (not parent scopes).
     */
    public Optional<Symbol> lookupLocal(String name) {
        return Optional.ofNullable(symbols.get(name));
    }
    
    public Scope getParent() {
        return parent;
    }
    
    public ScopeKind getKind() {
        return kind;
    }
    
    public Map<String, Symbol> getSymbols() {
        return new HashMap<>(symbols);
    }
    
    @Override
    public String toString() {
        return String.format("Scope{kind=%s, symbols=%d}", kind, symbols.size());
    }
}
