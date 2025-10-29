package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;

/**
 * Binary expression: left op right
 */
public class BinaryExpr extends Expression {
    
    public enum BinaryOp {
        // Arithmetic
        ADD,           // +
        SUBTRACT,      // -
        MULTIPLY,      // *
        DIVIDE,        // /
        MODULO,        // %
        POWER,         // **
        
        // Comparison
        EQUAL,         // ==
        NOT_EQUAL,     // !=
        LESS_THAN,     // <
        LESS_EQUAL,    // <=
        GREATER_THAN,  // >
        GREATER_EQUAL, // >=
        
        // Logical
        AND,           // &&
        OR,            // ||
        
        // Bitwise
        BIT_AND,       // &
        BIT_OR,        // |
        BIT_XOR,       // ^
        BIT_LEFT_SHIFT,  // <<
        BIT_RIGHT_SHIFT, // >>
        
        // Other
        RANGE,         // ..
        RANGE_INCLUSIVE, // ..=
        COALESCE,      // ??
        ELVIS,         // ?:
        SEND           // >> (actor message send, requires special parsing context)
    }
    
    private final Expression left;
    private final BinaryOp operator;
    private final Expression right;
    
    public BinaryExpr(
            Expression left,
            BinaryOp operator,
            Expression right,
            SourceLocation location) {
        super(location);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
    
    public Expression getLeft() {
        return left;
    }
    
    public BinaryOp getOperator() {
        return operator;
    }
    
    public Expression getRight() {
        return right;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitBinaryExpr(this);
    }
}
