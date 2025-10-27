package com.firefly.compiler.semantic;

/**
 * Exception thrown during semantic analysis (type checking, symbol resolution, etc.)
 */
public class SemanticException extends RuntimeException {
    
    public SemanticException(String message) {
        super(message);
    }
    
    public SemanticException(String message, Throwable cause) {
        super(message, cause);
    }
}
