package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Or pattern: pattern1 | pattern2
 * Matches if either pattern matches.
 */
public class OrPattern extends Pattern {
    
    private final Pattern left;
    private final Pattern right;
    
    public OrPattern(Pattern left, Pattern right, SourceLocation location) {
        super(location);
        this.left = left;
        this.right = right;
    }
    
    public Pattern getLeft() {
        return left;
    }
    
    public Pattern getRight() {
        return right;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}

