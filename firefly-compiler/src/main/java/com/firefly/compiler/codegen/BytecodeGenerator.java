package com.firefly.compiler.codegen;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.ast.pattern.*;
import com.firefly.compiler.ast.UseDeclaration;
import com.firefly.compiler.types.FireflyType;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Stack;

/**
 * Generates JVM bytecode from Firefly AST.
 * Currently supports basic Hello World functionality.
 */
public class BytecodeGenerator implements AstVisitor<Void> {
    private ClassWriter classWriter;  // Non-final to allow reassignment for nested classes and lambdas
    private MethodVisitor methodVisitor;
    private String className;
    private String moduleBasePath = "";  // com/example
    private final Map<String, Integer> localVariables = new HashMap<>();
    private final Map<String, VarType> localVariableTypes = new HashMap<>();
    private final Map<String, Class<?>> localVariableClasses = new HashMap<>(); // Track actual Java class for OBJECT-typed vars
    private final Map<String, String> functionSignatures = new HashMap<>(); // name -> descriptor
    private final Map<String, String> currentFunctionParams = new HashMap<>(); // param name -> type name
    private final Stack<Map<String, Integer>> scopeStack = new Stack<>();
    private int localVarIndex = 0;
    // Track declared types for local variables (dotted class names), to support invoking methods on Firefly classes
    private final Map<String, String> localVariableDeclaredTypes = new HashMap<>();
    private int labelCounter = 0;
    private boolean lastCallWasVoid = false;
    private VarType lastExpressionType = VarType.INT;
    private Class<?> lastExpressionClass = null;  // Track actual Java class for type inference
    private String currentFunctionName = null;
    private Label breakLabel = null;
    private Label continueLabel = null;
    private boolean inStatementContext = false;
    private boolean codeIsReachable = true;  // Track if current code path is reachable
    
    // Store generated class files: className -> bytecode
    private final Map<String, byte[]> generatedClasses = new HashMap<>();
    
    // Store struct metadata: structName -> StructMetadata
    private final Map<String, StructMetadata> structRegistry = new HashMap<>();
    
    // Store type aliases: aliasName -> targetType
    private final Map<String, com.firefly.compiler.ast.type.Type> typeAliases = new HashMap<>();
    
    // Professional type resolution system
    private final TypeResolver typeResolver;
    private final MethodResolver methodResolver;
    
    // Class hierarchy tracking for nested classes
    private final Stack<String> classNameStack = new Stack<>();  // Track enclosing class names
    private String currentEnclosingClass = null;  // Current enclosing class (null for top-level)
    
    // Track field types for current class: fieldName -> descriptor
    private final Map<String, String> currentClassFieldTypes = new HashMap<>();
    
    private enum VarType {
        INT, LONG, FLOAT, DOUBLE, BOOLEAN, STRING, OBJECT, STRING_ARRAY
    }
    
    // Flags to ensure helper methods are generated once per class
    private boolean optionMapHelperGenerated = false;
    private boolean optionUnwrapOrHelperGenerated = false;
    private boolean optionIsSomeHelperGenerated = false;
    
    // ============ FIREFLY TYPE SYSTEM INTEGRATION ============
    
    /**
     * Convert legacy VarType to modern FireflyType.
     * This allows gradual migration to the centralized type system.
     */
    private FireflyType varTypeToFireflyType(VarType varType) {
        switch (varType) {
            case INT: return FireflyType.INT;
            case LONG: return FireflyType.LONG;
            case FLOAT:
            case DOUBLE: return FireflyType.FLOAT;  // Both map to double
            case BOOLEAN: return FireflyType.BOOLEAN;
            case STRING: return FireflyType.STRING;
            case STRING_ARRAY: return FireflyType.STRING_ARRAY;
            case OBJECT: return FireflyType.OBJECT;
            default: return FireflyType.OBJECT;
        }
    }
    
    /**
     * Convert FireflyType to legacy VarType.
     * Used during transition period.
     */
    private VarType fireflyTypeToVarType(FireflyType fireflyType) {
        if (fireflyType == FireflyType.INT) return VarType.INT;
        if (fireflyType == FireflyType.LONG) return VarType.LONG;
        if (fireflyType == FireflyType.FLOAT || fireflyType == FireflyType.DOUBLE) return VarType.FLOAT;
        if (fireflyType == FireflyType.BOOLEAN) return VarType.BOOLEAN;
        if (fireflyType == FireflyType.STRING) return VarType.STRING;
        if (fireflyType == FireflyType.STRING_ARRAY) return VarType.STRING_ARRAY;
        return VarType.OBJECT;
    }
    
    /**
     * Get FireflyType from Firefly type name.
     * Tries FireflyType registry first, then falls back to manual mapping.
     */
    private FireflyType getFireflyTypeFromName(String typeName) {
        // Try FireflyType registry first
        FireflyType type = FireflyType.fromFireflyName(typeName);
        if (type != null) {
            return type;
        }
        
        // Manual fallback for primitives
        switch (typeName) {
            case "Int": return FireflyType.INT;
            case "Long": return FireflyType.LONG;
            case "Float":
            case "Double": return FireflyType.FLOAT;
            case "Bool":
            case "Boolean": return FireflyType.BOOLEAN;
            case "String": return FireflyType.STRING;
            default: return FireflyType.OBJECT;
        }
    }
    
    /**
     * Get store opcode for a variable type using FireflyType system.
     */
    private int getStoreOpcodeForType(VarType varType) {
        return varTypeToFireflyType(varType).getStoreOpcode();
    }
    
    /**
     * Get load opcode for a variable type using FireflyType system.
     */
    private int getLoadOpcodeForType(VarType varType) {
        return varTypeToFireflyType(varType).getLoadOpcode();
    }
    
    /**
     * Get return opcode for a variable type using FireflyType system.
     */
    private int getReturnOpcodeForType(VarType varType) {
        return varTypeToFireflyType(varType).getReturnOpcode();
    }
    
    public BytecodeGenerator() {
        this(new TypeResolver());
    }
    
    public BytecodeGenerator(TypeResolver typeResolver) {
        // Use COMPUTE_FRAMES for automatic frame generation (required for Java 7+)
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        this.typeResolver = typeResolver;
        this.methodResolver = new MethodResolver(typeResolver);
    }
    
    public Map<String, byte[]> generate(CompilationUnit unit) {
        generatedClasses.clear();
        
        // Pre-register type metadata (structs, sparks, data, traits, interfaces) so
        // code generation doesn't depend on declaration order.
        preRegisterTypes(unit);
        
        // Now generate code in a second pass
        // Initialize module base path (used by visitClassDecl et al.)
        String moduleBase = unit.getModuleName() != null ? unit.getModuleName().replace("::", "/").replace('.', '/') : "";
        this.moduleBasePath = moduleBase;
        this.className = moduleBase;
        classNameStack.clear();
        currentEnclosingClass = null;
        
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        
        return generatedClasses;
    }

    /**
     * Pre-register type information to remove declaration-order dependencies.
     * This collects struct/spark field metadata and other type info used during
     * code generation before emitting any bytecode.
     */
    private void preRegisterTypes(CompilationUnit unit) {
        // Register top-level structs and sparks first with fully-qualified internal names
        String mb = unit.getModuleName() != null ? unit.getModuleName().replace('.', '/') : "";
        for (Declaration decl : unit.getDeclarations()) {
            if (decl instanceof StructDecl) {
                StructDecl s = (StructDecl) decl;
                java.util.List<StructMetadata.FieldMetadata> fields = new java.util.ArrayList<>();
                for (StructDecl.Field f : s.getFields()) {
                    fields.add(new StructMetadata.FieldMetadata(f.getName(), f.getType()));
                }
                String internal = (mb.isEmpty() ? s.getName() : mb + "/" + s.getName());
                structRegistry.put(s.getName(), new StructMetadata(s.getName(), internal, fields));
            } else if (decl instanceof SparkDecl) {
                SparkDecl sp = (SparkDecl) decl;
                java.util.List<StructMetadata.FieldMetadata> fields = new java.util.ArrayList<>();
                for (SparkDecl.SparkField f : sp.getFields()) {
                    fields.add(new StructMetadata.FieldMetadata(f.getName(), f.getType()));
                }
                String internal = (mb.isEmpty() ? sp.getName() : mb + "/" + sp.getName());
                structRegistry.put(sp.getName(), new StructMetadata(sp.getName(), internal, fields));
            } else if (decl instanceof DataDecl) {
                // Data types: register base and variants so they can be referenced by name and matched in patterns
                DataDecl d = (DataDecl) decl;
                String baseInternal = (mb.isEmpty() ? d.getName() : mb + "/" + d.getName());
                structRegistry.put(d.getName(), new StructMetadata(d.getName(), baseInternal, new java.util.ArrayList<>()));
                for (DataDecl.Variant v : d.getVariants()) {
                    java.util.List<StructMetadata.FieldMetadata> vfields = new java.util.ArrayList<>();
                    for (DataDecl.VariantField vf : v.getFields()) {
                        String fname = vf.getName().orElse("value" + v.getFields().indexOf(vf));
                        vfields.add(new StructMetadata.FieldMetadata(fname, vf.getType()));
                    }
                    String variantSimple = v.getName();
                    String variantInternal = baseInternal + "$" + variantSimple;
                    structRegistry.put(variantSimple, new StructMetadata(variantSimple, variantInternal, vfields));
                }
            }
        }
    }
    
    @Override 
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Module name is MANDATORY - store as package path (like Java)
        String moduleName = unit.getModuleName();
        this.moduleBasePath = moduleName.replace("::", "/").replace('.', '/');
        this.className = this.moduleBasePath;  // Base module path only
        // Inform TypeResolver of current module for local class resolution
        this.typeResolver.setCurrentModulePackage(moduleName.replace("::", "."));
        
        // Ensure TypeResolver has imports (may already be initialized by compiler)
        // This is idempotent - adding same import twice is safe
        for (UseDeclaration importDecl : unit.getImports()) {
            if (importDecl.isWildcard()) {
                typeResolver.addWildcardImport(importDecl.getModulePath());
            } else {
                for (String item : importDecl.getItems()) {
                    typeResolver.addImport(importDecl.getModulePath(), item);
                }
            }
        }
        
