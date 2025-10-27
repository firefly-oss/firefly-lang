package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Represents a continue expression that skips to the next iteration of a loop.
 */
public class ContinueExpr extends Expression {
    
    public ContinueExpr(SourceLocation location) {
        super(location);
    }
    
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitContinueExpr(this);
    }
    
    @Override
    public String toString() {
        return "ContinueExpr()";
    }
}
