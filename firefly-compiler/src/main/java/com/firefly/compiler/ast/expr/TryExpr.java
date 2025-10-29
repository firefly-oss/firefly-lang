package com.firefly.compiler.ast.expr;

import com.firefly.compiler.ast.AstVisitor;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.type.Type;

import java.util.List;
import java.util.Optional;

/**
 * Try-catch-finally expression for exception handling
 * 
 * Examples:
 * - try { riskyOperation() } catch (e: IOException) { handleError(e) }
 * - try { operation() } catch { defaultHandler() }
 * - try { operation() } finally { cleanup() }
 * - try { operation() } catch (e: Exception) { handle(e) } finally { cleanup() }
 */
public class TryExpr extends Expression {
    
    private final BlockExpr tryBlock;
    private final List<CatchClause> catchClauses;
    private final BlockExpr finallyBlock; // May be null
    
    public TryExpr(
            BlockExpr tryBlock,
            List<CatchClause> catchClauses,
            BlockExpr finallyBlock,
            SourceLocation location) {
        super(location);
        this.tryBlock = tryBlock;
        this.catchClauses = catchClauses;
        this.finallyBlock = finallyBlock;
    }
    
    public BlockExpr getTryBlock() {
        return tryBlock;
    }
    
    public List<CatchClause> getCatchClauses() {
        return catchClauses;
    }
    
    public Optional<BlockExpr> getFinallyBlock() {
        return Optional.ofNullable(finallyBlock);
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitTryExpr(this);
    }
    
    /**
     * Represents a catch clause: catch (varName: ExceptionType) { handler }
     */
    public static class CatchClause {
        private final String variableName; // May be null for catch-all
        private final Type exceptionType;  // May be null for catch-all
        private final BlockExpr handler;
        
        /**
         * Constructor for typed catch clause: catch (e: IOException) { ... }
         */
        public CatchClause(String variableName, Type exceptionType, BlockExpr handler) {
            this.variableName = variableName;
            this.exceptionType = exceptionType;
            this.handler = handler;
        }
        
        /**
         * Constructor for catch-all clause: catch { ... }
         */
        public CatchClause(BlockExpr handler) {
            this.variableName = null;
            this.exceptionType = null;
            this.handler = handler;
        }
        
        public Optional<String> getVariableName() {
            return Optional.ofNullable(variableName);
        }
        
        public Optional<Type> getExceptionType() {
            return Optional.ofNullable(exceptionType);
        }
        
        public BlockExpr getHandler() {
            return handler;
        }
        
        public boolean isCatchAll() {
            return variableName == null && exceptionType == null;
        }
    }
}

