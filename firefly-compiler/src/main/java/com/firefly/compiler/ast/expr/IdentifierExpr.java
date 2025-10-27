package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Identifier expression: a variable or function name
 */
public class IdentifierExpr extends Expression {
    
    private final String name;
    
    public IdentifierExpr(String name, SourceLocation location) {
        super(location);
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitIdentifierExpr(this);
    }
}
