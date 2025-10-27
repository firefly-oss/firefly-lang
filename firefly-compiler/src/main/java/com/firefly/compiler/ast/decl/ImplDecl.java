package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.type.Type;
import java.util.List;
import java.util.Optional;

/**
 * Represents an implementation block in the AST.
 * Grammar: 'impl' typeParameters? TYPE_IDENTIFIER ('for' type)? '{' implMember* '}'
 */
public class ImplDecl extends Declaration {
    private final String name;
    private final List<String> typeParameters;
    private final Optional<Type> forType;
    private final List<FunctionDecl> methods;
    
    public ImplDecl(
        String name,
        List<String> typeParameters,
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
    public List<String> getTypeParameters() { return typeParameters; }
    public Optional<Type> getForType() { return forType; }
    public List<FunctionDecl> getMethods() { return methods; }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) { 
        return visitor.visitImplDecl(this); 
    }
}
