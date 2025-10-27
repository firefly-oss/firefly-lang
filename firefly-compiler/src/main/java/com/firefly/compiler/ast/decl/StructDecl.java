package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.type.Type;
import java.util.List;
import java.util.Optional;

/**
 * Represents a struct declaration (product type) in the AST.
 * Grammar: 'struct' TYPE_IDENTIFIER typeParameters? '{' structField* '}'
 */
public class StructDecl extends Declaration {
    private final String name;
    private final List<String> typeParameters;
    private final List<Field> fields;
    
    public StructDecl(
        String name,
        List<String> typeParameters,
        List<Field> fields,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.fields = fields;
    }
    
    public String getName() { return name; }
    public List<String> getTypeParameters() { return typeParameters; }
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
