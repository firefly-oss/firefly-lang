package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Unary expression: !expr, -expr, *expr, &expr, etc.
 */
public class UnaryExpr extends Expression {
    
    public enum UnaryOp {
        NOT,           // !
        MINUS,         // -
        DEREF,         // *
        REF,           // &
        MUT_REF,       // &mut
        UNWRAP,        // ?
        FORCE_UNWRAP,  // !!
        AWAIT          // .await
    }
    
    private final UnaryOp operator;
    private final Expression operand;
    
    public UnaryExpr(UnaryOp operator, Expression operand, SourceLocation location) {
        super(location);
        this.operator = operator;
        this.operand = operand;
    }
    
    public UnaryOp getOperator() {
        return operator;
    }
    
    public Expression getOperand() {
        return operand;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitUnaryExpr(this);
    }
}
