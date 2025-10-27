package com.firefly.compiler.ast;

import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.FireflyBaseVisitor;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.expr.*;
import com.firefly.compiler.ast.type.*;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds Firefly AST from ANTLR parse tree.
 * Converts ANTLR's concrete syntax tree into our abstract syntax tree.
 */
public class AstBuilder extends FireflyBaseVisitor<AstNode> {
    
    private final String fileName;
    private List<Annotation> currentAnnotations = new ArrayList<>();
    
    public AstBuilder(String fileName) {
        this.fileName = fileName;
    }
    
    // ============ Compilation Unit ============
    
    @Override
    public CompilationUnit visitCompilationUnit(FireflyParser.CompilationUnitContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        // Package declaration
        String packageName = null;
        if (ctx.packageDeclaration() != null) {
            packageName = ctx.packageDeclaration().qualifiedName().getText();
        }
        
        // Import declarations
        List<ImportDeclaration> imports = ctx.importDeclaration().stream()
            .map(this::buildImport)
            .collect(Collectors.toList());
        
        // Top-level declarations
        List<Declaration> declarations = ctx.topLevelDeclaration().stream()
            .map(declCtx -> (Declaration) visit(declCtx))
            .collect(Collectors.toList());
        
        return new CompilationUnit(packageName, imports, declarations, loc);
    }
    
    @Override
    public Declaration visitTopLevelDeclaration(FireflyParser.TopLevelDeclarationContext ctx) {
        // Extract and store annotations
        currentAnnotations = new ArrayList<>();
        if (ctx.annotation() != null) {
            currentAnnotations = ctx.annotation().stream()
                .map(this::buildAnnotation)
                .collect(Collectors.toList());
        }
        
        // Visit the actual declaration
        Declaration result = null;
        if (ctx.functionDeclaration() != null) {
            result = (Declaration) visit(ctx.functionDeclaration());
        } else if (ctx.classDeclaration() != null) {
            result = (Declaration) visit(ctx.classDeclaration());
        } else if (ctx.interfaceDeclaration() != null) {
            result = (Declaration) visit(ctx.interfaceDeclaration());
        } else if (ctx.structDeclaration() != null) {
            result = (Declaration) visit(ctx.structDeclaration());
        } else if (ctx.dataDeclaration() != null) {
            result = (Declaration) visit(ctx.dataDeclaration());
        } else if (ctx.traitDeclaration() != null) {
            result = (Declaration) visit(ctx.traitDeclaration());
        }
        // Add other declaration types as needed
        
        // Clear annotations after use
        currentAnnotations = new ArrayList<>();
        
        return result;
    }
    
    private ImportDeclaration buildImport(FireflyParser.ImportDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        // Get the import path segments
        List<FireflyParser.PathSegmentContext> pathSegments = ctx.importPath().pathSegment();
        
        List<String> items = new ArrayList<>();
        boolean isWildcard = false;
        String modulePath;
        
        // Check if the last segment is a TYPE_IDENTIFIER (class/type)
        if (!pathSegments.isEmpty()) {
            FireflyParser.PathSegmentContext lastSegment = pathSegments.get(pathSegments.size() - 1);
            boolean lastIsType = lastSegment.TYPE_IDENTIFIER() != null;
            
            if (lastIsType && ctx.importItems() == null) {
                // Case: import org::springframework::boot::SpringApplication
                // Last segment is the type to import
                String typeName = lastSegment.TYPE_IDENTIFIER().getText();
                items.add(typeName);
                
                // Module path is everything except the last segment
                StringBuilder modulePathBuilder = new StringBuilder();
                for (int i = 0; i < pathSegments.size() - 1; i++) {
                    if (i > 0) modulePathBuilder.append(".");
                    modulePathBuilder.append(pathSegments.get(i).getText());
                }
                modulePath = modulePathBuilder.toString();
            } else {
                // Case: import org::springframework::boot (wildcard or with explicit items)
                modulePath = ctx.importPath().getText().replace("::", ".");
                
                // Process import items if present
                if (ctx.importItems() != null) {
                    FireflyParser.ImportItemsContext itemsCtx = ctx.importItems();
                    
                    if (itemsCtx instanceof FireflyParser.SingleImportContext) {
                        FireflyParser.SingleImportContext singleCtx = (FireflyParser.SingleImportContext) itemsCtx;
                        items.add(singleCtx.TYPE_IDENTIFIER().getText());
                    } else if (itemsCtx instanceof FireflyParser.MultipleImportsContext) {
                        FireflyParser.MultipleImportsContext multipleCtx = (FireflyParser.MultipleImportsContext) itemsCtx;
                        for (FireflyParser.ImportItemContext itemCtx : multipleCtx.importItem()) {
                            if (itemCtx instanceof FireflyParser.TypeImportItemContext) {
                                FireflyParser.TypeImportItemContext typeItemCtx = (FireflyParser.TypeImportItemContext) itemCtx;
                                items.add(typeItemCtx.TYPE_IDENTIFIER(0).getText());
                            } else if (itemCtx instanceof FireflyParser.FunctionImportItemContext) {
                                FireflyParser.FunctionImportItemContext funcItemCtx = (FireflyParser.FunctionImportItemContext) itemCtx;
                                items.add(funcItemCtx.IDENTIFIER(0).getText());
                            }
                        }
                    } else if (itemsCtx instanceof FireflyParser.WildcardImportContext) {
                        isWildcard = true;
                    }
                } else {
                    // No items and not ending with TYPE_IDENTIFIER - wildcard
                    isWildcard = true;
                }
            }
        } else {
            // Empty path - shouldn't happen
            modulePath = "";
            isWildcard = true;
        }
        
        return new ImportDeclaration(modulePath, items, isWildcard, loc);
    }
    
