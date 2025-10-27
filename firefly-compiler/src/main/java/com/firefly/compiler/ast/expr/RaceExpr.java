package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Race expression - executes a block and returns the result of the first completed operation.
 * Grammar: race blockExpression
 * 
 * This is a unique Firefly feature for competitive concurrency.
 * Similar to Promise.race() in JavaScript or select! in Rust.
 */
public class RaceExpr extends Expression {
    
    private final BlockExpr body;
    
    public RaceExpr(BlockExpr body, SourceLocation location) {
        super(location);
        this.body = body;
    }
    
    public BlockExpr getBody() {
        return body;
    }
    
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitRaceExpr(this);
    }
}
