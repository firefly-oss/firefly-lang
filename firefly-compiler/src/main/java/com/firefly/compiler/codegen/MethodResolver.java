package com.firefly.compiler.codegen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Professional method resolution system implementing Java Language Specification (JLS) §15.12.
 * Handles method overloading, varargs, type conversion, boxing/unboxing with proper ranking.
 */
public class MethodResolver {
    
    private final TypeResolver typeResolver;
    
    // Primitive type relationships for widening conversions
    private static final Map<Class<?>, List<Class<?>>> WIDENING_CONVERSIONS = new HashMap<>();
    
    static {
        WIDENING_CONVERSIONS.put(byte.class, Arrays.asList(short.class, int.class, long.class, float.class, double.class));
        WIDENING_CONVERSIONS.put(short.class, Arrays.asList(int.class, long.class, float.class, double.class));
        WIDENING_CONVERSIONS.put(char.class, Arrays.asList(int.class, long.class, float.class, double.class));
        WIDENING_CONVERSIONS.put(int.class, Arrays.asList(long.class, float.class, double.class));
        WIDENING_CONVERSIONS.put(long.class, Arrays.asList(float.class, double.class));
        WIDENING_CONVERSIONS.put(float.class, Arrays.asList(double.class));
    }
    
    public MethodResolver(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }
    
    /**
     * Resolve the best matching method for a static call.
     * 
     * @param className The class containing the method
     * @param methodName The method name
     * @param argTypes The actual argument types being passed
     * @return The best matching method, or empty if no applicable method found
     */
    public Optional<MethodCandidate> resolveStaticMethod(String className, String methodName, List<Class<?>> argTypes) {
        return resolveMethod(className, methodName, argTypes, true);
    }
    
    /**
     * Resolve the best matching method for an instance call.
     */
    public Optional<MethodCandidate> resolveInstanceMethod(String className, String methodName, List<Class<?>> argTypes) {
        return resolveMethod(className, methodName, argTypes, false);
    }
    
    /**
     * Core method resolution logic implementing JLS overload resolution.
     */
    private Optional<MethodCandidate> resolveMethod(String className, String methodName, List<Class<?>> argTypes, boolean isStatic) {
        // Resolve class name
        Optional<String> fullName = typeResolver.resolveClassName(className);
        if (!fullName.isPresent()) {
            return Optional.empty();
        }
        
        Optional<Class<?>> clazz = typeResolver.getClass(fullName.get());
        if (!clazz.isPresent()) {
            return Optional.empty();
        }
        
        // Phase 1: Collect all applicable methods
        List<MethodCandidate> applicableMethods = new ArrayList<>();
        
        for (Method method : clazz.get().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            
            if (Modifier.isStatic(method.getModifiers()) != isStatic) {
                continue;
            }
            
            ApplicabilityResult applicability = isApplicable(method, argTypes);
            if (applicability.applicable) {
                applicableMethods.add(new MethodCandidate(method, applicability));
            }
        }
        
        if (applicableMethods.isEmpty()) {
            return Optional.empty();
        }
        
        // Phase 2: Find most specific method
        // JLS §15.12.2.5: The most specific method is chosen
        MethodCandidate best = applicableMethods.get(0);
        
        for (int i = 1; i < applicableMethods.size(); i++) {
            MethodCandidate candidate = applicableMethods.get(i);
            
            // Compare specificity
            int comparison = compareSpecificity(best, candidate, argTypes);
            
            if (comparison > 0) {
                // candidate is more specific
                best = candidate;
            } else if (comparison == 0) {
                // Ambiguous - prefer non-varargs over varargs
                if (!candidate.method.isVarArgs() && best.method.isVarArgs()) {
                    best = candidate;
                }
            }
        }
        
        return Optional.of(best);
    }
    
    /**
     * Check if a method is applicable for the given argument types.
     * JLS §15.12.2.1-15.12.2.4
     */
    private ApplicabilityResult isApplicable(Method method, List<Class<?>> argTypes) {
        Class<?>[] paramTypes = method.getParameterTypes();
        int paramCount = paramTypes.length;
        int argCount = argTypes.size();
        
        ApplicabilityResult result = new ApplicabilityResult();
        
        // Handle varargs
        if (method.isVarArgs()) {
            int fixedParams = paramCount - 1;
            
            if (argCount < fixedParams) {
                return result; // Not applicable
            }
            
            // Check fixed parameters
            for (int i = 0; i < fixedParams; i++) {
                ConversionKind conversion = getConversionKind(argTypes.get(i), paramTypes[i]);
                if (conversion == ConversionKind.NONE) {
                    return result; // Not applicable
                }
                result.conversions.add(conversion);
            }
            
            // Check varargs parameters
            Class<?> varargType = paramTypes[fixedParams].getComponentType();
            for (int i = fixedParams; i < argCount; i++) {
                ConversionKind conversion = getConversionKind(argTypes.get(i), varargType);
                if (conversion == ConversionKind.NONE) {
                    return result; // Not applicable
                }
                result.conversions.add(conversion);
            }
            
            result.applicable = true;
            result.usesVarargs = true;
            return result;
        }
        
        // Non-varargs: exact parameter count required
        if (argCount != paramCount) {
            return result;
        }
        
        // Check each parameter
        for (int i = 0; i < paramCount; i++) {
            ConversionKind conversion = getConversionKind(argTypes.get(i), paramTypes[i]);
            if (conversion == ConversionKind.NONE) {
                return result; // Not applicable
            }
            result.conversions.add(conversion);
        }
        
        result.applicable = true;
        return result;
    }
    
