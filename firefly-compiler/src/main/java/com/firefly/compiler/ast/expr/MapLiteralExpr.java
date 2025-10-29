package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map literal expression: {key1: value1, key2: value2, ...}
 * Represents a HashMap construction with initial key-value pairs.
 */
public class MapLiteralExpr extends Expression {
    private final Map<Expression, Expression> entries;
    
    public MapLiteralExpr(Map<Expression, Expression> entries, SourceLocation location) {
        super(location);
        this.entries = entries != null ? entries : new LinkedHashMap<>();
    }
    
    public Map<Expression, Expression> getEntries() {
        return entries;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitMapLiteralExpr(this);
    }
}
