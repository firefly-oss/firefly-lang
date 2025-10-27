package com.firefly.compiler.ast;

/**
 * Base interface for all AST nodes in Firefly.
 */
public interface AstNode {
    
    /**
     * Accept a visitor for traversing the AST.
     */
    <T> T accept(AstVisitor<T> visitor);
    
    /**
     * Get the source location of this node.
     */
    SourceLocation getLocation();
}
