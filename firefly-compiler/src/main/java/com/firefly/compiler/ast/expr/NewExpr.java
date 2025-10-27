package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

import java.util.List;

/**
 * New expression for object instantiation: new ClassName(args)
 * 
 * Examples:
 * - new ArrayList<String>()
 * - new User("John", 25)
 * - new HashMap<String, Int>()
 */
public class NewExpr extends Expression {
    
    private final Type type;           // The type being instantiated
    private final List<Expression> arguments;  // Constructor arguments
    
    public NewExpr(Type type, List<Expression> arguments, SourceLocation location) {
        super(location);
        this.type = type;
        this.arguments = arguments;
    }
    
    public Type getType() {
        return type;
    }
    
    public List<Expression> getArguments() {
        return arguments;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitNewExpr(this);
    }
}
