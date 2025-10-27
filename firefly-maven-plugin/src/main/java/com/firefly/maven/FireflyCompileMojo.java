package com.firefly.maven;

import com.firefly.compiler.FireflyCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Maven Mojo for compiling Firefly source files (.fly) to JVM bytecode.
 * 
 * Usage:
 * <pre>
 * &lt;plugin&gt;
 *   &lt;groupId&gt;com.firefly&lt;/groupId&gt;
 *   &lt;artifactId&gt;firefly-maven-plugin&lt;/artifactId&gt;
 *   &lt;version&gt;0.1.0-SNAPSHOT&lt;/version&gt;
 *   &lt;executions&gt;
 *     &lt;execution&gt;
 *       &lt;goals&gt;
 *         &lt;goal&gt;compile&lt;/goal&gt;
 *       &lt;/goals&gt;
 *     &lt;/execution&gt;
 *   &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
@Mojo(
    name = "compile",
    defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE
)
public class FireflyCompileMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Source directory containing Firefly files.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/firefly", property = "firefly.sourceDirectory")
    private File sourceDirectory;

    /**
     * Output directory for compiled classes.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "firefly.outputDirectory")
    private File outputDirectory;

    /**
     * Skip compilation.
     */
    @Parameter(defaultValue = "false", property = "firefly.skip")
    private boolean skip;
    
    /**
     * Project classpath elements (dependencies).
     */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> classpathElements;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Firefly compilation is skipped.");
            return;
        }

        if (!sourceDirectory.exists()) {
            getLog().info("No Firefly source directory found at: " + sourceDirectory);
            return;
        }

        try {
            // Collect all .fly files
            List<Path> fireflyFiles = new ArrayList<>();
            Path sourcePath = sourceDirectory.toPath();
            
            try (Stream<Path> walk = Files.walk(sourcePath)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".fly"))
                    .forEach(fireflyFiles::add);
            }

            if (fireflyFiles.isEmpty()) {
                getLog().info("No Firefly source files found.");
                return;
            }

            getLog().info("Compiling " + fireflyFiles.size() + " Firefly file(s) from " + sourceDirectory);
            getLog().info("Output directory: " + outputDirectory);

            // Ensure output directory exists
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            // Create classloader with project dependencies
            ClassLoader projectClassLoader = createProjectClassLoader();
            
            // Compile each file with project classloader
            FireflyCompiler compiler = new FireflyCompiler(projectClassLoader);
            Path outputPath = outputDirectory.toPath();
            
            int successCount = 0;
            int failCount = 0;
            
            for (Path sourceFile : fireflyFiles) {
                try {
                    compiler.compile(sourceFile, outputPath, false);
                    successCount++;
                } catch (Exception e) {
                    getLog().error("Failed to compile " + sourceFile + ": " + e.getMessage());
                    failCount++;
                }
            }

            if (failCount > 0) {
                throw new MojoExecutionException(
                    "Firefly compilation failed: " + failCount + " file(s) failed, " + 
                    successCount + " succeeded"
                );
            }

            getLog().info("Successfully compiled " + successCount + " Firefly file(s)");

        } catch (IOException e) {
            throw new MojoExecutionException("Error during Firefly compilation", e);
        }
    }
    
    /**
     * Creates a classloader that includes the project's compile classpath.
     * This allows the compiler to load and inspect classes from project dependencies.
     */
    private ClassLoader createProjectClassLoader() throws MojoExecutionException {
        try {
            List<URL> urls = new ArrayList<>();
            
            // Add project classpath elements (dependencies)
            for (String element : classpathElements) {
                File file = new File(element);
                if (file.exists()) {
                    urls.add(file.toURI().toURL());
                    getLog().debug("Added to classpath: " + element);
                }
            }
            
            getLog().debug("Created project classloader with " + urls.size() + " classpath entries");
            
            // Create URLClassLoader with project classpath + current thread context classloader as parent
            return new URLClassLoader(
                urls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader()
            );
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create project classloader", e);
        }
    }
}
