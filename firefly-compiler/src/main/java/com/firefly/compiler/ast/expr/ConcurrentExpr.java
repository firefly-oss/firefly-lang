package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Concurrent execution expression - executes multiple async operations concurrently.
 * Grammar: concurrent { let x = expr.await, let y = expr.await, ... }
 * 
 * This is a unique Firefly feature for structured concurrency.
 */
public class ConcurrentExpr extends Expression {
    
    private final List<ConcurrentBinding> bindings;
    
    public ConcurrentExpr(List<ConcurrentBinding> bindings, SourceLocation location) {
        super(location);
        this.bindings = bindings;
    }
    
    public List<ConcurrentBinding> getBindings() {
        return bindings;
    }
    
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitConcurrentExpr(this);
    }
    
    /**
     * Represents a binding in a concurrent block.
     * Grammar: let IDENTIFIER = expression.await
     */
    public static class ConcurrentBinding {
        private final String name;
        private final Expression expression;
        
        public ConcurrentBinding(String name, Expression expression) {
            this.name = name;
            this.expression = expression;
        }
        
        public String getName() {
            return name;
        }
        
        public Expression getExpression() {
            return expression;
        }
    }
}
