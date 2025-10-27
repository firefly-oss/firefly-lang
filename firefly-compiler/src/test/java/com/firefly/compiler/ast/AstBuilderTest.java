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
    
    @Test
    public void testSimpleLiteral() {
        String source = "fn main() { 42 }";
        CompilationUnit unit = parse(source);
        
        assertNotNull(unit);
        assertFalse(unit.getPackageName().isPresent());
        assertTrue(unit.getImports().isEmpty());
        // TODO: Validate function declaration once we implement it
    }
    
    @Test
    public void testBinaryExpression() {
        String source = """
            fn calculate() {
                let x = 10 + 20;
                let y = x * 2;
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testIfExpression() {
        String source = """
            fn check(x: Int) {
                if x > 10 {
                    println("big");
                } else {
                    println("small");
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testForLoop() {
        String source = """
            fn iterate() {
                for item in items {
                    println(item);
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testImportDeclaration() {
        String source = """
            import std::io
            import std::collections::{list, map}
            
            fn main() {
                println("Hello");
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(2, unit.getImports().size());
        assertEquals("std.io", unit.getImports().get(0).getModulePath());
    }
    
    @Test
    public void testPackageDeclaration() {
        String source = """
            package com.example.app
            
            fn main() {}
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertTrue(unit.getPackageName().isPresent());
        assertEquals("com.example.app", unit.getPackageName().get());
    }
    
    @Test
    public void testComplexExpression() {
        String source = """
            fn complex() {
                let result = (10 + 20) * 3 - 5 / 2;
                let name = user?.name ?? "Unknown";
                let items = array[index];
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testCoalesceExpression() {
        String source = """
            fn getNameOrDefault(user: User?) {
                let name = user?.name ?? "Unknown";
                name
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testConcurrentExpression() {
        String source = """
            async fn fetchData() {
                concurrent {
                    let user = fetchUser().await,
                    let posts = fetchPosts().await,
                    let comments = fetchComments().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testRaceExpression() {
        String source = """
            async fn fastestResponse() {
                race {
                    fetchFromServer1().await;
                    fetchFromServer2().await;
                    fetchFromCache().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testTimeoutExpression() {
        String source = """
            async fn fetchWithTimeout() {
                timeout(5000) {
                    fetchData().await
                }
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
    
    @Test
    public void testCombinedConcurrencyFeatures() {
        String source = """
            async fn complexAsync() {
                let result = timeout(10000) {
                    race {
                        concurrent {
                            let a = fetch1().await,
                            let b = fetch2().await
                        };
                        fallbackValue()
                    }
                };
                result ?? defaultValue()
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
    }
}