    // ============ Expressions ============
    
    @Override
    public Expression visitPrimaryExpr(FireflyParser.PrimaryExprContext ctx) {
        return (Expression) visit(ctx.primaryExpression());
    }
    
    @Override
    public Expression visitBlockExpr(FireflyParser.BlockExprContext ctx) {
        return (Expression) visit(ctx.blockExpression());
    }
    
    public Expression visitAdditiveExpr(FireflyParser.AdditiveExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression left = (Expression) visit(ctx.expression(0));
        Expression right = (Expression) visit(ctx.expression(1));
        BinaryExpr.BinaryOp op = ctx.op.getText().equals("+") 
            ? BinaryExpr.BinaryOp.ADD 
            : BinaryExpr.BinaryOp.SUBTRACT;
        return new BinaryExpr(left, op, right, loc);
    }
    
    @Override
    public Expression visitMultiplicativeExpr(FireflyParser.MultiplicativeExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression left = (Expression) visit(ctx.expression(0));
        Expression right = (Expression) visit(ctx.expression(1));
        
        BinaryExpr.BinaryOp op;
        String opText = ctx.op.getText();
        switch (opText) {
            case "*": op = BinaryExpr.BinaryOp.MULTIPLY; break;
            case "/": op = BinaryExpr.BinaryOp.DIVIDE; break;
            case "%": op = BinaryExpr.BinaryOp.MODULO; break;
            default: throw new RuntimeException("Unknown operator: " + opText);
        }
        
        return new BinaryExpr(left, op, right, loc);
    }
    
    @Override
    public Expression visitComparisonExpr(FireflyParser.ComparisonExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression left = (Expression) visit(ctx.expression(0));
        Expression right = (Expression) visit(ctx.expression(1));
        
        BinaryExpr.BinaryOp op;
        String opText = ctx.op.getText();
        switch (opText) {
            case "==": op = BinaryExpr.BinaryOp.EQUAL; break;
            case "!=": op = BinaryExpr.BinaryOp.NOT_EQUAL; break;
            case "<":  op = BinaryExpr.BinaryOp.LESS_THAN; break;
            case "<=": op = BinaryExpr.BinaryOp.LESS_EQUAL; break;
            case ">": op = BinaryExpr.BinaryOp.GREATER_THAN; break;
            case ">=": op = BinaryExpr.BinaryOp.GREATER_EQUAL; break;
            default: throw new RuntimeException("Unknown operator: " + opText);
        }
        
        return new BinaryExpr(left, op, right, loc);
    }
    
    @Override
    public Expression visitCallExpr(FireflyParser.CallExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression function = (Expression) visit(ctx.expression());
        
        List<Expression> arguments = new ArrayList<>();
        if (ctx.argumentList() != null) {
            arguments = ctx.argumentList().expression().stream()
                .map(argCtx -> (Expression) visit(argCtx))
                .collect(Collectors.toList());
        }
        
        return new CallExpr(function, arguments, loc);
    }
    
    @Override
    public Expression visitFieldAccessExpr(FireflyParser.FieldAccessExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression object = (Expression) visit(ctx.expression());
        String fieldName = ctx.IDENTIFIER().getText();
        return new FieldAccessExpr(object, fieldName, false, loc);
    }
    
    @Override
    public Expression visitClassLiteralExpr(FireflyParser.ClassLiteralExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression object = (Expression) visit(ctx.expression());
        // Create a field access to "class" which represents the Class literal
        return new FieldAccessExpr(object, "class", false, loc);
    }
    
    @Override
    public Expression visitSafeAccessExpr(FireflyParser.SafeAccessExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression object = (Expression) visit(ctx.expression());
        String fieldName = ctx.IDENTIFIER().getText();
        return new FieldAccessExpr(object, fieldName, true, loc);
    }
    
