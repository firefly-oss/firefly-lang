package com.firefly.compiler;

import com.firefly.compiler.ast.AstBuilder;
import com.firefly.compiler.ast.AstNode;
import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.codegen.BytecodeGenerator;
import com.firefly.compiler.codegen.TypeResolver;
import com.firefly.compiler.semantics.SemanticAnalyzer;
import com.firefly.compiler.diagnostics.CompilerDiagnostic;
import com.firefly.compiler.ui.ConsoleUI;
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
 *
 * <p>The Firefly compiler is a professional, multi-phase compiler that transforms
 * Firefly source code (.fly files) into optimized JVM bytecode (.class files).</p>
 *
 * <h2>Compilation Pipeline</h2>
 * <ol>
 *   <li><b>Lexical Analysis:</b> ANTLR lexer tokenizes source code</li>
 *   <li><b>Parsing:</b> ANTLR parser builds parse tree from tokens</li>
 *   <li><b>AST Construction:</b> {@link AstBuilder} converts parse tree to AST</li>
 *   <li><b>Semantic Analysis:</b> {@link com.firefly.compiler.semantics.SemanticAnalyzer} validates semantics</li>
 *   <li><b>Code Generation:</b> {@link BytecodeGenerator} emits JVM bytecode using ASM</li>
 * </ol>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Automatic Firefly standard library prelude import</li>
 *   <li>Java interoperability with full classpath access</li>
 *   <li>Spring Boot annotation support</li>
 *   <li>Professional error reporting with source locations</li>
 *   <li>Optimized bytecode generation with frame computation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Compile a single file
 * FireflyCompiler compiler = new FireflyCompiler();
 * compiler.compile(Paths.get("Main.fly"));
 *
 * // Compile with custom output directory
 * compiler.compile(Paths.get("Main.fly"), Paths.get("target/classes"), false);
 * }</pre>
 *
 * @see com.firefly.compiler.ast.AstBuilder
 * @see com.firefly.compiler.semantics.SemanticAnalyzer
 * @see com.firefly.compiler.codegen.BytecodeGenerator
 * @version 1.0-Alpha
 */
public class FireflyCompiler {

    /** Current compiler version */
    private static final String VERSION = "1.0-Alpha";

    /** Resource path for the Firefly logo */
    private static final String LOGO_RESOURCE = "/firefly-logo.txt";

    /** Classloader for accessing project dependencies during compilation */
    private final ClassLoader classLoader;

