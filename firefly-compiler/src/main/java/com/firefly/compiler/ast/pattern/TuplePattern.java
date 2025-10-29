package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Tuple pattern: (pattern1, pattern2, ...)
 * Matches tuples and destructures them.
 */
public class TuplePattern extends Pattern {
    
    private final List<Pattern> elements;
    
    public TuplePattern(List<Pattern> elements, SourceLocation location) {
        super(location);
        this.elements = elements;
    }
    
    public List<Pattern> getElements() {
        return elements;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}

