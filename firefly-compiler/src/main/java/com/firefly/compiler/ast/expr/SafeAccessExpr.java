package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Safe navigation expression: object?.field
 * Returns null if object is null, otherwise accesses the field.
 */
public class SafeAccessExpr extends Expression {
    private final Expression object;
    private final String fieldName;
    
    public SafeAccessExpr(Expression object, String fieldName, SourceLocation location) {
        super(location);
        this.object = object;
        this.fieldName = fieldName;
    }
    
    public Expression getObject() {
        return object;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitSafeAccessExpr(this);
    }
    
    @Override
    public String toString() {
        return object + "?." + fieldName;
    }
}
