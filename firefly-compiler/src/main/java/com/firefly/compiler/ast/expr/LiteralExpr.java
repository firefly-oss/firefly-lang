package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Literal expression: integers, floats, strings, booleans, etc.
 */
public class LiteralExpr extends Expression {
    
    public enum LiteralKind {
        INTEGER, FLOAT, STRING, CHAR, BOOLEAN, NONE
    }
    
    private final LiteralKind kind;
    private final Object value;
    private final SourceLocation location;
    
    public LiteralExpr(LiteralKind kind, Object value, SourceLocation location) {
        super(location);
        this.kind = kind;
        this.value = value;
        this.location = location;
    }
    
    public LiteralKind getKind() {
        return kind;
    }
    
    public Object getValue() {
        return value;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitLiteralExpr(this);
    }
    
    @Override
    public String toString() {
        return String.format("Literal(%s, %s)", kind, value);
    }
}
