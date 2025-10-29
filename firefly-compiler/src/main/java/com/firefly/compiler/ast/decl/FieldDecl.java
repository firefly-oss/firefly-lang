package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.Annotation;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.type.Type;

import java.util.List;
import java.util.Optional;

/**
 * Field declaration AST node.
 * 
 * <p>Represents a field in a class or actor with optional initialization.</p>
 * 
 * <h2>Example:</h2>
 * <pre>
 * let name: String;              // Immutable field
 * let mut count: Int;            // Mutable field
 * let value: Int = 42;           // Immutable with initializer
 * </pre>
 */
public class FieldDecl extends Declaration {
    
    private final String name;
    private final Type type;
    private final boolean isMutable;
    private final Optional<Expression> initializer;
    private final List<Annotation> annotations;
    
    public FieldDecl(
            String name,
            Type type,
            boolean isMutable,
            Optional<Expression> initializer,
            List<Annotation> annotations,
            SourceLocation location) {
        super(location);
        this.name = name;
        this.type = type;
        this.isMutable = isMutable;
        this.initializer = initializer;
        this.annotations = annotations;
    }
    
    public String getName() {
        return name;
    }
    
    public Type getType() {
        return type;
    }
    
    public boolean isMutable() {
        return isMutable;
    }
    
    public Optional<Expression> getInitializer() {
        return initializer;
    }
    
    public List<Annotation> getAnnotations() {
        return annotations;
    }
    
    @Override
    public <T> T accept(com.firefly.compiler.ast.AstVisitor<T> visitor) {
        // For now, treat as declaration
        return null;
    }
}
