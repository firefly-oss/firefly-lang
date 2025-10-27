package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Annotation;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

import java.util.List;
import java.util.Optional;

/**
 * Class declaration for Java interop and Spring Boot support.
 * Classes can have fields, methods, constructors, and annotations.
 */
public class ClassDecl extends Declaration {
    private final String name;
    private final List<String> typeParameters;
    private final Optional<Type> superClass;
    private final List<Type> interfaces;
    private final List<FieldDecl> fields;
    private final List<MethodDecl> methods;
    private final Optional<ConstructorDecl> constructor;
    private final List<Annotation> annotations;
    
    public ClassDecl(
        String name,
        List<String> typeParameters,
        Optional<Type> superClass,
        List<Type> interfaces,
        List<FieldDecl> fields,
        List<MethodDecl> methods,
        Optional<ConstructorDecl> constructor,
        List<Annotation> annotations,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.superClass = superClass;
        this.interfaces = interfaces;
        this.fields = fields;
        this.methods = methods;
        this.constructor = constructor;
        this.annotations = annotations;
    }
    
    public String getName() {
        return name;
    }
    
    public List<String> getTypeParameters() {
        return typeParameters;
    }
    
    public Optional<Type> getSuperClass() {
        return superClass;
    }
    
    public List<Type> getInterfaces() {
        return interfaces;
    }
    
    public List<FieldDecl> getFields() {
        return fields;
    }
    
    public List<MethodDecl> getMethods() {
        return methods;
    }
    
    public Optional<ConstructorDecl> getConstructor() {
        return constructor;
    }
    
    public List<Annotation> getAnnotations() {
        return annotations;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitClassDecl(this);
    }
    
    /**
     * Field declaration within a class
     */
    public static class FieldDecl {
        private final String name;
        private final Type type;
        private final boolean isMutable;
        private final Optional<Expression> initializer;
        private final List<Annotation> annotations;
        
        public FieldDecl(String name, Type type, boolean isMutable, Optional<Expression> initializer, List<Annotation> annotations) {
            this.name = name;
            this.type = type;
            this.isMutable = isMutable;
            this.initializer = initializer;
            this.annotations = annotations;
        }
        
        public String getName() { return name; }
        public Type getType() { return type; }
        public boolean isMutable() { return isMutable; }
        public Optional<Expression> getInitializer() { return initializer; }
        public List<Annotation> getAnnotations() { return annotations; }
    }
    
    /**
     * Method declaration within a class
     */
    public static class MethodDecl {
        private final String name;
        private final List<String> typeParameters;
        private final List<FunctionDecl.Parameter> parameters;
        private final Optional<Type> returnType;
        private final Expression body;
        private final boolean isAsync;
        private final List<Annotation> annotations;
        
        public MethodDecl(
            String name,
            List<String> typeParameters,
            List<FunctionDecl.Parameter> parameters,
            Optional<Type> returnType,
            Expression body,
            boolean isAsync,
            List<Annotation> annotations
        ) {
            this.name = name;
            this.typeParameters = typeParameters;
            this.parameters = parameters;
            this.returnType = returnType;
            this.body = body;
            this.isAsync = isAsync;
            this.annotations = annotations;
        }
        
        public String getName() { return name; }
        public List<String> getTypeParameters() { return typeParameters; }
        public List<FunctionDecl.Parameter> getParameters() { return parameters; }
        public Optional<Type> getReturnType() { return returnType; }
        public Expression getBody() { return body; }
        public boolean isAsync() { return isAsync; }
        public List<Annotation> getAnnotations() { return annotations; }
    }
    
    /**
     * Constructor declaration within a class
     */
    public static class ConstructorDecl {
        private final List<FunctionDecl.Parameter> parameters;
        private final Expression body;
        private final List<Annotation> annotations;
        
        public ConstructorDecl(List<FunctionDecl.Parameter> parameters, Expression body, List<Annotation> annotations) {
            this.parameters = parameters;
            this.body = body;
            this.annotations = annotations;
        }
        
        public List<FunctionDecl.Parameter> getParameters() { return parameters; }
        public Expression getBody() { return body; }
        public List<Annotation> getAnnotations() { return annotations; }
    }
}