    @Override
    public Expression visitIndexAccessExpr(FireflyParser.IndexAccessExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression object = (Expression) visit(ctx.expression(0));
        Expression index = (Expression) visit(ctx.expression(1));
        return new IndexAccessExpr(object, index, loc);
    }
    
    @Override
    public Expression visitNotExpr(FireflyParser.NotExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression operand = (Expression) visit(ctx.expression());
        return new UnaryExpr(UnaryExpr.UnaryOp.NOT, operand, loc);
    }
    
    @Override
    public Expression visitUnaryMinusExpr(FireflyParser.UnaryMinusExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression operand = (Expression) visit(ctx.expression());
        return new UnaryExpr(UnaryExpr.UnaryOp.MINUS, operand, loc);
    }
    
    @Override
    public Expression visitAwaitExpr(FireflyParser.AwaitExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression operand = (Expression) visit(ctx.expression());
        return new UnaryExpr(UnaryExpr.UnaryOp.AWAIT, operand, loc);
    }
    
    @Override
    public Expression visitUnwrapExpr(FireflyParser.UnwrapExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression operand = (Expression) visit(ctx.expression());
        return new UnaryExpr(UnaryExpr.UnaryOp.UNWRAP, operand, loc);
    }
    
    @Override
    public Expression visitForceUnwrapExpr(FireflyParser.ForceUnwrapExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression operand = (Expression) visit(ctx.expression());
        return new UnaryExpr(UnaryExpr.UnaryOp.FORCE_UNWRAP, operand, loc);
    }
    
    @Override
    public Expression visitIfExpr(FireflyParser.IfExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        return (Expression) visit(ctx.ifExpression());
    }
    
    @Override
    public Expression visitIfExpression(FireflyParser.IfExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        // Main condition and then branch
        Expression condition = (Expression) visit(ctx.expression(0));
        BlockExpr thenBranch = (BlockExpr) visit(ctx.blockExpression(0));
        
        // Else-if branches
        List<IfExpr.ElseIfBranch> elseIfBranches = new ArrayList<>();
        for (int i = 1; i < ctx.expression().size(); i++) {
            Expression elseIfCondition = (Expression) visit(ctx.expression(i));
            BlockExpr elseIfBody = (BlockExpr) visit(ctx.blockExpression(i));
            elseIfBranches.add(new IfExpr.ElseIfBranch(elseIfCondition, elseIfBody));
        }
        
        // Else branch (if present)
        BlockExpr elseBranch = null;
        int lastBlockIndex = ctx.blockExpression().size() - 1;
        if (ctx.blockExpression().size() > ctx.expression().size()) {
            elseBranch = (BlockExpr) visit(ctx.blockExpression(lastBlockIndex));
        }
        
        return new IfExpr(condition, thenBranch, elseIfBranches, elseBranch, loc);
    }
    
    @Override
    public BlockExpr visitBlockExpression(FireflyParser.BlockExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        List<Statement> statements = ctx.statement().stream()
            .map(stmtCtx -> (Statement) visit(stmtCtx))
            .collect(Collectors.toList());
        
        Expression finalExpression = null;
        if (ctx.expression() != null) {
            finalExpression = (Expression) visit(ctx.expression());
        }
        
        return new BlockExpr(statements, finalExpression, loc);
    }
    
    @Override
    public Statement visitLetStmt(FireflyParser.LetStmtContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Pattern pattern = (Pattern) visit(ctx.pattern());
        Expression initializer = null;
        if (ctx.expression() != null) {
            initializer = (Expression) visit(ctx.expression());
        }
        return new LetStatement(pattern, initializer, false, loc);
    }
    
    @Override
    public Statement visitLetMutStmt(FireflyParser.LetMutStmtContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Pattern pattern = (Pattern) visit(ctx.pattern());
        Expression initializer = null;
        if (ctx.expression() != null) {
            initializer = (Expression) visit(ctx.expression());
        }
        return new LetStatement(pattern, initializer, true, loc);
    }
    
    @Override
    public Statement visitExprStmt(FireflyParser.ExprStmtContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression expr = (Expression) visit(ctx.expression());
        return new ExprStatement(expr, loc);
    }
    
    @Override
    public Statement visitAssignmentStmt(FireflyParser.AssignmentStmtContext ctx) {
        SourceLocation loc = getLocation(ctx);
        String varName = ctx.IDENTIFIER().getText();
        Expression target = new IdentifierExpr(varName, loc);
        Expression value = (Expression) visit(ctx.expression());
        Expression assignment = new AssignmentExpr(target, value, loc);
        return new ExprStatement(assignment, loc);
    }
    
    @Override
    public Expression visitForExpr(FireflyParser.ForExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        return (Expression) visit(ctx.forExpression());
    }
    