    /**
     * Determine what kind of conversion is needed from source to target type.
     */
    private ConversionKind getConversionKind(Class<?> from, Class<?> to) {
        if (from == null || to == null) {
            // Unknown type - assume compatible (for now)
            return ConversionKind.IDENTITY;
        }
        
        // Identity conversion
        if (from.equals(to)) {
            return ConversionKind.IDENTITY;
        }
        
        // Reference widening (subtype)
        if (to.isAssignableFrom(from)) {
            return ConversionKind.WIDENING_REFERENCE;
        }
        
        // Primitive widening
        if (from.isPrimitive() && to.isPrimitive()) {
            List<Class<?>> conversions = WIDENING_CONVERSIONS.get(from);
            if (conversions != null && conversions.contains(to)) {
                return ConversionKind.WIDENING_PRIMITIVE;
            }
        }
        
        // Boxing/Unboxing
        Class<?> fromBoxed = box(from);
        Class<?> toBoxed = box(to);
        
        if (fromBoxed.equals(toBoxed)) {
            return ConversionKind.BOXING_UNBOXING;
        }
        
        // Boxing + widening
        if (toBoxed.isAssignableFrom(fromBoxed)) {
            return ConversionKind.BOXING_WIDENING;
        }
        
        // String conversion (everything can be converted to String)
        if (to.equals(String.class)) {
            return ConversionKind.STRING_CONVERSION;
        }
        
        return ConversionKind.NONE;
    }
    
    /**
     * Compare specificity of two methods for the given argument types.
     * Returns: < 0 if m1 is more specific, > 0 if m2 is more specific, 0 if equal
     */
    private int compareSpecificity(MethodCandidate m1, MethodCandidate m2, List<Class<?>> argTypes) {
        // Non-varargs is more specific than varargs
        if (!m1.applicability.usesVarargs && m2.applicability.usesVarargs) {
            return -1;
        }
        if (m1.applicability.usesVarargs && !m2.applicability.usesVarargs) {
            return 1;
        }
        
        // Compare conversion kinds
        int score1 = calculateConversionScore(m1.applicability.conversions);
        int score2 = calculateConversionScore(m2.applicability.conversions);
        
        if (score1 != score2) {
            return Integer.compare(score2, score1); // Higher score is more specific
        }
        
        // Compare parameter types pairwise
        Class<?>[] params1 = m1.method.getParameterTypes();
        Class<?>[] params2 = m2.method.getParameterTypes();
        
        int m1MoreSpecific = 0;
        int m2MoreSpecific = 0;
        
        for (int i = 0; i < Math.min(params1.length, params2.length); i++) {
            Class<?> p1 = params1[i];
            Class<?> p2 = params2[i];
            
            if (p1.equals(p2)) continue;
            
            if (p2.isAssignableFrom(p1)) {
                m1MoreSpecific++;
            }
            if (p1.isAssignableFrom(p2)) {
                m2MoreSpecific++;
            }
        }
        
        if (m1MoreSpecific > m2MoreSpecific) return -1;
        if (m2MoreSpecific > m1MoreSpecific) return 1;
        
        return 0;
    }
    
    /**
     * Calculate a score for conversion kinds (higher = better/more specific).
     */
    private int calculateConversionScore(List<ConversionKind> conversions) {
        int score = 0;
        for (ConversionKind kind : conversions) {
            score += kind.specificity;
        }
        return score;
    }
    
    /**
     * Box a primitive type to its wrapper class.
     */
    private Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        
        return type;
    }
    
    /**
     * Types of type conversions, ordered by specificity.
     */
    private enum ConversionKind {
        IDENTITY(100),              // Exact match
        WIDENING_PRIMITIVE(90),     // byte -> int
        WIDENING_REFERENCE(85),     // String -> Object
        BOXING_UNBOXING(80),        // int <-> Integer
        BOXING_WIDENING(70),        // int -> Number
        STRING_CONVERSION(50),      // anything -> String
        NONE(0);                    // Not applicable
        
        final int specificity;
        
        ConversionKind(int specificity) {
            this.specificity = specificity;
        }
    }
    
    /**
     * Result of applicability check.
     */
    private static class ApplicabilityResult {
        boolean applicable = false;
        boolean usesVarargs = false;
        List<ConversionKind> conversions = new ArrayList<>();
    }
    
    /**
     * A method candidate with its applicability info.
     */
    public static class MethodCandidate {
        public final Method method;
        public final ApplicabilityResult applicability;
        
        public MethodCandidate(Method method, ApplicabilityResult applicability) {
            this.method = method;
            this.applicability = applicability;
        }
        
        /**
         * Get the JVM method descriptor for this method.
         */
        public String getDescriptor() {
            return getMethodDescriptor(method);
        }
        
        /**
         * Get the internal class name (e.g., "java/lang/String").
         */
        public String getInternalClassName() {
            return method.getDeclaringClass().getName().replace('.', '/');
        }
        
        public boolean isStatic() {
            return Modifier.isStatic(method.getModifiers());
        }
        
        public boolean isVarArgs() {
            return method.isVarArgs();
        }
        
        private static String getMethodDescriptor(Method method) {
            StringBuilder desc = new StringBuilder("(");
            
            for (Class<?> paramType : method.getParameterTypes()) {
                desc.append(getTypeDescriptor(paramType));
            }
            
            desc.append(")");
            desc.append(getTypeDescriptor(method.getReturnType()));
            
            return desc.toString();
        }
        
        private static String getTypeDescriptor(Class<?> clazz) {
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
    }
}
