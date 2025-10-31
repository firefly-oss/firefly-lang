package firefly.std.option;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Predicate;

/**
 * Option Type - Safe handling of optional values
 * 
 * The Option type represents an optional value: every Option is either Some and contains a value,
 * or None, and does not.
 * 
 * @param <T> The type of the value
 */
public abstract class Option<T> {
    
    // Private constructor to prevent external subclassing
    private Option() {}
    
    /**
     * Some variant - contains a value
     */
    public static final class Some<T> extends Option<T> {
        public final T value;
        // Bytecode generator expects field named value0
        public final T value0;
        
        public Some(T value) {
            this.value = Objects.requireNonNull(value, "Some cannot contain null");
            this.value0 = value; // Alias for bytecode compatibility
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Some)) return false;
            Some<?> other = (Some<?>) obj;
            return Objects.equals(value, other.value);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
        
        @Override
        public String toString() {
            return "Some(" + value + ")";
        }
    }
    
    /**
     * None variant - no value (inner class)
     */
    public static final class NoneType<T> extends Option<T> {
        // Singleton instance
        private static final NoneType<?> INSTANCE = new NoneType<>();
        
        private NoneType() {}
        
        @SuppressWarnings("unchecked")
        public static <T> NoneType<T> instance() {
            return (NoneType<T>) INSTANCE;
        }
        
        @Override
        public boolean equals(Object obj) {
            return obj instanceof NoneType;
        }
        
        @Override
        public int hashCode() {
            return 0;
        }
        
        @Override
        public String toString() {
            return "None";
        }
    }
    
    // Public static constant for None (bytecode generator expects field named "None")
    @SuppressWarnings("rawtypes")
    public static final Option None = NoneType.instance();
    
    // Static factory methods
    
    /**
     * Creates a Some variant containing the given value.
     * This method name matches what the bytecode generator expects.
     */
    @SuppressWarnings("unchecked")
    public static <T> Option<T> Some(T value) {
        return new Some<>(value);
    }
    
    /**
     * Creates a Some variant containing the given value.
     */
    public static <T> Option<T> some(T value) {
        return new Some<>(value);
    }
    
    /**
     * Creates a None variant.
     */
    @SuppressWarnings("unchecked")
    public static <T> Option<T> none() {
        return (Option<T>) None;
    }
    
    // Querying methods
    
    /**
     * Returns true if the option is a Some value.
     */
    public boolean isSome() {
        return this instanceof Some;
    }
    
    /**
     * Returns true if the option is None.
     */
    public boolean isNone() {
        return this instanceof NoneType;
    }
    
    // Extracting values
    
    /**
     * Returns the contained Some value.
     * @throws RuntimeException if the value is None
     */
    public T unwrap() {
        if (this instanceof Some) {
            return ((Some<T>) this).value;
        }
        throw new RuntimeException("Called unwrap on None");
    }
    
    /**
     * Returns the contained Some value or a provided default.
     */
    public T unwrapOr(T defaultValue) {
        if (this instanceof Some) {
            return ((Some<T>) this).value;
        }
        return defaultValue;
    }
    
    /**
     * Returns the contained Some value or computes it from a supplier.
     */
    public T unwrapOrElse(Supplier<T> supplier) {
        if (this instanceof Some) {
            return ((Some<T>) this).value;
        }
        return supplier.get();
    }
    
    // Transforming values
    
    /**
     * Maps an Option<T> to Option<U> by applying a function to a contained value.
     */
    @SuppressWarnings("unchecked")
    public <U> Option<U> map(Function<T, U> f) {
        if (this instanceof Some) {
            return new Some<>(f.apply(((Some<T>) this).value));
        }
        return (Option<U>) None;
    }
    
    /**
     * Applies a function to the contained value (if any), or returns the provided default (if not).
     */
    public <U> U mapOr(U defaultValue, Function<T, U> f) {
        if (this instanceof Some) {
            return f.apply(((Some<T>) this).value);
        }
        return defaultValue;
    }
    
    /**
     * Applies a function to the contained value (if any), or computes a default (if not).
     */
    public <U> U mapOrElse(Supplier<U> defaultFn, Function<T, U> f) {
        if (this instanceof Some) {
            return f.apply(((Some<T>) this).value);
        }
        return defaultFn.get();
    }
    
    // Boolean operations
    
    /**
     * Returns None if the option is None, otherwise calls f with the wrapped value and returns the result.
     */
    @SuppressWarnings("unchecked")
    public <U> Option<U> flatMap(Function<T, Option<U>> f) {
        if (this instanceof Some) {
            return f.apply(((Some<T>) this).value);
        }
        return (Option<U>) None;
    }
    
    /**
     * Returns None if the option is None, otherwise calls predicate with the wrapped value.
     */
    @SuppressWarnings("unchecked")
    public Option<T> filter(Predicate<T> predicate) {
        if (this instanceof Some) {
            T value = ((Some<T>) this).value;
            if (predicate.test(value)) {
                return this;
            }
        }
        return (Option<T>) None;
    }
    
    /**
     * Returns the option if it contains a value, otherwise returns other.
     */
    public Option<T> or(Option<T> other) {
        if (this instanceof Some) {
            return this;
        }
        return other;
    }
    
    /**
     * Returns the option if it contains a value, otherwise calls f and returns the result.
     */
    public Option<T> orElse(Supplier<Option<T>> f) {
        if (this instanceof Some) {
            return this;
        }
        return f.get();
    }
}
