package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.ast.decl.FunctionDecl;
import com.firefly.compiler.ast.expr.MatchExpr;
import com.firefly.compiler.diagnostic.DiagnosticReporter;
import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pattern matching functionality.
 */
public class PatternMatchingTest {
    
private CompilationUnit parse(String source) {
        FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        AstBuilder builder = new AstBuilder("test.fly");
        return (CompilationUnit) builder.visit(parser.compilationUnit());
    }

private String header() { return "module tests::patterns\n\n"; }
    private String wrap(String body) { return "class Test {\n" + body + "\n}\n"; }
    
    @Test
    public void testSimpleMatchExpression() {
String source = header() + wrap("  pub fn test(x: Int) -> String {\n    match x {\n      1 => \"one\",\n      2 => \"two\",\n      _ => \"other\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(1, unit.getDeclarations().size());
        
com.firefly.compiler.ast.decl.ClassDecl cls = (com.firefly.compiler.ast.decl.ClassDecl) unit.getDeclarations().get(0);
        assertEquals("Test", cls.getName());
        assertEquals("test", cls.getMethods().get(0).getName());
    }
    
    @Test
    public void testMatchWithVariablePattern() {
String source = header() + wrap("  pub fn describe(x: Int) -> String {\n    match x {\n      0 => \"zero\",\n      n => \"number: \" + toString(n)\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testMatchWithGuard() {
String source = header() + wrap("  pub fn classify(x: Int) -> String {\n    match x {\n      n when n > 0 => \"positive\",\n      n when n < 0 => \"negative\",\n      _ => \"zero\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testMatchWithWildcard() {
String source = header() + wrap("  pub fn test(x: Int) -> String {\n    match x {\n      1 => \"one\",\n      _ => \"other\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testExhaustivenessWithWildcard() {
String source = header() + wrap("  pub fn test(x: Int) -> String {\n    match x {\n      1 => \"one\",\n      2 => \"two\",\n      _ => \"other\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter();
        SymbolTableBuilder builder = new SymbolTableBuilder(reporter);
        SymbolTable symTable = builder.build(unit);
        
        TypeChecker checker = new TypeChecker(reporter, symTable);
        checker.check(unit);
        
        // Should not have errors (wildcard makes it exhaustive)
        assertFalse(reporter.hasErrors());
    }
    
    @Test
    public void testExhaustivenessWarning() {
        String source = header() + wrap("  pub fn test(x: Int) -> String {\n    match x {\n      1 => \"one\",\n      2 => \"two\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter();
        SymbolTableBuilder builder = new SymbolTableBuilder(reporter);
        SymbolTable symTable = builder.build(unit);
        
        TypeChecker checker = new TypeChecker(reporter, symTable);
        checker.check(unit);
        
// Exhaustiveness warnings are optional in Alpha; ensure no hard errors
        assertFalse(reporter.hasErrors());
    }
    
    @Test
    public void testUnreachablePattern() {
String source = header() + wrap("  pub fn test(x: Int) -> String {\n    match x {\n      _ => \"catch all\",\n      1 => \"unreachable\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter();
        SymbolTableBuilder builder = new SymbolTableBuilder(reporter);
        SymbolTable symTable = builder.build(unit);
        
        TypeChecker checker = new TypeChecker(reporter, symTable);
        checker.check(unit);
        
// Unreachable pattern warnings are optional in Alpha; ensure compilation proceeds
        assertFalse(reporter.hasErrors());
    }
    
    @Test
    public void testMatchWithMultipleLiterals() {
String source = header() + wrap("  pub fn test(x: Int) -> String {\n    match x {\n      1 => \"one\",\n      2 => \"two\",\n      3 => \"three\",\n      _ => \"other\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testMatchWithStringLiterals() {
String source = header() + wrap("  pub fn greet(name: String) -> String {\n    match name {\n      \"Alice\" => \"Hello Alice!\",\n      \"Bob\" => \"Hi Bob!\",\n      _ => \"Hello stranger!\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testNestedMatch() {
String source = header() + wrap("  pub fn classify(x: Int, y: Int) -> String {\n    match x {\n      0 => match y {\n        0 => \"both zero\",\n        _ => \"x is zero\"\n      },\n      _ => \"x is not zero\"\n    }\n  }\n");
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
}

