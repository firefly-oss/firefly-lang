package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Function call expression: function(arg1, arg2, ...)
 */
public class CallExpr extends Expression {
    
    private final Expression function;
    private final List<Expression> arguments;
    
    public CallExpr(
            Expression function,
            List<Expression> arguments,
            SourceLocation location) {
        super(location);
        this.function = function;
        this.arguments = arguments;
    }
    
    public Expression getFunction() {
        return function;
    }
    
    public List<Expression> getArguments() {
        return arguments;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitCallExpr(this);
    }
}