        // Visit declarations - each class/interface/enum generates its own bytecode file
        // (like Java: each class in its own .class file)
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        
        return null;
    }
    @Override 
    public Void visitUseDeclaration(UseDeclaration decl) {
        // Imports are processed in visitCompilationUnit
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Generate interface bytecode
        // className now contains just the module path (no class name)
        String interfaceName = className + "/" + decl.getName();
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        // Determine super interfaces
        String[] superInterfaces = decl.getSuperInterfaces().stream()
            .map(this::getClassNameFromType)
            .map(name -> name.replace('.', '/'))
            .toArray(String[]::new);
        
        // Create interface (ACC_INTERFACE + ACC_ABSTRACT)
        cw.visit(
            V1_8,
            ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT,
            interfaceName,
            null,
            "java/lang/Object",
            superInterfaces
        );
        
        // Add interface-level annotations
        for (Annotation ann : decl.getAnnotations()) {
            emitAnnotation(cw, ann);
        }
        
        // Add method signatures (abstract methods)
        for (TraitDecl.FunctionSignature method : decl.getMethods()) {
            generateInterfaceMethod(cw, method);
        }
        
        cw.visitEnd();
        
        // Store the generated interface bytecode
        String interfaceFileName = interfaceName.substring(interfaceName.lastIndexOf('/') + 1);
        generatedClasses.put(interfaceName, cw.toByteArray());
        
        return null;
    }
    
    @Override
    public Void visitActorDecl(ActorDecl decl) {
        // Generate a class that implements Actor<State, Message>
        // className now contains just the module path (no class name)
        String actorClassName = className + "/" + decl.getName();
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        // Actor implements Actor<State, Message>
        // For now, we use Object for both State and Message types
        String[] interfaces = new String[]{"com/firefly/runtime/actor/Actor"};
        
        cw.visit(
            V1_8,
            ACC_PUBLIC | ACC_SUPER,
            actorClassName,
            "Ljava/lang/Object;Lcom/firefly/runtime/actor/Actor<Ljava/lang/Object;Ljava/lang/Object;>;",
            "java/lang/Object",
            interfaces
        );
        
        // Add fields
        for (FieldDecl field : decl.getFields()) {
            int access = ACC_PRIVATE;
            if (!field.isMutable()) {
                access |= ACC_FINAL;
            }
            String descriptor = getTypeDescriptor(field.getType());
            FieldVisitor fv = cw.visitField(access, field.getName(), descriptor, null, null);
            fv.visitEnd();
        }
        
        // Generate default constructor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Generate init() method - returns initial state (this)
        generateActorInit(cw, decl, actorClassName);
        
        // Generate handle(Message, State) method - processes messages
        generateActorHandle(cw, decl, actorClassName);
        
        cw.visitEnd();
        generatedClasses.put(actorClassName, cw.toByteArray());
        
        return null;
    }
    
    private void generateInterfaceMethod(ClassWriter cw, TraitDecl.FunctionSignature method) {
        // Build descriptor
        StringBuilder descriptor = new StringBuilder("(");
        for (FunctionDecl.Parameter param : method.getParameters()) {
            descriptor.append(getTypeDescriptor(param.getType()));
        }
        descriptor.append(")");
        descriptor.append(getTypeDescriptor(method.getReturnType()));
        
        // Interface methods are public and abstract
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC | ACC_ABSTRACT,
            method.getName(),
            descriptor.toString(),
            null,
            null
        );
        
        mv.visitEnd();
    }
    
    @Override
    public Void visitClassDecl(ClassDecl decl) {
        // Generate separate class file for each class declaration
        // Handle nested classes with proper JVM naming: Outer$Inner
        
        String classFileName;
        if (decl.isNested() && !classNameStack.isEmpty()) {
            // Nested class: use Outer$Inner naming
            String enclosingClass = classNameStack.peek();
            classFileName = enclosingClass + "$" + decl.getName();
        } else {
            // Top-level class: module/path/ClassName
            classFileName = className + "/" + decl.getName();
        }
        
        // Push this class onto the stack for any nested classes it may contain
        classNameStack.push(classFileName);
        String previousEnclosingClass = currentEnclosingClass;
        currentEnclosingClass = classFileName;
        
        // Save and set classWriter for lambda method generation
        ClassWriter savedClassWriter = this.classWriter;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        this.classWriter = cw;  // Lambda methods need to be added to this class
        
        // Determine superclass
        String superClass = "java/lang/Object";
        if (decl.getSuperClass().isPresent()) {
            superClass = getClassNameFromType(decl.getSuperClass().get()).replace('.', '/');
        }
        
        // Determine interfaces
        String[] interfaces = decl.getInterfaces().stream()
            .map(this::getClassNameFromType)
            .map(name -> name.replace('.', '/'))
            .toArray(String[]::new);
        
        // Generate generic signature if class has type parameters
        String signature = null;
        if (!decl.getTypeParameters().isEmpty()) {
            // Convert String type parameters to TypeParameter objects
            List<TypeParameter> typeParams = decl.getTypeParameters().stream()
                .map(name -> new TypeParameter(name, new SourceLocation("", 0, 0)))
                .collect(java.util.stream.Collectors.toList());
            signature = generateGenericSignature(typeParams, superClass, interfaces);
        }
        
        // Determine access flags
        int accessFlags = ACC_PUBLIC | ACC_SUPER;
        if (decl.isNested() && decl.isStatic()) {
            accessFlags |= ACC_STATIC;  // Static nested class
        }
        
        // Create class
        cw.visit(
            V1_8,
            accessFlags,
            classFileName,
            signature,
            superClass,
            interfaces
        );
        
        // Add class-level annotations
        for (Annotation ann : decl.getAnnotations()) {
            emitAnnotation(cw, ann);
        }
        
        // Add fields
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            generateField(cw, field, classFileName);
        }
        
        // PHASE 1: Pre-register all method signatures (for mutual recursion and self calls)
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            StringBuilder descriptor = new StringBuilder("(");
            for (FunctionDecl.Parameter param : method.getParameters()) {
                descriptor.append(getTypeDescriptor(param.getType()));
            }
            descriptor.append(")");
            if (method.isAsync()) {
                descriptor.append("Lcom/firefly/runtime/async/Future;");
            } else if (method.getReturnType().isPresent()) {
                descriptor.append(getTypeDescriptor(method.getReturnType().get()));
            } else {
                descriptor.append("V");
            }
            functionSignatures.put(method.getName(), descriptor.toString());
        }
        
        // Add constructor
        if (decl.getConstructor().isPresent()) {
            generateConstructor(cw, decl.getConstructor().get(), classFileName, superClass);
        } else {
            // Generate default constructor
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        
        // PHASE 2: Generate methods (signatures already registered)
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            generateMethod(cw, method, classFileName);
        }
        
        // Add fly() declaration if present (generates main method)
        if (decl.getFlyDeclaration().isPresent()) {
            generateFlyMethod(cw, decl.getFlyDeclaration().get(), classFileName);
        } else {
            // Fallback: if there's a public instance method named 'fly(String[] args)',
            // generate a static main() wrapper that creates an instance and calls it.
            boolean hasFlyInstance = false;
            for (ClassDecl.MethodDecl m : decl.getMethods()) {
                if ("fly".equals(m.getName()) && m.getVisibility() == ClassDecl.Visibility.PUBLIC) {
                    hasFlyInstance = true;
                    break;
                }
            }
            if (hasFlyInstance) {
                // Generate static main(String[] args)
                methodVisitor = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                methodVisitor.visitCode();

                // Create instance: new ClassName()
                methodVisitor.visitTypeInsn(NEW, classFileName);
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, classFileName, "<init>", "()V", false);

                // Load args and call fly(args)
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, classFileName, "fly", "([Ljava/lang/String;)V", false);

                // Ensure process terminates even if background executors have non-daemon threads
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "exit", "(I)V", false);

                // Return (for verifier)
                methodVisitor.visitInsn(RETURN);
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            }
        }
        
        // Generate nested classes recursively
        for (ClassDecl nestedClass : decl.getNestedClasses()) {
            nestedClass.accept(this);
        }
        
        // Generate nested interfaces
        for (InterfaceDecl nestedInterface : decl.getNestedInterfaces()) {
            nestedInterface.accept(this);
        }
        
        // Generate nested sparks
        for (SparkDecl nestedSpark : decl.getNestedSparks()) {
            nestedSpark.accept(this);
        }
        
        // Generate nested structs  
        for (StructDecl nestedStruct : decl.getNestedStructs()) {
            nestedStruct.accept(this);
        }
        
        // Generate nested data types
        for (DataDecl nestedData : decl.getNestedData()) {
            nestedData.accept(this);
        }
        
        cw.visitEnd();
        
        // Store the generated class bytecode
        generatedClasses.put(classFileName, cw.toByteArray());
        
        // Restore classWriter
        this.classWriter = savedClassWriter;
        
        // Pop this class from the stack
        classNameStack.pop();
        currentEnclosingClass = previousEnclosingClass;
        
        return null;
    }
    
    private void generateField(ClassWriter cw, ClassDecl.FieldDecl field, String className) {
        // Map Firefly visibility to JVM access flags
        int access = field.getVisibility() == ClassDecl.Visibility.PUBLIC ? ACC_PUBLIC : ACC_PRIVATE;
        if (!field.isMutable()) {
            access |= ACC_FINAL;
        }
        
        String descriptor = getTypeDescriptor(field.getType());
        FieldVisitor fv = cw.visitField(access, field.getName(), descriptor, null, null);
        
        // Register field type for later GETFIELD/PUTFIELD
        currentClassFieldTypes.put(field.getName(), descriptor);
        
        // Add field annotations
        for (Annotation ann : field.getAnnotations()) {
            emitFieldAnnotation(fv, ann);
        }
        
        fv.visitEnd();
    }
    
    private void generateConstructor(ClassWriter cw, ClassDecl.ConstructorDecl constructor,
                                    String className, String superClass) {
        // Build descriptor from parameters
        StringBuilder descriptor = new StringBuilder("(");
        for (FunctionDecl.Parameter param : constructor.getParameters()) {
            descriptor.append(getTypeDescriptor(param.getType()));
        }
        descriptor.append(")V");
        
        // Map Firefly visibility to JVM access flags
        int access = constructor.getVisibility() == ClassDecl.Visibility.PUBLIC ? ACC_PUBLIC : ACC_PRIVATE;
        MethodVisitor mv = cw.visitMethod(access, "<init>", descriptor.toString(), null, null);
        
        // Record parameter names and annotations (for DI/Jackson)
        int pIdx = 0;
        for (FunctionDecl.Parameter param : constructor.getParameters()) {
            mv.visitParameter(param.getName(), 0);
            for (Annotation ann : param.getAnnotations()) {
                emitParameterAnnotation(mv, pIdx, ann);
            }
            pIdx++;
        }
        
        // Add constructor annotations
        for (Annotation ann : constructor.getAnnotations()) {
            emitMethodAnnotation(mv, ann);
        }
        
        mv.visitCode();
        
        // Call super constructor
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V", false);
        
        // Generate constructor body
        MethodVisitor savedMethodVisitor = methodVisitor;
        methodVisitor = mv;
        Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
        Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
        Map<String, String> savedDeclaredTypes = new HashMap<>(localVariableDeclaredTypes);
        int savedLocalVarIndex = localVarIndex;
        
        localVariables.clear();
        localVariableTypes.clear();
        localVariableDeclaredTypes.clear();
        
        // Constructor has 'this' at index 0
        localVariables.put("self", 0);
        localVariableTypes.put("self", VarType.OBJECT);
        localVarIndex = 1;
        
        // Add parameters to local variables
        for (FunctionDecl.Parameter param : constructor.getParameters()) {
            int paramIndex = localVarIndex++;
            localVariables.put(param.getName(), paramIndex);
            localVariableTypes.put(param.getName(), getVarTypeFromType(param.getType()));
            String dotted = getClassNameFromType(param.getType());
            if (dotted != null) {
                localVariableDeclaredTypes.put(param.getName(), dotted);
            }
        }
        
        // Generate constructor body
        constructor.getBody().accept(this);
        // If body is a block with a final expression, it may have left a value on stack
        // Constructors must not return a value, so pop it if present
        if (constructor.getBody() instanceof BlockExpr) {
            BlockExpr block = (BlockExpr) constructor.getBody();
            if (block.getFinalExpression().isPresent()) {
                // Pop the value from stack (constructor returns void)
                mv.visitInsn(POP);
            }
        }
        
        // Restore state
        methodVisitor = savedMethodVisitor;
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVariableDeclaredTypes.clear();
        localVariableDeclaredTypes.putAll(savedDeclaredTypes);
        localVarIndex = savedLocalVarIndex;
        
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateMethod(ClassWriter cw, ClassDecl.MethodDecl method, String classFileName) {
        // Build descriptor
        StringBuilder descriptor = new StringBuilder("(");
        
        // Note: fly method is now handled separately in generateFlyMethod
        // Regular methods are all instance methods
        for (FunctionDecl.Parameter param : method.getParameters()) {
            descriptor.append(getTypeDescriptor(param.getType()));
        }
        descriptor.append(")");
        
        if (method.isAsync()) {
            descriptor.append("Lcom/firefly/runtime/async/Future;");
        } else if (method.getReturnType().isPresent()) {
            descriptor.append(getTypeDescriptor(method.getReturnType().get()));
        } else {
            descriptor.append("V");
        }
        
        // Method signature already registered in Phase 1 (pre-registration in visitClassDecl)
        // Determine method access flags based on visibility
        // All regular methods are instance methods (not static)
        int accessFlags = method.getVisibility() == ClassDecl.Visibility.PUBLIC ? ACC_PUBLIC : ACC_PRIVATE;
        
        // Save current className and method visitor for nested lambda generation
        String savedClassName = this.className;
        this.className = classFileName;  // Set to full class name for lambda generation
        MethodVisitor savedMethodVisitor = methodVisitor;
        methodVisitor = cw.visitMethod(accessFlags, method.getName(), descriptor.toString(), null, null);
        
        // Add method annotations
        for (Annotation ann : method.getAnnotations()) {
            emitMethodAnnotation(methodVisitor, ann);
        }
        
        methodVisitor.visitCode();
        
        // Set up local variables
        Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
        Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
        int savedLocalVarIndex = localVarIndex;
        
        localVariables.clear();
        localVariableTypes.clear();
        localVariableDeclaredTypes.clear();
        
        // All methods are instance methods - index 0 is 'this' (self)
        localVariables.put("self", 0);
        localVariableTypes.put("self", VarType.OBJECT);
        localVarIndex = 1;
        
        // Add parameters to local variables and emit parameter annotations
        int paramIdx = 0;
        for (FunctionDecl.Parameter param : method.getParameters()) {
            int paramIndex = localVarIndex++;
            localVariables.put(param.getName(), paramIndex);
            localVariableTypes.put(param.getName(), getVarTypeFromType(param.getType()));
            // Track declared type for parameters (for instance method resolution)
            String dotted = getClassNameFromType(param.getType());
            if (dotted != null) {
                localVariableDeclaredTypes.put(param.getName(), dotted);
            }
            
            // Emit parameter annotations
            for (Annotation ann : param.getAnnotations()) {
                emitParameterAnnotation(methodVisitor, paramIdx, ann);
            }
            paramIdx++;
        }
        
        if (method.isAsync()) {
            // Async instance method: generate static helper and return Future.async(lambda)
            String helperMethodName = "$async$body$" + method.getName();
            StringBuilder helperDesc = new StringBuilder("(");
            helperDesc.append("L").append(classFileName).append(";");
            for (FunctionDecl.Parameter p : method.getParameters()) {
                helperDesc.append(getTypeDescriptor(p.getType()));
            }
            // Helper returns the declared body type (not Object)
            String bodyReturnDesc = method.getReturnType().isPresent() ? getTypeDescriptor(method.getReturnType().get()) : "V";
            helperDesc.append(")").append(bodyReturnDesc);
            
            // Create helper method
            MethodVisitor helperMv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, helperMethodName, helperDesc.toString(), null, null);
            helperMv.visitCode();
            
            // Save state and set up helper local variables
            MethodVisitor outerMv = methodVisitor;
            Map<String, Integer> outerLocals = new HashMap<>(localVariables);
            Map<String, VarType> outerLocalTypes = new HashMap<>(localVariableTypes);
            int outerLocalIdx = localVarIndex;
            
            methodVisitor = helperMv;
            localVariables.clear();
            localVariableTypes.clear();
            
            // helper locals: self at 0, then params
            localVariables.put("self", 0);
            localVariableTypes.put("self", VarType.OBJECT);
            int paramIndexStart = 1;
            int idx = paramIndexStart;
            for (FunctionDecl.Parameter p : method.getParameters()) {
                localVariables.put(p.getName(), idx);
                localVariableTypes.put(p.getName(), getVarTypeFromType(p.getType()));
                idx += getTypeSize(p.getType());
            }
            localVarIndex = idx;
            
            // Generate body
            method.getBody().accept(this);
            
            // Typed return based on bodyReturnDesc
            if ("V".equals(bodyReturnDesc)) {
                methodVisitor.visitInsn(RETURN);
            } else {
                VarType rt = getVarTypeFromType(method.getReturnType().get());
                // If expression produced an Object but return type is primitive, unbox now
                if (lastExpressionType == VarType.OBJECT) {
                    switch (rt) {
                        case INT:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                            lastExpressionType = VarType.INT;
                            break;
                        case BOOLEAN:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                            lastExpressionType = VarType.BOOLEAN;
                            break;
                        case LONG:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                            lastExpressionType = VarType.LONG;
                            break;
                        case FLOAT:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                            lastExpressionType = VarType.DOUBLE;
                            break;
                        case DOUBLE:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                            lastExpressionType = VarType.DOUBLE;
                            break;
                        default:
                            // No-op for reference types
                    }
                }
                switch (rt) {
                    case INT:
                    case BOOLEAN:
                        methodVisitor.visitInsn(IRETURN);
                        break;
                    case LONG:
                        methodVisitor.visitInsn(LRETURN);
                        break;
                    case FLOAT:
                    case DOUBLE:
                        methodVisitor.visitInsn(DRETURN);
                        break;
                    default:
                        methodVisitor.visitInsn(ARETURN);
                        break;
                }
            }
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
            
            // Restore state for outer (public) method
            methodVisitor = outerMv;
            localVariables.clear();
            localVariableTypes.clear();
            localVariables.put("self", 0);
            localVariableTypes.put("self", VarType.OBJECT);
            localVarIndex = 1;
            for (FunctionDecl.Parameter p : method.getParameters()) {
                localVariables.put(p.getName(), localVarIndex);
                localVariableTypes.put(p.getName(), getVarTypeFromType(p.getType()));
                localVarIndex += getTypeSize(p.getType());
            }
            
            // Push captures: self and params
            methodVisitor.visitVarInsn(ALOAD, 0); // self
            int loadIdx = 1;
            for (FunctionDecl.Parameter p : method.getParameters()) {
                methodVisitor.visitVarInsn(getLoadOpcode(p.getType()), loadIdx);
                loadIdx += getTypeSize(p.getType());
            }
            
            // Build invokedynamic to create Callable or Runnable with captures
            Handle bootstrap = new Handle(
                H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
            );
            Handle impl = new Handle(
                H_INVOKESTATIC,
                classFileName,
                helperMethodName,
                helperDesc.toString(),
                false
            );
            String sam;
            String instantiated;
            StringBuilder indyDesc = new StringBuilder("(");
            indyDesc.append("L").append(classFileName).append(";");
            for (FunctionDecl.Parameter p : method.getParameters()) {
                indyDesc.append(getTypeDescriptor(p.getType()));
            }
            if ("V".equals(bodyReturnDesc)) {
                // Runnable path for void
                sam = "()V";
                instantiated = "()V";
                indyDesc.append(")Ljava/lang/Runnable;");
                methodVisitor.visitInvokeDynamicInsn(
                    "run",
                    indyDesc.toString(),
                    bootstrap,
                    org.objectweb.asm.Type.getType(sam),
                    impl,
                    org.objectweb.asm.Type.getType(instantiated)
                );
                // Future.async(Runnable, Executor)
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/util/concurrent/ForkJoinPool",
                    "commonPool",
                    "()Ljava/util/concurrent/ForkJoinPool;",
                    false
                );
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "com/firefly/runtime/async/Future",
                    "async",
                    "(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Lcom/firefly/runtime/async/Future;",
                    false
                );
            } else {
                // Callable path with typed instantiated return
                sam = "()Ljava/lang/Object;";
                instantiated = "()" + bodyReturnDesc;
                indyDesc.append(")Ljava/util/concurrent/Callable;");
                methodVisitor.visitInvokeDynamicInsn(
                    "call",
                    indyDesc.toString(),
                    bootstrap,
                    org.objectweb.asm.Type.getType(sam),
                    impl,
                    org.objectweb.asm.Type.getType(instantiated)
                );
                // Future.async(Callable, Executor)
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/util/concurrent/ForkJoinPool",
                    "commonPool",
                    "()Ljava/util/concurrent/ForkJoinPool;",
                    false
                );
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "com/firefly/runtime/async/Future",
                    "async",
                    "(Ljava/util/concurrent/Callable;Ljava/util/concurrent/Executor;)Lcom/firefly/runtime/async/Future;",
                    false
                );
            }
            methodVisitor.visitInsn(ARETURN);
        } else {
            // Generate method body
            method.getBody().accept(this);
            
            // Add appropriate return based on return type
            if (method.getReturnType().isPresent()) {
                String returnDescriptor = getTypeDescriptor(method.getReturnType().get());
                if ("V".equals(returnDescriptor)) {
                    // Unit/void type - just return
                    methodVisitor.visitInsn(RETURN);
                } else {
                    VarType returnType = getVarTypeFromType(method.getReturnType().get());
                    // If result is Object but return type is primitive, unbox before returning
                    if (lastExpressionType == VarType.OBJECT && returnType != VarType.OBJECT) {
                        switch (returnType) {
                            case INT:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                                lastExpressionType = VarType.INT;
                                break;
                            case BOOLEAN:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                                lastExpressionType = VarType.BOOLEAN;
                                break;
                            case FLOAT:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                                lastExpressionType = VarType.DOUBLE;
                                break;
                            case LONG:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                                lastExpressionType = VarType.LONG;
                                break;
                            case DOUBLE:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                                lastExpressionType = VarType.DOUBLE;
                                break;
                            default:
                                // No-op for reference types
                        }
                    }
                    // If result is primitive but return type is Object, box before returning
                    else if (lastExpressionType != VarType.OBJECT && returnType == VarType.OBJECT) {
                        switch (lastExpressionType) {
                            case INT:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                lastExpressionType = VarType.OBJECT;
                                break;
                            case BOOLEAN:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                                lastExpressionType = VarType.OBJECT;
                                break;
                            case FLOAT:
                            case DOUBLE:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                                lastExpressionType = VarType.OBJECT;
                                break;
                            case LONG:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                                lastExpressionType = VarType.OBJECT;
                                break;
                            default:
                                // STRING and other reference types don't need boxing
                        }
                    }
                    switch (returnType) {
                        case INT:
                        case BOOLEAN:
                            methodVisitor.visitInsn(IRETURN);
                            break;
                        case FLOAT:
                            methodVisitor.visitInsn(DRETURN);
                            break;
                        case STRING:
                        case OBJECT:
                            methodVisitor.visitInsn(ARETURN);
                            break;
                    }
                }
            } else {
                methodVisitor.visitInsn(RETURN);
            }
        }
        
        try {
            methodVisitor.visitMaxs(0, 0);
        } catch (Exception ex) {
            throw new RuntimeException("ASM frame computation failed in method '" + method.getName() + "' of class '" + classFileName + "'", ex);
        }
        methodVisitor.visitEnd();
        
        // Restore previous state
        this.className = savedClassName;  // Restore module-level class name
        methodVisitor = savedMethodVisitor;
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
    }
    
    /**
     * Generate JVM main() method from Firefly fly() declaration.
     * fly() is now an INSTANCE method (so it can access self and call other methods),
     * and we generate a static main() wrapper that creates an instance and calls fly().
     */
    private void generateFlyMethod(ClassWriter cw, ClassDecl.FlyDecl flyDecl, String classFileName) {
        // STEP 1: Generate fly() as an INSTANCE method with access to self
        String flyDescriptor = "([Ljava/lang/String;)V";
        
        // Save current state
        String savedClassName = this.className;
        this.className = classFileName;
        MethodVisitor savedMethodVisitor = methodVisitor;
        
        // Generate fly() as PUBLIC INSTANCE method (not static)
        methodVisitor = cw.visitMethod(ACC_PUBLIC, "fly", flyDescriptor, null, null);
        
        // Add method annotations
        for (Annotation ann : flyDecl.getAnnotations()) {
            emitMethodAnnotation(methodVisitor, ann);
        }
        
        methodVisitor.visitCode();
        // Mark start of method for stable frame computation
        Label __flyStart = new Label();
        methodVisitor.visitLabel(__flyStart);
        
        // Set up local variables
        Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
        Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
        int savedLocalVarIndex = localVarIndex;
        
        localVariables.clear();
        localVariableTypes.clear();
        
        // INSTANCE method - index 0 is 'this' (self), args at index 1
        localVariables.put("self", 0);
        localVariableTypes.put("self", VarType.OBJECT);
        localVariables.put("args", 1);
        localVariableTypes.put("args", VarType.STRING_ARRAY);
        localVarIndex = 2;
        
        // Generate fly method body (now has access to self)
        flyDecl.getBody().accept(this);
        
        // Add return (fly returns void/Unit)
        methodVisitor.visitInsn(RETURN);
        
        // Mark end label
        Label __flyEnd = new Label();
        methodVisitor.visitLabel(__flyEnd);
        
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        
        // Restore state after fly() generation
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
        
        // STEP 2: Generate static main() wrapper that creates instance and calls fly()
        methodVisitor = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        
        // Create instance: new ClassName()
        methodVisitor.visitTypeInsn(NEW, classFileName);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, classFileName, "<init>", "()V", false);
        
        // Load args: pass args to fly()
        methodVisitor.visitVarInsn(ALOAD, 0);
        
        // Call instance method: instance.fly(args)
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, classFileName, "fly", "([Ljava/lang/String;)V", false);
        
        // Return from main; JVM will exit if only daemon threads remain
        methodVisitor.visitInsn(RETURN);
        
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        
        // Restore previous state
        this.className = savedClassName;
        methodVisitor = savedMethodVisitor;
    }
    
    private void emitAnnotation(ClassWriter cw, Annotation ann) {
        String descriptor = resolveAnnotationDescriptor(ann.getName());
        AnnotationVisitor av = cw.visitAnnotation(descriptor, true);
        
        // Add annotation arguments
        emitAnnotationValues(av, ann);
        
        av.visitEnd();
    }
    
    private void emitFieldAnnotation(FieldVisitor fv, Annotation ann) {
        String descriptor = resolveAnnotationDescriptor(ann.getName());
        AnnotationVisitor av = fv.visitAnnotation(descriptor, true);
        
        emitAnnotationValues(av, ann);
        
        av.visitEnd();
    }
    
    private void emitMethodAnnotation(MethodVisitor mv, Annotation ann) {
        String descriptor = resolveAnnotationDescriptor(ann.getName());
        AnnotationVisitor av = mv.visitAnnotation(descriptor, true);
        
        emitAnnotationValues(av, ann);
        
        av.visitEnd();
    }
    
    private void emitParameterAnnotation(MethodVisitor mv, int parameterIndex, Annotation ann) {
        String descriptor = resolveAnnotationDescriptor(ann.getName());
        AnnotationVisitor av = mv.visitParameterAnnotation(parameterIndex, descriptor, true);
        
        emitAnnotationValues(av, ann);
        
        av.visitEnd();
    }
    
    /**
     * Emit annotation values correctly handling arrays.
     * Spring annotations like @GetMapping expect String[] for value attribute.
     */
    private void emitAnnotationValues(AnnotationVisitor av, Annotation ann) {
        for (Map.Entry<String, Object> entry : ann.getArguments().entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            
            // Determine expected return type of the annotation element via reflection (no framework-specific heuristics)
            java.util.Optional<Class<?>> annClass = resolveAnnotationClass(ann.getName());
            Class<?> expectedReturn = null;
            if (annClass.isPresent()) {
                try {
                    java.lang.reflect.Method m = annClass.get().getMethod(name);
                    expectedReturn = m.getReturnType();
                } catch (NoSuchMethodException ignore) {
                    // If element not found, fall back to emitting as-is
                }
            }
            
            // If expected type is an array (e.g., String[]), wrap single String into array
            if (value instanceof String && expectedReturn != null && expectedReturn.isArray()) {
                AnnotationVisitor arrayVisitor = av.visitArray(name);
                arrayVisitor.visit(null, value);
                arrayVisitor.visitEnd();
                continue;
            }
            
            
            // Emit as-is when type cannot be resolved or is not an array
            av.visit(name, value);
        }
    }

    private java.util.Optional<Class<?>> resolveAnnotationClass(String annotationName) {
        // Try to resolve using TypeResolver first (respects project classpath)
        java.util.Optional<String> fullName = typeResolver.resolveClassName(annotationName);
        if (fullName.isPresent()) {
            java.util.Optional<Class<?>> cls = typeResolver.getClass(fullName.get());
            if (cls.isPresent()) return cls;
        }
        // If already qualified, try loading via TypeResolver too
        if (annotationName.contains(".")) {
            java.util.Optional<Class<?>> cls = typeResolver.getClass(annotationName);
            if (cls.isPresent()) return cls;
        }
        return java.util.Optional.empty();
    }
    
    /**
     * Resolve an annotation name to its JVM descriptor.
     * Uses the TypeResolver to find the fully qualified name from imports.
     */
    private String resolveAnnotationDescriptor(String annotationName) {
        // Try to resolve using TypeResolver
        java.util.Optional<String> fullName = typeResolver.resolveClassName(annotationName);
        
        if (fullName.isPresent()) {
            return "L" + fullName.get().replace('.', '/') + ";";
        }
        
        // Fallback: if it contains dots, assume it's already qualified
        if (annotationName.contains(".")) {
            return "L" + annotationName.replace('.', '/') + ";";
        }
        
        // Last resort: use as-is (will likely fail at runtime, but let's be defensive)
        return "L" + annotationName + ";";
    }
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        currentFunctionName = decl.getName();
        
        // Generate method descriptor
        StringBuilder descriptor = new StringBuilder("(");
        
        // Handle fly method specially (maps to JVM main)
        if (decl.getName().equals("fly")) {
            descriptor.append("[Ljava/lang/String;");
        } else {
            // Add parameter types to descriptor
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                descriptor.append(getTypeDescriptor(param.getType()));
            }
        }
        
        descriptor.append(")");
        
        // For async functions, return type is Future<T>
        if (decl.isAsync()) {
            // Async functions always return Future
            descriptor.append("Lcom/firefly/runtime/async/Future;");
        } else {
            // Add return type
            if (decl.getReturnType().isPresent()) {
                descriptor.append(getTypeDescriptor(decl.getReturnType().get()));
            } else {
                descriptor.append("V"); // void
            }
        }
        
        String descriptorStr = descriptor.toString();
        functionSignatures.put(decl.getName(), descriptorStr);
        
        // Create method - fly() is translated to main for JVM entry point
        String jvmMethodName = decl.getName().equals("fly") ? "main" : decl.getName();
        methodVisitor = classWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            jvmMethodName,
            descriptorStr,
            null,
            null
        );
        methodVisitor.visitCode();
        
        // Initialize local variables
        localVariables.clear();
        localVariableTypes.clear();
        localVariableDeclaredTypes.clear();
        currentFunctionParams.clear();
        scopeStack.clear();
        
        if (decl.getName().equals("fly")) {
            // JVM main requires String[] args at index 0
            // Register args parameter so it's accessible in fly() body
            localVarIndex = 1;
            localVariables.put("args", 0);
            localVariableTypes.put("args", VarType.STRING_ARRAY);
        } else {
            localVarIndex = 0;
            // Register parameters as local variables
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                int paramIndex = localVarIndex;
                localVariables.put(param.getName(), paramIndex);
                VarType paramVarType = getVarTypeFromType(param.getType());
                localVariableTypes.put(param.getName(), paramVarType);
                
                // Track declared class name for parameters to aid pattern resolution
                String dottedClass = getClassNameFromType(param.getType());
                if (dottedClass != null) {
                    localVariableDeclaredTypes.put(param.getName(), dottedClass);
                }
                
                // Increment by type size (1 for most types, 2 for long/double)
                localVarIndex += getVarTypeSize(paramVarType);
                
                // Store parameter type name for struct field access inference
                String typeName = getTypeNameFromType(param.getType());
                if (typeName != null) {
                    currentFunctionParams.put(param.getName(), typeName);
                }
            }
        }
        
        if (decl.isAsync()) {
            // For async functions, generate: Future.async(lambda)
            String helperMethodName = "$async$body$" + decl.getName();
            // Helper takes original params and returns the declared body type
            StringBuilder helperDescBuilder = new StringBuilder("(");
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                helperDescBuilder.append(getTypeDescriptor(param.getType()));
            }
            String bodyReturnDesc = decl.getReturnType().isPresent() ? getTypeDescriptor(decl.getReturnType().get()) : "V";
            helperDescBuilder.append(")").append(bodyReturnDesc);
            String helperDescriptor = helperDescBuilder.toString();
            
            // Store current method visitor to generate helper method
            MethodVisitor asyncMethodVisitor = methodVisitor;
            Map<String, Integer> asyncLocalVars = new HashMap<>(localVariables);
            Map<String, VarType> asyncLocalVarTypes = new HashMap<>(localVariableTypes);
            int asyncLocalVarIndex = localVarIndex;
            
            // Create helper method for async body
            methodVisitor = classWriter.visitMethod(
                ACC_PRIVATE | ACC_STATIC,
                helperMethodName,
                helperDescriptor,
                null,
                null
            );
            methodVisitor.visitCode();
            
            // Reset local variables for helper method and map params
            localVariables.clear();
            localVariableTypes.clear();
            localVarIndex = 0;
            int idx = 0;
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                localVariables.put(param.getName(), idx);
                localVariableTypes.put(param.getName(), getVarTypeFromType(param.getType()));
                idx += getTypeSize(param.getType());
            }
            localVarIndex = idx;
            
            // Visit function body in helper method
            decl.getBody().accept(this);
            
            // Typed return for helper
            if ("V".equals(bodyReturnDesc)) {
                methodVisitor.visitInsn(RETURN);
            } else {
                VarType rt = getVarTypeFromType(decl.getReturnType().get());
                // If expression produced an Object but return type is primitive, unbox now
                if (lastExpressionType == VarType.OBJECT) {
                    switch (rt) {
                        case INT:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                            lastExpressionType = VarType.INT;
                            break;
                        case BOOLEAN:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                            lastExpressionType = VarType.BOOLEAN;
                            break;
                        case LONG:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                            lastExpressionType = VarType.LONG;
                            break;
                        case FLOAT:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Float");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                            lastExpressionType = VarType.FLOAT;
                            break;
                        case DOUBLE:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                            lastExpressionType = VarType.DOUBLE;
                            break;
                        default:
                            // no-op for object
                    }
                }
                switch (rt) {
                    case INT:
                    case BOOLEAN:
                        methodVisitor.visitInsn(IRETURN);
                        break;
                    case LONG:
                        methodVisitor.visitInsn(LRETURN);
                        break;
                    case FLOAT:
                    case DOUBLE:
                        methodVisitor.visitInsn(DRETURN);
                        break;
                    default:
                        methodVisitor.visitInsn(ARETURN);
                        break;
                }
            }
            
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
            
            // Restore original method visitor for async function
            methodVisitor = asyncMethodVisitor;
            localVariables.clear();
            localVariables.putAll(asyncLocalVars);
            localVariableTypes.clear();
            localVariableTypes.putAll(asyncLocalVarTypes);
            localVarIndex = asyncLocalVarIndex;
            
            // Push captures: all original params
            int loadIdxTop = 0;
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                methodVisitor.visitVarInsn(getLoadOpcode(param.getType()), loadIdxTop);
                loadIdxTop += getTypeSize(param.getType());
            }
            
            // Use invokedynamic to create lambda
            Handle bootstrapMethod = new Handle(
                H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
            );
            
            Handle implMethod = new Handle(
                H_INVOKESTATIC,
                className,
                helperMethodName,
                helperDescriptor,
                false
            );
            
            String sam;
            String instantiated;
            StringBuilder indyFactoryDesc = new StringBuilder("(");
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                indyFactoryDesc.append(getTypeDescriptor(param.getType()));
            }
            if ("V".equals(bodyReturnDesc)) {
                // Runnable path for void
                sam = "()V";
                instantiated = "()V";
                indyFactoryDesc.append(")Ljava/lang/Runnable;");
                methodVisitor.visitInvokeDynamicInsn(
                    "run",
                    indyFactoryDesc.toString(),
                    bootstrapMethod,
                    org.objectweb.asm.Type.getType(sam),
                    implMethod,
                    org.objectweb.asm.Type.getType(instantiated)
                );
                // Call Future.async(Runnable, ForkJoinPool.commonPool())
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/util/concurrent/ForkJoinPool",
                    "commonPool",
                    "()Ljava/util/concurrent/ForkJoinPool;",
                    false
                );
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "com/firefly/runtime/async/Future",
                    "async",
                    "(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Lcom/firefly/runtime/async/Future;",
                    false
                );
            } else {
                // Callable path
                sam = "()Ljava/lang/Object;";
                instantiated = "()" + bodyReturnDesc;
                indyFactoryDesc.append(")Ljava/util/concurrent/Callable;");
                methodVisitor.visitInvokeDynamicInsn(
                    "call",
                    indyFactoryDesc.toString(),
                    bootstrapMethod,
                    org.objectweb.asm.Type.getType(sam),
                    implMethod,
                    org.objectweb.asm.Type.getType(instantiated)
                );
                // Call Future.async(callable, ForkJoinPool.commonPool())
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/util/concurrent/ForkJoinPool",
                    "commonPool",
                    "()Ljava/util/concurrent/ForkJoinPool;",
                    false
                );
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "com/firefly/runtime/async/Future",
                    "async",
                    "(Ljava/util/concurrent/Callable;Ljava/util/concurrent/Executor;)Lcom/firefly/runtime/async/Future;",
                    false
                );
            }
            
            // Return the Future
            methodVisitor.visitInsn(ARETURN);
        } else {
            // Visit function body
            decl.getBody().accept(this);
            
            // Add return if needed
            if (decl.getReturnType().isPresent()) {
                // Check if return type is Unit (which maps to void in JVM)
                String returnTypeDescriptor = getTypeDescriptor(decl.getReturnType().get());
                if (returnTypeDescriptor.equals("V")) {
                    // Unit type - use void return
                    methodVisitor.visitInsn(RETURN);
                } else {
                    // For non-void functions, the body expression result is already on stack
                    VarType returnType = getVarTypeFromType(decl.getReturnType().get());
                    // If result is Object but return type is primitive, unbox before returning
                    if (lastExpressionType == VarType.OBJECT) {
                        switch (returnType) {
                            case INT:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                                lastExpressionType = VarType.INT;
                                break;
                            case BOOLEAN:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                                lastExpressionType = VarType.BOOLEAN;
                                break;
                            case FLOAT:
                                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Float");
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                                lastExpressionType = VarType.FLOAT;
                                break;
                            default:
                                // No-op for non-primitive or unsupported here
                                break;
                        }
                    }
                    switch (returnType) {
                        case INT:
                        case BOOLEAN:
                            methodVisitor.visitInsn(IRETURN);
                            break;
                        case FLOAT:
                            methodVisitor.visitInsn(FRETURN);
                            break;
                        case STRING:
                        case STRING_ARRAY:
                        case OBJECT:
                            methodVisitor.visitInsn(ARETURN);
                            break;
                    }
                }
            } else {
                // No return type specified - use void return
                methodVisitor.visitInsn(RETURN);
            }
        }
        
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        methodVisitor = null;
        currentFunctionName = null;
        
        return null;
    }
    @Override 
    public Void visitStructDecl(StructDecl decl) {
        // Compute internal class name under module path
        String structInternalName = (moduleBasePath == null || moduleBasePath.isEmpty())
            ? decl.getName()
            : moduleBasePath + "/" + decl.getName();
        
        // Register struct metadata for type resolution
        List<StructMetadata.FieldMetadata> fieldMetadata = new ArrayList<>();
        for (StructDecl.Field field : decl.getFields()) {
            fieldMetadata.add(new StructMetadata.FieldMetadata(field.getName(), field.getType()));
        }
        structRegistry.put(decl.getName(), new StructMetadata(decl.getName(), structInternalName, fieldMetadata));
        
        // Generate generic signature if struct has type parameters
        String signature = null;
        if (!decl.getTypeParameters().isEmpty()) {
            signature = generateGenericSignature(decl.getTypeParameters(), null, null);
        }
        
        // Start class (public final class)
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(
            V1_8,
            ACC_PUBLIC + ACC_FINAL,
            structInternalName,
            signature,
            "java/lang/Object",
            null
        );
        
        // Generate fields (private final)
        for (StructDecl.Field field : decl.getFields()) {
            String fieldName = field.getName();
            String fieldDescriptor = getTypeDescriptor(field.getType());
            
            cw.visitField(
                ACC_PRIVATE + ACC_FINAL,
                fieldName,
                fieldDescriptor,
                null,
                null
            ).visitEnd();
        }
        
        // Generate constructor
        generateStructConstructor(cw, structInternalName, decl.getFields());
        
        // Generate getters
        for (StructDecl.Field field : decl.getFields()) {
            generateStructGetter(cw, structInternalName, field);
        }
        
        // Generate equals, hashCode, toString
        generateStructEquals(cw, structInternalName, decl.getFields());
        generateStructHashCode(cw, structInternalName, decl.getFields());
        generateStructToString(cw, structInternalName, decl.getFields());
        
        cw.visitEnd();
        
        // Store the generated struct bytecode
        generatedClasses.put(structInternalName, cw.toByteArray());
        
        return null;
    }
    
    @Override
    public Void visitSparkDecl(SparkDecl decl) {
        String sparkInternalName = (moduleBasePath == null || moduleBasePath.isEmpty())
            ? decl.getName()
            : moduleBasePath + "/" + decl.getName();
        
        // Register spark metadata for type resolution (similar to struct)
        List<StructMetadata.FieldMetadata> fieldMetadata = new ArrayList<>();
        for (SparkDecl.SparkField field : decl.getFields()) {
            fieldMetadata.add(new StructMetadata.FieldMetadata(field.getName(), field.getType()));
        }
        structRegistry.put(decl.getName(), new StructMetadata(decl.getName(), sparkInternalName, fieldMetadata));
        
        // Generate generic signature if spark has type parameters
        String signature = null;
        if (!decl.getTypeParameters().isEmpty()) {
            signature = generateGenericSignature(decl.getTypeParameters(), null, null);
        }
        
        // Start class (public final class - immutable record)
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(
            V1_8,
            ACC_PUBLIC + ACC_FINAL,
            sparkInternalName,
            signature,
            "java/lang/Object",
            null
        );
        
        // Generate fields (private final - immutable)
        for (SparkDecl.SparkField field : decl.getFields()) {
            String fieldName = field.getName();
            String fieldDescriptor = getTypeDescriptor(field.getType());
            
            cw.visitField(
                ACC_PRIVATE + ACC_FINAL,
                fieldName,
                fieldDescriptor,
                null,
                null
            ).visitEnd();
        }
        
        // Generate constructor with validation
        generateSparkConstructor(cw, sparkInternalName, decl);
        
        // Generate getters (immutable accessors)
        for (SparkDecl.SparkField field : decl.getFields()) {
            generateSparkGetter(cw, sparkInternalName, field);
        }
        
        // Generate .with() method for copy-with-modifications
        generateSparkWithMethod(cw, sparkInternalName, decl);
        
        // Generate computed properties
        for (SparkDecl.ComputedProperty prop : decl.getComputedProperties()) {
            generateComputedProperty(cw, sparkInternalName, prop);
        }
        
        // Generate custom methods
        for (FunctionDecl method : decl.getMethods()) {
            generateSparkCustomMethod(cw, sparkInternalName, method, decl);
        }
        
        // Generate equals, hashCode, toString
        generateSparkEquals(cw, sparkInternalName, decl.getFields());
        generateSparkHashCode(cw, sparkInternalName, decl.getFields());
        generateSparkToString(cw, sparkInternalName, decl.getFields());
        
        // Check for @derive annotations and generate accordingly
        if (decl.hasAnnotation("derive")) {
            generateDeriveImplementations(cw, sparkInternalName, decl);
        }
        
        // Check for @travelable annotation
        if (decl.hasAnnotation("travelable")) {
            generateTravelableWrapper(cw, sparkInternalName, decl);
        }
        
        cw.visitEnd();
        
        // Store the generated spark bytecode
        generatedClasses.put(sparkInternalName, cw.toByteArray());
        
        return null;
    }
    
    private void generateSparkConstructor(ClassWriter cw, String className, SparkDecl decl) {
        // Build constructor signature
        StringBuilder descriptor = new StringBuilder("(");
        for (SparkDecl.SparkField field : decl.getFields()) {
            descriptor.append(getTypeDescriptor(field.getType()));
        }
        descriptor.append(")V");
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "<init>",
            descriptor.toString(),
            null,
            new String[]{"java/lang/IllegalArgumentException"}
        );
        mv.visitCode();
        
        // Call super constructor
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        
        // Assign fields
        int localIndex = 1;
        for (SparkDecl.SparkField field : decl.getFields()) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(getLoadOpcode(field.getType()), localIndex);
            mv.visitFieldInsn(
                PUTFIELD,
                className,
                field.getName(),
                getTypeDescriptor(field.getType())
            );
            localIndex += getTypeSize(field.getType());
        }
        
        // Generate validation code if present
        if (decl.getValidateBlock().isPresent()) {
            // Execute validation block - if it throws, constructor fails
            SparkDecl.ValidationBlock validation = decl.getValidateBlock().get();
            
            // Save current method visitor and set up for validation
            MethodVisitor savedMv = methodVisitor;
            Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
            Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
            int savedLocalVarIndex = localVarIndex;
            
            methodVisitor = mv;
            localVariables.clear();
            localVariableTypes.clear();
            
            // Register 'this' and fields as accessible in validation block
            localVariables.put("self", 0);
            localVariableTypes.put("self", VarType.OBJECT);
            localVarIndex = localIndex;  // Continue from where constructor params ended
            
            // Visit validation block body
            validation.getBody().accept(this);
            
            // Restore state
            methodVisitor = savedMv;
            localVariables.clear();
            localVariables.putAll(savedLocalVars);
            localVariableTypes.clear();
            localVariableTypes.putAll(savedLocalVarTypes);
            localVarIndex = savedLocalVarIndex;
        }
        
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateSparkGetter(ClassWriter cw, String className, SparkDecl.SparkField field) {
        String fieldName = field.getName();
        String descriptor = "()" + getTypeDescriptor(field.getType());
        
        // JavaBean getter names for consistency with struct access and reflection-based users
        String getterName;
        VarType vt = getVarTypeFromType(field.getType());
        if (vt == VarType.BOOLEAN) {
            getterName = (fieldName.startsWith("is") ? fieldName : "is" + capitalize(fieldName));
        } else {
            getterName = "get" + capitalize(fieldName);
        }
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            getterName,
            descriptor,
            null,
            null
        );
        mv.visitCode();
        
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(
            GETFIELD,
            className,
            field.getName(),
            getTypeDescriptor(field.getType())
        );
        mv.visitInsn(getReturnOpcode(field.getType()));
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateSparkWithMethod(ClassWriter cw, String className, SparkDecl decl) {
        // Generate .with() method that takes optional new values and returns new instance
        // Method signature: with(field1: Type1, field2: Type2, ...) -> ClassName
        
        StringBuilder descriptor = new StringBuilder("(");
        for (SparkDecl.SparkField field : decl.getFields()) {
            descriptor.append(getTypeDescriptor(field.getType()));
        }
        descriptor.append(")L").append(className).append(";");
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "with",
            descriptor.toString(),
            null,
            null
        );
        mv.visitCode();
        
        // Create new instance with new values
        mv.visitTypeInsn(NEW, className);
        mv.visitInsn(DUP);
        
        // Load parameters (new values)
        int localIndex = 1;
        for (SparkDecl.SparkField field : decl.getFields()) {
            mv.visitVarInsn(getLoadOpcode(field.getType()), localIndex);
            localIndex += getTypeSize(field.getType());
        }
        
        // Call constructor
        StringBuilder constructorDesc = new StringBuilder("(");
        for (SparkDecl.SparkField field : decl.getFields()) {
            constructorDesc.append(getTypeDescriptor(field.getType()));
        }
        constructorDesc.append(")V");
        
        mv.visitMethodInsn(
            INVOKESPECIAL,
            className,
            "<init>",
            constructorDesc.toString(),
            false
        );
        
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateComputedProperty(ClassWriter cw, String className, 
                                          SparkDecl.ComputedProperty prop) {
        String methodName = prop.getName();
        String descriptor = "()" + getTypeDescriptor(prop.getType());
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            methodName,
            descriptor,
            null,
            null
        );
        mv.visitCode();
        
        // Execute computed property body for real
        MethodVisitor savedMv = methodVisitor;
        Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
        Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
        int savedLocalVarIndex = localVarIndex;
        
        methodVisitor = mv;
        localVariables.clear();
        localVariableTypes.clear();
        
        // Register 'this' (self) at index 0
        localVariables.put("self", 0);
        localVariableTypes.put("self", VarType.OBJECT);
        localVarIndex = 1;
        
        // Visit the computed property body
        prop.getBody().accept(this);
        
        // Return based on type
        VarType returnType = getVarTypeFromType(prop.getType());
        switch (returnType) {
            case INT:
            case BOOLEAN:
                mv.visitInsn(IRETURN);
                break;
            case FLOAT:
                mv.visitInsn(FRETURN);
                break;
            default:
                mv.visitInsn(ARETURN);
                break;
        }
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Restore state
        methodVisitor = savedMv;
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
    }
    
    private void generateSparkEquals(ClassWriter cw, String className, 
                                     List<SparkDecl.SparkField> fields) {
        // Similar to struct equals but for spark fields
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "equals",
            "(Ljava/lang/Object;)Z",
            null,
            null
        );
        mv.visitCode();
        
        // if (this == obj) return true;
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        Label notSame = new Label();
        mv.visitJumpInsn(IF_ACMPNE, notSame);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        mv.visitLabel(notSame);
        
        // if (obj == null || getClass() != obj.getClass()) return false;
        mv.visitVarInsn(ALOAD, 1);
        Label notNull = new Label();
        mv.visitJumpInsn(IFNONNULL, notNull);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(IRETURN);
        mv.visitLabel(notNull);
        
        // Cast and compare fields
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitVarInsn(ASTORE, 2);
        
        // Compare each field
        for (SparkDecl.SparkField field : fields) {
            Label nextField = new Label();
            
            // Load both field values
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            mv.visitVarInsn(ALOAD, 2);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            
            // Compare based on type
            if (field.getType() instanceof PrimitiveType) {
                PrimitiveType pt = (PrimitiveType) field.getType();
                switch (pt.getKind()) {
                    case INT:
                    case BOOL:
                        // Compare int/bool with IF_ICMPEQ
                        mv.visitJumpInsn(IF_ICMPEQ, nextField);
                        mv.visitInsn(ICONST_0);
                        mv.visitInsn(IRETURN);
                        break;
                    case LONG:
                        // Compare long with LCMP
                        mv.visitInsn(LCMP);
                        mv.visitJumpInsn(IFEQ, nextField);
                        mv.visitInsn(ICONST_0);
                        mv.visitInsn(IRETURN);
                        break;
                    case FLOAT:
                    case DOUBLE:
                        // Compare double with DCMPL (Flylang Float maps to JVM double)
                        mv.visitInsn(DCMPL);
                        mv.visitJumpInsn(IFEQ, nextField);
                        mv.visitInsn(ICONST_0);
                        mv.visitInsn(IRETURN);
                        break;
                    default:
                        // String and other reference types
                        mv.visitMethodInsn(
                            INVOKESTATIC,
                            "java/util/Objects",
                            "equals",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                            false
                        );
                        mv.visitJumpInsn(IFNE, nextField);
                        mv.visitInsn(ICONST_0);
                        mv.visitInsn(IRETURN);
                        break;
                }
            } else if (field.getType() instanceof NamedType) {
                // Handle NamedType wrapping primitives
                String typeName = ((NamedType) field.getType()).getName();
                if ("Int".equals(typeName) || "Bool".equals(typeName)) {
                    mv.visitJumpInsn(IF_ICMPEQ, nextField);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                } else if ("Long".equals(typeName)) {
                    mv.visitInsn(LCMP);
                    mv.visitJumpInsn(IFEQ, nextField);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                } else if ("Float".equals(typeName) || "Double".equals(typeName)) {
                    mv.visitInsn(DCMPL);
                    mv.visitJumpInsn(IFEQ, nextField);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                } else {
                    // Reference types
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        "java/util/Objects",
                        "equals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                        false
                    );
                    mv.visitJumpInsn(IFNE, nextField);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                }
            } else {
                // Reference types - use Objects.equals
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    "java/util/Objects",
                    "equals",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                    false
                );
                mv.visitJumpInsn(IFNE, nextField);
                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);
            }
            mv.visitLabel(nextField);
        }
        
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateSparkHashCode(ClassWriter cw, String className, 
                                       List<SparkDecl.SparkField> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "hashCode",
            "()I",
            null,
            null
        );
        mv.visitCode();
        
        // Use Objects.hash(field1, field2, ...)
        mv.visitIntInsn(BIPUSH, fields.size());
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        
        int index = 0;
        for (SparkDecl.SparkField field : fields) {
            mv.visitInsn(DUP);
            mv.visitIntInsn(BIPUSH, index);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            
            // Box primitives
            if (field.getType() instanceof PrimitiveType) {
                PrimitiveType pt = (PrimitiveType) field.getType();
                switch (pt.getKind()) {
                    case INT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        break;
                    case LONG:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                        break;
                    case BOOL:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                        break;
                    case FLOAT:
                    case DOUBLE:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                        break;
                    // STRING and others are already objects
                }
            } else if (field.getType() instanceof NamedType) {
                String typeName = ((NamedType) field.getType()).getName();
                if ("Int".equals(typeName)) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                } else if ("Long".equals(typeName)) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                } else if ("Bool".equals(typeName)) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                } else if ("Float".equals(typeName) || "Double".equals(typeName)) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                }
            }
            
            mv.visitInsn(AASTORE);
            index++;
        }
        
        mv.visitMethodInsn(
            INVOKESTATIC,
            "java/util/Objects",
            "hash",
            "([Ljava/lang/Object;)I",
            false
        );
        mv.visitInsn(IRETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateSparkToString(ClassWriter cw, String className, 
                                       List<SparkDecl.SparkField> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "toString",
            "()Ljava/lang/String;",
            null,
            null
        );
        mv.visitCode();
        
        // Build string: "ClassName { field1: value1, field2: value2 }"
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        
        // Append class name
        mv.visitLdcInsn(className + " { ");
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        );
        
        // Append each field
        for (int i = 0; i < fields.size(); i++) {
            SparkDecl.SparkField field = fields.get(i);
            
            // field name
            mv.visitLdcInsn(field.getName() + ": ");
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false
            );
            
            // field value
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            
            // Use correct append method based on type
            String appendDescriptor;
            if (field.getType() instanceof PrimitiveType) {
                PrimitiveType pt = (PrimitiveType) field.getType();
                switch (pt.getKind()) {
                    case INT:
                        appendDescriptor = "(I)Ljava/lang/StringBuilder;";
                        break;
                    case LONG:
                        appendDescriptor = "(J)Ljava/lang/StringBuilder;";
                        break;
                    case BOOL:
                        appendDescriptor = "(Z)Ljava/lang/StringBuilder;";
                        break;
                    case FLOAT:
                    case DOUBLE:
                        appendDescriptor = "(D)Ljava/lang/StringBuilder;";
                        break;
                    default:
                        // STRING and others
                        appendDescriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                        break;
                }
            } else if (field.getType() instanceof NamedType) {
                String typeName = ((NamedType) field.getType()).getName();
                if ("Int".equals(typeName)) {
                    appendDescriptor = "(I)Ljava/lang/StringBuilder;";
                } else if ("Long".equals(typeName)) {
                    appendDescriptor = "(J)Ljava/lang/StringBuilder;";
                } else if ("Bool".equals(typeName)) {
                    appendDescriptor = "(Z)Ljava/lang/StringBuilder;";
                } else if ("Float".equals(typeName) || "Double".equals(typeName)) {
                    appendDescriptor = "(D)Ljava/lang/StringBuilder;";
                } else {
                    appendDescriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                }
            } else {
                appendDescriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            }
            
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                appendDescriptor,
                false
            );
            
            // Add comma if not last
            if (i < fields.size() - 1) {
                mv.visitLdcInsn(", ");
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                );
            }
        }
        
        // Close brace
        mv.visitLdcInsn(" }");
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        );
        
        // Call toString()
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "toString",
            "()Ljava/lang/String;",
            false
        );
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateSparkCustomMethod(ClassWriter cw, String className, 
                                           FunctionDecl method, SparkDecl sparkDecl) {
        // Build method descriptor
        StringBuilder descriptor = new StringBuilder("(");
        for (FunctionDecl.Parameter param : method.getParameters()) {
            descriptor.append(getTypeDescriptor(param.getType()));
        }
        descriptor.append(")");
        
        if (method.isAsync()) {
            descriptor.append("Lcom/firefly/runtime/async/Future;");
        } else if (method.getReturnType().isPresent()) {
            descriptor.append(getTypeDescriptor(method.getReturnType().get()));
        } else {
            descriptor.append("V");
        }
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            method.getName(),
            descriptor.toString(),
            null,
            null
        );
        mv.visitCode();
        
        // Save current state
        MethodVisitor savedMv = methodVisitor;
        Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
        Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
        int savedLocalVarIndex = localVarIndex;
        
        if (method.isAsync()) {
            // Async spark method: generate static helper and create Callable with captures
            String helperMethodName = "$async$body$" + method.getName();
            StringBuilder helperDesc = new StringBuilder("(");
            helperDesc.append("L").append(className).append(";");
            for (FunctionDecl.Parameter p : method.getParameters()) {
                helperDesc.append(getTypeDescriptor(p.getType()));
            }
            helperDesc.append(")");
            String bodyReturnDesc = method.getReturnType().isPresent() ? getTypeDescriptor(method.getReturnType().get()) : "V";
            helperDesc.append(bodyReturnDesc);
            
            // Create helper
            MethodVisitor hm = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, helperMethodName, helperDesc.toString(), null, null);
            hm.visitCode();
            
            // Switch context to helper
            methodVisitor = hm;
            localVariables.clear();
            localVariableTypes.clear();
            localVariables.put("self", 0);
            localVariableTypes.put("self", VarType.OBJECT);
            int idx = 1;
            for (FunctionDecl.Parameter p : method.getParameters()) {
                localVariables.put(p.getName(), idx);
                localVariableTypes.put(p.getName(), getVarTypeFromType(p.getType()));
                idx += getTypeSize(p.getType());
            }
            localVarIndex = idx;
            
            // Body
            method.getBody().accept(this);
            
            if ("V".equals(bodyReturnDesc)) hm.visitInsn(RETURN);
            else {
                VarType rt = getVarTypeFromType(method.getReturnType().get());
                // Unbox if needed (result currently Object)
                if (lastExpressionType == VarType.OBJECT) {
                    switch (rt) {
                        case INT:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                            lastExpressionType = VarType.INT;
                            break;
                        case BOOLEAN:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                            lastExpressionType = VarType.BOOLEAN;
                            break;
                        case LONG:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                            lastExpressionType = VarType.LONG;
                            break;
                        case FLOAT:
                        case DOUBLE:
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                            lastExpressionType = VarType.DOUBLE;
                            break;
                        default:
                            break;
                    }
                }
                switch (rt) {
                    case INT:
                    case BOOLEAN: hm.visitInsn(IRETURN); break;
                    case FLOAT: hm.visitInsn(DRETURN); break;
                    case LONG: hm.visitInsn(LRETURN); break;
                    default: hm.visitInsn(ARETURN); break;
                }
            }
            hm.visitMaxs(0,0);
            hm.visitEnd();
            
            // Restore context to outer method
            methodVisitor = mv;
            localVariables.clear();
            localVariableTypes.clear();
            localVariables.put("self", 0);
            localVariableTypes.put("self", VarType.OBJECT);
            int loadIdx = 1;
            for (FunctionDecl.Parameter p : method.getParameters()) {
                localVariables.put(p.getName(), loadIdx);
                localVariableTypes.put(p.getName(), getVarTypeFromType(p.getType()));
                loadIdx += getTypeSize(p.getType());
            }
            localVarIndex = loadIdx;
            
            // Push captures
            mv.visitVarInsn(ALOAD, 0);
            int capIdx = 1;
            for (FunctionDecl.Parameter p : method.getParameters()) {
                mv.visitVarInsn(getLoadOpcode(p.getType()), capIdx);
                capIdx += getTypeSize(p.getType());
            }
            
            Handle bootstrap = new Handle(
                H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
            );
            Handle impl = new Handle(
                H_INVOKESTATIC,
                className,
                helperMethodName,
                helperDesc.toString(),
                false
            );
            String sam = "()Ljava/lang/Object;";
            String instantiated = "()Ljava/lang/Object;";
            StringBuilder indyDesc = new StringBuilder("(");
            indyDesc.append("L").append(className).append(";");
            for (FunctionDecl.Parameter p : method.getParameters()) {
                indyDesc.append(getTypeDescriptor(p.getType()));
            }
            indyDesc.append(")Ljava/util/concurrent/Callable;");
            
            mv.visitInvokeDynamicInsn(
                "call",
                indyDesc.toString(),
                bootstrap,
                org.objectweb.asm.Type.getType(sam),
                impl,
                org.objectweb.asm.Type.getType(instantiated)
            );
            
            // Future.async(callable, ForkJoinPool.commonPool())
            mv.visitMethodInsn(
                INVOKESTATIC,
                "java/util/concurrent/ForkJoinPool",
                "commonPool",
                "()Ljava/util/concurrent/ForkJoinPool;",
                false
            );
            mv.visitMethodInsn(
                INVOKESTATIC,
                "com/firefly/runtime/async/Future",
                "async",
                "(Ljava/util/concurrent/Callable;Ljava/util/concurrent/Executor;)Lcom/firefly/runtime/async/Future;",
                false
            );
            mv.visitInsn(ARETURN);
            
        } else {
            methodVisitor = mv;
            localVariables.clear();
            localVariableTypes.clear();
            
            // Register 'this' (self) at index 0
            localVariables.put("self", 0);
            localVariableTypes.put("self", VarType.OBJECT);
            localVarIndex = 1;
            
            // Register method parameters
            for (FunctionDecl.Parameter param : method.getParameters()) {
                int paramIndex = localVarIndex++;
                localVariables.put(param.getName(), paramIndex);
                localVariableTypes.put(param.getName(), getVarTypeFromType(param.getType()));
            }
            
            // Visit method body
            method.getBody().accept(this);
            
            // Add return if needed
            if (method.getReturnType().isPresent()) {
                VarType returnType = getVarTypeFromType(method.getReturnType().get());
                switch (returnType) {
                    case INT:
                    case BOOLEAN:
                        mv.visitInsn(IRETURN);
                        break;
                    case FLOAT:
                        mv.visitInsn(DRETURN);
                        break;
                    default:
                        mv.visitInsn(ARETURN);
                        break;
                }
            } else {
                mv.visitInsn(RETURN);
            }
        }
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Restore state
        methodVisitor = savedMv;
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
    }
    
    private int getLoadOpcode(com.firefly.compiler.ast.type.Type type) {
        // Get appropriate load opcode for type
        if (type instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) type;
            switch (pt.getKind()) {
                case INT: return ILOAD;
                case LONG: return LLOAD;
                case BOOL: return ILOAD;
                case FLOAT: return DLOAD;  // Flylang Float maps to JVM double
                case DOUBLE: return DLOAD;
                default: return ALOAD;
            }
        } else if (type instanceof NamedType || type instanceof com.firefly.compiler.ast.type.GenericType) {
            // Handle Named/Generic wrapping primitives by name
            String name = (type instanceof NamedType)
                ? ((NamedType) type).getName()
                : ((com.firefly.compiler.ast.type.GenericType) type).getBaseName();
            switch (name) {
                case "Int": return ILOAD;
                case "Long": return LLOAD;
                case "Bool": return ILOAD;
                case "Float": return DLOAD;  // Flylang Float maps to JVM double
                case "Double": return DLOAD;
                default: return ALOAD;
            }
        }
        return ALOAD;
    }
    
    private int getReturnOpcode(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) type;
            switch (pt.getKind()) {
                case INT: return IRETURN;
                case LONG: return LRETURN;
                case BOOL: return IRETURN;
                case FLOAT: return DRETURN;  // Flylang Float maps to JVM double
                case DOUBLE: return DRETURN;
                case VOID: return RETURN;
                default: return ARETURN;
            }
        } else if (type instanceof NamedType || type instanceof com.firefly.compiler.ast.type.GenericType) {
            String name = (type instanceof NamedType)
                ? ((NamedType) type).getName()
                : ((com.firefly.compiler.ast.type.GenericType) type).getBaseName();
            switch (name) {
                case "Int": return IRETURN;
                case "Long": return LRETURN;
                case "Bool": return IRETURN;
                case "Float": return DRETURN;
                case "Double": return DRETURN;
                case "Void": return RETURN;
                default: return ARETURN;
            }
        }
        return ARETURN;
    }
    
    private void generateDefaultReturn(MethodVisitor mv, com.firefly.compiler.ast.type.Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) type;
            switch (pt.getKind()) {
                case INT:
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                    return;
                case LONG:
                    mv.visitInsn(LCONST_0);
                    mv.visitInsn(LRETURN);
                    return;
                case BOOL:
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                    return;
                case FLOAT:
                case DOUBLE:
                    mv.visitInsn(DCONST_0);
                    mv.visitInsn(DRETURN);
                    return;
                case VOID:
                    mv.visitInsn(RETURN);
                    return;
            }
        }
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);
    }
    
    private int getTypeSize(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) type;
            PrimitiveType.Kind kind = pt.getKind();
            if (kind == PrimitiveType.Kind.FLOAT || kind == PrimitiveType.Kind.DOUBLE || kind == PrimitiveType.Kind.LONG) {
                return 2; // double and long take 2 slots
            }
        } else if (type instanceof NamedType || type instanceof com.firefly.compiler.ast.type.GenericType) {
            String name = (type instanceof NamedType)
                ? ((NamedType) type).getName()
                : ((com.firefly.compiler.ast.type.GenericType) type).getBaseName();
            int lt3 = name.indexOf('<');
            if (lt3 > 0) name = name.substring(0, lt3);
            if ("Float".equals(name) || "Double".equals(name) || "Long".equals(name)) {
                return 2; // double and long take 2 slots
            }
        }
        return 1;
    }
    
    /**
     * Get the JVM local variable slot size for a VarType.
     * Long and Double take 2 slots, others take 1.
     */
    private int getVarTypeSize(VarType type) {
        if (type == VarType.LONG || type == VarType.DOUBLE || type == VarType.FLOAT) {
            return 2;
        }
        return 1;
    }
    
    /**
     * Convert the value on the stack (with given VarType) to match the expected field type.
     * Handles widening conversions like Int -> Long, Int -> Double, etc.
     */
    private void convertToFieldType(VarType actualType, com.firefly.compiler.ast.type.Type expectedType) {
        if (actualType == null || expectedType == null) return;
        
        // Determine expected JVM type
        String expectedDesc = getTypeDescriptor(expectedType);
        
        // Int to Long conversion
        if (actualType == VarType.INT && "J".equals(expectedDesc)) {
            methodVisitor.visitInsn(I2L);
            lastExpressionType = VarType.LONG;
        }
        // Int to Double conversion 
        else if (actualType == VarType.INT && "D".equals(expectedDesc)) {
            methodVisitor.visitInsn(I2D);
            lastExpressionType = VarType.DOUBLE;
        }
        // Float to Double conversion (no-op in Firefly since Float already maps to JVM double)
        else if (actualType == VarType.FLOAT && "D".equals(expectedDesc)) {
            // No conversion needed - VarType.FLOAT already maps to JVM double
            lastExpressionType = VarType.DOUBLE;
        }
        // Long to Double conversion
        else if (actualType == VarType.LONG && "D".equals(expectedDesc)) {
            methodVisitor.visitInsn(L2D);
            lastExpressionType = VarType.DOUBLE;
        }
        // Add more conversions as needed
    }
    
    @Override 
    public Void visitDataDecl(DataDecl decl) {
        String dataName = decl.getName();
        // Compute internal name under module path
        String internalBase = (moduleBasePath == null || moduleBasePath.isEmpty()) ? dataName : moduleBasePath + "/" + dataName;
        
        // Generate generic signature if data type has type parameters
        String signature = null;
        if (!decl.getTypeParameters().isEmpty()) {
            signature = generateGenericSignature(decl.getTypeParameters(), null, null);
        }
        
        // Generate abstract base class (sealed-like in JVM bytecode)
        ClassWriter baseCw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        baseCw.visit(
            V1_8,
            ACC_PUBLIC + ACC_ABSTRACT,
            internalBase,
            signature,
            "java/lang/Object",
            null
        );
        
        // Add private constructor to prevent external instantiation
        MethodVisitor baseMv = baseCw.visitMethod(
            ACC_PROTECTED,
            "<init>",
            "()V",
            null,
            null
        );
        baseMv.visitCode();
        baseMv.visitVarInsn(ALOAD, 0);
        baseMv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        baseMv.visitInsn(RETURN);
        baseMv.visitMaxs(0, 0);
        baseMv.visitEnd();
        
        // Prepare to add clinit and factory methods
        // Collect no-arg variants for static singleton fields
        java.util.List<DataDecl.Variant> noArgVariants = new java.util.ArrayList<>();
        for (DataDecl.Variant v : decl.getVariants()) {
            if (v.getFields().isEmpty()) noArgVariants.add(v);
        }
        
        // Emit static fields for no-arg variants
        for (DataDecl.Variant v : noArgVariants) {
            baseCw.visitField(ACC_PUBLIC + ACC_STATIC + ACC_FINAL, v.getName(), "L" + internalBase + ";", null, null).visitEnd();
        }
        
        // Static initializer to assign singletons
        if (!noArgVariants.isEmpty()) {
            MethodVisitor clinit = baseCw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.visitCode();
            for (DataDecl.Variant v : noArgVariants) {
                String variantInternal = internalBase + "$" + v.getName();
                clinit.visitTypeInsn(NEW, variantInternal);
                clinit.visitInsn(DUP);
                clinit.visitMethodInsn(INVOKESPECIAL, variantInternal, "<init>", "()V", false);
                clinit.visitFieldInsn(PUTSTATIC, internalBase, v.getName(), "L" + internalBase + ";");
            }
            clinit.visitInsn(RETURN);
            clinit.visitMaxs(0,0);
            clinit.visitEnd();
        }
        
        // Factory methods for payload variants
        for (DataDecl.Variant v : decl.getVariants()) {
            if (!v.getFields().isEmpty()) {
                // Build descriptor (params) -> base
                StringBuilder desc = new StringBuilder("(");
                for (DataDecl.VariantField f : v.getFields()) {
                    desc.append(getTypeDescriptor(f.getType()));
                }
                desc.append(")L").append(internalBase).append(";");
                MethodVisitor fm = baseCw.visitMethod(ACC_PUBLIC + ACC_STATIC, v.getName(), desc.toString(), null, null);
                fm.visitCode();
                // new variantInternal(args)
                String variantInternal = internalBase + "$" + v.getName();
                fm.visitTypeInsn(NEW, variantInternal);
                fm.visitInsn(DUP);
                int argIndex = 0;
                for (DataDecl.VariantField f : v.getFields()) {
                    // Load args by index
                    // Determine load opcode from type
                    com.firefly.compiler.ast.type.Type t = f.getType();
                    String td = getTypeDescriptor(t);
                    if ("I".equals(td) || "Z".equals(td)) {
                        fm.visitVarInsn(ILOAD, argIndex);
                    } else if ("J".equals(td)) {
                        fm.visitVarInsn(LLOAD, argIndex);
                        argIndex++; // long takes 2 slots
                    } else if ("D".equals(td)) {
                        fm.visitVarInsn(DLOAD, argIndex);
                        argIndex++; // double takes 2 slots
                    } else {
                        fm.visitVarInsn(ALOAD, argIndex);
                    }
                    // Increment index by size
                    if ("J".equals(td) || "D".equals(td)) argIndex++;
                    argIndex++;
                }
                // Call ctor
                StringBuilder ctorDesc = new StringBuilder("(");
                for (DataDecl.VariantField f : v.getFields()) ctorDesc.append(getTypeDescriptor(f.getType()));
                ctorDesc.append(")V");
                fm.visitMethodInsn(INVOKESPECIAL, variantInternal, "<init>", ctorDesc.toString(), false);
                fm.visitInsn(ARETURN);
                fm.visitMaxs(0,0);
                fm.visitEnd();
            }
        }
        
        baseCw.visitEnd();
        generatedClasses.put(internalBase, baseCw.toByteArray());
        
        // Generate variant classes
        for (DataDecl.Variant variant : decl.getVariants()) {
            generateDataVariant(internalBase, variant);
        }
        
        return null;
    }
    
    @Override
    public Void visitStructLiteralExpr(StructLiteralExpr expr) {
        if (methodVisitor == null) return null;
        
        String structSimpleName = expr.getStructName();
        
        // Look up struct metadata
        StructMetadata metadata = structRegistry.get(structSimpleName);
        if (metadata == null) {
            throw new RuntimeException("Unknown struct: " + structSimpleName);
        }
        String structInternal = metadata.internalName != null ? metadata.internalName : resolveStructInternalName(structSimpleName);
        
        // Create new instance
        methodVisitor.visitTypeInsn(NEW, structInternal);
        methodVisitor.visitInsn(DUP);  // Duplicate for constructor call
        
        // Load each field argument in struct declaration order
        for (StructMetadata.FieldMetadata fieldMeta : metadata.fields) {
            // Find matching field init
            StructLiteralExpr.FieldInit fieldInit = expr.getFieldInits().stream()
                .filter(init -> init.getFieldName().equals(fieldMeta.name))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                    "Missing field '" + fieldMeta.name + "' in struct literal"));
            
            // Evaluate field value
            fieldInit.getValue().accept(this);
            
            // Convert to expected field type if needed (e.g., Int -> Long, Int -> Double)
            convertToFieldType(lastExpressionType, fieldMeta.type);
        }
        
        // Build constructor descriptor from struct metadata
        StringBuilder descriptor = new StringBuilder("(");
        for (StructMetadata.FieldMetadata fieldMeta : metadata.fields) {
            descriptor.append(getTypeDescriptor(fieldMeta.type));
        }
        descriptor.append(")V");
        
        // Call constructor
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            structInternal,
            "<init>",
            descriptor.toString(),
            false
        );
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    @Override
    public Void visitTraitDecl(TraitDecl decl) {
        // Generate Java interface for trait
        String currentClassName = className;
        String packageName = currentClassName.contains("/") ? 
            currentClassName.substring(0, currentClassName.lastIndexOf("/")) : "";
        
        String traitName = packageName.isEmpty() ? 
            decl.getName() : packageName + "/" + decl.getName();
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        // Generate generic signature if trait has type parameters
        String signature = null;
        if (!decl.getTypeParameters().isEmpty()) {
            signature = generateGenericSignature(decl.getTypeParameters(), null, null);
        }
        
        // Create interface (ACC_INTERFACE + ACC_ABSTRACT)
        cw.visit(
            V1_8,
            ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT,
            traitName,
            signature,
            "java/lang/Object",
            null
        );
        
        // Add method signatures (abstract methods)
        for (TraitDecl.FunctionSignature method : decl.getMembers()) {
            generateTraitMethod(cw, method);
        }
        
        cw.visitEnd();
        
        // Store the generated trait bytecode
        generatedClasses.put(traitName, cw.toByteArray());
        
        return null;
    }
    
    @Override 
    public Void visitImplDecl(ImplDecl decl) {
        // Generate implementation class for trait
        String currentClassName = className;
        String packageName = currentClassName.contains("/") ? 
            currentClassName.substring(0, currentClassName.lastIndexOf("/")) : "";
        
        // For "impl Trait for Type" - generate adapter/wrapper class
        if (decl.getForType().isPresent()) {
            String traitName = decl.getName();
            com.firefly.compiler.ast.type.Type forType = decl.getForType().get();
            String targetTypeName = getClassNameFromType(forType);
            
            // Generate impl class: Type$TraitImpl
            String implClassName = packageName.isEmpty() ? 
                targetTypeName + "$" + traitName + "Impl" : 
                packageName + "/" + targetTypeName + "$" + traitName + "Impl";
            
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            
            String traitInterface = packageName.isEmpty() ? traitName : packageName + "/" + traitName;
            
            // Generate signature if needed
            String signature = null;
            if (!decl.getTypeParameters().isEmpty()) {
                signature = generateGenericSignature(decl.getTypeParameters(), null, new String[]{traitInterface});
            }
            
            // Create class implementing trait
            cw.visit(
                V1_8,
                ACC_PUBLIC,
                implClassName,
                signature,
                "java/lang/Object",
                new String[]{traitInterface}
            );
            
            // Add field to hold the target instance
            cw.visitField(
                ACC_PRIVATE | ACC_FINAL,
                "target",
                "L" + targetTypeName.replace('.', '/') + ";",
                null,
                null
            ).visitEnd();
            
            // Generate constructor
            MethodVisitor constructor = cw.visitMethod(
                ACC_PUBLIC,
                "<init>",
                "(L" + targetTypeName.replace('.', '/') + ";)V",
                null,
                null
            );
            constructor.visitCode();
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitVarInsn(ALOAD, 1);
            constructor.visitFieldInsn(PUTFIELD, implClassName, "target", "L" + targetTypeName.replace('.', '/') + ";");
            constructor.visitInsn(RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
            
            // Generate methods from impl block
            String previousClassName = this.className;
            this.className = implClassName;
            
            for (FunctionDecl method : decl.getMethods()) {
                visitFunctionDecl(method);
            }
            
            this.className = previousClassName;
            
            cw.visitEnd();
            generatedClasses.put(implClassName, cw.toByteArray());
        } else {
            // For "impl Type" - inherent implementation (extension methods)
            // Generate static methods in a companion class
            String targetTypeName = getClassNameFromType(decl.getForType().orElse(null));
            String implClassName = packageName.isEmpty() ? 
                decl.getName() + "Extensions" : 
                packageName + "/" + decl.getName() + "Extensions";
            
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            
            cw.visit(
                V1_8,
                ACC_PUBLIC,
                implClassName,
                null,
                "java/lang/Object",
                null
            );
            
            // Generate static methods
            String previousClassName = this.className;
            this.className = implClassName;
            
            for (FunctionDecl method : decl.getMethods()) {
                // Generate as static method
                visitFunctionDecl(method);
            }
            
            this.className = previousClassName;
            
            cw.visitEnd();
            generatedClasses.put(implClassName, cw.toByteArray());
        }
        
        return null;
    }
    @Override 
    public Void visitTypeAliasDecl(TypeAliasDecl decl) {
        // Register type alias for resolution
        typeAliases.put(decl.getName(), decl.getTargetType());
        // Type aliases don't generate bytecode themselves
        return null;
    }
    
    @Override
    public Void visitExceptionDecl(ExceptionDecl decl) {
        // Generate exception class extending FlyException
        String exceptionClassName = className + "/" + decl.getName();
        
        // Determine superclass
        String superClass;
        if (decl.getSuperException().isPresent()) {
            String superName = decl.getSuperException().get();
            // Try to resolve through TypeResolver first
            Optional<String> resolved = typeResolver.resolveClassName(superName);
            if (resolved.isPresent()) {
                superClass = resolved.get().replace('.', '/');
            } else {
                // If resolution fails, assume it's in the same package or runtime
                if (superName.contains(".")) {
                    superClass = superName.replace('.', '/');
                } else {
                    // Default to runtime exceptions package
                    superClass = "com/firefly/runtime/exceptions/" + superName;
                }
            }
        } else {
            // Default to FlyException
            superClass = "com/firefly/runtime/exceptions/FlyException";
        }
        
        // Save and set classWriter for nested methods/lambdas
        ClassWriter savedClassWriter = this.classWriter;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        this.classWriter = cw;
        
        // Create exception class
        cw.visit(
            V1_8,
            ACC_PUBLIC | ACC_SUPER,
            exceptionClassName,
            null,
            superClass,
            null
        );
        
        // Add fields
        for (FieldDecl field : decl.getFields()) {
            // Use visibility from field
            int access = ACC_PRIVATE;  // Default to private for exception fields
            if (!field.isMutable()) {
                access |= ACC_FINAL;
            }
            String descriptor = getTypeDescriptor(field.getType());
            FieldVisitor fv = cw.visitField(access, field.getName(), descriptor, null, null);
            fv.visitEnd();
        }
        
        // Add constructor if present, otherwise generate default
        if (decl.getConstructor().isPresent()) {
            ClassDecl.ConstructorDecl constructor = decl.getConstructor().get();
            generateConstructor(cw, constructor, exceptionClassName, superClass);
        } else {
            // Generate default constructor calling super()
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        
        // Add methods
        String previousClassName = this.className;
        this.className = exceptionClassName;
        
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            generateMethod(cw, method, exceptionClassName);
        }
        
        this.className = previousClassName;
        
        cw.visitEnd();
        
        // Store generated exception class
        generatedClasses.put(exceptionClassName, cw.toByteArray());
        
        // Restore classWriter
        this.classWriter = savedClassWriter;
        
        return null;
    }
    @Override 
    public Void visitLetStatement(LetStatement stmt) {
        if (methodVisitor == null) return null;
        
        // Handle both simple and typed variable patterns
        if (stmt.getPattern() instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern = 
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) stmt.getPattern();
            String varName = typedPattern.getName();
            
            // Track declared type for this local variable (for Firefly class method calls)
            com.firefly.compiler.ast.type.Type declaredType = typedPattern.getType();
            String declaredClassName = getClassNameFromType(declaredType);
            if (declaredClassName != null) {
                localVariableDeclaredTypes.put(varName, declaredClassName);
            }
            
            // Evaluate initializer if present
            if (stmt.getInitializer().isPresent()) {
                stmt.getInitializer().get().accept(this);
                
                // If value is OBJECT and declared type is primitive, unbox to match declared type
                String declaredDesc = getTypeDescriptor(declaredType);
                if (lastExpressionType == VarType.OBJECT) {
                    switch (declaredDesc) {
                        case "I": // Int
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                            lastExpressionType = VarType.INT;
                            break;
                        case "J": // Long
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                            lastExpressionType = VarType.LONG;
                            break;
                        case "D": // Double / Float
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                            lastExpressionType = VarType.DOUBLE;
                            break;
                        case "Z": // Boolean
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                            lastExpressionType = VarType.BOOLEAN;
                            break;
                        default:
                            // Otherwise, try downcast towards declared reference type
                            if (declaredClassName != null) {
                                try {
                                    java.util.Optional<String> resolvedClassName = typeResolver.resolveClassName(declaredClassName);
                                    if (resolvedClassName.isPresent()) {
                                        String internalName = resolvedClassName.get().replace('.', '/');
                                        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
                                    }
                                } catch (Exception ignore) {}
                            }
                            break;
                    }
                }
                
                // Assign to local variable
                int varIndex = localVarIndex;
                localVariables.put(varName, varIndex);
                localVariableTypes.put(varName, lastExpressionType);
                
                // Increment by type size (1 for most types, 2 for long/double)
                localVarIndex += getVarTypeSize(lastExpressionType);
                
                // Track actual Java class if available
                if (lastExpressionClass != null) {
                    localVariableClasses.put(varName, lastExpressionClass);
                }
                
                // Store based on type
                switch (lastExpressionType) {
                    case INT:
                    case BOOLEAN:
                        methodVisitor.visitVarInsn(ISTORE, varIndex);
                        break;
                    case FLOAT:
                    case DOUBLE:
                        methodVisitor.visitVarInsn(DSTORE, varIndex);
                        break;
                    case LONG:
                        methodVisitor.visitVarInsn(LSTORE, varIndex);
                        break;
                    case STRING:
                    case STRING_ARRAY:
                    case OBJECT:
                        methodVisitor.visitVarInsn(ASTORE, varIndex);
                        break;
                }
            }
        } else if (stmt.getPattern() instanceof VariablePattern) {
            VariablePattern varPattern = (VariablePattern) stmt.getPattern();
            String varName = varPattern.getName();
            
            // Evaluate initializer if present
            if (stmt.getInitializer().isPresent()) {
                stmt.getInitializer().get().accept(this);
                
                // Assign to local variable
                int varIndex = localVarIndex;
                localVariables.put(varName, varIndex);
                localVariableTypes.put(varName, lastExpressionType);
                
                // Increment by type size (1 for most types, 2 for long/double)
                localVarIndex += getVarTypeSize(lastExpressionType);
                
                // Track actual Java class if available
                if (lastExpressionClass != null) {
                    localVariableClasses.put(varName, lastExpressionClass);
                }
                
                // Store based on type
                switch (lastExpressionType) {
                    case INT:
                    case BOOLEAN:
                        methodVisitor.visitVarInsn(ISTORE, varIndex);
                        break;
                    case FLOAT:
                    case DOUBLE:
                        methodVisitor.visitVarInsn(DSTORE, varIndex);
                        break;
                    case LONG:
                        methodVisitor.visitVarInsn(LSTORE, varIndex);
                        break;
                    case STRING:
                    case STRING_ARRAY:
                    case OBJECT:
                        methodVisitor.visitVarInsn(ASTORE, varIndex);
                        break;
                }
            }
        }
        return null;
    }
    @Override 
    public Void visitExprStatement(ExprStatement stmt) {
        if (methodVisitor != null) {
            lastCallWasVoid = false;
            inStatementContext = true;
            stmt.getExpression().accept(this);
            inStatementContext = false;
            // Discard any value left on the stack by expression statements
            if (!lastCallWasVoid && lastExpressionType != null) {
                switch (lastExpressionType) {
                    case LONG:
                    case DOUBLE:
                        // 64-bit primitives occupy two stack slots
                        methodVisitor.visitInsn(POP2);
                        break;
                    default:
                        methodVisitor.visitInsn(POP);
                        break;
                }
            }
        }
        return null;
    }
    @Override 
    public Void visitBinaryExpr(BinaryExpr expr) {
        if (methodVisitor == null) return null;
        
        // Visit left and right operands
        expr.getLeft().accept(this);
        VarType leftType = lastExpressionType;
        expr.getRight().accept(this);
        VarType rightType = lastExpressionType;
        
        // Generate operation bytecode
        switch (expr.getOperator()) {
            // Arithmetic operations
            case ADD:
                // Check if either operand is a string - perform string concatenation
                if (leftType == VarType.STRING || rightType == VarType.STRING) {
                    generateStringConcatenation(leftType, rightType);
                    lastExpressionType = VarType.STRING;
                } else if (leftType == VarType.DOUBLE || leftType == VarType.FLOAT || 
                          rightType == VarType.DOUBLE || rightType == VarType.FLOAT) {
                    // Double/Float addition - convert Int to Double if needed
                    convertMixedTypesForDoubleOp(leftType, rightType);
                    methodVisitor.visitInsn(DADD);
                    lastExpressionType = VarType.DOUBLE;
                } else if (leftType == VarType.LONG || rightType == VarType.LONG) {
                    // Long addition
                    convertMixedTypesForLongOp(leftType, rightType);
                    methodVisitor.visitInsn(LADD);
                    lastExpressionType = VarType.LONG;
                } else {
                    // Int addition
                    methodVisitor.visitInsn(IADD);
                    lastExpressionType = VarType.INT;
                }
                break;
            case SUBTRACT:
                if (leftType == VarType.DOUBLE || leftType == VarType.FLOAT || 
                   rightType == VarType.DOUBLE || rightType == VarType.FLOAT) {
                    convertMixedTypesForDoubleOp(leftType, rightType);
                    methodVisitor.visitInsn(DSUB);
                    lastExpressionType = VarType.DOUBLE;
                } else if (leftType == VarType.LONG || rightType == VarType.LONG) {
                    convertMixedTypesForLongOp(leftType, rightType);
                    methodVisitor.visitInsn(LSUB);
                    lastExpressionType = VarType.LONG;
                } else {
                    methodVisitor.visitInsn(ISUB);
                    lastExpressionType = VarType.INT;
                }
                break;
            case MULTIPLY:
                if (leftType == VarType.DOUBLE || leftType == VarType.FLOAT || 
                   rightType == VarType.DOUBLE || rightType == VarType.FLOAT) {
                    convertMixedTypesForDoubleOp(leftType, rightType);
                    methodVisitor.visitInsn(DMUL);
                    lastExpressionType = VarType.DOUBLE;
                } else if (leftType == VarType.LONG || rightType == VarType.LONG) {
                    convertMixedTypesForLongOp(leftType, rightType);
                    methodVisitor.visitInsn(LMUL);
                    lastExpressionType = VarType.LONG;
                } else {
                    methodVisitor.visitInsn(IMUL);
                    lastExpressionType = VarType.INT;
                }
                break;
            case DIVIDE:
                if (leftType == VarType.DOUBLE || leftType == VarType.FLOAT || 
                   rightType == VarType.DOUBLE || rightType == VarType.FLOAT) {
                    convertMixedTypesForDoubleOp(leftType, rightType);
                    methodVisitor.visitInsn(DDIV);
                    lastExpressionType = VarType.DOUBLE;
                } else if (leftType == VarType.LONG || rightType == VarType.LONG) {
                    convertMixedTypesForLongOp(leftType, rightType);
                    methodVisitor.visitInsn(LDIV);
                    lastExpressionType = VarType.LONG;
                } else {
                    methodVisitor.visitInsn(IDIV);
                    lastExpressionType = VarType.INT;
                }
                break;
            case MODULO:
                if (leftType == VarType.DOUBLE || leftType == VarType.FLOAT || 
                   rightType == VarType.DOUBLE || rightType == VarType.FLOAT) {
                    convertMixedTypesForDoubleOp(leftType, rightType);
                    methodVisitor.visitInsn(DREM);
                    lastExpressionType = VarType.DOUBLE;
                } else if (leftType == VarType.LONG || rightType == VarType.LONG) {
                    convertMixedTypesForLongOp(leftType, rightType);
                    methodVisitor.visitInsn(LREM);
                    lastExpressionType = VarType.LONG;
                } else {
                    methodVisitor.visitInsn(IREM);
                    lastExpressionType = VarType.INT;
                }
                break;
            
            // Comparison operations
            case EQUAL:
                generateComparison(IF_ICMPEQ, leftType, rightType);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case NOT_EQUAL:
                generateComparison(IF_ICMPNE, leftType, rightType);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case LESS_THAN:
                generateComparison(IF_ICMPLT, leftType, rightType);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case LESS_EQUAL:
                generateComparison(IF_ICMPLE, leftType, rightType);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case GREATER_THAN:
                generateComparison(IF_ICMPGT, leftType, rightType);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case GREATER_EQUAL:
                generateComparison(IF_ICMPGE, leftType, rightType);
                lastExpressionType = VarType.BOOLEAN;
                break;
            
            // Logical operations
            case AND:
                // a && b: if a is false, result is false; otherwise result is b
                Label andFalse = new Label();
                Label andEnd = new Label();
                methodVisitor.visitJumpInsn(IFEQ, andFalse); // if left is 0, jump to false
                methodVisitor.visitJumpInsn(GOTO, andEnd);
                methodVisitor.visitLabel(andFalse);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitLabel(andEnd);
                break;
            case OR:
                // a || b: if a is true, result is true; otherwise result is b
                Label orTrue = new Label();
                Label orEnd = new Label();
                methodVisitor.visitJumpInsn(IFNE, orTrue); // if left is not 0, jump to true
                methodVisitor.visitJumpInsn(GOTO, orEnd);
                methodVisitor.visitLabel(orTrue);
                methodVisitor.visitInsn(ICONST_1);
                methodVisitor.visitLabel(orEnd);
                break;
            
            // Range operations
            case RANGE:
                // a..b - exclusive range [a, b)
                // Create Range object (start, end, exclusive=true)
                generateRangeCreation(false);
                lastExpressionType = VarType.OBJECT;
                break;
                
            case RANGE_INCLUSIVE:
                // a..=b - inclusive range [a, b]
                // Create Range object (start, end, exclusive=false)
                generateRangeCreation(true);
                lastExpressionType = VarType.OBJECT;
                break;
            
            // Null coalescing operations
            case COALESCE:
                // a ?? b - returns a if not null, else b
                // Stack: [left, right]
                // Duplicate left for null check
                methodVisitor.visitInsn(SWAP);  // [right, left]
                methodVisitor.visitInsn(DUP);   // [right, left, left]
                
                Label coalesceNotNull = new Label();
                Label coalesceEnd = new Label();
                
                // Check if left is null
                methodVisitor.visitJumpInsn(IFNONNULL, coalesceNotNull);
                
                // Left is null, pop it and use right
                methodVisitor.visitInsn(POP);   // [right]
                methodVisitor.visitJumpInsn(GOTO, coalesceEnd);
                
                // Left is not null, keep it and pop right
                methodVisitor.visitLabel(coalesceNotNull);
                methodVisitor.visitInsn(SWAP);  // [left, right]
                methodVisitor.visitInsn(POP);   // [left]
                
                methodVisitor.visitLabel(coalesceEnd);
                lastExpressionType = VarType.OBJECT;
                break;
            
            case ELVIS:
                // a ?: b - returns a if truthy, else b
                // Similar to coalesce but checks truthiness instead of null
                // Stack: [left, right]
                methodVisitor.visitInsn(SWAP);  // [right, left]
                methodVisitor.visitInsn(DUP);   // [right, left, left]
                
                Label elvisTruthy = new Label();
                Label elvisEnd = new Label();
                
                // Check if left is truthy (non-zero for int, non-null for object)
                if (leftType == VarType.INT || leftType == VarType.BOOLEAN) {
                    methodVisitor.visitJumpInsn(IFNE, elvisTruthy);
                } else {
                    methodVisitor.visitJumpInsn(IFNONNULL, elvisTruthy);
                }
                
                // Left is falsy, pop it and use right
                methodVisitor.visitInsn(POP);
                methodVisitor.visitJumpInsn(GOTO, elvisEnd);
                
                // Left is truthy, keep it and pop right
                methodVisitor.visitLabel(elvisTruthy);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitInsn(POP);
                
                methodVisitor.visitLabel(elvisEnd);
                lastExpressionType = leftType;
                break;
            
            case SEND:
                // actor >> message - send message to actor
                // Stack: [actor, message]
                // Call ActorRef.send(message)
                methodVisitor.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "com/firefly/runtime/actor/Actor$ActorRef",
                    "send",
                    "(Ljava/lang/Object;)V",
                    false
                );
                lastExpressionType = VarType.OBJECT;
                lastCallWasVoid = true;
                break;
            
            // Bitwise operations
            case BIT_AND:
                // a & b - bitwise AND
                methodVisitor.visitInsn(IAND);
                lastExpressionType = VarType.INT;
                break;
            
            case BIT_OR:
                // a | b - bitwise OR
                methodVisitor.visitInsn(IOR);
                lastExpressionType = VarType.INT;
                break;
            
            case BIT_XOR:
                // a ^ b - bitwise XOR
                methodVisitor.visitInsn(IXOR);
                lastExpressionType = VarType.INT;
                break;
            
            case BIT_LEFT_SHIFT:
                // a << b - bitwise left shift
                methodVisitor.visitInsn(ISHL);
                lastExpressionType = VarType.INT;
                break;
            
            case BIT_RIGHT_SHIFT:
                // a >> b - bitwise right shift
                methodVisitor.visitInsn(ISHR);
                lastExpressionType = VarType.INT;
                break;
            
            // Power operation
            case POWER:
                // a ** b - exponentiation
                // Stack: [left, right]
                // Convert to doubles for Math.pow
                methodVisitor.visitInsn(I2D);
                methodVisitor.visitInsn(DUP2_X2);
                methodVisitor.visitInsn(POP2);
                methodVisitor.visitInsn(I2D);
                
                // Call Math.pow(double a, double b)
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/lang/Math",
                    "pow",
                    "(DD)D",
                    false
                );
                
                // Convert result back to int (truncating)
                methodVisitor.visitInsn(D2I);
                lastExpressionType = VarType.INT;
                break;
            
            default:
                // All binary operators should be handled above
                // Log warning - binary operator not implemented
                System.err.println("Warning: Binary operator not implemented: " + expr.getOperator());
                lastExpressionType = VarType.OBJECT;
                break;
        }
        
        return null;
    }
    
    /**
     * Resolve exception type name to JVM internal format.
     * Maps Firefly exception types to proper JVM exception class names.
     */
    private String resolveExceptionType(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof com.firefly.compiler.ast.type.NamedType) {
            com.firefly.compiler.ast.type.NamedType namedType = 
                (com.firefly.compiler.ast.type.NamedType) type;
            String name = namedType.getName();
            
            // Map common exception types
            switch (name) {
                // Flylang exceptions
                case "FlyException":
                    return "com/firefly/runtime/exceptions/FlyException";
                case "ValidationException":
                    return "com/firefly/runtime/exceptions/ValidationException";
                // Java exceptions
                case "Exception":
                    return "java/lang/Exception";
                case "RuntimeException":
                    return "java/lang/RuntimeException";
                case "NullPointerException":
                    return "java/lang/NullPointerException";
                case "IllegalArgumentException":
                    return "java/lang/IllegalArgumentException";
                case "IllegalStateException":
                    return "java/lang/IllegalStateException";
                case "IOException":
                    return "java/io/IOException";
                case "FileNotFoundException":
                    return "java/io/FileNotFoundException";
                case "SQLException":
                    return "java/sql/SQLException";
                case "ClassNotFoundException":
                    return "java/lang/ClassNotFoundException";
                case "InterruptedException":
                    return "java/lang/InterruptedException";
                case "TimeoutException":
                    return "java/util/concurrent/TimeoutException";
                case "ExecutionException":
                    return "java/util/concurrent/ExecutionException";
                case "NoSuchElementException":
                    return "java/util/NoSuchElementException";
                case "IndexOutOfBoundsException":
                    return "java/lang/IndexOutOfBoundsException";
                case "UnsupportedOperationException":
                    return "java/lang/UnsupportedOperationException";
                case "ArithmeticException":
                    return "java/lang/ArithmeticException";
                case "NumberFormatException":
                    return "java/lang/NumberFormatException";
                case "ClassCastException":
                    return "java/lang/ClassCastException";
                default:
                    // Try to resolve as a class via type resolver
                    java.util.Optional<String> resolved = typeResolver.resolveClassName(name);
                    if (resolved.isPresent()) {
                        return resolved.get().replace('.', '/');
                    }
                    // Default: qualify with current module if available
                    if (moduleBasePath != null && !moduleBasePath.isEmpty()) {
                        return moduleBasePath + "/" + name;
                    }
                    // Fallback: use simple name
                    return name.replace('.', '/');
            }
        }
        
        // Default to Throwable for unknown types
        return "java/lang/Throwable";
    }
    
    /**
     * Apply automatic type conversion from actual type to expected type.
     * Handles boxing, unboxing, and widening conversions.
     */
    private void applyTypeConversion(VarType actualType, Class<?> expectedType) {
        if (actualType == null || expectedType == null) return;
        
        // Primitive to wrapper boxing
        if (expectedType == Object.class || expectedType == Integer.class || 
            expectedType == Boolean.class || expectedType == Float.class) {
            
            switch (actualType) {
                case INT:
                    if (expectedType == Integer.class || expectedType == Object.class) {
                        methodVisitor.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Integer",
                            "valueOf",
                            "(I)Ljava/lang/Integer;",
                            false
                        );
                    }
                    break;
                    
                case BOOLEAN:
                    if (expectedType == Boolean.class || expectedType == Object.class) {
                        methodVisitor.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Boolean",
                            "valueOf",
                            "(Z)Ljava/lang/Boolean;",
                            false
                        );
                    }
                    break;
                    
                case FLOAT:
                    if (expectedType == Float.class || expectedType == Object.class) {
                        methodVisitor.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Float",
                            "valueOf",
                            "(F)Ljava/lang/Float;",
                            false
                        );
                    }
                    break;
            }
        }
        
        // Wrapper to primitive unboxing
        if (expectedType == int.class && actualType == VarType.OBJECT) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Integer",
                "intValue",
                "()I",
                false
            );
        } else if (expectedType == boolean.class && actualType == VarType.OBJECT) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Boolean",
                "booleanValue",
                "()Z",
                false
            );
        } else if (expectedType == float.class && actualType == VarType.OBJECT) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Float");
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Float",
                "floatValue",
                "()F",
                false
            );
        }
        
        // Widening conversions
        if (actualType == VarType.INT && expectedType == float.class) {
            methodVisitor.visitInsn(I2F);
        } else if (actualType == VarType.INT && expectedType == long.class) {
            methodVisitor.visitInsn(I2L);
        }
    }
    
    /**
     * Generate Range object creation.
     * Stack on entry: [start, end]
     * Stack on exit: [Range]
     */
    private void generateRangeCreation(boolean inclusive) {
        // Create new Range(start, end, inclusive)
        // Assuming Range class exists in runtime
        methodVisitor.visitTypeInsn(NEW, "com/firefly/runtime/Range");
        methodVisitor.visitInsn(DUP_X2);  // [Range, start, end, Range]
        methodVisitor.visitInsn(DUP_X2);  // [Range, Range, start, end, Range]
        methodVisitor.visitInsn(POP);      // [Range, Range, start, end]
        
        // Push inclusive flag
        methodVisitor.visitInsn(inclusive ? ICONST_1 : ICONST_0);
        
        // Call constructor Range(int start, int end, boolean inclusive)
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/firefly/runtime/Range",
            "<init>",
            "(IIZ)V",
            false
        );
        // Stack: [Range]
    }
    
    /**
     * Convert mixed Int/Long or Int/Double operands for double operation.
     * Stack on entry: [left_value, right_value]
     * Stack on exit: [left_double, right_double]
     */
    private void convertMixedTypesForDoubleOp(VarType leftType, VarType rightType) {
        // Stack: [left, right]
        if (leftType == VarType.INT && (rightType == VarType.DOUBLE || rightType == VarType.FLOAT)) {
            // Convert: [int, double] -> [double, double]
            // Store right temporarily
            int tempSlot = localVarIndex;
            localVarIndex += 2;
            methodVisitor.visitVarInsn(DSTORE, tempSlot);
            // Convert left
            methodVisitor.visitInsn(I2D);
            // Reload right
            methodVisitor.visitVarInsn(DLOAD, tempSlot);
            localVarIndex -= 2;
        } else if ((leftType == VarType.DOUBLE || leftType == VarType.FLOAT) && rightType == VarType.INT) {
            // Convert: [double, int] -> [double, double]
            methodVisitor.visitInsn(I2D);
        }
        // If both are already double/float, no conversion needed
    }
    
    /**
     * Convert mixed Int/Long operands for long operation.
     * Stack on entry: [left_value, right_value]
     * Stack on exit: [left_long, right_long]
     */
    private void convertMixedTypesForLongOp(VarType leftType, VarType rightType) {
        // Stack: [left, right]
        if (leftType == VarType.INT && rightType == VarType.LONG) {
            // Convert: [int, long] -> [long, long]
            // Store right temporarily
            int tempSlot = localVarIndex;
            localVarIndex += 2;
            methodVisitor.visitVarInsn(LSTORE, tempSlot);
            // Convert left
            methodVisitor.visitInsn(I2L);
            // Reload right
            methodVisitor.visitVarInsn(LLOAD, tempSlot);
            localVarIndex -= 2;
        } else if (leftType == VarType.LONG && rightType == VarType.INT) {
            // Convert: [long, int] -> [long, long]
            methodVisitor.visitInsn(I2L);
        }
        // If both are already long, no conversion needed
    }
    
    /**
     * Generate comparison bytecode that produces a boolean result (0 or 1)
     * Handles both integer and floating-point comparisons correctly.
     */
    private void generateComparison(int comparisonOpcode, VarType leftType, VarType rightType) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        
        // Check if we're comparing floats/doubles
        if (leftType == VarType.FLOAT || leftType == VarType.DOUBLE || 
            rightType == VarType.FLOAT || rightType == VarType.DOUBLE) {
            // For floating point comparisons, we need DCMPG/DCMPL followed by conditional jump
            // Stack: [left_double, right_double]
            // DCMPG: returns 1 if left > right, 0 if equal, -1 if left < right (or if either is NaN)
            methodVisitor.visitInsn(DCMPG);
            // Stack now: [int_result]
            
            // Map IF_ICMP* to IF* (comparing result to 0)
            int singleOpcode;
            switch (comparisonOpcode) {
                case IF_ICMPEQ: singleOpcode = IFEQ; break;  // result == 0
                case IF_ICMPNE: singleOpcode = IFNE; break;  // result != 0
                case IF_ICMPLT: singleOpcode = IFLT; break;  // result < 0
                case IF_ICMPLE: singleOpcode = IFLE; break;  // result <= 0
                case IF_ICMPGT: singleOpcode = IFGT; break;  // result > 0
                case IF_ICMPGE: singleOpcode = IFGE; break;  // result >= 0
                default: throw new RuntimeException("Unsupported comparison opcode: " + comparisonOpcode);
            }
            
            methodVisitor.visitJumpInsn(singleOpcode, trueLabel);
        } else if (leftType == VarType.LONG || rightType == VarType.LONG) {
            // For long comparisons, we need LCMP followed by conditional jump
            methodVisitor.visitInsn(LCMP);
            // Stack now: [int_result]
            
            // Map IF_ICMP* to IF*
            int singleOpcode;
            switch (comparisonOpcode) {
                case IF_ICMPEQ: singleOpcode = IFEQ; break;
                case IF_ICMPNE: singleOpcode = IFNE; break;
                case IF_ICMPLT: singleOpcode = IFLT; break;
                case IF_ICMPLE: singleOpcode = IFLE; break;
                case IF_ICMPGT: singleOpcode = IFGT; break;
                case IF_ICMPGE: singleOpcode = IFGE; break;
                default: throw new RuntimeException("Unsupported comparison opcode: " + comparisonOpcode);
            }
            
            methodVisitor.visitJumpInsn(singleOpcode, trueLabel);
        } else {
            // Integer comparison - use IF_ICMP* directly
            methodVisitor.visitJumpInsn(comparisonOpcode, trueLabel);
        }
        
        // False case: push 0
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitJumpInsn(GOTO, endLabel);
        
        // True case: push 1
        methodVisitor.visitLabel(trueLabel);
        methodVisitor.visitInsn(ICONST_1);
        
        methodVisitor.visitLabel(endLabel);
    }
    
    /**
     * Generate efficient string concatenation using StringBuilder.
     * Stack on entry: [left_value, right_value]
     * Stack on exit: [result_string]
     */
    private void generateStringConcatenation(VarType leftType, VarType rightType) {
        // Stack on entry: [left, right]
        int savedIdx = localVarIndex;

        // Reserve temp slots for both operands
        int rightSlots = (rightType == VarType.LONG || rightType == VarType.DOUBLE) ? 2 : 1;
        int rightTmp = localVarIndex; // first free slot
        localVarIndex += rightSlots;

        // Store right into temp (pop top)
        switch (rightType) {
            case INT:
            case BOOLEAN:
                methodVisitor.visitVarInsn(ISTORE, rightTmp);
                break;
            case LONG:
                methodVisitor.visitVarInsn(LSTORE, rightTmp);
                break;
            case FLOAT:
            case DOUBLE:
                methodVisitor.visitVarInsn(DSTORE, rightTmp);
                break;
            default:
                methodVisitor.visitVarInsn(ASTORE, rightTmp);
                break;
        }

        // Now stack: [left]
        int leftSlots = (leftType == VarType.LONG || leftType == VarType.DOUBLE) ? 2 : 1;
        int leftTmp = localVarIndex;
        localVarIndex += leftSlots;
        // Store left into temp
        switch (leftType) {
            case INT:
            case BOOLEAN:
                methodVisitor.visitVarInsn(ISTORE, leftTmp);
                break;
            case LONG:
                methodVisitor.visitVarInsn(LSTORE, leftTmp);
                break;
            case FLOAT:
            case DOUBLE:
                methodVisitor.visitVarInsn(DSTORE, leftTmp);
                break;
            default:
                methodVisitor.visitVarInsn(ASTORE, leftTmp);
                break;
        }

        // Create StringBuilder
        methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        // Stack: [StringBuilder]

        // Load left back and append
        switch (leftType) {
            case INT:
            case BOOLEAN:
                methodVisitor.visitVarInsn(ILOAD, leftTmp);
                break;
            case LONG:
                methodVisitor.visitVarInsn(LLOAD, leftTmp);
                break;
            case FLOAT:
            case DOUBLE:
                methodVisitor.visitVarInsn(DLOAD, leftTmp);
                break;
            default:
                methodVisitor.visitVarInsn(ALOAD, leftTmp);
                break;
        }
        appendToStringBuilder(leftType);

        // Load right back and append
        switch (rightType) {
            case INT:
            case BOOLEAN:
                methodVisitor.visitVarInsn(ILOAD, rightTmp);
                break;
            case LONG:
                methodVisitor.visitVarInsn(LLOAD, rightTmp);
                break;
            case FLOAT:
            case DOUBLE:
                methodVisitor.visitVarInsn(DLOAD, rightTmp);
                break;
            default:
                methodVisitor.visitVarInsn(ALOAD, rightTmp);
                break;
        }
        appendToStringBuilder(rightType);

        // Call toString()
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "toString",
            "()Ljava/lang/String;",
            false
        );

        // Restore localVarIndex (drop temps)
        localVarIndex = savedIdx;
        // Stack on exit: [String]
    }
    
    /**
     * Helper: push and fill a vararg array of the given component type with provided expressions.
     * If the list is empty, pushes a zero-length array on the stack.
     */
    private void pushAndFillVarArgArray(Class<?> componentType, java.util.List<Expression> elements) {
        int size = elements.size();
        // Push size
        if (size >= -1 && size <= 5) {
            switch (size) {
                case 0: methodVisitor.visitInsn(ICONST_0); break;
                case 1: methodVisitor.visitInsn(ICONST_1); break;
                case 2: methodVisitor.visitInsn(ICONST_2); break;
                case 3: methodVisitor.visitInsn(ICONST_3); break;
                case 4: methodVisitor.visitInsn(ICONST_4); break;
                case 5: methodVisitor.visitInsn(ICONST_5); break;
            }
        } else if (size <= Byte.MAX_VALUE) {
            methodVisitor.visitIntInsn(BIPUSH, size);
        } else if (size <= Short.MAX_VALUE) {
            methodVisitor.visitIntInsn(SIPUSH, size);
        } else {
            methodVisitor.visitLdcInsn(size);
        }
        
        // Create the array
        if (componentType.isPrimitive()) {
            if (componentType == int.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_INT);
            } else if (componentType == boolean.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_BOOLEAN);
            } else if (componentType == float.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_FLOAT);
            } else if (componentType == long.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_LONG);
            } else if (componentType == double.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_DOUBLE);
            } else if (componentType == short.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_SHORT);
            } else if (componentType == byte.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_BYTE);
            } else if (componentType == char.class) {
                methodVisitor.visitIntInsn(NEWARRAY, T_CHAR);
            } else {
                // Fallback to Object[] if unknown primitive (shouldn't happen)
                methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            }
        } else {
            String internalName = org.objectweb.asm.Type.getInternalName(componentType);
            methodVisitor.visitTypeInsn(ANEWARRAY, internalName);
        }
        
        // Fill the array
        for (int i = 0; i < size; i++) {
            methodVisitor.visitInsn(DUP);
            // index
            if (i >= 0 && i <= 5) {
                switch (i) {
                    case 0: methodVisitor.visitInsn(ICONST_0); break;
                    case 1: methodVisitor.visitInsn(ICONST_1); break;
                    case 2: methodVisitor.visitInsn(ICONST_2); break;
                    case 3: methodVisitor.visitInsn(ICONST_3); break;
                    case 4: methodVisitor.visitInsn(ICONST_4); break;
                    case 5: methodVisitor.visitInsn(ICONST_5); break;
                }
            } else if (i <= Byte.MAX_VALUE) {
                methodVisitor.visitIntInsn(BIPUSH, i);
            } else if (i <= Short.MAX_VALUE) {
                methodVisitor.visitIntInsn(SIPUSH, i);
            } else {
                methodVisitor.visitLdcInsn(i);
            }
            
            // element value
            Expression elem = elements.get(i);
            elem.accept(this);
            // Convert to expected component type if needed
            applyTypeConversion(lastExpressionType, componentType);
            
            // store
            if (componentType.isPrimitive()) {
                if (componentType == int.class) methodVisitor.visitInsn(IASTORE);
                else if (componentType == boolean.class) methodVisitor.visitInsn(BASTORE);
                else if (componentType == float.class) methodVisitor.visitInsn(FASTORE);
                else if (componentType == long.class) methodVisitor.visitInsn(LASTORE);
                else if (componentType == double.class) methodVisitor.visitInsn(DASTORE);
                else if (componentType == short.class) methodVisitor.visitInsn(SASTORE);
                else if (componentType == byte.class) methodVisitor.visitInsn(BASTORE);
                else if (componentType == char.class) methodVisitor.visitInsn(CASTORE);
                else methodVisitor.visitInsn(AASTORE);
            } else {
                methodVisitor.visitInsn(AASTORE);
            }
        }
    }
    
    /**
     * Append a value to StringBuilder on top of stack.
     * Stack on entry: [StringBuilder, value]
     * Stack on exit: [StringBuilder]
     */
    private void appendToStringBuilder(VarType valueType) {
        String descriptor;
        switch (valueType) {
            case INT:
                descriptor = "(I)Ljava/lang/StringBuilder;";
                break;
            case LONG:
                descriptor = "(J)Ljava/lang/StringBuilder;";
                break;
            case FLOAT:
            case DOUBLE:
                descriptor = "(D)Ljava/lang/StringBuilder;";
                break;
            case BOOLEAN:
                descriptor = "(Z)Ljava/lang/StringBuilder;";
                break;
            case STRING:
                descriptor = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
                break;
            case OBJECT:
            case STRING_ARRAY:
            default:
                descriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                break;
        }
        
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            descriptor,
            false
        );
    }
    
    // Ensure helper methods for std::option are generated on the current class
    private void ensureOptionMapHelper() {
        if (optionMapHelperGenerated) return;
        optionMapHelperGenerated = true;
        MethodVisitor mv = classWriter.visitMethod(
            ACC_PRIVATE | ACC_STATIC,
            "$opt_map",
            "(Lfirefly/std/option/Option;Ljava/util/function/Function;)Lfirefly/std/option/Option;",
            null,
            null
        );
        mv.visitCode();
        Label elseLabel = new Label();
        // if (!(opt instanceof Option$Some)) goto else
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(INSTANCEOF, "firefly/std/option/Option$Some");
        mv.visitJumpInsn(IFEQ, elseLabel);
        // return Option.Some(fn.apply(((Option$Some)opt).value0))
        mv.visitVarInsn(ALOAD, 1); // fn
        mv.visitVarInsn(ALOAD, 0); // opt
        mv.visitTypeInsn(CHECKCAST, "firefly/std/option/Option$Some");
        mv.visitFieldInsn(GETFIELD, "firefly/std/option/Option$Some", "value0", "Ljava/lang/Object;");
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitMethodInsn(INVOKESTATIC, "firefly/std/option/Option", "Some", "(Ljava/lang/Object;)Lfirefly/std/option/Option;", false);
        mv.visitInsn(ARETURN);
        // else: return Option.None
        mv.visitLabel(elseLabel);
        mv.visitFieldInsn(GETSTATIC, "firefly/std/option/Option", "None", "Lfirefly/std/option/Option;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0,0);
        mv.visitEnd();
    }

    private void ensureOptionUnwrapOrHelper() {
        if (optionUnwrapOrHelperGenerated) return;
        optionUnwrapOrHelperGenerated = true;
        MethodVisitor mv = classWriter.visitMethod(
            ACC_PRIVATE | ACC_STATIC,
            "$opt_unwrapOr",
            "(Lfirefly/std/option/Option;Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            null
        );
        mv.visitCode();
        Label elseLabel = new Label();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(INSTANCEOF, "firefly/std/option/Option$Some");
        mv.visitJumpInsn(IFEQ, elseLabel);
        // then: return ((Option$Some)opt).value0
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(CHECKCAST, "firefly/std/option/Option$Some");
        mv.visitFieldInsn(GETFIELD, "firefly/std/option/Option$Some", "value0", "Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        // else: return default
        mv.visitLabel(elseLabel);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0,0);
        mv.visitEnd();
    }

    private void ensureOptionIsSomeHelper() {
        if (optionIsSomeHelperGenerated) return;
        optionIsSomeHelperGenerated = true;
        MethodVisitor mv = classWriter.visitMethod(
            ACC_PRIVATE | ACC_STATIC,
            "$opt_isSome",
            "(Lfirefly/std/option/Option;)Z",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(INSTANCEOF, "firefly/std/option/Option$Some");
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0,0);
        mv.visitEnd();
    }
    
    /**
     * Generate bytecode for built-in format function.
     * 
     * Maps to String.format(format, args...).
     * 
     * @param arguments The format string and arguments
     */
    private void generateBuiltinFormatFunction(java.util.List<Expression> arguments) {
        if (arguments.isEmpty()) {
            throw new RuntimeException("format() requires at least a format string argument");
        }
        
        // First argument is the format string
        arguments.get(0).accept(this);
        
        if (arguments.size() == 1) {
            // No format arguments - just return the format string
            // String.format with no args still works but we can optimize
            return;
        }
        
        // Create array for varargs
        int argCount = arguments.size() - 1;
        methodVisitor.visitIntInsn(BIPUSH, argCount);
        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        
        // Fill array with format arguments
        for (int i = 0; i < argCount; i++) {
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitIntInsn(BIPUSH, i);
            
            // Visit the argument
            arguments.get(i + 1).accept(this);
            
            // Box primitives
            switch (lastExpressionType) {
                case INT:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    break;
                case FLOAT:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    break;
                case BOOLEAN:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    break;
                // STRING and OBJECT don't need boxing
            }
            
            methodVisitor.visitInsn(AASTORE);
        }
        
        // Call String.format(String, Object...)
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "java/lang/String",
            "format",
            "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
            false
        );
    }
    
    /**
     * Generate bytecode for built-in print functions (println, print).
     * 
     * These functions are mapped to System.out.println() and System.out.print().
     * 
     * @param functionName The function name ("println" or "print")
     * @param arguments The arguments to print
     */
    private void generateBuiltinPrintFunction(String functionName, java.util.List<Expression> arguments) {
        // Load System.out
        methodVisitor.visitFieldInsn(
            GETSTATIC,
            "java/lang/System",
            "out",
            "Ljava/io/PrintStream;"
        );
        
        // Determine descriptor based on argument type
        String descriptor;
        if (arguments.isEmpty()) {
            // No arguments - println() or print() with empty string
            if (functionName.equals("println")) {
                methodVisitor.visitLdcInsn("");
                descriptor = "(Ljava/lang/String;)V";
            } else {
                // print() with no args does nothing
                methodVisitor.visitInsn(POP); // Pop System.out
                return;
            }
        } else {
            // Visit first argument and determine type
            arguments.get(0).accept(this);
            
            switch (lastExpressionType) {
                case INT:
                    descriptor = "(I)V";
                    break;
                case LONG:
                    descriptor = "(J)V";
                    break;
                case FLOAT:
                    descriptor = "(F)V";
                    break;
                case DOUBLE:
                    descriptor = "(D)V";
                    break;
                case BOOLEAN:
                    descriptor = "(Z)V";
                    break;
                case OBJECT:
                    descriptor = "(Ljava/lang/Object;)V";
                    break;
                default:
                    descriptor = "(Ljava/lang/String;)V";
                    break;
            }
        }
        
        // Call println or print
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/io/PrintStream",
            functionName,
            descriptor,
            false
        );
    }
    
    /**
     * Generate bytecode for built-in spawn function.
     * 
     * spawn(actorClass) creates a new actor instance and returns an ActorRef.
     * 
     * Implementation:
     * 1. Get or create the global ActorSystem singleton
     * 2. Create an instance of the actor class
     * 3. Call ActorSystem.spawn(actor) to start the actor
     * 4. Return the ActorRef
     * 
     * @param arguments The arguments (actor class reference)
     */
    private void generateBuiltinSpawnFunction(java.util.List<Expression> arguments) {
        if (arguments.isEmpty()) {
            throw new RuntimeException("spawn() requires an actor class as argument");
        }
        
        // Get the actor class expression
        Expression actorClassExpr = arguments.get(0);
        
        // Case 1: spawn(ActorClass.class) - class literal
        if (actorClassExpr instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) actorClassExpr;
            if ("class".equals(fieldAccess.getFieldName()) && 
                fieldAccess.getObject() instanceof IdentifierExpr) {
                
                String actorClassName = ((IdentifierExpr) fieldAccess.getObject()).getName();
                
                // Resolve the actor class name
                String resolvedClassName = actorClassName;
                java.util.Optional<String> resolved = typeResolver.resolveClassName(actorClassName);
                if (resolved.isPresent()) {
                    resolvedClassName = resolved.get();
                } else {
                    // Try in current package
                    String packageName = this.className.contains("/") ? 
                        this.className.substring(0, this.className.lastIndexOf("/")) : "";
                    resolvedClassName = packageName.isEmpty() ? actorClassName : packageName + "/" + actorClassName;
                }
                
                String internalClassName = resolvedClassName.replace('.', '/');
                
                // Get or create ActorSystem singleton
                // Call ActorSystemHolder.getInstance()
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "com/firefly/runtime/actor/ActorSystemHolder",
                    "getInstance",
                    "()Lcom/firefly/runtime/actor/Actor$ActorSystem;",
                    false
                );
                
                // Create new actor instance: new ActorClass()
                methodVisitor.visitTypeInsn(NEW, internalClassName);
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(
                    INVOKESPECIAL,
                    internalClassName,
                    "<init>",
                    "()V",
                    false
                );
                
                // Call ActorSystem.spawn(actor)
                // Signature: <State, Message> ActorRef<Message> spawn(Actor<State, Message> actor)
                methodVisitor.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "com/firefly/runtime/actor/Actor$ActorSystem",
                    "spawn",
                    "(Lcom/firefly/runtime/actor/Actor;)Lcom/firefly/runtime/actor/Actor$ActorRef;",
                    false
                );
                
                return;
            }
        }
        
        // Case 2: spawn(actorInstance) - pre-created actor instance
        // Visit the actor expression to get it on the stack
        actorClassExpr.accept(this);
        
        // Get or create ActorSystem singleton
        // We need to swap the order: actorSystem should be receiver, actor should be argument
        // Stack currently: [actor]
        
        // Get ActorSystem instance
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/firefly/runtime/actor/ActorSystemHolder",
            "getInstance",
            "()Lcom/firefly/runtime/actor/Actor$ActorSystem;",
            false
        );
        
        // Stack: [actor, actorSystem]
        // Swap them so actorSystem is receiver
        methodVisitor.visitInsn(SWAP);
        
        // Stack: [actorSystem, actor]
        // Call ActorSystem.spawn(actor)
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/firefly/runtime/actor/Actor$ActorSystem",
            "spawn",
            "(Lcom/firefly/runtime/actor/Actor;)Lcom/firefly/runtime/actor/Actor$ActorRef;",
            false
        );
    }
    @Override 
    public Void visitUnaryExpr(UnaryExpr expr) {
        if (methodVisitor == null) return null;
        
        // Visit the operand first
        expr.getOperand().accept(this);
        VarType operandType = lastExpressionType;
        
        // Generate bytecode based on operator
        switch (expr.getOperator()) {
            case NOT:
                // Logical NOT: !x (for boolean)
                // If x is 0 (false), push 1 (true); if x is 1 (true), push 0 (false)
                Label trueLabel = new Label();
                Label endLabel = new Label();
                
                // If operand is 0, jump to true
                methodVisitor.visitJumpInsn(IFEQ, trueLabel);
                
                // Operand was 1 (true), push 0 (false)
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitJumpInsn(GOTO, endLabel);
                
                // Operand was 0 (false), push 1 (true)
                methodVisitor.visitLabel(trueLabel);
                methodVisitor.visitInsn(ICONST_1);
                
                methodVisitor.visitLabel(endLabel);
                lastExpressionType = VarType.BOOLEAN;
                break;
                
            case MINUS:
                // Arithmetic negation: -x
                switch (operandType) {
                    case INT:
                        methodVisitor.visitInsn(INEG);
                        lastExpressionType = VarType.INT;
                        break;
                    case LONG:
                        methodVisitor.visitInsn(LNEG);
                        lastExpressionType = VarType.LONG;
                        break;
                    case FLOAT:
                        methodVisitor.visitInsn(FNEG);
                        lastExpressionType = VarType.FLOAT;
                        break;
                    case DOUBLE:
                        methodVisitor.visitInsn(DNEG);
                        lastExpressionType = VarType.DOUBLE;
                        break;
                    default:
                        throw new RuntimeException("Cannot negate non-numeric type: " + operandType);
                }
                break;
            
            case DEREF:
                // *x - dereference pointer/reference
                // In JVM, this is essentially a no-op since we don't have explicit pointers
                // The value is already on the stack
                // Could add runtime check for Reference wrapper if needed
                lastExpressionType = VarType.OBJECT;
                break;
            
            case REF:
                // &x - create immutable reference
                // Wrap value in Reference object for explicit reference semantics
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "com/firefly/runtime/Reference",
                    "of",
                    "(Ljava/lang/Object;)Lcom/firefly/runtime/Reference;",
                    false
                );
                lastExpressionType = VarType.OBJECT;
                break;
            
            case MUT_REF:
                // &mut x - create mutable reference
                // Wrap value in MutableReference object
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "com/firefly/runtime/MutableReference",
                    "of",
                    "(Ljava/lang/Object;)Lcom/firefly/runtime/MutableReference;",
                    false
                );
                lastExpressionType = VarType.OBJECT;
                break;
            
            case UNWRAP:
                // x? - unwrap optional, returns value or null
                // Call Optional.orElse(null) or similar
                methodVisitor.visitInsn(ACONST_NULL);
                methodVisitor.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/util/Optional",
                    "orElse",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false
                );
                lastExpressionType = VarType.OBJECT;
                break;
            
            case FORCE_UNWRAP:
                // x!! - force unwrap, throws if null/empty
                // Call Optional.get() which throws NoSuchElementException if empty
                Label notNull = new Label();
                
                // Duplicate value for null check
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitJumpInsn(IFNONNULL, notNull);
                
                // Value is null, throw NullPointerException
                methodVisitor.visitTypeInsn(NEW, "java/lang/NullPointerException");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitLdcInsn("Force unwrap failed: value is null");
                methodVisitor.visitMethodInsn(
                    INVOKESPECIAL,
                    "java/lang/NullPointerException",
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
                );
                methodVisitor.visitInsn(ATHROW);
                
                // Not null, continue with value
                methodVisitor.visitLabel(notNull);
                lastExpressionType = VarType.OBJECT;
                break;
            
            case AWAIT:
                // .await - this is handled separately in visitAwaitExpr
                // But if we get here, treat it like await
                methodVisitor.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "com/firefly/runtime/async/Future",
                    "get",
                    "()Ljava/lang/Object;",
                    false
                );
                lastExpressionType = VarType.OBJECT;
                break;
                
            default:
                // All unary operators should be handled above
                // Log warning - unary operator not implemented
                System.err.println("Warning: Unary operator not implemented: " + expr.getOperator());
                lastExpressionType = VarType.OBJECT;
                break;
        }
        
        return null;
    }
    @Override 
    public Void visitCallExpr(CallExpr expr) {
        if (methodVisitor == null) return null;
        
        // Special handling for imported ADT variant factories like Some(x), None, Ok(x), Err(x)
        if (expr.getFunction() instanceof IdentifierExpr) {
            String name = ((IdentifierExpr) expr.getFunction()).getName();

            // Inline implementations for common std::option helpers: map, unwrapOr, isSome
            if ("map".equals(name) && expr.getArguments().size() == 2) {
                // Generate or ensure helper, then call: $opt_map(Option, Function)
                ensureOptionMapHelper();
                expr.getArguments().get(0).accept(this); // Option
                expr.getArguments().get(1).accept(this); // Function
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    className,
                    "$opt_map",
                    "(Lfirefly/std/option/Option;Ljava/util/function/Function;)Lfirefly/std/option/Option;",
                    false
                );
                lastExpressionType = VarType.OBJECT;
                lastCallWasVoid = false;
                return null;
            }
            if ("unwrapOr".equals(name) && expr.getArguments().size() == 2) {
                // Call helper: $opt_unwrapOr(Option, Object) -> Object
                ensureOptionUnwrapOrHelper();
                expr.getArguments().get(0).accept(this); // Option
                expr.getArguments().get(1).accept(this); // default
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    className,
                    "$opt_unwrapOr",
                    "(Lfirefly/std/option/Option;Ljava/lang/Object;)Ljava/lang/Object;",
                    false
                );
                lastExpressionType = VarType.OBJECT;
                lastCallWasVoid = false;
                return null;
            }
            if ("isSome".equals(name) && expr.getArguments().size() == 1) {
                ensureOptionIsSomeHelper();
                expr.getArguments().get(0).accept(this); // Option
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    className,
                    "$opt_isSome",
                    "(Lfirefly/std/option/Option;)Z",
                    false
                );
                lastExpressionType = VarType.BOOLEAN;
                lastCallWasVoid = false;
                return null;
            }

            String outerInternal = null;
            if ("Some".equals(name) || "None".equals(name)) {
                outerInternal = "firefly/std/option/Option";
            } else if ("Ok".equals(name) || "Err".equals(name)) {
                outerInternal = "firefly/std/result/Result";
            }
            if (outerInternal != null) {
                // Build descriptor from argument expressions
                StringBuilder desc = new StringBuilder("(");
                for (Expression arg : expr.getArguments()) {
                    arg.accept(this);
                    // Box primitives to match reference types in factory signature
                    switch (lastExpressionType) {
                        case INT:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                            break;
                        case LONG:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                            break;
                        case FLOAT:
                        case DOUBLE:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                            break;
                        case BOOLEAN:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                            break;
                        default:
                            // already object
                            break;
                    }
                    // All factory parameters are emitted as Object for now
                    desc.append("Ljava/lang/Object;");
                }
                desc.append(")L").append(outerInternal).append(";");
                methodVisitor.visitMethodInsn(INVOKESTATIC, outerInternal, name, desc.toString(), false);
                lastExpressionType = VarType.OBJECT;
                lastCallWasVoid = false;
                return null;
            }
        }
        
        // Handle method calls: ClassName.method(args) or object.method(args)
        if (expr.getFunction() instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expr.getFunction();
            String methodName = fieldAccess.getFieldName();
            
            // SPECIAL CASE: self.method() (Flylang instance method on current class)
            if (fieldAccess.getObject() instanceof IdentifierExpr &&
                "self".equals(((IdentifierExpr) fieldAccess.getObject()).getName())) {
                String descriptor = functionSignatures.get(methodName);
                if (descriptor == null) {
                    System.err.println("Warning: Method " + methodName + " not found in function signatures");
                    lastExpressionType = VarType.OBJECT;
                    return null;
                }
                methodVisitor.visitVarInsn(ALOAD, 0);
                // Ensure correct receiver type for verifier when 'self' is captured as Object in lambdas
                methodVisitor.visitTypeInsn(CHECKCAST, className);
                for (Expression arg : expr.getArguments()) {
                    arg.accept(this);
                }
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, className, methodName, descriptor, false);
                String returnTypeDesc = descriptor.substring(descriptor.indexOf(')') + 1);
                lastCallWasVoid = "V".equals(returnTypeDesc);
                if (!lastCallWasVoid) {
                    lastExpressionType = getVarTypeFromDescriptor(returnTypeDesc);
                }
                return null;
            }
            
            // STATIC CALLS: IdentifierExpr treated as class name (only if it resolves to a class)
            if (fieldAccess.getObject() instanceof IdentifierExpr) {
                String objectName = ((IdentifierExpr) fieldAccess.getObject()).getName();
                
                // Special-case: Future.any/all varargs helpers for robustness
                if ("Future".equals(objectName) && ("any".equals(methodName) || "all".equals(methodName))) {
                    // Create array [Lcom/firefly/runtime/async/Future; from arguments
                    int num = expr.getArguments().size();
                    methodVisitor.visitIntInsn(BIPUSH, num);
                    methodVisitor.visitTypeInsn(ANEWARRAY, "com/firefly/runtime/async/Future");
                    for (int i = 0; i < num; i++) {
                        methodVisitor.visitInsn(DUP);
                        if (i <= 5) {
                            switch (i) { case 0: methodVisitor.visitInsn(ICONST_0); break; case 1: methodVisitor.visitInsn(ICONST_1); break; case 2: methodVisitor.visitInsn(ICONST_2); break; case 3: methodVisitor.visitInsn(ICONST_3); break; case 4: methodVisitor.visitInsn(ICONST_4); break; default: methodVisitor.visitInsn(ICONST_5); }
                        } else if (i <= Byte.MAX_VALUE) {
                            methodVisitor.visitIntInsn(BIPUSH, i);
                        } else if (i <= Short.MAX_VALUE) {
                            methodVisitor.visitIntInsn(SIPUSH, i);
                        } else {
                            methodVisitor.visitLdcInsn(i);
                        }
                        expr.getArguments().get(i).accept(this);
                        methodVisitor.visitInsn(AASTORE);
                    }
                    String mdesc = "([Lcom/firefly/runtime/async/Future;)Lcom/firefly/runtime/async/Future;";
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "com/firefly/runtime/async/Future", methodName, mdesc, false);
                    lastExpressionType = VarType.OBJECT;
                    lastCallWasVoid = false;
                    return null;
                }
                
                // Special-case: Thread.sleep(int/long)
                if ("Thread".equals(objectName) && "sleep".equals(methodName) && expr.getArguments().size() == 1) {
                    Expression arg = expr.getArguments().get(0);
                    arg.accept(this);
                    switch (lastExpressionType) {
                        case INT:
                            methodVisitor.visitInsn(I2L);
                            break;
                        case LONG:
                            break;
                        case OBJECT:
                            // Attempt unboxing from Long
                            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                            break;
                        default:
                            // Force convert int-like to long
                            methodVisitor.visitInsn(I2L);
                            break;
                    }
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "sleep", "(J)V", false);
                    lastCallWasVoid = true;
                    lastExpressionType = null;
                    return null;
                }
                
                java.util.Optional<String> resolvedClass = typeResolver.resolveClassName(objectName);
                if (resolvedClass.isPresent()) {
                    // Resolve argument types
                    java.util.List<Class<?>> argTypes = new java.util.ArrayList<>();
                    for (Expression arg : expr.getArguments()) {
                        argTypes.add(inferExpressionType(arg));
                    }
                    
                    // Prefer full resolver (handles overloading + varargs)
                    java.util.Optional<MethodResolver.MethodCandidate> staticMethod = 
                        methodResolver.resolveStaticMethod(objectName, methodName, argTypes);
                    
                    if (staticMethod.isPresent()) {
                        MethodResolver.MethodCandidate candidate = staticMethod.get();
                        Class<?>[] paramTypes = candidate.method.getParameterTypes();
                        boolean isVarArgs = candidate.isVarArgs();
                        int totalArgs = expr.getArguments().size();
                        int fixedParams = isVarArgs ? Math.max(0, paramTypes.length - 1) : paramTypes.length;
                        
                        // Fixed params
                        for (int i = 0; i < Math.min(fixedParams, totalArgs); i++) {
                            Expression arg = expr.getArguments().get(i);
                            arg.accept(this);
                            applyTypeConversion(lastExpressionType, paramTypes[i]);
                        }
                        
                        // Varargs tail
                        if (isVarArgs) {
                            Class<?> arrayParamType = paramTypes[paramTypes.length - 1];
                            Class<?> componentType = arrayParamType.getComponentType();
                            int varArgCount = Math.max(0, totalArgs - fixedParams);
                            if (varArgCount == 1) {
                                Class<?> lastArgType = inferExpressionType(expr.getArguments().get(fixedParams));
                                if (arrayParamType.isAssignableFrom(lastArgType)) {
                                    Expression lastArg = expr.getArguments().get(fixedParams);
                                    lastArg.accept(this);
                                    if (!lastArgType.equals(arrayParamType) && arrayParamType.isArray()) {
                                        String castType = org.objectweb.asm.Type.getDescriptor(arrayParamType);
                                        methodVisitor.visitTypeInsn(CHECKCAST, castType);
                                    }
                                } else {
                                    pushAndFillVarArgArray(componentType, java.util.Collections.singletonList(expr.getArguments().get(fixedParams)));
                                }
                            } else {
                                java.util.List<Expression> varArgExprs = new java.util.ArrayList<>();
                                for (int i = fixedParams; i < totalArgs; i++) varArgExprs.add(expr.getArguments().get(i));
                                pushAndFillVarArgArray(componentType, varArgExprs);
                            }
                        } else {
                            for (int i = fixedParams; i < totalArgs; i++) {
                                Expression arg = expr.getArguments().get(i);
                                arg.accept(this);
                                if (i < paramTypes.length) applyTypeConversion(lastExpressionType, paramTypes[i]);
                            }
                        }
                        
                        methodVisitor.visitMethodInsn(
                            INVOKESTATIC,
                            candidate.getInternalClassName(),
                            candidate.method.getName(),
                            candidate.getDescriptor(),
                            false
                        );
                        Class<?> returnType = candidate.method.getReturnType();
                        lastCallWasVoid = returnType.equals(void.class);
                        lastExpressionType = getVarTypeFromClass(returnType);
                        lastExpressionClass = returnType;
                        return null;
                    }
                    
                    // Fallback: allow static method on current class when not yet loadable
                    String currentSimple = className.contains("/") ? className.substring(className.lastIndexOf('/') + 1) : className;
                    if (objectName.equals(currentSimple)) {
                        String desc = functionSignatures.get(methodName);
                        if (desc != null) {
                            // Push arguments
                            for (Expression arg : expr.getArguments()) {
                                arg.accept(this);
                            }
                            methodVisitor.visitMethodInsn(INVOKESTATIC, className, methodName, desc, false);
                            String returnTypeDesc = desc.substring(desc.indexOf(')') + 1);
                            lastCallWasVoid = "V".equals(returnTypeDesc);
                            if (!lastCallWasVoid) {
                                lastExpressionType = getVarTypeFromDescriptor(returnTypeDesc);
                            }
                            return null;
                        }
                    }
                    
                    // No suitable static method found
                    throw new RuntimeException("Cannot resolve static method: " + objectName + "::" + methodName + " with " + expr.getArguments().size() + " argument(s)");
                } else if (structRegistry.containsKey(objectName)) {
                    // Static call on locally-defined data/struct base class (e.g., Maybe::Some(...))
                    String baseInternal = resolveStructInternalName(objectName);
                    // Prefer exact descriptor from variant metadata if available
                    StructMetadata variantMeta = structRegistry.get(methodName);
                    StringBuilder mdesc = new StringBuilder("(");
                    java.util.List<com.firefly.compiler.ast.type.Type> expectedTypes = new java.util.ArrayList<>();
                    if (variantMeta != null && !variantMeta.fields.isEmpty()) {
                        for (StructMetadata.FieldMetadata fm : variantMeta.fields) {
                            expectedTypes.add(fm.type);
                            mdesc.append(getTypeDescriptor(fm.type));
                        }
                    } else {
                        // Fallback to inferred types
                        for (Expression arg : expr.getArguments()) {
                            Class<?> t = inferExpressionType(arg);
                            if (t == int.class || t == boolean.class) mdesc.append('I');
                            else if (t == long.class) mdesc.append('J');
                            else if (t == double.class || t == float.class) mdesc.append('D');
                            else if (t == java.lang.String.class) mdesc.append("Ljava/lang/String;");
                            else mdesc.append("Ljava/lang/Object;");
                            expectedTypes.add(null);
                        }
                    }
                    mdesc.append(")L").append(baseInternal).append(";");
                    // Push arguments converting to expected types when known
                    for (int i = 0; i < expr.getArguments().size(); i++) {
                        Expression arg = expr.getArguments().get(i);
                        arg.accept(this);
                        com.firefly.compiler.ast.type.Type et = i < expectedTypes.size() ? expectedTypes.get(i) : null;
                        if (et != null) {
                            // Apply widening conversions to match expected JVM type
                            convertToFieldType(lastExpressionType, et);
                        } else {
                            // Ensure numeric widening if inferred float
                            if (lastExpressionType == VarType.FLOAT) methodVisitor.visitInsn(F2D);
                        }
                    }
                    methodVisitor.visitMethodInsn(INVOKESTATIC, baseInternal, methodName, mdesc.toString(), false);
                    lastExpressionType = VarType.OBJECT;
                    lastCallWasVoid = false;
                    return null;
                }
            }
            
            // Instance method call: resolve on receiver object
            Class<?> receiverType = inferExpressionType(fieldAccess.getObject());
            
            java.util.List<Class<?>> argTypes = new java.util.ArrayList<>();
            for (Expression arg : expr.getArguments()) argTypes.add(inferExpressionType(arg));
            
            java.util.Optional<MethodResolver.MethodCandidate> instanceMethod = 
                methodResolver.resolveInstanceMethod(receiverType, methodName, argTypes);
            
            // If method not found on Object, try String as fallback (common case: lambda parameters erased to Object)
            if (!instanceMethod.isPresent() && receiverType == Object.class) {
                instanceMethod = methodResolver.resolveInstanceMethod(String.class, methodName, argTypes);
                if (instanceMethod.isPresent()) {
                    receiverType = String.class;
                }
            }
            
            if (instanceMethod.isPresent()) {
                MethodResolver.MethodCandidate candidate = instanceMethod.get();
                
                fieldAccess.getObject().accept(this);
                // Insert cast so the verifier sees the exact receiver class
                methodVisitor.visitTypeInsn(CHECKCAST, candidate.getInternalClassName());
                
                Class<?>[] paramTypes = candidate.method.getParameterTypes();
                boolean isVarArgs = candidate.isVarArgs();
                int totalArgs = expr.getArguments().size();
                int fixedParams = isVarArgs ? Math.max(0, paramTypes.length - 1) : paramTypes.length;
                
                for (int i = 0; i < Math.min(fixedParams, totalArgs); i++) {
                    Expression arg = expr.getArguments().get(i);
                    arg.accept(this);
                    applyTypeConversion(lastExpressionType, paramTypes[i]);
                }
                if (isVarArgs) {
                    Class<?> arrayParamType = paramTypes[paramTypes.length - 1];
                    Class<?> componentType = arrayParamType.getComponentType();
                    int varArgCount = Math.max(0, totalArgs - fixedParams);
                    if (varArgCount == 1) {
                        Class<?> lastArgType = inferExpressionType(expr.getArguments().get(fixedParams));
                        if (arrayParamType.isAssignableFrom(lastArgType)) {
                            Expression lastArg = expr.getArguments().get(fixedParams);
                            lastArg.accept(this);
                            if (!lastArgType.equals(arrayParamType) && arrayParamType.isArray()) {
                                String castType = org.objectweb.asm.Type.getDescriptor(arrayParamType);
                                methodVisitor.visitTypeInsn(CHECKCAST, castType);
                            }
                        } else {
                            pushAndFillVarArgArray(componentType, java.util.Collections.singletonList(expr.getArguments().get(fixedParams)));
                        }
                    } else {
                        java.util.List<Expression> varArgExprs = new java.util.ArrayList<>();
                        for (int i = fixedParams; i < totalArgs; i++) varArgExprs.add(expr.getArguments().get(i));
                        pushAndFillVarArgArray(componentType, varArgExprs);
                    }
                } else {
                    for (int i = fixedParams; i < totalArgs; i++) {
                        Expression arg = expr.getArguments().get(i);
                        arg.accept(this);
                        if (i < paramTypes.length) applyTypeConversion(lastExpressionType, paramTypes[i]);
                    }
                }
                
                methodVisitor.visitMethodInsn(
                    INVOKEVIRTUAL,
                    candidate.getInternalClassName(),
                    candidate.method.getName(),
                    candidate.getDescriptor(),
                    false
                );
                Class<?> returnType = candidate.method.getReturnType();
                lastCallWasVoid = returnType.equals(void.class);
                lastExpressionType = getVarTypeFromClass(returnType);
                lastExpressionClass = returnType;
            } else {
                // Firefly-class-aware fallback
                if (fieldAccess.getObject() instanceof IdentifierExpr) {
                    String objName = ((IdentifierExpr) fieldAccess.getObject()).getName();
                    String declaredDotted = localVariableDeclaredTypes.get(objName);
                    if (declaredDotted != null) {
                        String internalName = declaredDotted.contains(".")
                            ? declaredDotted.replace('.', '/')
                            : (structRegistry.containsKey(declaredDotted)
                                ? resolveStructInternalName(declaredDotted)
                                : ((moduleBasePath != null && !moduleBasePath.isEmpty()) ? moduleBasePath + "/" + declaredDotted : declaredDotted));
                        java.util.Optional<Class<?>> declaredClassOpt = typeResolver.getClass(declaredDotted);
                        if (declaredClassOpt.isPresent()) {
                            java.util.List<Class<?>> declArgTypes = new java.util.ArrayList<>();
                            for (Expression arg : expr.getArguments()) declArgTypes.add(inferExpressionType(arg));
                            java.util.Optional<MethodResolver.MethodCandidate> cand = methodResolver.resolveInstanceMethod(declaredClassOpt.get(), methodName, declArgTypes);
                            if (cand.isPresent()) {
                                fieldAccess.getObject().accept(this);
                                // Ensure receiver is of the declared type for verifier
                                methodVisitor.visitTypeInsn(CHECKCAST, cand.get().getInternalClassName());
                                Class<?>[] paramTypes = cand.get().method.getParameterTypes();
                                int idx = 0;
                                for (Expression arg : expr.getArguments()) {
                                    arg.accept(this);
                                    if (idx < paramTypes.length) applyTypeConversion(lastExpressionType, paramTypes[idx]);
                                    idx++;
                                }
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, cand.get().getInternalClassName(), cand.get().method.getName(), cand.get().getDescriptor(), false);
                                Class<?> returnType = cand.get().method.getReturnType();
                                lastCallWasVoid = returnType.equals(void.class);
                                lastExpressionType = getVarTypeFromClass(returnType);
                                lastExpressionClass = returnType;
                                return null;
                            }
                        }
                        fieldAccess.getObject().accept(this);
                        // Cast receiver to the declared/internal type before invoke
                        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
                        for (Expression arg : expr.getArguments()) arg.accept(this);
                        String descriptor = functionSignatures.get(methodName);
                        if (descriptor != null) {
                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, internalName, methodName, descriptor, false);
                            String returnTypeDesc = descriptor.substring(descriptor.indexOf(')') + 1);
                            lastCallWasVoid = "V".equals(returnTypeDesc);
                            lastExpressionType = lastCallWasVoid ? null : getVarTypeFromDescriptor(returnTypeDesc);
                            return null;
                        }
                    }
                }
                
                if ("get".equals(methodName) && expr.getArguments().isEmpty()) {
                    fieldAccess.getObject().accept(this);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "com/firefly/runtime/async/Future", "get", "()Ljava/lang/Object;", false);
                    lastExpressionType = VarType.OBJECT;
                    lastCallWasVoid = false;
                    return null;
                }
                
                fieldAccess.getObject().accept(this);
                for (Expression arg : expr.getArguments()) arg.accept(this);
                StringBuilder argDescriptor = new StringBuilder("(");
                for (int i = 0; i < expr.getArguments().size(); i++) argDescriptor.append("Ljava/lang/Object;");
                argDescriptor.append(")Ljava/lang/Object;");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", methodName, argDescriptor.toString(), false);
                lastExpressionType = VarType.OBJECT;
                lastCallWasVoid = false;
            }
            
        } else if (expr.getFunction() instanceof IdentifierExpr) {
            String funcName = ((IdentifierExpr) expr.getFunction()).getName();
            
            if (funcName.equals("println") || funcName.equals("print")) {
                generateBuiltinPrintFunction(funcName, expr.getArguments());
                lastCallWasVoid = true;
                return null;
            } else if (funcName.equals("format")) {
                generateBuiltinFormatFunction(expr.getArguments());
                lastCallWasVoid = false;
                lastExpressionType = VarType.STRING;
                return null;
            } else if (funcName.equals("spawn")) {
                generateBuiltinSpawnFunction(expr.getArguments());
                lastCallWasVoid = false;
                lastExpressionType = VarType.OBJECT;
                return null;
            } else if (functionSignatures.containsKey(funcName)) {
                String descriptor = functionSignatures.get(funcName);
                lastCallWasVoid = descriptor.endsWith("V");
                for (Expression arg : expr.getArguments()) arg.accept(this);
                methodVisitor.visitMethodInsn(INVOKESTATIC, className, funcName, descriptor, false);
                if (!lastCallWasVoid) {
                    String returnTypeDesc = descriptor.substring(descriptor.indexOf(')') + 1);
                    lastExpressionType = getVarTypeFromDescriptor(returnTypeDesc);
                }
            } else if (localVariables.containsKey(funcName)) {
                Integer varIndex = localVariables.get(funcName);
                methodVisitor.visitVarInsn(ALOAD, varIndex);
                int argCount = expr.getArguments().size();
                for (Expression arg : expr.getArguments()) {
                    arg.accept(this);
                    switch (lastExpressionType) {
                        case INT:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                            break;
                        case BOOLEAN:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                            break;
                        case FLOAT:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                            break;
                        case DOUBLE:
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                            break;
                    }
                }
                String functionalInterface;
                String mName;
                String mDesc;
                switch (argCount) {
                    case 0:
                        functionalInterface = "java/util/function/Supplier";
                        mName = "get";
                        mDesc = "()Ljava/lang/Object;";
                        break;
                    case 1:
                        functionalInterface = "java/util/function/Function";
                        mName = "apply";
                        mDesc = "(Ljava/lang/Object;)Ljava/lang/Object;";
                        break;
                    case 2:
                        functionalInterface = "java/util/function/BiFunction";
                        mName = "apply";
                        mDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                        break;
                    default:
                        functionalInterface = "java/util/function/Function";
                        mName = "apply";
                        mDesc = "(Ljava/lang/Object;)Ljava/lang/Object;";
                        break;
                }
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, functionalInterface, mName, mDesc, true);
                lastExpressionType = VarType.OBJECT;
                lastCallWasVoid = false;
            }
        }
        return null;
    }
    @Override 
    public Void visitFieldAccessExpr(FieldAccessExpr expr) {
        if (methodVisitor == null) return null;
        
        // Check if this is a class literal (e.g., Application.class)
        if ("class".equals(expr.getFieldName()) && expr.getObject() instanceof IdentifierExpr) {
            String className = ((IdentifierExpr) expr.getObject()).getName();
            
            // Try to resolve the class name
            java.util.Optional<String> resolvedClass = typeResolver.resolveClassName(className);
            if (resolvedClass.isPresent()) {
                // Load the Class object
                String classDescriptor = "L" + resolvedClass.get().replace('.', '/') + ";";
                methodVisitor.visitLdcInsn(org.objectweb.asm.Type.getType(classDescriptor));
                lastExpressionType = VarType.OBJECT;
                return null;
            }
            
            // If not resolved, try as a type in current package
            String packageName = this.className.contains("/") ? 
                this.className.substring(0, this.className.lastIndexOf("/")) : "";
            String fullClassName = packageName.isEmpty() ? className : packageName + "/" + className;
            String classDescriptor = "L" + fullClassName + ";";
            methodVisitor.visitLdcInsn(org.objectweb.asm.Type.getType(classDescriptor));
            lastExpressionType = VarType.OBJECT;
            return null;
        }
        
        // Check if this is static field/method access (ClassName::member)
        // This happens when the object is an IdentifierExpr with a class name (starts with uppercase)
        if (expr.getObject() instanceof IdentifierExpr) {
            String objectName = ((IdentifierExpr) expr.getObject()).getName();
            String fieldName = expr.getFieldName();
            
            // First: handle local data/struct types generated in this unit
            if (structRegistry.containsKey(objectName)) {
                String baseInternal = resolveStructInternalName(objectName);
                // Try GETSTATIC base.field (for data nullary variants or static fields)
                methodVisitor.visitFieldInsn(GETSTATIC, baseInternal, fieldName, "L" + baseInternal + ";");
                lastExpressionType = VarType.OBJECT;
                return null;
            }
            
            // Check if objectName looks like a class (starts with uppercase)
            if (objectName.length() > 0 && Character.isUpperCase(objectName.charAt(0))) {
                // This is likely static access: ClassName::member
                // Try to resolve the class
                java.util.Optional<String> resolvedClass = typeResolver.resolveClassName(objectName);
                
                if (resolvedClass.isPresent()) {
                    String fullClassName = resolvedClass.get().replace('.', '/');
                    
                    // For now, we'll handle this as a static field access
                    // The proper way would be to check if it's followed by a method call
                    // But we'll emit GETSTATIC and let the CallExpr visitor handle the method call
                    
                    // Note: This will be handled properly when it's part of a CallExpr
                    // For standalone field access like Integer::MAX_VALUE, emit GETSTATIC
                    try {
                        // Try to load the class and find the field
                        Class<?> clazz = Class.forName(resolvedClass.get());
                        java.lang.reflect.Field field = clazz.getField(fieldName);
                        String fieldDescriptor = org.objectweb.asm.Type.getDescriptor(field.getType());
                        
                        methodVisitor.visitFieldInsn(
                            GETSTATIC,
                            fullClassName,
                            fieldName,
                            fieldDescriptor
                        );
                        
                        // Set appropriate type based on field
                        if (field.getType() == int.class) {
                            lastExpressionType = VarType.INT;
                        } else if (field.getType() == long.class) {
                            lastExpressionType = VarType.LONG;
                        } else if (field.getType() == float.class) {
                            lastExpressionType = VarType.FLOAT;
                        } else if (field.getType() == double.class) {
                            lastExpressionType = VarType.DOUBLE;
                        } else if (field.getType() == boolean.class) {
                            lastExpressionType = VarType.BOOLEAN;
                        } else {
                            lastExpressionType = VarType.OBJECT;
                        }
                        return null;
                    } catch (Exception e) {
                        // Field not found or other error, might be a method reference
                        // Store info for CallExpr to use
                        lastExpressionType = VarType.OBJECT;
                        // Don't emit anything here - CallExpr will handle it
                        return null;
                    }
                }
            }
        }
        
        // Regular field access - visit the object
        expr.getObject().accept(this);
        VarType objectType = lastExpressionType;
        
        // Special case: .length on arrays/collections -> call size() method
        if ("length".equals(expr.getFieldName()) && objectType == VarType.OBJECT) {
            // For PersistentVector, call size() method
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "com/firefly/runtime/collections/PersistentVector",
                "size",
                "()I",
                false
            );
            lastExpressionType = VarType.INT;
            return null;
        }
        
        // Try to infer the struct type from the object expression
        String structTypeName = inferStructType(expr.getObject());
        
        if (structTypeName != null) {
            // Resolve internal class name
            String structInternalName = resolveStructInternalName(structTypeName);
            // Look up struct metadata
            StructMetadata structMeta = structRegistry.get(structTypeName);
            
            if (structMeta != null) {
                String fieldName = expr.getFieldName();
                
                // Find the field in struct metadata
                StructMetadata.FieldMetadata fieldMeta = null;
                for (StructMetadata.FieldMetadata fm : structMeta.fields) {
                    if (fm.name.equals(fieldName)) {
                        fieldMeta = fm;
                        break;
                    }
                }
                
                if (fieldMeta != null) {
                    // Generate getter method call
                    String getterDescriptor = "()" + getTypeDescriptor(fieldMeta.type);
                    
                    methodVisitor.visitMethodInsn(
                        INVOKEVIRTUAL,
                        structInternalName,
                        (getVarTypeFromType(fieldMeta.type) == VarType.BOOLEAN
                            ? (fieldName.startsWith("is") ? fieldName : "is" + capitalize(fieldName))
                            : ("get" + capitalize(fieldName))),
                        getterDescriptor,
                        false
                    );
                    
                    // Set the expression type based on field type
                    lastExpressionType = getVarTypeFromType(fieldMeta.type);
                    return null;
                }
            }
        }
        
        // Regular instance field access using GETFIELD
        // Object is already on stack from line 4969
        String fieldName = expr.getFieldName();
        String ownerClass = this.className;
        if (currentEnclosingClass != null) {
            ownerClass = currentEnclosingClass;
        }
        
        // Get field descriptor from registered field types
        String fieldDescriptor = currentClassFieldTypes.getOrDefault(fieldName, "Ljava/lang/Object;");
        VarType fieldType = VarType.OBJECT;
        
        // Determine VarType from descriptor
        switch (fieldDescriptor) {
            case "I": fieldType = VarType.INT; break;
            case "J": fieldType = VarType.LONG; break;
            case "F": fieldType = VarType.DOUBLE; break;
            case "D": fieldType = VarType.DOUBLE; break;
            case "Z": fieldType = VarType.BOOLEAN; break;
            case "Ljava/lang/String;": fieldType = VarType.STRING; break;
            case "[Ljava/lang/String;": fieldType = VarType.STRING_ARRAY; break;
            default: fieldType = VarType.OBJECT; break;
        }
        
        // Generate GETFIELD instruction
        methodVisitor.visitFieldInsn(GETFIELD, ownerClass, fieldName, fieldDescriptor);
        lastExpressionType = fieldType;
        return null;
    }
    
    /**
     * Infer the struct type name from an expression.
     * Returns null if type cannot be inferred.
     */
    private String inferStructType(Expression expr) {
        // If it's a variable, check its type in local variables
        if (expr instanceof IdentifierExpr) {
            String varName = ((IdentifierExpr) expr).getName();
            
            // Check if we have type information for this variable
            // For now, we'll check the struct registry for all known structs
            // and see if the variable type matches
            
            // Try to get type from parameter types if available
            if (currentFunctionParams.containsKey(varName)) {
                return currentFunctionParams.get(varName);
            }
            
            // Try to infer from local variable types
            // For simplicity, iterate through struct registry to find matches
            for (String structName : structRegistry.keySet()) {
                // This is a heuristic - in a full implementation, we'd have
                // proper type tracking for all variables
                // For now, assume if the variable is typed as Object and
                // we have only one struct, use that struct
                VarType varType = localVariableTypes.get(varName);
                if (varType == VarType.OBJECT) {
                    // Return the struct name - this is a simplification
                    // In production code, we'd have actual type information
                    return structName;
                }
            }
        }
        
        // If it's a constructor call, get the type from that
        if (expr instanceof CallExpr) {
            CallExpr callExpr = (CallExpr) expr;
            if (callExpr.getFunction() instanceof IdentifierExpr) {
                String typeName = ((IdentifierExpr) callExpr.getFunction()).getName();
                // Check if this is a known struct
                if (structRegistry.containsKey(typeName)) {
                    return typeName;
                }
            }
        }
        
        return null;
    }
    @Override 
    public Void visitIndexAccessExpr(IndexAccessExpr expr) {
        if (methodVisitor == null) return null;
        
        // Visit the array/list expression
        expr.getObject().accept(this);
        VarType arrayType = lastExpressionType;
        
        // Visit the index expression
        expr.getIndex().accept(this);
        VarType indexType = lastExpressionType;
        
        // Check if we're accessing a native array or a collection
        if (arrayType == VarType.STRING_ARRAY) {
            // Native array access: arr[i]
            // Stack: [array, index]
            methodVisitor.visitInsn(AALOAD);  // Load element from Object array
            lastExpressionType = VarType.STRING;
        } else if (arrayType == VarType.OBJECT) {
            // For Firefly arrays (PersistentVector), call get(int) -> Object
            // Stack: [vector, index]
            
            // The index is already on the stack as int
            // Call PersistentVector.get(int) -> Object (virtual method, not interface)
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "com/firefly/runtime/collections/PersistentVector",
                "get",
                "(I)Ljava/lang/Object;",
                false  // not an interface
            );
            
            lastExpressionType = VarType.OBJECT;
        } else {
            // Fallback: treat as PersistentVector
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "com/firefly/runtime/collections/PersistentVector",
                "get",
                "(I)Ljava/lang/Object;",
                false
            );
            lastExpressionType = VarType.OBJECT;
        }
        
        return null;
    }
    @Override 
    public Void visitLiteralExpr(LiteralExpr expr) {
        if (methodVisitor != null) {
            switch (expr.getKind()) {
                case STRING:
                    methodVisitor.visitLdcInsn((String) expr.getValue());
                    lastExpressionType = VarType.STRING;
                    break;
                case INTEGER:
                    int intValue = (Integer) expr.getValue();
                    if (intValue >= -1 && intValue <= 5) {
                        methodVisitor.visitInsn(ICONST_0 + intValue);
                    } else if (intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE) {
                        methodVisitor.visitIntInsn(BIPUSH, intValue);
                    } else if (intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE) {
                        methodVisitor.visitIntInsn(SIPUSH, intValue);
                    } else {
                        methodVisitor.visitLdcInsn(intValue);
                    }
                    lastExpressionType = VarType.INT;
                    break;
                case FLOAT:
                    // Flylang FLOAT maps to JVM double
                    methodVisitor.visitLdcInsn((Double) expr.getValue());
                    lastExpressionType = VarType.DOUBLE;
                    break;
                case BOOLEAN:
                    boolean boolValue = (Boolean) expr.getValue();
                    methodVisitor.visitInsn(boolValue ? ICONST_1 : ICONST_0);
                    lastExpressionType = VarType.BOOLEAN;
                    break;
                case NONE:
                    methodVisitor.visitInsn(ACONST_NULL);
                    lastExpressionType = VarType.OBJECT;
                    break;
            }
        }
        return null;
    }
    @Override 
    public Void visitIdentifierExpr(IdentifierExpr expr) {
        if (methodVisitor == null) return null;
        
        // Special-case: bare nullary ADT variants from stdlib (e.g., None)
        if ("None".equals(expr.getName())) {
            // Default to Option.None
            methodVisitor.visitFieldInsn(GETSTATIC, "firefly/std/option/Option", "None", "Lfirefly/std/option/Option;");
            lastExpressionType = VarType.OBJECT;
            return null;
        }
        
        // Map 'this' to 'self' (self is stored as the 0-index local in methods)
        String lookupName = "this".equals(expr.getName()) ? "self" : expr.getName();
        
        // Look up variable in local variables
        Integer varIndex = localVariables.get(lookupName);
        if (varIndex != null) {
            VarType varType = localVariableTypes.getOrDefault(lookupName, VarType.INT);
            lastExpressionType = varType;
            
            if (System.getenv("CODEGEN_DEBUG") != null) {
                System.err.println("[CODEGEN] Loading local var: " + lookupName + " index=" + varIndex + " type=" + varType);
            }
            
            // Load local variable based on type
            switch (varType) {
                case INT:
                case BOOLEAN:
                    methodVisitor.visitVarInsn(ILOAD, varIndex);
                    break;
                case FLOAT:
                case DOUBLE:
                    methodVisitor.visitVarInsn(DLOAD, varIndex);
                    break;
                case LONG:
                    methodVisitor.visitVarInsn(LLOAD, varIndex);
                    break;
                case STRING:
                case STRING_ARRAY:
                case OBJECT:
                    methodVisitor.visitVarInsn(ALOAD, varIndex);
                    break;
            }
        } else {
            // Check if it's a class name (for static method calls)
            // Class names used in static calls will be resolved in visitCallExpr
            // For now, just don't load anything - the parent CallExpr will handle it
            java.util.Optional<String> className = typeResolver.resolveClassName(expr.getName());
            if (className.isPresent()) {
                // It's a valid class name - don't generate bytecode here
                // The parent FieldAccessExpr/CallExpr will handle this
                lastExpressionType = VarType.OBJECT;
                return null;
            }
            
            // Fallback: push null to allow codegen to proceed (unbound identifier in unreachable branch)
            methodVisitor.visitInsn(ACONST_NULL);
            lastExpressionType = VarType.OBJECT;
        }
        
        return null;
    }
    @Override 
    public Void visitIfExpr(IfExpr expr) {
        if (methodVisitor == null) return null;
        
        Label elseLabel = new Label();
        Label endLabel = new Label();
        
        // Evaluate condition
        expr.getCondition().accept(this);
        
        // Jump to else if condition is false (0)
        methodVisitor.visitJumpInsn(IFEQ, elseLabel);
        
        // Then branch
        boolean savedReachable = codeIsReachable;
        VarType savedExprType = lastExpressionType;
        codeIsReachable = true;
        expr.getThenBranch().accept(this);
        boolean thenReachable = codeIsReachable;
        VarType thenType = lastExpressionType;
        
        // In statement context, pop any result from then branch
        if (inStatementContext && thenType != null && thenReachable) {
            switch (thenType) {
                case DOUBLE:
                case LONG:
                    methodVisitor.visitInsn(POP2);
                    break;
                case INT:
                case BOOLEAN:
                case FLOAT:
                case STRING:
                case STRING_ARRAY:
                case OBJECT:
                    methodVisitor.visitInsn(POP);
                    break;
            }
        }
        
        // Only add GOTO if then branch doesn't end with control flow (break/continue/return)
        if (thenReachable) {
            methodVisitor.visitJumpInsn(GOTO, endLabel);
        }
        
        // Else branch
        methodVisitor.visitLabel(elseLabel);
        codeIsReachable = true;
        if (expr.getElseBranch().isPresent()) {
            expr.getElseBranch().get().accept(this);
            VarType elseType = lastExpressionType;
            
            // In statement context, pop any result from else branch
            if (inStatementContext && elseType != null) {
                switch (elseType) {
                    case DOUBLE:
                    case LONG:
                        methodVisitor.visitInsn(POP2);
                        break;
                    case INT:
                    case BOOLEAN:
                    case FLOAT:
                    case STRING:
                    case STRING_ARRAY:
                    case OBJECT:
                        methodVisitor.visitInsn(POP);
                        break;
                }
            }
        }
        boolean elseReachable = codeIsReachable;
        
        // End label
        methodVisitor.visitLabel(endLabel);
        
        // Code after if is reachable if either branch is reachable
        codeIsReachable = thenReachable || elseReachable;
        
        // In statement context, result is void
        if (inStatementContext) {
            lastExpressionType = null;
        }
        
        return null;
    }
    @Override 
    public Void visitMatchExpr(MatchExpr expr) {
        if (methodVisitor == null) return null;
        
        // Evaluate the value being matched
        expr.getValue().accept(this);
        
        // Box primitives so we can store as Object and reuse in pattern matching
        switch (lastExpressionType) {
            case INT:
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                break;
            case LONG:
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case FLOAT:
            case DOUBLE:
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case BOOLEAN:
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                break;
            default:
                // already an object
                break;
        }
        
        // Store the value in a local variable for repeated access
        int matchValueIndex = localVarIndex++;
        methodVisitor.visitVarInsn(ASTORE, matchValueIndex);
        
        // Prepare a result local to ensure consistent stack at join point
        int resultIndex = localVarIndex++;
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, resultIndex);
        
        // Create labels for each arm and the end
        List<Label> nextArmLabels = new ArrayList<>();
        Label endLabel = new Label();
        
        for (int i = 0; i < expr.getArms().size(); i++) {
            nextArmLabels.add(new Label());
        }
        
        // Generate code for each arm
        for (int i = 0; i < expr.getArms().size(); i++) {
            MatchExpr.MatchArm arm = expr.getArms().get(i);
            Label nextArm = (i < expr.getArms().size() - 1) ? nextArmLabels.get(i) : endLabel;
            
            // Start label for this arm to help frame computation
            Label armStart = new Label();
            methodVisitor.visitLabel(armStart);
            
            // Try to match the pattern
            generatePatternMatch(
                arm.getPattern(), 
                matchValueIndex, 
                nextArm
            );
            
            // If pattern matched, check guard (if present)
            if (arm.getGuard() != null) {
                arm.getGuard().accept(this);
                methodVisitor.visitJumpInsn(IFEQ, nextArm); // If guard false, try next arm
            }
            
            // Execute arm body
            arm.getBody().accept(this);
            // If arm body returned (code not reachable), skip storing into result
            if (codeIsReachable) {
                // Store into result (boxed as Object)
                switch (lastExpressionType) {
                    case INT:
                        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        break;
                    case LONG:
                        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                        break;
                    case FLOAT:
                    case DOUBLE:
                        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                        break;
                    case BOOLEAN:
                        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                        break;
                    default:
                        // Already an object
                        break;
                }
                methodVisitor.visitVarInsn(ASTORE, resultIndex);
                methodVisitor.visitJumpInsn(GOTO, endLabel);
            }
            
            // Label for next arm (try next pattern)
            if (i < expr.getArms().size() - 1) {
                methodVisitor.visitLabel(nextArm);
            }
        }
        
        // End label: load the result (could be null if no arm matched)
        methodVisitor.visitLabel(endLabel);
        methodVisitor.visitVarInsn(ALOAD, resultIndex);
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    /**
     * Generate bytecode to match a pattern against a value.
     * Returns true if pattern always matches, false otherwise.
     * 
     * @param pattern The pattern to match
     * @param valueIndex Local variable index containing the value
     * @param failLabel Label to jump to if pattern doesn't match
     * @return true if pattern always matches
     */
    private boolean generatePatternMatch(Pattern pattern, int valueIndex, Label failLabel) {
        try {
            if (pattern instanceof com.firefly.compiler.ast.pattern.WildcardPattern) {
                // Wildcard always matches
                return true;
            }
            
            if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
                // Typed variable pattern always matches and binds the value
                com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern = 
                    (com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern;
                String varName = typedPattern.getName();
                
                // Load the value and store it in a new local variable
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                int varIndex = localVarIndex++;
                localVariables.put(varName, varIndex);
                localVariableTypes.put(varName, VarType.OBJECT);
                methodVisitor.visitVarInsn(ASTORE, varIndex);
                
                return true;
            }
            
            if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
                // Variable pattern always matches and binds the value
                VariablePattern varPattern = (VariablePattern) pattern;
                String varName = varPattern.getName();
                
                // Load the value and store it in a new local variable
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                int varIndex = localVarIndex++;
                localVariables.put(varName, varIndex);
                localVariableTypes.put(varName, VarType.OBJECT);
                methodVisitor.visitVarInsn(ASTORE, varIndex);
                
                return true;
            }
            
            if (pattern instanceof com.firefly.compiler.ast.pattern.LiteralPattern) {
                // Literal pattern: check equality
                com.firefly.compiler.ast.pattern.LiteralPattern litPattern = 
                    (com.firefly.compiler.ast.pattern.LiteralPattern) pattern;
                
                // Load the value
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                
                // Load the literal
                litPattern.getLiteral().accept(this);
                
                // Compare (for now, use equals for objects)
                // Box primitives if needed
                if (lastExpressionType == VarType.INT) {
                    methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        "java/lang/Integer",
                        "valueOf",
                        "(I)Ljava/lang/Integer;",
                        false
                    );
                } else if (lastExpressionType == VarType.BOOLEAN) {
                    methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        "java/lang/Boolean",
                        "valueOf",
                        "(Z)Ljava/lang/Boolean;",
                        false
                    );
                }
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
                methodVisitor.visitJumpInsn(IFEQ, failLabel);
                return false;
            }
            
            if (pattern instanceof com.firefly.compiler.ast.pattern.RangePattern) {
                com.firefly.compiler.ast.pattern.RangePattern rp = (com.firefly.compiler.ast.pattern.RangePattern) pattern;
                // Evaluate start and end
                rp.getStart().accept(this);
                rp.getEnd().accept(this);
                // Create Range(start, end, inclusive)
                generateRangeCreation(rp.isInclusive());
                int rangeIdx = localVarIndex++;
                methodVisitor.visitVarInsn(ASTORE, rangeIdx);
                // Load range and value, unbox int, and call contains
                methodVisitor.visitVarInsn(ALOAD, rangeIdx);
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "com/firefly/runtime/Range", "contains", "(I)Z", false);
                methodVisitor.visitJumpInsn(IFEQ, failLabel);
                return false;
            }
            
            if (pattern instanceof com.firefly.compiler.ast.pattern.TuplePattern) {
                // Tuple pattern: (p1, p2, ...)
                com.firefly.compiler.ast.pattern.TuplePattern tuple = (com.firefly.compiler.ast.pattern.TuplePattern) pattern;
                java.util.List<Pattern> elems = tuple.getElements();
                
                // Ensure value is a List
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                methodVisitor.visitTypeInsn(INSTANCEOF, "java/util/List");
                methodVisitor.visitJumpInsn(IFEQ, failLabel);
                
                // Cast and store
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/util/List");
                int listIdx = localVarIndex++;
                methodVisitor.visitVarInsn(ASTORE, listIdx);
                
                for (int i = 0; i < elems.size(); i++) {
                    Pattern p = elems.get(i);
                    if (p instanceof com.firefly.compiler.ast.pattern.WildcardPattern) {
                        continue; // skip
                    }
                    // Load list.get(i)
                    methodVisitor.visitVarInsn(ALOAD, listIdx);
                    if (i <= 5) {
                        switch (i) {
                            case 0: methodVisitor.visitInsn(ICONST_0); break;
                            case 1: methodVisitor.visitInsn(ICONST_1); break;
                            case 2: methodVisitor.visitInsn(ICONST_2); break;
                            case 3: methodVisitor.visitInsn(ICONST_3); break;
                            case 4: methodVisitor.visitInsn(ICONST_4); break;
                            default: methodVisitor.visitInsn(ICONST_5); break;
                        }
                    } else if (i <= Byte.MAX_VALUE) {
                        methodVisitor.visitIntInsn(BIPUSH, i);
                    } else if (i <= Short.MAX_VALUE) {
                        methodVisitor.visitIntInsn(SIPUSH, i);
                    } else {
                        methodVisitor.visitLdcInsn(i);
                    }
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
                    // Store element for potential recursive match/binding
                    int elemIdx = localVarIndex++;
                    methodVisitor.visitVarInsn(ASTORE, elemIdx);
                    
                    if (p instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
                        // Bind variable name to element object
                        String name = ((VariablePattern) p).getName();
                        localVariables.put(name, elemIdx);
                        localVariableTypes.put(name, VarType.OBJECT);
                    } else if (p instanceof com.firefly.compiler.ast.pattern.LiteralPattern) {
                        // Compare element.equals(boxedLiteral)
                        methodVisitor.visitVarInsn(ALOAD, elemIdx);
                        com.firefly.compiler.ast.pattern.LiteralPattern lit = (com.firefly.compiler.ast.pattern.LiteralPattern) p;
                        lit.getLiteral().accept(this);
                        switch (lastExpressionType) {
                            case INT:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                break;
                            case LONG:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                                break;
                            case FLOAT:
                            case DOUBLE:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                                break;
                            case BOOLEAN:
                                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                                break;
                            default:
                                // already object
                                break;
                        }
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
                        methodVisitor.visitJumpInsn(IFEQ, failLabel);
                    } else {
                        // Recurse with element index
                        boolean alwaysMatches = generatePatternMatch(p, elemIdx, failLabel);
                    }
                }
                return false;
            }
            
            if (pattern instanceof com.firefly.compiler.ast.pattern.StructPattern) {
                // Named struct pattern: TypeName { field1, field2, ... }
                com.firefly.compiler.ast.pattern.StructPattern structPattern = 
                    (com.firefly.compiler.ast.pattern.StructPattern) pattern;
                
                // Load the value
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                
                // Check instanceof
                String typeName = resolveStructInternalName(structPattern.getTypeName());
                methodVisitor.visitTypeInsn(INSTANCEOF, typeName);
                methodVisitor.visitJumpInsn(IFEQ, failLabel);
                
                // Cast to the type
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                methodVisitor.visitTypeInsn(CHECKCAST, typeName);
                
                // Store in temporary
                int tempIndex = localVarIndex++;
                methodVisitor.visitVarInsn(ASTORE, tempIndex);
                
                // Get struct metadata
                StructMetadata structMeta = structRegistry.get(structPattern.getTypeName());
                
                // Destructure fields by name
                List<com.firefly.compiler.ast.pattern.StructPattern.FieldPattern> fieldPatterns = 
                    structPattern.getFields();
                
                if (structMeta != null) {
                    for (com.firefly.compiler.ast.pattern.StructPattern.FieldPattern fieldPattern : fieldPatterns) {
                        String fieldName = fieldPattern.getFieldName();
                        
                        // Find the field metadata
                        StructMetadata.FieldMetadata fieldMeta = null;
                        for (StructMetadata.FieldMetadata fm : structMeta.fields) {
                            if (fm.name.equals(fieldName)) {
                                fieldMeta = fm;
                                break;
                            }
                        }
                        
                        if (fieldMeta == null) {
                            throw new RuntimeException("Field '" + fieldName + "' not found in struct '" + structPattern.getTypeName() + "'");
                        }
                        
                        // Load the struct instance
                        methodVisitor.visitVarInsn(ALOAD, tempIndex);
                        
                        // Call getter method for the field (use JavaBean naming)
                        String getterDescriptor = "()" + getTypeDescriptor(fieldMeta.type);
                        String getterName = (getVarTypeFromType(fieldMeta.type) == VarType.BOOLEAN
                            ? (fieldName.startsWith("is") ? fieldName : "is" + capitalize(fieldName))
                            : ("get" + capitalize(fieldName)));
                        methodVisitor.visitMethodInsn(
                            INVOKEVIRTUAL,
                            typeName,
                            getterName,
                            getterDescriptor,
                            false
                        );
                        
                        // Store field value in a temp variable
                        int fieldValueIndex = localVarIndex++;
                        VarType fieldType = getVarTypeFromType(fieldMeta.type);
                        
                        // Store based on type
                        switch (fieldType) {
                            case INT:
                            case BOOLEAN:
                                methodVisitor.visitVarInsn(ISTORE, fieldValueIndex);
                                break;
                            case FLOAT:
                            case DOUBLE:
                                methodVisitor.visitVarInsn(DSTORE, fieldValueIndex);
                                break;
                            default:
                                methodVisitor.visitVarInsn(ASTORE, fieldValueIndex);
                                break;
                        }
                        
                        // Handle the field pattern
                        Pattern nestedPattern = fieldPattern.getPattern();
                        if (nestedPattern == null || fieldPattern.isShorthand()) {
                            // Shorthand: bind directly to variable with field name
                            localVariables.put(fieldName, fieldValueIndex);
                            localVariableTypes.put(fieldName, fieldType);
                        } else if (nestedPattern instanceof VariablePattern) {
                            // Explicit variable pattern
                            VariablePattern varPattern = (VariablePattern) nestedPattern;
                            String varName = varPattern.getName();
                            localVariables.put(varName, fieldValueIndex);
                            localVariableTypes.put(varName, fieldType);
                        } else if (nestedPattern instanceof com.firefly.compiler.ast.pattern.LiteralPattern) {
                            // Inline literal comparison to handle primitives correctly
                            com.firefly.compiler.ast.pattern.LiteralPattern lit = (com.firefly.compiler.ast.pattern.LiteralPattern) nestedPattern;
                            // Load stored field value with correct opcode
                            switch (fieldType) {
                                case INT:
                                case BOOLEAN:
                                    methodVisitor.visitVarInsn(ILOAD, fieldValueIndex);
                                    break;
                                case LONG:
                                    methodVisitor.visitVarInsn(LLOAD, fieldValueIndex);
                                    break;
                                case FLOAT:
                                case DOUBLE:
                                    methodVisitor.visitVarInsn(DLOAD, fieldValueIndex);
                                    break;
                                default:
                                    methodVisitor.visitVarInsn(ALOAD, fieldValueIndex);
                                    break;
                            }
                            // Load literal and compare
                            lit.getLiteral().accept(this);
                            if (fieldType == VarType.INT || fieldType == VarType.BOOLEAN) {
                                // If not equal, jump to fail
                                methodVisitor.visitJumpInsn(IF_ICMPNE, failLabel);
                            } else if (fieldType == VarType.LONG) {
                                methodVisitor.visitInsn(LCMP);
                                Label ok = new Label();
                                methodVisitor.visitJumpInsn(IFEQ, ok);
                                methodVisitor.visitJumpInsn(GOTO, failLabel);
                                methodVisitor.visitLabel(ok);
                            } else if (fieldType == VarType.FLOAT || fieldType == VarType.DOUBLE) {
                                methodVisitor.visitInsn(DCMPL);
                                Label ok = new Label();
                                methodVisitor.visitJumpInsn(IFEQ, ok);
                                methodVisitor.visitJumpInsn(GOTO, failLabel);
                                methodVisitor.visitLabel(ok);
                            } else {
                                // Object equals
                                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
                                methodVisitor.visitJumpInsn(IFEQ, failLabel);
                            }
                        } else {
                            // Other pattern types - match recursively
                            boolean alwaysMatches = generatePatternMatch(
                                nestedPattern,
                                fieldValueIndex,
                                failLabel
                            );
                        }
                    }
                }
                
                return false;
            }
            
            if (pattern instanceof com.firefly.compiler.ast.pattern.TupleStructPattern) {
                // Constructor pattern: check type and destructure
                com.firefly.compiler.ast.pattern.TupleStructPattern structPattern = 
                    (com.firefly.compiler.ast.pattern.TupleStructPattern) pattern;
                
                // Load the value
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                
                // Resolve the variant class name robustly (supports stdlib / imported types)
                String simpleName = structPattern.getTypeName();
                String internalTypeName = null;
                java.util.Optional<String> resolvedCls = typeResolver.resolveClassName(simpleName);
                if (resolvedCls.isPresent()) {
                    // Verify the class actually exists (explicit imports may point to non-nested classes)
                    java.util.Optional<Class<?>> cls = typeResolver.getClass(resolvedCls.get());
                    if (cls.isPresent()) {
                        internalTypeName = resolvedCls.get().replace('.', '/');
                    }
                }
                if (internalTypeName == null) {
                    // Try resolving as a nested variant class on any explicitly imported type
                    java.util.Optional<String> nested = typeResolver.resolveVariantNestedClass(simpleName);
                    if (nested.isPresent()) {
                        internalTypeName = nested.get().replace('.', '/');
                    }
                }
                if (internalTypeName == null) {
                    // Fallback to local resolution
                    internalTypeName = resolveStructInternalName(simpleName);
                }
                
                // Check instanceof
                methodVisitor.visitTypeInsn(INSTANCEOF, internalTypeName);
                methodVisitor.visitJumpInsn(IFEQ, failLabel);
                
                // Cast to the type
                methodVisitor.visitVarInsn(ALOAD, valueIndex);
                methodVisitor.visitTypeInsn(CHECKCAST, internalTypeName);
                
                // Store in temporary
                int tempIndex = localVarIndex++;
                methodVisitor.visitVarInsn(ASTORE, tempIndex);
                
                // Get struct metadata to find actual field names (only available for locally-declared types)
                StructMetadata structMeta = structRegistry.get(simpleName);
                
                // Destructure nested patterns
                List<Pattern> nestedPatterns = structPattern.getPatterns();
                if (!nestedPatterns.isEmpty()) {
                    if (structMeta != null) {
                        // For each nested pattern, extract the field and match recursively using metadata
                        for (int i = 0; i < nestedPatterns.size() && i < structMeta.fields.size(); i++) {
                            Pattern nestedPattern = nestedPatterns.get(i);
                            StructMetadata.FieldMetadata fieldMeta = structMeta.fields.get(i);
                            String fieldName = fieldMeta.name;
                            
                            // Load the struct instance
                            methodVisitor.visitVarInsn(ALOAD, tempIndex);
                            
                            // Access field directly for data variants
                            String fieldDescriptor = "" + getTypeDescriptor(fieldMeta.type);
                            methodVisitor.visitFieldInsn(
                                GETFIELD,
                                internalTypeName,
                                fieldName,
                                fieldDescriptor
                            );
                            
                            // Store field value in a temp variable
                            int fieldValueIndex = localVarIndex++;
                            VarType fieldType = getVarTypeFromType(fieldMeta.type);
                            
                            // Store based on type
                            switch (fieldType) {
                                case INT:
                                case BOOLEAN:
                                    methodVisitor.visitVarInsn(ISTORE, fieldValueIndex);
                                    break;
                                case LONG:
                                    methodVisitor.visitVarInsn(LSTORE, fieldValueIndex);
                                    localVarIndex++; // 64-bit takes 2 slots
                                    break;
                                case FLOAT:
                                case DOUBLE:
                                    methodVisitor.visitVarInsn(DSTORE, fieldValueIndex);
                                    localVarIndex++; // 64-bit takes 2 slots
                                    break;
                                default:
                                    methodVisitor.visitVarInsn(ASTORE, fieldValueIndex);
                                    break;
                            }
                            
                            // Recursively match the nested pattern or bind variable
                            if (nestedPattern instanceof VariablePattern) {
                                VariablePattern varPattern = (VariablePattern) nestedPattern;
                                String varName = varPattern.getName();
                                localVariables.put(varName, fieldValueIndex);
                                localVariableTypes.put(varName, fieldType);
                            } else if (nestedPattern instanceof com.firefly.compiler.ast.pattern.LiteralPattern) {
                                com.firefly.compiler.ast.pattern.LiteralPattern lit = (com.firefly.compiler.ast.pattern.LiteralPattern) nestedPattern;
                                // Load stored field value with correct opcode
                                switch (fieldType) {
                                    case INT:
                                    case BOOLEAN:
                                        methodVisitor.visitVarInsn(ILOAD, fieldValueIndex);
                                        break;
                                    case LONG:
                                        methodVisitor.visitVarInsn(LLOAD, fieldValueIndex);
                                        break;
                                    case FLOAT:
                                    case DOUBLE:
                                        methodVisitor.visitVarInsn(DLOAD, fieldValueIndex);
                                        break;
                                    default:
                                        methodVisitor.visitVarInsn(ALOAD, fieldValueIndex);
                                        break;
                                }
                                // Load literal and compare
                                lit.getLiteral().accept(this);
                                if (fieldType == VarType.INT || fieldType == VarType.BOOLEAN) {
                                    methodVisitor.visitJumpInsn(IF_ICMPNE, failLabel);
                                } else if (fieldType == VarType.LONG) {
                                    methodVisitor.visitInsn(LCMP);
                                    Label ok = new Label();
                                    methodVisitor.visitJumpInsn(IFEQ, ok);
                                    methodVisitor.visitJumpInsn(GOTO, failLabel);
                                    methodVisitor.visitLabel(ok);
                                } else if (fieldType == VarType.FLOAT || fieldType == VarType.DOUBLE) {
                                    methodVisitor.visitInsn(DCMPL);
                                    Label ok = new Label();
                                    methodVisitor.visitJumpInsn(IFEQ, ok);
                                    methodVisitor.visitJumpInsn(GOTO, failLabel);
                                    methodVisitor.visitLabel(ok);
                                } else {
                                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
                                    methodVisitor.visitJumpInsn(IFEQ, failLabel);
                                }
                            } else {
                                boolean alwaysMatches = generatePatternMatch(
                                    nestedPattern,
                                    fieldValueIndex,
                                    failLabel
                                );
                            }
                        }
                    } else {
                        // Fallback path for external ADTs (e.g., stdlib variants) without local metadata
                        java.util.Optional<Class<?>> variantClass = typeResolver.getClass(internalTypeName.replace('/', '.'));
                        for (int i = 0; i < nestedPatterns.size(); i++) {
                            Pattern nestedPattern = nestedPatterns.get(i);
                            String fieldName = "value" + i;
                            String fieldDescriptor = "Ljava/lang/Object;";
                            VarType fieldType = VarType.OBJECT;
                            if (variantClass.isPresent()) {
                                try {
                                    java.lang.reflect.Field rf = variantClass.get().getField(fieldName);
                                    fieldDescriptor = org.objectweb.asm.Type.getDescriptor(rf.getType());
                                    fieldType = getVarTypeFromClass(rf.getType());
                                } catch (NoSuchFieldException ignore) {}
                            }
                            // Load the struct instance and read the field
                            methodVisitor.visitVarInsn(ALOAD, tempIndex);
                            methodVisitor.visitFieldInsn(GETFIELD, internalTypeName, fieldName, fieldDescriptor);
                            
                            // Store field in a temp local
                            int fieldValueIndex = localVarIndex++;
                            switch (fieldType) {
                                case INT:
                                case BOOLEAN:
                                    methodVisitor.visitVarInsn(ISTORE, fieldValueIndex);
                                    break;
                                case LONG:
                                    methodVisitor.visitVarInsn(LSTORE, fieldValueIndex);
                                    localVarIndex++; // 64-bit takes 2 slots
                                    break;
                                case FLOAT:
                                case DOUBLE:
                                    methodVisitor.visitVarInsn(DSTORE, fieldValueIndex);
                                    localVarIndex++; // 64-bit takes 2 slots
                                    break;
                                default:
                                    methodVisitor.visitVarInsn(ASTORE, fieldValueIndex);
                                    break;
                            }
                            
                            // Bind or match nested pattern
                            if (nestedPattern instanceof VariablePattern) {
                                String varName = ((VariablePattern) nestedPattern).getName();
                                localVariables.put(varName, fieldValueIndex);
                                localVariableTypes.put(varName, fieldType);
                            } else if (nestedPattern instanceof com.firefly.compiler.ast.pattern.LiteralPattern) {
                                com.firefly.compiler.ast.pattern.LiteralPattern lit = (com.firefly.compiler.ast.pattern.LiteralPattern) nestedPattern;
                                // Load stored field value with correct opcode
                                switch (fieldType) {
                                    case INT:
                                    case BOOLEAN:
                                        methodVisitor.visitVarInsn(ILOAD, fieldValueIndex);
                                        break;
                                    case LONG:
                                        methodVisitor.visitVarInsn(LLOAD, fieldValueIndex);
                                        break;
                                    case FLOAT:
                                    case DOUBLE:
                                        methodVisitor.visitVarInsn(DLOAD, fieldValueIndex);
                                        break;
                                    default:
                                        methodVisitor.visitVarInsn(ALOAD, fieldValueIndex);
                                        break;
                                }
                                // Load literal and compare
                                lit.getLiteral().accept(this);
                                if (fieldType == VarType.INT || fieldType == VarType.BOOLEAN) {
                                    methodVisitor.visitJumpInsn(IF_ICMPNE, failLabel);
                                } else if (fieldType == VarType.LONG) {
                                    methodVisitor.visitInsn(LCMP);
                                    Label ok = new Label();
                                    methodVisitor.visitJumpInsn(IFEQ, ok);
                                    methodVisitor.visitJumpInsn(GOTO, failLabel);
                                    methodVisitor.visitLabel(ok);
                                } else if (fieldType == VarType.FLOAT || fieldType == VarType.DOUBLE) {
                                    methodVisitor.visitInsn(DCMPL);
                                    Label ok = new Label();
                                    methodVisitor.visitJumpInsn(IFEQ, ok);
                                    methodVisitor.visitJumpInsn(GOTO, failLabel);
                                    methodVisitor.visitLabel(ok);
                                } else {
                                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
                                    methodVisitor.visitJumpInsn(IFEQ, failLabel);
                                }
                            } else {
                                boolean alwaysMatches = generatePatternMatch(
                                    nestedPattern,
                                    fieldValueIndex,
                                    failLabel
                                );
                            }
                        }
                    }
                }
                
                return false;
            }
            
            // For other patterns, default to always match (fallback)
            return true;
        } catch (Exception e) {
            String loc = (pattern != null && pattern.getLocation() != null) ? (pattern.getLocation().toString()) : "<unknown>";
            throw new RuntimeException("Pattern codegen failed at " + loc + " (" + pattern.getClass().getSimpleName() + ") - " + e.getMessage(), e);
        }
    }
    @Override 
    public Void visitBlockExpr(BlockExpr expr) {
        if (methodVisitor != null) {
            // Visit statements
            for (Statement stmt : expr.getStatements()) {
                stmt.accept(this);
            }
            
            // Visit final expression if present
            if (expr.getFinalExpression().isPresent()) {
                expr.getFinalExpression().get().accept(this);
            }
        }
        return null;
    }
    @Override 
    public Void visitLambdaExpr(LambdaExpr expr) {
        if (methodVisitor == null) return null;
        
        // Generate lambda using invokedynamic (Java 8 approach)
        // This creates a functional interface implementation
        
        // Determine functional interface based on lambda signature
        int paramCount = expr.getParameters().size();
        String functionalInterface;
        String methodName;
        String methodDescriptor;
        
        // Map to Java functional interfaces
        switch (paramCount) {
            case 0:
                functionalInterface = "java/util/function/Supplier";
                methodName = "get";
                methodDescriptor = "()Ljava/lang/Object;";
                break;
            case 1:
                functionalInterface = "java/util/function/Function";
                methodName = "apply";
                methodDescriptor = "(Ljava/lang/Object;)Ljava/lang/Object;";
                break;
            case 2:
                functionalInterface = "java/util/function/BiFunction";
                methodName = "apply";
                methodDescriptor = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                break;
            default:
                // For more than 2 parameters, generate a custom functional interface
                // For now, fallback to Function
                functionalInterface = "java/util/function/Function";
                methodName = "apply";
                methodDescriptor = "(Ljava/lang/Object;)Ljava/lang/Object;";
                break;
        }
        
        // Generate a synthetic lambda method
        String lambdaMethodName = "lambda$" + (labelCounter++);
        generateLambdaMethod(lambdaMethodName, expr);
        
        // Use invokedynamic to create the lambda instance
        Handle bootstrapMethod = new Handle(
            H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false
        );
        
        // Method handle for the lambda implementation
        Handle implMethod = new Handle(
            H_INVOKESTATIC,
            className,
            lambdaMethodName,
            methodDescriptor,
            false
        );
        
        // Generate invokedynamic instruction
        methodVisitor.visitInvokeDynamicInsn(
            methodName,
            "()L" + functionalInterface + ";",
            bootstrapMethod,
            org.objectweb.asm.Type.getType(methodDescriptor),
            implMethod,
            org.objectweb.asm.Type.getType(methodDescriptor)
        );
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    /**
     * Generate a static method for lambda body.
     */
    private void generateLambdaMethod(String methodName, LambdaExpr lambda) {
        // Build method descriptor based on parameter count
        StringBuilder descriptor = new StringBuilder("(");
        for (int i = 0; i < lambda.getParameters().size(); i++) {
            descriptor.append("Ljava/lang/Object;");
        }
        descriptor.append(")Ljava/lang/Object;");
        
        // Save current method visitor
        MethodVisitor outerMethodVisitor = methodVisitor;
        Map<String, Integer> outerLocalVariables = new HashMap<>(localVariables);
        Map<String, VarType> outerLocalVariableTypes = new HashMap<>(localVariableTypes);
        int outerLocalVarIndex = localVarIndex;
        
        // Create new method for lambda
        methodVisitor = classWriter.visitMethod(
            ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
            methodName,
            descriptor.toString(),
            null,
            null
        );
        
        methodVisitor.visitCode();
        
        // Reset local variables for lambda scope
        localVariables.clear();
        localVariableTypes.clear();
        localVarIndex = 0;
        
        // Bind lambda parameters
        for (int i = 0; i < lambda.getParameters().size(); i++) {
            String paramName = lambda.getParameters().get(i);
            localVariables.put(paramName, localVarIndex);
            localVariableTypes.put(paramName, VarType.OBJECT);
            localVarIndex++;
        }
        
        // Generate lambda body
        lambda.getBody().accept(this);
        
        // Box primitive return value if needed
        switch (lastExpressionType) {
            case INT:
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/lang/Integer",
                    "valueOf",
                    "(I)Ljava/lang/Integer;",
                    false
                );
                break;
            case BOOLEAN:
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/lang/Boolean",
                    "valueOf",
                    "(Z)Ljava/lang/Boolean;",
                    false
                );
                break;
            case FLOAT:
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "java/lang/Float",
                    "valueOf",
                    "(F)Ljava/lang/Float;",
                    false
                );
                break;
            // STRING, OBJECT already objects
        }
        
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        
        // Restore outer method context
        methodVisitor = outerMethodVisitor;
        localVariables.clear();
        localVariables.putAll(outerLocalVariables);
        localVariableTypes.clear();
        localVariableTypes.putAll(outerLocalVariableTypes);
        localVarIndex = outerLocalVarIndex;
    }
    @Override 
    public Void visitForExpr(ForExpr expr) {
        if (methodVisitor == null) return null;
        
        // Save old break/continue labels
        Label oldBreak = breakLabel;
        Label oldContinue = continueLabel;
        
        // Create loop labels
        Label loopStart = new Label();
        Label loopEnd = new Label();
        Label loopCondition = new Label();
        breakLabel = loopEnd;
        continueLabel = loopCondition;
        
        // Evaluate iterable and store it
        expr.getIterable().accept(this);
        int iterableIndex = localVarIndex++;
        methodVisitor.visitVarInsn(ASTORE, iterableIndex);
        
        // Get iterator from iterable: iterable.iterator()
        methodVisitor.visitVarInsn(ALOAD, iterableIndex);
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "java/lang/Iterable",
            "iterator",
            "()Ljava/util/Iterator;",
            true
        );
        int iteratorIndex = localVarIndex++;
        methodVisitor.visitVarInsn(ASTORE, iteratorIndex);
        
        // Loop start
        methodVisitor.visitLabel(loopStart);
        
        // Check hasNext(): if (!iterator.hasNext()) goto loopEnd
        methodVisitor.visitVarInsn(ALOAD, iteratorIndex);
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "java/util/Iterator",
            "hasNext",
            "()Z",
            true
        );
        methodVisitor.visitJumpInsn(IFEQ, loopEnd);
        
        // Get next element: Object elem = iterator.next()
        methodVisitor.visitVarInsn(ALOAD, iteratorIndex);
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "java/util/Iterator",
            "next",
            "()Ljava/lang/Object;",
            true
        );
        
        // Bind pattern variable
        if (expr.getPattern() instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern = 
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) expr.getPattern();
            String varName = typedPattern.getName();
            
            // Check if element needs unboxing for primitives
            // For now, assume Object type - proper implementation would need type inference
            int varIndex = localVarIndex++;
            localVariables.put(varName, varIndex);
            localVariableTypes.put(varName, VarType.OBJECT);
            methodVisitor.visitVarInsn(ASTORE, varIndex);
        } else if (expr.getPattern() instanceof VariablePattern) {
            VariablePattern varPattern = (VariablePattern) expr.getPattern();
            String varName = varPattern.getName();
            
            // Check if element needs unboxing for primitives
            // For now, assume Object type - proper implementation would need type inference
            int varIndex = localVarIndex++;
            localVariables.put(varName, varIndex);
            localVariableTypes.put(varName, VarType.OBJECT);
            methodVisitor.visitVarInsn(ASTORE, varIndex);
        }
        
        // Visit loop body
        expr.getBody().accept(this);
        
        // Continue label (jump back to start)
        methodVisitor.visitLabel(loopCondition);
        methodVisitor.visitJumpInsn(GOTO, loopStart);
        
        // Loop end
        methodVisitor.visitLabel(loopEnd);
        
        // Restore break/continue labels
        breakLabel = oldBreak;
        continueLabel = oldContinue;
        
        lastExpressionType = VarType.OBJECT; // Unit type
        return null;
    }
    
    @Override 
    public Void visitWhileExpr(WhileExpr expr) {
        if (methodVisitor == null) return null;
        
        // Save old break/continue labels
        Label oldBreak = breakLabel;
        Label oldContinue = continueLabel;
        
        // Create loop labels
        Label loopStart = new Label();
        Label loopEnd = new Label();
        Label conditionCheck = new Label();
        
        breakLabel = loopEnd;
        continueLabel = conditionCheck;
        
        // Jump to condition check first
        methodVisitor.visitJumpInsn(GOTO, conditionCheck);
        
        // Loop body
        methodVisitor.visitLabel(loopStart);
        expr.getBody().accept(this);
        
        // Condition check
        methodVisitor.visitLabel(conditionCheck);
        expr.getCondition().accept(this);
        
        // If condition is true (non-zero), jump back to loop start
        methodVisitor.visitJumpInsn(IFNE, loopStart);
        
        // Loop end
        methodVisitor.visitLabel(loopEnd);
        
        // Restore break/continue labels
        breakLabel = oldBreak;
        continueLabel = oldContinue;
        
        lastExpressionType = VarType.OBJECT; // Unit type
        return null;
    }
    @Override 
    public Void visitReturnExpr(ReturnExpr expr) {
        if (methodVisitor == null) return null;
        
        if (expr.getValue().isPresent()) {
            // Evaluate return value
            expr.getValue().get().accept(this);
            
            // Return with value
            switch (lastExpressionType) {
                case INT:
                case BOOLEAN:
                    methodVisitor.visitInsn(IRETURN);
                    break;
                case FLOAT:
                    methodVisitor.visitInsn(DRETURN);
                    break;
                case STRING:
                case OBJECT:
                    methodVisitor.visitInsn(ARETURN);
                    break;
            }
        } else {
            // Bare return
            methodVisitor.visitInsn(RETURN);
        }
        // Mark following code as unreachable for frame computation
        codeIsReachable = false;
        
        codeIsReachable = false;  // Code after return is unreachable
        
        return null;
    }
    
    @Override 
    public Void visitBreakExpr(BreakExpr expr) {
        if (methodVisitor == null) return null;
        
        if (breakLabel == null) {
            throw new RuntimeException("break statement outside of loop at " + expr.getLocation());
        }
        
        // Jump to the end of the loop
        methodVisitor.visitJumpInsn(GOTO, breakLabel);
        codeIsReachable = false;  // Code after break is unreachable
        
        return null;
    }
    
    @Override 
    public Void visitContinueExpr(ContinueExpr expr) {
        if (methodVisitor == null) return null;
        
        if (continueLabel == null) {
            throw new RuntimeException("continue statement outside of loop at " + expr.getLocation());
        }
        
        // Jump to the continue point (condition check for while, or loop start for for)
        methodVisitor.visitJumpInsn(GOTO, continueLabel);
        codeIsReachable = false;  // Code after continue is unreachable
        
        return null;
    }
    
    @Override 
    public Void visitAwaitExpr(AwaitExpr expr) {
        if (methodVisitor == null) return null;
        
        // Visit the future expression - should put Future on stack
        expr.getFuture().accept(this);
        
        // Call Future.get() to block and get the value
        // Signature: ()Ljava/lang/Object;
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/firefly/runtime/async/Future",
            "get",
            "()Ljava/lang/Object;",
            false
        );
        
        // Result is now on stack (as Object)
        lastExpressionType = VarType.OBJECT;
        lastCallWasVoid = false;
        
        return null;
    }
    
    @Override 
    public Void visitConcurrentExpr(ConcurrentExpr expr) {
        if (methodVisitor == null) return null;
        
        // concurrent { let x = a.await, let y = b.await }
        // Generates code that runs all bindings in parallel and waits for all
        
        int numBindings = expr.getBindings().size();
        
        // Create array of Futures
        methodVisitor.visitIntInsn(BIPUSH, numBindings);
        methodVisitor.visitTypeInsn(ANEWARRAY, "com/firefly/runtime/async/Future");
        
        // Fill array with futures from each binding
        int index = 0;
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            methodVisitor.visitInsn(DUP);  // Duplicate array reference
            methodVisitor.visitIntInsn(BIPUSH, index);
            
            // Visit the expression (should return a Future)
            binding.getExpression().accept(this);
            
            methodVisitor.visitInsn(AASTORE);  // Store Future in array
            index++;
        }
        
        // Call Future.all() to wait for all futures
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/firefly/runtime/async/Future",
            "all",
            "([Lcom/firefly/runtime/async/Future;)Lcom/firefly/runtime/async/Future;",
            false
        );
        
        // Call get() to block and get the result (returns Void for all())
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/firefly/runtime/async/Future",
            "get",
            "()Ljava/lang/Object;",
            false
        );
        
        // Pop the Void result since we don't need it
        methodVisitor.visitInsn(POP);
        
        // Now bind variables: for each binding, call get() on the original future
        // We need to re-evaluate the futures and await them to bind variables
        for (ConcurrentExpr.ConcurrentBinding binding : expr.getBindings()) {
            String varName = binding.getName();
            
            // Evaluate the future expression again
            binding.getExpression().accept(this);
            
            // Call get() to await the result
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "com/firefly/runtime/async/Future",
                "get",
                "()Ljava/lang/Object;",
                false
            );
            
            // Store in local variable
            int varIndex = localVarIndex++;
            localVariables.put(varName, varIndex);
            localVariableTypes.put(varName, VarType.OBJECT);
            methodVisitor.visitVarInsn(ASTORE, varIndex);
        }
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    @Override 
    public Void visitRaceExpr(RaceExpr expr) {
        if (methodVisitor == null) return null;
        
        // race { fut1, fut2, fut3 } - returns first completed future
        // Collect all expressions from the block and pass to Future.any()
        
        BlockExpr body = expr.getBody();
        java.util.List<com.firefly.compiler.ast.Statement> statements = body.getStatements();
        java.util.List<Expression> expressions = new java.util.ArrayList<>();
        
        // Extract expressions from statements
        for (com.firefly.compiler.ast.Statement stmt : statements) {
            if (stmt instanceof ExprStatement) {
                expressions.add(((ExprStatement) stmt).getExpression());
            }
        }
        
        if (expressions.isEmpty()) {
            // No expressions to race - return null
            methodVisitor.visitInsn(ACONST_NULL);
            lastExpressionType = VarType.OBJECT;
            return null;
        }
        
        // Create array of Futures
        int numExpressions = expressions.size();
        methodVisitor.visitIntInsn(BIPUSH, numExpressions);
        methodVisitor.visitTypeInsn(ANEWARRAY, "com/firefly/runtime/async/Future");
        
        // Fill array with futures from each expression
        for (int i = 0; i < numExpressions; i++) {
            methodVisitor.visitInsn(DUP);  // Duplicate array reference
            methodVisitor.visitIntInsn(BIPUSH, i);
            
            // Visit the expression (should return a Future)
            expressions.get(i).accept(this);
            
            methodVisitor.visitInsn(AASTORE);  // Store Future in array
        }
        
        // Call Future.any() to wait for first completed future
        // Signature: any([Lcom/firefly/runtime/async/Future;)Lcom/firefly/runtime/async/Future;
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/firefly/runtime/async/Future",
            "any",
            "([Lcom/firefly/runtime/async/Future;)Lcom/firefly/runtime/async/Future;",
            false
        );
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    @Override 
    public Void visitTimeoutExpr(TimeoutExpr expr) {
        if (methodVisitor == null) return null;
        
        // timeout(millis) { body } - wraps operation with timeout
        // Generate: Future.timeout(duration, () -> { body })
        
        // First, evaluate the duration expression (milliseconds)
        expr.getDuration().accept(this);
        // Stack: [duration]
        
        // Convert to long if it's int
        if (lastExpressionType == VarType.INT) {
            methodVisitor.visitInsn(I2L);
        }
        // Stack: [duration_long]
        
        // Generate lambda for the body: () -> body with captures of current locals
        String lambdaMethodName = "lambda$timeout$" + (labelCounter++);
        // Build capture list in stable slot order
        java.util.List<java.util.Map.Entry<String,Integer>> entries = new java.util.ArrayList<>(localVariables.entrySet());
        entries.sort(java.util.Comparator.comparingInt(java.util.Map.Entry::getValue));
        java.util.List<String> captureNames = new java.util.ArrayList<>();
        java.util.List<VarType> captureTypes = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String,Integer> e : entries) {
            String n = e.getKey();
            captureNames.add(n);
            captureTypes.add(localVariableTypes.getOrDefault(n, VarType.OBJECT));
        }
        generateTimeoutLambdaMethod(lambdaMethodName, expr.getBody(), captureNames, captureTypes);
        // Capture the (boxed) return kind of the body before we overwrite it
        VarType bodyReturnType = lastExpressionType; // INT/BOOLEAN/FLOAT map to boxed in lambda
        
        // Create Callable lambda using invokedynamic
        Handle bootstrapMethod = new Handle(
            H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false
        );
        
        // Push captures in slot order
        for (java.util.Map.Entry<String,Integer> e : entries) {
            String n = e.getKey();
            int slot = e.getValue();
            VarType vt = localVariableTypes.getOrDefault(n, VarType.OBJECT);
            switch (vt) {
                case INT:
                case BOOLEAN:
                    methodVisitor.visitVarInsn(ILOAD, slot);
                    break;
                case LONG:
                    methodVisitor.visitVarInsn(LLOAD, slot);
                    break;
                case FLOAT:
                case DOUBLE:
                    methodVisitor.visitVarInsn(DLOAD, slot);
                    break;
                default:
                    methodVisitor.visitVarInsn(ALOAD, slot);
                    break;
            }
        }
        // Build method descriptors for impl and indy
        StringBuilder capDesc = new StringBuilder("(");
        for (VarType vt : captureTypes) {
            switch (vt) {
                case INT: capDesc.append('I'); break;
                case LONG: capDesc.append('J'); break;
                case FLOAT:
                case DOUBLE: capDesc.append('D'); break;
                case BOOLEAN: capDesc.append('Z'); break;
                default: capDesc.append("Ljava/lang/Object;"); break;
            }
        }
        capDesc.append(")");
        Handle implMethod = new Handle(
            H_INVOKESTATIC,
            className,
            lambdaMethodName,
            capDesc.toString() + "Ljava/lang/Object;",
            false
        );
        String indyDesc = capDesc.toString() + "Ljava/util/concurrent/Callable;";
        methodVisitor.visitInvokeDynamicInsn(
            "call",
            indyDesc,
            bootstrapMethod,
            org.objectweb.asm.Type.getType("()Ljava/lang/Object;"),
            implMethod,
            org.objectweb.asm.Type.getType("()Ljava/lang/Object;")
        );
        
        // Stack: [duration_long, Callable]
        // Call Future.timeout(long, Callable) -> Future
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/firefly/runtime/async/Future",
            "timeout",
            "(JLjava/util/concurrent/Callable;)Lcom/firefly/runtime/async/Future;",
            false
        );
        
        // Then call get() to obtain the value for expression semantics
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/firefly/runtime/async/Future",
            "get",
            "()Ljava/lang/Object;",
            false
        );
        
        // Unbox if the timeout body produced a primitive
        switch (bodyReturnType) {
            case INT:
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                lastExpressionType = VarType.INT;
                break;
            case BOOLEAN:
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case FLOAT:
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Float");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                lastExpressionType = VarType.FLOAT;
                break;
            default:
                lastExpressionType = VarType.OBJECT;
        }
        return null;
    }
    
    /**
     * Generate a static method for timeout body.
     */
    private void generateTimeoutLambdaMethod(String methodName, Expression body,
                                              java.util.List<String> captureNames,
                                              java.util.List<VarType> captureTypes) {
        // Save current method visitor
        MethodVisitor outerMethodVisitor = methodVisitor;
        Map<String, Integer> outerLocalVariables = new HashMap<>(localVariables);
        Map<String, VarType> outerLocalVariableTypes = new HashMap<>(localVariableTypes);
        Map<String, String> outerLocalDeclaredTypes = new HashMap<>(localVariableDeclaredTypes);
        int outerLocalVarIndex = localVarIndex;
        
        // Create new method for timeout body with captures: (captures...)Ljava/lang/Object;
        StringBuilder lambdaDescBuilder = new StringBuilder("(");
        for (VarType vt : captureTypes) {
            switch (vt) {
                case INT: lambdaDescBuilder.append('I'); break;
                case LONG: lambdaDescBuilder.append('J'); break;
                case FLOAT:
                case DOUBLE: lambdaDescBuilder.append('D'); break;
                case BOOLEAN: lambdaDescBuilder.append('Z'); break;
                default: lambdaDescBuilder.append("Ljava/lang/Object;"); break;
            }
        }
        lambdaDescBuilder.append(")Ljava/lang/Object;");
        String lambdaDesc = lambdaDescBuilder.toString();
        methodVisitor = classWriter.visitMethod(
            ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
            methodName,
            lambdaDesc,
            null,
            new String[]{"java/lang/Exception"}
        );
        
        methodVisitor.visitCode();
        
        // Reset local variables for lambda scope and register captures
        localVariables.clear();
        localVariableTypes.clear();
        localVariableDeclaredTypes.clear();
        int idx = 0;
        for (int i = 0; i < captureNames.size(); i++) {
            String name = captureNames.get(i);
            VarType vt = captureTypes.get(i);
            localVariables.put(name, idx);
            localVariableTypes.put(name, vt);
            if (outerLocalDeclaredTypes.containsKey(name)) {
                localVariableDeclaredTypes.put(name, outerLocalDeclaredTypes.get(name));
            }
            idx += (vt == VarType.LONG || vt == VarType.DOUBLE) ? 2 : 1;
        }
        localVarIndex = idx;
        
        // Visit body
        body.accept(this);
        
        // Box result if primitive
        switch (lastExpressionType) {
            case INT:
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", 
                    "(I)Ljava/lang/Integer;", false);
                break;
            case BOOLEAN:
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", 
                    "(Z)Ljava/lang/Boolean;", false);
                break;
            case FLOAT:
            case DOUBLE:
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", 
                    "(D)Ljava/lang/Double;", false);
                break;
            default:
                // already object
                break;
        }
        
        // Return
        methodVisitor.visitInsn(ARETURN);
        
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        
        // Restore method visitor
        methodVisitor = outerMethodVisitor;
        localVariables.clear();
        localVariables.putAll(outerLocalVariables);
        localVariableTypes.clear();
        localVariableTypes.putAll(outerLocalVariableTypes);
        localVarIndex = outerLocalVarIndex;
    }
    
    @Override public Void visitCoalesceExpr(CoalesceExpr expr) {
        if (methodVisitor == null) return null;
        
        // a ?? b - returns a if not null, else b
        expr.getLeft().accept(this);
        
        // Duplicate for null check
        methodVisitor.visitInsn(DUP);
        
        Label notNull = new Label();
        Label end = new Label();
        
        // Check if null
        methodVisitor.visitJumpInsn(IFNONNULL, notNull);
        
        // Was null, pop it and evaluate right
        methodVisitor.visitInsn(POP);
        expr.getRight().accept(this);
        methodVisitor.visitJumpInsn(GOTO, end);
        
        // Not null, keep left value
        methodVisitor.visitLabel(notNull);
        
        methodVisitor.visitLabel(end);
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    @Override public Void visitSafeAccessExpr(SafeAccessExpr expr) {
        if (methodVisitor == null) return null;
        
        // object?.field - returns null if object is null, else accesses field
        
        // Evaluate the object
        expr.getObject().accept(this);
        
        // Duplicate for null check
        methodVisitor.visitInsn(DUP);
        
        Label isNull = new Label();
        Label end = new Label();
        
        // Check if object is null
        methodVisitor.visitJumpInsn(IFNULL, isNull);
        
        // Not null: access the field
        // Object is on stack, get the field
        String fieldName = expr.getFieldName();
        
        // For now, assume it's a method call (getter pattern)
        // Try calling fieldName() as a getter
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/Object",  // Will be resolved at runtime
            fieldName,
            "()Ljava/lang/Object;",
            false
        );
        
        methodVisitor.visitJumpInsn(GOTO, end);
        
        // Is null: pop the duplicate and push null
        methodVisitor.visitLabel(isNull);
        methodVisitor.visitInsn(POP);  // Pop the duplicate
        methodVisitor.visitInsn(ACONST_NULL);
        
        methodVisitor.visitLabel(end);
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    @Override public Void visitForceUnwrapExpr(ForceUnwrapExpr expr) {
        if (methodVisitor == null) return null;
        
        // value!! - unwraps value or throws NullPointerException if null
        
        // Evaluate the expression
        expr.getExpression().accept(this);
        
        // Duplicate for null check
        methodVisitor.visitInsn(DUP);
        
        Label notNull = new Label();
        
        // Check if null
        methodVisitor.visitJumpInsn(IFNONNULL, notNull);
        
        // Is null: throw NullPointerException
        methodVisitor.visitTypeInsn(NEW, "java/lang/NullPointerException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitLdcInsn("Force unwrap of null value");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "java/lang/NullPointerException",
            "<init>",
            "(Ljava/lang/String;)V",
            false
        );
        methodVisitor.visitInsn(ATHROW);
        
        // Not null: value is on stack
        methodVisitor.visitLabel(notNull);
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    @Override 
    public Void visitAssignmentExpr(AssignmentExpr expr) {
        if (methodVisitor == null) return null;
        
        // Handle field assignment (this.field = value)
        if (expr.getTarget() instanceof FieldAccessExpr) {
            FieldAccessExpr target = (FieldAccessExpr) expr.getTarget();
            String fieldName = target.getFieldName();
            
            // Evaluate object reference (e.g., this)
            target.getObject().accept(this);
            
            // Evaluate the value to assign
            expr.getValue().accept(this);
            VarType valueType = lastExpressionType;
            
            // Store to field - need to determine field descriptor
            // For now, assume it's in the current class and use Int as default type
            String className = this.className;
            if (currentEnclosingClass != null) {
                className = currentEnclosingClass;
            }
            
            // Get field descriptor from registered field types
            String descriptor = currentClassFieldTypes.getOrDefault(fieldName, "Ljava/lang/Object;");
            
            // Debug logging
            if (System.getenv("CODEGEN_DEBUG") != null) {
                System.err.println("[CODEGEN] Field assignment: " + fieldName + " descriptor=" + descriptor + " valueType=" + valueType);
            }
            
            // Convert value type to field type if needed (e.g., Int -> Double for Float fields)
            if (valueType == VarType.INT && "D".equals(descriptor)) {
                methodVisitor.visitInsn(I2D);
                lastExpressionType = VarType.DOUBLE;
            } else if (valueType == VarType.INT && "J".equals(descriptor)) {
                methodVisitor.visitInsn(I2L);
                lastExpressionType = VarType.LONG;
            } else if (valueType == VarType.LONG && "D".equals(descriptor)) {
                methodVisitor.visitInsn(L2D);
                lastExpressionType = VarType.DOUBLE;
            }
            
            methodVisitor.visitFieldInsn(PUTFIELD, className, fieldName, descriptor);
            lastCallWasVoid = true;
        }
        // Handle simple identifier assignment
        else if (expr.getTarget() instanceof IdentifierExpr) {
            IdentifierExpr target = (IdentifierExpr) expr.getTarget();
            String varName = target.getName();
            
            // Evaluate the value
            expr.getValue().accept(this);
            VarType valueType = lastExpressionType;
            
            // Get variable slot
            Integer varIndex = localVariables.get(varName);
            if (varIndex == null) {
                throw new RuntimeException("Variable not defined: " + varName);
            }
            
            VarType varType = localVariableTypes.getOrDefault(varName, lastExpressionType);
            
            // Convert value type to variable type if needed
            if (valueType == VarType.INT && (varType == VarType.DOUBLE || varType == VarType.FLOAT)) {
                methodVisitor.visitInsn(I2D);
                lastExpressionType = VarType.DOUBLE;
            } else if (valueType == VarType.INT && varType == VarType.LONG) {
                methodVisitor.visitInsn(I2L);
                lastExpressionType = VarType.LONG;
            } else if (valueType == VarType.LONG && (varType == VarType.DOUBLE || varType == VarType.FLOAT)) {
                methodVisitor.visitInsn(L2D);
                lastExpressionType = VarType.DOUBLE;
            }
            
            // Store to variable (assignment doesn't produce a value in statements)
            // Use FireflyType system for correct opcode selection
            methodVisitor.visitVarInsn(getStoreOpcodeForType(varType), varIndex);
            
            lastCallWasVoid = true; // Assignment is void-like in statements
        }
        
        return null;
    }
    
    @Override
    public Void visitNewExpr(com.firefly.compiler.ast.expr.NewExpr expr) {
        if (methodVisitor == null) return null;
        
        // Get class name from type
        String className = getClassNameFromType(expr.getType());
        String jvmClassName = className.replace('.', '/');
        
        // Try to resolve and store the actual Java class
        lastExpressionClass = null;
        java.util.Optional<String> resolvedName = typeResolver.resolveClassName(className);
        if (resolvedName.isPresent()) {
            java.util.Optional<Class<?>> clazz = typeResolver.getClass(resolvedName.get());
            if (clazz.isPresent()) {
                lastExpressionClass = clazz.get();
            }
        }
        
        // NEW instruction - creates object reference
        methodVisitor.visitTypeInsn(NEW, jvmClassName);
        
        // DUP instruction - duplicate reference for constructor call
        methodVisitor.visitInsn(DUP);
        
        // Visit constructor arguments
        StringBuilder constructorDescriptor = new StringBuilder("(");
        for (com.firefly.compiler.ast.expr.Expression arg : expr.getArguments()) {
            arg.accept(this);
            // Build descriptor based on argument types
            switch (lastExpressionType) {
                case INT:
                    constructorDescriptor.append("I");
                    break;
                case FLOAT:
                    constructorDescriptor.append("D");
                    break;
                case BOOLEAN:
                    constructorDescriptor.append("Z");
                    break;
                case STRING:
                    constructorDescriptor.append("Ljava/lang/String;");
                    break;
                default:
                    constructorDescriptor.append("Ljava/lang/Object;");
                    break;
            }
        }
        constructorDescriptor.append(")V"); // Constructor returns void
        
        // INVOKESPECIAL <init> - call constructor
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            jvmClassName,
            "<init>",
            constructorDescriptor.toString(),
            false
        );
        
        // Result is the new object (reference left on stack from DUP)
        lastExpressionType = VarType.OBJECT;
        lastCallWasVoid = false;
        
        return null;
    }
    
    @Override
    public Void visitArrayLiteralExpr(com.firefly.compiler.ast.expr.ArrayLiteralExpr expr) {
        if (methodVisitor == null) return null;
        
        // Use PersistentVector for immutable array literals (native Firefly collections)
        // Create Object array for varargs call to PersistentVector.of(...)
        
        int elementCount = expr.getElements().size();
        
        // Push array size
        methodVisitor.visitIntInsn(BIPUSH, elementCount);
        
        // Create Object array
        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        
        // Fill array with elements
        int index = 0;
        for (com.firefly.compiler.ast.expr.Expression element : expr.getElements()) {
            // Duplicate array reference
            methodVisitor.visitInsn(DUP);
            
            // Push index
            methodVisitor.visitIntInsn(BIPUSH, index);
            
            // Evaluate element
            element.accept(this);
            
            // Box primitive types
            switch (lastExpressionType) {
                case INT:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    break;
                case LONG:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    break;
                case FLOAT:
                case DOUBLE:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    break;
                case BOOLEAN:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    break;
                // STRING and OBJECT don't need boxing
            }
            
            // Store in array
            methodVisitor.visitInsn(AASTORE);
            
            index++;
        }
        
        // Call PersistentVector.of(Object... elements) static method
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/firefly/runtime/collections/PersistentVector",
            "of",
            "([Ljava/lang/Object;)Lcom/firefly/runtime/collections/PersistentVector;",
            false
        );
        
        // PersistentVector reference is now on stack
        lastExpressionType = VarType.OBJECT;
        lastCallWasVoid = false;
        
        return null;
    }
    
    public Void visitMapLiteralExpr(com.firefly.compiler.ast.expr.MapLiteralExpr expr) {
        if (methodVisitor == null) return null;
        
        // Create HashMap
        methodVisitor.visitTypeInsn(NEW, "java/util/HashMap");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "java/util/HashMap",
            "<init>",
            "()V",
            false
        );
        
        // Put each entry
        for (var entry : expr.getEntries().entrySet()) {
            methodVisitor.visitInsn(DUP);  // Duplicate HashMap reference
            
            // Visit key
            entry.getKey().accept(this);
            
            // Box key if primitive
            switch (lastExpressionType) {
                case INT:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    break;
                case FLOAT:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    break;
                case BOOLEAN:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    break;
            }
            
            // Visit value
            entry.getValue().accept(this);
            
            // Box value if primitive
            switch (lastExpressionType) {
                case INT:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    break;
                case FLOAT:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    break;
                case BOOLEAN:
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    break;
            }
            
            // Call put(key, value)
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
            );
            methodVisitor.visitInsn(POP);  // Pop returned value
        }
        
        lastExpressionType = VarType.OBJECT;
        lastCallWasVoid = false;
        return null;
    }
    
    @Override public Void visitPattern(Pattern pattern) { return null; }
    @Override public Void visitPrimitiveType(PrimitiveType type) { return null; }
    @Override public Void visitNamedType(NamedType type) { return null; }
    @Override public Void visitOptionalType(OptionalType type) { return null; }
    @Override public Void visitArrayType(ArrayType type) { return null; }
    @Override public Void visitFunctionType(FunctionType type) { return null; }
    
    /**
     * Get class name from Firefly Type
     */
    private String getClassNameFromType(com.firefly.compiler.ast.type.Type type) {
        // Normalize: treat GenericType like NamedType using its base name
        String name = null;
        if (type instanceof com.firefly.compiler.ast.type.NamedType) {
            name = ((com.firefly.compiler.ast.type.NamedType) type).getName();
        } else if (type instanceof com.firefly.compiler.ast.type.GenericType) {
            name = ((com.firefly.compiler.ast.type.GenericType) type).getBaseName();
        }
        if (name != null) {
            // Strip generic arguments if present (e.g., "Option<String>" -> "Option")
            int lt = name.indexOf('<');
            if (lt > 0) name = name.substring(0, lt);
            // Check common Java classes
            switch (name) {
                case "ArrayList": return "java.util.ArrayList";
                case "HashMap": return "java.util.HashMap";
                case "HashSet": return "java.util.HashSet";
                case "Collections": return "java.util.Collections";
                case "StringBuilder": return "java.lang.StringBuilder";
                case "String": return "java.lang.String";
                case "Integer": return "java.lang.Integer";
                case "Long": return "java.lang.Long";
                case "Double": return "java.lang.Double";
                case "Boolean": return "java.lang.Boolean";
                default:
                    // If it's already qualified, use as-is
                    if (name.contains(".")) return name;

                    // If it's a known struct/spark/data in this unit, qualify with current module package
                    if (structRegistry.containsKey(name)) {
                        String modulePkg = (moduleBasePath != null && !moduleBasePath.isEmpty())
                            ? moduleBasePath.replace('/', '.')
                            : "";
                        return modulePkg.isEmpty() ? name : modulePkg + "." + name;
                    }

                    // Try to resolve via imports and wildcard imports
                    java.util.Optional<String> resolved = typeResolver.resolveClassName(name);
                    if (resolved.isPresent()) {
                        return resolved.get();
                    }

                    // Heuristics for stdlib ADTs
                    if ("Option".equals(name)) {
                        return "firefly.std.option.Option";
                    }
                    if ("Result".equals(name)) {
                        return "firefly.std.result.Result";
                    }

                    // Fallback: assume it's a local class in current module
                    String modulePkg = (moduleBasePath != null && !moduleBasePath.isEmpty())
                        ? moduleBasePath.replace('/', '.')
                        : "";
                    if (!modulePkg.isEmpty()) {
                        return modulePkg + "." + name;
                    }
                    return name; // Last resort
            }
        }
        return "java.lang.Object";
    }
    
    /**
     * Resolve type alias to its target type recursively.
     */
    private com.firefly.compiler.ast.type.Type resolveTypeAlias(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof com.firefly.compiler.ast.type.NamedType) {
            String typeName = ((com.firefly.compiler.ast.type.NamedType) type).getName();
            if (typeAliases.containsKey(typeName)) {
                // Recursively resolve in case alias points to another alias
                return resolveTypeAlias(typeAliases.get(typeName));
            }
        }
        return type;
    }
    
    /**
     * Convert a Firefly Type to JVM type descriptor
     */
    private String getTypeDescriptor(com.firefly.compiler.ast.type.Type type) {
        // Resolve type aliases first
        type = resolveTypeAlias(type);
        
        if (type instanceof com.firefly.compiler.ast.type.PrimitiveType) {
            com.firefly.compiler.ast.type.PrimitiveType primType = 
                (com.firefly.compiler.ast.type.PrimitiveType) type;
            String pname = primType.getName();
            switch (pname) {
                case "Int": return "I";
                case "Long": return "J";
                case "Float": return "D";  // Flylang Float maps to JVM double
                case "Double": return "D";
                case "Bool":
                case "Boolean": return "Z";
                case "String": return "Ljava/lang/String;";
                case "Void": return "V";  // void
                default: return "Ljava/lang/Object;";
            }
        } else if (type instanceof com.firefly.compiler.ast.type.NamedType || type instanceof com.firefly.compiler.ast.type.GenericType) {
            String name;
            if (type instanceof com.firefly.compiler.ast.type.NamedType) {
                name = ((com.firefly.compiler.ast.type.NamedType) type).getName();
            } else {
                name = ((com.firefly.compiler.ast.type.GenericType) type).getBaseName();
            }
            // Strip generic arguments from Named/Generic simple names if present
            int lt = name.indexOf('<');
            if (lt > 0) name = name.substring(0, lt);
            switch (name) {
                case "Int": return "I";
                case "Long": return "J";
                case "Float": return "D";  // Flylang Float maps to JVM double
                case "Double": return "D";
                case "Bool":
                case "Boolean": return "Z";
                case "String": return "Ljava/lang/String;";
                case "Void": return "V";  // void
                default:
                    // Check if it's a known struct/spark generated in this unit
                    if (structRegistry.containsKey(name)) {
                        StructMetadata meta = structRegistry.get(name);
                        return "L" + meta.internalName + ";";
                    }
                    // Try to resolve as an imported or JDK/library class
                    java.util.Optional<String> resolved = typeResolver.resolveClassName(name);
                    if (resolved.isPresent()) {
                        return "L" + resolved.get().replace('.', '/') + ";";
                    }
                    // Heuristics for stdlib ADTs
                    if ("Option".equals(name)) {
                        return "Lfirefly/std/option/Option;";
                    }
                    if ("Result".equals(name)) {
                        return "Lfirefly/std/result/Result;";
                    }
                    // Fallback: assume in current module
                    if (moduleBasePath != null && !moduleBasePath.isEmpty()) {
                        return "L" + moduleBasePath + "/" + name + ";";
                    }
                    return "Ljava/lang/Object;";
            }
        }
        return "Ljava/lang/Object;";
    }
    
    /**
     * Get the type name from a Firefly Type (for struct types).
     * Returns null for primitive types.
     */
    private String getTypeNameFromType(com.firefly.compiler.ast.type.Type type) {
        String name = null;
        if (type instanceof com.firefly.compiler.ast.type.NamedType) {
            name = ((com.firefly.compiler.ast.type.NamedType) type).getName();
        } else if (type instanceof com.firefly.compiler.ast.type.GenericType) {
            name = ((com.firefly.compiler.ast.type.GenericType) type).getBaseName();
        }
        if (name != null) {
            // Only return non-primitive type names
            switch (name) {
                case "Int":
                case "Long":
                case "Float":
                case "Double":
                case "Boolean":
                case "String":
                case "Void":
                    return null;
                default:
                    return name;  // This could be a struct name
            }
        }
        return null;
    }
    
    /**
     * Convert a Firefly Type to VarType
     */
    private VarType getVarTypeFromType(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof com.firefly.compiler.ast.type.PrimitiveType) {
            com.firefly.compiler.ast.type.PrimitiveType primType = 
                (com.firefly.compiler.ast.type.PrimitiveType) type;
            String name = primType.getName();
            // Strip generic arguments if present
            int lt = name.indexOf('<');
            if (lt > 0) name = name.substring(0, lt);
            switch (name) {
                case "Int": return VarType.INT;
                case "Long": return VarType.LONG;
                case "Float": return VarType.DOUBLE;  // Flylang Float maps to JVM double
                case "Double": return VarType.DOUBLE;
                case "Bool":
                case "Boolean": return VarType.BOOLEAN;
                case "String": return VarType.STRING;
                case "Void": return VarType.OBJECT;  // Unit maps to void, handle specially
                default: return VarType.OBJECT;
            }
        } else if (type instanceof com.firefly.compiler.ast.type.NamedType || type instanceof com.firefly.compiler.ast.type.GenericType) {
            String name = (type instanceof com.firefly.compiler.ast.type.NamedType)
                ? ((com.firefly.compiler.ast.type.NamedType) type).getName()
                : ((com.firefly.compiler.ast.type.GenericType) type).getBaseName();
            int lt2 = name.indexOf('<');
            if (lt2 > 0) name = name.substring(0, lt2);
            switch (name) {
                case "Int": return VarType.INT;
                case "Long": return VarType.LONG;
                case "Float": return VarType.DOUBLE;  // Flylang Float maps to JVM double
                case "Double": return VarType.DOUBLE;
                case "Bool":
                case "Boolean": return VarType.BOOLEAN;
                case "String": return VarType.STRING;
                case "Void": return VarType.OBJECT;  // Unit maps to void, handle specially
                default: return VarType.OBJECT;
            }
        }
        return VarType.OBJECT;
    }
    
    /**
     * Convert JVM type descriptor to VarType
     */
    private VarType getVarTypeFromDescriptor(String descriptor) {
        switch (descriptor) {
            case "I": return VarType.INT;
            case "J": return VarType.LONG;
            case "F": return VarType.FLOAT;
            case "D": return VarType.DOUBLE;
            case "Z": return VarType.BOOLEAN;
            case "Ljava/lang/String;": return VarType.STRING;
            default: return VarType.OBJECT;
        }
    }
    
    /**
     * Convert Java Class to VarType
     */
    private VarType getVarTypeFromClass(Class<?> clazz) {
        if (clazz == int.class || clazz == Integer.class) return VarType.INT;
        if (clazz == long.class || clazz == Long.class) return VarType.LONG;
        if (clazz == float.class || clazz == Float.class) return VarType.FLOAT;
        if (clazz == double.class || clazz == Double.class) return VarType.DOUBLE;
        if (clazz == boolean.class || clazz == Boolean.class) return VarType.BOOLEAN;
        if (clazz == String.class) return VarType.STRING;
        return VarType.OBJECT;
    }
    
    /**
     * Infer the Java Class type of an expression.
     * This provides best-effort type inference for method resolution.
     */
    private Class<?> inferExpressionType(Expression expr) {
        if (expr instanceof LiteralExpr) {
            LiteralExpr literal = (LiteralExpr) expr;
            switch (literal.getKind()) {
                case INTEGER: return int.class;
                case FLOAT: return double.class;
                case BOOLEAN: return boolean.class;
                case STRING: return String.class;
                default: return Object.class;
            }
        }
        
        if (expr instanceof IdentifierExpr) {
            String name = ((IdentifierExpr) expr).getName();
            
            // Check if it's a local variable
            if (localVariableTypes.containsKey(name)) {
                // First check if we have the actual class tracked
                if (localVariableClasses.containsKey(name)) {
                    return localVariableClasses.get(name);
                }
                // Next, check declared type annotation if available
                if (localVariableDeclaredTypes.containsKey(name)) {
                    String dotted = localVariableDeclaredTypes.get(name);
                    java.util.Optional<Class<?>> c = dotted != null ? typeResolver.getClass(dotted) : java.util.Optional.empty();
                    if (c.isPresent()) return c.get();
                }
                // Fallback to VarType-based inference
                VarType varType = localVariableTypes.get(name);
                return varTypeToClass(varType);
            }
            
            // Check if it's a class name (for .class literals)
            java.util.Optional<String> className = typeResolver.resolveClassName(name);
            if (className.isPresent()) {
                java.util.Optional<Class<?>> clazz = typeResolver.getClass(className.get());
                if (clazz.isPresent()) {
                    return clazz.get();
                }
            }
            
            return Object.class;
        }
        
        if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expr;
            
            // Check for .class literal
            if ("class".equals(fieldAccess.getFieldName())) {
                return Class.class;
            }
            
            return Object.class;
        }
        
        if (expr instanceof CallExpr) {
            // Would require recursive type inference
            return Object.class;
        }
        
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            switch (binary.getOperator()) {
                case EQUAL:
                case NOT_EQUAL:
                case LESS_THAN:
                case LESS_EQUAL:
                case GREATER_THAN:
                case GREATER_EQUAL:
                case AND:
                case OR:
                    return boolean.class;
                    
                case ADD:
                case SUBTRACT:
                case MULTIPLY:
                case DIVIDE:
                case MODULO:
                    // Simplified: assume int
                    return int.class;
                    
                default:
                    return Object.class;
            }
        }
        
        if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            switch (unary.getOperator()) {
                case MINUS:
                    // Numeric unary minus preserves the operand type
                    return inferExpressionType(unary.getOperand());
                case NOT:
                    return boolean.class;
                default:
                    return Object.class;
            }
        }
        
        if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            // Get the type being instantiated
            com.firefly.compiler.ast.type.Type type = newExpr.getType();
            
            // Convert AST type to Class
            String typeName = getClassNameFromType(type);
            if (typeName != null) {
                java.util.Optional<String> resolvedName = typeResolver.resolveClassName(typeName);
                if (resolvedName.isPresent()) {
                    java.util.Optional<Class<?>> clazz = typeResolver.getClass(resolvedName.get());
                    if (clazz.isPresent()) {
                        return clazz.get();
                    }
                }
            }
            return Object.class;
        }
        
        if (expr instanceof ArrayLiteralExpr) {
            // For now, treat as ArrayList (not native array)
            return java.util.ArrayList.class;
        }
        
        // Default to Object for unknown expressions
        return Object.class;
    }
    
    /**
     * Convert VarType to Java Class
     */
    private Class<?> varTypeToClass(VarType varType) {
        switch (varType) {
            case INT: return int.class;
            case LONG: return long.class;
            case FLOAT: return double.class;  // Flylang FLOAT maps to Java double
            case DOUBLE: return double.class;
            case BOOLEAN: return boolean.class;
            case STRING: return String.class;
            case STRING_ARRAY: return String[].class;
            case OBJECT: return Object.class;
            default: return Object.class;
        }
    }

    @Override
    public Void visitTupleType(TupleType type) {
        return null;
    }


    @Override
    public Void visitTypeParameter(TypeParameter type) {
        return null;
    }


    @Override
    public Void visitGenericType(GenericType type) {
        return null;
    }


    @Override
    public Void visitTupleLiteralExpr(TupleLiteralExpr expr) {
        if (methodVisitor == null) return null;
        
        // Tuple literal: (expr1, expr2, expr3, ...)
        // Implemented as ArrayList<Object> in JVM
        
        java.util.List<Expression> elements = expr.getElements();
        
        // Create new ArrayList with size
        methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
        methodVisitor.visitInsn(DUP);
        
        // Call ArrayList(int capacity) constructor
        methodVisitor.visitIntInsn(BIPUSH, elements.size());
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "java/util/ArrayList",
            "<init>",
            "(I)V",
            false
        );
        
        // Add each element to the list
        for (Expression element : elements) {
            // Duplicate list reference for next add() call
            methodVisitor.visitInsn(DUP);
            
            // Evaluate element
            element.accept(this);
            VarType elementType = lastExpressionType;
            
            // Box primitives if necessary
            switch (elementType) {
                case INT:
                    methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        "java/lang/Integer",
                        "valueOf",
                        "(I)Ljava/lang/Integer;",
                        false
                    );
                    break;
                case LONG:
                    methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        "java/lang/Long",
                        "valueOf",
                        "(J)Ljava/lang/Long;",
                        false
                    );
                    break;
                case FLOAT:
                case DOUBLE:
                    methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        "java/lang/Double",
                        "valueOf",
                        "(D)Ljava/lang/Double;",
                        false
                    );
                    break;
                case BOOLEAN:
                    methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        "java/lang/Boolean",
                        "valueOf",
                        "(Z)Ljava/lang/Boolean;",
                        false
                    );
                    break;
                // STRING and OBJECT don't need boxing
            }
            
            // Call add(Object) on the list
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/util/ArrayList",
                "add",
                "(Ljava/lang/Object;)Z",
                false
            );
            
            // Pop the boolean return value from add()
            methodVisitor.visitInsn(POP);
        }
        
        // List is now on stack
        lastExpressionType = VarType.OBJECT;
        return null;
    }


    @Override
    public Void visitThrowExpr(ThrowExpr expr) {
        if (methodVisitor == null) return null;
        
        // Evaluate the exception expression
        expr.getException().accept(this);
        
        // Throw the exception
        // The exception should be on the stack (Object type)
        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Throwable");
        methodVisitor.visitInsn(ATHROW);
        
        codeIsReachable = false;  // Code after throw is unreachable
        
        return null;
    }


    @Override
    public Void visitTryExpr(TryExpr expr) {
        if (methodVisitor == null) return null;
        
        boolean hasCatch = !expr.getCatchClauses().isEmpty();
        boolean hasFinally = expr.getFinallyBlock().isPresent();
        
        if (!hasCatch && !hasFinally) {
            // No catch or finally - just execute try block
            expr.getTryBlock().accept(this);
            return null;
        }
        
        // Labels for try-catch-finally structure
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchEnd = new Label();
        Label finallyStart = hasFinally ? new Label() : null;
        Label finallyEnd = new Label();
        
        // Create labels for each catch clause
        List<Label> catchStarts = new ArrayList<>();
        for (int i = 0; i < expr.getCatchClauses().size(); i++) {
            catchStarts.add(new Label());
        }
        
        // Register catch handlers with ASM
        // Track if we need to wrap caught Throwable into FlyException
        java.util.List<Boolean> wrapToFly = new java.util.ArrayList<>();
        // Register catch handlers for try block
        for (int i = 0; i < expr.getCatchClauses().size(); i++) {
            TryExpr.CatchClause catchClause = expr.getCatchClauses().get(i);
            String exceptionType = "java/lang/Throwable";  // Default
            boolean wrap = false;
            
            if (catchClause.getExceptionType().isPresent()) {
                // Resolve exception type properly
                com.firefly.compiler.ast.type.Type type = catchClause.getExceptionType().get();
                String resolved = resolveExceptionType(type);
                // If catching FlyException, register for Throwable and wrap in handler
                if ("com/firefly/runtime/exceptions/FlyException".equals(resolved)) {
                    exceptionType = "java/lang/Throwable";
                    wrap = true;
                } else {
                    exceptionType = resolved;
                }
            }
            wrapToFly.add(wrap);
            methodVisitor.visitTryCatchBlock(tryStart, tryEnd, catchStarts.get(i), exceptionType);
        }
        
        // If there's a finally block without catch, register it for uncaught exceptions
        if (hasFinally && !hasCatch) {
            methodVisitor.visitTryCatchBlock(tryStart, tryEnd, finallyStart, null);
        }
        
        // If there's a finally block with catches, register finally for uncaught exceptions from catch blocks
        if (hasFinally && hasCatch) {
            for (Label catchStart : catchStarts) {
                methodVisitor.visitTryCatchBlock(catchStart, catchEnd, finallyStart, null);
            }
        }
        
        // Try block
        methodVisitor.visitLabel(tryStart);
        expr.getTryBlock().accept(this);
        methodVisitor.visitLabel(tryEnd);
        
        // If no exception thrown in try block, execute finally and jump to end
        if (hasFinally) {
            expr.getFinallyBlock().get().accept(this);
        }
        methodVisitor.visitJumpInsn(GOTO, finallyEnd);
        
        // Catch clauses
        for (int i = 0; i < expr.getCatchClauses().size(); i++) {
            TryExpr.CatchClause catchClause = expr.getCatchClauses().get(i);
            Label catchStart = catchStarts.get(i);
            
            methodVisitor.visitLabel(catchStart);
            
            // Always store the caught exception first
            int caughtVar = localVarIndex++;
            methodVisitor.visitVarInsn(ASTORE, caughtVar);
            
            // Optionally wrap to FlyException if requested
            // We assume wrapToFly list parallel to catch clauses (constructed above)
            if (wrapToFly.size() > i && wrapToFly.get(i)) {
                // if (!(caught instanceof FlyException)) caught = new FlyException(caught)
                Label isFly = new Label();
                methodVisitor.visitVarInsn(ALOAD, caughtVar);
                methodVisitor.visitTypeInsn(INSTANCEOF, "com/firefly/runtime/exceptions/FlyException");
                methodVisitor.visitJumpInsn(IFNE, isFly);
                // Construct new FlyException(caught)
                methodVisitor.visitTypeInsn(NEW, "com/firefly/runtime/exceptions/FlyException");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitVarInsn(ALOAD, caughtVar);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "com/firefly/runtime/exceptions/FlyException", "<init>", "(Ljava/lang/Throwable;)V", false);
                // Store back
                methodVisitor.visitVarInsn(ASTORE, caughtVar);
                methodVisitor.visitLabel(isFly);
            }
            
            // Bind to user variable if present, else discard
            if (catchClause.getVariableName().isPresent()) {
                String varName = catchClause.getVariableName().get();
                localVariables.put(varName, caughtVar);
                localVariableTypes.put(varName, VarType.OBJECT);
            } else {
                // Not used: pop by loading and POP
                methodVisitor.visitVarInsn(ALOAD, caughtVar);
                methodVisitor.visitInsn(POP);
            }
            
            // Execute catch handler
            catchClause.getHandler().accept(this);
            
            // After catch, execute finally if present
            if (hasFinally) {
                expr.getFinallyBlock().get().accept(this);
            }
            
            // Jump to end
            methodVisitor.visitJumpInsn(GOTO, finallyEnd);
        }
        
        // Mark end of catch blocks
        if (hasCatch) {
            methodVisitor.visitLabel(catchEnd);
        }
        
        // Finally block (for re-throwing uncaught exceptions)
        if (hasFinally) {
            methodVisitor.visitLabel(finallyStart);
            
            // Store exception
            int exceptionVar = localVarIndex++;
            methodVisitor.visitVarInsn(ASTORE, exceptionVar);
            
            // Execute finally block
            expr.getFinallyBlock().get().accept(this);
            
            // Re-throw exception
            methodVisitor.visitVarInsn(ALOAD, exceptionVar);
            methodVisitor.visitInsn(ATHROW);
        }
        
        // End label
        methodVisitor.visitLabel(finallyEnd);
        
        lastExpressionType = VarType.OBJECT;
        return null;
    }


    @Override
    public Void visitTupleAccessExpr(TupleAccessExpr expr) {
        if (methodVisitor == null) return null;
        
        // Tuple field access: tuple.0, tuple.1, etc.
        // Since tuples are implemented as ArrayList, this is equivalent to list.get(index)
        
        // Visit the tuple expression (should be a List/ArrayList)
        expr.getTuple().accept(this);
        
        // Push the index as a constant
        int index = expr.getIndex();
        methodVisitor.visitIntInsn(BIPUSH, index);
        
        // Call List.get(int) -> Object
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "java/util/List",
            "get",
            "(I)Ljava/lang/Object;",
            true  // isInterface = true
        );
        
        // Result is Object (may need unboxing depending on usage)
        lastExpressionType = VarType.OBJECT;
        return null;
    }
    
    /**
     * Generate the init() method for an actor.
     * Returns the initial state (typically 'this' for stateful actors).
     */
    private void generateActorInit(ClassWriter cw, ActorDecl decl, String actorClassName) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "init",
            "()Ljava/lang/Object;",
            "()TState;",
            null
        );
        
        mv.visitCode();
        
        // Save current method visitor
        MethodVisitor savedMv = methodVisitor;
        Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
        Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
        int savedLocalVarIndex = localVarIndex;
        
        methodVisitor = mv;
        localVariables.clear();
        localVariableTypes.clear();
        
        // 'this' (self) is at index 0
        localVariables.put("self", 0);
        localVariableTypes.put("self", VarType.OBJECT);
        localVarIndex = 1;
        
        // Execute init block if present
        BlockExpr initBlock = decl.getInitBlock();
        if (initBlock != null) {
            List<Statement> statements = initBlock.getStatements();
            if (statements != null) {
                for (Statement stmt : statements) {
                    if (stmt != null) {
                        stmt.accept(this);
                    }
                }
            }
        }
        
        // Return 'this' as the initial state
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Restore method visitor
        methodVisitor = savedMv;
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
    }
    
    /**
     * Generate the handle(Message, State) method for an actor.
     * Processes incoming messages using pattern matching on receive cases.
     */
    private void generateActorHandle(ClassWriter cw, ActorDecl decl, String actorClassName) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "handle",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            "(TMessage;TState;)TState;",
            null
        );
        
        mv.visitCode();
        
        // Save current method visitor
        MethodVisitor savedMv = methodVisitor;
        Map<String, Integer> savedLocalVars = new HashMap<>(localVariables);
        Map<String, VarType> savedLocalVarTypes = new HashMap<>(localVariableTypes);
        int savedLocalVarIndex = localVarIndex;
        
        methodVisitor = mv;
        localVariables.clear();
        localVariableTypes.clear();
        
        // Parameters: this (0), message (1), state (2)
        localVariables.put("self", 0);
        localVariables.put("message", 1);
        localVariables.put("state", 2);
        localVariableTypes.put("self", VarType.OBJECT);
        localVariableTypes.put("message", VarType.OBJECT);
        localVariableTypes.put("state", VarType.OBJECT);
        localVarIndex = 3;
        
        // Generate pattern matching for receive cases
        Label endLabel = new Label();
        
        // If no receive cases, just return state unchanged
        if (decl.getReceiveCases().isEmpty()) {
            mv.visitVarInsn(ALOAD, 2);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            
            methodVisitor = savedMv;
            localVariables.clear();
            localVariables.putAll(savedLocalVars);
            localVariableTypes.clear();
            localVariableTypes.putAll(savedLocalVarTypes);
            localVarIndex = savedLocalVarIndex;
            return;
        }
        
        // Generate pattern matching for each receive case
        for (int i = 0; i < decl.getReceiveCases().size(); i++) {
            ActorDecl.ReceiveCase receiveCase = decl.getReceiveCases().get(i);
            Label nextCase = new Label();
            
            // Pattern match the message (parameter at index 1)
            Pattern pattern = receiveCase.getPattern();
            boolean alwaysMatches = generateActorPatternMatch(mv, pattern, 1, nextCase);
            
            // If pattern matches, execute the handler
            Expression handler = receiveCase.getExpression();
            if (handler != null) {
                handler.accept(this);
                
                // The handler result could be:
                // 1. Unit (void) - no return value
                // 2. New state value - update state
                // For now, handlers modify actor fields directly via self.field = value
                // So we just discard any return value and return current state
                if (!lastCallWasVoid && lastExpressionType != null) {
                    // Pop any return value from handler
                    switch (lastExpressionType) {
                        case INT:
                        case BOOLEAN:
                        case FLOAT:
                            mv.visitInsn(POP);
                            break;
                        case LONG:
                        case DOUBLE:
                            // 64-bit values require POP2
                            mv.visitInsn(POP2);
                            break;
                        case OBJECT:
                        case STRING:
                        case STRING_ARRAY:
                            mv.visitInsn(POP);
                            break;
                    }
                }
            }
            
            // After handling, load and return current state (self at index 0)
            // Since self is the state, return self
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(ARETURN);
            
            // Jump removed - we return directly after handling
            
            // If pattern didn't match, try next case
            if (!alwaysMatches) {
                mv.visitLabel(nextCase);
            }
        }
        
        // If no pattern matched, just return state unchanged
        mv.visitLabel(endLabel);
        
        // Return the state (parameter at index 2)
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Restore method visitor
        methodVisitor = savedMv;
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
    }
    
    /**
     * Generate pattern matching bytecode for actor message handling.
     * 
     * @param mv MethodVisitor for generating bytecode
     * @param pattern Pattern to match against
     * @param valueIndex Local variable index of the value to match
     * @param failLabel Label to jump to if pattern doesn't match
     * @return true if pattern always matches (wildcard), false otherwise
     */
    private boolean generateActorPatternMatch(MethodVisitor mv, Pattern pattern, int valueIndex, Label failLabel) {
        // Handle wildcard pattern: matches everything
        if (pattern instanceof com.firefly.compiler.ast.pattern.WildcardPattern) {
            return true;
        }
        
        // Handle typed variable pattern: binds value to variable name with type
        if (pattern instanceof com.firefly.compiler.ast.pattern.TypedVariablePattern) {
            com.firefly.compiler.ast.pattern.TypedVariablePattern typedPattern = 
                (com.firefly.compiler.ast.pattern.TypedVariablePattern) pattern;
            String varName = typedPattern.getName();
            
            // Load the value and bind it to a local variable
            mv.visitVarInsn(ALOAD, valueIndex);
            int varIndex = localVarIndex++;
            localVariables.put(varName, varIndex);
            localVariableTypes.put(varName, VarType.OBJECT);
            mv.visitVarInsn(ASTORE, varIndex);
            
            return true; // Variable patterns always match
        }
        
        // Handle variable pattern: binds value to variable name
        if (pattern instanceof com.firefly.compiler.ast.pattern.VariablePattern) {
            com.firefly.compiler.ast.pattern.VariablePattern varPattern = 
                (com.firefly.compiler.ast.pattern.VariablePattern) pattern;
            String varName = varPattern.getName();
            
            // Load the value and bind it to a local variable
            mv.visitVarInsn(ALOAD, valueIndex);
            int varIndex = localVarIndex++;
            localVariables.put(varName, varIndex);
            localVariableTypes.put(varName, VarType.OBJECT);
            mv.visitVarInsn(ASTORE, varIndex);
            
            return true; // Variable patterns always match
        }
        
        // Handle constructor/type pattern: checks instanceof
        if (pattern instanceof com.firefly.compiler.ast.pattern.TupleStructPattern) {
            com.firefly.compiler.ast.pattern.TupleStructPattern structPattern = 
                (com.firefly.compiler.ast.pattern.TupleStructPattern) pattern;
            
            // Load the message
            mv.visitVarInsn(ALOAD, valueIndex);
            
            // Resolve type name
            String typeName = structPattern.getTypeName();
            
            // Try to resolve the class name
            java.util.Optional<String> resolvedClass = typeResolver.resolveClassName(typeName);
            String internalTypeName;
            if (resolvedClass.isPresent()) {
                internalTypeName = resolvedClass.get().replace('.', '/');
            } else {
                // Try in current package
                String packageName = this.className.contains("/") ? 
                    this.className.substring(0, this.className.lastIndexOf("/")) : "";
                internalTypeName = packageName.isEmpty() ? typeName : packageName + "/" + typeName;
            }
            
            // Check instanceof
            mv.visitTypeInsn(INSTANCEOF, internalTypeName);
            // If not instance of type, jump to fail label
            mv.visitJumpInsn(IFEQ, failLabel);
            
            // If we have nested patterns (arguments), extract and match them
            List<Pattern> nestedPatterns = structPattern.getPatterns();
            if (!nestedPatterns.isEmpty()) {
                // Load message and cast to type
                mv.visitVarInsn(ALOAD, valueIndex);
                mv.visitTypeInsn(CHECKCAST, internalTypeName);
                
                // Store in temp variable
                int tempIndex = localVarIndex++;
                mv.visitVarInsn(ASTORE, tempIndex);
                
                // Destructure each nested pattern
                for (int i = 0; i < nestedPatterns.size(); i++) {
                    Pattern nestedPattern = nestedPatterns.get(i);
                    
                    // Load the message instance
                    mv.visitVarInsn(ALOAD, tempIndex);
                    
                    // Access field i
                    // For data types, fields are typically named field0, field1, etc.
                    // or we can use positional getters
                    String fieldName = "field" + i;
                    
                    // Try field access (data types use public fields)
                    mv.visitFieldInsn(
                        GETFIELD,
                        internalTypeName,
                        fieldName,
                        "Ljava/lang/Object;"
                    );
                    
                    // Store field value
                    int fieldValueIndex = localVarIndex++;
                    mv.visitVarInsn(ASTORE, fieldValueIndex);
                    
                    // Recursively match nested pattern
                    boolean alwaysMatches = generateActorPatternMatch(
                        mv,
                        nestedPattern,
                        fieldValueIndex,
                        failLabel
                    );
                    
                    // If pattern doesn't match, we'll jump to failLabel
                }
            }
            
            return false;
        }
        
        // Handle literal pattern: check equality
        if (pattern instanceof com.firefly.compiler.ast.pattern.LiteralPattern) {
            com.firefly.compiler.ast.pattern.LiteralPattern litPattern = 
                (com.firefly.compiler.ast.pattern.LiteralPattern) pattern;
            
            // Load the value
            mv.visitVarInsn(ALOAD, valueIndex);
            
            // Load the literal
            litPattern.getLiteral().accept(this);
            
            // Box primitive if needed
            if (lastExpressionType == VarType.INT) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", 
                    "(I)Ljava/lang/Integer;", false);
            } else if (lastExpressionType == VarType.BOOLEAN) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", 
                    "(Z)Ljava/lang/Boolean;", false);
            } else if (lastExpressionType == VarType.FLOAT) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", 
                    "(D)Ljava/lang/Double;", false);
            }
            
            // Call equals
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", 
                "(Ljava/lang/Object;)Z", false);
            
            // If not equal, jump to next pattern
            mv.visitJumpInsn(IFEQ, failLabel);
            
            return false;
        }
        
        // Default: treat as wildcard (always matches)
        return true;
    }
    
    /**
     * Generate constructor for a struct.
     * Constructor takes all fields as parameters and initializes them.
     */
    private void generateStructConstructor(ClassWriter cw, String className, List<StructDecl.Field> fields) {
        // Build constructor descriptor: (field1Type, field2Type, ...)V
        StringBuilder descriptor = new StringBuilder("(");
        for (StructDecl.Field field : fields) {
            descriptor.append(getTypeDescriptor(field.getType()));
        }
        descriptor.append(")V");
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "<init>",
            descriptor.toString(),
            null,
            null
        );
        
        // Record parameter names for tooling (e.g., Jackson ParameterNamesModule)
        for (StructDecl.Field field : fields) {
            mv.visitParameter(field.getName(), 0);
        }
        
        mv.visitCode();
        
        // Call super() - Object constructor
        mv.visitVarInsn(ALOAD, 0);  // this
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        
        // Initialize each field
        int paramIndex = 1;  // Parameter indices start at 1 (0 is 'this')
        for (StructDecl.Field field : fields) {
            mv.visitVarInsn(ALOAD, 0);  // this
            
            // Load parameter based on type
            VarType varType = getVarTypeFromType(field.getType());
            switch (varType) {
                case INT:
                case BOOLEAN:
                    mv.visitVarInsn(ILOAD, paramIndex);
                    paramIndex++;
                    break;
                case FLOAT:
                    mv.visitVarInsn(DLOAD, paramIndex);
                    paramIndex += 2; // double takes 2 slots
                    break;
                case STRING:
                case STRING_ARRAY:
                case OBJECT:
                    mv.visitVarInsn(ALOAD, paramIndex);
                    paramIndex++;
                    break;
            }
            
            // Store in field
            String fieldDescriptor = getTypeDescriptor(field.getType());
            mv.visitFieldInsn(PUTFIELD, className, field.getName(), fieldDescriptor);
        }
        
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate getter method for a struct field.
     */
    private void generateStructGetter(ClassWriter cw, String className, StructDecl.Field field) {
        String fieldName = field.getName();
        String fieldDescriptor = getTypeDescriptor(field.getType());
        
        // Getter name: getFieldName() or isFieldName() for booleans
        // JavaBean getter names for Jackson compatibility
        String getterName;
        com.firefly.compiler.ast.type.Type ft = field.getType();
        VarType vt = getVarTypeFromType(ft);
        if (vt == VarType.BOOLEAN) {
            getterName = (fieldName.startsWith("is") ? fieldName : "is" + capitalize(fieldName));
        } else {
            getterName = "get" + capitalize(fieldName);
        }
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            getterName,
            "()" + fieldDescriptor,
            null,
            null
        );
        
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);  // this
        mv.visitFieldInsn(GETFIELD, className, fieldName, fieldDescriptor);
        
        // Return based on type
        VarType varType = getVarTypeFromType(field.getType());
        switch (varType) {
            case INT:
            case BOOLEAN:
                mv.visitInsn(IRETURN);
                break;
            case FLOAT:
                mv.visitInsn(DRETURN);
                break;
            case STRING:
            case STRING_ARRAY:
            case OBJECT:
                mv.visitInsn(ARETURN);
                break;
        }
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate equals() method for struct.
     */
    private void generateStructEquals(ClassWriter cw, String className, List<StructDecl.Field> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "equals",
            "(Ljava/lang/Object;)Z",
            null,
            null
        );
        
        mv.visitCode();
        
        Label notEqual = new Label();
        Label isEqual = new Label();
        
        // Check if same reference
        mv.visitVarInsn(ALOAD, 0);  // this
        mv.visitVarInsn(ALOAD, 1);  // other
        mv.visitJumpInsn(IF_ACMPEQ, isEqual);
        
        // Check if other is null
        mv.visitVarInsn(ALOAD, 1);
        mv.visitJumpInsn(IFNULL, notEqual);
        
        // Check if same class
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        mv.visitJumpInsn(IF_ACMPNE, notEqual);
        
        // Cast other to same type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitVarInsn(ASTORE, 2);  // Store as local var
        
        // Compare each field
        for (StructDecl.Field field : fields) {
            String fieldName = field.getName();
            String fieldDescriptor = getTypeDescriptor(field.getType());
            VarType varType = getVarTypeFromType(field.getType());
            
            mv.visitVarInsn(ALOAD, 0);  // this
            mv.visitFieldInsn(GETFIELD, className, fieldName, fieldDescriptor);
            
            mv.visitVarInsn(ALOAD, 2);  // other (casted)
            mv.visitFieldInsn(GETFIELD, className, fieldName, fieldDescriptor);
            
            // Compare based on type
            switch (varType) {
                case INT:
                case BOOLEAN:
                    mv.visitJumpInsn(IF_ICMPNE, notEqual);
                    break;
                case FLOAT:
                    mv.visitInsn(FCMPG);
                    mv.visitJumpInsn(IFNE, notEqual);
                    break;
                case STRING:
                case STRING_ARRAY:
                case OBJECT:
                    // Use Objects.equals(a, b) for null safety
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        "java/util/Objects",
                        "equals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                        false
                    );
                    mv.visitJumpInsn(IFEQ, notEqual);
                    break;
            }
        }
        
        // All fields equal
        mv.visitLabel(isEqual);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        
        // Not equal
        mv.visitLabel(notEqual);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(IRETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate hashCode() method for struct.
     */
    private void generateStructHashCode(ClassWriter cw, String className, List<StructDecl.Field> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "hashCode",
            "()I",
            null,
            null
        );
        
        mv.visitCode();
        
        if (fields.isEmpty()) {
            // No fields - return constant
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
        } else {
            // Use Objects.hash(field1, field2, ...)
            mv.visitIntInsn(BIPUSH, fields.size());
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            
            int index = 0;
            for (StructDecl.Field field : fields) {
                mv.visitInsn(DUP);  // Duplicate array reference
                mv.visitIntInsn(BIPUSH, index);
                
                // Load field value
                mv.visitVarInsn(ALOAD, 0);  // this
                String fieldDescriptor = getTypeDescriptor(field.getType());
                mv.visitFieldInsn(GETFIELD, className, field.getName(), fieldDescriptor);
                
                // Box primitive types
                VarType varType = getVarTypeFromType(field.getType());
                switch (varType) {
                    case INT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        break;
                    case BOOLEAN:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                        break;
                    case FLOAT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                        break;
                    // STRING, STRING_ARRAY, OBJECT are already objects
                }
                
                mv.visitInsn(AASTORE);  // Store in array
                index++;
            }
            
            // Call Objects.hash(Object[])
            mv.visitMethodInsn(
                INVOKESTATIC,
                "java/util/Objects",
                "hash",
                "([Ljava/lang/Object;)I",
                false
            );
            mv.visitInsn(IRETURN);
        }
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate toString() method for struct.
     */
    private void generateStructToString(ClassWriter cw, String className, List<StructDecl.Field> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "toString",
            "()Ljava/lang/String;",
            null,
            null
        );
        
        mv.visitCode();
        
        // Use StringBuilder for efficiency
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        
        // Append class name
        mv.visitLdcInsn(className + "(");
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        );
        
        // Append each field
        for (int i = 0; i < fields.size(); i++) {
            StructDecl.Field field = fields.get(i);
            
            // Append field name
            mv.visitLdcInsn(field.getName() + "=");
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false
            );
            
            // Append field value
            mv.visitVarInsn(ALOAD, 0);  // this
            String fieldDescriptor = getTypeDescriptor(field.getType());
            mv.visitFieldInsn(GETFIELD, className, field.getName(), fieldDescriptor);
            
            // Append based on type
            VarType varType = getVarTypeFromType(field.getType());
            String appendDescriptor;
            switch (varType) {
                case INT:
                    appendDescriptor = "(I)Ljava/lang/StringBuilder;";
                    break;
                case BOOLEAN:
                    appendDescriptor = "(Z)Ljava/lang/StringBuilder;";
                    break;
                case FLOAT:
                    appendDescriptor = "(F)Ljava/lang/StringBuilder;";
                    break;
                default:
                    appendDescriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                    break;
            }
            
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                appendDescriptor,
                false
            );
            
            // Add comma if not last field
            if (i < fields.size() - 1) {
                mv.visitLdcInsn(", ");
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                );
            }
        }
        
        // Append closing paren
        mv.visitLdcInsn(")");
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        );
        
        // Call toString()
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "toString",
            "()Ljava/lang/String;",
            false
        );
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate a variant class for a data type (ADT).
     * Each variant extends the base data class.
     */
    private void generateDataVariant(String baseInternalName, DataDecl.Variant variant) {
        String variantInternalName = baseInternalName + "$" + variant.getName();
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(
            V1_8,
            ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
            variantInternalName,
            null,
            baseInternalName,  // Extends base data class
            null
        );
        
        // Generate fields for variant
        for (DataDecl.VariantField field : variant.getFields()) {
            String fieldName = field.getName().orElse("value" + variant.getFields().indexOf(field));
            String fieldDescriptor = getTypeDescriptor(field.getType());
            
            cw.visitField(
                ACC_PUBLIC + ACC_FINAL,
                fieldName,
                fieldDescriptor,
                null,
                null
            ).visitEnd();
        }
        
        // Generate constructor
        generateVariantConstructor(cw, baseInternalName, variantInternalName, variant.getFields());
        
        // Generate equals, hashCode, toString
        generateVariantEquals(cw, variantInternalName, variant.getFields());
        generateVariantHashCode(cw, variantInternalName, variant.getFields());
        generateVariantToString(cw, variantInternalName, variant.getFields());
        
        cw.visitEnd();
        generatedClasses.put(variantInternalName, cw.toByteArray());
    }
    
    /**
     * Generate constructor for a data variant.
     */
    private void generateVariantConstructor(ClassWriter cw, String baseName, String variantName, 
                                           List<DataDecl.VariantField> fields) {
        // Build constructor descriptor
        StringBuilder descriptor = new StringBuilder("(");
        for (DataDecl.VariantField field : fields) {
            descriptor.append(getTypeDescriptor(field.getType()));
        }
        descriptor.append(")V");
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "<init>",
            descriptor.toString(),
            null,
            null
        );
        
        mv.visitCode();
        
        // Call super constructor (base data class)
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, baseName, "<init>", "()V", false);
        
        // Initialize fields
        int paramIndex = 1;
        for (int i = 0; i < fields.size(); i++) {
            DataDecl.VariantField field = fields.get(i);
            String fieldName = field.getName().orElse("value" + i);
            
            mv.visitVarInsn(ALOAD, 0);  // this
            
            VarType varType = getVarTypeFromType(field.getType());
            switch (varType) {
                case INT:
                case BOOLEAN:
                    mv.visitVarInsn(ILOAD, paramIndex);
                    paramIndex++;
                    break;
                case FLOAT:
                    mv.visitVarInsn(DLOAD, paramIndex);
                    paramIndex += 2; // double takes 2 slots
                    break;
                case STRING:
                case STRING_ARRAY:
                case OBJECT:
                    mv.visitVarInsn(ALOAD, paramIndex);
                    paramIndex++;
                    break;
            }
            
            String fieldDescriptor = getTypeDescriptor(field.getType());
            mv.visitFieldInsn(PUTFIELD, variantName, fieldName, fieldDescriptor);
        }
        
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate equals() for variant.
     */
    private void generateVariantEquals(ClassWriter cw, String variantName, List<DataDecl.VariantField> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "equals",
            "(Ljava/lang/Object;)Z",
            null,
            null
        );
        
        mv.visitCode();
        
        Label notEqual = new Label();
        Label isEqual = new Label();
        
        // Check if same reference
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitJumpInsn(IF_ACMPEQ, isEqual);
        
        // Check if null or different class
        mv.visitVarInsn(ALOAD, 1);
        mv.visitJumpInsn(IFNULL, notEqual);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        mv.visitJumpInsn(IF_ACMPNE, notEqual);
        
        // Cast and compare fields
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, variantName);
        mv.visitVarInsn(ASTORE, 2);
        
        for (int i = 0; i < fields.size(); i++) {
            DataDecl.VariantField field = fields.get(i);
            String fieldName = field.getName().orElse("value" + i);
            String fieldDescriptor = getTypeDescriptor(field.getType());
            VarType varType = getVarTypeFromType(field.getType());
            
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, variantName, fieldName, fieldDescriptor);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitFieldInsn(GETFIELD, variantName, fieldName, fieldDescriptor);
            
            switch (varType) {
                case INT:
                case BOOLEAN:
                    mv.visitJumpInsn(IF_ICMPNE, notEqual);
                    break;
                case FLOAT:
                    mv.visitInsn(DCMPL);
                    mv.visitJumpInsn(IFNE, notEqual);
                    break;
                case STRING:
                case STRING_ARRAY:
                case OBJECT:
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        "java/util/Objects",
                        "equals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                        false
                    );
                    mv.visitJumpInsn(IFEQ, notEqual);
                    break;
            }
        }
        
        mv.visitLabel(isEqual);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        
        mv.visitLabel(notEqual);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(IRETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate hashCode() for variant.
     */
    private void generateVariantHashCode(ClassWriter cw, String variantName, List<DataDecl.VariantField> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "hashCode",
            "()I",
            null,
            null
        );
        
        mv.visitCode();
        
        if (fields.isEmpty()) {
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
        } else {
            // Use Objects.hash()
            mv.visitIntInsn(BIPUSH, fields.size());
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            
            for (int i = 0; i < fields.size(); i++) {
                DataDecl.VariantField field = fields.get(i);
                String fieldName = field.getName().orElse("value" + i);
                String fieldDescriptor = getTypeDescriptor(field.getType());
                VarType varType = getVarTypeFromType(field.getType());
                
                mv.visitInsn(DUP);
                mv.visitIntInsn(BIPUSH, i);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, variantName, fieldName, fieldDescriptor);
                
                // Box primitives
                switch (varType) {
                    case INT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        break;
                    case BOOLEAN:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                        break;
                    case FLOAT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                        break;
                }
                
                mv.visitInsn(AASTORE);
            }
            
            mv.visitMethodInsn(
                INVOKESTATIC,
                "java/util/Objects",
                "hash",
                "([Ljava/lang/Object;)I",
                false
            );
            mv.visitInsn(IRETURN);
        }
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate toString() for variant.
     */
    private void generateVariantToString(ClassWriter cw, String variantName, List<DataDecl.VariantField> fields) {
        // Extract simple name (after $)
        String simpleName = variantName.contains("$") ? 
            variantName.substring(variantName.lastIndexOf('$') + 1) : variantName;
        
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "toString",
            "()Ljava/lang/String;",
            null,
            null
        );
        
        mv.visitCode();
        
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        
        mv.visitLdcInsn(simpleName + "(");
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        );
        
        for (int i = 0; i < fields.size(); i++) {
            DataDecl.VariantField field = fields.get(i);
            String fieldName = field.getName().orElse("value" + i);
            String fieldDescriptor = getTypeDescriptor(field.getType());
            VarType varType = getVarTypeFromType(field.getType());
            
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, variantName, fieldName, fieldDescriptor);
            
            String appendDescriptor;
            switch (varType) {
                case INT:
                    appendDescriptor = "(I)Ljava/lang/StringBuilder;";
                    break;
                case BOOLEAN:
                    appendDescriptor = "(Z)Ljava/lang/StringBuilder;";
                    break;
                case FLOAT:
                    appendDescriptor = "(F)Ljava/lang/StringBuilder;";
                    break;
                default:
                    appendDescriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                    break;
            }
            
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                appendDescriptor,
                false
            );
            
            if (i < fields.size() - 1) {
                mv.visitLdcInsn(", ");
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                );
            }
        }
        
        mv.visitLdcInsn(")");
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        );
        
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "toString",
            "()Ljava/lang/String;",
            false
        );
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate method signature for trait.
     */
    private void generateTraitMethod(ClassWriter cw, TraitDecl.FunctionSignature method) {
        // Build method descriptor
        StringBuilder descriptor = new StringBuilder("(");
        for (FunctionDecl.Parameter param : method.getParameters()) {
            descriptor.append(getTypeDescriptor(param.getType()));
        }
        descriptor.append(")");
        descriptor.append(getTypeDescriptor(method.getReturnType()));
        
        // Generate generic signature if method has type parameters
        String signature = null;
        if (!method.getTypeParameters().isEmpty()) {
            signature = generateMethodGenericSignature(method.getTypeParameters(), method.getParameters(), method.getReturnType());
        }
        
        // Add abstract method to interface
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC | ACC_ABSTRACT,
            method.getName(),
            descriptor.toString(),
            signature,
            null
        );
        mv.visitEnd();
    }
    
    /**
     * Generate generic signature for class/interface.
     * Format: <T:Ljava/lang/Object;>Ljava/lang/Object;Lpackage/Interface;
     */
    private String generateGenericSignature(List<TypeParameter> typeParams, String superClass, String[] interfaces) {
        if (typeParams.isEmpty()) {
            return null;
        }
        
        StringBuilder sig = new StringBuilder();
        
        // Type parameters: <T:Ljava/lang/Object;U:Ljava/lang/Number;>
        sig.append("<");
        for (TypeParameter typeParam : typeParams) {
            sig.append(typeParam.getName()).append(":");
            if (typeParam.hasBounds() && !typeParam.getBounds().isEmpty()) {
                // Use first bound
                sig.append("L").append(getClassNameFromType(typeParam.getBounds().get(0)).replace('.', '/')).append(";");
            } else {
                sig.append("Ljava/lang/Object;");
            }
        }
        sig.append(">");
        
        // Superclass
        if (superClass != null) {
            sig.append("L").append(superClass.replace('.', '/')).append(";");
        } else {
            sig.append("Ljava/lang/Object;");
        }
        
        // Interfaces
        if (interfaces != null) {
            for (String iface : interfaces) {
                sig.append("L").append(iface.replace('.', '/')).append(";");
            }
        }
        
        return sig.toString();
    }
    
    /**
     * Generate generic signature for method.
     * Format: <T:Ljava/lang/Object;>(TT;)TT;
     */
    private String generateMethodGenericSignature(List<TypeParameter> typeParams, 
                                                   List<FunctionDecl.Parameter> params, 
                                                   com.firefly.compiler.ast.type.Type returnType) {
        if (typeParams.isEmpty()) {
            return null;
        }
        
        StringBuilder sig = new StringBuilder();
        
        // Type parameters
        sig.append("<");
        for (TypeParameter typeParam : typeParams) {
            sig.append(typeParam.getName()).append(":");
            if (typeParam.hasBounds() && !typeParam.getBounds().isEmpty()) {
                // Use first bound
                sig.append("L").append(getClassNameFromType(typeParam.getBounds().get(0)).replace('.', '/')).append(";");
            } else {
                sig.append("Ljava/lang/Object;");
            }
        }
        sig.append(">");
        
        // Parameters
        sig.append("(");
        for (FunctionDecl.Parameter param : params) {
            sig.append(getGenericTypeDescriptor(param.getType(), typeParams));
        }
        sig.append(")");
        
        // Return type
        sig.append(getGenericTypeDescriptor(returnType, typeParams));
        
        return sig.toString();
    }
    
    /**
     * Get type descriptor for generics-aware types.
     */
    private String getGenericTypeDescriptor(com.firefly.compiler.ast.type.Type type, List<TypeParameter> typeParams) {
        if (type instanceof NamedType) {
            String typeName = ((NamedType) type).getName();
            // Check if it's a type parameter
            for (TypeParameter tp : typeParams) {
                if (tp.getName().equals(typeName)) {
                    return "T" + typeName + ";";
                }
            }
        }
        return getTypeDescriptor(type);
    }
    
    /**
     * Generate bridge method for generic method override.
     * Bridge methods are needed when a generic method is overridden with a specific type.
     * Example: class Box<T> { T get() } -> class IntBox extends Box<Integer> { Integer get() }
     * JVM needs: synthetic Object get() { return (Integer) this.get(); }
     */
    private void generateBridgeMethod(ClassWriter cw, String methodName, 
                                      String erasedDescriptor, String actualDescriptor,
                                      String className) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
            methodName,
            erasedDescriptor,
            null,
            null
        );
        
        mv.visitCode();
        
        // Load 'this'
        mv.visitVarInsn(ALOAD, 0);
        
        // Load parameters (if any)
        // For now, assuming no parameters for simplicity
        // In a complete implementation, we'd analyze the descriptors
        
        // Call the actual method
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            className,
            methodName,
            actualDescriptor,
            false
        );
        
        // Return (ARETURN for objects, type-specific returns for primitives)
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Check if a type is a type parameter.
     */
    private boolean isTypeParameter(com.firefly.compiler.ast.type.Type type, List<TypeParameter> typeParams) {
        if (type instanceof NamedType) {
            String typeName = ((NamedType) type).getName();
            for (TypeParameter tp : typeParams) {
                if (tp.getName().equals(typeName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Generate @derive implementations for Show, Eq, Hash, Json, Binary traits.
     * Example: @derive(Show, Eq, Hash, Json, Binary)
     */
    private void generateDeriveImplementations(ClassWriter cw, String className, SparkDecl decl) {
        Optional<Annotation> deriveAnn = decl.getAnnotation("derive");
        if (!deriveAnn.isPresent()) {
            return;
        }
        
        // Parse which traits to derive from annotation arguments
        Object value = deriveAnn.get().getValue();
        if (value == null) {
            return;
        }
        
        // Value can be a single string or a list
        java.util.List<String> traits = new java.util.ArrayList<>();
        if (value instanceof String) {
            traits.add((String) value);
        } else if (value instanceof java.util.List) {
            for (Object item : (java.util.List<?>) value) {
                if (item instanceof String) {
                    traits.add((String) item);
                }
            }
        }
        
        // Generate implementations for each trait
        for (String trait : traits) {
            switch (trait) {
                case "Show":
                    generateShowImplementation(cw, className, decl.getFields());
                    break;
                case "Eq":
                    // Already generated via generateSparkEquals
                    break;
                case "Hash":
                    // Already generated via generateSparkHashCode
                    break;
                case "Json":
                    generateJsonImplementation(cw, className, decl.getFields());
                    break;
                case "Binary":
                    generateBinaryImplementation(cw, className, decl.getFields());
                    break;
                case "Ord":
                    generateOrdImplementation(cw, className, decl.getFields());
                    break;
            }
        }
    }
    
    /**
     * Generate Show trait implementation: show() -> String
     * Returns a human-readable representation (similar to toString but more structured)
     */
    private void generateShowImplementation(ClassWriter cw, String className,
                                           List<SparkDecl.SparkField> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "show",
            "()Ljava/lang/String;",
            null,
            null
        );
        mv.visitCode();
        
        // Delegate to toString for now (could be more sophisticated)
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, className, "toString", "()Ljava/lang/String;", false);
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate Json trait implementation: toJson() -> String, fromJson(String) -> T
     */
    private void generateJsonImplementation(ClassWriter cw, String className,
                                           List<SparkDecl.SparkField> fields) {
        // Generate toJson()
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "toJson",
            "()Ljava/lang/String;",
            null,
            null
        );
        mv.visitCode();
        
        // Build JSON string: {"field1": value1, "field2": value2}
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        
        mv.visitLdcInsn("{");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                          "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        
        for (int i = 0; i < fields.size(); i++) {
            SparkDecl.SparkField field = fields.get(i);
            
            // Add field name in quotes
            mv.visitLdcInsn("\"");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                              "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            mv.visitLdcInsn(field.getName());
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                              "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            mv.visitLdcInsn("\": ");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                              "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            
            // Add field value (quote strings)
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            
            // If String type, add quotes around value
            if (field.getType() instanceof PrimitiveType && 
                ((PrimitiveType) field.getType()).getKind() == PrimitiveType.Kind.STRING) {
                mv.visitLdcInsn("\"");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                  "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                  "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
                mv.visitLdcInsn("\"");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                  "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                // Append value directly
                String appendDesc = getAppendDescriptor(field.getType());
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                  appendDesc, false);
            }
            
            // Add comma if not last
            if (i < fields.size() - 1) {
                mv.visitLdcInsn(", ");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                  "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
        }
        
        mv.visitLdcInsn("}");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                          "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                          "()Ljava/lang/String;", false);
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Generate static fromJson(String) method for deserialization
        // For now, this is a stub that would require JSON parsing library
        MethodVisitor mvFromJson = cw.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            "fromJson",
            "(Ljava/lang/String;)L" + className + ";",
            null,
            new String[]{"java/lang/Exception"}
        );
        mvFromJson.visitCode();
        
        // Use Gson for JSON parsing
        // Create Gson instance
        mvFromJson.visitTypeInsn(NEW, "com/google/gson/Gson");
        mvFromJson.visitInsn(DUP);
        mvFromJson.visitMethodInsn(
            INVOKESPECIAL,
            "com/google/gson/Gson",
            "<init>",
            "()V",
            false
        );
        mvFromJson.visitVarInsn(ASTORE, 1);
        
        // Parse JSON string to object
        // gson.fromJson(json, ClassName.class)
        mvFromJson.visitVarInsn(ALOAD, 1);
        mvFromJson.visitVarInsn(ALOAD, 0);
        
        // Get class literal for the spark type
        mvFromJson.visitLdcInsn(org.objectweb.asm.Type.getObjectType(className));
        
        mvFromJson.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/google/gson/Gson",
            "fromJson",
            "(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
            false
        );
        
        // Cast to the spark class
        mvFromJson.visitTypeInsn(CHECKCAST, className);
        mvFromJson.visitInsn(ARETURN);
        
        mvFromJson.visitMaxs(0, 0);
        mvFromJson.visitEnd();
    }
    
    /**
     * Generate Binary trait implementation for actor serialization
     */
    private void generateBinaryImplementation(ClassWriter cw, String className,
                                             List<SparkDecl.SparkField> fields) {
        // Generate toBinary() -> byte[]
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "toBinary",
            "()[B",
            null,
            null
        );
        mv.visitCode();
        
        // Use Java serialization for simplicity
        // In production, would use more efficient binary format
        mv.visitTypeInsn(NEW, "java/io/ByteArrayOutputStream");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/io/ByteArrayOutputStream", "<init>", "()V", false);
        mv.visitVarInsn(ASTORE, 1);
        
        mv.visitTypeInsn(NEW, "java/io/ObjectOutputStream");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, "java/io/ObjectOutputStream", "<init>",
                          "(Ljava/io/OutputStream;)V", false);
        mv.visitVarInsn(ASTORE, 2);
        
        // Write each field
        for (SparkDecl.SparkField field : fields) {
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            
            // Box primitives if needed
            if (field.getType() instanceof PrimitiveType) {
                boxPrimitive(mv, (PrimitiveType) field.getType());
            }
            
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/ObjectOutputStream", "writeObject",
                              "(Ljava/lang/Object;)V", false);
        }
        
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "toByteArray",
                          "()[B", false);
        mv.visitInsn(ARETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate Ord trait implementation: compareTo(T) -> Int
     */
    private void generateOrdImplementation(ClassWriter cw, String className,
                                          List<SparkDecl.SparkField> fields) {
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "compareTo",
            "(L" + className + ";)I",
            null,
            null
        );
        mv.visitCode();
        
        // Compare fields lexicographically
        Label equalLabel = new Label();
        Label endLabel = new Label();
        
        for (int i = 0; i < fields.size(); i++) {
            SparkDecl.SparkField field = fields.get(i);
            
            // Load this.field
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            
            // Load other.field
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETFIELD, className, field.getName(), getTypeDescriptor(field.getType()));
            
            // Compare based on type
            if (field.getType() instanceof PrimitiveType) {
                PrimitiveType pt = (PrimitiveType) field.getType();
                switch (pt.getKind()) {
                    case INT:
                        // Use Integer.compare(int, int)
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "compare",
                                          "(II)I", false);
                        break;
                    case FLOAT:
                        // Use Double.compare(double, double)
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "compare",
                                          "(DD)I", false);
                        break;
                    case BOOL:
                        // Use Boolean.compare(boolean, boolean)
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "compare",
                                          "(ZZ)I", false);
                        break;
                    case STRING:
                        // Use String.compareTo(String)
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "compareTo",
                                          "(Ljava/lang/String;)I", false);
                        break;
                }
            } else {
                // For objects, assume Comparable
                mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Comparable", "compareTo",
                                  "(Ljava/lang/Object;)I", true);
            }
            
            // Store comparison result
            mv.visitInsn(DUP);
            mv.visitVarInsn(ISTORE, 2);
            
            // If result != 0, return it
            if (i < fields.size() - 1) {
                mv.visitJumpInsn(IFEQ, equalLabel);
                mv.visitVarInsn(ILOAD, 2);
                mv.visitInsn(IRETURN);
                mv.visitLabel(equalLabel);
            }
        }
        
        // All fields equal, return 0
        mv.visitInsn(ICONST_0);
        mv.visitInsn(IRETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Generate @travelable wrapper for time-travel debugging.
     * Wraps the spark with history tracking.
     */
    private void generateTravelableWrapper(ClassWriter cw, String className, SparkDecl decl) {
        // Generate history tracking field
        cw.visitField(
            ACC_PRIVATE,
            "_history",
            "Ljava/util/List;",
            "Ljava/util/List<L" + className + ";>;",
            null
        ).visitEnd();
        
        // Generate history() method -> List<T>
        MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            "history",
            "()Ljava/util/List;",
            "()Ljava/util/List<L" + className + ";>;",
            null
        );
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "_history", "Ljava/util/List;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Generate previous() method -> T
        mv = cw.visitMethod(
            ACC_PUBLIC,
            "previous",
            "()L" + className + ";",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "_history", "Ljava/util/List;");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
        mv.visitInsn(ICONST_2);
        mv.visitInsn(ISUB);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        // Generate revert(int) method -> T
        mv = cw.visitMethod(
            ACC_PUBLIC,
            "revert",
            "(I)L" + className + ";",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "_history", "Ljava/util/List;");
        mv.visitVarInsn(ILOAD, 1);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    /**
     * Get StringBuilder append descriptor for a type.
     */
    private String getAppendDescriptor(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) type;
            switch (pt.getKind()) {
                case INT:
                    return "(I)Ljava/lang/StringBuilder;";
                case BOOL:
                    return "(Z)Ljava/lang/StringBuilder;";
                case FLOAT:
                    return "(D)Ljava/lang/StringBuilder;";
                default:
                    return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            }
        }
        return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
    }
    
    /**
     * Box a primitive value on the stack.
     */
    private void boxPrimitive(MethodVisitor mv, PrimitiveType type) {
        switch (type.getKind()) {
            case INT:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf",
                                  "(I)Ljava/lang/Integer;", false);
                break;
            case BOOL:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf",
                                  "(Z)Ljava/lang/Boolean;", false);
                break;
            case FLOAT:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf",
                                  "(D)Ljava/lang/Double;", false);
                break;
            // STRING is already an object
        }
    }
    
    /**
     * Metadata for struct types - stores field information for type resolution
     */
    private static class StructMetadata {
        final String name;              // simple name (e.g., User)
        final String internalName;      // internal JVM name with module path (e.g., com/example/User)
        final List<FieldMetadata> fields;
        
        StructMetadata(String name, String internalName, List<FieldMetadata> fields) {
            this.name = name;
            this.internalName = internalName;
            this.fields = fields;
        }
        
        static class FieldMetadata {
            final String name;
            final com.firefly.compiler.ast.type.Type type;
            
            FieldMetadata(String name, com.firefly.compiler.ast.type.Type type) {
                this.name = name;
                this.type = type;
            }
        }
    }

    private String resolveStructInternalName(String simpleName) {
        StructMetadata meta = structRegistry.get(simpleName);
        if (meta != null) return meta.internalName;
        // Try resolving as a nested variant class from imports (e.g., Result$Ok)
        java.util.Optional<String> nested = typeResolver.resolveVariantNestedClass(simpleName);
        if (nested.isPresent()) {
            return nested.get().replace('.', '/');
        }
        if (moduleBasePath != null && !moduleBasePath.isEmpty()) {
            return moduleBasePath + "/" + simpleName;
        }
        return simpleName;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toUpperCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
