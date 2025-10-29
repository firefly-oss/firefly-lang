package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.Annotation;
import com.firefly.compiler.ast.AstNode;
import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AST node for Flylang exception declarations.
 */
public class ExceptionDecl extends Declaration {
    private final String name;
    private final Optional<String> superException;
    private final List<Annotation> annotations;
    private final List<FieldDecl> fields;
    private final List<ClassDecl.MethodDecl> methods;
    private final Optional<ClassDecl.ConstructorDecl> constructor;
    
    public ExceptionDecl(
            String name,
            Optional<String> superException,
            List<Annotation> annotations,
            List<FieldDecl> fields,
            List<ClassDecl.MethodDecl> methods,
            Optional<ClassDecl.ConstructorDecl> constructor,
            SourceLocation location) {
        super(location);
        this.name = name;
        this.superException = superException;
        this.annotations = new ArrayList<>(annotations);
        this.fields = new ArrayList<>(fields);
        this.methods = new ArrayList<>(methods);
        this.constructor = constructor;
    }
    
    public String getName() {
        return name;
    }
    
    public Optional<String> getSuperException() {
        return superException;
    }
    
    public List<Annotation> getAnnotations() {
        return annotations;
    }
    
    public List<FieldDecl> getFields() {
        return fields;
    }
    
    public List<ClassDecl.MethodDecl> getMethods() {
        return methods;
    }
    
    public Optional<ClassDecl.ConstructorDecl> getConstructor() {
        return constructor;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitExceptionDecl(this);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder("exception " + name);
        if (superException.isPresent()) {
            sb.append(" extends ").append(superException.get());
        }
        sb.append(" { ... }");
        return sb.toString();
    }
}
