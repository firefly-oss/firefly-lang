package com.firefly.runtime.exceptions;

/**
 * Base exception class for all Flylang exceptions.
 * 
 * <p>This is the root of the Flylang exception hierarchy, similar to how
 * {@code java.lang.Exception} is the root for Java exceptions. All custom
 * Flylang exceptions should extend this class.</p>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Immutability:</b> Exception messages and causes are immutable once created</li>
 *   <li><b>Type Safety:</b> Proper exception hierarchy for compile-time checking</li>
 *   <li><b>Interoperability:</b> Extends RuntimeException for seamless Java integration</li>
 *   <li><b>Rich Context:</b> Support for cause chains and detailed error messages</li>
 * </ul>
 * 
 * <h2>Example Usage in Flylang</h2>
 * <pre>{@code
 * exception ValidationError extends FlyException {
 *     priv let field: String
 *     
 *     pub init(message: String, field: String) {
 *         super(message)
 *         self.field = field
 *     }
 *     
 *     pub fn getField() -> String {
 *         self.field
 *     }
 * }
 * 
 * fn validateAge(age: Int) -> Unit {
 *     if (age < 0) {
 *         throw new ValidationError("Age cannot be negative", "age")
 *     }
 * }
 * }</pre>
 * 
 * @see java.lang.RuntimeException
 */
public class FlyException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new FlyException with no detail message.
     */
    public FlyException() {
        super();
    }
    
    /**
     * Constructs a new FlyException with the specified detail message.
     * 
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method)
     */
    public FlyException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new FlyException with the specified detail message and cause.
     * 
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method)
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method). A {@code null} value is
     *                permitted, and indicates that the cause is nonexistent or unknown.
     */
    public FlyException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new FlyException with the specified cause and a detail
     * message of {@code (cause==null ? null : cause.toString())} (which
     * typically contains the class and detail message of {@code cause}).
     * 
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link #getCause()} method). A {@code null} value is
     *              permitted, and indicates that the cause is nonexistent or unknown.
     */
    public FlyException(Throwable cause) {
        super(cause);
    }
    
    /**
     * Returns a string representation of this exception.
     * 
     * @return a string representation of this exception
     */
    @Override
    public String toString() {
        String className = getClass().getSimpleName();
        String message = getLocalizedMessage();
        return (message != null) ? (className + ": " + message) : className;
    }
}
