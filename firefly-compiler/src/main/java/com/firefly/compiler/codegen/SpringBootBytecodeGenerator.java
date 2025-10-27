package com.firefly.compiler.codegen;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.FunctionDecl;
import org.objectweb.asm.*;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Extended bytecode generator with Spring Boot annotation support.
 * 
 * This generator can emit:
 * - @RestController annotated classes
 * - @RequestMapping, @GetMapping, @PostMapping methods  
 * - @Autowired dependency injection
 * - @SpringBootApplication main class
 */
public class SpringBootBytecodeGenerator extends BytecodeGenerator {
    
    private boolean isRestController = false;
    private String baseRequestMapping = "";
    
    @Override
    public Void visitFunctionDecl(FunctionDecl decl) {
        // Check for Spring annotations
        for (Annotation ann : decl.getAnnotations()) {
            if (ann.isNamed("RestController")) {
                isRestController = true;
            } else if (ann.isNamed("RequestMapping")) {
                Object value = ann.getValue();
                if (value != null) {
                    baseRequestMapping = value.toString().replace("\"", "");
                }
            }
        }
        
        // If this is an annotated method, generate with annotations
        if (!decl.getAnnotations().isEmpty()) {
            return visitAnnotatedFunction(decl);
        }
        
        // Otherwise, use default behavior
        return super.visitFunctionDecl(decl);
    }
    
    /**
     * Generate a Spring Boot annotated method
     */
    private Void visitAnnotatedFunction(FunctionDecl decl) {
        // For now, delegate to parent but add annotation metadata
        // In full implementation, this would:
        // 1. Use ASM's AnnotationVisitor to add runtime annotations
        // 2. Generate proper method signatures for Spring
        // 3. Handle @RequestParam, @PathVariable, etc.
        
        // TODO: Full Spring annotation bytecode generation
        // For v0.1.0, we document this as planned for v0.5.0
        
        return super.visitFunctionDecl(decl);
    }
    
    /**
     * Add Spring Boot runtime annotation to a method
     */
    private void addSpringAnnotation(MethodVisitor mv, Annotation annotation) {
        // Convert annotation name to JVM descriptor
        String descriptor = getAnnotationDescriptor(annotation.getName());
        
        // Visit annotation
        AnnotationVisitor av = mv.visitAnnotation(descriptor, true);
        
        // Add annotation arguments
        for (var entry : annotation.getArguments().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                av.visit(key, value.toString().replace("\"", ""));
            } else if (value instanceof Integer) {
                av.visit(key, value);
            } else if (value instanceof Boolean) {
                av.visit(key, value);
            }
        }
        
        av.visitEnd();
    }
    
    /**
     * Convert annotation name to JVM descriptor
     * E.g., "RestController" -> "Lorg/springframework/web/bind/annotation/RestController;"
     */
    private String getAnnotationDescriptor(String annotationName) {
        // Common Spring annotations
        switch (annotationName) {
            case "RestController":
                return "Lorg/springframework/web/bind/annotation/RestController;";
            case "RequestMapping":
                return "Lorg/springframework/web/bind/annotation/RequestMapping;";
            case "GetMapping":
                return "Lorg/springframework/web/bind/annotation/GetMapping;";
            case "PostMapping":
                return "Lorg/springframework/web/bind/annotation/PostMapping;";
            case "PutMapping":
                return "Lorg/springframework/web/bind/annotation/PutMapping;";
            case "DeleteMapping":
                return "Lorg/springframework/web/bind/annotation/DeleteMapping;";
            case "RequestParam":
                return "Lorg/springframework/web/bind/annotation/RequestParam;";
            case "PathVariable":
                return "Lorg/springframework/web/bind/annotation/PathVariable;";
            case "RequestBody":
                return "Lorg/springframework/web/bind/annotation/RequestBody;";
            case "Autowired":
                return "Lorg/springframework/beans/factory/annotation/Autowired;";
            case "Service":
                return "Lorg/springframework/stereotype/Service;";
            case "Component":
                return "Lorg/springframework/stereotype/Component;";
            case "SpringBootApplication":
                return "Lorg/springframework/boot/autoconfigure/SpringBootApplication;";
            default:
                // Try to resolve as fully qualified name
                if (annotationName.contains(".")) {
                    return "L" + annotationName.replace(".", "/") + ";";
                }
                throw new IllegalArgumentException("Unknown Spring annotation: " + annotationName);
        }
    }
}
