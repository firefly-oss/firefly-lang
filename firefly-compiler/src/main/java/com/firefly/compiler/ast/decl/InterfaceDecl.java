package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Annotation;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

import java.util.List;

/**
 * Interface declaration for Java interop.
 */
public class InterfaceDecl extends Declaration {
    private final String name;
    private final List<String> typeParameters;
    private final List<Type> superInterfaces;
    private final List<TraitDecl.FunctionSignature> methods;
    private final List<Annotation> annotations;
    
    public InterfaceDecl(
        String name,
        List<String> typeParameters,
        List<Type> superInterfaces,
        List<TraitDecl.FunctionSignature> methods,
        List<Annotation> annotations,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.superInterfaces = superInterfaces;
        this.methods = methods;
        this.annotations = annotations;
    }
    
    public String getName() {
        return name;
    }
    
    public List<String> getTypeParameters() {
        return typeParameters;
    }
    
    public List<Type> getSuperInterfaces() {
        return superInterfaces;
    }
    
    public List<TraitDecl.FunctionSignature> getMethods() {
        return methods;
    }
    
    public List<Annotation> getAnnotations() {
        return annotations;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitInterfaceDecl(this);
    }
}
