package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Tuple struct pattern: TypeName(pattern1, pattern2, ...)
 * Matches data type variants and destructures them.
 * Also handles unit variants like None (with empty pattern list).
 */
public class TupleStructPattern extends Pattern {
    
    private final String typeName;
    private final List<Pattern> patterns;
    
    public TupleStructPattern(String typeName, List<Pattern> patterns, SourceLocation location) {
        super(location);
        this.typeName = typeName;
        this.patterns = patterns;
    }
    
    public String getTypeName() {
        return typeName;
    }
    
    public List<Pattern> getPatterns() {
        return patterns;
    }
    
    public boolean isUnitVariant() {
        return patterns.isEmpty();
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}