    /**
     * Creates a compiler with the default classloader.
     *
     * <p>Uses the current thread's context classloader, which typically includes
     * the application classpath and all Maven dependencies.</p>
     */
    public FireflyCompiler() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a compiler with a custom classloader.
     *
     * <p>This allows the compiler to access project dependencies during compilation,
     * enabling Java interoperability and type resolution for imported classes.</p>
     *
     * @param classLoader The classloader to use for class resolution, or null for default
     */
    public FireflyCompiler(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * ANSI color codes for terminal output formatting.
     *
     * <p>Provides professional, colorized compiler output for better readability
     * and user experience.</p>
     */
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

    /**
     * Command-line entry point for the Firefly compiler.
     *
     * <p>Compiles one or more Firefly source files or directories to JVM bytecode.</p>
     *
     * <h3>Usage</h3>
     * <pre>
     * firefly-compiler &lt;source-file.fly|directory&gt; [...] [-o &lt;output-dir&gt;]
     * </pre>
     *
     * <h3>Arguments</h3>
     * <ul>
     *   <li><b>source-file.fly:</b> Single Firefly source file to compile</li>
     *   <li><b>directory:</b> Directory to recursively search for .fly files</li>
     *   <li><b>-o output-dir:</b> Optional output directory for compiled .class files</li>
     * </ul>
     *
     * <h3>Examples</h3>
     * <pre>
     * # Compile a single file
     * firefly-compiler Main.fly
     *
     * # Compile all files in a directory
     * firefly-compiler src/main/firefly
     *
     * # Compile with custom output directory
     * firefly-compiler Main.fly -o target/classes
     *
     * # Compile multiple files and directories
     * firefly-compiler Main.fly src/lib -o build
     * </pre>
     *
     * @param args Command-line arguments
     */
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
     *
     * <p>Convenience method that compiles to the same directory as the source file
     * without showing the logo banner.</p>
     *
     * @param sourceFile The Firefly source file to compile (.fly extension)
     * @throws IOException If file I/O errors occur during compilation
     */
    public void compile(Path sourceFile) throws IOException {
        compile(sourceFile, null, false);
    }

    /**
     * Compiles a Firefly source file to JVM bytecode with full control.
     *
     * <p>Executes the complete compilation pipeline:</p>
     * <ol>
     *   <li><b>Lexical Analysis:</b> Tokenizes source code using ANTLR lexer</li>
     *   <li><b>Parsing:</b> Builds parse tree using ANTLR parser</li>
     *   <li><b>AST Construction:</b> Converts parse tree to abstract syntax tree</li>
     *   <li><b>Semantic Analysis:</b> Validates imports, symbols, and types</li>
     *   <li><b>Code Generation:</b> Generates JVM bytecode using ASM library</li>
     * </ol>
     *
     * <p>The compiler automatically imports the Firefly standard library prelude,
     * making core types like Option and Result available without explicit imports.</p>
     *
     * <p>Generated .class files are written to the output directory, preserving
     * the package structure. If no output directory is specified, files are written
     * to the same directory as the source file.</p>
     *
     * @param sourceFile The Firefly source file to compile (.fly extension)
     * @param outputDir Optional output directory for .class files (null = same as source)
     * @param showLogo Whether to display the Firefly logo banner
     * @throws IOException If file I/O errors occur during compilation
     * @throws RuntimeException If compilation fails due to syntax or semantic errors
     */
    public void compile(Path sourceFile, Path outputDir, boolean showLogo) throws IOException {
        long startTime = System.currentTimeMillis();
        
        if (showLogo) {
            printLogo();
        }

        ConsoleUI.printFileHeader(sourceFile.toString());
        
        boolean parseSuccess = false;
        boolean astSuccess = false;
        boolean semanticSuccess = false;
        boolean codegenSuccess = false;

        // Read source code
        String source = Files.readString(sourceFile);
        long linesOfCode = source.lines().count();

        // Phase 1: Lexical analysis and parsing
        ConsoleUI.printPhase(1, 4, "Parsing");
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
        ConsoleUI.printSuccess("Parse tree generated " + ConsoleUI.formatDuration(phaseDuration));
        parseSuccess = true;

        // Phase 2: Build AST
        ConsoleUI.printPhase(2, 4, "Building AST");
        phaseStart = System.currentTimeMillis();
        AstBuilder astBuilder = new AstBuilder(sourceFile.toString());
        AstNode ast = astBuilder.visit(tree);
        phaseDuration = System.currentTimeMillis() - phaseStart;
        ConsoleUI.printSuccess("AST constructed " + ConsoleUI.formatDuration(phaseDuration));
        astSuccess = true;

        // Phase 3: Semantic analysis
        ConsoleUI.printPhase(3, 4, "Semantic Analysis");
        phaseStart = System.currentTimeMillis();
        TypeResolver sharedTypeResolver = new TypeResolver(classLoader);
        try {
            if (ast instanceof CompilationUnit) {
                // Create TypeResolver with classpath access - shared with codegen
                
                // Process imports into TypeResolver
                CompilationUnit unit = (CompilationUnit) ast;
                for (com.firefly.compiler.ast.UseDeclaration importDecl : unit.getImports()) {
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
                ConsoleUI.printSuccess("Semantic analysis passed " + ConsoleUI.formatDuration(phaseDuration));

                long warningCount = diagnostics.stream()
                    .filter(d -> d.getLevel() == CompilerDiagnostic.Level.WARNING)
                    .count();
                if (warningCount > 0) {
                    ConsoleUI.printWarning(warningCount + " warning(s)");
                }

                semanticSuccess = true;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            ConsoleUI.printError("Semantic analysis failed: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            throw e;
        }

        // Phase 4: Bytecode generation
        ConsoleUI.printPhase(4, 4, "Code Generation");
        phaseStart = System.currentTimeMillis();
        try {
            // Generate bytecode
            if (ast instanceof CompilationUnit) {
                // Reuse TypeResolver from semantic analysis
                BytecodeGenerator generator = new BytecodeGenerator(sharedTypeResolver);
                Map<String, byte[]> generatedClasses = generator.generate((CompilationUnit) ast);
                
                // Get module name for base directory (module is MANDATORY)
                CompilationUnit unit = (CompilationUnit) ast;
                String moduleName = unit.getModuleName();

                // Use specified output directory or default to target/classes
                Path baseOutputDir;
                if (outputDir != null) {
                    baseOutputDir = outputDir;
                } else {
                    // Default to target/classes (like Maven/Java)
                    baseOutputDir = Paths.get("target", "classes");
                }

                // Create output directory if it doesn't exist
                Files.createDirectories(baseOutputDir);
                
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
                        ConsoleUI.printSuccess("Bytecode generated: " + ConsoleUI.formatPath(outputPath.toString()));
                    } else {
                        ConsoleUI.printDetail("✓ " + outputPath);
                    }
                }

                long phaseDurationCg = System.currentTimeMillis() - phaseStart;
                ConsoleUI.printDetail(ConsoleUI.formatCount(classCount, "class", "classes") + ", " + phaseDurationCg + "ms");
                codegenSuccess = true;
            } else {
                ConsoleUI.printWarning("Code generation skipped (invalid AST)");
            }
        } catch (Exception e) {
            ConsoleUI.printError("Code generation failed: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
        }

        System.out.println();
        long totalDuration = System.currentTimeMillis() - startTime;

        // Report compilation status with better design
        if (codegenSuccess) {
            String message = "Compilation completed (" + totalDuration + "ms, " +
                           ConsoleUI.formatCount((int)linesOfCode, "line", "lines") + ")";
            ConsoleUI.printSuccessBox(message);
        } else if (semanticSuccess) {
            ConsoleUI.printWarningBox("Semantic analysis passed, code generation failed (" +
                                     totalDuration + "ms)");
        } else if (astSuccess) {
            ConsoleUI.printWarningBox("Parsing succeeded, semantic analysis failed (" +
                                     totalDuration + "ms)");
        } else if (parseSuccess) {
            ConsoleUI.printWarningBox("Parsing succeeded, AST building failed (" +
                                     totalDuration + "ms)");
        } else {
            ConsoleUI.printErrorBox("Compilation failed (" + totalDuration + "ms)");
        }
    }
    
    private static void printLogo() {
        String logo = loadAsciiArt();
        if (logo != null) {
            System.out.println(Colors.BRIGHT_YELLOW + logo + Colors.RESET);
            System.out.println(Colors.DIM + "  Version " + VERSION + Colors.RESET);
            System.out.println();
            System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN +
                "  Flylang" + Colors.RESET + Colors.DIM + " - Firefly Programming Language" + Colors.RESET);
            System.out.println(Colors.DIM +
                "  Modern Concurrent Language for the JVM" + Colors.RESET);
        } else {
            // Fallback
            System.out.println("🔥 Flylang Compiler v" + VERSION);
            System.out.println("Firefly Programming Language");
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
