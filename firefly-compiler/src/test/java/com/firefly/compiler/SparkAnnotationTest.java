package com.firefly.compiler;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.codegen.BytecodeGenerator;
import com.firefly.compiler.semantics.SemanticAnalyzer;
import com.firefly.compiler.diagnostics.CompilerDiagnostic;
import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test spark declarations with annotations like @travelable, @derive, etc.
 */
public class SparkAnnotationTest {
    
    @Test
    public void testSparkWithTravelableAnnotation() throws IOException {
        String source = """
module test::spark

@travelable
spark Account {
    id: String,
    balance: Int,
    owner: String
}
""";
        
        CompilationUnit cu = parse(source);
        assertNotNull(cu);
        assertEquals(1, cu.getDeclarations().size());
        
        Declaration decl = cu.getDeclarations().get(0);
        assertTrue(decl instanceof SparkDecl);
        
        SparkDecl spark = (SparkDecl) decl;
        assertEquals("Account", spark.getName());
        assertEquals(3, spark.getFields().size());
        
        // Check that @travelable annotation is present
        assertTrue(spark.hasAnnotation("travelable"), 
            "Spark should have @travelable annotation");
    }
    
    @Test
    public void testSparkWithMultipleAnnotations() throws IOException {
        String source = """
module test::spark

@travelable
@derive(Show)
spark Transaction {
    id: String,
    amount: Int
}
""";
        
        CompilationUnit cu = parse(source);
        assertNotNull(cu);
        
        SparkDecl spark = (SparkDecl) cu.getDeclarations().get(0);
        assertEquals("Transaction", spark.getName());
        
        // Check both annotations
        assertTrue(spark.hasAnnotation("travelable"), 
            "Spark should have @travelable annotation");
        assertTrue(spark.hasAnnotation("derive"), 
            "Spark should have @derive annotation");
        
        // Check derive annotation value
        var deriveAnnotation = spark.getAnnotation("derive");
        assertTrue(deriveAnnotation.isPresent());
        assertNotNull(deriveAnnotation.get().getValue());
    }
    
    @Test
    public void testSparkWithValidationBlock() throws IOException {
        String source = """
module test::spark

@travelable
spark Account {
    balance: Int,
    
    validate {
        println("validating");
    }
}
""";
        
        CompilationUnit cu = parse(source);
        SparkDecl spark = (SparkDecl) cu.getDeclarations().get(0);
        
        assertTrue(spark.getValidateBlock().isPresent(),
            "Spark should have validation block");
        assertTrue(spark.hasAnnotation("travelable"));
    }
    
    @Test
    public void testSparkWithComputedProperty() throws IOException {
        String source = """
module test::spark

@travelable
spark Account {
    balance: Int,
    
    computed isOverdrawn: Bool {
        false
    }
}
""";
        
        CompilationUnit cu = parse(source);
        SparkDecl spark = (SparkDecl) cu.getDeclarations().get(0);
        
        assertEquals(1, spark.getComputedProperties().size(),
            "Spark should have one computed property");
        assertEquals("isOverdrawn", spark.getComputedProperties().get(0).getName());
        assertTrue(spark.hasAnnotation("travelable"));
    }
    
    @Test
    public void testSparkWithCustomMethods() throws IOException {
        String source = """
module test::spark

@travelable
spark Account {
    balance: Int,
    
    fn getBalance() -> Int {
        42
    }
}
""";
        
        CompilationUnit cu = parse(source);
        SparkDecl spark = (SparkDecl) cu.getDeclarations().get(0);
        
        assertEquals(1, spark.getMethods().size(),
            "Spark should have one custom method");
        assertEquals("getBalance", spark.getMethods().get(0).getName());
        assertTrue(spark.hasAnnotation("travelable"));
    }
    
    @Test
    public void testSparkCodegenWithTravelable() throws IOException {
        String source = """
module test::spark

@travelable
spark Account {
    id: String,
    balance: Int
}
""";
        
        CompilationUnit cu = parse(source);
        
        // Run semantic analysis
        com.firefly.compiler.codegen.TypeResolver typeResolver = new com.firefly.compiler.codegen.TypeResolver();
        SemanticAnalyzer analyzer = new SemanticAnalyzer(typeResolver);
        List<CompilerDiagnostic> diagnostics = analyzer.analyze(cu);
        
        // Check for errors
        boolean hasErrors = diagnostics.stream().anyMatch(CompilerDiagnostic::isError);
        assertFalse(hasErrors, "Semantic analysis should not produce errors");
        
        // Generate bytecode
        BytecodeGenerator generator = new BytecodeGenerator(typeResolver);
        Map<String, byte[]> classes = generator.generate(cu);
        
        assertNotNull(classes);
        
        // The class should be generated with full module path: test/spark/Account
        boolean hasAccountClass = classes.keySet().stream()
            .anyMatch(key -> key.contains("Account"));
        assertTrue(hasAccountClass, 
            "Generated classes should contain Account class");
        
        // Verify the class has history tracking methods
        // (This would require more sophisticated bytecode analysis)
    }
    
    @Test
    public void testSparkWithHooks() throws IOException {
        String source = """
module test::spark

@travelable
spark Account {
    balance: Int,
    
    before update {
        println("Before update");
    }
    
    after update (old, newVal) {
        println("After update");
    }
}
""";
        
        CompilationUnit cu = parse(source);
        SparkDecl spark = (SparkDecl) cu.getDeclarations().get(0);
        
        assertTrue(spark.getBeforeHook().isPresent(),
            "Spark should have before hook");
        assertTrue(spark.getAfterHook().isPresent(),
            "Spark should have after hook");
        assertTrue(spark.hasAnnotation("travelable"));
    }
    
    // Helper method to parse source code
    private CompilationUnit parse(String source) throws IOException {
        CharStream input = CharStreams.fromString(source);
        FireflyLexer lexer = new FireflyLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        
        FireflyParser.CompilationUnitContext tree = parser.compilationUnit();
        
        com.firefly.compiler.ast.AstBuilder builder = 
            new com.firefly.compiler.ast.AstBuilder("test.ff");
        return builder.visitCompilationUnit(tree);
    }
}
