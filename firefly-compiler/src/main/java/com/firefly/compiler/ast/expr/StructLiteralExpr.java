package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Struct literal expression: Point { x: 10, y: 20 }
 * 
 * Represents the instantiation of a struct with named fields.
 * Grammar: TYPE_IDENTIFIER '{' (structLiteralField (',' structLiteralField)* ','?)? '}'
 * 
 * Examples:
 * - Point { x: 10, y: 20 }
 * - Person { name: "Alice", age: 30 }
 * - Config { enabled: true, timeout: 5000 }
 */
public class StructLiteralExpr extends Expression {
    
    private final String structName;
    private final List<FieldInit> fieldInits;
    
    public StructLiteralExpr(String structName, List<FieldInit> fieldInits, SourceLocation location) {
        super(location);
        this.structName = structName;
        this.fieldInits = fieldInits;
    }
    
    public String getStructName() {
        return structName;
    }
    
    public List<FieldInit> getFieldInits() {
        return fieldInits;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitStructLiteralExpr(this);
    }
    
    /**
     * Represents a field initialization in a struct literal.
     * Example: x: 10
     */
    public static class FieldInit {
        private final String fieldName;
        private final Expression value;
        
        public FieldInit(String fieldName, Expression value) {
            this.fieldName = fieldName;
            this.value = value;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public Expression getValue() {
            return value;
        }
    }
}
