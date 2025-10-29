package com.firefly.lsp;

import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.*;
import com.firefly.compiler.ast.decl.*;
import com.firefly.compiler.ast.type.Type;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Professional symbol analyzer for Firefly source code.
 * Parses source code using the Firefly compiler's ANTLR parser and extracts symbol information.
 */
public class SymbolAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(SymbolAnalyzer.class);
    
    /**
     * Represents a symbol in the source code.
     */
    public static class SymbolInfo {
        public final String name;
        public final SymbolKind kind;
        public final Location location;
        public final String type;
        
        public SymbolInfo(String name, SymbolKind kind, Location location, String type) {
            this.name = name;
            this.kind = kind;
            this.location = location;
            this.type = type;
        }
    }
    
    /**
     * Symbol kinds supported by the analyzer.
     */
    public enum SymbolKind {
        FUNCTION,
        CLASS,
        INTERFACE,
        STRUCT,
        DATA,
        TRAIT,
        IMPL,
        VARIABLE,
        PARAMETER,
        FIELD,
        METHOD
    }
    
    /**
     * Analyze source code and extract all symbols.
     * 
     * @param uri The document URI
     * @param source The source code
     * @return Map of symbol names to their information
     */
    public Map<String, List<SymbolInfo>> analyzeSymbols(String uri, String source) {
        Map<String, List<SymbolInfo>> symbols = new HashMap<>();
        
        try {
            // Parse the source code
            FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(source));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            FireflyParser parser = new FireflyParser(tokens);
            
            // Build AST
            AstBuilder builder = new AstBuilder(uri);
            CompilationUnit unit = (CompilationUnit) builder.visit(parser.compilationUnit());
            
            // Extract symbols from declarations
            for (Declaration decl : unit.getDeclarations()) {
                extractSymbolsFromDeclaration(decl, uri, symbols);
            }
            
        } catch (Exception e) {
            logger.error("Failed to analyze symbols in {}: {}", uri, e.getMessage(), e);
        }
        
        return symbols;
    }
    
    /**
     * Extract symbols from a declaration.
     */
    private void extractSymbolsFromDeclaration(Declaration decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        if (decl instanceof FunctionDecl) {
            extractFunctionSymbol((FunctionDecl) decl, uri, symbols);
        } else if (decl instanceof ClassDecl) {
            extractClassSymbol((ClassDecl) decl, uri, symbols);
        } else if (decl instanceof InterfaceDecl) {
            extractInterfaceSymbol((InterfaceDecl) decl, uri, symbols);
        } else if (decl instanceof StructDecl) {
            extractStructSymbol((StructDecl) decl, uri, symbols);
        } else if (decl instanceof DataDecl) {
            extractDataSymbol((DataDecl) decl, uri, symbols);
        } else if (decl instanceof TraitDecl) {
            extractTraitSymbol((TraitDecl) decl, uri, symbols);
        } else if (decl instanceof ImplDecl) {
            extractImplSymbol((ImplDecl) decl, uri, symbols);
        }
    }
    
    /**
     * Extract function symbol.
     */
    private void extractFunctionSymbol(FunctionDecl decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        String name = decl.getName();
        Location location = createLocation(uri, decl.getLocation());
        String type = buildFunctionType(decl);
        
        SymbolInfo symbol = new SymbolInfo(name, SymbolKind.FUNCTION, location, type);
        symbols.computeIfAbsent(name, k -> new ArrayList<>()).add(symbol);
        
        // Extract parameters
        for (FunctionDecl.Parameter param : decl.getParameters()) {
            String paramName = param.getName();
            Location paramLocation = createLocation(uri, decl.getLocation()); // Approximate
            String paramType = param.getType().toString();
            
            SymbolInfo paramSymbol = new SymbolInfo(paramName, SymbolKind.PARAMETER, paramLocation, paramType);
            symbols.computeIfAbsent(paramName, k -> new ArrayList<>()).add(paramSymbol);
        }
    }
    
    /**
     * Extract class symbol.
     */
    private void extractClassSymbol(ClassDecl decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        String name = decl.getName();
        Location location = createLocation(uri, decl.getLocation());
        
        SymbolInfo symbol = new SymbolInfo(name, SymbolKind.CLASS, location, "class");
        symbols.computeIfAbsent(name, k -> new ArrayList<>()).add(symbol);
        
        // Extract fields
        for (ClassDecl.FieldDecl field : decl.getFields()) {
            String fieldName = field.getName();
            Location fieldLocation = createLocation(uri, decl.getLocation()); // Approximate
            String fieldType = field.getType().toString();
            
            SymbolInfo fieldSymbol = new SymbolInfo(fieldName, SymbolKind.FIELD, fieldLocation, fieldType);
            symbols.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(fieldSymbol);
        }
        
        // Extract methods
        for (ClassDecl.MethodDecl method : decl.getMethods()) {
            String methodName = method.getName();
            Location methodLocation = createLocation(uri, decl.getLocation()); // Approximate
            String methodType = buildMethodType(method);
            
            SymbolInfo methodSymbol = new SymbolInfo(methodName, SymbolKind.METHOD, methodLocation, methodType);
            symbols.computeIfAbsent(methodName, k -> new ArrayList<>()).add(methodSymbol);
        }
    }
    
    /**
     * Extract interface symbol.
     */
    private void extractInterfaceSymbol(InterfaceDecl decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        String name = decl.getName();
        Location location = createLocation(uri, decl.getLocation());
        
        SymbolInfo symbol = new SymbolInfo(name, SymbolKind.INTERFACE, location, "interface");
        symbols.computeIfAbsent(name, k -> new ArrayList<>()).add(symbol);
        
        // Extract method signatures
        for (TraitDecl.FunctionSignature method : decl.getMethods()) {
            String methodName = method.getName();
            Location methodLocation = createLocation(uri, decl.getLocation()); // Approximate
            String methodType = buildSignatureType(method);
            
            SymbolInfo methodSymbol = new SymbolInfo(methodName, SymbolKind.METHOD, methodLocation, methodType);
            symbols.computeIfAbsent(methodName, k -> new ArrayList<>()).add(methodSymbol);
        }
    }
    
    /**
     * Extract struct symbol.
     */
    private void extractStructSymbol(StructDecl decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        String name = decl.getName();
        Location location = createLocation(uri, decl.getLocation());
        
        SymbolInfo symbol = new SymbolInfo(name, SymbolKind.STRUCT, location, "struct");
        symbols.computeIfAbsent(name, k -> new ArrayList<>()).add(symbol);
        
        // Extract fields
        for (StructDecl.Field field : decl.getFields()) {
            String fieldName = field.getName();
            Location fieldLocation = createLocation(uri, decl.getLocation()); // Approximate
            String fieldType = field.getType().toString();
            
            SymbolInfo fieldSymbol = new SymbolInfo(fieldName, SymbolKind.FIELD, fieldLocation, fieldType);
            symbols.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(fieldSymbol);
        }
    }
    
    /**
     * Extract data type symbol.
     */
    private void extractDataSymbol(DataDecl decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        String name = decl.getName();
        Location location = createLocation(uri, decl.getLocation());
        
        SymbolInfo symbol = new SymbolInfo(name, SymbolKind.DATA, location, "data");
        symbols.computeIfAbsent(name, k -> new ArrayList<>()).add(symbol);
        
        // Extract variants
        for (DataDecl.Variant variant : decl.getVariants()) {
            String variantName = variant.getName();
            Location variantLocation = createLocation(uri, decl.getLocation()); // Approximate
            
            SymbolInfo variantSymbol = new SymbolInfo(variantName, SymbolKind.DATA, variantLocation, "variant");
            symbols.computeIfAbsent(variantName, k -> new ArrayList<>()).add(variantSymbol);
        }
    }
    
    /**
     * Extract trait symbol.
     */
    private void extractTraitSymbol(TraitDecl decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        String name = decl.getName();
        Location location = createLocation(uri, decl.getLocation());
        
        SymbolInfo symbol = new SymbolInfo(name, SymbolKind.TRAIT, location, "trait");
        symbols.computeIfAbsent(name, k -> new ArrayList<>()).add(symbol);
    }
    
    /**
     * Extract impl symbol.
     */
    private void extractImplSymbol(ImplDecl decl, String uri, Map<String, List<SymbolInfo>> symbols) {
        // Extract methods from impl block
        for (FunctionDecl method : decl.getMethods()) {
            extractFunctionSymbol(method, uri, symbols);
        }
    }
    
    /**
     * Create LSP Location from URI and SourceLocation.
     */
    private Location createLocation(String uri, SourceLocation loc) {
        Location location = new Location();
        location.setUri(uri);
        location.setRange(createRange(loc));
        return location;
    }
    
    /**
     * Create LSP Range from SourceLocation.
     */
    private Range createRange(SourceLocation loc) {
        Range range = new Range();
        
        if (loc != null) {
            // Convert 1-based to 0-based
            int line = Math.max(0, loc.line() - 1);
            int column = Math.max(0, loc.column() - 1);
            
            range.setStart(new Position(line, column));
            range.setEnd(new Position(line, column + 1)); // Approximate end
        } else {
            range.setStart(new Position(0, 0));
            range.setEnd(new Position(0, 0));
        }
        
        return range;
    }
    
    /**
     * Build function type string.
     */
    private String buildFunctionType(FunctionDecl decl) {
        StringBuilder sb = new StringBuilder();
        sb.append("fn(");
        
        for (int i = 0; i < decl.getParameters().size(); i++) {
            if (i > 0) sb.append(", ");
            FunctionDecl.Parameter param = decl.getParameters().get(i);
            sb.append(param.getType().toString());
        }
        
        sb.append(")");
        
        if (decl.getReturnType().isPresent()) {
            sb.append(" -> ").append(decl.getReturnType().get().toString());
        }
        
        return sb.toString();
    }
    
    /**
     * Build method type string.
     */
    private String buildMethodType(ClassDecl.MethodDecl method) {
        StringBuilder sb = new StringBuilder();
        sb.append("fn(");
        
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) sb.append(", ");
            FunctionDecl.Parameter param = method.getParameters().get(i);
            sb.append(param.getType().toString());
        }
        
        sb.append(")");
        
        if (method.getReturnType().isPresent()) {
            sb.append(" -> ").append(method.getReturnType().get().toString());
        }
        
        return sb.toString();
    }
    
    /**
     * Build signature type string.
     */
    private String buildSignatureType(TraitDecl.FunctionSignature sig) {
        StringBuilder sb = new StringBuilder();
        sb.append("fn(");
        
        for (int i = 0; i < sig.getParameters().size(); i++) {
            if (i > 0) sb.append(", ");
            FunctionDecl.Parameter param = sig.getParameters().get(i);
            sb.append(param.getType().toString());
        }
        
        sb.append(")");
        
        Type returnType = sig.getReturnType();
        if (returnType != null) {
            sb.append(" -> ").append(returnType.toString());
        }
        
        return sb.toString();
    }
    
    /**
     * Get symbol at a specific position in the source code.
     * Uses simple text-based matching to find the identifier at the cursor position.
     */
    public String getSymbolAtPosition(String source, int line, int character) {
        String[] lines = source.split("\n");
        if (line < 0 || line >= lines.length) {
            return null;
        }
        
        String currentLine = lines[line];
        if (character < 0 || character >= currentLine.length()) {
            return null;
        }
        
        // Find the start and end of the identifier at the cursor position
        int start = character;
        int end = character;
        
        // Move start backwards while we're in an identifier
        while (start > 0 && isIdentifierChar(currentLine.charAt(start - 1))) {
            start--;
        }
        
        // Move end forwards while we're in an identifier
        while (end < currentLine.length() && isIdentifierChar(currentLine.charAt(end))) {
            end++;
        }
        
        if (start == end) {
            return null;
        }
        
        return currentLine.substring(start, end);
    }
    
    /**
     * Check if a character is part of an identifier.
     */
    private boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}

