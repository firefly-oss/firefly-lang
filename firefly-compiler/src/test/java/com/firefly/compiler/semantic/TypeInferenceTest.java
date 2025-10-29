package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.ast.expr.*;
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
 * Tests for type inference.
 */
public class TypeInferenceTest {
    
private CompilationUnit parse(String source) {
        FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        AstBuilder builder = new AstBuilder("test.fly");
        return (CompilationUnit) builder.visit(parser.compilationUnit());
    }

private String header() { return "module tests::type_inference\n\n"; }
    private String wrap(String body) { return "class Test {\n" + body + "\n}\n"; }
    
    @Test
    public void testLiteralInference() {
String source = header() + wrap("  pub fn test() -> Void {\n    let x = 42;\n    let y = 3.14;\n    let z = \"hello\";\n    let b = true;\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type unitType = unit.accept(inference);
        assertNotNull(unitType);
        assertEquals("Void", unitType.getName());
    }
    
    @Test
    public void testBinaryArithmeticInference() {
String source = header() + wrap("  pub fn calculate() -> Void {\n    let result = 10 + 20 * 2;\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        // Infer type of compilation unit
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testComparisonInference() {
String source = header() + wrap("  pub fn compare() -> Void {\n    let result = 10 > 5;\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testFunctionReturnTypeInference() {
String source = header() + wrap("  pub fn add() -> Int { 10 + 20 }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testIfExpressionInference() {
String source = header() + wrap("  pub fn check() -> Void {\n    let result = if true {\n      \"positive\"\n    } else {\n      \"negative\"\n    };\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testBlockExpressionInference() {
String source = header() + wrap("  pub fn test() -> Void {\n    let x = {\n      let y = 10;\n      20\n    };\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
        assertEquals("Void", type.getName());
    }
    
    @Test
    public void testNumericTypePromotion() {
        // Float should take precedence over Int
String source = header() + wrap("  pub fn mix() -> Void {\n    let result = 10 + 3.14;\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testUnaryExpressionInference() {
String source = header() + wrap("  pub fn negate() -> Void {\n    let neg = -42;\n    let notTrue = !true;\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testOptionalTypeInference() {
String source = header() + wrap("  pub fn option() -> Void {\n    let x = none;\n  }\n");
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
}
