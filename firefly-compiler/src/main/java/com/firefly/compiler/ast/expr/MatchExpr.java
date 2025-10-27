package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;

/**
 * Match expression: match value { pattern => expression, ... }
 */
public class MatchExpr extends Expression {
    
    private final Expression value;
    private final List<MatchArm> arms;
    
    public MatchExpr(Expression value, List<MatchArm> arms, SourceLocation location) {
        super(location);
        this.value = value;
        this.arms = arms;
    }
    
    public Expression getValue() {
        return value;
    }
    
    public List<MatchArm> getArms() {
        return arms;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitMatchExpr(this);
    }
    
    /**
     * Represents a single match arm: pattern => expression
     */
    public static class MatchArm {
        private final Pattern pattern;
        private final Expression guard; // May be null
        private final Expression body;
        
        public MatchArm(Pattern pattern, Expression guard, Expression body) {
            this.pattern = pattern;
            this.guard = guard;
            this.body = body;
        }
        
        public Pattern getPattern() {
            return pattern;
        }
        
        public Expression getGuard() {
            return guard;
        }
        
        public Expression getBody() {
            return body;
        }
    }
}
