package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

public class PrimitiveType implements Type {
    public enum Kind {
        INT, LONG, FLOAT, DOUBLE, STRING, BOOL, CHAR, VOID
    }
    
    // Constants for common primitive types
    public static final PrimitiveType INT = new PrimitiveType("Int", Kind.INT);
    public static final PrimitiveType LONG = new PrimitiveType("Long", Kind.LONG);
    public static final PrimitiveType FLOAT = new PrimitiveType("Float", Kind.FLOAT);
    public static final PrimitiveType DOUBLE = new PrimitiveType("Double", Kind.DOUBLE);
    public static final PrimitiveType STRING = new PrimitiveType("String", Kind.STRING);
    public static final PrimitiveType BOOL = new PrimitiveType("Bool", Kind.BOOL);
    public static final PrimitiveType CHAR = new PrimitiveType("Char", Kind.CHAR);
    public static final PrimitiveType VOID = new PrimitiveType("Void", Kind.VOID);
    
    private final String name;
    private final Kind kind;
    
    public PrimitiveType(String name) {
        this.name = name;
        this.kind = inferKind(name);
    }
    
    public PrimitiveType(String name, Kind kind) {
        this.name = name;
        this.kind = kind;
    }
    
    private Kind inferKind(String name) {
        switch (name) {
            case "Int": return Kind.INT;
            case "Long": return Kind.LONG;
            case "Float": return Kind.FLOAT;
            case "Double": return Kind.DOUBLE;
            case "String": return Kind.STRING;
            case "Bool": return Kind.BOOL;
            case "Char": return Kind.CHAR;
            case "Void": return Kind.VOID;
            default: return Kind.STRING; // default fallback
        }
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public Kind getKind() {
        return kind;
    }
    
    @Override
    public SourceLocation getLocation() {
        return SourceLocation.unknown();
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPrimitiveType(this);
    }
}
