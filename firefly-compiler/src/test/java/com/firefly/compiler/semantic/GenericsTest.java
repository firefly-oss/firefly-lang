package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.ast.SourceLocation;
import com.firefly.compiler.ast.decl.FunctionDecl;
import com.firefly.compiler.ast.decl.StructDecl;
import com.firefly.compiler.ast.type.*;
import com.firefly.compiler.diagnostic.DiagnosticReporter;
import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for generics support in Firefly.
 */
public class GenericsTest {
    
private CompilationUnit parse(String source) {
        FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        AstBuilder builder = new AstBuilder("test.fly");
        return (CompilationUnit) builder.visit(parser.compilationUnit());
    }

private String header() { return "module tests::generics\n\n"; }
    private String wrap(String body) { return "class Test {\n" + body + "\n}\n"; }
    
    @Test
    public void testGenericFunctionDeclaration() {
String source = header() + wrap("  pub fn identity<T>(value: T) -> T { value }\n");
        
CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(1, unit.getDeclarations().size());
        
        com.firefly.compiler.ast.decl.ClassDecl cls = (com.firefly.compiler.ast.decl.ClassDecl) unit.getDeclarations().get(0);
        com.firefly.compiler.ast.decl.ClassDecl.MethodDecl func = cls.getMethods().get(0);
        assertEquals("identity", func.getName());
assertEquals(1, func.getTypeParameters().size());
        assertEquals("T", func.getTypeParameters().get(0));
    }
    
    @Test
    public void testGenericStructDeclaration() {
        String source = header() + """
            struct Pair<A, B> {
                first: A,
                second: B
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(1, unit.getDeclarations().size());
        
        StructDecl struct = (StructDecl) unit.getDeclarations().get(0);
        assertEquals("Pair", struct.getName());
        assertEquals(2, struct.getTypeParameters().size());
        assertEquals("A", struct.getTypeParameters().get(0).getName());
        assertEquals("B", struct.getTypeParameters().get(1).getName());
    }
    
    @Test
    public void testGenericTypeArguments() {
String source = header() + wrap("  pub fn test() -> Void {\n    let list: List<Int> = createList();\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        
        // The type should be parsed as GenericType
com.firefly.compiler.ast.decl.ClassDecl.MethodDecl func = ((com.firefly.compiler.ast.decl.ClassDecl) unit.getDeclarations().get(0)).getMethods().get(0);
        assertNotNull(func);
    }
    
    @Test
    public void testMultipleTypeParameters() {
String source = header() + wrap("  pub fn swap<A, B>(a: A, b: B) -> B { b }\n");

        CompilationUnit unit = parse(source);
        assertNotNull(unit);

        com.firefly.compiler.ast.decl.ClassDecl cls = (com.firefly.compiler.ast.decl.ClassDecl) unit.getDeclarations().get(0);
        com.firefly.compiler.ast.decl.ClassDecl.MethodDecl func = cls.getMethods().get(0);
        assertEquals("swap", func.getName());
        assertEquals(2, func.getTypeParameters().size());
        assertEquals("A", func.getTypeParameters().get(0));
        assertEquals("B", func.getTypeParameters().get(1));
    }
    
    @Test
    public void testNestedGenericTypes() {
String source = header() + wrap("  pub fn test() -> Void {\n    let nested: List<List<Int>> = createNestedList();\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testGenericTypeInference() {
String source = header() + wrap("  pub fn identity<T>(value: T) -> T { value }\n  pub fn main() -> Void {\n    let x = identity(42);\n    let s = identity(\"hello\");\n  }\n");
        
CompilationUnit unit = parse(source);
        DiagnosticReporter dr = new DiagnosticReporter();
        SymbolTableBuilder builder = new SymbolTableBuilder(dr);
        SymbolTable symTable = builder.build(unit);
        
        assertNotNull(symTable);
        assertFalse(dr.hasErrors());
    }
    
    @Test
    public void testTypeSubstitution() {
        // Test TypeSubstitution utility
        TypeSubstitution sub = new TypeSubstitution();
        sub.addSubstitution("T", new PrimitiveType("Int"));
        
        TypeParameter typeParam = new TypeParameter("T", SourceLocation.unknown());
        Type substituted = sub.substitute(typeParam);
        
        assertTrue(substituted instanceof PrimitiveType);
        assertEquals("Int", substituted.getName());
    }
    
    @Test
    public void testGenericTypeSubstitution() {
        // Test substitution in generic types
        TypeSubstitution sub = new TypeSubstitution();
        sub.addSubstitution("T", new PrimitiveType("String"));
        
        GenericType listOfT = new GenericType(
            "List",
            java.util.List.of(new TypeParameter("T", SourceLocation.unknown())),
            SourceLocation.unknown()
        );
        
        Type substituted = sub.substitute(listOfT);
        
        assertTrue(substituted instanceof GenericType);
        GenericType genericResult = (GenericType) substituted;
        assertEquals("List", genericResult.getBaseName());
        assertEquals(1, genericResult.getTypeArguments().size());
        assertEquals("String", genericResult.getTypeArguments().get(0).getName());
    }
    
    @Test
    public void testGenericFunctionWithConstraints() {
String source = header() + wrap("  pub fn printable<T>(value: T) -> Void {\n    println(toString(value));\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        
        com.firefly.compiler.ast.decl.ClassDecl cls2 = (com.firefly.compiler.ast.decl.ClassDecl) unit.getDeclarations().get(0);
        com.firefly.compiler.ast.decl.ClassDecl.MethodDecl func2 = cls2.getMethods().get(0);
        assertEquals("printable", func2.getName());
        assertEquals(1, func2.getTypeParameters().size());
    }
    
    @Test
    public void testGenericDataType() {
        String source = header() + """
            data Option<T> {
                Some(T),
                None
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(1, unit.getDeclarations().size());
    }
    
    @Test
    public void testGenericTrait() {
        String source = header() + """
            trait Container<T> {
                fn get() -> T;
                fn set(value: T) -> Unit;
            }
            """;

        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(1, unit.getDeclarations().size());
    }
}

