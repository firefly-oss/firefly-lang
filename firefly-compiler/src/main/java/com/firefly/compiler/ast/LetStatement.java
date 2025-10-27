package com.firefly.compiler.ast;

import com.firefly.compiler.ast.expr.Expression;

import java.util.Optional;

/**
 * Let binding statement: let pattern = expression;
 */
public class LetStatement extends Statement {
    
    private final Pattern pattern;
    private final Expression initializer; // May be null
    private final boolean isMutable;
    
    public LetStatement(
            Pattern pattern,
            Expression initializer,
            boolean isMutable,
            SourceLocation location) {
        super(location);
        this.pattern = pattern;
        this.initializer = initializer;
        this.isMutable = isMutable;
    }
    
    public Pattern getPattern() {
        return pattern;
    }
    
    public Optional<Expression> getInitializer() {
        return Optional.ofNullable(initializer);
    }
    
    public boolean isMutable() {
        return isMutable;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitLetStatement(this);
    }
}
