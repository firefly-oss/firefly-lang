package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.ast.type.TypeParameter;
import java.util.List;

/**
 * Represents a type alias declaration in the AST.
 * Grammar: 'type' TYPE_IDENTIFIER typeParameters? '=' type
 *
 * Example: type UserId = Int
 * Example: type Result<T> = Either<Error, T>
 */
public class TypeAliasDecl extends Declaration {
    private final String name;
    private final List<TypeParameter> typeParameters;
    private final Type targetType;

    public TypeAliasDecl(
        String name,
        List<TypeParameter> typeParameters,
        Type targetType,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.targetType = targetType;
    }

    public String getName() { return name; }
    public List<TypeParameter> getTypeParameters() { return typeParameters; }
    public Type getTargetType() { return targetType; }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) { 
        return visitor.visitTypeAliasDecl(this); 
    }
}
