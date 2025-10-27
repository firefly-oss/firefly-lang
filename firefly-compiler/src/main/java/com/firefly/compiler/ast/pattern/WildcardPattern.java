package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Wildcard pattern: _ (matches anything, discards value)
 */
public class WildcardPattern extends Pattern {
    
    public WildcardPattern(SourceLocation location) {
        super(location);
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}
