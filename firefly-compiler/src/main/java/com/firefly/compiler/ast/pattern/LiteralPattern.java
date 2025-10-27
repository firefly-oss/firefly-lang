package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.expr.LiteralExpr;

/**
 * Literal pattern: matches a specific literal value
 */
public class LiteralPattern extends Pattern {
    
    private final LiteralExpr literal;
    
    public LiteralPattern(LiteralExpr literal, SourceLocation location) {
        super(location);
        this.literal = literal;
    }
    
    public LiteralExpr getLiteral() {
        return literal;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}
