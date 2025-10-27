package com.firefly.compiler.codegen;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.ast.pattern.*;
import com.firefly.compiler.ast.ImportDeclaration;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Generates JVM bytecode from Firefly AST.
 * Currently supports basic Hello World functionality.
 */
public class BytecodeGenerator implements AstVisitor<Void> {
    private final ClassWriter classWriter;
    private MethodVisitor methodVisitor;
    private String className;
    private final Map<String, Integer> localVariables = new HashMap<>();
    private final Map<String, VarType> localVariableTypes = new HashMap<>();
    private final Map<String, String> functionSignatures = new HashMap<>(); // name -> descriptor
    private final Stack<Map<String, Integer>> scopeStack = new Stack<>();
    private int localVarIndex = 0;
    private int labelCounter = 0;
    private boolean lastCallWasVoid = false;
    private VarType lastExpressionType = VarType.INT;
    private String currentFunctionName = null;
    private Label breakLabel = null;
    private Label continueLabel = null;
    private boolean inStatementContext = false;
    private boolean codeIsReachable = true;  // Track if current code path is reachable
    
    // Store generated class files: className -> bytecode
    private final Map<String, byte[]> generatedClasses = new HashMap<>();
    
    // Professional type resolution system
    private final TypeResolver typeResolver;
    private final MethodResolver methodResolver;
    
    private enum VarType {
        INT, FLOAT, BOOLEAN, STRING, OBJECT, STRING_ARRAY
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
        unit.accept(this);
        
        // Add the main class if it was generated
        classWriter.visitEnd();
        generatedClasses.put(className, classWriter.toByteArray());
        
        return generatedClasses;
    }
    
    @Override 
    public Void visitCompilationUnit(CompilationUnit unit) {
        // Determine class name from package or use default
        String packageName = unit.getPackageName().orElse("");
        if (!packageName.isEmpty()) {
            this.className = packageName.replace(".", "/") + "/Main";
        } else {
            this.className = "Main";
        }
        
        // Ensure TypeResolver has imports (may already be initialized by compiler)
        // This is idempotent - adding same import twice is safe
        for (ImportDeclaration importDecl : unit.getImports()) {
            if (importDecl.isWildcard()) {
                typeResolver.addWildcardImport(importDecl.getModulePath());
            } else {
                for (String item : importDecl.getItems()) {
                    typeResolver.addImport(importDecl.getModulePath(), item);
                }
            }
        }
        
        // Start class - targeting Java 1.8 for maximum compatibility
        // Java 1.8 bytecode runs on all modern JVMs including Java 21
        classWriter.visit(
            V1_8, // Java 8 bytecode (runs on Java 8-21+)
            ACC_PUBLIC | ACC_SUPER,
            className,
            null,
            "java/lang/Object",
            null
        );
        
        // Add default constructor
        MethodVisitor constructor = classWriter.visitMethod(
            ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null
        );
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        
        // Visit declarations
        for (Declaration decl : unit.getDeclarations()) {
            decl.accept(this);
        }
        
        return null;
    }
    @Override 
    public Void visitImportDeclaration(ImportDeclaration decl) {
        // Imports are processed in visitCompilationUnit
        return null;
    }
    
    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        // Generate interface bytecode
        String currentClassName = className;
        String packageName = currentClassName.contains("/") ? 
            currentClassName.substring(0, currentClassName.lastIndexOf("/")) : "";
        
        String interfaceName = packageName.isEmpty() ? 
            decl.getName() : packageName + "/" + decl.getName();
        
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
        String currentClassName = className;
        String packageName = currentClassName.contains("/") ? 
            currentClassName.substring(0, currentClassName.lastIndexOf("/")) : "";
        
        String classFileName = packageName.isEmpty() ? 
            decl.getName() : packageName + "/" + decl.getName();
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
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
        
        // Create class
        cw.visit(
            V1_8,
            ACC_PUBLIC | ACC_SUPER,
            classFileName,
            null,
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
        
        // Add methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            generateMethod(cw, method, classFileName);
        }
        
        cw.visitEnd();
        
        // Store the generated class bytecode
        generatedClasses.put(classFileName, cw.toByteArray());
        
