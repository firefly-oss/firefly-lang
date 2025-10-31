package com.firefly.compiler.ast.pattern;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.expr.Expression;

/**
 * Range pattern: start..end or start..=end
 */
public class RangePattern extends Pattern {
    private final Expression start;
    private final Expression end;
    private final boolean inclusive;

    public RangePattern(Expression start, Expression end, boolean inclusive, SourceLocation location) {
        super(location);
        this.start = start;
        this.end = end;
        this.inclusive = inclusive;
    }

    public Expression getStart() { return start; }
    public Expression getEnd() { return end; }
    public boolean isInclusive() { return inclusive; }

    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitPattern(this);
    }
}
