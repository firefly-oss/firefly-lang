package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.ast.type.TypeParameter;
import java.util.List;
import java.util.Optional;

/**
 * Represents an implementation block in the AST.
 * Grammar: 'impl' typeParameters? TYPE_IDENTIFIER ('for' type)? '{' implMember* '}'
 *
 * Supports both trait implementations and inherent implementations.
 * Example: impl<T: Clone> Display for List<T> { ... }
 */
public class ImplDecl extends Declaration {
    private final String name;
    private final List<TypeParameter> typeParameters;
    private final Optional<Type> forType;
    private final List<FunctionDecl> methods;

    public ImplDecl(
        String name,
        List<TypeParameter> typeParameters,
        Optional<Type> forType,
        List<FunctionDecl> methods,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.forType = forType;
        this.methods = methods;
    }

    public String getName() { return name; }
    public List<TypeParameter> getTypeParameters() { return typeParameters; }
    public Optional<Type> getForType() { return forType; }
    public List<FunctionDecl> getMethods() { return methods; }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) { 
        return visitor.visitImplDecl(this); 
    }
}
