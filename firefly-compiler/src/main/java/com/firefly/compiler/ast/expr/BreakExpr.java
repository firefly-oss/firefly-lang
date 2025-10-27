package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Represents a break expression that exits a loop.
 */
public class BreakExpr extends Expression {
    
    public BreakExpr(SourceLocation location) {
        super(location);
    }
    
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitBreakExpr(this);
    }
    
    @Override
    public String toString() {
        return "BreakExpr()";
    }
}
