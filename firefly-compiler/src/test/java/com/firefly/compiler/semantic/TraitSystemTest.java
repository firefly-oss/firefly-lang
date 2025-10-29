package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.ast.decl.TraitDecl;
import com.firefly.compiler.ast.decl.ImplDecl;
import com.firefly.compiler.diagnostic.DiagnosticReporter;
import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for trait system functionality.
 */
public class TraitSystemTest {
    
    private CompilationUnit parse(String source) {
        FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        
        AstBuilder builder = new AstBuilder("test.fly");
        return (CompilationUnit) builder.visit(parser.compilationUnit());
    }
    
    @Test
    public void testSimpleTraitDeclaration() {
        String source = "module tests::traits\n\n" + """
            trait Printable {
                fn print() -> String;
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(1, unit.getDeclarations().size());
        
        TraitDecl trait = (TraitDecl) unit.getDeclarations().get(0);
        assertEquals("Printable", trait.getName());
        assertEquals(1, trait.getMembers().size());
        assertEquals("print", trait.getMembers().get(0).getName());
    }
    
    @Test
    public void testTraitWithMultipleMethods() {
        String source = "module tests::traits\n\n" + """
            trait Comparable {
                fn compare(other: String) -> String;
                fn equals(other: String) -> String;
            }
            """;

        CompilationUnit unit = parse(source);
        assertNotNull(unit);

        TraitDecl trait = (TraitDecl) unit.getDeclarations().get(0);
        assertEquals("Comparable", trait.getName());
        assertEquals(2, trait.getMembers().size());
    }
    
    @Test
    public void testTraitImplementation() {
        String source = "module tests::traits\n\n" + """
            trait Printable {
                fn print() -> String;
            }
            
            impl Printable for Int {
                fn print() -> String { "number" }
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        assertEquals(2, unit.getDeclarations().size());
        
        ImplDecl impl = (ImplDecl) unit.getDeclarations().get(1);
        assertEquals("Printable", impl.getName());
        assertTrue(impl.getForType().isPresent());
        assertEquals(1, impl.getMethods().size());
    }
    
    @Test
    public void testTraitCheckerValidation() {
        String source = "module tests::traits\n\n" + """
            trait Printable {
                fn print() -> String;
            }
            
            impl Printable for Int {
                fn print() -> String { "number" }
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter();
        // Run trait checks
        TraitChecker traitChecker = new TraitChecker(reporter);
        for (var decl : unit.getDeclarations()) {
            if (decl instanceof TraitDecl t) traitChecker.registerTrait(t);
            if (decl instanceof ImplDecl i) traitChecker.registerImpl(i);
        }

        // Should not have errors
        assertFalse(reporter.hasErrors());
    }
    
    @Test
    public void testMissingTraitMethod() {
        String source = "module tests::traits\n\n" + """
            trait Printable {
                fn print() -> String;
                fn debug() -> String;
            }
            
            impl Printable for Int {
                fn print() -> String { "number" }
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter();
        TraitChecker traitChecker = new TraitChecker(reporter);
        for (var decl : unit.getDeclarations()) {
            if (decl instanceof TraitDecl t) traitChecker.registerTrait(t);
            if (decl instanceof ImplDecl i) traitChecker.registerImpl(i);
        }
        
        // Should have error for missing debug() method
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TRAIT005")));
    }
    
    @Test
    public void testExtraMethodInImpl() {
        String source = "module tests::traits\n\n" + """
            trait Printable {
                fn print() -> String;
            }
            
            impl Printable for Int {
                fn print() -> String { "number" }
                fn extra() -> String { "extra" }
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter();
        TraitChecker traitChecker = new TraitChecker(reporter);
        for (var decl : unit.getDeclarations()) {
            if (decl instanceof TraitDecl t) traitChecker.registerTrait(t);
            if (decl instanceof ImplDecl i) traitChecker.registerImpl(i);
        }
        
        // Should have error for extra method not in trait
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TRAIT006")));
    }
    
    @Test
    public void testUndefinedTrait() {
        String source = "module tests::traits\n\n" + """
            impl NonExistent for Int {
                fn foo() -> String { "bar" }
            }
            """;
        
        CompilationUnit unit = parse(source);
        DiagnosticReporter reporter = new DiagnosticReporter();
        TraitChecker traitChecker = new TraitChecker(reporter);
        for (var decl : unit.getDeclarations()) {
            if (decl instanceof TraitDecl t) traitChecker.registerTrait(t);
            if (decl instanceof ImplDecl i) traitChecker.registerImpl(i);
        }
        
        // Should have error for undefined trait
        assertTrue(reporter.getDiagnostics().stream()
            .anyMatch(d -> d.getCode().equals("TRAIT002")));
    }
    
    @Test
    public void testGenericTrait() {
        String source = "module tests::traits\n\n" + """
            trait Container<T> {
                fn get() -> T;
                fn set(value: T) -> Unit;
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        
        TraitDecl trait = (TraitDecl) unit.getDeclarations().get(0);
        assertEquals("Container", trait.getName());
        assertEquals(1, trait.getTypeParameters().size());
        assertEquals("T", trait.getTypeParameters().get(0).getName());
    }
    
    @org.junit.jupiter.api.Disabled("Inherent impl parsing not supported yet")
    @Test
    public void testInherentImpl() {
        String source = "module tests::traits\n\n" + """
            impl Int {
                fn double() -> Int { self * 2 }
            }
            """;
        
        CompilationUnit unit = parse(source);
        assertNotNull(unit);
        
        ImplDecl impl = (ImplDecl) unit.getDeclarations().get(0);
        assertEquals("Int", impl.getName());
        assertFalse(impl.getForType().isPresent());
    }
}

