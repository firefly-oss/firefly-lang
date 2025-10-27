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
    
    private void typeCheck(CompilationUnit unit) {
        TypeChecker checker = new TypeChecker(reporter, symbolTable);
        checker.check(unit);
    }
    
    @Test
    public void testValidConcurrentExpression() {
        String source = """
            async fn fetchData() {
                concurrent {
                    let user = fetchUser().await,
                    let posts = fetchPosts().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for valid concurrent expression");
    }
    
    @Test
    public void testConcurrentInNonAsyncFunction() {
        String source = """
            fn fetchData() {
                concurrent {
                    let user = fetchUser().await,
                    let posts = fetchPosts().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for concurrent in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC003")));
    }
    
    @Test
    public void testDuplicateBindingInConcurrent() {
        String source = """
            async fn fetchData() {
                concurrent {
                    let data = fetch1().await,
                    let data = fetch2().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for duplicate binding names");
    }
    
    @Test
    public void testValidRaceExpression() {
        String source = """
            async fn fetchFastest() {
                race {
                    fetch1().await;
                    fetch2().await;
                    fetch3().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for valid race expression");
    }
    
    @Test
    public void testRaceInNonAsyncFunction() {
        String source = """
            fn fetchFastest() {
                race {
                    fetch1().await;
                    fetch2().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for race in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC005")));
    }
    
    @Test
    public void testValidTimeoutExpression() {
        String source = """
            async fn fetchWithTimeout() {
                timeout(5000) {
                    fetchData().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for valid timeout expression");
    }
    
    @Test
    public void testTimeoutWithInvalidDuration() {
        String source = """
            async fn fetchWithTimeout() {
                timeout("5000") {
                    fetchData().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for non-numeric timeout duration");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC007")));
    }
    
    @Test
    public void testTimeoutInNonAsyncFunction() {
        String source = """
            fn fetchWithTimeout() {
                timeout(5000) {
                    fetchData().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for timeout in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC006")));
    }
    
    @Test
    public void testValidCoalesceExpression() {
        String source = """
            fn getName(user: User?) {
                user?.name ?? "Unknown"
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for valid coalesce expression");
    }
    
    @Test
    public void testAwaitInNonAsyncFunction() {
        String source = """
            fn getData() {
                fetchData().await
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertTrue(reporter.hasErrors(), "Should have error for await in non-async function");
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TC002")));
    }
    
    @Test
    public void testNestedConcurrencyFeatures() {
        String source = """
            async fn complexFetch() {
                timeout(10000) {
                    race {
                        concurrent {
                            let a = fetch1().await,
                            let b = fetch2().await
                        };
                        fallback().await
                    }
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for nested concurrency features");
    }
    
    @Test
    public void testCoalesceWithTimeout() {
        String source = """
            async fn fetchWithFallback() {
                let result = timeout(3000) {
                    fetchData().await
                };
                result ?? getCachedData()
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for coalesce with timeout");
    }
    
    @Test
    public void testMultipleAsyncFunctions() {
        String source = """
            async fn fetch1() {
                concurrent {
                    let a = op1().await,
                    let b = op2().await
                }
            }
            
            async fn fetch2() {
                race {
                    source1().await;
                    source2().await
                }
            }
            
            async fn fetch3() {
                timeout(1000) {
                    operation().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        typeCheck(unit);
        
        assertFalse(reporter.hasErrors(), "Should not have errors for multiple async functions");
    }
}
