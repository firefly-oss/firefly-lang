package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.Statement;

import java.util.List;
import java.util.Optional;

/**
 * Block expression: { statements; finalExpression }
 * The value of a block is the value of its final expression.
 */
public class BlockExpr extends Expression {
    
    private final List<Statement> statements;
    private final Expression finalExpression; // May be null if block is Unit-typed
    
    public BlockExpr(
            List<Statement> statements,
            Expression finalExpression,
            SourceLocation location) {
        super(location);
        this.statements = statements;
        this.finalExpression = finalExpression;
    }
    
    public List<Statement> getStatements() {
        return statements;
    }
    
    public Optional<Expression> getFinalExpression() {
        return Optional.ofNullable(finalExpression);
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitBlockExpr(this);
    }
}
