package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Field access expression: object.field
 *
 * Note: Also reused as the callee for instance/static method calls. When constructed
 * from a double-colon method call (receiver::method(...)), the fromDoubleColon flag
 * is set to true so downstream analyzers can enforce style rules.
 */
public class FieldAccessExpr extends Expression {
    private final Expression object;
    private final String fieldName;
    private final boolean isSafe; // true for ?.
    private final boolean fromDoubleColon; // true when created from expression::method(...)
    
    public FieldAccessExpr(Expression object, String fieldName, boolean isSafe, boolean fromDoubleColon, SourceLocation location) {
        super(location);
        this.object = object;
        this.fieldName = fieldName;
        this.isSafe = isSafe;
        this.fromDoubleColon = fromDoubleColon;
    }
    
    // Backward-compatible constructor (defaults fromDoubleColon=false)
    public FieldAccessExpr(Expression object, String fieldName, boolean isSafe, SourceLocation location) {
        this(object, fieldName, isSafe, false, location);
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
    
    public boolean isFromDoubleColon() {
        return fromDoubleColon;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitFieldAccessExpr(this);
    }
}
