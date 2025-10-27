package com.firefly.runtime.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Immutable persistent list implementation using a linked structure.
 * Operations create new lists that share structure with the original.
 */
public sealed interface PersistentList<T> extends Iterable<T> {

    /**
     * Returns an empty list.
     */
    static <T> PersistentList<T> empty() {
        return (Nil<T>) Nil.INSTANCE;
    }

    /**
     * Creates a list from varargs.
     */
    @SafeVarargs
    static <T> PersistentList<T> of(T... elements) {
        PersistentList<T> list = empty();
        for (int i = elements.length - 1; i >= 0; i--) {
            list = list.cons(elements[i]);
        }
        return list;
    }

    /**
     * Returns true if the list is empty.
     */
    boolean isEmpty();

    /**
     * Returns the head (first element) of the list.
     */
    T head();

    /**
     * Returns the tail (all elements except the first) of the list.
     */
    PersistentList<T> tail();

    /**
     * Prepends an element to the list.
     */
    PersistentList<T> cons(T element);

    /**
     * Returns the size of the list.
     */
    int size();

    /**
     * Maps a function over the list.
     */
    <R> PersistentList<R> map(Function<T, R> mapper);

    /**
     * Filters the list based on a predicate.
     */
    PersistentList<T> filter(java.util.function.Predicate<T> predicate);

    /**
     * Empty list (Nil).
     */
    final class Nil<T> implements PersistentList<T> {
        @SuppressWarnings("rawtypes")
        static final Nil INSTANCE = new Nil();

        private Nil() {}

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public T head() {
            throw new NoSuchElementException("head of empty list");
        }

        @Override
        public PersistentList<T> tail() {
            throw new NoSuchElementException("tail of empty list");
        }

        @Override
        public PersistentList<T> cons(T element) {
            return new Cons<>(element, this);
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public <R> PersistentList<R> map(Function<T, R> mapper) {
            return empty();
        }

        @Override
        public PersistentList<T> filter(java.util.function.Predicate<T> predicate) {
            return this;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public T next() {
                    throw new NoSuchElementException();
                }
            };
        }

        @Override
        public String toString() {
            return "[]";
        }
    }

    /**
     * Non-empty list (Cons).
     */
    final class Cons<T> implements PersistentList<T> {
        private final T head;
        private final PersistentList<T> tail;
        private final int size;

        Cons(T head, PersistentList<T> tail) {
            this.head = head;
            this.tail = tail;
            this.size = tail.size() + 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public T head() {
            return head;
        }

        @Override
        public PersistentList<T> tail() {
            return tail;
        }

        @Override
        public PersistentList<T> cons(T element) {
            return new Cons<>(element, this);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public <R> PersistentList<R> map(Function<T, R> mapper) {
            return tail.map(mapper).cons(mapper.apply(head));
        }

        @Override
        public PersistentList<T> filter(java.util.function.Predicate<T> predicate) {
            PersistentList<T> filteredTail = tail.filter(predicate);
            return predicate.test(head) ? filteredTail.cons(head) : filteredTail;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<>() {
                private PersistentList<T> current = Cons.this;

                @Override
                public boolean hasNext() {
                    return !current.isEmpty();
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    T value = current.head();
                    current = current.tail();
                    return value;
                }
            };
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            Iterator<T> it = iterator();
            while (it.hasNext()) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
