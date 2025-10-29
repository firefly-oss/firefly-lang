package com.firefly.cli;

import com.firefly.compiler.FireflyCompiler;
import com.firefly.repl.FireflyRepl;

import com.firefly.compiler.FireflyLexer;
import com.firefly.compiler.FireflyParser;
import com.firefly.compiler.ast.AstBuilder;
import com.firefly.compiler.ast.AstNode;
import com.firefly.compiler.ast.CompilationUnit;
import com.firefly.compiler.ast.UseDeclaration;
import com.firefly.compiler.codegen.TypeResolver;
import com.firefly.compiler.semantics.SemanticAnalyzer;
import com.firefly.compiler.diagnostics.CompilerDiagnostic;
import com.firefly.compiler.ui.ConsoleUI;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Beautiful, modern CLI for the Firefly compiler
 * 
 * Features:
 * - Rich ANSI colors and styling
 * - Interactive progress indicators
 * - Professional output formatting
 * - Multiple commands (compile, run, check, version, help)
 * - Emoji support for better UX
 */
public class FireflyCLI {
    
    // ANSI Color codes for beautiful output
    private static class Colors {
        static final String RESET = "\u001B[0m";
        static final String BOLD = "\u001B[1m";
        static final String DIM = "\u001B[2m";
        
        // Foreground colors
        static final String RED = "\u001B[31m";
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String BLUE = "\u001B[34m";
        static final String CYAN = "\u001B[36m";
        
        // Bright colors
        static final String BRIGHT_RED = "\u001B[91m";
        static final String BRIGHT_GREEN = "\u001B[92m";
        static final String BRIGHT_YELLOW = "\u001B[93m";
        static final String BRIGHT_BLUE = "\u001B[94m";
        static final String BRIGHT_CYAN = "\u001B[96m";
        static final String BRIGHT_WHITE = "\u001B[97m";
    }
    
