package com.firefly.compiler;

import com.firefly.compiler.ast.AstBuilder;
import com.firefly.compiler.ast.AstNode;
import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.codegen.BytecodeGenerator;
import com.firefly.compiler.codegen.TypeResolver;
import com.firefly.compiler.semantics.SemanticAnalyzer;
import com.firefly.compiler.diagnostics.CompilerDiagnostic;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main entry point for the Firefly compiler.
 * Compiles Firefly source code (.ff files) to JVM bytecode.
 */
public class FireflyCompiler {
    
    private static final String VERSION = "0.1.0";
    private static final String LOGO_RESOURCE = "/firefly-logo.txt";
    
    private final ClassLoader classLoader;
    
    /**
     * Creates a compiler with the default classloader.
     */
    public FireflyCompiler() {
        this(Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Creates a compiler with a custom classloader.
     * This allows the compiler to access project dependencies during compilation.
     */
    public FireflyCompiler(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
    // ANSI Color codes
    private static class Colors {
        static final String RESET = "\u001B[0m";
        static final String BOLD = "\u001B[1m";
        static final String DIM = "\u001B[2m";
        
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String BLUE = "\u001B[34m";
        static final String CYAN = "\u001B[36m";
        
        static final String BRIGHT_GREEN = "\u001B[92m";
        static final String BRIGHT_YELLOW = "\u001B[93m";
        static final String BRIGHT_BLUE = "\u001B[94m";
        static final String BRIGHT_CYAN = "\u001B[96m";
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: firefly-compiler <source-file.fly|directory> [...] [-o <output-dir>]");
            System.exit(1);
        }

        try {
            FireflyCompiler compiler = new FireflyCompiler();
            
            // Parse arguments
            List<String> inputPaths = new ArrayList<>();
            String outputDir = null;
            
            for (int i = 0; i < args.length; i++) {
                if ("-o".equals(args[i]) && i + 1 < args.length) {
                    outputDir = args[++i];
                } else if (!args[i].startsWith("-")) {
                    inputPaths.add(args[i]);
                }
            }
            
            if (inputPaths.isEmpty()) {
                System.err.println("Error: No source files or directories specified");
                System.exit(1);
            }
            
            // Collect all .fly files from inputs (files or directories)
            List<Path> sourceFiles = new ArrayList<>();
            for (String inputPath : inputPaths) {
                Path path = Paths.get(inputPath);
                if (Files.isDirectory(path)) {
                    // Recursively find all .fly files
                    try (Stream<Path> walk = Files.walk(path)) {
                        walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".fly"))
                            .forEach(sourceFiles::add);
                    }
                } else if (Files.isRegularFile(path) && path.toString().endsWith(".fly")) {
                    sourceFiles.add(path);
                }
            }
            
            if (sourceFiles.isEmpty()) {
                System.err.println("Error: No .fly files found");
                System.exit(1);
            }
            
            Path outputPath = outputDir != null ? Paths.get(outputDir) : null;
            
            // Show logo once
            printLogo();
            
            System.out.println(Colors.BOLD + "Found " + sourceFiles.size() + " file(s) to compile" + Colors.RESET);
            System.out.println();
            
            // Compile each file
            int successCount = 0;
            int failCount = 0;
            for (Path sourceFile : sourceFiles) {
                try {
                    compiler.compile(sourceFile, outputPath, false);
                    successCount++;
                } catch (Exception e) {
                    System.err.println(Colors.BRIGHT_YELLOW + "✗ Failed to compile " + sourceFile + ": " + e.getMessage() + Colors.RESET);
                    failCount++;
                }
            }
            
            System.out.println();
            System.out.println(Colors.BOLD + Colors.BRIGHT_GREEN + "Compilation Summary:" + Colors.RESET);
            System.out.println("  " + Colors.BRIGHT_GREEN + "✓" + Colors.RESET + " Success: " + successCount);
            if (failCount > 0) {
                System.out.println("  " + Colors.BRIGHT_YELLOW + "✗" + Colors.RESET + " Failed: " + failCount);
            }
            
            if (failCount > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Compilation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Compiles a Firefly source file to JVM bytecode.
     */
    public void compile(Path sourceFile) throws IOException {
        compile(sourceFile, null, false);
    }
    
    /**
     * Compiles a Firefly source file to JVM bytecode.
     * @param sourceFile The source file to compile
     * @param outputDir Optional output directory (null = same as source)
     * @param showLogo Whether to show the logo banner
     */
    public void compile(Path sourceFile, Path outputDir, boolean showLogo) throws IOException {
        long startTime = System.currentTimeMillis();
        
        if (showLogo) {
            printLogo();
        }
        
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "┌─" + "─".repeat(60) + "┐" + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "│" + Colors.RESET + 
                          " Compiling: " + Colors.BOLD + sourceFile + Colors.RESET + 
                          " ".repeat(Math.max(0, 60 - sourceFile.toString().length() - 11)) + 
                          Colors.BOLD + Colors.BRIGHT_CYAN + "│" + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "└─" + "─".repeat(60) + "┘" + Colors.RESET);
        System.out.println();
        
        boolean parseSuccess = false;
        boolean astSuccess = false;
        boolean semanticSuccess = false;
        boolean codegenSuccess = false;

        // Read source code
        String source = Files.readString(sourceFile);
        long linesOfCode = source.lines().count();

        // Phase 1: Lexical analysis and parsing
        System.out.println(Colors.BRIGHT_BLUE + "[1/4]" + Colors.RESET + " " + 
                          Colors.BOLD + "Parsing" + Colors.RESET + Colors.DIM + "..." + Colors.RESET);
        long phaseStart = System.currentTimeMillis();
        CharStream input = CharStreams.fromString(source);
        FireflyLexer lexer = new FireflyLexer(input);
        
        // Add error listener
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine,
                                    String msg, RecognitionException e) {
                System.err.println("  ✗ Syntax error at " + line + ":" + charPositionInLine + ": " + msg);
            }
        });
        
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine,
                                    String msg, RecognitionException e) {
                System.err.println("  ✗ Parse error at " + line + ":" + charPositionInLine + ": " + msg);
            }
        });

        // Parse and get parse tree
        ParseTree tree = parser.compilationUnit();

        // Check for syntax errors
        if (parser.getNumberOfSyntaxErrors() > 0) {
            throw new RuntimeException("Compilation failed with syntax errors");
        }

        long phaseDuration = System.currentTimeMillis() - phaseStart;
        System.out.println("  " + Colors.BRIGHT_GREEN + "✓" + Colors.RESET + 
                          " Parse tree generated " + Colors.DIM + "(" + phaseDuration + "ms)" + Colors.RESET);
        parseSuccess = true;

        // Phase 2: Build AST
        System.out.println(Colors.BRIGHT_BLUE + "[2/4]" + Colors.RESET + " " + 
                          Colors.BOLD + "Building AST" + Colors.RESET + Colors.DIM + "..." + Colors.RESET);
        phaseStart = System.currentTimeMillis();
        AstBuilder astBuilder = new AstBuilder(sourceFile.toString());
        AstNode ast = astBuilder.visit(tree);
        phaseDuration = System.currentTimeMillis() - phaseStart;
        System.out.println("  " + Colors.BRIGHT_GREEN + "✓" + Colors.RESET + 
                          " AST constructed " + Colors.DIM + "(" + phaseDuration + "ms)" + Colors.RESET);
        astSuccess = true;

        // Phase 3: Semantic analysis
        System.out.println(Colors.BRIGHT_BLUE + "[3/4]" + Colors.RESET + " " + 
                          Colors.BOLD + "Semantic Analysis" + Colors.RESET + Colors.DIM + "..." + Colors.RESET);
        phaseStart = System.currentTimeMillis();
        TypeResolver sharedTypeResolver = new TypeResolver(classLoader);
        try {
            if (ast instanceof CompilationUnit) {
                // Create TypeResolver with classpath access - shared with codegen
                
                // Process imports into TypeResolver
                CompilationUnit unit = (CompilationUnit) ast;
                for (com.firefly.compiler.ast.ImportDeclaration importDecl : unit.getImports()) {
                    if (importDecl.isWildcard()) {
                        sharedTypeResolver.addWildcardImport(importDecl.getModulePath());
                    } else {
                        for (String item : importDecl.getItems()) {
                            sharedTypeResolver.addImport(importDecl.getModulePath(), item);
                        }
                    }
                }
                
                // Run semantic analyzer with shared TypeResolver
                SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(sharedTypeResolver);
                java.util.List<CompilerDiagnostic> diagnostics = semanticAnalyzer.analyze(unit);
                
                // Report diagnostics
                for (CompilerDiagnostic diagnostic : diagnostics) {
                    if (diagnostic.getLevel() == CompilerDiagnostic.Level.INFO) {
                        // Only show info in verbose mode
                        if (System.getenv("VERBOSE") != null) {
                            System.out.println("  " + diagnostic.format(true));
                        }
                    } else {
                        System.out.println("  " + diagnostic.format(true));
                    }
                }
                
                if (semanticAnalyzer.hasErrors()) {
                    System.out.println();
                    throw new RuntimeException("Semantic analysis failed with errors");
                }
                
                phaseDuration = System.currentTimeMillis() - phaseStart;
                System.out.println("  " + Colors.BRIGHT_GREEN + "✓" + Colors.RESET + 
                                  " Semantic analysis passed " + Colors.DIM + "(" + phaseDuration + "ms)" + Colors.RESET);
                
                long warningCount = diagnostics.stream()
                    .filter(d -> d.getLevel() == CompilerDiagnostic.Level.WARNING)
                    .count();
                if (warningCount > 0) {
                    System.out.println("  " + Colors.YELLOW + "⚠" + Colors.RESET + " " + warningCount + " warning(s)");
                }
                
                semanticSuccess = true;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("  ✗ Semantic analysis failed: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            throw e;
        }

        // Phase 4: Bytecode generation
        System.out.println(Colors.BRIGHT_BLUE + "[4/4]" + Colors.RESET + " " + 
                          Colors.BOLD + "Code Generation" + Colors.RESET + Colors.DIM + "..." + Colors.RESET);
        phaseStart = System.currentTimeMillis();
        try {
            // Generate bytecode
            if (ast instanceof CompilationUnit) {
                // Reuse TypeResolver from semantic analysis
                BytecodeGenerator generator = new BytecodeGenerator(sharedTypeResolver);
                Map<String, byte[]> generatedClasses = generator.generate((CompilationUnit) ast);
                
                // Get package name for base directory
                CompilationUnit unit = (CompilationUnit) ast;
                String packageName = unit.getPackageName().orElse("");
                
                // Use specified output directory or source directory
                Path baseOutputDir = outputDir != null ? outputDir : sourceFile.getParent();
                if (baseOutputDir == null) {
                    baseOutputDir = Paths.get(".");
                }
                
                // Write all generated class files
                int classCount = 0;
                for (Map.Entry<String, byte[]> entry : generatedClasses.entrySet()) {
                    String fullClassName = entry.getKey();
                    byte[] bytecode = entry.getValue();
                    
                    // Determine output path
                    Path outputPath;
                    if (fullClassName.contains("/")) {
                        // Class with package - create directory structure
                        Path classDir = baseOutputDir.resolve(fullClassName.substring(0, fullClassName.lastIndexOf("/")));
                        Files.createDirectories(classDir);
                        String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf("/") + 1);
                        outputPath = classDir.resolve(simpleClassName + ".class");
                    } else {
                        // Class without package
                        outputPath = baseOutputDir.resolve(fullClassName + ".class");
                    }
                    
                    Files.write(outputPath, bytecode);
                    classCount++;
                    
                    if (classCount == 1) {
                        System.out.println("  " + Colors.BRIGHT_GREEN + "✓" + Colors.RESET + " Bytecode generated: " + outputPath);
                    } else {
                        System.out.println("  " + Colors.GREEN + "  ✓" + Colors.RESET + " " + outputPath);
                    }
                }
                
                long phaseDurationCg = System.currentTimeMillis() - phaseStart;
                System.out.println("  " + Colors.DIM + "  (" + classCount + " class" + (classCount != 1 ? "es" : "") + ", " + phaseDurationCg + "ms)" + Colors.RESET);
                codegenSuccess = true;
            } else {
                System.out.println("  " + Colors.YELLOW + "⚠" + Colors.RESET + " Code generation skipped (invalid AST)");
            }
        } catch (Exception e) {
            System.out.println("  " + Colors.YELLOW + "✗" + Colors.RESET + " Code generation failed: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
        }

        System.out.println();
        
        System.out.println();
        long totalDuration = System.currentTimeMillis() - startTime;
        
        // Report compilation status with better design
        if (codegenSuccess) {
            System.out.println(Colors.BOLD + Colors.BRIGHT_GREEN + "┌─" + "─".repeat(58) + "┐" + Colors.RESET);
            System.out.println(Colors.BOLD + Colors.BRIGHT_GREEN + "│" + Colors.RESET + 
                              " " + Colors.BRIGHT_GREEN + "✅ Success!" + Colors.RESET + 
                              " Compilation completed " + 
                              Colors.DIM + "(" + totalDuration + "ms, " + linesOfCode + " lines)" + Colors.RESET +
                              " ".repeat(Math.max(0, 10)) +
                              Colors.BOLD + Colors.BRIGHT_GREEN + "│" + Colors.RESET);
            System.out.println(Colors.BOLD + Colors.BRIGHT_GREEN + "└─" + "─".repeat(58) + "┘" + Colors.RESET);
        } else if (semanticSuccess) {
            System.out.println(Colors.YELLOW + "⚠" + Colors.RESET + " Partial: semantic analysis passed, code generation failed " +
                               Colors.DIM + "(" + totalDuration + "ms)" + Colors.RESET);
        } else if (astSuccess) {
            System.out.println(Colors.YELLOW + "⚠" + Colors.RESET + " Partial: parsing succeeded, semantic analysis failed " +
                               Colors.DIM + "(" + totalDuration + "ms)" + Colors.RESET);
        } else if (parseSuccess) {
            System.out.println(Colors.YELLOW + "⚠" + Colors.RESET + " Partial: parsing succeeded, AST building failed " +
                               Colors.DIM + "(" + totalDuration + "ms)" + Colors.RESET);
        } else {
            System.out.println(Colors.BRIGHT_YELLOW + "❌" + Colors.RESET + " Compilation failed " + 
                               Colors.DIM + "(" + totalDuration + "ms)" + Colors.RESET);
        }
    }
    
    private static void printLogo() {
        String logo = loadAsciiArt();
        if (logo != null) {
            System.out.println(Colors.BRIGHT_YELLOW + logo + Colors.RESET);
            System.out.println(Colors.DIM + "    Version " + VERSION + Colors.RESET);
        } else {
            // Fallback
            System.out.println("🔥 Firefly Compiler v" + VERSION);
        }
        System.out.println();
    }
    
    private static String loadAsciiArt() {
        try (InputStream is = FireflyCompiler.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (is == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return null;
        }
    }
}
