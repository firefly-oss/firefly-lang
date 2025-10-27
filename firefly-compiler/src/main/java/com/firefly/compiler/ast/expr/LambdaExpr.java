package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Lambda expression: |param1, param2| expression or |param| { body }
 */
public class LambdaExpr extends Expression {
    
    private final List<String> parameters;
    private final Expression body;
    
    public LambdaExpr(List<String> parameters, Expression body, SourceLocation location) {
        super(location);
        this.parameters = parameters;
        this.body = body;
    }
    
    public List<String> getParameters() {
        return parameters;
    }
    
    public Expression getBody() {
        return body;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitLambdaExpr(this);
    }
}
