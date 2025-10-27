package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * If expression: if condition { then } else if condition2 { then2 } else { otherwise }
 */
public class IfExpr extends Expression {
    
    private final Expression condition;
    private final BlockExpr thenBranch;
    private final List<ElseIfBranch> elseIfBranches;
    private final BlockExpr elseBranch; // May be null
    
    public IfExpr(
            Expression condition,
            BlockExpr thenBranch,
            List<ElseIfBranch> elseIfBranches,
            BlockExpr elseBranch,
            SourceLocation location) {
        super(location);
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseIfBranches = elseIfBranches;
        this.elseBranch = elseBranch;
    }
    
    public Expression getCondition() {
        return condition;
    }
    
    public BlockExpr getThenBranch() {
        return thenBranch;
    }
    
    public List<ElseIfBranch> getElseIfBranches() {
        return elseIfBranches;
    }
    
    public Optional<BlockExpr> getElseBranch() {
        return Optional.ofNullable(elseBranch);
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitIfExpr(this);
    }
    
    /**
     * Represents an else-if branch
     */
    public static class ElseIfBranch {
        private final Expression condition;
        private final BlockExpr body;
        
        public ElseIfBranch(Expression condition, BlockExpr body) {
            this.condition = condition;
            this.body = body;
        }
        
        public Expression getCondition() {
            return condition;
        }
        
        public BlockExpr getBody() {
            return body;
        }
    }
}
