package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.ast.type.TypeParameter;
import java.util.List;
import java.util.Optional;

/**
 * Represents a struct declaration (product type) in the AST.
 * Grammar: 'struct' TYPE_IDENTIFIER typeParameters? '{' structField* '}'
 *
 * Supports generic structs with type parameters and bounds.
 * Example: struct Point<T: Numeric> { x: T, y: T }
 */
public class StructDecl extends Declaration {
    private final String name;
    private final List<TypeParameter> typeParameters;
    private final List<Field> fields;

    public StructDecl(
        String name,
        List<TypeParameter> typeParameters,
        List<Field> fields,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.fields = fields;
    }

    public String getName() { return name; }
    public List<TypeParameter> getTypeParameters() { return typeParameters; }
    public List<Field> getFields() { return fields; }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) { 
        return visitor.visitStructDecl(this); 
    }
    
    /**
     * Represents a struct field.
     * Grammar: IDENTIFIER ':' type ('=' expression)?
     */
    public static class Field {
        private final String name;
        private final Type type;
        private final Optional<Expression> defaultValue;
        
        public Field(String name, Type type, Optional<Expression> defaultValue) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }
        
        public String getName() { return name; }
        public Type getType() { return type; }
        public Optional<Expression> getDefaultValue() { return defaultValue; }
    }
}
