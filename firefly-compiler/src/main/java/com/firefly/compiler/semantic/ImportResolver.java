package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.UseDeclaration;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves import statements to fully qualified class names.
 * 
 * Handles:
 * - Simple imports: import java.util.List
 * - Wildcard imports: import java.util.*
 * - Spring annotations: import org.springframework.web.bind.annotation.RestController
 * 
 * Maps short names to fully qualified names for type resolution.
 */
public class ImportResolver {
    
    // Maps simple name -> fully qualified name
    private final Map<String, String> importMap = new HashMap<>();
    
    // Track wildcard imports
    private final java.util.List<String> wildcardPackages = new java.util.ArrayList<>();
    
    /**
     * Add an import declaration to the resolver
     */
    public void addImport(UseDeclaration importDecl) {
        String modulePath = importDecl.getModulePath();
        
        if (importDecl.isWildcard()) {
            // Wildcard import: import java.util.*
            wildcardPackages.add(convertToJavaPackage(modulePath));
        } else if (importDecl.getItems().isEmpty()) {
            // Simple import: import java.util.ArrayList
            String javaClassName = convertToJavaClassName(modulePath);
            String simpleName = getSimpleName(javaClassName);
            importMap.put(simpleName, javaClassName);
        } else {
            // Specific items: import java.util::{ArrayList, HashMap}
            String packagePath = convertToJavaPackage(modulePath);
            for (String item : importDecl.getItems()) {
                String fullClassName = packagePath + "." + item;
                importMap.put(item, fullClassName);
            }
        }
    }
    
    /**
     * Resolve a simple class name to its fully qualified name
     * 
     * @param simpleName e.g., "ArrayList", "RestController"
     * @return fully qualified name e.g., "java.util.ArrayList", "org.springframework.web.bind.annotation.RestController"
     */
    public String resolve(String simpleName) {
        // Check explicit imports first
        if (importMap.containsKey(simpleName)) {
            return importMap.get(simpleName);
        }
        
        // Check wildcard imports
        for (String pkg : wildcardPackages) {
            String candidate = pkg + "." + simpleName;
            // In a real implementation, we'd check if class exists on classpath
            // For now, we'll assume it exists if it's a known package
            if (isKnownPackage(pkg)) {
                return candidate;
            }
        }
        
        // Check common Java/Spring packages
        String resolved = resolveCommonClass(simpleName);
        if (resolved != null) {
            return resolved;
        }
        
        // Return as-is if not found (might be a Firefly type)
        return simpleName;
    }
    
    /**
     * Convert Firefly module path to Java package
     * e.g., "java::util" -> "java.util"
     */
    private String convertToJavaPackage(String modulePath) {
        return modulePath.replace("::", ".");
    }
    
    /**
     * Convert Firefly module path to Java class name
     * e.g., "java::util::ArrayList" -> "java.util.ArrayList"
     */
    private String convertToJavaClassName(String modulePath) {
        return modulePath.replace("::", ".");
    }
    
    /**
     * Get simple name from fully qualified name
     * e.g., "java.util.ArrayList" -> "ArrayList"
     */
    private String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
    
    /**
     * Check if package is a known package
     */
    private boolean isKnownPackage(String pkg) {
        return pkg.startsWith("java.") 
            || pkg.startsWith("javax.")
            || pkg.startsWith("org.springframework.")
            || pkg.startsWith("com.firefly.");
    }
    
    /**
     * Resolve common Java and Spring classes without explicit imports
     */
    private String resolveCommonClass(String simpleName) {
        // Java standard library
        switch (simpleName) {
            case "String": return "java.lang.String";
            case "Integer": return "java.lang.Integer";
            case "Long": return "java.lang.Long";
            case "Double": return "java.lang.Double";
            case "Boolean": return "java.lang.Boolean";
            case "Object": return "java.lang.Object";
            case "List": return "java.util.List";
            case "ArrayList": return "java.util.ArrayList";
            case "Map": return "java.util.Map";
            case "HashMap": return "java.util.HashMap";
            case "Set": return "java.util.Set";
            case "HashSet": return "java.util.HashSet";
            case "Optional": return "java.util.Optional";
            
            // Spring annotations
            case "RestController": return "org.springframework.web.bind.annotation.RestController";
            case "Controller": return "org.springframework.stereotype.Controller";
            case "Service": return "org.springframework.stereotype.Service";
            case "Component": return "org.springframework.stereotype.Component";
            case "Repository": return "org.springframework.stereotype.Repository";
            case "Autowired": return "org.springframework.beans.factory.annotation.Autowired";
            case "RequestMapping": return "org.springframework.web.bind.annotation.RequestMapping";
            case "GetMapping": return "org.springframework.web.bind.annotation.GetMapping";
            case "PostMapping": return "org.springframework.web.bind.annotation.PostMapping";
            case "PutMapping": return "org.springframework.web.bind.annotation.PutMapping";
            case "DeleteMapping": return "org.springframework.web.bind.annotation.DeleteMapping";
            case "PatchMapping": return "org.springframework.web.bind.annotation.PatchMapping";
            case "RequestParam": return "org.springframework.web.bind.annotation.RequestParam";
            case "PathVariable": return "org.springframework.web.bind.annotation.PathVariable";
            case "RequestBody": return "org.springframework.web.bind.annotation.RequestBody";
            case "ResponseBody": return "org.springframework.web.bind.annotation.ResponseBody";
            case "SpringBootApplication": return "org.springframework.boot.autoconfigure.SpringBootApplication";
            
            default: return null;
        }
    }
    
    /**
     * Convert fully qualified name to JVM descriptor
     * e.g., "java.util.ArrayList" -> "Ljava/util/ArrayList;"
     */
    public String toJvmDescriptor(String fullyQualifiedName) {
        // Handle primitive types
        switch (fullyQualifiedName) {
            case "int": return "I";
            case "long": return "J";
            case "float": return "F";
            case "double": return "D";
            case "boolean": return "Z";
            case "char": return "C";
            case "byte": return "B";
            case "short": return "S";
            case "void": return "V";
        }
        
        // Object types
        return "L" + fullyQualifiedName.replace('.', '/') + ";";
    }
    
    /**
     * Get all resolved imports for debugging
     */
    public Map<String, String> getImportMap() {
        return new HashMap<>(importMap);
    }
    
    /**
     * Clear all imports (for new compilation unit)
     */
    public void clear() {
        importMap.clear();
        wildcardPackages.clear();
    }
}
