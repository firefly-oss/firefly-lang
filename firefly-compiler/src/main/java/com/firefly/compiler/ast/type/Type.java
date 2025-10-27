package com.firefly.compiler.ast.type;

import com.firefly.compiler.ast.AstNode;

/**
 * Base interface for all types in Firefly.
 */
public interface Type extends AstNode {
    String getName();
}
