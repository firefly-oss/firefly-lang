package com.firefly.compiler.codegen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Professional type resolution system for Firefly compiler.
 * Handles imports, class resolution, and method signature lookups.
 */
public class TypeResolver {
    
    // Import mappings: simple name -> fully qualified name
    private final Map<String, String> importedTypes = new HashMap<>();
    
    // Package-level wildcard imports
    private final Map<String, String> wildcardImports = new HashMap<>();
    
    // Cached class information to avoid repeated reflection
    private final Map<String, Class<?>> classCache = new HashMap<>();
    
    // Classloader for loading classes
    private final ClassLoader classLoader;
    
    // NO hardcoded packages - only java.lang as per JLS
    
    public TypeResolver() {
        this(Thread.currentThread().getContextClassLoader());
    }
    
    public TypeResolver(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
    }
    
    /**
     * Register an import statement
     */
    public void addImport(String modulePath, String item) {
        // Convert Firefly-style imports to Java
        // e.g., "org::springframework::boot" -> "org.springframework.boot"
        String javaPath = modulePath.replace("::", ".");
        String fullName = javaPath + "." + item;
        importedTypes.put(item, fullName);
    }
    
    /**
     * Register a wildcard import
     */
    public void addWildcardImport(String modulePath) {
        String javaPath = modulePath.replace("::", ".");
        wildcardImports.put(modulePath, javaPath);
    }
    
    /**
     * Resolve a simple class name to its fully qualified name.
     * Uses ONLY explicit imports and wildcard imports - no hardcoded packages.
     * This is the professional approach following Java Language Specification.
     */
    public Optional<String> resolveClassName(String simpleName) {
        // 1. Check explicit imports first
        if (importedTypes.containsKey(simpleName)) {
            return Optional.of(importedTypes.get(simpleName));
        }
        
        // 2. Try wildcard imports
        for (String packagePath : wildcardImports.values()) {
            String fullName = packagePath + "." + simpleName;
            if (tryLoadClass(fullName)) {
                importedTypes.put(simpleName, fullName); // Cache for next time
                return Optional.of(fullName);
            }
        }
        
        // 3. JLS: java.lang is implicitly imported
        String javaLangName = "java.lang." + simpleName;
        if (tryLoadClass(javaLangName)) {
            importedTypes.put(simpleName, javaLangName);
            return Optional.of(javaLangName);
        }
        
        // 4. Try assuming it's already fully qualified
        if (tryLoadClass(simpleName)) {
            return Optional.of(simpleName);
        }
        
        // 5. Could not resolve - this means missing import!
        return Optional.empty();
    }
    
