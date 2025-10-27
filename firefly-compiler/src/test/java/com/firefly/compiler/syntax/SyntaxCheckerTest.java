package com.firefly.compiler.syntax;

import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.diagnostic.DiagnosticReporter;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for syntax checking.
 */
public class SyntaxCheckerTest {
    
    private CompilationUnit parse(String source) {
        FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        
        AstBuilder builder = new AstBuilder("test.fly");
        return (CompilationUnit) builder.visit(parser.compilationUnit());
    }
    
    @Test
    public void testValidProgram() {
        String source = """
            fn main() {
                let x = 42;
                println(x);
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertFalse(reporter.hasErrors());
        assertEquals(0, reporter.getErrorCount());
    }
    
    @Test
    public void testDuplicateFunctionDeclaration() {
        String source = """
            fn test() { 42 }
            fn test() { 24 }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
        assertEquals(1, reporter.getErrorCount());
    }
    
    @Test
    public void testDuplicateParameterName() {
        String source = """
            fn test(x: Int, y: Int, x: String) {
                println(x);
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
    }
    
    @Test
    public void testDuplicateStructDeclaration() {
        String source = """
            struct User { name: String }
            struct User { age: Int }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
    }
    
    @Test
    public void testDuplicateStructField() {
        String source = """
            struct User {
                name: String,
                name: String
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
    }
    
    @Test
    public void testAwaitInNonAsyncFunction() {
        String source = """
            fn test() {
                let x = someAsyncOp().await;
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.getDiagnostics().get(0).getMessage().contains("await"));
    }
    
    @Test
    public void testAwaitInAsyncFunction() {
        String source = """
            async fn test() {
                let x = someAsyncOp().await;
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertFalse(reporter.hasErrors());
    }
    
    @Test
    public void testComplexValidProgram() {
        String source = """
            struct Point {
                x: Int,
                y: Int
            }
            
            fn distance(p1: Point, p2: Point) -> Float {
                let dx = p2.x - p1.x;
                let dy = p2.y - p1.y;
                sqrt(dx * dx + dy * dy)
            }
            
            async fn fetchData() -> String {
                let response = api.call().await;
                response.text()
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertFalse(reporter.hasErrors());
        assertEquals(0, reporter.getErrorCount());
    }
}
