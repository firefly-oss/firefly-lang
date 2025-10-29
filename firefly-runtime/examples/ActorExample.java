package com.firefly.runtime.examples;

import com.firefly.runtime.actor.Actor;
import com.firefly.runtime.actor.Actor.ActorRef;
import com.firefly.runtime.actor.Actor.ActorSystem;

/**
 * Example demonstrating the Firefly Actor System.
 * 
 * This example shows:
 * - Creating actors with state
 * - Sending messages between actors
 * - Actor lifecycle (init, handle, terminate)
 * - Graceful shutdown
 */
public class ActorExample {

    /**
     * A simple counter actor that maintains an integer count.
     */
    static class Counter implements Actor<Integer, String> {
        private final String name;

        public Counter(String name) {
            this.name = name;
        }

        @Override
        public Integer init() {
            System.out.println("[" + name + "] Initializing with count = 0");
            return 0;
        }

        @Override
        public Integer handle(String message, Integer state) {
            System.out.println("[" + name + "] Received: " + message + ", Current count: " + state);
            
            return switch (message) {
                case "increment" -> {
                    int newState = state + 1;
                    System.out.println("[" + name + "] Incremented to: " + newState);
                    yield newState;
                }
                case "decrement" -> {
                    int newState = state - 1;
                    System.out.println("[" + name + "] Decremented to: " + newState);
                    yield newState;
                }
                case "reset" -> {
                    System.out.println("[" + name + "] Reset to: 0");
                    yield 0;
                }
                default -> {
                    System.out.println("[" + name + "] Unknown message, keeping state: " + state);
                    yield state;
                }
            };
        }

        @Override
        public void terminate(Integer state) {
            System.out.println("[" + name + "] Terminating with final count: " + state);
        }
    }

    /**
     * A logger actor that accumulates log messages.
     */
    static class Logger implements Actor<StringBuilder, String> {
        @Override
        public StringBuilder init() {
            System.out.println("[Logger] Starting...");
            return new StringBuilder();
        }

        @Override
        public StringBuilder handle(String message, StringBuilder state) {
            state.append("[LOG] ").append(message).append("\n");
            System.out.println("[Logger] Logged: " + message);
            return state;
        }

        @Override
        public void terminate(StringBuilder state) {
            System.out.println("[Logger] Final log:\n" + state.toString());
        }
    }

    /**
     * A ping-pong actor that responds to ping messages.
     */
    static class PingPong implements Actor<Integer, String> {
        private final ActorRef<String> logger;

        public PingPong(ActorRef<String> logger) {
            this.logger = logger;
        }

        @Override
        public Integer init() {
            logger.send("PingPong actor initialized");
            return 0;
        }

        @Override
        public Integer handle(String message, Integer state) {
            if (message.equals("ping")) {
                logger.send("Received ping #" + (state + 1));
                System.out.println("[PingPong] Pong! (count: " + (state + 1) + ")");
                return state + 1;
            }
            return state;
        }

        @Override
        public void terminate(Integer state) {
            logger.send("PingPong actor terminated after " + state + " pings");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Firefly Actor System Example ===\n");

        // Create an actor system with 4 threads
        ActorSystem system = new ActorSystem(4);

        try {
            // Example 1: Simple counter
            System.out.println("--- Example 1: Simple Counter ---");
            ActorRef<String> counter = system.spawn(new Counter("Counter1"));
            
            counter.send("increment");
            counter.send("increment");
            counter.send("increment");
            counter.send("decrement");
            counter.send("reset");
            counter.send("increment");
            
            Thread.sleep(500); // Give actors time to process
            counter.stop();
            Thread.sleep(200);
            
            System.out.println();

            // Example 2: Multiple counters
            System.out.println("--- Example 2: Multiple Counters ---");
            ActorRef<String> counter1 = system.spawn(new Counter("Counter-A"));
            ActorRef<String> counter2 = system.spawn(new Counter("Counter-B"));
            
            counter1.send("increment");
            counter2.send("increment");
            counter1.send("increment");
            counter2.send("increment");
            counter1.send("increment");
            
            Thread.sleep(500);
            counter1.stop();
            counter2.stop();
            Thread.sleep(200);
            
            System.out.println();

            // Example 3: Logger and PingPong
            System.out.println("--- Example 3: Logger and PingPong ---");
            ActorRef<String> logger = system.spawn(new Logger());
            ActorRef<String> pingPong = system.spawn(new PingPong(logger));
            
            logger.send("System started");
            
            for (int i = 0; i < 5; i++) {
                pingPong.send("ping");
                Thread.sleep(100);
            }
            
            logger.send("All pings sent");
            
            Thread.sleep(500);
            pingPong.stop();
            logger.stop();
            Thread.sleep(200);
            
            System.out.println();

            // Example 4: Stress test with many messages
            System.out.println("--- Example 4: Stress Test ---");
            ActorRef<String> stressCounter = system.spawn(new Counter("StressTest"));
            
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                stressCounter.send("increment");
            }
            
            Thread.sleep(2000); // Wait for processing
            long endTime = System.currentTimeMillis();
            
            System.out.println("Processed 1000 messages in " + (endTime - startTime) + "ms");
            stressCounter.stop();
            Thread.sleep(200);

        } finally {
            // Always shutdown the actor system
            System.out.println("\n--- Shutting down actor system ---");
            system.shutdown();
            System.out.println("Actor system shut down successfully");
        }

        System.out.println("\n=== Example Complete ===");
    }
}

