package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Array pattern: [pattern1, pattern2, ...]
 * Matches arrays and destructures them.
 */
public class ArrayPattern extends Pattern {
    
    private final List<Pattern> elements;
    private final boolean hasRest; // true if pattern ends with ..
    
    public ArrayPattern(List<Pattern> elements, boolean hasRest, SourceLocation location) {
        super(location);
        this.elements = elements;
        this.hasRest = hasRest;
    }
    
    public List<Pattern> getElements() {
        return elements;
    }
    
    public boolean hasRest() {
        return hasRest;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}

