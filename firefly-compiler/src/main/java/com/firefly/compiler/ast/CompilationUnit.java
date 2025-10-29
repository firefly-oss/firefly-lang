package com.firefly.compiler.ast;

import com.firefly.compiler.ast.decl.Declaration;

import java.util.List;
import java.util.Optional;

/**
 * Root node of the AST representing a complete source file.
 * 
 * <p>All Firefly files MUST declare a module (like Java's package requirement).</p>
 */
public class CompilationUnit implements AstNode {
    
    private final String moduleName;  // MANDATORY - never null
    private final List<UseDeclaration> uses;
    private final List<Declaration> declarations;
    private final SourceLocation location;
    
    public CompilationUnit(
            String moduleName,
            List<UseDeclaration> uses,
            List<Declaration> declarations,
            SourceLocation location) {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name is mandatory. All .fly files must declare a module.");
        }
        this.moduleName = moduleName;
        this.uses = uses;
        this.declarations = declarations;
        this.location = location;
    }
    
    /**
     * Get the module name (always present).
     * @return The module name (never null)
     */
    public String getModuleName() {
        return moduleName;
    }
    
    /**
     * Legacy method for backward compatibility.
     * @return Optional containing the module name
     */
    @Deprecated
    public Optional<String> getPackageName() {
        return Optional.of(moduleName);
    }
    
    public List<UseDeclaration> getUses() {
        return uses;
    }
    
    // Legacy method for backward compatibility
    public List<UseDeclaration> getImports() {
        return uses;
    }
    
    public List<Declaration> getDeclarations() {
        return declarations;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitCompilationUnit(this);
    }
}
