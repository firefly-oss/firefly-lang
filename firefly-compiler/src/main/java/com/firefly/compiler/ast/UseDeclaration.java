package com.firefly.compiler.ast;

import java.util.List;

/**
 * Use declaration: use std::io::{println, read_line}
 */
public class UseDeclaration implements AstNode {
    
    private final String modulePath;
    private final List<String> items; // Empty for wildcard imports
    private final boolean isWildcard;
    private final SourceLocation location;
    
    public UseDeclaration(
            String modulePath,
            List<String> items,
            boolean isWildcard,
            SourceLocation location) {
        this.modulePath = modulePath;
        this.items = items;
        this.isWildcard = isWildcard;
        this.location = location;
    }
    
    public String getModulePath() {
        return modulePath;
    }
    
    public List<String> getItems() {
        return items;
    }
    
    public boolean isWildcard() {
        return isWildcard;
    }
    
    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitUseDeclaration(this);
    }
}