    /**
     * Get the loaded Class object for a fully qualified name.
     * Uses Thread context classloader to access Maven project dependencies.
     */
    public Optional<Class<?>> getClass(String fullyQualifiedName) {
        if (classCache.containsKey(fullyQualifiedName)) {
            return Optional.of(classCache.get(fullyQualifiedName));
        }
        
        try {
            // Use injected classloader (includes Maven project classpath)
            Class<?> clazz = Class.forName(fullyQualifiedName, true, classLoader);
            classCache.put(fullyQualifiedName, clazz);
            return Optional.of(clazz);
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if a method is static
     */
    public boolean isStaticMethod(String className, String methodName) {
        Optional<String> fullName = resolveClassName(className);
        if (!fullName.isPresent()) {
            return false;
        }
        
        Optional<Class<?>> clazz = getClass(fullName.get());
        if (!clazz.isPresent()) {
            return false;
        }
        
        // Check if any method with this name is static
        for (Method method : clazz.get().getDeclaredMethods()) {
            if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())) {
                return true;
            }
        }
        
        for (Method method : clazz.get().getMethods()) {
            if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get method descriptor for a static method
     */
    public Optional<MethodInfo> getStaticMethod(String className, String methodName, int argCount) {
        Optional<String> fullName = resolveClassName(className);
        if (!fullName.isPresent()) {
            return Optional.empty();
        }
        
        Optional<Class<?>> clazz = getClass(fullName.get());
        if (!clazz.isPresent()) {
            return Optional.empty();
        }
        
        Method bestMatch = null;
        int bestMatchScore = -1;
        
        // Find best matching static method
        // Priority: varargs methods > non-array params > array params
        for (Method method : clazz.get().getMethods()) {
            if (!method.getName().equals(methodName) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            
            int paramCount = method.getParameterCount();
            int score = 0;
            
            if (paramCount == argCount) {
                // Exact parameter count match
                if (method.isVarArgs()) {
                    score = 30; // Varargs methods are most flexible
                } else {
                    score = 20; // Non-varargs exact match
                }
                
                // Bonus: prefer methods with non-array first parameter
                if (paramCount > 0) {
                    Class<?> firstParam = method.getParameterTypes()[0];
                    if (!firstParam.isArray()) {
                        score += 10; // Prefer singular over array
                    }
                }
            } else if (method.isVarArgs() && argCount >= paramCount - 1) {
                // Varargs can accept argCount if it's >= fixed params count
                score = 5;
            } else {
                continue; // Not a match
            }
            
            if (score > bestMatchScore) {
                bestMatch = method;
                bestMatchScore = score;
            }
        }
        
        if (bestMatch != null) {
            return Optional.of(new MethodInfo(
                fullName.get().replace('.', '/'),
                methodName,
                getMethodDescriptor(bestMatch),
                true
            ));
        }
        
        return Optional.empty();
    }
    
    /**
     * Get method descriptor for an instance method
     */
    public Optional<MethodInfo> getInstanceMethod(String className, String methodName, int argCount) {
        Optional<String> fullName = resolveClassName(className);
        if (!fullName.isPresent()) {
            return Optional.empty();
        }
        
        Optional<Class<?>> clazz = getClass(fullName.get());
        if (!clazz.isPresent()) {
            return Optional.empty();
        }
        
        // Find matching instance method
        for (Method method : clazz.get().getDeclaredMethods()) {
            if (method.getName().equals(methodName) && 
                !Modifier.isStatic(method.getModifiers()) &&
                method.getParameterCount() == argCount) {
                return Optional.of(new MethodInfo(
                    fullName.get().replace('.', '/'),
                    methodName,
                    getMethodDescriptor(method),
                    false
                ));
            }
        }
        
        for (Method method : clazz.get().getMethods()) {
            if (method.getName().equals(methodName) && 
                !Modifier.isStatic(method.getModifiers()) &&
                method.getParameterCount() == argCount) {
                return Optional.of(new MethodInfo(
                    fullName.get().replace('.', '/'),
                    methodName,
                    getMethodDescriptor(method),
                    false
                ));
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Try to load a class without throwing exception
     */
    private boolean tryLoadClass(String fullName) {
        return getClass(fullName).isPresent();
    }
    
    /**
     * Convert a Java Method to JVM descriptor
     */
    private String getMethodDescriptor(Method method) {
        StringBuilder desc = new StringBuilder("(");
        
        for (Class<?> paramType : method.getParameterTypes()) {
            desc.append(getTypeDescriptor(paramType));
        }
        
        desc.append(")");
        desc.append(getTypeDescriptor(method.getReturnType()));
        
        return desc.toString();
    }
    
    /**
     * Convert a Java Class to JVM type descriptor
     */
    private String getTypeDescriptor(Class<?> clazz) {
        if (clazz == void.class) return "V";
        if (clazz == boolean.class) return "Z";
        if (clazz == byte.class) return "B";
        if (clazz == char.class) return "C";
        if (clazz == short.class) return "S";
        if (clazz == int.class) return "I";
        if (clazz == long.class) return "J";
        if (clazz == float.class) return "F";
        if (clazz == double.class) return "D";
        
        if (clazz.isArray()) {
            return "[" + getTypeDescriptor(clazz.getComponentType());
        }
        
        return "L" + clazz.getName().replace('.', '/') + ";";
    }
    
    /**
     * Information about a resolved method
     */
    public static class MethodInfo {
        public final String className;
        public final String methodName;
        public final String descriptor;
        public final boolean isStatic;
        
        public MethodInfo(String className, String methodName, String descriptor, boolean isStatic) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.isStatic = isStatic;
        }
    }
}
