package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Struct pattern: TypeName { field1: pattern1, field2: pattern2, ... }
 * Matches struct instances and destructures them.
 */
public class StructPattern extends Pattern {
    
    private final String typeName;
    private final List<FieldPattern> fields;
    
    public StructPattern(String typeName, List<FieldPattern> fields, SourceLocation location) {
        super(location);
        this.typeName = typeName;
        this.fields = fields;
    }
    
    public String getTypeName() {
        return typeName;
    }
    
    public List<FieldPattern> getFields() {
        return fields;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
    
    /**
     * Represents a field pattern in a struct pattern.
     * Can be either:
     * - field (shorthand for field: field)
     * - field: pattern
     */
    public static class FieldPattern {
        private final String fieldName;
        private final Pattern pattern; // null for shorthand
        
        public FieldPattern(String fieldName, Pattern pattern) {
            this.fieldName = fieldName;
            this.pattern = pattern;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public Pattern getPattern() {
            return pattern;
        }
        
        public boolean isShorthand() {
            return pattern == null;
        }
    }
}

