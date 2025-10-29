package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.ast.type.TypeParameter;
import java.util.List;

/**
 * Represents a trait declaration in the AST (like Rust traits).
 * Grammar: 'trait' TYPE_IDENTIFIER typeParameters? '{' traitMember* '}'
 *
 * Supports generic traits with type parameters and bounds.
 * Example: trait Container<T: Clone> { fn get() -> T; }
 */
public class TraitDecl extends Declaration {
    private final String name;
    private final List<TypeParameter> typeParameters;
    private final List<FunctionSignature> members;

    public TraitDecl(
        String name,
        List<TypeParameter> typeParameters,
        List<FunctionSignature> members,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.members = members;
    }

    public String getName() { return name; }
    public List<TypeParameter> getTypeParameters() { return typeParameters; }
    public List<FunctionSignature> getMembers() { return members; }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) { 
        return visitor.visitTraitDecl(this); 
    }
    
    /**
     * Represents a function signature in a trait.
     * Grammar: 'fn' IDENTIFIER typeParameters? '(' parameterList? ')' '->' type
     *
     * Supports generic methods with type parameters and bounds.
     * Example: fn map<U: Display>(f: T -> U) -> U
     */
    public static class FunctionSignature {
        private final String name;
        private final List<TypeParameter> typeParameters;
        private final List<FunctionDecl.Parameter> parameters;
        private final Type returnType;

        public FunctionSignature(
            String name,
            List<TypeParameter> typeParameters,
            List<FunctionDecl.Parameter> parameters,
            Type returnType
        ) {
            this.name = name;
            this.typeParameters = typeParameters;
            this.parameters = parameters;
            this.returnType = returnType;
        }

        public String getName() { return name; }
        public List<TypeParameter> getTypeParameters() { return typeParameters; }
        public List<FunctionDecl.Parameter> getParameters() { return parameters; }
        public Type getReturnType() { return returnType; }
    }
}
