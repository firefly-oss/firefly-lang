package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.expr.BlockExpr;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.type.TypeParameter;

import java.util.List;

/**
 * Actor declaration AST node.
 * 
 * <p>Represents an actor with isolated state and message-passing concurrency.</p>
 * 
 * <h2>Syntax:</h2>
 * <pre>
 * actor Counter {
 *     let mut count: Int;
 *     
 *     init {
 *         self.count = 0;
 *     }
 *     
 *     receive {
 *         case Increment => self.count = self.count + 1
 *         case Decrement => self.count = self.count - 1
 *         case GetCount(reply) => reply.send(self.count)
 *     }
 * }
 * </pre>
 * 
 * @see com.firefly.runtime.actor.Actor
 */
public class ActorDecl extends Declaration {
    
    private final String name;
    private final List<TypeParameter> typeParameters;
    private final List<FieldDecl> fields;
    private final BlockExpr initBlock;
    private final List<ReceiveCase> receiveCases;
    
    public ActorDecl(
            String name,
            List<TypeParameter> typeParameters,
            List<FieldDecl> fields,
            BlockExpr initBlock,
            List<ReceiveCase> receiveCases,
            SourceLocation location) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.fields = fields;
        this.initBlock = initBlock;
        this.receiveCases = receiveCases;
    }
    
    public String getName() {
        return name;
    }
    
    public List<TypeParameter> getTypeParameters() {
        return typeParameters;
    }
    
    public List<FieldDecl> getFields() {
        return fields;
    }
    
    public BlockExpr getInitBlock() {
        return initBlock;
    }
    
    public List<ReceiveCase> getReceiveCases() {
        return receiveCases;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitActorDecl(this);
    }
    
    /**
     * Represents a case in a receive block.
     * 
     * <p>Example: {@code case Increment => self.count + 1}</p>
     */
    public static class ReceiveCase {
        private final Pattern pattern;
        private final Expression expression;
        private final SourceLocation location;
        
        public ReceiveCase(Pattern pattern, Expression expression, SourceLocation location) {
            this.pattern = pattern;
            this.expression = expression;
            this.location = location;
        }
        
        public Pattern getPattern() {
            return pattern;
        }
        
        public Expression getExpression() {
            return expression;
        }
        
        public SourceLocation getLocation() {
            return location;
        }
    }
}
