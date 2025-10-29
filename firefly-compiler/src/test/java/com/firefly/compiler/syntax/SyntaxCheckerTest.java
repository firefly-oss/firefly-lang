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

private String header() { return "module tests::syntax\n\n"; }
    private String wrap(String body) { return "class Test {\n" + body + "\n}\n"; }
    
    @Test
    public void testValidProgram() {
String source = header() + wrap("  pub fn main() -> Void {\n    let x = 42;\n    println(x);\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertFalse(reporter.hasErrors());
assertEquals(0, reporter.getErrorCount());
    }
    
    @Test
    public void testDuplicateFunctionDeclaration() {
String source = header() + wrap("  pub fn test() -> Int { 42 }\n  pub fn test() -> Int { 24 }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
        assertEquals(1, reporter.getErrorCount());
    }
    
    @Test
    public void testDuplicateParameterName() {
String source = header() + wrap("  pub fn test(x: Int, y: Int, x: String) -> Void {\n    println(x);\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
    }
    
    @Test
    public void testDuplicateStructDeclaration() {
String source = header() + "struct User { name: String }\nstruct User { age: Int }\n";
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
    }
    
    @Test
    public void testDuplicateStructField() {
String source = header() + "struct User {\n  name: String,\n  name: String\n}\n";
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
    }
    
    @Test
    public void testAwaitInNonAsyncFunction() {
String source = header() + wrap("  pub fn test() -> Void {\n    let x = someAsyncOp().await;\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.getDiagnostics().get(0).getMessage().contains("await"));
    }
    
    @Test
    public void testAwaitInAsyncFunction() {
String source = header() + wrap("  pub async fn test() -> Void {\n    let x = someAsyncOp().await;\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertFalse(reporter.hasErrors());
    }
    
    @Test
    public void testComplexValidProgram() {
String source = header() + "struct Point {\n  x: Int,\n  y: Int\n}\n\n" + wrap(
        "  pub fn distance(p1: Point, p2: Point) -> Float {\n    let dx = p2.x - p1.x;\n    let dy = p2.y - p1.y;\n    sqrt(dx * dx + dy * dy)\n  }\n\n  pub async fn fetchData() -> String {\n    let response = api.call().await;\n    response.text()\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter(false);
        SyntaxChecker checker = new SyntaxChecker(reporter);
        
        checker.check(unit);
        
        assertFalse(reporter.hasErrors());
        assertEquals(0, reporter.getErrorCount());
    }
}
