package com.firefly.runtime.exceptions;

/**
 * Exception thrown when data validation fails.
 * 
 * <p>This exception is commonly used in Flylang for:
 * <ul>
 *   <li>Spark validation blocks that detect invalid data</li>
 *   <li>Smart constructors with requires clauses</li>
 *   <li>Business logic validation failures</li>
 * </ul>
 * 
 * @see FlyException
 */
public class ValidationException extends FlyException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new ValidationException with the specified detail message.
     * 
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new ValidationException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause   the cause
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
