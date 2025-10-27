package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.Pattern;

/**
 * For loop expression: for pattern in iterable { body }
 */
public class ForExpr extends Expression {
    private final Pattern pattern;
    private final Expression iterable;
    private final BlockExpr body;
    
    public ForExpr(Pattern pattern, Expression iterable, BlockExpr body, SourceLocation location) {
        super(location);
        this.pattern = pattern;
        this.iterable = iterable;
        this.body = body;
    }
    
    public Pattern getPattern() {
        return pattern;
    }
    
    public Expression getIterable() {
        return iterable;
    }
    
    public BlockExpr getBody() {
        return body;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitForExpr(this);
    }
}
