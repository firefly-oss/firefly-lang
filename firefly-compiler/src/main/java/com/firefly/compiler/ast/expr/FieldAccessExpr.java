package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Field access expression: object.field
 */
public class FieldAccessExpr extends Expression {
    private final Expression object;
    private final String fieldName;
    private final boolean isSafe; // true for ?.
    
    public FieldAccessExpr(Expression object, String fieldName, boolean isSafe, SourceLocation location) {
        super(location);
        this.object = object;
        this.fieldName = fieldName;
        this.isSafe = isSafe;
    }
    
    public Expression getObject() {
        return object;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public boolean isSafe() {
        return isSafe;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitFieldAccessExpr(this);
    }
}
