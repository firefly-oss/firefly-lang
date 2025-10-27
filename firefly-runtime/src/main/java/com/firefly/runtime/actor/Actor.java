package com.firefly.runtime.actor;

import java.util.concurrent.*;
import java.util.function.BiFunction;

/**
 * Base interface for actors in the Firefly actor system.
 * Inspired by Erlang/Elixir's process model.
 */
public interface Actor<State, Message> {

    /**
     * Initialize the actor's state.
     */
    State init();

    /**
     * Handle a message and return the new state.
     */
    State handle(Message message, State state);

    /**
     * Called when the actor is being terminated.
     */
    default void terminate(State state) {
        // Default: do nothing
    }

    /**
     * Actor reference for sending messages.
     */
    class ActorRef<Message> {
        private final BlockingQueue<Message> mailbox;
        private final ExecutorService executor;
        private volatile boolean running;

        ActorRef(BlockingQueue<Message> mailbox, ExecutorService executor) {
            this.mailbox = mailbox;
            this.executor = executor;
            this.running = true;
        }

        /**
         * Send a message to this actor (asynchronous).
         */
        public void send(Message message) {
            if (!running) {
                throw new IllegalStateException("Actor is not running");
            }
            try {
                mailbox.put(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to send message", e);
            }
        }

        /**
         * Send a message to this actor (operator overload simulation).
         */
        public void tell(Message message) {
            send(message);
        }

        /**
         * Stop the actor.
         */
        public void stop() {
            running = false;
        }

        public boolean isRunning() {
            return running;
        }
    }

    /**
     * Actor system for managing actors.
     */
    class ActorSystem {
        private final ExecutorService executor;

        public ActorSystem(int threads) {
            this.executor = Executors.newFixedThreadPool(threads);
        }

        public ActorSystem() {
            this(Runtime.getRuntime().availableProcessors());
        }

        /**
         * Spawn a new actor.
         */
        public <State, Message> ActorRef<Message> spawn(Actor<State, Message> actor) {
            BlockingQueue<Message> mailbox = new LinkedBlockingQueue<>();
            ActorRef<Message> ref = new ActorRef<>(mailbox, executor);

            executor.submit(() -> runActor(actor, ref, mailbox));

            return ref;
        }

        private <State, Message> void runActor(
                Actor<State, Message> actor,
                ActorRef<Message> ref,
                BlockingQueue<Message> mailbox) {
            try {
                State state = actor.init();

                while (ref.isRunning()) {
                    Message message = mailbox.poll(100, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        state = actor.handle(message, state);
                    }
                }

                actor.terminate(state);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Actor crashed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * Shutdown the actor system.
         */
        public void shutdown() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
