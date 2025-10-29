package com.firefly.maven;

import com.firefly.compiler.FireflyCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "compile-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class CompileMojo extends AbstractMojo {

    @Parameter(property = "firefly.sourceDirectory", defaultValue = "${project.basedir}/src/main/firefly")
    private String sourceDirectory;

    @Parameter(property = "firefly.outputDirectory", defaultValue = "${project.build.outputDirectory}")
    private String outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        Path srcDir = Path.of(sourceDirectory);
        Path outDir = Path.of(outputDirectory);

        if (!Files.exists(srcDir)) {
            getLog().info("[firefly] No sources found at " + srcDir + ", skipping");
            return;
        }

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(srcDir)) {
            sources = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".fly"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan sources", e);
        }

        if (sources.isEmpty()) {
            getLog().info("[firefly] No .fly files found in " + srcDir + ", skipping");
            return;
        }

        getLog().info("[firefly] Compiling " + sources.size() + " .fly source(s) → " + outDir);
        try {
            Files.createDirectories(outDir);
            FireflyCompiler compiler = new FireflyCompiler(Thread.currentThread().getContextClassLoader());
            for (Path src : sources) {
                compiler.compile(src, outDir, false);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Firefly compilation failed", e);
        }
    }
}