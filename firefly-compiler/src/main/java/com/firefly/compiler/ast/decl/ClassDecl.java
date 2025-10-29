package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Annotation;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

import java.util.ArrayList;
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
    private final Optional<FlyDecl> flyDeclaration;
    private final List<Annotation> annotations;
    
    // Nested type support
    private final List<ClassDecl> nestedClasses;
    private final List<InterfaceDecl> nestedInterfaces;
    // private final List<EnumDecl> nestedEnums;  // TODO: Create EnumDecl class
    private final List<SparkDecl> nestedSparks;
    private final List<StructDecl> nestedStructs;
    private final List<DataDecl> nestedData;
    
    // Nesting metadata
    private final boolean isStatic;
    private final boolean isNested;
    private final String enclosingClassName;  // null for top-level classes
    
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
        this(name, typeParameters, superClass, interfaces, fields, methods, constructor, Optional.empty(), 
             annotations, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 
             new ArrayList<>(), new ArrayList<>(), false, false, null, location);
    }
    
    // Constructor with fly declaration (backward compatible)
    public ClassDecl(
        String name,
        List<String> typeParameters,
        Optional<Type> superClass,
        List<Type> interfaces,
        List<FieldDecl> fields,
        List<MethodDecl> methods,
        Optional<ConstructorDecl> constructor,
        Optional<FlyDecl> flyDeclaration,
        List<Annotation> annotations,
        SourceLocation location
    ) {
        this(name, typeParameters, superClass, interfaces, fields, methods, constructor, flyDeclaration, 
             annotations, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 
             new ArrayList<>(), new ArrayList<>(), false, false, null, location);
    }
    
    public ClassDecl(
        String name,
        List<String> typeParameters,
        Optional<Type> superClass,
        List<Type> interfaces,
        List<FieldDecl> fields,
        List<MethodDecl> methods,
        Optional<ConstructorDecl> constructor,
        Optional<FlyDecl> flyDeclaration,
        List<Annotation> annotations,
        List<ClassDecl> nestedClasses,
        List<InterfaceDecl> nestedInterfaces,
        // List<EnumDecl> nestedEnums,  // TODO
        List<SparkDecl> nestedSparks,
        List<StructDecl> nestedStructs,
        List<DataDecl> nestedData,
        boolean isStatic,
        boolean isNested,
        String enclosingClassName,
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
        this.flyDeclaration = flyDeclaration;
        this.annotations = annotations;
        this.nestedClasses = nestedClasses != null ? nestedClasses : new ArrayList<>();
        this.nestedInterfaces = nestedInterfaces != null ? nestedInterfaces : new ArrayList<>();
        // this.nestedEnums = nestedEnums != null ? nestedEnums : new ArrayList<>();  // TODO
        this.nestedSparks = nestedSparks != null ? nestedSparks : new ArrayList<>();
        this.nestedStructs = nestedStructs != null ? nestedStructs : new ArrayList<>();
        this.nestedData = nestedData != null ? nestedData : new ArrayList<>();
        this.isStatic = isStatic;
        this.isNested = isNested;
        this.enclosingClassName = enclosingClassName;
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
    
    public Optional<FlyDecl> getFlyDeclaration() {
        return flyDeclaration;
    }
    
    public List<Annotation> getAnnotations() {
        return annotations;
    }
    
    public List<ClassDecl> getNestedClasses() {
        return nestedClasses;
    }
    
    public List<InterfaceDecl> getNestedInterfaces() {
        return nestedInterfaces;
    }
    
    // public List<EnumDecl> getNestedEnums() {  // TODO
    //     return nestedEnums;
    // }
    
    public List<SparkDecl> getNestedSparks() {
        return nestedSparks;
    }
    
    public List<StructDecl> getNestedStructs() {
        return nestedStructs;
    }
    
    public List<DataDecl> getNestedData() {
        return nestedData;
    }
    
    public boolean isStatic() {
        return isStatic;
    }
    
    public boolean isNested() {
        return isNested;
    }
    
    public String getEnclosingClassName() {
        return enclosingClassName;
    }
    
    public String getFullyQualifiedName(String modulePath) {
        if (isNested && enclosingClassName != null) {
            return modulePath + "." + enclosingClassName + "$" + name;
        }
        return modulePath + "." + name;
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
        private final Visibility visibility;
        
        public FieldDecl(String name, Type type, boolean isMutable, Optional<Expression> initializer, List<Annotation> annotations) {
            this(name, type, isMutable, initializer, annotations, Visibility.PRIVATE);
        }
        
        public FieldDecl(String name, Type type, boolean isMutable, Optional<Expression> initializer, List<Annotation> annotations, Visibility visibility) {
            this.name = name;
            this.type = type;
            this.isMutable = isMutable;
            this.initializer = initializer;
            this.annotations = annotations;
            this.visibility = visibility;
        }
        
        public String getName() { return name; }
        public Type getType() { return type; }
        public boolean isMutable() { return isMutable; }
        public Optional<Expression> getInitializer() { return initializer; }
        public List<Annotation> getAnnotations() { return annotations; }
        public Visibility getVisibility() { return visibility; }
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
        private final Visibility visibility;
        
        public MethodDecl(
            String name,
            List<String> typeParameters,
            List<FunctionDecl.Parameter> parameters,
            Optional<Type> returnType,
            Expression body,
            boolean isAsync,
            List<Annotation> annotations
        ) {
            this(name, typeParameters, parameters, returnType, body, isAsync, annotations, Visibility.PRIVATE);
        }
        
        public MethodDecl(
            String name,
            List<String> typeParameters,
            List<FunctionDecl.Parameter> parameters,
            Optional<Type> returnType,
            Expression body,
            boolean isAsync,
            List<Annotation> annotations,
            Visibility visibility
        ) {
            this.name = name;
            this.typeParameters = typeParameters;
            this.parameters = parameters;
            this.returnType = returnType;
            this.body = body;
            this.isAsync = isAsync;
            this.annotations = annotations;
            this.visibility = visibility;
        }
        
        public String getName() { return name; }
        public List<String> getTypeParameters() { return typeParameters; }
        public List<FunctionDecl.Parameter> getParameters() { return parameters; }
        public Optional<Type> getReturnType() { return returnType; }
        public Expression getBody() { return body; }
        public boolean isAsync() { return isAsync; }
        public List<Annotation> getAnnotations() { return annotations; }
        public Visibility getVisibility() { return visibility; }
    }
    
    /**
     * Constructor declaration within a class
     */
    public static class ConstructorDecl {
        private final List<FunctionDecl.Parameter> parameters;
        private final Expression body;
        private final List<Annotation> annotations;
        private final Visibility visibility;
        
        public ConstructorDecl(List<FunctionDecl.Parameter> parameters, Expression body, List<Annotation> annotations) {
            this(parameters, body, annotations, Visibility.PUBLIC);
        }
        
        public ConstructorDecl(List<FunctionDecl.Parameter> parameters, Expression body, List<Annotation> annotations, Visibility visibility) {
            this.parameters = parameters;
            this.body = body;
            this.annotations = annotations;
            this.visibility = visibility;
        }
        
        public List<FunctionDecl.Parameter> getParameters() { return parameters; }
        public Expression getBody() { return body; }
        public List<Annotation> getAnnotations() { return annotations; }
        public Visibility getVisibility() { return visibility; }
    }
    
    /**
     * Fly declaration (main entry point) within a class
     * Must be public static and take String[] args
     */
    public static class FlyDecl {
        private final List<FunctionDecl.Parameter> parameters;
        private final Optional<Type> returnType;
        private final Expression body;
        private final List<Annotation> annotations;
        
        public FlyDecl(List<FunctionDecl.Parameter> parameters, Optional<Type> returnType, Expression body, List<Annotation> annotations) {
            this.parameters = parameters;
            this.returnType = returnType;
            this.body = body;
            this.annotations = annotations;
        }
        
        public List<FunctionDecl.Parameter> getParameters() { return parameters; }
        public Optional<Type> getReturnType() { return returnType; }
        public Expression getBody() { return body; }
        public List<Annotation> getAnnotations() { return annotations; }
    }
    
    /**
     * Visibility modifier enum
     */
    public enum Visibility {
        PUBLIC,
        PRIVATE
    }
}
