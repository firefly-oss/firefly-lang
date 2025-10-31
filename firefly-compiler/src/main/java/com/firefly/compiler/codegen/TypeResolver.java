package com.firefly.compiler.codegen;

import com.firefly.compiler.types.FireflyType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Professional type resolution system for Firefly compiler.
 *
 * <p>Handles imports, class resolution, and method signature lookups with support for:</p>
 * <ul>
 *   <li>Explicit imports: {@code import java::util::ArrayList}</li>
 *   <li>Wildcard imports: {@code import java::util::*}</li>
 *   <li>Firefly standard library prelude (automatically imported)</li>
 *   <li>Java standard library (java.lang automatically imported per JLS)</li>
 * </ul>
 *
 * <p>The Firefly standard library prelude is automatically available in all compilation units,
 * providing access to core types like Option, Result, and common utility functions.</p>
 *
 * @see com.firefly.compiler.ast.UseDeclaration
 * @see com.firefly.compiler.semantics.SemanticAnalyzer
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

    // Current module package (e.g., com.example), used for resolving local class names
    private String currentModulePackage = null;

    /**
     * Creates a TypeResolver with the default classloader.
     * Automatically imports the Firefly standard library prelude.
     */
    public TypeResolver() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a TypeResolver with a custom classloader.
     * Automatically imports the Firefly standard library prelude.
     *
     * @param classLoader The classloader to use for class resolution
     */
    public TypeResolver(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        initializeStandardLibrary();
    }

    /**
     * Initialize the Firefly standard library prelude.
     * This makes core types and functions available without explicit imports.
     *
     * <p>Automatically imports:</p>
     * <ul>
     *   <li>Firefly stdlib modules (option, result, math, string)</li>
     *   <li>Firefly runtime collections (PersistentList, PersistentVector, etc.)</li>
     *   <li>Firefly runtime async (Future)</li>
     *   <li>Firefly runtime actors (Actor, ActorSystem, ActorRef)</li>
     * </ul>
     */
    private void initializeStandardLibrary() {
        // Import firefly::std::option module
        addWildcardImport("firefly::std::option");

        // Import firefly::std::result module
        addWildcardImport("firefly::std::result");

        // Import firefly::std::math module
        addWildcardImport("firefly::std::math");

        // Import firefly::std::string module
        addWildcardImport("firefly::std::string");
        
        // Import firefly::std::time module (Date, DateTime, Instant, Duration)
        addWildcardImport("firefly::std::time");

        // Import Firefly runtime collections
        addWildcardImport("com::firefly::runtime::collections");

        // Import Firefly runtime async
        addWildcardImport("com::firefly::runtime::async");

        // Import Firefly runtime actors
        addWildcardImport("com::firefly::runtime::actor");
    }
    
    /**
     * Register an explicit import statement.
     *
     * <p>Converts Firefly-style module paths to Java package paths and registers
     * the imported type for resolution.</p>
     *
     * <p>Example: {@code addImport("org::springframework::boot", "SpringApplication")}
     * registers {@code SpringApplication} as {@code org.springframework.boot.SpringApplication}</p>
     *
     * @param modulePath The Firefly module path (e.g., "org::springframework::boot")
     * @param item The type or function name to import (e.g., "SpringApplication")
     */
    public void addImport(String modulePath, String item) {
        // Convert Firefly-style imports to Java
        // e.g., "org::springframework::boot" -> "org.springframework.boot"
        String javaPath = modulePath.replace("::", ".");
        String fullName = javaPath + "." + item;
        importedTypes.put(item, fullName);
    }

    /**
     * Register a wildcard import statement.
     *
     * <p>Converts Firefly-style module paths to Java package paths and registers
     * the package for wildcard resolution. Types from this package will be resolved
     * on-demand when referenced.</p>
     *
     * <p>Example: {@code addWildcardImport("java::util")} makes all types from
     * {@code java.util} available for resolution.</p>
     *
     * @param modulePath The Firefly module path (e.g., "java::util")
     */
    public void addWildcardImport(String modulePath) {
        String javaPath = modulePath.replace("::", ".");
        wildcardImports.put(modulePath, javaPath);
    }
    
    /**
     * Resolve a simple class name to its fully qualified name.
     *
     * <p>Resolution follows this priority order:</p>
     * <ol>
     *   <li>Explicit imports (highest priority)</li>
     *   <li>Wildcard imports (including Firefly stdlib prelude)</li>
     *   <li>java.lang package (per Java Language Specification)</li>
     *   <li>Fully qualified names</li>
     * </ol>
     *
     * <p>This implementation follows the Java Language Specification approach
     * with no hardcoded packages except java.lang. All other packages must be
     * explicitly imported or available via wildcard imports.</p>
     *
     * <p>Successfully resolved names are cached for performance.</p>
     *
     * @param simpleName The simple class name to resolve (e.g., "ArrayList", "Option")
     * @return The fully qualified class name if resolved, empty otherwise
     */
    public void setCurrentModulePackage(String pkg) {
        this.currentModulePackage = pkg;
    }
    
    public Optional<String> resolveClassName(String simpleName) {
        // 0. Check Firefly native types first (highest priority)
        FireflyType fireflyType = FireflyType.fromFireflyName(simpleName);
        if (fireflyType != null && fireflyType.getJvmInternalName() != null) {
            // Return the JVM internal name as a dotted class name
            return Optional.of(fireflyType.getJvmInternalName().replace('/', '.'));
        }
        
        // 1. Check explicit imports
        if (importedTypes.containsKey(simpleName)) {
            return Optional.of(importedTypes.get(simpleName));
        }

        // 2. Try wildcard imports (includes Firefly stdlib prelude)
        for (String packagePath : wildcardImports.values()) {
            String fullName = packagePath + "." + simpleName;
            if (tryLoadClass(fullName)) {
                importedTypes.put(simpleName, fullName); // Cache for next time
                return Optional.of(fullName);
            }
        }

        // 3. Current module package fallback
        if (currentModulePackage != null && !currentModulePackage.isEmpty()) {
            String moduleName = currentModulePackage + "." + simpleName;
            if (tryLoadClass(moduleName)) {
                importedTypes.put(simpleName, moduleName);
                return Optional.of(moduleName);
            }
        }

        // 4. JLS: java.lang is implicitly imported
        String javaLangName = "java.lang." + simpleName;
        if (tryLoadClass(javaLangName)) {
            importedTypes.put(simpleName, javaLangName);
            return Optional.of(javaLangName);
        }

        // 5. Try assuming it's already fully qualified
        if (tryLoadClass(simpleName)) {
            return Optional.of(simpleName);
        }

        // 6. Could not resolve - this means missing import!
        return Optional.empty();
    }
    
    /**
     * Attempt to resolve a nested variant class (e.g., Result$Ok) given the variant simple name.
     * This tries all explicitly imported types as potential outers.
     */
    public Optional<String> resolveVariantNestedClass(String variantSimpleName) {
        // Prefer explicitly imported types as potential outers
        for (String outerFqcn : importedTypes.values()) {
            String candidate = outerFqcn + "$" + variantSimpleName;
            if (tryLoadClass(candidate)) {
                return Optional.of(candidate);
            }
        }
        
        // Canonical mapping for stdlib ADTs without requiring classloader
        if ("Some".equals(variantSimpleName) || "None".equals(variantSimpleName)) {
            return Optional.of("firefly.std.option.Option$" + variantSimpleName);
        }
        if ("Ok".equals(variantSimpleName) || "Err".equals(variantSimpleName)) {
            return Optional.of("firefly.std.result.Result$" + variantSimpleName);
        }
        
        // Also try current module package if available (Outer$Variant)
        if (currentModulePackage != null && !currentModulePackage.isEmpty()) {
            String candidate = currentModulePackage + "." + variantSimpleName;
            return Optional.of(candidate);
        }
        return Optional.empty();
    }
    
    /**
     * Get the loaded Class object for a fully qualified name.
     *
     * <p>Uses the configured classloader to load classes, which includes:</p>
     * <ul>
     *   <li>JDK standard library classes</li>
     *   <li>Maven project dependencies</li>
     *   <li>Firefly runtime and standard library</li>
     * </ul>
     *
     * <p>Successfully loaded classes are cached for performance.</p>
     *
     * @param fullyQualifiedName The fully qualified class name (e.g., "java.util.ArrayList")
     * @return The loaded Class object if found, empty otherwise
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
     * Check if a method is static.
     *
     * <p>Resolves the class name and checks if any method with the given name
     * has the static modifier.</p>
     *
     * @param className The simple or fully qualified class name
     * @param methodName The method name to check
     * @return true if a static method with this name exists, false otherwise
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
     * Get method descriptor for a static method.
     *
     * <p>Resolves the class and finds the best matching static method based on:</p>
     * <ul>
     *   <li>Method name</li>
     *   <li>Argument count</li>
     *   <li>Varargs compatibility</li>
     * </ul>
     *
     * <p>Scoring priority (highest to lowest):</p>
     * <ol>
     *   <li>Varargs methods with exact parameter count (score: 30+)</li>
     *   <li>Non-varargs methods with exact parameter count (score: 20+)</li>
     *   <li>Varargs methods with compatible parameter count (score: 5)</li>
     * </ol>
     *
     * <p>Additional bonus: Methods with non-array first parameter get +10 score.</p>
     *
     * @param className The simple or fully qualified class name
     * @param methodName The method name to find
     * @param argCount The number of arguments in the call
     * @return MethodInfo with JVM descriptor if found, empty otherwise
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
     * Get method descriptor for an instance method.
     *
     * <p>Resolves the class and finds a matching instance method based on:</p>
     * <ul>
     *   <li>Method name</li>
     *   <li>Exact argument count match</li>
     *   <li>Non-static modifier</li>
     * </ul>
     *
     * <p>Searches declared methods first, then public methods from superclasses.</p>
     *
     * @param className The simple or fully qualified class name
     * @param methodName The method name to find
     * @param argCount The number of arguments in the call
     * @return MethodInfo with JVM descriptor if found, empty otherwise
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
     * Try to load a class without throwing exception.
     *
     * @param fullName The fully qualified class name
     * @return true if the class can be loaded, false otherwise
     */
    private boolean tryLoadClass(String fullName) {
        return getClass(fullName).isPresent();
    }
    
    /**
     * Convert a Java Method to JVM method descriptor.
     *
     * <p>Generates a JVM method descriptor in the format: {@code (param1param2...)returnType}</p>
     *
     * <p>Example: {@code println(String)} becomes {@code (Ljava/lang/String;)V}</p>
     *
     * @param method The Java reflection Method object
     * @return The JVM method descriptor string
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
     * Convert a Java Class to JVM type descriptor.
     *
     * <p>Primitive types:</p>
     * <ul>
     *   <li>void → V</li>
     *   <li>boolean → Z</li>
     *   <li>byte → B</li>
     *   <li>char → C</li>
     *   <li>short → S</li>
     *   <li>int → I</li>
     *   <li>long → J</li>
     *   <li>float → F</li>
     *   <li>double → D</li>
     * </ul>
     *
     * <p>Arrays: {@code [} + component type descriptor</p>
     * <p>Objects: {@code L} + internal name + {@code ;}</p>
     *
     * @param clazz The Java Class object
     * @return The JVM type descriptor string
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
     * Information about a resolved method.
     *
     * <p>Contains all necessary information for generating JVM bytecode
     * to invoke a method:</p>
     * <ul>
     *   <li>className: Internal JVM class name (e.g., "java/lang/System")</li>
     *   <li>methodName: Method name (e.g., "println")</li>
     *   <li>descriptor: JVM method descriptor (e.g., "(Ljava/lang/String;)V")</li>
     *   <li>isStatic: Whether the method is static</li>
     * </ul>
     */
    public static class MethodInfo {
        /** Internal JVM class name (e.g., "java/lang/System") */
        public final String className;

        /** Method name (e.g., "println") */
        public final String methodName;

        /** JVM method descriptor (e.g., "(Ljava/lang/String;)V") */
        public final String descriptor;

        /** Whether the method is static */
        public final boolean isStatic;

        /**
         * Creates a new MethodInfo.
         *
         * @param className Internal JVM class name
         * @param methodName Method name
         * @param descriptor JVM method descriptor
         * @param isStatic Whether the method is static
         */
        public MethodInfo(String className, String methodName, String descriptor, boolean isStatic) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.isStatic = isStatic;
        }
    }
}
