package com.firefly.compiler.ast;

import com.firefly.compiler.ast.decl.Declaration;

import java.util.List;
import java.util.Optional;

/**
 * Root node of the AST representing a complete source file.
 */
public class CompilationUnit implements AstNode {
    
    private final String packageName;
    private final List<ImportDeclaration> imports;
    private final List<Declaration> declarations;
    private final SourceLocation location;
    
    public CompilationUnit(
            String packageName,
            List<ImportDeclaration> imports,
            List<Declaration> declarations,
            SourceLocation location) {
        this.packageName = packageName;
        this.imports = imports;
        this.declarations = declarations;
        this.location = location;
    }
    
    public Optional<String> getPackageName() {
        return Optional.ofNullable(packageName);
    }
    
    public List<ImportDeclaration> getImports() {
        return imports;
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