        return null;
    }
    
    private void generateField(ClassWriter cw, ClassDecl.FieldDecl field, String className) {
        int access = ACC_PRIVATE;
        if (!field.isMutable()) {
            access |= ACC_FINAL;
        }
        
        String descriptor = getTypeDescriptor(field.getType());
        FieldVisitor fv = cw.visitField(access, field.getName(), descriptor, null, null);
        
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
        
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", descriptor.toString(), null, null);
        
        // Add constructor annotations
        for (Annotation ann : constructor.getAnnotations()) {
            emitMethodAnnotation(mv, ann);
        }
        
        mv.visitCode();
        
        // Call super constructor
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V", false);
        
        // Constructor body would be generated here
        // For now, simplified
        
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
    
    private void generateMethod(ClassWriter cw, ClassDecl.MethodDecl method, String className) {
        // Build descriptor
        StringBuilder descriptor = new StringBuilder("(");
        
        // Check if this is a main method
        boolean isMainMethod = "main".equals(method.getName()) && 
                              method.getParameters().size() == 1;
        
        if (isMainMethod) {
            // main method always takes String[]
            descriptor.append("[Ljava/lang/String;");
        } else {
            for (FunctionDecl.Parameter param : method.getParameters()) {
                descriptor.append(getTypeDescriptor(param.getType()));
            }
        }
        descriptor.append(")");
        
        if (method.getReturnType().isPresent()) {
            descriptor.append(getTypeDescriptor(method.getReturnType().get()));
        } else {
            descriptor.append("V");
        }
        
        // Determine method access flags
        int accessFlags = ACC_PUBLIC;
        if (isMainMethod) {
            accessFlags |= ACC_STATIC;  // main must be static
        }
        
        // Save current method visitor and set up for method
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
        
        if (isMainMethod) {
            // Static method - no 'this', args start at index 0
            localVarIndex = 1;  // args at 0
        } else {
            // Instance method - index 0 is 'this' (self)
            localVariables.put("self", 0);
            localVariableTypes.put("self", VarType.OBJECT);
            localVarIndex = 1;
        }
        
        // Add parameters to local variables and emit parameter annotations
        int paramIdx = 0;
        for (FunctionDecl.Parameter param : method.getParameters()) {
            int paramIndex = isMainMethod ? 0 : localVarIndex++;
            localVariables.put(param.getName(), paramIndex);
            if (isMainMethod) {
                // main method args is String[]
                localVariableTypes.put(param.getName(), VarType.STRING_ARRAY);
            } else {
                localVariableTypes.put(param.getName(), getVarTypeFromType(param.getType()));
            }
            
            // Emit parameter annotations
            for (Annotation ann : param.getAnnotations()) {
                emitParameterAnnotation(methodVisitor, paramIdx, ann);
            }
            paramIdx++;
        }
        
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
                switch (returnType) {
                    case INT:
                    case BOOLEAN:
                        methodVisitor.visitInsn(IRETURN);
                        break;
                    case FLOAT:
                        methodVisitor.visitInsn(FRETURN);
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
        
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        
        // Restore previous state
        methodVisitor = savedMethodVisitor;
        localVariables.clear();
        localVariables.putAll(savedLocalVars);
        localVariableTypes.clear();
        localVariableTypes.putAll(savedLocalVarTypes);
        localVarIndex = savedLocalVarIndex;
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
            
            // Check if this is a "value" attribute that should be a String array
            // Common Spring annotations use String[] for their value attribute
            if ("value".equals(name) && value instanceof String) {
                // Emit as a single-element String array
                AnnotationVisitor arrayVisitor = av.visitArray(name);
                arrayVisitor.visit(null, value);
                arrayVisitor.visitEnd();
            } else {
                // Emit as-is
                av.visit(name, value);
            }
        }
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
        
        // Handle main method specially
        if (decl.getName().equals("main")) {
            descriptor.append("[Ljava/lang/String;");
        } else {
            // Add parameter types to descriptor
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                descriptor.append(getTypeDescriptor(param.getType()));
            }
        }
        
        descriptor.append(")");
        
        // Add return type
        if (decl.getReturnType().isPresent()) {
            descriptor.append(getTypeDescriptor(decl.getReturnType().get()));
        } else {
            descriptor.append("V"); // void
        }
        
        String descriptorStr = descriptor.toString();
        functionSignatures.put(decl.getName(), descriptorStr);
        
        // Create method
        methodVisitor = classWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            decl.getName(),
            descriptorStr,
            null,
            null
        );
        methodVisitor.visitCode();
        
        // Initialize local variables
        localVariables.clear();
        localVariableTypes.clear();
        scopeStack.clear();
        
        if (decl.getName().equals("main")) {
            localVarIndex = 1; // args array at index 0
            // Register args parameter for main method
            if (!decl.getParameters().isEmpty()) {
                FunctionDecl.Parameter argsParam = decl.getParameters().get(0);
                localVariables.put(argsParam.getName(), 0);
                localVariableTypes.put(argsParam.getName(), VarType.STRING_ARRAY);
            }
        } else {
            localVarIndex = 0;
            // Register parameters as local variables
            for (FunctionDecl.Parameter param : decl.getParameters()) {
                int paramIndex = localVarIndex++;
                localVariables.put(param.getName(), paramIndex);
                localVariableTypes.put(param.getName(), getVarTypeFromType(param.getType()));
            }
        }
        
        // Visit function body
        decl.getBody().accept(this);
        
        // Add return if needed
        if (decl.getReturnType().isPresent()) {
            // For non-void functions, the body expression result is already on stack
            VarType returnType = getVarTypeFromType(decl.getReturnType().get());
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
        } else {
            methodVisitor.visitInsn(RETURN);
        }
        
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        methodVisitor = null;
        currentFunctionName = null;
        
        return null;
    }
    @Override public Void visitStructDecl(StructDecl decl) { return null; }
    @Override public Void visitDataDecl(DataDecl decl) { return null; }
    @Override public Void visitTraitDecl(TraitDecl decl) { return null; }
    @Override public Void visitImplDecl(ImplDecl decl) { return null; }
    @Override 
    public Void visitLetStatement(LetStatement stmt) {
        if (methodVisitor == null) return null;
        
        // For now, only handle simple variable patterns
        if (stmt.getPattern() instanceof VariablePattern) {
            VariablePattern varPattern = (VariablePattern) stmt.getPattern();
            String varName = varPattern.getName();
            
            // Evaluate initializer if present
            if (stmt.getInitializer().isPresent()) {
                stmt.getInitializer().get().accept(this);
                
                // Assign to local variable
                int varIndex = localVarIndex++;
                localVariables.put(varName, varIndex);
                localVariableTypes.put(varName, lastExpressionType);
                
                // Store based on type
                switch (lastExpressionType) {
                    case INT:
                    case BOOLEAN:
                        methodVisitor.visitVarInsn(ISTORE, varIndex);
                        break;
                    case FLOAT:
                        methodVisitor.visitVarInsn(FSTORE, varIndex);
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
            // Pop unused result only if the expression produced a value
            if (!lastCallWasVoid) {
                // For now, only pop if it's not a void call
                // TODO: track return types properly
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
                methodVisitor.visitInsn(IADD);
                lastExpressionType = VarType.INT;
                break;
            case SUBTRACT:
                methodVisitor.visitInsn(ISUB);
                lastExpressionType = VarType.INT;
                break;
            case MULTIPLY:
                methodVisitor.visitInsn(IMUL);
                lastExpressionType = VarType.INT;
                break;
            case DIVIDE:
                methodVisitor.visitInsn(IDIV);
                lastExpressionType = VarType.INT;
                break;
            case MODULO:
                methodVisitor.visitInsn(IREM);
                lastExpressionType = VarType.INT;
                break;
            
            // Comparison operations
            case EQUAL:
                generateComparison(IF_ICMPEQ);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case NOT_EQUAL:
                generateComparison(IF_ICMPNE);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case LESS_THAN:
                generateComparison(IF_ICMPLT);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case LESS_EQUAL:
                generateComparison(IF_ICMPLE);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case GREATER_THAN:
                generateComparison(IF_ICMPGT);
                lastExpressionType = VarType.BOOLEAN;
                break;
            case GREATER_EQUAL:
                generateComparison(IF_ICMPGE);
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
            
            default:
                throw new UnsupportedOperationException(
                    "Binary operator not yet implemented: " + expr.getOperator()
                );
        }
        
        return null;
    }
    
    /**
     * Generate comparison bytecode that produces a boolean result (0 or 1)
     */
    private void generateComparison(int comparisonOpcode) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        
        // Compare and jump to true label if condition holds
        methodVisitor.visitJumpInsn(comparisonOpcode, trueLabel);
        
        // False case: push 0
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitJumpInsn(GOTO, endLabel);
        
        // True case: push 1
        methodVisitor.visitLabel(trueLabel);
        methodVisitor.visitInsn(ICONST_1);
        
        methodVisitor.visitLabel(endLabel);
    }
    @Override public Void visitUnaryExpr(UnaryExpr expr) { return null; }
    @Override 
    public Void visitCallExpr(CallExpr expr) {
        if (methodVisitor == null) return null;
        
        // Handle method calls: ClassName.method(args) or object.method(args)
        if (expr.getFunction() instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expr.getFunction();
            String methodName = fieldAccess.getFieldName();
            
            // Check if the object is an identifier (could be a class name for static calls)
            if (fieldAccess.getObject() instanceof IdentifierExpr) {
                String objectName = ((IdentifierExpr) fieldAccess.getObject()).getName();
                
                // Resolve argument types first
                java.util.List<Class<?>> argTypes = new java.util.ArrayList<>();
                for (Expression arg : expr.getArguments()) {
                    Class<?> argType = inferExpressionType(arg);
                    argTypes.add(argType);
                }
                
                // Try to resolve as a static method call using professional resolver
                java.util.Optional<MethodResolver.MethodCandidate> staticMethod = 
                    methodResolver.resolveStaticMethod(objectName, methodName, argTypes);
                
                if (staticMethod.isPresent()) {
                    // Static method call: ClassName.method()
                    MethodResolver.MethodCandidate candidate = staticMethod.get();
                    
                    // Visit arguments and potentially apply conversions
                    for (Expression arg : expr.getArguments()) {
                        arg.accept(this);
                        // TODO: Apply type conversions if needed based on candidate.applicability
                    }
                    
                    // Call static method
                    methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        candidate.getInternalClassName(),
                        candidate.method.getName(),
                        candidate.getDescriptor(),
                        false
                    );
                    
                    // Determine return type from method
                    Class<?> returnType = candidate.method.getReturnType();
                    lastCallWasVoid = returnType.equals(void.class);
                    lastExpressionType = getVarTypeFromClass(returnType);
                    
                    return null;
                }
            }
            
            // Instance method call: object.method()
            // Visit the object (receiver)
            fieldAccess.getObject().accept(this);
            
            // Visit arguments and build descriptor
            StringBuilder argDescriptor = new StringBuilder("(");
            for (Expression arg : expr.getArguments()) {
                arg.accept(this);
                switch (lastExpressionType) {
                    case INT:
                        argDescriptor.append("I");
                        break;
                    case FLOAT:
                        argDescriptor.append("F");
                        break;
                    case BOOLEAN:
                        argDescriptor.append("Z");
                        break;
                    default:
                        argDescriptor.append("Ljava/lang/String;");
                        break;
                }
            }
            argDescriptor.append(")");
            
            // Default to Object return type (will be refined with type inference)
            String returnDesc = "Ljava/lang/String;";
            String fullDescriptor = argDescriptor.toString() + returnDesc;
            
            // Generate INVOKEVIRTUAL for instance method
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Object",
                methodName,
                fullDescriptor,
                false
            );
            
            lastExpressionType = VarType.STRING;
            lastCallWasVoid = returnDesc.equals("V");
            
        } else if (expr.getFunction() instanceof IdentifierExpr) {
            String funcName = ((IdentifierExpr) expr.getFunction()).getName();
            
            // Handle built-in functions
            if (funcName.equals("println")) {
                lastCallWasVoid = true;
                
                methodVisitor.visitFieldInsn(
                    GETSTATIC,
                    "java/lang/System",
                    "out",
                    "Ljava/io/PrintStream;"
                );
                
                String descriptor = "(Ljava/lang/String;)V";
                if (!expr.getArguments().isEmpty()) {
                    expr.getArguments().get(0).accept(this);
                    switch (lastExpressionType) {
                        case INT:
                            descriptor = "(I)V";
                            break;
                        case BOOLEAN:
                            descriptor = "(Z)V";
                            break;
                        case FLOAT:
                            descriptor = "(F)V";
                            break;
                        case OBJECT:
                            descriptor = "(Ljava/lang/Object;)V";
                            break;
                        default:
                            descriptor = "(Ljava/lang/String;)V";
                            break;
                    }
                } else {
                    methodVisitor.visitLdcInsn("");
                }
                
                methodVisitor.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/io/PrintStream",
                    "println",
                    descriptor,
                    false
                );
            } else if (functionSignatures.containsKey(funcName)) {
                // User-defined function call
                String descriptor = functionSignatures.get(funcName);
                lastCallWasVoid = descriptor.endsWith("V");
                
                for (Expression arg : expr.getArguments()) {
                    arg.accept(this);
                }
                
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    className,
                    funcName,
                    descriptor,
                    false
                );
                
                if (!lastCallWasVoid) {
                    String returnTypeDesc = descriptor.substring(descriptor.indexOf(')') + 1);
                    lastExpressionType = getVarTypeFromDescriptor(returnTypeDesc);
                }
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
        
        // Regular field access - visit the object
        expr.getObject().accept(this);
        
        // For now, we can't generate actual field access without knowing the type
        // This will be improved with full type inference
        
        return null;
    }
    @Override public Void visitIndexAccessExpr(IndexAccessExpr expr) { return null; }
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
                    methodVisitor.visitLdcInsn(((Double) expr.getValue()).floatValue());
                    lastExpressionType = VarType.FLOAT;
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
        
        // Look up variable in local variables
        Integer varIndex = localVariables.get(expr.getName());
        if (varIndex != null) {
            VarType varType = localVariableTypes.getOrDefault(expr.getName(), VarType.INT);
            lastExpressionType = varType;
            
            // Load local variable based on type
            switch (varType) {
                case INT:
                case BOOLEAN:
                    methodVisitor.visitVarInsn(ILOAD, varIndex);
                    break;
                case FLOAT:
                    methodVisitor.visitVarInsn(FLOAD, varIndex);
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
            
            throw new RuntimeException("Undefined variable: " + expr.getName() + 
                " (not found in local variables and not resolvable as a class)");
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
        codeIsReachable = true;
        expr.getThenBranch().accept(this);
        boolean thenReachable = codeIsReachable;
        
        // Only add GOTO if then branch doesn't end with control flow (break/continue/return)
        if (thenReachable) {
            methodVisitor.visitJumpInsn(GOTO, endLabel);
        }
        
        // Else branch
        methodVisitor.visitLabel(elseLabel);
        codeIsReachable = true;
        if (expr.getElseBranch().isPresent()) {
            expr.getElseBranch().get().accept(this);
        } else if (!inStatementContext) {
            // No else branch in expression context - push unit/null
            methodVisitor.visitInsn(ACONST_NULL);
        }
        boolean elseReachable = codeIsReachable;
        
        // End label
        methodVisitor.visitLabel(endLabel);
        
        // Ensure consistent stack state at merge point
        // If only one branch is reachable, we still need valid bytecode at the label
        if (!thenReachable && elseReachable) {
            // Only else branch reaches here - this is fine, code continues
        } else if (thenReachable && !elseReachable) {
            // Only then branch reaches here (else has break/continue) - this is fine
        } else if (!thenReachable && !elseReachable) {
            // Neither branch reaches here - add NOP for verifier
            methodVisitor.visitInsn(NOP);
        }
        
        // Code after if is reachable if either branch is reachable
        codeIsReachable = thenReachable || elseReachable;
        
        return null;
    }
    @Override public Void visitMatchExpr(MatchExpr expr) { return null; }
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
    @Override public Void visitLambdaExpr(LambdaExpr expr) { return null; }
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
        
        // Bind pattern variable (for now, assume simple variable pattern)
        if (expr.getPattern() instanceof VariablePattern) {
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
                    methodVisitor.visitInsn(FRETURN);
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
    
    @Override public Void visitConcurrentExpr(ConcurrentExpr expr) { return null; }
    @Override public Void visitRaceExpr(RaceExpr expr) { return null; }
    @Override public Void visitTimeoutExpr(TimeoutExpr expr) { return null; }
    @Override public Void visitCoalesceExpr(CoalesceExpr expr) { return null; }
    
    @Override 
    public Void visitAssignmentExpr(AssignmentExpr expr) {
        if (methodVisitor == null) return null;
        
        // For now, only handle simple identifier assignment
        if (expr.getTarget() instanceof IdentifierExpr) {
            IdentifierExpr target = (IdentifierExpr) expr.getTarget();
            String varName = target.getName();
            
            // Evaluate the value
            expr.getValue().accept(this);
            
            // Get variable slot
            Integer varIndex = localVariables.get(varName);
            if (varIndex == null) {
                throw new RuntimeException("Variable not defined: " + varName);
            }
            
            VarType varType = localVariableTypes.getOrDefault(varName, lastExpressionType);
            
            // Store to variable (assignment doesn't produce a value in statements)
            switch (varType) {
                case INT:
                case BOOLEAN:
                    methodVisitor.visitVarInsn(ISTORE, varIndex);
                    break;
                case FLOAT:
                    methodVisitor.visitVarInsn(FSTORE, varIndex);
                    break;
                case STRING:
                case OBJECT:
                    methodVisitor.visitVarInsn(ASTORE, varIndex);
                    break;
            }
            
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
                    constructorDescriptor.append("F");
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
        
        // Create new ArrayList
        methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        
        // Add each element
        for (com.firefly.compiler.ast.expr.Expression element : expr.getElements()) {
            // Duplicate list reference for next add call
            methodVisitor.visitInsn(DUP);
            
            // Evaluate element
            element.accept(this);
            
            // Box primitive types
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
                // STRING and OBJECT don't need boxing
            }
            
            // Call add method
            methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/util/ArrayList",
                "add",
                "(Ljava/lang/Object;)Z",
                false
            );
            
            // Pop the boolean result from add
            methodVisitor.visitInsn(POP);
        }
        
        // List reference is now on stack
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
        if (type instanceof com.firefly.compiler.ast.type.NamedType) {
            com.firefly.compiler.ast.type.NamedType namedType = 
                (com.firefly.compiler.ast.type.NamedType) type;
            String name = namedType.getName();
            
            // Check common Java classes
            switch (name) {
                case "ArrayList": return "java.util.ArrayList";
                case "HashMap": return "java.util.HashMap";
                case "HashSet": return "java.util.HashSet";
                case "StringBuilder": return "java.lang.StringBuilder";
                case "String": return "java.lang.String";
                case "Integer": return "java.lang.Integer";
                case "Long": return "java.lang.Long";
                case "Double": return "java.lang.Double";
                case "Boolean": return "java.lang.Boolean";
                default: return name; // Use as-is (might be fully qualified)
            }
        }
        return "java.lang.Object";
    }
    
    /**
     * Convert a Firefly Type to JVM type descriptor
     */
    private String getTypeDescriptor(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof com.firefly.compiler.ast.type.PrimitiveType) {
            com.firefly.compiler.ast.type.PrimitiveType primType = 
                (com.firefly.compiler.ast.type.PrimitiveType) type;
            String name = primType.getName();
            switch (name) {
                case "Int": return "I";
                case "Float": return "F";
                case "Boolean": return "Z";
                case "String": return "Ljava/lang/String;";
                case "Unit": return "V";  // void
                default: return "Ljava/lang/Object;";
            }
        } else if (type instanceof com.firefly.compiler.ast.type.NamedType) {
            com.firefly.compiler.ast.type.NamedType namedType = 
                (com.firefly.compiler.ast.type.NamedType) type;
            String name = namedType.getName();
            switch (name) {
                case "Int": return "I";
                case "Float": return "F";
                case "Boolean": return "Z";
                case "String": return "Ljava/lang/String;";
                case "Unit": return "V";  // void
                default: return "Ljava/lang/Object;";
            }
        }
        return "Ljava/lang/Object;";
    }
    
    /**
     * Convert a Firefly Type to VarType
     */
    private VarType getVarTypeFromType(com.firefly.compiler.ast.type.Type type) {
        if (type instanceof com.firefly.compiler.ast.type.PrimitiveType) {
            com.firefly.compiler.ast.type.PrimitiveType primType = 
                (com.firefly.compiler.ast.type.PrimitiveType) type;
            String name = primType.getName();
            switch (name) {
                case "Int": return VarType.INT;
                case "Float": return VarType.FLOAT;
                case "Boolean": return VarType.BOOLEAN;
                case "String": return VarType.STRING;
                case "Unit": return VarType.OBJECT;  // Unit maps to void, handle specially
                default: return VarType.OBJECT;
            }
        } else if (type instanceof com.firefly.compiler.ast.type.NamedType) {
            com.firefly.compiler.ast.type.NamedType namedType = 
                (com.firefly.compiler.ast.type.NamedType) type;
            String name = namedType.getName();
            switch (name) {
                case "Int": return VarType.INT;
                case "Float": return VarType.FLOAT;
                case "Boolean": return VarType.BOOLEAN;
                case "String": return VarType.STRING;
                case "Unit": return VarType.OBJECT;  // Unit maps to void, handle specially
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
            case "F": return VarType.FLOAT;
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
        if (clazz == float.class || clazz == Float.class) return VarType.FLOAT;
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
            case FLOAT: return float.class;
            case BOOLEAN: return boolean.class;
            case STRING: return String.class;
            case STRING_ARRAY: return String[].class;
            case OBJECT: return Object.class;
            default: return Object.class;
        }
    }
}
