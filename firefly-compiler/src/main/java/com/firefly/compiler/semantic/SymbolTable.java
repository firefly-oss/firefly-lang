package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.decl.FunctionDecl;
import com.firefly.compiler.ast.type.Type;

import java.util.*;

/**
 * Symbol table for tracking variable and function declarations during semantic analysis.
 */
public class SymbolTable {
    
    /**
     * A symbol entry in the table.
     */
    public static class Symbol {
        private final String name;
        private final Type type;
        private final SymbolKind kind;
        private final boolean isMutable;
        private final boolean isAsync;
        private final FunctionDecl functionDecl; // For generic function type inference

        public Symbol(String name, Type type, SymbolKind kind, boolean isMutable) {
            this(name, type, kind, isMutable, false, null);
        }

        public Symbol(String name, Type type, SymbolKind kind, boolean isMutable, boolean isAsync) {
            this(name, type, kind, isMutable, isAsync, null);
        }

        public Symbol(String name, Type type, SymbolKind kind, boolean isMutable, boolean isAsync, FunctionDecl functionDecl) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.isMutable = isMutable;
            this.isAsync = isAsync;
            this.functionDecl = functionDecl;
        }

        public String getName() { return name; }
        public Type getType() { return type; }
        public SymbolKind getKind() { return kind; }
        public boolean isMutable() { return isMutable; }
        public boolean isAsync() { return isAsync; }
        public FunctionDecl getFunctionDecl() { return functionDecl; }
    }
    
    public enum SymbolKind {
        VARIABLE, FUNCTION, PARAMETER, STRUCT, DATA, TRAIT, FIELD, SPARK
    }
    
    private final SymbolTable parent;
    private final Map<String, Symbol> symbols = new HashMap<>();
    
    public SymbolTable() {
        this(null);
        initializeBuiltins();
    }
    
    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }
    
    /**
     * Enter a new scope.
     */
    public SymbolTable enterScope() {
        return new SymbolTable(this);
    }
    
    /**
     * Exit current scope and return to parent.
     */
    public SymbolTable exitScope() {
        return parent;
    }
    
    /**
     * Define a new symbol in the current scope.
     */
    public void define(String name, Type type, SymbolKind kind, boolean isMutable) {
        define(name, type, kind, isMutable, false, null);
    }

    /**
     * Define a new symbol in the current scope with async flag.
     */
    public void define(String name, Type type, SymbolKind kind, boolean isMutable, boolean isAsync) {
        define(name, type, kind, isMutable, isAsync, null);
    }

    /**
     * Define a new symbol in the current scope with async flag and function declaration.
     */
    public void define(String name, Type type, SymbolKind kind, boolean isMutable, boolean isAsync, FunctionDecl functionDecl) {
        if (symbols.containsKey(name)) {
            throw new SemanticException("Symbol already defined in current scope: " + name);
        }
        symbols.put(name, new Symbol(name, type, kind, isMutable, isAsync, functionDecl));
    }
    
    /**
     * Look up a symbol in current or parent scopes.
     */
    public Optional<Symbol> lookup(String name) {
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return Optional.of(symbol);
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return Optional.empty();
    }
    
    /**
     * Check if a symbol exists in current scope only (not parent scopes).
     */
    public boolean existsInCurrentScope(String name) {
        return symbols.containsKey(name);
    }
    
    /**
     * Get all symbols in current scope.
     */
    public Collection<Symbol> getSymbols() {
        return symbols.values();
    }
    
    /**
     * Initialize built-in functions and types.
     */
    private void initializeBuiltins() {
        // Only initialize in root scope (no parent)
        if (parent != null) {
            return;
        }

        // Built-in print functions
        symbols.put("println", new Symbol("println", null, SymbolKind.FUNCTION, false));
        symbols.put("print", new Symbol("print", null, SymbolKind.FUNCTION, false));

        // Built-in utility functions
        symbols.put("toString", new Symbol("toString", null, SymbolKind.FUNCTION, false));
        symbols.put("error", new Symbol("error", null, SymbolKind.FUNCTION, false));
        symbols.put("panic", new Symbol("panic", null, SymbolKind.FUNCTION, false));
    }
}
