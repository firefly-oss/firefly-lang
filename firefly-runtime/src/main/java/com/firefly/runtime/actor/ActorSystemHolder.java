package com.firefly.runtime.actor;

/**
 * Singleton holder for the global ActorSystem.
 * 
 * This provides a default ActorSystem instance that is used by the spawn() builtin.
 * The ActorSystem is created lazily on first access using the Bill Pugh Singleton pattern.
 */
public class ActorSystemHolder {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private ActorSystemHolder() {
        throw new AssertionError("Cannot instantiate ActorSystemHolder");
    }
    
    /**
     * Bill Pugh Singleton pattern - thread-safe lazy initialization.
     */
    private static class Holder {
        private static final Actor.ActorSystem INSTANCE = new Actor.ActorSystem();
    }
    
    /**
     * Get the global ActorSystem instance.
     * 
     * @return The singleton ActorSystem instance
     */
    public static Actor.ActorSystem getInstance() {
        return Holder.INSTANCE;
    }
    
    /**
     * Shutdown the global ActorSystem.
     * This should be called when the application is terminating.
     */
    public static void shutdown() {
        Holder.INSTANCE.shutdown();
    }
}
