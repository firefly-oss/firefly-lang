package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.type.Type;
import java.util.List;
import java.util.Optional;

/**
 * Represents a function declaration in the AST.
 * Grammar: 'async'? 'fn' IDENTIFIER typeParameters? '(' parameterList? ')' ('->' type)? '=' expression
 */
public class FunctionDecl extends Declaration {
    private final String name;
    private final List<Parameter> parameters;
    private final Optional<Type> returnType;
    private final Expression body;
    private final boolean isAsync;
    private final List<String> typeParameters;
    private final List<Annotation> annotations;
    
    public FunctionDecl(
        String name,
        List<Parameter> parameters,
        Optional<Type> returnType,
        Expression body,
        boolean isAsync,
        List<String> typeParameters,
        SourceLocation location
    ) {
        this(name, parameters, returnType, body, isAsync, typeParameters, List.of(), location);
    }
    
    public FunctionDecl(
        String name,
        List<Parameter> parameters,
        Optional<Type> returnType,
        Expression body,
        boolean isAsync,
        List<String> typeParameters,
        List<Annotation> annotations,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
        this.body = body;
        this.isAsync = isAsync;
        this.typeParameters = typeParameters;
        this.annotations = annotations;
    }
    
    public String getName() { return name; }
    public List<Parameter> getParameters() { return parameters; }
    public Optional<Type> getReturnType() { return returnType; }
    public Expression getBody() { return body; }
    public boolean isAsync() { return isAsync; }
    public List<String> getTypeParameters() { return typeParameters; }
    public List<Annotation> getAnnotations() { return annotations; }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) { 
        return visitor.visitFunctionDecl(this); 
    }
    
    /**
     * Represents a function parameter.
     */
    public static class Parameter {
        private final String name;
        private final Type type;
        private final Optional<Expression> defaultValue;
        private final boolean isMutable;
        private final List<Annotation> annotations;
        
        public Parameter(String name, Type type, Optional<Expression> defaultValue, boolean isMutable) {
            this(name, type, defaultValue, isMutable, List.of());
        }
        
        public Parameter(String name, Type type, Optional<Expression> defaultValue, boolean isMutable, List<Annotation> annotations) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.isMutable = isMutable;
            this.annotations = annotations;
        }
        
        public String getName() { return name; }
        public Type getType() { return type; }
        public Optional<Expression> getDefaultValue() { return defaultValue; }
        public boolean isMutable() { return isMutable; }
        public List<Annotation> getAnnotations() { return annotations; }
    }
}
