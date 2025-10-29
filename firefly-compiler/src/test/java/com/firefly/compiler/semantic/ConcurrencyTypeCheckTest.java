package com.firefly.compiler.semantic;

import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.diagnostic.DiagnosticReporter;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for concurrency type checking.
 */
public class ConcurrencyTypeCheckTest {
    
    private DiagnosticReporter reporter;
    private SymbolTable symbolTable;
    
    @BeforeEach
    public void setup() {
        reporter = new DiagnosticReporter();
        symbolTable = new SymbolTable();
    }
    
private CompilationUnit parse(String source) {
        FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        AstBuilder builder = new AstBuilder("test.fly");
        return (CompilationUnit) builder.visit(parser.compilationUnit());
    }

private String header() { return "module tests::concurrency\n\n"; }
    private String wrap(String body) { return "class Test {\n" + body + "\n}\n"; }
    
    private void typeCheck(CompilationUnit unit) {
        // Build symbol table first
        SymbolTableBuilder builder = new SymbolTableBuilder(reporter);
        symbolTable = builder.build(unit);

        // Then type check
        TypeChecker checker = new TypeChecker(reporter, symbolTable);
        checker.check(unit);
    }
    
    @Test
    public void testValidConcurrentExpression() {
String source = header() + wrap("  pub async fn fetchUser() -> Int { 42 }\n  pub async fn fetchPosts() -> String { \"posts\" }\n\n  pub async fn fetchData() -> Void {\n    concurrent {\n      let user = fetchUser().await,\n      let posts = fetchPosts().await\n    }\n  }\n");

        CompilationUnit unit = parse(source);
        typeCheck(unit);

        assertFalse(reporter.hasErrors(), "Should not have errors for valid concurrent expression");
    }
    
    @Test
    public void testConcurrentInNonAsyncFunction() {
String source = header() + wrap("  pub fn fetchData() -> Void {\n    concurrent {\n      let user = fetchUser().await,\n      let posts = fetchPosts().await\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for concurrent in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC003")));
    }
    
    @Test
    public void testDuplicateBindingInConcurrent() {
String source = header() + wrap("  pub async fn fetchData() -> Void {\n    concurrent {\n      let result = fetch1().await,\n      let result = fetch2().await\n    }\n  }\n");

        CompilationUnit unit = parse(source);
        typeCheck(unit);

        assertTrue(reporter.hasErrors(), "Should have error for duplicate binding names");
    }
    
    @Test
    public void testValidRaceExpression() {
String source = header() + wrap("  pub async fn fetch1() -> Int { 1 }\n  pub async fn fetch2() -> Int { 2 }\n  pub async fn fetch3() -> Int { 3 }\n\n  pub async fn fetchFastest() -> Void {\n    race {\n      fetch1().await;\n      fetch2().await;\n      fetch3().await\n    }\n  }\n");

        CompilationUnit unit = parse(source);
        typeCheck(unit);

        assertFalse(reporter.hasErrors(), "Should not have errors for valid race expression");
    }
    
    @Test
    public void testRaceInNonAsyncFunction() {
String source = header() + wrap("  pub fn fetchFastest() -> Void {\n    race {\n      fetch1().await;\n      fetch2().await\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for race in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC005")));
    }
    
    @Test
    public void testValidTimeoutExpression() {
String source = header() + wrap("  pub async fn fetchData() -> String { \"data\" }\n\n  pub async fn fetchWithTimeout() -> String? {\n    timeout(5000) {\n      fetchData().await\n    }\n  }\n");

        CompilationUnit unit = parse(source);
        typeCheck(unit);

        assertFalse(reporter.hasErrors(), "Should not have errors for valid timeout expression");
    }
    
    @Test
    public void testTimeoutWithInvalidDuration() {
String source = header() + wrap("  pub async fn fetchWithTimeout() -> String? {\n    timeout(\"5000\") {\n      fetchData().await\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for non-numeric timeout duration");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC007")));
    }
    
    @Test
    public void testTimeoutInNonAsyncFunction() {
String source = header() + wrap("  pub fn fetchWithTimeout() -> Void {\n    timeout(5000) {\n      fetchData().await\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for timeout in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC006")));
    }
    
    @Test
    public void testValidCoalesceExpression() {
String source = header() + wrap("  pub fn getName(user: User?) -> String {\n    user?.name ?? \"Unknown\"\n  }\n");
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for valid coalesce expression");
    }
    
    @Test
    public void testAwaitInNonAsyncFunction() {
String source = header() + wrap("  pub fn getData() -> Void {\n    fetchData().await\n  }\n");
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for await in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC002")));
    }
    
    @Test
    public void testNestedConcurrencyFeatures() {
String source = header() + wrap("  pub async fn fetch1() -> Int { 1 }\n  pub async fn fetch2() -> Int { 2 }\n  pub async fn fallback() -> Int { 0 }\n\n  pub async fn complexFetch() -> Int {\n    let result = timeout(10000) {\n      concurrent {\n        let a = fetch1().await,\n        let b = fetch2().await\n      };\n      0\n    };\n    result ?? fallback()\n  }\n");

        CompilationUnit unit = parse(source);
        typeCheck(unit);

        assertFalse(reporter.hasErrors(), "Should not have errors for nested concurrency features");
    }
    
    @Test
    public void testCoalesceWithTimeout() {
String source = header() + wrap("  pub async fn fetchData() -> String { \"data\" }\n  pub fn getCachedData() -> String { \"cached\" }\n\n  pub async fn fetchWithFallback() -> String {\n    let result = timeout(3000) {\n      fetchData().await\n    };\n    result ?? getCachedData()\n  }\n");

        CompilationUnit unit = parse(source);
        typeCheck(unit);

        assertFalse(reporter.hasErrors(), "Should not have errors for coalesce with timeout");
    }
    
    @Test
    public void testMultipleAsyncFunctions() {
String source = header() + wrap("  pub async fn op1() -> Int { 1 }\n  pub async fn op2() -> Int { 2 }\n  pub async fn source1() -> String { \"s1\" }\n  pub async fn source2() -> String { \"s2\" }\n  pub async fn operation() -> Bool { true }\n\n  pub async fn fetch1() -> Void {\n    concurrent {\n      let a = op1().await,\n      let b = op2().await\n    }\n  }\n\n  pub async fn fetch2() -> Void {\n    race {\n      source1().await;\n      source2().await\n    }\n  }\n\n  pub async fn fetch3() -> Bool? {\n    timeout(1000) {\n      operation().await\n    }\n  }\n");

        CompilationUnit unit = parse(source);
        typeCheck(unit);

        assertFalse(reporter.hasErrors(), "Should not have errors for multiple async functions");
    }
}
