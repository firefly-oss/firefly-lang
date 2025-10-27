package com.firefly.compiler.ast;

import java.util.List;
import java.util.Map;

/**
 * Represents an annotation like @RestController or @GetMapping("/api/users")
 */
public class Annotation {
    
    private final String name;  // e.g., "RestController", "GetMapping"
    private final Map<String, Object> arguments;  // e.g., {"value": "/api/users"}
    private final SourceLocation location;
    
    public Annotation(String name, Map<String, Object> arguments, SourceLocation location) {
        this.name = name;
        this.arguments = arguments;
        this.location = location;
    }
    
    public String getName() {
        return name;
    }
    
    public Map<String, Object> getArguments() {
        return arguments;
    }
    
    public SourceLocation getLocation() {
        return location;
    }
    
    /**
     * Check if this annotation has a specific name
     */
    public boolean isNamed(String name) {
        return this.name.equals(name) || this.name.endsWith("." + name);
    }
    
    /**
     * Get the value argument (most common case)
     */
    public Object getValue() {
        return arguments.get("value");
    }
    
    @Override
    public String toString() {
        if (arguments.isEmpty()) {
            return "@" + name;
        } else {
            return "@" + name + arguments;
        }
    }
}
