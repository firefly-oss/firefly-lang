package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.ast.type.TypeParameter;
import java.util.List;
import java.util.Optional;

/**
 * Represents a data declaration (algebraic data type / sum type) in the AST.
 * Grammar: 'data' TYPE_IDENTIFIER typeParameters? '{' dataVariant (',' dataVariant)* '}'
 *
 * Supports generic algebraic data types with type parameters and bounds.
 * Example: data Option<T> { Some(T), None }
 */
public class DataDecl extends Declaration {
    private final String name;
    private final List<TypeParameter> typeParameters;
    private final List<Variant> variants;

    public DataDecl(
        String name,
        List<TypeParameter> typeParameters,
        List<Variant> variants,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.variants = variants;
    }

    public String getName() { return name; }
    public List<TypeParameter> getTypeParameters() { return typeParameters; }
    public List<Variant> getVariants() { return variants; }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) { 
        return visitor.visitDataDecl(this); 
    }
    
    /**
     * Represents a data variant.
     * Grammar: TYPE_IDENTIFIER ('(' fieldList ')')?
     */
    public static class Variant {
        private final String name;
        private final List<VariantField> fields;
        
        public Variant(String name, List<VariantField> fields) {
            this.name = name;
            this.fields = fields;
        }
        
        public String getName() { return name; }
        public List<VariantField> getFields() { return fields; }
    }
    
    /**
     * Represents a field in a data variant.
     * Can be named (IDENTIFIER ':' type) or unnamed (just type).
     */
    public static class VariantField {
        private final Optional<String> name;
        private final Type type;
        
        public VariantField(Optional<String> name, Type type) {
            this.name = name;
            this.type = type;
        }
        
        public Optional<String> getName() { return name; }
        public Type getType() { return type; }
    }
}
