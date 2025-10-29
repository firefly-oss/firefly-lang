package com.firefly.compiler.ast.decl;

import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.expr.BlockExpr;
import com.firefly.compiler.ast.expr.Expression;
import com.firefly.compiler.ast.type.Type;
import com.firefly.compiler.ast.type.TypeParameter;
import java.util.List;
import java.util.Optional;

/**
 * Represents a spark declaration (immutable smart record) in the AST.
 * Grammar: 'spark' TYPE_IDENTIFIER typeParameters? '{' sparkMember* '}'
 *
 * Sparks are immutable records with superpowers:
 * - Auto-generated getters, equals/hashCode, toString
 * - .with() method for copy-with-modifications
 * - Validation hooks
 * - Lifecycle hooks (before/after update)
 * - Computed properties
 * - Pattern matching support
 * - Optional features: @derive, @travelable, @events, @observable
 *
 * Example:
 * spark Account {
 *     id: String,
 *     balance: Int,
 *     owner: String,
 *     
 *     validate {
 *         require(balance >= 0, "Balance must be positive");
 *     }
 *     
 *     computed isOverdrawn: Bool {
 *         self.balance < 0
 *     }
 *     
 *     fn deposit(self, amount: Int) -> Account {
 *         self.with(balance: self.balance + amount)
 *     }
 * }
 */
public class SparkDecl extends Declaration {
    private final String name;
    private final List<TypeParameter> typeParameters;
    private final List<SparkField> fields;
    private final Optional<ValidationBlock> validateBlock;
    private final Optional<BeforeHook> beforeHook;
    private final Optional<AfterHook> afterHook;
    private final List<ComputedProperty> computedProperties;
    private final List<FunctionDecl> methods;
    private final List<Annotation> annotations; // @derive, @travelable, etc.

    // Full constructor with all features
    public SparkDecl(
        String name,
        List<TypeParameter> typeParameters,
        List<SparkField> fields,
        Optional<ValidationBlock> validateBlock,
        Optional<BeforeHook> beforeHook,
        Optional<AfterHook> afterHook,
        List<ComputedProperty> computedProperties,
        List<FunctionDecl> methods,
        List<Annotation> annotations,
        SourceLocation location
    ) {
        super(location);
        this.name = name;
        this.typeParameters = typeParameters;
        this.fields = fields;
        this.validateBlock = validateBlock;
        this.beforeHook = beforeHook;
        this.afterHook = afterHook;
        this.computedProperties = computedProperties;
        this.methods = methods;
        this.annotations = annotations;
    }
    
    // Simplified constructor for basic sparks
    public SparkDecl(
        String name,
        List<TypeParameter> typeParameters,
        List<SparkField> fields,
        List<FunctionDecl> methods,
        SourceLocation location
    ) {
        this(
            name,
            typeParameters,
            fields,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            new java.util.ArrayList<>(),
            methods,
            new java.util.ArrayList<>(),
            location
        );
    }

    public String getName() { return name; }
    public List<TypeParameter> getTypeParameters() { return typeParameters; }
    public List<SparkField> getFields() { return fields; }
    public Optional<ValidationBlock> getValidateBlock() { return validateBlock; }
    public Optional<BeforeHook> getBeforeHook() { return beforeHook; }
    public Optional<AfterHook> getAfterHook() { return afterHook; }
    public List<ComputedProperty> getComputedProperties() { return computedProperties; }
    public List<FunctionDecl> getMethods() { return methods; }
    public List<Annotation> getAnnotations() { return annotations; }

    /**
     * Check if spark has a specific annotation (e.g., @derive, @travelable)
     */
    public boolean hasAnnotation(String annotationName) {
        return annotations.stream()
            .anyMatch(ann -> ann.getName().equals(annotationName));
    }

    /**
     * Get annotation by name
     */
    public Optional<Annotation> getAnnotation(String annotationName) {
        return annotations.stream()
            .filter(ann -> ann.getName().equals(annotationName))
            .findFirst();
    }

    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visitSparkDecl(this);
    }

    /**
     * Represents a spark field.
     * Grammar: IDENTIFIER ':' type ('=' expression)?
     */
    public static class SparkField {
        private final String name;
        private final Type type;
        private final Optional<Expression> defaultValue;
        private final List<Annotation> annotations;

        public SparkField(
            String name,
            Type type,
            Optional<Expression> defaultValue,
            List<Annotation> annotations
        ) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.annotations = annotations;
        }

        public String getName() { return name; }
        public Type getType() { return type; }
        public Optional<Expression> getDefaultValue() { return defaultValue; }
        public List<Annotation> getAnnotations() { return annotations; }
    }

    /**
     * Validation block - runs on construction.
     * Grammar: 'validate' blockExpression
     */
    public static class ValidationBlock {
        private final BlockExpr body;

        public ValidationBlock(BlockExpr body) {
            this.body = body;
        }

        public BlockExpr getBody() { return body; }
    }

    /**
     * Before update hook - runs before any .with() call.
     * Grammar: 'before' 'update' blockExpression
     */
    public static class BeforeHook {
        private final BlockExpr body;

        public BeforeHook(BlockExpr body) {
            this.body = body;
        }

        public BlockExpr getBody() { return body; }
    }

    /**
     * After update hook - runs after any .with() call.
     * Grammar: 'after' 'update' '(' IDENTIFIER ',' IDENTIFIER ')' blockExpression
     */
    public static class AfterHook {
        private final String oldParamName;
        private final String newParamName;
        private final BlockExpr body;

        public AfterHook(String oldParamName, String newParamName, BlockExpr body) {
            this.oldParamName = oldParamName;
            this.newParamName = newParamName;
            this.body = body;
        }

        public String getOldParamName() { return oldParamName; }
        public String getNewParamName() { return newParamName; }
        public BlockExpr getBody() { return body; }
    }

    /**
     * Computed property - derived value from fields.
     * Grammar: 'computed' IDENTIFIER ':' type blockExpression
     */
    public static class ComputedProperty {
        private final String name;
        private final Type type;
        private final BlockExpr body;

        public ComputedProperty(String name, Type type, BlockExpr body) {
            this.name = name;
            this.type = type;
            this.body = body;
        }

        public String getName() { return name; }
        public Type getType() { return type; }
        public BlockExpr getBody() { return body; }
    }
}
