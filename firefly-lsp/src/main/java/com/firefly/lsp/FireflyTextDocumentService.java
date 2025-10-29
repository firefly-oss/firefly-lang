package com.firefly.lsp;

import com.firefly.compiler.FireflyCompiler;
import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.diagnostics.CompilerDiagnostic;
import com.firefly.compiler.semantics.SemanticAnalyzer;
import com.firefly.compiler.codegen.TypeResolver;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Text Document Service for Firefly Language Server.
 * 
 * <p>Handles all text document-related operations:
 * <ul>
 *   <li>Document open/close/change events</li>
 *   <li>Diagnostics (syntax errors)</li>
 *   <li>Code completion</li>
 *   <li>Hover information</li>
 *   <li>Go to definition</li>
 *   <li>Document symbols</li>
 * </ul>
 */
public class FireflyTextDocumentService implements TextDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(FireflyTextDocumentService.class);

    private final FireflyLanguageServer server;
    private final Map<String, String> documentContents = new HashMap<>();
    private final SymbolAnalyzer symbolAnalyzer = new SymbolAnalyzer();
    private final Map<String, Map<String, List<SymbolAnalyzer.SymbolInfo>>> documentSymbols = new HashMap<>();

    public FireflyTextDocumentService(FireflyLanguageServer server) {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();

        logger.info("Document opened: {}", uri);
        documentContents.put(uri, text);

        // Analyze symbols
        analyzeDocumentSymbols(uri, text);

        // Validate document
        validateDocument(uri, text);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getContentChanges().get(0).getText();

        logger.info("📝 Document changed: {} ({} chars)", uri, text.length());
        documentContents.put(uri, text);

        // Analyze symbols
        analyzeDocumentSymbols(uri, text);

        // Validate document
        logger.info("🔍 Starting validation for: {}", uri);
        validateDocument(uri, text);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        logger.info("Document closed: {}", uri);
        documentContents.remove(uri);
        
        // Clear diagnostics
        publishDiagnostics(uri, new ArrayList<>());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        logger.info("Document saved: {}", uri);
        
        // Re-validate on save
        String text = documentContents.get(uri);
        if (text != null) {
            validateDocument(uri, text);
        }
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        logger.debug("Completion requested at {}:{}", 
            params.getPosition().getLine(), 
            params.getPosition().getCharacter());
        
        List<CompletionItem> items = new ArrayList<>();
        
        // Add Firefly keywords
        addKeywordCompletions(items);
        
        // Add runtime classes
        addRuntimeCompletions(items);
        
        // Add common types
        addTypeCompletions(items);
        
        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        Position position = params.getPosition();
        
        logger.debug("Hover requested at {}:{}", position.getLine(), position.getCharacter());
        
        // Get word at position
        String text = documentContents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String word = getWordAtPosition(text, position);
        if (word == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Provide hover information
        String hoverText = getHoverInfo(word);
        if (hoverText != null) {
            MarkupContent content = new MarkupContent();
            content.setKind(MarkupKind.MARKDOWN);
            content.setValue(hoverText);
            
            Hover hover = new Hover();
            hover.setContents(content);
            return CompletableFuture.completedFuture(hover);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        Position position = params.getPosition();

        logger.debug("Definition requested at {}:{}", position.getLine(), position.getCharacter());

        List<Location> locations = new ArrayList<>();

        // Get the symbol at the cursor position
        String text = documentContents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(Either.forLeft(locations));
        }

        String symbolName = symbolAnalyzer.getSymbolAtPosition(text, position.getLine(), position.getCharacter());
        if (symbolName == null) {
            return CompletableFuture.completedFuture(Either.forLeft(locations));
        }

        logger.debug("Looking for definition of symbol: {}", symbolName);

        // Search for the symbol definition in the current document
        Map<String, List<SymbolAnalyzer.SymbolInfo>> symbols = documentSymbols.get(uri);
        if (symbols != null && symbols.containsKey(symbolName)) {
            List<SymbolAnalyzer.SymbolInfo> symbolInfos = symbols.get(symbolName);
            for (SymbolAnalyzer.SymbolInfo symbolInfo : symbolInfos) {
                // Add the first definition found (usually the declaration)
                if (symbolInfo.kind != SymbolAnalyzer.SymbolKind.PARAMETER) {
                    locations.add(symbolInfo.location);
                    break;
                }
            }
        }

        logger.debug("Found {} definition(s) for symbol: {}", locations.size(), symbolName);
        return CompletableFuture.completedFuture(Either.forLeft(locations));
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        logger.debug("Document symbols requested for {}", uri);

        List<Either<SymbolInformation, DocumentSymbol>> symbols = new ArrayList<>();

        // Get symbols for this document
        Map<String, List<SymbolAnalyzer.SymbolInfo>> docSymbols = documentSymbols.get(uri);
        if (docSymbols == null) {
            return CompletableFuture.completedFuture(symbols);
        }

        // Convert to LSP SymbolInformation
        for (Map.Entry<String, List<SymbolAnalyzer.SymbolInfo>> entry : docSymbols.entrySet()) {
            for (SymbolAnalyzer.SymbolInfo symbolInfo : entry.getValue()) {
                // Skip parameters as they're not top-level symbols
                if (symbolInfo.kind == SymbolAnalyzer.SymbolKind.PARAMETER) {
                    continue;
                }

                SymbolInformation info = new SymbolInformation();
                info.setName(symbolInfo.name);
                info.setKind(convertSymbolKind(symbolInfo.kind));
                info.setLocation(symbolInfo.location);

                symbols.add(Either.forLeft(info));
            }
        }

        logger.debug("Returning {} symbols for {}", symbols.size(), uri);
        return CompletableFuture.completedFuture(symbols);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        String uri = params.getTextDocument().getUri();
        logger.debug("Formatting requested for {}", uri);

        List<TextEdit> edits = new ArrayList<>();

        String text = documentContents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(edits);
        }

        // Apply basic formatting rules
        String formatted = formatFireflyCode(text, params.getOptions());

        if (!formatted.equals(text)) {
            // Create a text edit that replaces the entire document
            TextEdit edit = new TextEdit();

            String[] lines = text.split("\n", -1);
            Range range = new Range();
            range.setStart(new Position(0, 0));
            range.setEnd(new Position(lines.length - 1, lines[lines.length - 1].length()));

            edit.setRange(range);
            edit.setNewText(formatted);
            edits.add(edit);
        }

        logger.debug("Formatting complete for {}", uri);
        return CompletableFuture.completedFuture(edits);
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        String uri = params.getTextDocument().getUri();
        Position position = params.getPosition();

        logger.debug("Signature help requested at {}:{}", position.getLine(), position.getCharacter());

        String text = documentContents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Find the function call context
        String functionName = getFunctionNameAtPosition(text, position);
        if (functionName == null) {
            return CompletableFuture.completedFuture(null);
        }

        logger.debug("Looking for signature of function: {}", functionName);

        // Look up function signature
        Map<String, List<SymbolAnalyzer.SymbolInfo>> symbols = documentSymbols.get(uri);
        if (symbols != null && symbols.containsKey(functionName)) {
            for (SymbolAnalyzer.SymbolInfo symbolInfo : symbols.get(functionName)) {
                if (symbolInfo.kind == SymbolAnalyzer.SymbolKind.FUNCTION ||
                    symbolInfo.kind == SymbolAnalyzer.SymbolKind.METHOD) {

                    SignatureHelp help = new SignatureHelp();
                    SignatureInformation signature = new SignatureInformation();
                    signature.setLabel(functionName + symbolInfo.type.substring(2)); // Remove "fn" prefix
                    signature.setDocumentation("Function: " + functionName);

                    help.setSignatures(Collections.singletonList(signature));
                    help.setActiveSignature(0);
                    help.setActiveParameter(0);

                    return CompletableFuture.completedFuture(help);
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        String uri = params.getTextDocument().getUri();
        Position position = params.getPosition();

        logger.debug("References requested at {}:{}", position.getLine(), position.getCharacter());

        List<Location> locations = new ArrayList<>();

        String text = documentContents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(locations);
        }

        String symbolName = symbolAnalyzer.getSymbolAtPosition(text, position.getLine(), position.getCharacter());
        if (symbolName == null) {
            return CompletableFuture.completedFuture(locations);
        }

        logger.debug("Looking for references of symbol: {}", symbolName);

        // Find all occurrences of the symbol in the document
        String[] lines = text.split("\n");
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];
            int index = 0;
            while ((index = line.indexOf(symbolName, index)) != -1) {
                // Check if it's a whole word match
                boolean isWholeWord = true;
                if (index > 0 && Character.isJavaIdentifierPart(line.charAt(index - 1))) {
                    isWholeWord = false;
                }
                if (index + symbolName.length() < line.length() &&
                    Character.isJavaIdentifierPart(line.charAt(index + symbolName.length()))) {
                    isWholeWord = false;
                }

                if (isWholeWord) {
                    Location location = new Location();
                    location.setUri(uri);
                    Range range = new Range();
                    range.setStart(new Position(lineNum, index));
                    range.setEnd(new Position(lineNum, index + symbolName.length()));
                    location.setRange(range);
                    locations.add(location);
                }

                index += symbolName.length();
            }
        }

        logger.debug("Found {} reference(s) for symbol: {}", locations.size(), symbolName);
        return CompletableFuture.completedFuture(locations);
    }

    /**
     * Validate a Firefly document and publish diagnostics.
     * Uses the Firefly compiler's ANTLR parser and semantic analyzer for professional error reporting.
     */
    private void validateDocument(String uri, String text) {
        logger.info("🔍 Validating document: {} ({} lines)", uri, text.split("\n").length);
        List<Diagnostic> diagnostics = new ArrayList<>();

        // Basic syntax validation (fast, immediate feedback)
        logger.info("  ⚡ Running basic syntax validation...");
        List<Diagnostic> basicDiags = performBasicSyntaxValidation(text);
        diagnostics.addAll(basicDiags);
        logger.info("  ✓ Basic validation found {} issue(s)", basicDiags.size());

        // Professional compilation-based validation
        logger.info("  🔧 Running compiler validation...");
        List<Diagnostic> compilerDiags = performCompilerValidation(uri, text);
        diagnostics.addAll(compilerDiags);
        logger.info("  ✓ Compiler validation found {} issue(s)", compilerDiags.size());

        logger.info("📤 Publishing {} total diagnostic(s) for {}", diagnostics.size(), uri);
        publishDiagnostics(uri, diagnostics);
        logger.info("✅ Validation complete for {}", uri);
    }

    /**
     * Perform professional compiler-based validation using ANTLR parser and semantic analyzer.
     */
    private List<Diagnostic> performCompilerValidation(String uri, String text) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<String> syntaxErrors = new ArrayList<>();

        try {
            // Phase 1: Lexical and Syntax Analysis
            FireflyLexer lexer = new FireflyLexer(CharStreams.fromString(text));

            // Capture lexer errors
            lexer.removeErrorListeners();
            lexer.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                        int line, int charPositionInLine,
                                        String msg, RecognitionException e) {
                    Diagnostic diag = new Diagnostic();
                    diag.setSeverity(DiagnosticSeverity.Error);
                    diag.setMessage("Syntax error: " + msg);
                    diag.setRange(createRange(line - 1, charPositionInLine, line - 1, charPositionInLine + 1));
                    diag.setSource("firefly-lexer");
                    diagnostics.add(diag);
                    syntaxErrors.add(msg);
                }
            });

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            FireflyParser parser = new FireflyParser(tokens);

            // Capture parser errors
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                        int line, int charPositionInLine,
                                        String msg, RecognitionException e) {
                    Diagnostic diag = new Diagnostic();
                    diag.setSeverity(DiagnosticSeverity.Error);
                    diag.setMessage("Parse error: " + msg);
                    diag.setRange(createRange(line - 1, charPositionInLine, line - 1, charPositionInLine + 1));
                    diag.setSource("firefly-parser");
                    diagnostics.add(diag);
                    syntaxErrors.add(msg);
                }
            });

            // Parse the source
            FireflyParser.CompilationUnitContext parseTree = parser.compilationUnit();

            // If there were syntax errors, don't proceed to semantic analysis
            if (!syntaxErrors.isEmpty()) {
                logger.debug("Found {} syntax error(s), skipping semantic analysis", syntaxErrors.size());
                return diagnostics;
            }

            // Phase 2: AST Building
            try {
                AstBuilder astBuilder = new AstBuilder(uri);
                CompilationUnit compilationUnit = (CompilationUnit) astBuilder.visit(parseTree);

                // Phase 3: Semantic Analysis
                TypeResolver typeResolver = new TypeResolver();
                SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(typeResolver);
                List<CompilerDiagnostic> compilerDiagnostics = semanticAnalyzer.analyze(compilationUnit);

                // Convert compiler diagnostics to LSP diagnostics
                for (CompilerDiagnostic compilerDiag : compilerDiagnostics) {
                    Diagnostic lspDiag = convertCompilerDiagnostic(compilerDiag);
                    if (lspDiag != null) {
                        diagnostics.add(lspDiag);
                    }
                }

                logger.debug("Semantic analysis completed: {} diagnostic(s)", compilerDiagnostics.size());

            } catch (Exception e) {
                logger.debug("AST building or semantic analysis failed: {}", e.getMessage());
                // Don't add error here - syntax errors already captured
            }

        } catch (Exception e) {
            logger.error("Compiler validation failed: {}", e.getMessage(), e);
        }

        return diagnostics;
    }

    /**
     * Convert CompilerDiagnostic to LSP Diagnostic.
     */
    private Diagnostic convertCompilerDiagnostic(CompilerDiagnostic compilerDiag) {
        Diagnostic lspDiag = new Diagnostic();

        // Map severity
        switch (compilerDiag.getLevel()) {
            case ERROR:
                lspDiag.setSeverity(DiagnosticSeverity.Error);
                break;
            case WARNING:
                lspDiag.setSeverity(DiagnosticSeverity.Warning);
                break;
            case INFO:
                lspDiag.setSeverity(DiagnosticSeverity.Information);
                break;
            default:
                lspDiag.setSeverity(DiagnosticSeverity.Hint);
        }

        // Set message
        String message = compilerDiag.getMessage();
        if (compilerDiag.getPhase() != null) {
            message = "[" + compilerDiag.getPhase().getDisplayName() + "] " + message;
        }
        lspDiag.setMessage(message);

        // Set location
        if (compilerDiag.getLocation() != null) {
            com.firefly.compiler.ast.SourceLocation loc = compilerDiag.getLocation();
            // Convert 1-based to 0-based
            int line = Math.max(0, loc.line() - 1);
            int column = Math.max(0, loc.column() - 1);
            lspDiag.setRange(createRange(line, column, line, column + 1));
        } else {
            lspDiag.setRange(createRange(0, 0, 0, 0));
        }

        lspDiag.setSource("firefly-compiler");

        return lspDiag;
    }

    /**
     * Perform basic syntax validation without full compilation.
     */
    private List<Diagnostic> performBasicSyntaxValidation(String text) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        String[] lines = text.split("\n");

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum].trim();

            // Check for common syntax errors

            // Unclosed strings
            if (countOccurrences(line, '"') % 2 != 0 && !line.contains("//")) {
                Diagnostic diag = new Diagnostic();
                diag.setSeverity(DiagnosticSeverity.Error);
                diag.setMessage("Unclosed string literal");
                diag.setRange(createRange(lineNum, 0, lineNum, lines[lineNum].length()));
                diag.setSource("firefly-lsp");
                diagnostics.add(diag);
            }

            // Invalid type operations (basic check)
            if (line.matches(".*\".*\"\\s*[+\\-*/]\\s*\\d+.*") ||
                line.matches(".*\\d+\\s*[+\\-*/]\\s*\".*\".*")) {
                Diagnostic diag = new Diagnostic();
                diag.setSeverity(DiagnosticSeverity.Error);
                diag.setMessage("Type mismatch: cannot perform arithmetic between String and numeric types");
                diag.setRange(createRange(lineNum, 0, lineNum, lines[lineNum].length()));
                diag.setSource("firefly-lsp");
                diagnostics.add(diag);
            }

            // Missing semicolons for let/var statements
            if (line.matches("^(let|mut)\\s+\\w+.*") && !line.endsWith(";") && !line.endsWith("{")) {
                Diagnostic diag = new Diagnostic();
                diag.setSeverity(DiagnosticSeverity.Warning);
                diag.setMessage("Missing semicolon");
                diag.setRange(createRange(lineNum, lines[lineNum].length(), lineNum, lines[lineNum].length()));
                diag.setSource("firefly-lsp");
                diagnostics.add(diag);
            }
        }

        // Check for unbalanced braces
        int braceBalance = 0;
        int lastOpenBrace = -1;
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '{') {
                    braceBalance++;
                    lastOpenBrace = lineNum;
                } else if (c == '}') {
                    braceBalance--;
                    if (braceBalance < 0) {
                        Diagnostic diag = new Diagnostic();
                        diag.setSeverity(DiagnosticSeverity.Error);
                        diag.setMessage("Unmatched closing brace");
                        diag.setRange(createRange(lineNum, i, lineNum, i + 1));
                        diag.setSource("firefly-lsp");
                        diagnostics.add(diag);
                        braceBalance = 0; // Reset to avoid cascading errors
                    }
                }
            }
        }

        if (braceBalance > 0 && lastOpenBrace >= 0) {
            Diagnostic diag = new Diagnostic();
            diag.setSeverity(DiagnosticSeverity.Error);
            diag.setMessage("Unclosed brace");
            diag.setRange(createRange(lastOpenBrace, 0, lastOpenBrace, lines[lastOpenBrace].length()));
            diag.setSource("firefly-lsp");
            diagnostics.add(diag);
        }

        return diagnostics;
    }

    /**
     * Count occurrences of a character in a string.
     */
    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }

    /**
     * Create a Range object.
     */
    private Range createRange(int startLine, int startChar, int endLine, int endChar) {
        Range range = new Range();
        range.setStart(new Position(startLine, startChar));
        range.setEnd(new Position(endLine, endChar));
        return range;
    }

    /**
     * Publish diagnostics to the client.
     */
    private void publishDiagnostics(String uri, List<Diagnostic> diagnostics) {
        logger.info("📤 Publishing diagnostics to client:");
        logger.info("   URI: {}", uri);
        logger.info("   Count: {}", diagnostics.size());

        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic d = diagnostics.get(i);
            logger.info("   [{}] Line {}: {} - {}",
                i + 1,
                d.getRange().getStart().getLine() + 1,
                d.getSeverity(),
                d.getMessage());
        }

        PublishDiagnosticsParams params = new PublishDiagnosticsParams();
        params.setUri(uri);
        params.setDiagnostics(diagnostics);

        if (server.getClient() != null) {
            logger.info("   ✓ Client is connected, sending diagnostics...");
            server.getClient().publishDiagnostics(params);
            logger.info("   ✅ Diagnostics sent successfully!");
        } else {
            logger.error("   ❌ ERROR: Client is NULL! Cannot publish diagnostics!");
        }
    }

    /**
     * Add keyword completions.
     */
    private void addKeywordCompletions(List<CompletionItem> items) {
        String[] keywords = {
            "fn", "let", "mut", "if", "else", "match", "for", "while", "in",
            "return", "break", "continue", "class", "interface", "new",
            "package", "import", "pub", "priv", "self"
        };
        
        for (String keyword : keywords) {
            CompletionItem item = new CompletionItem(keyword);
            item.setKind(CompletionItemKind.Keyword);
            items.add(item);
        }
    }

    /**
     * Add runtime class completions.
     */
    private void addRuntimeCompletions(List<CompletionItem> items) {
        String[] runtimeClasses = {
            "PersistentList", "PersistentVector", "PersistentHashMap", "PersistentHashSet",
            "Actor", "Future", "ArrayList", "HashMap", "StringBuilder"
        };
        
        for (String className : runtimeClasses) {
            CompletionItem item = new CompletionItem(className);
            item.setKind(CompletionItemKind.Class);
            item.setDetail("Firefly Runtime");
            items.add(item);
        }
    }

    /**
     * Add type completions.
     */
    private void addTypeCompletions(List<CompletionItem> items) {
        String[] types = {
            "Int", "Long", "Float", "Double", "Boolean", "String", "Void",
            "Array", "List", "Map", "Set"
        };
        
        for (String type : types) {
            CompletionItem item = new CompletionItem(type);
            item.setKind(CompletionItemKind.Class);
            items.add(item);
        }
    }

    /**
     * Get word at position in text.
     */
    private String getWordAtPosition(String text, Position position) {
        String[] lines = text.split("\n");
        if (position.getLine() >= lines.length) {
            return null;
        }
        
        String line = lines[position.getLine()];
        int col = position.getCharacter();
        
        if (col >= line.length()) {
            return null;
        }
        
        // Find word boundaries
        int start = col;
        int end = col;
        
        while (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1))) {
            start--;
        }
        
        while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            end++;
        }
        
        if (start == end) {
            return null;
        }
        
        return line.substring(start, end);
    }

    /**
     * Get hover information for a word.
     */
    private String getHoverInfo(String word) {
        return switch (word) {
            case "PersistentList" -> "**PersistentList<T>**\n\nImmutable linked list with O(1) cons, head, tail operations.";
            case "PersistentVector" -> "**PersistentVector<T>**\n\nImmutable vector with O(1) random access.";
            case "PersistentHashMap" -> "**PersistentHashMap<K, V>**\n\nImmutable hash map with O(1) get/put operations.";
            case "PersistentHashSet" -> "**PersistentHashSet<T>**\n\nImmutable hash set with O(1) contains/add operations.";
            case "fn" -> "**fn** - Function declaration keyword";
            case "let" -> "**let** - Immutable variable declaration";
            case "mut" -> "**mut** - Mutable variable modifier";
            default -> null;
        };
    }

    /**
     * Analyze document symbols and store them.
     */
    private void analyzeDocumentSymbols(String uri, String text) {
        Map<String, List<SymbolAnalyzer.SymbolInfo>> symbols = symbolAnalyzer.analyzeSymbols(uri, text);
        documentSymbols.put(uri, symbols);
        logger.debug("Analyzed {} unique symbols in {}", symbols.size(), uri);
    }

    /**
     * Convert SymbolAnalyzer.SymbolKind to LSP SymbolKind.
     */
    private org.eclipse.lsp4j.SymbolKind convertSymbolKind(SymbolAnalyzer.SymbolKind kind) {
        return switch (kind) {
            case FUNCTION -> org.eclipse.lsp4j.SymbolKind.Function;
            case CLASS -> org.eclipse.lsp4j.SymbolKind.Class;
            case INTERFACE -> org.eclipse.lsp4j.SymbolKind.Interface;
            case STRUCT -> org.eclipse.lsp4j.SymbolKind.Struct;
            case DATA -> org.eclipse.lsp4j.SymbolKind.Enum;
            case TRAIT -> org.eclipse.lsp4j.SymbolKind.Interface;
            case IMPL -> org.eclipse.lsp4j.SymbolKind.Module;
            case VARIABLE -> org.eclipse.lsp4j.SymbolKind.Variable;
            case PARAMETER -> org.eclipse.lsp4j.SymbolKind.Variable;
            case FIELD -> org.eclipse.lsp4j.SymbolKind.Field;
            case METHOD -> org.eclipse.lsp4j.SymbolKind.Method;
        };
    }

    /**
     * Format Firefly code with basic formatting rules.
     */
    private String formatFireflyCode(String code, FormattingOptions options) {
        int tabSize = options.getTabSize();
        boolean insertSpaces = options.isInsertSpaces();
        String indent = insertSpaces ? " ".repeat(tabSize) : "\t";

        StringBuilder formatted = new StringBuilder();
        String[] lines = code.split("\n", -1);
        int indentLevel = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // Decrease indent for closing braces
            if (trimmed.startsWith("}")) {
                indentLevel = Math.max(0, indentLevel - 1);
            }

            // Add indentation
            if (!trimmed.isEmpty()) {
                formatted.append(indent.repeat(indentLevel));
                formatted.append(trimmed);
            }
            formatted.append("\n");

            // Increase indent for opening braces
            if (trimmed.endsWith("{")) {
                indentLevel++;
            }
        }

        // Remove trailing newline if original didn't have one
        String result = formatted.toString();
        if (!code.endsWith("\n") && result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    /**
     * Get function name at position (for signature help).
     */
    private String getFunctionNameAtPosition(String text, Position position) {
        String[] lines = text.split("\n");
        if (position.getLine() >= lines.length) {
            return null;
        }

        String line = lines[position.getLine()];
        int col = position.getCharacter();

        // Look backwards from cursor to find function name before '('
        int parenPos = line.lastIndexOf('(', col);
        if (parenPos == -1) {
            return null;
        }

        // Extract function name before the '('
        int start = parenPos - 1;
        while (start >= 0 && Character.isWhitespace(line.charAt(start))) {
            start--;
        }

        if (start < 0) {
            return null;
        }

        int end = start + 1;
        while (start >= 0 && Character.isJavaIdentifierPart(line.charAt(start))) {
            start--;
        }
        start++;

        if (start >= end) {
            return null;
        }

        return line.substring(start, end);
    }
}