    @Override
    public Expression visitForExpression(FireflyParser.ForExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Pattern pattern = (Pattern) visit(ctx.pattern());
        Expression iterable = (Expression) visit(ctx.expression());
        BlockExpr body = (BlockExpr) visit(ctx.blockExpression());
        return new ForExpr(pattern, iterable, body, loc);
    }
    
    @Override
    public Expression visitWhileExpr(FireflyParser.WhileExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        return (Expression) visit(ctx.whileExpression());
    }
    
    @Override
    public Expression visitWhileExpression(FireflyParser.WhileExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression condition = (Expression) visit(ctx.expression());
        BlockExpr body = (BlockExpr) visit(ctx.blockExpression());
        return new WhileExpr(condition, body, loc);
    }
    
    @Override
    public Expression visitReturnExpr(FireflyParser.ReturnExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression value = null;
        if (ctx.expression() != null) {
            value = (Expression) visit(ctx.expression());
        }
        return new ReturnExpr(value, loc);
    }
    
    @Override
    public Expression visitBreakExpr(FireflyParser.BreakExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        return new BreakExpr(loc);
    }
    
    @Override
    public Expression visitContinueExpr(FireflyParser.ContinueExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        return new ContinueExpr(loc);
    }
    
    @Override
    public Expression visitConcurrentExpr(FireflyParser.ConcurrentExprContext ctx) {
        return (Expression) visit(ctx.concurrentExpression());
    }
    
    @Override
    public Expression visitConcurrentExpression(FireflyParser.ConcurrentExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        // Build list of concurrent bindings
        List<ConcurrentExpr.ConcurrentBinding> bindings = ctx.concurrentBinding().stream()
            .map(this::buildConcurrentBinding)
            .collect(Collectors.toList());
        
        return new ConcurrentExpr(bindings, loc);
    }
    
    private ConcurrentExpr.ConcurrentBinding buildConcurrentBinding(FireflyParser.ConcurrentBindingContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        Expression asyncExpr = (Expression) visit(ctx.expression());
        return new ConcurrentExpr.ConcurrentBinding(name, asyncExpr);
    }
    
    @Override
    public Expression visitRaceExpr(FireflyParser.RaceExprContext ctx) {
        return (Expression) visit(ctx.raceExpression());
    }
    
    @Override
    public Expression visitRaceExpression(FireflyParser.RaceExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        BlockExpr body = (BlockExpr) visit(ctx.blockExpression());
        return new RaceExpr(body, loc);
    }
    
    @Override
    public Expression visitTimeoutExpr(FireflyParser.TimeoutExprContext ctx) {
        return (Expression) visit(ctx.timeoutExpression());
    }
    
    @Override
    public Expression visitTimeoutExpression(FireflyParser.TimeoutExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression duration = (Expression) visit(ctx.expression());
        BlockExpr body = (BlockExpr) visit(ctx.blockExpression());
        return new TimeoutExpr(duration, body, loc);
    }
    
    @Override
    public Expression visitCoalesceExpr(FireflyParser.CoalesceExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        Expression left = (Expression) visit(ctx.expression(0));
        Expression right = (Expression) visit(ctx.expression(1));
        return new CoalesceExpr(left, right, loc);
    }
    
    @Override
    public Expression visitAssignmentExpr(FireflyParser.AssignmentExprContext ctx) {
        SourceLocation loc = getLocation(ctx);
        String varName = ctx.IDENTIFIER().getText();
        Expression target = new IdentifierExpr(varName, loc);
        Expression value = (Expression) visit(ctx.expression());
        return new AssignmentExpr(target, value, loc);
    }
    
    @Override
    public Expression visitPrimaryExpression(FireflyParser.PrimaryExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        if (ctx.literal() != null) {
            return (Expression) visit(ctx.literal());
        }
        
        if (ctx.IDENTIFIER() != null) {
            return new IdentifierExpr(ctx.IDENTIFIER().getText(), loc);
        }
        
        if (ctx.TYPE_IDENTIFIER() != null) {
            // Allow TYPE_IDENTIFIER as expression (for Class.class literals)
            return new IdentifierExpr(ctx.TYPE_IDENTIFIER().getText(), loc);
        }
        
        if (ctx.expression() != null) {
            // Parenthesized expression
            return (Expression) visit(ctx.expression());
        }
        
        if (ctx.getText().equals("self")) {
            return new IdentifierExpr("self", loc);
        }
        
        if (ctx.arrayLiteral() != null) {
            return (Expression) visit(ctx.arrayLiteral());
        }
        
        // TODO: Handle map literals, tuple literals, struct literals
        
        throw new RuntimeException("Unknown primary expression: " + ctx.getText());
    }
    
    @Override
    public Expression visitArrayLiteral(FireflyParser.ArrayLiteralContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        List<Expression> elements = new ArrayList<>();
        if (ctx.expression() != null) {
            elements = ctx.expression().stream()
                .map(exprCtx -> (Expression) visit(exprCtx))
                .collect(Collectors.toList());
        }
        
        return new ArrayLiteralExpr(elements, loc);
    }
    
