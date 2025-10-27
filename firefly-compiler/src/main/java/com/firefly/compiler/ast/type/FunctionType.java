package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;
import java.util.List;

public class FunctionType implements Type {
    private final List<Type> paramTypes;
    private final Type returnType;
    
    public FunctionType(List<Type> paramTypes, Type returnType) {
        this.paramTypes = paramTypes;
        this.returnType = returnType;
    }
    
    public List<Type> getParamTypes() {
        return paramTypes;
    }
    
    public Type getReturnType() {
        return returnType;
    }
    
    @Override
    public String getName() {
        return "(" + String.join(", ", paramTypes.stream().map(Type::getName).toList()) + 
               ") -> " + returnType.getName();
    }
    
    @Override
    public SourceLocation getLocation() {
        return SourceLocation.unknown();
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitFunctionType(this);
    }
}
