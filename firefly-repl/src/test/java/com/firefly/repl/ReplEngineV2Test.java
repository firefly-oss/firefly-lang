package com.firefly.repl;

public class ReplEngineV2Test {
    
    public static void main(String[] args) {
        ReplEngine engine = new ReplEngine();
        
        // Test 1: let mut
        System.out.println("=== Test 1: let mut x: Int = 10; ===");
        ReplEngine.EvalResult r1 = engine.eval("let mut x: Int = 10;");
        System.out.println("Success: " + r1.isSuccess());
        if (!r1.isSuccess()) {
            System.out.println("Error: " + r1.getError());
            if (r1.getCause() != null) {
                r1.getCause().printStackTrace();
            }
        }
        
        // Test 2: Read x
        System.out.println("\n=== Test 2: x ===");
        ReplEngine.EvalResult r2 = engine.eval("x");
        System.out.println("Success: " + r2.isSuccess());
        if (!r2.isSuccess()) {
            System.out.println("Error: " + r2.getError());
        }
        
        // Test 3: Assignment
        System.out.println("\n=== Test 3: x = 15; ===");
        ReplEngine.EvalResult r3 = engine.eval("x = 15;");
        System.out.println("Success: " + r3.isSuccess());
        if (!r3.isSuccess()) {
            System.out.println("Error: " + r3.getError());
        }
        
        // Test 4: Read x again
        System.out.println("\n=== Test 4: x ===");
        ReplEngine.EvalResult r4 = engine.eval("x");
        System.out.println("Success: " + r4.isSuccess());
        if (!r4.isSuccess()) {
            System.out.println("Error: " + r4.getError());
        }
        
        // Test 5: println
        System.out.println("\n=== Test 5: println(x) ===");
        ReplEngine.EvalResult r5 = engine.eval("println(x)");
        System.out.println("Success: " + r5.isSuccess());
        if (!r5.isSuccess()) {
            System.out.println("Error: " + r5.getError());
            if (r5.getCause() != null) {
                r5.getCause().printStackTrace();
            }
        }
        
        System.out.println("\n=== ALL TESTS COMPLETE ===");
    }
}
