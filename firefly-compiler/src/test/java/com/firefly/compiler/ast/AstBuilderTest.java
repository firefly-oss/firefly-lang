package com.firefly.compiler.ast;

import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.expr.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AST builder.
 */
public class AstBuilderTest {
    
private CompilationUnit parse(String source) {
        FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        AstBuilder builder = new AstBuilder("test.fly");
        return (CompilationUnit) builder.visit(parser.compilationUnit());
    }

private String header(String module) {
        return "module " + module + "\n\n";
    }
    
    private String wrapInClass(String body) {
        return "class Test {\n" + body + "\n}\n";
    }
    
    @Test
    public void testSimpleLiteral() {
String source = header("tests::ast") + wrapInClass("  pub fn main() -> Int { 42 }\n");
        CompilationUnit unit = parse(source);
        
        assertNotNull(unit);
assertNotNull(unit.getModuleName());
assertTrue(unit.getUses().isEmpty());
        // TODO: Validate function declaration once we implement it
    }
    
    @Test
    public void testBinaryExpression() {
String source = header("tests::ast") + wrapInClass("  pub fn calculate() -> Void {\n    let x = 10 + 20;\n    let y = x * 2;\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testIfExpression() {
String source = header("tests::ast") + wrapInClass("  pub fn check(x: Int) -> Void {\n    if x > 10 {\n      println(\"big\");\n    } else {\n      println(\"small\");\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testForLoop() {
String source = header("tests::ast") + wrapInClass("  pub fn iterate() -> Void {\n    for item in items {\n      println(item);\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testImportDeclaration() {
String source = "module tests::ast\n\n" +
                "use std::io\n" +
                "use std::collections::{list, map}\n\n" +
wrapInClass("  pub fn main() -> Void {\n    println(\"Hello\");\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
assertEquals(2, unit.getUses().size());
assertEquals("std.io", unit.getUses().get(0).getModulePath());
    }
    
    @Test
    public void testPackageDeclaration() {
String source = "module com::example::app\n\n" + wrapInClass("  pub fn main() -> Void {}\n");

        CompilationUnit unit = parse(source);
        assertNotNull(unit);
assertEquals("com.example.app", unit.getModuleName());
    }
    
    @Test
    public void testComplexExpression() {
String source = header("tests::ast") + wrapInClass("  pub fn complex() -> Void {\n    let result = (10 + 20) * 3 - 5 / 2;\n    let name = user?.name ?? \"Unknown\";\n    let items = array[index];\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testCoalesceExpression() {
String source = header("tests::ast") + wrapInClass("  pub fn getNameOrDefault(user: User?) -> String {\n    let name = user?.name ?? \"Unknown\";\n    name\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testConcurrentExpression() {
String source = header("tests::ast") + wrapInClass("  pub async fn fetchData() -> Void {\n    concurrent {\n      let user = fetchUser().await,\n      let posts = fetchPosts().await,\n      let comments = fetchComments().await\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testRaceExpression() {
String source = header("tests::ast") + wrapInClass("  pub async fn fastestResponse() -> Void {\n    race {\n      fetchFromServer1().await;\n      fetchFromServer2().await;\n      fetchFromCache().await\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testTimeoutExpression() {
String source = header("tests::ast") + wrapInClass("  pub async fn fetchWithTimeout() -> Void {\n    timeout(5000) {\n      fetchData().await\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testCombinedConcurrencyFeatures() {
String source = header("tests::ast") + wrapInClass("  pub async fn complexAsync() -> Void {\n    let result = timeout(10000) {\n      race {\n        concurrent {\n          let a = fetch1().await,\n          let b = fetch2().await\n        };\n        fallbackValue()\n      }\n    };\n    result ?? defaultValue()\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
}