    private static final String VERSION = "1.0-Alpha";
    private static final String FIREFLY_EMOJI = "🔥";
    private static final String LOGO_RESOURCE = "/firefly-logo.txt";
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printBasicHelp();
            return;
        }

        String command = args[0].toLowerCase();

        try {
            boolean verboseFlag = false;
            boolean noClearFlag = false;
            // Simple global flag support: allow --verbose and --no-clear after the command
            for (int i = 1; i < args.length; i++) {
                if ("--verbose".equals(args[i])) { verboseFlag = true; }
                if ("--no-clear".equals(args[i])) { noClearFlag = true; }
            }

            switch (command) {
                case "compile":
                case "c": {
                    String file = null;
                    for (int i = 1; i < args.length; i++) if (!args[i].startsWith("-")) { file = args[i]; break; }
                    if (file == null) {
                        printError("No input file specified");
                        System.err.println("Usage: fly compile <file.fly> [--verbose]");
                        System.exit(1);
                    }
                    compile(file, verboseFlag);
                    break;
                }

                case "run":
                case "r": {
                    String target = null;
                    for (int i = 1; i < args.length; i++) if (!args[i].startsWith("-")) { target = args[i]; break; }
                    if (target == null) {
                        // Default to current working directory
                        target = Paths.get("").toAbsolutePath().toString();
                    }
                    run(target, verboseFlag, noClearFlag);
                    break;
                }

                case "check": {
                    String file = null;
                    for (int i = 1; i < args.length; i++) if (!args[i].startsWith("-")) { file = args[i]; break; }
                    if (file == null) {
                        printError("No input file specified");
                        System.err.println("Usage: fly check <file.fly> [--verbose]");
                        System.exit(1);
                    }
                    check(file, verboseFlag);
                    break;
                }

                case "repl":
                    repl();
                    break;

                case "version":
                case "v":
                case "-v":
                case "--version":
                    printVersion();
                    break;

                case "help":
                case "h":
                case "-h":
                case "--help":
                    printDetailedHelp();
                    break;

                case "test": {
                    String target = null;
                    for (int i = 1; i < args.length; i++) if (!args[i].startsWith("-")) { target = args[i]; break; }
                    if (target == null) {
                        target = Paths.get("").toAbsolutePath().toString();
                    }
                    testProject(Paths.get(target), verboseFlag);
                    break;
                }
                case "doctor":
                    doctor();
                    break;
                default:
                    printError("Unknown command: " + command);
                    printBasicHelp();
                    System.exit(1);
            }
        } catch (Exception e) {
            printError("Fatal error: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    private static void compile(String filePath, boolean verbose) throws IOException {
        if (verbose) printBanner();
        
        Path sourceFile = Paths.get(filePath);
        if (!Files.exists(sourceFile)) {
            printError("File not found: " + filePath);
            System.exit(1);
        }
        
        if (!filePath.endsWith(".fly")) {
            printError("Invalid file extension. Firefly source files must use .fly extension");
            System.err.println("Expected: " + filePath.replace(filePath.substring(filePath.lastIndexOf('.')), ".fly"));
            System.exit(1);
        }
        
        // Don't print "Compiling" here - it's handled by the compiler
        FireflyCompiler compiler = new FireflyCompiler();
        compiler.compile(sourceFile, null, verbose /*showLogo*/, !verbose /*quiet*/);
    }
    
    private static void run(String inputPath, boolean verbose, boolean noClear) throws Exception {
        Path path = Paths.get(inputPath);
        if (!Files.exists(path)) {
            printError("Path not found: " + inputPath);
            System.exit(1);
        }
        
        // Case 1: Build project directory (Maven or Gradle)
        if (Files.isDirectory(path)) {
            boolean isMaven = Files.exists(path.resolve("pom.xml"));
            boolean isGradle = Files.exists(path.resolve("build.gradle")) || Files.exists(path.resolve("gradlew")) || Files.exists(path.resolve("settings.gradle"));
            if (isMaven) {
                runMavenProject(path, null, verbose, noClear);
                return;
            } else if (isGradle) {
                runGradleProject(path, null, verbose, noClear);
                return;
            }
        }
        
        // Case 2: Single .fly source file
        if (!inputPath.endsWith(".fly")) {
            printError("Invalid input. Provide a .fly file or a Maven project directory");
            System.exit(1);
        }
        
        Path sourceFile = path;
        
        // Compile quietly (show diagnostics only on failure)
        FireflyCompiler compiler = new FireflyCompiler();
        try {
            compiler.compile(sourceFile, null, verbose ? true : false, !verbose);
        } catch (RuntimeException ex) {
            printError("Compilation failed. Showing diagnostics...\n");
            compiler.compile(sourceFile, null, true, false);
            System.exit(1);
            return;
        }
        
        // Clear screen before running program
        if (!noClear && !verbose) clearScreen();
        
        // Extract module and main class
        String source = Files.readString(sourceFile);
        String moduleName = extractModuleName(source); // e.g., com.example.app
        String className = "Main";
        String qualifiedName = moduleName.isEmpty() ? className : moduleName + "." + className;
        
        // Use default compiler output directory (target/classes)
        String classpath = Paths.get("target", "classes").toString();
        
        // Run
        if (!noClear && !verbose) clearScreen();
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-cp",
            classpath,
            qualifiedName
        );
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            printRuntimeErrorBox("Program exited with code: " + exitCode);
            System.exit(exitCode);
        }
    }
    
    private static void runGradleProject(Path projectDir, String mainFqn, boolean verbose, boolean noClear) throws Exception {
        projectDir = projectDir.toAbsolutePath();
        // Build classes
        boolean hasWrapper = Files.exists(projectDir.resolve("gradlew"));
        String gradleCmd = hasWrapper ? projectDir.resolve("gradlew").toAbsolutePath().toString() : "gradle";
        if (hasWrapper) projectDir.resolve("gradlew").toFile().setExecutable(true);
        ProcessBuilder gradleBuild = new ProcessBuilder(gradleCmd, "-q", "classes");
        gradleBuild.directory(projectDir.toFile());
        gradleBuild.redirectErrorStream(true);
        Process gb = gradleBuild.start(); gb.waitFor();

        // Find main class across subprojects if not provided
        String foundFqn = null; Path foundModuleDir = null; Path foundClassesDir = null;
        try (var walk = Files.walk(projectDir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)
                .filter(px -> px.toString().contains("build/classes/java/main"))
                .filter(px -> px.getFileName().toString().equals("Main.class"))::iterator) {
                Path classesDir = p.getParent();
                // moduleDir/build/classes/java/main
                Path moduleDir = classesDir.getParent().getParent().getParent().getParent();
                Path rel = classesDir.relativize(p);
                String fqn = rel.toString().replace('/', '.').replace('\\', '.');
                if (fqn.endsWith(".class")) fqn = fqn.substring(0, fqn.length()-6);
                foundFqn = fqn; foundModuleDir = moduleDir; foundClassesDir = classesDir; break;
            }
        }
        if (mainFqn == null || mainFqn.isEmpty()) mainFqn = foundFqn;
        if (mainFqn == null) {
            printWarning("Could not infer main class; defaulting to Main");
            mainFqn = "Main";
        }

        // Build runtime classpath via init script
        Path init = Files.createTempFile("fly-gradle-", ".init.gradle");
        String initContent = "gradle.projectsEvaluated { if (project.plugins.hasPlugin('java')) { project.tasks.create('printRuntimeClasspath') { doLast { def cp = project.sourceSets.main.runtimeClasspath.files.collect { it.absolutePath }.join(java.io.File.pathSeparator); println '__RUNTIME_CLASSPATH__=' + cp } } } }";
        Files.writeString(init, initContent);
        ProcessBuilder printCp = new ProcessBuilder(gradleCmd, "-q", "-I", init.toAbsolutePath().toString(), "printRuntimeClasspath");
        Path moduleDirToUse = foundModuleDir != null ? foundModuleDir : projectDir;
        printCp.directory(moduleDirToUse.toFile());
        printCp.redirectErrorStream(true);
        Process gpc = printCp.start();
        String cpOut;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(gpc.getInputStream()))) {
            cpOut = r.lines().collect(Collectors.joining("\n"));
        }
        gpc.waitFor();
        String depsCp = null;
        for (String line : cpOut.split("\n")) {
            if (line.startsWith("__RUNTIME_CLASSPATH__=")) { depsCp = line.substring("__RUNTIME_CLASSPATH__=".length()); break; }
        }
        if (depsCp == null) depsCp = "";
        String classesPath = (foundClassesDir != null ? foundClassesDir : projectDir.resolve("build").resolve("classes").resolve("java").resolve("main")).toAbsolutePath().toString();
        String pathSep = System.getProperty("path.separator");
        String fullCp = depsCp.isEmpty() ? classesPath : classesPath + pathSep + depsCp;

        if (!noClear && !verbose) clearScreen();
        ProcessBuilder java = new ProcessBuilder("java", "-cp", fullCp, mainFqn);
        java.directory(moduleDirToUse.toFile());
        java.inheritIO();
        Process jp = java.start();
        int jec = jp.waitFor();
        if (jec != 0) {
            printRuntimeErrorBox("Program exited with code: " + jec);
            System.exit(jec);
        }
    }

    private static void runMavenProject(Path projectDir, String mainFqn, boolean verbose, boolean noClear) throws Exception {
        projectDir = projectDir.toAbsolutePath();
        
        // Build project quietly and capture logs
        ProcessBuilder mvn = new ProcessBuilder("mvn", "-q", "-DskipTests", "clean", "compile");
        mvn.directory(projectDir.toFile());
        mvn.redirectErrorStream(true);
        Process p = mvn.start();
        String buildLog;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            buildLog = r.lines().collect(Collectors.joining("\n"));
        }
        int ec = p.waitFor();
        if (ec != 0) {
            printError("Maven build failed (exit code: " + ec + ")");
            System.out.println(buildLog);
            System.exit(ec);
        }
        
        // If main FQN not provided, try to infer from src/main/firefly/**/Main.fly
        // Try to infer main class from Main.fly; if not found, we will scan classes later
        boolean inferredFromSource = false;
        if (mainFqn == null || mainFqn.isEmpty()) {
            Path maybeMain = findFirstMainFly(projectDir.resolve("src/main/firefly"));
            if (maybeMain != null) {
                String source = Files.readString(maybeMain);
                String module = extractModuleName(source);
                mainFqn = module.isEmpty() ? "Main" : module + "." + "Main";
                inferredFromSource = true;
            }
        }
        
        // Compute classpath and run program directly (minimal output)
        try {
            String classesPath = projectDir.resolve("target").resolve("classes").toAbsolutePath().toString();

            // If we couldn't infer from source, scan target/classes for *Main.class
            if ((mainFqn == null || mainFqn.isEmpty()) && !inferredFromSource) {
                Path classesDir = projectDir.resolve("target").resolve("classes");
                String found = findMainClassInClasses(classesDir);
                if (found != null) {
                    mainFqn = found;
                } else {
                    printWarning("Could not infer main class; defaulting to Main");
                    mainFqn = "Main";
                }
            }
            String cpFile = projectDir.resolve("target").resolve("classpath.txt").toAbsolutePath().toString();
            ProcessBuilder cpBuilder = new ProcessBuilder(
                "mvn", "-q", "dependency:build-classpath",
                "-DincludeScope=runtime",
                "-Dmdep.outputFile=" + cpFile
            );
            cpBuilder.directory(projectDir.toFile());
            Process cpProc = cpBuilder.start();
            cpProc.waitFor();
            String depsCp = Files.exists(Path.of(cpFile)) ? Files.readString(Path.of(cpFile)).trim() : "";
            String pathSep = System.getProperty("path.separator");
            String fullCp = depsCp.isEmpty() ? classesPath : classesPath + pathSep + depsCp;

            // Determine moduleDir if main class belongs to a submodule
            Path moduleDirForMain = projectDir;
            if (!inferredFromSource && (mainFqn == null || mainFqn.isEmpty() || "Main".equals(mainFqn))) {
                String[] found = findMainClassAcrossMavenModules(projectDir);
                if (found != null) {
                    mainFqn = found[0];
                    moduleDirForMain = Paths.get(found[1]);
                    // Recompute classpath for module
                    classesPath = moduleDirForMain.resolve("target").resolve("classes").toAbsolutePath().toString();
                    ProcessBuilder cpModule = new ProcessBuilder(
                        "mvn", "-q", "dependency:build-classpath",
                        "-DincludeScope=runtime",
                        "-Dmdep.outputFile=" + cpFile
                    );
                    cpModule.directory(moduleDirForMain.toFile());
                    Process cpm = cpModule.start();
                    cpm.waitFor();
                    depsCp = Files.exists(Path.of(cpFile)) ? Files.readString(Path.of(cpFile)).trim() : depsCp;
                    fullCp = depsCp.isEmpty() ? classesPath : classesPath + pathSep + depsCp;
                }
            }

            // Clear screen before running
            if (!noClear && !verbose) clearScreen();

            ProcessBuilder java = new ProcessBuilder("java", "-cp", fullCp, mainFqn);
            java.directory(moduleDirForMain.toFile());
            java.inheritIO();
            Process jp = java.start();
            int jec = jp.waitFor();
            if (jec != 0) {
                printRuntimeErrorBox("Program exited with code: " + jec);
                System.exit(jec);
            }
        } catch (Exception e) {
            // As a robust fallback, compute full classpath and run with java directly
            try {
                String classesPath = projectDir.resolve("target").resolve("classes").toAbsolutePath().toString();
                // Build dependency classpath
                String cpFile = projectDir.resolve("target").resolve("classpath.txt").toAbsolutePath().toString();
                ProcessBuilder cpBuilder = new ProcessBuilder(
                    "mvn", "-q", "dependency:build-classpath",
                    "-DincludeScope=runtime",
                    "-Dmdep.outputFile=" + cpFile
                );
                cpBuilder.directory(projectDir.toFile());
                Process cpProc = cpBuilder.start();
                cpProc.waitFor();
                String depsCp = Files.exists(Path.of(cpFile)) ? Files.readString(Path.of(cpFile)).trim() : "";
                String pathSep = System.getProperty("path.separator");
                String fullCp = depsCp.isEmpty() ? classesPath : classesPath + pathSep + depsCp;

                printWarning("exec:java failed; using computed classpath fallback");
                printInfo("Running: " + highlight(mainFqn));
                printDivider();
                ProcessBuilder java = new ProcessBuilder("java", "-cp", fullCp, mainFqn);
                java.directory(projectDir.toFile());
                java.inheritIO();
                Process jp = java.start();
                int jec = jp.waitFor();
                printDivider();
                if (jec == 0) {
                    printSuccess("✨ Program completed successfully (exit code: 0)");
                } else {
                    printError("Program exited with code: " + jec);
                    System.exit(jec);
                }
            } catch (Exception ex) {
                printError("Failed to launch program: " + ex.getMessage());
                System.exit(1);
            }
        }
    }
    
    private static boolean isCommandAvailable(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "-v").redirectErrorStream(true).start();
            p.waitFor();
            return true;
        } catch (Exception e) { return false; }
    }

    private static void testProject(Path projectDir, boolean verbose) throws Exception {
        projectDir = projectDir.toAbsolutePath();

        boolean isMavenProject = Files.exists(projectDir.resolve("pom.xml"));
        boolean isGradleProject = Files.exists(projectDir.resolve("build.gradle")) || Files.exists(projectDir.resolve("gradlew"));

        Process p;
        String buildLog;
        if (isMavenProject) {
            ProcessBuilder mvn = new ProcessBuilder("mvn", "-q", "-DskipTests=false", "test");
            mvn.directory(projectDir.toFile());
            mvn.redirectErrorStream(true);
            p = mvn.start();
        } else if (isGradleProject) {
            boolean hasWrapper = Files.exists(projectDir.resolve("gradlew"));
            String gradleCmd = hasWrapper ? (projectDir.resolve("gradlew").toAbsolutePath().toString()) : "gradle";
            if (hasWrapper) {
                projectDir.resolve("gradlew").toFile().setExecutable(true);
            }
            ProcessBuilder gradle = new ProcessBuilder(gradleCmd, "-q", "test");
            gradle.directory(projectDir.toFile());
            gradle.redirectErrorStream(true);
            p = gradle.start();
        } else {
            printError("No Maven (pom.xml) or Gradle (build.gradle) project detected.\n       Create a pom.xml or build.gradle to use 'fly test'.");
            System.exit(1);
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            buildLog = r.lines().collect(Collectors.joining("\n"));
        }
        int ec = p.waitFor();
        // Summarize results from reports (Maven surefire or Gradle test)
        Path reportsDir = Files.exists(projectDir.resolve("target/surefire-reports"))
            ? projectDir.resolve("target/surefire-reports")
            : projectDir.resolve("build/test-results/test");
        int tests=0, failures=0, errors=0, skipped=0;
        java.util.List<String> failedTests = new java.util.ArrayList<>();
        if (java.nio.file.Files.exists(reportsDir)) {
            try (java.util.stream.Stream<Path> stream = java.nio.file.Files.list(reportsDir)) {
                for (Path f : (Iterable<Path>) stream.filter(pth -> pth.getFileName().toString().endsWith(".xml"))::iterator) {
                    String xml = java.nio.file.Files.readString(f);
                    tests += extractAttr(xml, "tests");
                    failures += extractAttr(xml, "failures");
                    errors += extractAttr(xml, "errors");
                    skipped += extractAttr(xml, "skipped");
                    failedTests.addAll(extractFailures(xml));
                }
            }
        }
        // Print summary
        String summary = String.format("Tests: %d, Failed: %d, Errors: %d, Skipped: %d", tests, failures, errors, skipped);
        if (ec != 0 || failures > 0 || errors > 0) {
            printRuntimeErrorBox(summary);
            for (String ft : failedTests) {
                System.out.println("  • " + ft);
            }
            if (verbose) {
                System.out.println();
                System.out.println(buildLog);
            }
            System.exit(1);
        } else {
            System.out.println(Colors.BRIGHT_GREEN + "✓ " + Colors.RESET + summary);
        }
    }

    private static int extractAttr(String xml, String name) {
        int idx = xml.indexOf(name + "=\"");
        if (idx < 0) return 0;
        int start = idx + name.length() + 2;
        int end = xml.indexOf("\"", start);
        try { return Integer.parseInt(xml.substring(start, end)); } catch (Exception e) { return 0; }
    }

    private static java.util.List<String> extractFailures(String xml) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int pos = 0;
        while (true) {
            int caseIdx = xml.indexOf("<testcase", pos);
            if (caseIdx < 0) break;
            int nameIdx = xml.indexOf("name=\"", caseIdx);
            if (nameIdx < 0) { pos = caseIdx + 1; continue; }
            int nameStart = nameIdx + 6; int nameEnd = xml.indexOf("\"", nameStart);
            String name = xml.substring(nameStart, nameEnd);
            int classIdx = xml.indexOf("classname=\"", caseIdx);
            int classStart = classIdx + 11; int classEnd = xml.indexOf("\"", classStart);
            String cls = classIdx > 0 ? xml.substring(classStart, classEnd) : "";
            int failIdx = xml.indexOf("<failure", caseIdx);
            int errIdx = xml.indexOf("<error", caseIdx);
            int endIdx = xml.indexOf("</testcase>", caseIdx);
            if (endIdx < 0) endIdx = caseIdx + 1;
            if ((failIdx > 0 && failIdx < endIdx) || (errIdx > 0 && errIdx < endIdx)) {
                String kind = (failIdx > 0 && failIdx < endIdx) ? "failure" : "error";
                out.add(cls + "." + name + " (" + kind + ")");
            }
            pos = endIdx + 11;
        }
        return out;
    }

    private static String[] findMainClassAcrossMavenModules(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)
                    .filter(px -> px.toString().contains("/target/classes/") || px.toString().contains("\\target\\classes\\"))
                    .filter(px -> px.getFileName().toString().equals("Main.class"))::iterator) {
                // Compute module dir up to .../target/classes
                Path classesDir = p.getParent();
                // ascend to .../target
                Path targetDir = classesDir.getParent();
                Path moduleDir = targetDir.getParent();
                // FQN
                Path rel = classesDir.relativize(p);
                String fqn = rel.toString().replace('/', '.').replace('\\', '.');
                if (fqn.endsWith(".class")) fqn = fqn.substring(0, fqn.length()-6);
                return new String[]{fqn, moduleDir.toAbsolutePath().toString()};
            }
        }
        return null;
    }

    private static String findMainClassInClasses(Path classesDir) throws IOException {
        if (classesDir == null || !Files.exists(classesDir)) return null;
        final String[] result = new String[1];
        try (var walk = Files.walk(classesDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith("Main.class"))
                .findFirst()
                .ifPresent(p -> {
                    Path rel = classesDir.relativize(p);
                    String fqn = rel.toString().replace('/', '.').replace('\\', '.');
                    if (fqn.endsWith(".class")) fqn = fqn.substring(0, fqn.length()-6);
                    result[0] = fqn;
                });
        }
        return result[0];
    }

    private static Path findFirstMainFly(Path srcDir) throws IOException {
        if (srcDir == null || !Files.exists(srcDir)) return null;
        try (var walk = Files.walk(srcDir)) {
            return walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals("Main.fly"))
                .findFirst().orElse(null);
        }
    }
    
    private static void check(String filePath, boolean verbose) throws IOException {
        printBanner();

        Path sourceFile = Paths.get(filePath);
        if (!Files.exists(sourceFile)) {
            printError("File not found: " + filePath);
            System.exit(1);
        }

        if (!filePath.endsWith(".fly")) {
            printError("Invalid file extension. Firefly source files must use .fly extension");
            System.exit(1);
        }

        printInfo("Checking: " + highlight(filePath));
        System.out.println();

        String source = Files.readString(sourceFile);

        // Phase 1: Lex + Parse
        CharStream input = CharStreams.fromString(source);
        FireflyLexer lexer = new FireflyLexer(input);
        List<String> syntaxErrors = new ArrayList<>();
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                syntaxErrors.add("  ✗ Syntax error at " + line + ":" + charPositionInLine + ": " + msg);
            }
        });

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FireflyParser parser = new FireflyParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                syntaxErrors.add("  ✗ Parse error at " + line + ":" + charPositionInLine + ": " + msg);
            }
        });

        ParseTree tree = parser.compilationUnit();
        if (parser.getNumberOfSyntaxErrors() > 0 || !syntaxErrors.isEmpty()) {
            for (String err : syntaxErrors) System.err.println(err);
            printError("Syntax check failed");
            System.exit(1);
        }

        // Phase 2: AST build
        AstBuilder astBuilder = new AstBuilder(sourceFile.toString());
        AstNode ast = astBuilder.visit(tree);

        // Phase 3: Semantic analysis
        if (ast instanceof CompilationUnit) {
            CompilationUnit unit = (CompilationUnit) ast;
            TypeResolver resolver = new TypeResolver(Thread.currentThread().getContextClassLoader());
            for (UseDeclaration imp : unit.getImports()) {
                if (imp.isWildcard()) {
                    resolver.addWildcardImport(imp.getModulePath());
                } else {
                    for (String item : imp.getItems()) resolver.addImport(imp.getModulePath(), item);
                }
            }
            SemanticAnalyzer analyzer = new SemanticAnalyzer(resolver);
            List<CompilerDiagnostic> diagnostics = analyzer.analyze(unit);
            boolean hasErrors = false;
            for (CompilerDiagnostic d : diagnostics) {
                if (d.getLevel() != CompilerDiagnostic.Level.INFO || verbose) {
                    System.out.println("  " + d.format(true));
                }
                if (d.getLevel() == CompilerDiagnostic.Level.ERROR) hasErrors = true;
            }
            if (hasErrors) {
                printError("Type check failed");
                System.exit(1);
            }
        }

        printSuccess("✓ Syntax check passed");
        printSuccess("✓ Type check passed");
        printSuccess("✓ No errors found");
    }

    private static void clearScreen() {
        System.out.print("\u001B[2J\u001B[H");
        System.out.flush();
    }
    
    private static void doctor() throws IOException, InterruptedException {
        System.out.println();
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "fly doctor" + Colors.RESET);
        System.out.println();
        // Java and version
        int javaMajor = -1;
        try {
            Process pj = new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
            String out = new BufferedReader(new InputStreamReader(pj.getInputStream())).lines().collect(Collectors.joining("\n"));
            pj.waitFor();
            System.out.println("  Java:    " + (out.isEmpty() ? "detected" : out.split("\n")[0]));
            int q1 = out.indexOf('"'); int q2 = out.indexOf('"', q1+1);
            if (q1 >= 0 && q2 > q1) {
                String ver = out.substring(q1+1, q2);
                String majorStr = ver.split("\\.")[0];
                try { javaMajor = Integer.parseInt(majorStr); } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            printError("Java not found in PATH");
        }
        if (javaMajor > 0 && javaMajor < 21) {
            printWarning("Java " + javaMajor + " detected; Flylang recommends Java 21+.");
        }
        // Maven
        try {
            Process pm = new ProcessBuilder("mvn", "-v").redirectErrorStream(true).start();
            String out = new BufferedReader(new InputStreamReader(pm.getInputStream())).lines().collect(Collectors.joining("\n"));
            pm.waitFor();
            System.out.println("  Maven:   " + (out.isEmpty() ? "detected" : out.split("\n")[0]));
        } catch (Exception e) {
            printWarning("Maven not found. Maven features will be unavailable in fly run/test.");
        }
        // Gradle
        try {
            Process pg = new ProcessBuilder("gradle", "-v").redirectErrorStream(true).start();
            String out = new BufferedReader(new InputStreamReader(pg.getInputStream())).lines().collect(Collectors.joining("\n"));
            pg.waitFor();
            System.out.println("  Gradle:  " + (out.isEmpty() ? "detected" : out.split("\n")[0]));
        } catch (Exception e) {
            printWarning("Gradle not found. Gradle projects will require the wrapper (./gradlew)");
        }
        // PATH duplicates for fly
        try {
            Process pw = new ProcessBuilder("bash", "-lc", "which -a fly").redirectErrorStream(true).start();
            String out = new BufferedReader(new InputStreamReader(pw.getInputStream())).lines().collect(Collectors.joining("\n"));
            pw.waitFor();
            if (!out.isEmpty()) {
                String[] lines = out.split("\n");
                if (lines.length > 1) {
                    printWarning("Multiple 'fly' binaries found in PATH; ensure the desired one is first:\n" + out);
                }
            }
        } catch (Exception ignore) {}

        // Permission checks for ~/.local
        Path localBin = Paths.get(System.getProperty("user.home"), ".local", "bin");
        Path localLib = Paths.get(System.getProperty("user.home"), ".local", "lib", "firefly");
        try { Files.createDirectories(localBin); Files.createTempFile(localBin, "perm", ".chk"); } catch (Exception e) { printWarning("~/.local/bin not writable"); }
        try { Files.createDirectories(localLib); Files.createTempFile(localLib, "perm", ".chk"); } catch (Exception e) { printWarning("~/.local/lib/firefly not writable"); }

        // Runtime jar presence in local m2
        Path m2rt = Paths.get(System.getProperty("user.home"), ".m2", "repository", "com", "firefly", "firefly-runtime");
        if (!Files.exists(m2rt)) {
            printWarning("firefly-runtime not found in local Maven repo; first build may download dependencies");
        }

        System.out.println();
        System.out.println(Colors.BRIGHT_GREEN + "✓" + Colors.RESET + " Environment checks completed");
    }

    private static void printRuntimeErrorBox(String message) {
        String clean = message;
        String H = "─";
        System.out.println(Colors.BOLD + Colors.BRIGHT_RED + "┌" + H.repeat(Math.max(20, clean.length()+2)) + "┐" + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BRIGHT_RED + "│ " + Colors.RESET + Colors.BRIGHT_RED + "Runtime Error" + Colors.RESET + ": " + clean + Colors.BOLD + Colors.BRIGHT_RED + " │" + Colors.RESET);
        System.out.println(Colors.BOLD + Colors.BRIGHT_RED + "└" + H.repeat(Math.max(20, clean.length()+2)) + "┘" + Colors.RESET);
    }
    
    private static void repl() throws IOException {
        FireflyRepl repl = new FireflyRepl();
        repl.run();
    }
    
    private static String extractModuleName(String source) {
        String[] lines = source.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("module ")) {
                String raw = line.substring(7).trim();
                // module paths are like a::b::c -> convert to a.b.c
                return raw.replace("::", ".");
            }
        }
        return "";
    }
    
    // ==================== Beautiful Output Methods ====================
    
    private static void printBanner() {
        String logo = loadAsciiArt();
        if (logo != null) {
            System.out.println(Colors.BRIGHT_YELLOW + logo + Colors.RESET);
            System.out.println(Colors.DIM + "  Version " + VERSION + Colors.RESET);
            System.out.println();
            System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN +
                "  flylang" + Colors.RESET + Colors.DIM + " - Immutable, concurrent, and fast on the JVM" + Colors.RESET);
        } else {
            // Fallback if logo file not found
            System.out.println();
            System.out.println(Colors.BOLD + Colors.BRIGHT_YELLOW +
                "╔═══════════════════════════════════════════════════╗" + Colors.RESET);
            System.out.println(Colors.BOLD + Colors.BRIGHT_YELLOW +
                "║  " + FIREFLY_EMOJI + "  " +
                Colors.BRIGHT_CYAN + "flylang" + Colors.RESET + Colors.DIM +
                " - v" + VERSION + Colors.BRIGHT_YELLOW + "  ║" + Colors.RESET);
            System.out.println(Colors.BOLD + Colors.BRIGHT_YELLOW +
                "╚═══════════════════════════════════════════════════╝" + Colors.RESET);
            System.out.println(Colors.DIM + "  Immutable, concurrent, and fast on the JVM" + Colors.RESET);
        }
        System.out.println();
    }
    
    private static void printVersion() {
        System.out.println();
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN +
            FIREFLY_EMOJI + " flylang " + Colors.RESET +
            Colors.BRIGHT_WHITE + "v" + VERSION + Colors.RESET);
        System.out.println(Colors.DIM + "Immutable, concurrent, and fast on the JVM" + Colors.RESET);
        System.out.println();
        System.out.println("Build:       " + Colors.GREEN + "release" + Colors.RESET);
        System.out.println("Target:      " + Colors.GREEN + "JVM 21+" + Colors.RESET);
        System.out.println("License:     " + Colors.GREEN + "Apache 2.0" + Colors.RESET);
        System.out.println();
        System.out.println(Colors.DIM + "https://fireflyframework.com/flylang" + Colors.RESET);
        System.out.println();
    }
    
    private static void printBasicHelp() {
        printBanner();

        String HORIZONTAL = "─";

        // Top border
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "  " + HORIZONTAL.repeat(76) + Colors.RESET);

        // Usage section
        System.out.println();
        System.out.println("  " + Colors.BOLD + "USAGE" + Colors.RESET);
        System.out.println("    fly " + Colors.DIM + "<command> [options]" + Colors.RESET);
        System.out.println();

        // Commands section
        System.out.println("  " + Colors.BOLD + "COMMANDS" + Colors.RESET);
        System.out.println();
        printSimpleCommand("compile, c", "Compile Firefly source files to JVM bytecode");
        printSimpleCommand("run, r", "Compile and execute a Firefly program");
        printSimpleCommand("test", "Run project tests and summarize results");
        printSimpleCommand("repl", "Start interactive REPL (Read-Eval-Print Loop)");
        printSimpleCommand("check", "Validate syntax and types without compilation");
        printSimpleCommand("version, v", "Display version and build information");
        printSimpleCommand("doctor", "Validate Java/Maven/Gradle and environment (deep checks)");
        printSimpleCommand("help, h", "Show detailed help and usage examples");
        System.out.println();

        // Footer
        System.out.println("  " + Colors.DIM + "Run 'fly help' for detailed information and examples" + Colors.RESET);
        System.out.println();

        // Bottom border
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "  " + HORIZONTAL.repeat(76) + Colors.RESET);
        System.out.println();
    }

    private static void printSimpleCommand(String command, String description) {
        System.out.println("    " + Colors.BRIGHT_GREEN + String.format("%-15s", command) + Colors.RESET + description);
    }

    private static void printDetailedHelp() {
        String HORIZONTAL = "─";

        System.out.println();

        // Top border
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "  " + HORIZONTAL.repeat(76) + Colors.RESET);
        System.out.println();

        // Header
        System.out.println("  " + Colors.BOLD + "flylang CLI" + Colors.RESET + Colors.DIM + " v" + VERSION + Colors.RESET);
        System.out.println();

        // Usage
        System.out.println("  " + Colors.BOLD + "USAGE" + Colors.RESET);
        System.out.println("    fly " + Colors.DIM + "<command> [options]" + Colors.RESET);
        System.out.println();

        // Commands
        System.out.println("  " + Colors.BOLD + "COMMANDS" + Colors.RESET);
        System.out.println();
        printDetailedCommand("compile, c", "Compile Firefly source files to JVM bytecode", "fly compile hello.fly");
        printDetailedCommand("run, r", "Compile and execute a Firefly program", "fly run hello.fly");
        printDetailedCommand("repl", "Start interactive REPL (Read-Eval-Print Loop)", "fly repl");
        printDetailedCommand("check", "Validate syntax and types without compilation", "fly check hello.fly");
        printDetailedCommand("version, v", "Display version and build information", "fly version");
        printDetailedCommand("help, h", "Show this detailed help message", "fly help");

        // Examples
        System.out.println("  " + Colors.BOLD + "EXAMPLES" + Colors.RESET);
        System.out.println();
        printExample("Compile a Firefly source file", "fly compile examples/hello.fly");
        printExample("Run a Firefly program directly", "fly run examples/loops.fly");
        printExample("Start interactive REPL", "fly repl");
        printExample("Check code without compiling", "fly check src/main.fly");
        printExample("Display compiler version", "fly version");
        System.out.println();

        // Footer
        System.out.println("  " + Colors.DIM + "https://fireflyframework.com/flylang" + Colors.RESET);
        System.out.println();

        // Bottom border
        System.out.println(Colors.BOLD + Colors.BRIGHT_CYAN + "  " + HORIZONTAL.repeat(76) + Colors.RESET);
        System.out.println();
    }

    private static void printDetailedCommand(String command, String description, String example) {
        System.out.println("    " + Colors.BRIGHT_GREEN + String.format("%-15s", command) + Colors.RESET + description);
        System.out.println("      " + Colors.DIM + "Example: " + example + Colors.RESET);
        System.out.println();
    }

    private static void printExample(String description, String command) {
        System.out.println("    " + Colors.CYAN + description + Colors.RESET);
        System.out.println("      $ " + command);
        System.out.println();
    }
    
    private static void printSuccess(String message) {
        System.out.println(Colors.BRIGHT_GREEN + "✓ " + Colors.RESET + 
                          Colors.GREEN + message + Colors.RESET);
    }
    
    private static void printError(String message) {
        System.err.println(Colors.BRIGHT_RED + "✗ " + Colors.RESET + 
                          Colors.RED + "Error: " + Colors.RESET + message);
    }
    
    private static void printWarning(String message) {
        System.out.println(Colors.BRIGHT_YELLOW + "⚠ " + Colors.RESET + 
                          Colors.YELLOW + "Warning: " + Colors.RESET + message);
    }
    
    private static void printInfo(String message) {
        System.out.println(Colors.BRIGHT_BLUE + "ℹ " + Colors.RESET + 
                          Colors.BLUE + message + Colors.RESET);
    }
    
    private static String highlight(String text) {
        return Colors.BOLD + Colors.BRIGHT_WHITE + text + Colors.RESET;
    }
    
    private static void printDivider() {
        System.out.println(Colors.DIM + "─".repeat(60) + Colors.RESET);
    }
    
    private static String loadAsciiArt() {
        try (InputStream is = FireflyCLI.class.getResourceAsStream(LOGO_RESOURCE)) {
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
