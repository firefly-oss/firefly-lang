package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

/**
 * Represents a symbol in the symbol table (variable, function, type, etc.).
 */
public class Symbol {
    
    public enum SymbolKind {
        VARIABLE,
        FUNCTION,
        PARAMETER,
        TYPE,
        STRUCT_FIELD
    }
    
    private final String name;
    private final SymbolKind kind;
    private final Type type;
    private final SourceLocation location;
    private final boolean isMutable;
    
    public Symbol(String name, SymbolKind kind, Type type, SourceLocation location, boolean isMutable) {
        this.name = name;
        this.kind = kind;
        this.type = type;
        this.location = location;
        this.isMutable = isMutable;
    }
    
    public Symbol(String name, SymbolKind kind, Type type, SourceLocation location) {
        this(name, kind, type, location, false);
    }
    
    public String getName() { return name; }
    public SymbolKind getKind() { return kind; }
    public Type getType() { return type; }
    public SourceLocation getLocation() { return location; }
    public boolean isMutable() { return isMutable; }
    
    @Override
    public String toString() {
        return String.format("Symbol{name='%s', kind=%s, type=%s, mutable=%s}", 
            name, kind, type.getName(), isMutable);
    }
}