    @Override
    public AstNode visitLiteral(FireflyParser.LiteralContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        if (ctx.INTEGER_LITERAL() != null) {
            String text = ctx.INTEGER_LITERAL().getText().replace("_", "");
            int value;
            if (text.startsWith("0x")) {
                value = Integer.parseInt(text.substring(2), 16);
            } else if (text.startsWith("0b")) {
                value = Integer.parseInt(text.substring(2), 2);
            } else if (text.startsWith("0o")) {
                value = Integer.parseInt(text.substring(2), 8);
            } else {
                value = Integer.parseInt(text);
            }
            return new LiteralExpr(LiteralExpr.LiteralKind.INTEGER, value, loc);
        }
        
        if (ctx.FLOAT_LITERAL() != null) {
            double value = Double.parseDouble(ctx.FLOAT_LITERAL().getText());
            return new LiteralExpr(LiteralExpr.LiteralKind.FLOAT, value, loc);
        }
        
        if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            // Remove quotes and handle escape sequences
            String value = text.substring(1, text.length() - 1)
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
            return new LiteralExpr(LiteralExpr.LiteralKind.STRING, value, loc);
        }
        
        if (ctx.CHAR_LITERAL() != null) {
            String text = ctx.CHAR_LITERAL().getText();
            char value = text.charAt(1); // Skip opening quote
            return new LiteralExpr(LiteralExpr.LiteralKind.CHAR, value, loc);
        }
        
        if (ctx.BOOLEAN_LITERAL() != null) {
            boolean value = ctx.BOOLEAN_LITERAL().getText().equals("true");
            return new LiteralExpr(LiteralExpr.LiteralKind.BOOLEAN, value, loc);
        }
        
        if (ctx.getText().equals("none")) {
            return new LiteralExpr(LiteralExpr.LiteralKind.NONE, null, loc);
        }
        
