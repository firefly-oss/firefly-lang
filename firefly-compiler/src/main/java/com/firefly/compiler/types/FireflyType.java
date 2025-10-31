package com.firefly.compiler.types;

import org.objectweb.asm.Opcodes;
import static org.objectweb.asm.Opcodes.*;

import java.util.*;

/**
 * Sistema central de tipos de Firefly con mapeo completo a JVM.
 * Define todos los tipos del lenguaje y su representación en bytecode.
 */
public class FireflyType {
    private final String fireflyName;       // Nombre en Firefly: "Int", "Float", "String", "UUID"
    private final String jvmDescriptor;     // Descriptor JVM: "I", "D", "Ljava/lang/String;", "Ljava/util/UUID;"
    private final String jvmInternalName;   // Nombre interno JVM: "java/lang/String", "java/util/UUID"
    private final TypeCategory category;    // PRIMITIVE o REFERENCE
    private final int loadOpcode;           // ILOAD, DLOAD, ALOAD, etc.
    private final int storeOpcode;          // ISTORE, DSTORE, ASTORE, etc.
    private final int returnOpcode;         // IRETURN, DRETURN, ARETURN, etc.
    private final int arrayLoadOpcode;      // IALOAD, DALOAD, AALOAD, etc.
    private final int arrayStoreOpcode;     // IASTORE, DASTORE, AASTORE, etc.
    private final String boxedType;         // Tipo boxeado: "java/lang/Integer", "java/lang/Double", null si no es primitivo
    private final String boxedDescriptor;   // Descriptor del boxed: "Ljava/lang/Integer;", "Ljava/lang/Double;"
    private final boolean is64Bit;          // true para long y double (ocupan 2 slots)
    
    public enum TypeCategory {
        PRIMITIVE,  // int, long, float, double, boolean, char, byte, short
        REFERENCE   // String, UUID, Array, Object, etc.
    }
    
    // Constructor principal
    private FireflyType(String fireflyName, String jvmDescriptor, String jvmInternalName, 
                       TypeCategory category, int loadOpcode, int storeOpcode, int returnOpcode,
                       int arrayLoadOpcode, int arrayStoreOpcode,
                       String boxedType, String boxedDescriptor, boolean is64Bit) {
        this.fireflyName = fireflyName;
        this.jvmDescriptor = jvmDescriptor;
        this.jvmInternalName = jvmInternalName;
        this.category = category;
        this.loadOpcode = loadOpcode;
        this.storeOpcode = storeOpcode;
        this.returnOpcode = returnOpcode;
        this.arrayLoadOpcode = arrayLoadOpcode;
        this.arrayStoreOpcode = arrayStoreOpcode;
        this.boxedType = boxedType;
        this.boxedDescriptor = boxedDescriptor;
        this.is64Bit = is64Bit;
    }
    
    // ============ TIPOS PRIMITIVOS ============
    
    public static final FireflyType INT = new FireflyType(
        "Int", "I", null,
        TypeCategory.PRIMITIVE,
        ILOAD, ISTORE, IRETURN, IALOAD, IASTORE,
        "java/lang/Integer", "Ljava/lang/Integer;",
        false
    );
    
    public static final FireflyType LONG = new FireflyType(
        "Long", "J", null,
        TypeCategory.PRIMITIVE,
        LLOAD, LSTORE, LRETURN, LALOAD, LASTORE,
        "java/lang/Long", "Ljava/lang/Long;",
        true  // 64-bit
    );
    
    public static final FireflyType FLOAT = new FireflyType(
        "Float", "D", null,  // Firefly Float mapea a JVM double
        TypeCategory.PRIMITIVE,
        DLOAD, DSTORE, DRETURN, DALOAD, DASTORE,
        "java/lang/Double", "Ljava/lang/Double;",
        true  // 64-bit
    );
    
    public static final FireflyType DOUBLE = new FireflyType(
        "Double", "D", null,
        TypeCategory.PRIMITIVE,
        DLOAD, DSTORE, DRETURN, DALOAD, DASTORE,
        "java/lang/Double", "Ljava/lang/Double;",
        true  // 64-bit
    );
    
    public static final FireflyType BOOLEAN = new FireflyType(
        "Bool", "Z", null,
        TypeCategory.PRIMITIVE,
        ILOAD, ISTORE, IRETURN, BALOAD, BASTORE,
        "java/lang/Boolean", "Ljava/lang/Boolean;",
        false
    );
    
