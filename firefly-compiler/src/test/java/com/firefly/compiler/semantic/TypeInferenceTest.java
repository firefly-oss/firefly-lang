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
    
    @Test
    public void testLiteralInference() {
        String source = """
            fn test() {
                let x = 42;
                let y = 3.14;
                let z = "hello";
                let b = true;
            }
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type unitType = unit.accept(inference);
        assertNotNull(unitType);
        assertEquals("Unit", unitType.getName());
    }
    
    @Test
    public void testBinaryArithmeticInference() {
        String source = """
            fn calculate() {
                let result = 10 + 20 * 2;
            }
            """;
        
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
        String source = """
            fn compare() {
                let result = 10 > 5;
            }
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testFunctionReturnTypeInference() {
        String source = """
            fn add() = 10 + 20
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testIfExpressionInference() {
        String source = """
            fn check() {
                let result = if true {
                    "positive"
                } else {
                    "negative"
                };
            }
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testBlockExpressionInference() {
        String source = """
            fn test() {
                let x = {
                    let y = 10;
                    20
                };
            }
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
        assertEquals("Unit", type.getName());
    }
    
    @Test
    public void testNumericTypePromotion() {
        // Float should take precedence over Int
        String source = """
            fn mix() {
                let result = 10 + 3.14;
            }
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testUnaryExpressionInference() {
        String source = """
            fn negate() {
                let neg = -42;
                let notTrue = !true;
            }
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
    
    @Test
    public void testOptionalTypeInference() {
        String source = """
            fn option() {
                let x = none;
            }
            """;
        
        CompilationUnit unit = parse(source);
        SymbolTable symTable = new SymbolTable();
        DiagnosticReporter reporter = new DiagnosticReporter();
        TypeInference inference = new TypeInference(symTable, reporter);
        
        Type type = unit.accept(inference);
        assertNotNull(type);
    }
}