        throw new RuntimeException("Unknown literal type: " + ctx.getText());
    }
    
    // ============ Patterns ============
    
    @Override
    public Pattern visitVariablePattern(FireflyParser.VariablePatternContext ctx) {
        SourceLocation loc = getLocation(ctx);
        String name = ctx.IDENTIFIER().getText();
        return new com.firefly.compiler.ast.pattern.VariablePattern(name, false, loc);
    }
    
    @Override
    public Pattern visitMutableVariablePattern(FireflyParser.MutableVariablePatternContext ctx) {
        SourceLocation loc = getLocation(ctx);
        String name = ctx.IDENTIFIER().getText();
        return new com.firefly.compiler.ast.pattern.VariablePattern(name, true, loc);
    }
    
    @Override
    public Pattern visitLiteralPattern(FireflyParser.LiteralPatternContext ctx) {
        SourceLocation loc = getLocation(ctx);
        LiteralExpr literal = (LiteralExpr) visit(ctx.literal());
        return new com.firefly.compiler.ast.pattern.LiteralPattern(literal, loc);
    }
    
    @Override
    public Pattern visitWildcardPattern(FireflyParser.WildcardPatternContext ctx) {
        SourceLocation loc = getLocation(ctx);
        return new com.firefly.compiler.ast.pattern.WildcardPattern(loc);
    }
    
    @Override
    public Expression visitLambdaExpr(FireflyParser.LambdaExprContext ctx) {
        return (Expression) visit(ctx.lambdaExpression());
    }
    
    @Override
    public Expression visitLambdaExpression(FireflyParser.LambdaExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        // Extract parameter names
        List<String> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            parameters = ctx.parameterList().parameter().stream()
                .map(param -> param.IDENTIFIER().getText())
                .collect(Collectors.toList());
        }
        
        // Lambda body
        Expression body;
        if (ctx.expression() != null) {
            body = (Expression) visit(ctx.expression());
        } else {
            body = (Expression) visit(ctx.blockExpression());
        }
        
        return new LambdaExpr(parameters, body, loc);
    }
    
    @Override
    public Expression visitMatchExpr(FireflyParser.MatchExprContext ctx) {
        return (Expression) visit(ctx.matchExpression());
    }
    
    @Override
    public Expression visitMatchExpression(FireflyParser.MatchExpressionContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        // The value to match against
        Expression value = (Expression) visit(ctx.expression());
        
        // Build match arms
        List<MatchExpr.MatchArm> arms = ctx.matchArm().stream()
            .map(this::buildMatchArm)
            .collect(Collectors.toList());
        
        return new MatchExpr(value, arms, loc);
    }
    
    private MatchExpr.MatchArm buildMatchArm(FireflyParser.MatchArmContext ctx) {
        Pattern pattern = (Pattern) visit(ctx.pattern());
        
        // Optional guard (when clause)
        Expression guard = null;
        if (ctx.expression() != null && ctx.expression().size() > 1) {
            guard = (Expression) visit(ctx.expression(0));
        }
        
        // Arm body
        int bodyIndex = ctx.expression().size() - 1;
        Expression body = (Expression) visit(ctx.expression(bodyIndex));
        
        return new MatchExpr.MatchArm(pattern, guard, body);
    }
    
    // ============ Declarations ============
    
    @Override
    public FunctionDecl visitFunctionDeclaration(FireflyParser.FunctionDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        String name = ctx.IDENTIFIER().getText();
        boolean isAsync = ctx.ASYNC() != null;
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Parameters
        List<FunctionDecl.Parameter> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            parameters = ctx.parameterList().parameter().stream()
                .map(this::buildParameter)
                .collect(Collectors.toList());
        }
        
        // Return type
        java.util.Optional<Type> returnType = java.util.Optional.empty();
        if (ctx.type() != null) {
            returnType = java.util.Optional.of((Type) visit(ctx.type()));
        }
        
        // Body expression
        Expression body;
        if (ctx.expression() != null) {
            body = (Expression) visit(ctx.expression());
        } else {
            body = (Expression) visit(ctx.blockExpression());
        }
        
        return new FunctionDecl(name, parameters, returnType, body, isAsync, typeParameters, loc);
    }
    
    private FunctionDecl.Parameter buildParameter(FireflyParser.ParameterContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        Type type = (Type) visit(ctx.type());
        boolean isMutable = ctx.MUT() != null;
        
        java.util.Optional<Expression> defaultValue = java.util.Optional.empty();
        if (ctx.expression() != null) {
            defaultValue = java.util.Optional.of((Expression) visit(ctx.expression()));
        }
        
        // Extract parameter annotations
        List<Annotation> annotations = new ArrayList<>();
        if (ctx.annotation() != null) {
            annotations = ctx.annotation().stream()
                .map(this::buildAnnotation)
                .collect(Collectors.toList());
        }
        
        return new FunctionDecl.Parameter(name, type, defaultValue, isMutable, annotations);
    }
    
    @Override
    public ClassDecl visitClassDeclaration(FireflyParser.ClassDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        String name = ctx.TYPE_IDENTIFIER().getText();
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Superclass
        java.util.Optional<Type> superClass = java.util.Optional.empty();
        if (ctx.type() != null && ctx.EXTENDS() != null) {
            superClass = java.util.Optional.of((Type) visit(ctx.type()));
        }
        
        // Interfaces
        List<Type> interfaces = new ArrayList<>();
        if (ctx.typeList() != null) {
            interfaces = ctx.typeList().type().stream()
                .map(typeCtx -> (Type) visit(typeCtx))
                .collect(Collectors.toList());
        }
        
        // Annotations from parent topLevelDeclaration
        List<Annotation> annotations = new ArrayList<>(currentAnnotations);
        
        // Class members
        List<ClassDecl.FieldDecl> fields = new ArrayList<>();
        List<ClassDecl.MethodDecl> methods = new ArrayList<>();
        java.util.Optional<ClassDecl.ConstructorDecl> constructor = java.util.Optional.empty();
        
        for (FireflyParser.ClassMemberContext member : ctx.classMember()) {
            if (member.fieldDeclaration() != null) {
                fields.add(buildFieldDeclaration(member.fieldDeclaration()));
            } else if (member.methodDeclaration() != null) {
                methods.add(buildMethodDeclaration(member.methodDeclaration()));
            } else if (member.constructorDeclaration() != null) {
                constructor = java.util.Optional.of(buildConstructorDeclaration(member.constructorDeclaration()));
            }
        }
        
        return new ClassDecl(name, typeParameters, superClass, interfaces, fields, methods, constructor, annotations, loc);
    }
    
    private ClassDecl.FieldDecl buildFieldDeclaration(FireflyParser.FieldDeclarationContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        Type type = (Type) visit(ctx.type());
        boolean isMutable = ctx.MUT() != null;
        
        java.util.Optional<Expression> initializer = java.util.Optional.empty();
        if (ctx.expression() != null) {
            initializer = java.util.Optional.of((Expression) visit(ctx.expression()));
        }
        
        List<Annotation> annotations = new ArrayList<>();
        if (ctx.annotation() != null) {
            annotations = ctx.annotation().stream()
                .map(this::buildAnnotation)
                .collect(Collectors.toList());
        }
        
        return new ClassDecl.FieldDecl(name, type, isMutable, initializer, annotations);
    }
    
    private ClassDecl.MethodDecl buildMethodDeclaration(FireflyParser.MethodDeclarationContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        boolean isAsync = ctx.ASYNC() != null;
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Parameters
        List<FunctionDecl.Parameter> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            parameters = ctx.parameterList().parameter().stream()
                .map(this::buildParameter)
                .collect(Collectors.toList());
        }
        
        // Return type
        java.util.Optional<Type> returnType = java.util.Optional.empty();
        if (ctx.type() != null) {
            returnType = java.util.Optional.of((Type) visit(ctx.type()));
        }
        
        // Body
        Expression body = (Expression) visit(ctx.blockExpression());
        
        // Annotations
        List<Annotation> annotations = new ArrayList<>();
        if (ctx.annotation() != null) {
            annotations = ctx.annotation().stream()
                .map(this::buildAnnotation)
                .collect(Collectors.toList());
        }
        
        return new ClassDecl.MethodDecl(name, typeParameters, parameters, returnType, body, isAsync, annotations);
    }
    
    private ClassDecl.ConstructorDecl buildConstructorDeclaration(FireflyParser.ConstructorDeclarationContext ctx) {
        // Parameters
        List<FunctionDecl.Parameter> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            parameters = ctx.parameterList().parameter().stream()
                .map(this::buildParameter)
                .collect(Collectors.toList());
        }
        
        // Body
        Expression body = (Expression) visit(ctx.blockExpression());
        
        // Annotations
        List<Annotation> annotations = new ArrayList<>();
        if (ctx.annotation() != null) {
            annotations = ctx.annotation().stream()
                .map(this::buildAnnotation)
                .collect(Collectors.toList());
        }
        
        return new ClassDecl.ConstructorDecl(parameters, body, annotations);
    }
    
    private Annotation buildAnnotation(FireflyParser.AnnotationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        String name = ctx.qualifiedTypeName().getText();
        
        // Parse annotation arguments
        Map<String, Object> arguments = new java.util.HashMap<>();
        if (ctx.annotationArguments() != null) {
            for (FireflyParser.AnnotationArgumentContext argCtx : ctx.annotationArguments().annotationArgument()) {
                if (argCtx.IDENTIFIER() != null) {
                    // Named argument: name = value
                    String argName = argCtx.IDENTIFIER().getText();
                    Object value = extractLiteralValue(argCtx.literal());
                    arguments.put(argName, value);
                } else {
                    // Positional argument (becomes "value")
                    Object value = extractLiteralValue(argCtx.literal());
                    arguments.put("value", value);
                }
            }
        }
        
        return new Annotation(name, arguments, loc);
    }
    
    private Object extractLiteralValue(FireflyParser.LiteralContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            String text = ctx.INTEGER_LITERAL().getText().replace("_", "");
            return Integer.parseInt(text);
        } else if (ctx.FLOAT_LITERAL() != null) {
            return Double.parseDouble(ctx.FLOAT_LITERAL().getText());
        } else if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            // Remove quotes
            return text.substring(1, text.length() - 1);
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return ctx.BOOLEAN_LITERAL().getText().equals("true");
        }
        return null;
    }
    
    @Override
    public InterfaceDecl visitInterfaceDeclaration(FireflyParser.InterfaceDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        String name = ctx.TYPE_IDENTIFIER().getText();
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Super interfaces (extends clause)
        List<Type> superInterfaces = new ArrayList<>();
        if (ctx.typeList() != null) {
            superInterfaces = ctx.typeList().type().stream()
                .map(typeCtx -> (Type) visit(typeCtx))
                .collect(Collectors.toList());
        }
        
        // Annotations from parent topLevelDeclaration
        List<Annotation> annotations = new ArrayList<>(currentAnnotations);
        
        // Method signatures
        List<TraitDecl.FunctionSignature> methods = ctx.interfaceMember().stream()
            .map(member -> buildFunctionSignature(member.functionSignature()))
            .collect(Collectors.toList());
        
        return new InterfaceDecl(name, typeParameters, superInterfaces, methods, annotations, loc);
    }
    
    @Override
    public StructDecl visitStructDeclaration(FireflyParser.StructDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        String name = ctx.TYPE_IDENTIFIER().getText();
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Fields
        List<StructDecl.Field> fields = ctx.structField().stream()
            .map(this::buildStructField)
            .collect(Collectors.toList());
        
        return new StructDecl(name, typeParameters, fields, loc);
    }
    
    private StructDecl.Field buildStructField(FireflyParser.StructFieldContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        Type type = (Type) visit(ctx.type());
        
        java.util.Optional<Expression> defaultValue = java.util.Optional.empty();
        if (ctx.expression() != null) {
            defaultValue = java.util.Optional.of((Expression) visit(ctx.expression()));
        }
        
        return new StructDecl.Field(name, type, defaultValue);
    }
    
    @Override
    public DataDecl visitDataDeclaration(FireflyParser.DataDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        String name = ctx.TYPE_IDENTIFIER().getText();
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Variants
        List<DataDecl.Variant> variants = ctx.dataVariant().stream()
            .map(this::buildDataVariant)
            .collect(Collectors.toList());
        
        return new DataDecl(name, typeParameters, variants, loc);
    }
    
    private DataDecl.Variant buildDataVariant(FireflyParser.DataVariantContext ctx) {
        String name = ctx.TYPE_IDENTIFIER().getText();
        
        List<DataDecl.VariantField> fields = new ArrayList<>();
        if (ctx.fieldList() != null) {
            fields = ctx.fieldList().field().stream()
                .map(this::buildVariantField)
                .collect(Collectors.toList());
        }
        
        return new DataDecl.Variant(name, fields);
    }
    
    private DataDecl.VariantField buildVariantField(FireflyParser.FieldContext ctx) {
        Type type = (Type) visit(ctx.type());
        
        java.util.Optional<String> name = java.util.Optional.empty();
        if (ctx.IDENTIFIER() != null) {
            name = java.util.Optional.of(ctx.IDENTIFIER().getText());
        }
        
        return new DataDecl.VariantField(name, type);
    }
    
    @Override
    public TraitDecl visitTraitDeclaration(FireflyParser.TraitDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        String name = ctx.TYPE_IDENTIFIER().getText();
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Members (function signatures)
        List<TraitDecl.FunctionSignature> members = ctx.traitMember().stream()
            .map(member -> buildFunctionSignature(member.functionSignature()))
            .collect(Collectors.toList());
        
        return new TraitDecl(name, typeParameters, members, loc);
    }
    
    private TraitDecl.FunctionSignature buildFunctionSignature(FireflyParser.FunctionSignatureContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // Parameters
        List<FunctionDecl.Parameter> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            parameters = ctx.parameterList().parameter().stream()
                .map(this::buildParameter)
                .collect(Collectors.toList());
        }
        
        // Return type
        Type returnType = (Type) visit(ctx.type());
        
        return new TraitDecl.FunctionSignature(name, typeParameters, parameters, returnType);
    }
    
    @Override
    public ImplDecl visitImplDeclaration(FireflyParser.ImplDeclarationContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        String name = ctx.TYPE_IDENTIFIER().getText();
        
        // Type parameters
        List<String> typeParameters = new ArrayList<>();
        if (ctx.typeParameters() != null) {
            typeParameters = ctx.typeParameters().TYPE_IDENTIFIER().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.toList());
        }
        
        // For type (optional)
        java.util.Optional<Type> forType = java.util.Optional.empty();
        if (ctx.type() != null) {
            forType = java.util.Optional.of((Type) visit(ctx.type()));
        }
        
        // Methods
        List<FunctionDecl> methods = ctx.implMember().stream()
            .map(member -> visitFunctionDeclaration(member.functionDeclaration()))
            .collect(Collectors.toList());
        
        return new ImplDecl(name, typeParameters, forType, methods, loc);
    }
    
    // ============ Types ============
    
    @Override
    public Type visitType(FireflyParser.TypeContext ctx) {
        SourceLocation loc = getLocation(ctx);
        
        // Primitive type
        if (ctx.primitiveType() != null) {
            return (Type) visit(ctx.primitiveType());
        }
        
        // Named type
        if (ctx.TYPE_IDENTIFIER() != null) {
            String name = ctx.TYPE_IDENTIFIER().getText();
            // TODO: Support type arguments when NamedType is updated
            return new NamedType(name);
        }
        
        // Optional type (type?)
        if (ctx.getText().endsWith("?")) {
            Type innerType = (Type) visit(ctx.type(0));
            return new OptionalType(innerType);
        }
        
        // Array type ([type])
        if (ctx.getText().startsWith("[") && !ctx.getText().contains(":")) {
            Type elementType = (Type) visit(ctx.type(0));
            return new ArrayType(elementType);
        }
        
        // Function type ((params) -> returnType)
        if (ctx.getText().contains("->")) {
            List<Type> paramTypes = new ArrayList<>();
            if (ctx.typeList() != null) {
                paramTypes = ctx.typeList().type().stream()
                    .map(typeCtx -> (Type) visit(typeCtx))
                    .collect(Collectors.toList());
            }
            Type returnType = (Type) visit(ctx.type(ctx.type().size() - 1));
            return new FunctionType(paramTypes, returnType);
        }
        
        // Parenthesized type
        if (ctx.type() != null && ctx.type().size() == 1) {
            return (Type) visit(ctx.type(0));
        }
        
        throw new RuntimeException("Unknown type: " + ctx.getText());
    }
    
    @Override
    public Type visitPrimitiveType(FireflyParser.PrimitiveTypeContext ctx) {
        String typeName = ctx.getText();
        return new PrimitiveType(typeName);
    }
    
    // ============ Helper Methods ============
    
    private SourceLocation getLocation(org.antlr.v4.runtime.ParserRuleContext ctx) {
        return new SourceLocation(
            fileName,
            ctx.getStart().getLine(),
            ctx.getStart().getCharPositionInLine()
        );
    }
}