    public static final FireflyType CHAR = new FireflyType(
        "Char", "C", null,
        TypeCategory.PRIMITIVE,
        ILOAD, ISTORE, IRETURN, CALOAD, CASTORE,
        "java/lang/Character", "Ljava/lang/Character;",
        false
    );
    
    public static final FireflyType BYTE = new FireflyType(
        "Byte", "B", null,
        TypeCategory.PRIMITIVE,
        ILOAD, ISTORE, IRETURN, BALOAD, BASTORE,
        "java/lang/Byte", "Ljava/lang/Byte;",
        false
    );
    
    public static final FireflyType SHORT = new FireflyType(
        "Short", "S", null,
        TypeCategory.PRIMITIVE,
        ILOAD, ISTORE, IRETURN, SALOAD, SASTORE,
        "java/lang/Short", "Ljava/lang/Short;",
        false
    );
    
    // ============ TIPOS DE REFERENCIA COMUNES ============
    
    public static final FireflyType STRING = new FireflyType(
        "String", "Ljava/lang/String;", "java/lang/String",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,  // String ya es objeto
        false
    );
    
    public static final FireflyType UUID = new FireflyType(
        "UUID", "Ljava/util/UUID;", "java/util/UUID",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType BIGDECIMAL = new FireflyType(
        "BigDecimal", "Ljava/math/BigDecimal;", "java/math/BigDecimal",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType BIGINTEGER = new FireflyType(
        "BigInteger", "Ljava/math/BigInteger;", "java/math/BigInteger",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType INSTANT = new FireflyType(
        "Instant", "Ljava/time/Instant;", "java/time/Instant",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType LOCAL_DATE = new FireflyType(
        "LocalDate", "Ljava/time/LocalDate;", "java/time/LocalDate",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType LOCAL_TIME = new FireflyType(
        "LocalTime", "Ljava/time/LocalTime;", "java/time/LocalTime",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType LOCAL_DATE_TIME = new FireflyType(
        "LocalDateTime", "Ljava/time/LocalDateTime;", "java/time/LocalDateTime",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType ZONED_DATE_TIME = new FireflyType(
        "ZonedDateTime", "Ljava/time/ZonedDateTime;", "java/time/ZonedDateTime",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType DURATION = new FireflyType(
        "Duration", "Ljava/time/Duration;", "java/time/Duration",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType PERIOD = new FireflyType(
        "Period", "Ljava/time/Period;", "java/time/Period",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType OBJECT = new FireflyType(
        "Object", "Ljava/lang/Object;", "java/lang/Object",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // ============ TIPOS DE COLECCIONES ============
    
    public static final FireflyType LIST = new FireflyType(
        "List", "Ljava/util/List;", "java/util/List",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType ARRAY_LIST = new FireflyType(
        "ArrayList", "Ljava/util/ArrayList;", "java/util/ArrayList",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType SET = new FireflyType(
        "Set", "Ljava/util/Set;", "java/util/Set",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType HASH_SET = new FireflyType(
        "HashSet", "Ljava/util/HashSet;", "java/util/HashSet",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType MAP = new FireflyType(
        "Map", "Ljava/util/Map;", "java/util/Map",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType HASH_MAP = new FireflyType(
        "HashMap", "Ljava/util/HashMap;", "java/util/HashMap",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType QUEUE = new FireflyType(
        "Queue", "Ljava/util/Queue;", "java/util/Queue",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType DEQUE = new FireflyType(
        "Deque", "Ljava/util/Deque;", "java/util/Deque",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType STREAM = new FireflyType(
        "Stream", "Ljava/util/stream/Stream;", "java/util/stream/Stream",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // ============ TIPOS OPCIONALES ============
    
    public static final FireflyType OPTIONAL = new FireflyType(
        "Optional", "Ljava/util/Optional;", "java/util/Optional",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // ============ TIPOS DE I/O Y FILES ============
    
    public static final FireflyType PATH = new FireflyType(
        "Path", "Ljava/nio/file/Path;", "java/nio/file/Path",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType FILE = new FireflyType(
        "File", "Ljava/io/File;", "java/io/File",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType URI = new FireflyType(
        "URI", "Ljava/net/URI;", "java/net/URI",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType URL = new FireflyType(
        "URL", "Ljava/net/URL;", "java/net/URL",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType INPUT_STREAM = new FireflyType(
        "InputStream", "Ljava/io/InputStream;", "java/io/InputStream",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType OUTPUT_STREAM = new FireflyType(
        "OutputStream", "Ljava/io/OutputStream;", "java/io/OutputStream",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType READER = new FireflyType(
        "Reader", "Ljava/io/Reader;", "java/io/Reader",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType WRITER = new FireflyType(
        "Writer", "Ljava/io/Writer;", "java/io/Writer",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // ============ TIPOS FUNCIONALES ============
    
    public static final FireflyType FUNCTION = new FireflyType(
        "Function", "Ljava/util/function/Function;", "java/util/function/Function",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType PREDICATE = new FireflyType(
        "Predicate", "Ljava/util/function/Predicate;", "java/util/function/Predicate",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType CONSUMER = new FireflyType(
        "Consumer", "Ljava/util/function/Consumer;", "java/util/function/Consumer",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType SUPPLIER = new FireflyType(
        "Supplier", "Ljava/util/function/Supplier;", "java/util/function/Supplier",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType BI_FUNCTION = new FireflyType(
        "BiFunction", "Ljava/util/function/BiFunction;", "java/util/function/BiFunction",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // ============ TIPOS DE CONCURRENCIA ============
    
    public static final FireflyType FUTURE = new FireflyType(
        "Future", "Ljava/util/concurrent/Future;", "java/util/concurrent/Future",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType COMPLETABLE_FUTURE = new FireflyType(
        "CompletableFuture", "Ljava/util/concurrent/CompletableFuture;", "java/util/concurrent/CompletableFuture",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType EXECUTOR = new FireflyType(
        "Executor", "Ljava/util/concurrent/Executor;", "java/util/concurrent/Executor",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // ============ TIPOS DE EXCEPCIONES ============
    
    public static final FireflyType EXCEPTION = new FireflyType(
        "Exception", "Ljava/lang/Exception;", "java/lang/Exception",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType RUNTIME_EXCEPTION = new FireflyType(
        "RuntimeException", "Ljava/lang/RuntimeException;", "java/lang/RuntimeException",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType THROWABLE = new FireflyType(
        "Throwable", "Ljava/lang/Throwable;", "java/lang/Throwable",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // ============ TIPOS DE PATTERN MATCHING Y RESULT ============
    
    public static final FireflyType PATTERN = new FireflyType(
        "Pattern", "Ljava/util/regex/Pattern;", "java/util/regex/Pattern",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType MATCHER = new FireflyType(
        "Matcher", "Ljava/util/regex/Matcher;", "java/util/regex/Matcher",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    // Arrays
    public static final FireflyType STRING_ARRAY = new FireflyType(
        "String[]", "[Ljava/lang/String;", "[Ljava/lang/String;",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, AALOAD, AASTORE,
        null, null,
        false
    );
    
    public static final FireflyType INT_ARRAY = new FireflyType(
        "Int[]", "[I", "[I",
        TypeCategory.REFERENCE,
        ALOAD, ASTORE, ARETURN, IALOAD, IASTORE,
        null, null,
        false
    );
    
    // ============ REGISTRO DE TIPOS ============
    
    private static final Map<String, FireflyType> BY_FIREFLY_NAME = new HashMap<>();
    private static final Map<String, FireflyType> BY_DESCRIPTOR = new HashMap<>();
    private static final Map<String, FireflyType> BY_INTERNAL_NAME = new HashMap<>();
    
    static {
        // Registrar todos los tipos
        // Primitivos
        register(INT, LONG, FLOAT, DOUBLE, BOOLEAN, CHAR, BYTE, SHORT);
        // Tipos numéricos y básicos
        register(STRING, UUID, BIGDECIMAL, BIGINTEGER);
        // Tipos de fecha/tiempo
        register(INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, ZONED_DATE_TIME, DURATION, PERIOD);
        // Colecciones
        register(LIST, ARRAY_LIST, SET, HASH_SET, MAP, HASH_MAP, QUEUE, DEQUE, STREAM);
        // Opcionales
        register(OPTIONAL);
        // I/O y Files
        register(PATH, FILE, URI, URL, INPUT_STREAM, OUTPUT_STREAM, READER, WRITER);
        // Funcionales
        register(FUNCTION, PREDICATE, CONSUMER, SUPPLIER, BI_FUNCTION);
        // Concurrencia
        register(FUTURE, COMPLETABLE_FUTURE, EXECUTOR);
        // Excepciones
        register(EXCEPTION, RUNTIME_EXCEPTION, THROWABLE);
        // Pattern matching
        register(PATTERN, MATCHER);
        // Objeto y arrays
        register(OBJECT, STRING_ARRAY, INT_ARRAY);
    }
    
    private static void register(FireflyType... types) {
        for (FireflyType type : types) {
            BY_FIREFLY_NAME.put(type.fireflyName, type);
            BY_DESCRIPTOR.put(type.jvmDescriptor, type);
            if (type.jvmInternalName != null) {
                BY_INTERNAL_NAME.put(type.jvmInternalName, type);
            }
        }
    }
    
    // ============ BÚSQUEDA DE TIPOS ============
    
    public static FireflyType fromFireflyName(String name) {
        return BY_FIREFLY_NAME.get(name);
    }
    
    public static FireflyType fromDescriptor(String descriptor) {
        return BY_DESCRIPTOR.get(descriptor);
    }
    
    public static FireflyType fromInternalName(String internalName) {
        return BY_INTERNAL_NAME.get(internalName);
    }
    
    public static Collection<FireflyType> allTypes() {
        return Collections.unmodifiableCollection(BY_FIREFLY_NAME.values());
    }
    
    // ============ GETTERS ============
    
    public String getFireflyName() { return fireflyName; }
    public String getJvmDescriptor() { return jvmDescriptor; }
    public String getJvmInternalName() { return jvmInternalName; }
    public TypeCategory getCategory() { return category; }
    public int getLoadOpcode() { return loadOpcode; }
    public int getStoreOpcode() { return storeOpcode; }
    public int getReturnOpcode() { return returnOpcode; }
    public int getArrayLoadOpcode() { return arrayLoadOpcode; }
    public int getArrayStoreOpcode() { return arrayStoreOpcode; }
    public String getBoxedType() { return boxedType; }
    public String getBoxedDescriptor() { return boxedDescriptor; }
    public boolean is64Bit() { return is64Bit; }
    public boolean isPrimitive() { return category == TypeCategory.PRIMITIVE; }
    public boolean isReference() { return category == TypeCategory.REFERENCE; }
    
    // ============ UTILIDADES DE CONVERSIÓN ============
    
    /**
     * Genera bytecode para convertir de este tipo a otro
     */
    public void generateConversionTo(FireflyType target, org.objectweb.asm.MethodVisitor mv) {
        if (this == target) return;  // No conversion needed
        
        // Conversiones numéricas primitivas
        if (this.isPrimitive() && target.isPrimitive()) {
            if (this == INT) {
                if (target == LONG) mv.visitInsn(I2L);
                else if (target == FLOAT || target == DOUBLE) mv.visitInsn(I2D);
            } else if (this == LONG) {
                if (target == INT) mv.visitInsn(L2I);
                else if (target == FLOAT || target == DOUBLE) mv.visitInsn(L2D);
            } else if (this == FLOAT || this == DOUBLE) {
                if (target == INT) mv.visitInsn(D2I);
                else if (target == LONG) mv.visitInsn(D2L);
            }
        }
    }
    
    /**
     * Genera bytecode para boxing (primitivo -> objeto)
     */
    public void generateBoxing(org.objectweb.asm.MethodVisitor mv) {
        if (!isPrimitive() || boxedType == null) return;
        
        mv.visitMethodInsn(
            INVOKESTATIC,
            boxedType,
            "valueOf",
            "(" + jvmDescriptor + ")" + boxedDescriptor,
            false
        );
    }
    
    /**
     * Genera bytecode para unboxing (objeto -> primitivo)
     */
    public void generateUnboxing(org.objectweb.asm.MethodVisitor mv) {
        if (!isPrimitive() || boxedType == null) return;
        
        // Cast al tipo boxeado correcto
        mv.visitTypeInsn(CHECKCAST, boxedType);
        
        // Llamar al método de unboxing apropiado
        String unboxMethod = getUnboxMethodName();
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            boxedType,
            unboxMethod,
            "()" + jvmDescriptor,
            false
        );
    }
    
    private String getUnboxMethodName() {
        if (this == INT) return "intValue";
        if (this == LONG) return "longValue";
        if (this == FLOAT || this == DOUBLE) return "doubleValue";
        if (this == BOOLEAN) return "booleanValue";
        if (this == CHAR) return "charValue";
        if (this == BYTE) return "byteValue";
        if (this == SHORT) return "shortValue";
        throw new UnsupportedOperationException("No unbox method for " + fireflyName);
    }
    
    @Override
    public String toString() {
        return "FireflyType{" + fireflyName + " -> " + jvmDescriptor + "}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FireflyType)) return false;
        FireflyType that = (FireflyType) o;
        return Objects.equals(fireflyName, that.fireflyName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fireflyName);
    }
}
