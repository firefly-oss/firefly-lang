package com.firefly.compiler.config;

import com.firefly.compiler.diagnostic.CompilerLogger;

/**
 * Centralized compiler configuration.
 * Controls compilation behavior, optimization levels, and debug settings.
 */
public class CompilerConfig {
    
    public enum OptimizationLevel {
        NONE(0, "No optimizations"),
        BASIC(1, "Basic optimizations"),
        AGGRESSIVE(2, "Aggressive optimizations"),
        FULL(3, "Full optimization suite");
        
        private final int level;
        private final String description;
        
        OptimizationLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
    
    private OptimizationLevel optimizationLevel = OptimizationLevel.BASIC;
    private CompilerLogger.LogLevel logLevel = CompilerLogger.LogLevel.INFO;
    private boolean enableDebugInfo = false;
    private boolean enableLineNumbers = true;
    private boolean enableLocalVarNames = true;
    private boolean enableTypeAnnotations = false;
    private boolean enableProfiler = false;
    private boolean failOnWarnings = false;
    private int maxErrorCount = 100;
    private int threadPoolSize = Runtime.getRuntime().availableProcessors();
    
    // Performance tuning
    private boolean enableCaching = true;
    private int cacheSize = 1000;
    private boolean enableInlining = true;
    private boolean enableConstantFolding = true;
    private boolean enableDeadCodeElimination = true;
    
    // Output options
    private String targetVersion = "1.8"; // JVM target version
    private boolean generateSourceDebugExtension = true;
    private String outputDir = "./target/classes";
    
    public CompilerConfig() {
        // Default configuration
    }
    
    // Getters and setters
    public OptimizationLevel getOptimizationLevel() {
        return optimizationLevel;
    }
    
    public void setOptimizationLevel(OptimizationLevel level) {
        this.optimizationLevel = level;
    }
    
    public CompilerLogger.LogLevel getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(CompilerLogger.LogLevel level) {
        this.logLevel = level;
    }
    
    public boolean isDebugInfoEnabled() {
        return enableDebugInfo;
    }
    
    public void setDebugInfoEnabled(boolean enabled) {
        this.enableDebugInfo = enabled;
    }
    
    public boolean isLineNumbersEnabled() {
        return enableLineNumbers;
    }
    
    public void setLineNumbersEnabled(boolean enabled) {
        this.enableLineNumbers = enabled;
    }
    
    public boolean isLocalVarNamesEnabled() {
        return enableLocalVarNames;
    }
    
    public void setLocalVarNamesEnabled(boolean enabled) {
        this.enableLocalVarNames = enabled;
    }
    
    public boolean isTypeAnnotationsEnabled() {
        return enableTypeAnnotations;
    }
    
    public void setTypeAnnotationsEnabled(boolean enabled) {
        this.enableTypeAnnotations = enabled;
    }
    
    public boolean isProfilerEnabled() {
        return enableProfiler;
    }
    
    public void setProfilerEnabled(boolean enabled) {
        this.enableProfiler = enabled;
    }
    
    public boolean isFailOnWarnings() {
        return failOnWarnings;
    }
    
    public void setFailOnWarnings(boolean fail) {
        this.failOnWarnings = fail;
    }
    
    public int getMaxErrorCount() {
        return maxErrorCount;
    }
    
    public void setMaxErrorCount(int count) {
        this.maxErrorCount = count;
    }
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public void setThreadPoolSize(int size) {
        this.threadPoolSize = Math.max(1, size);
    }
    
    public boolean isCachingEnabled() {
        return enableCaching;
    }
    
    public void setCachingEnabled(boolean enabled) {
        this.enableCaching = enabled;
    }
    
    public int getCacheSize() {
        return cacheSize;
    }
    
    public void setCacheSize(int size) {
        this.cacheSize = Math.max(10, size);
    }
    
    public boolean isInliningEnabled() {
        return enableInlining && optimizationLevel.getLevel() >= OptimizationLevel.BASIC.getLevel();
    }
    
    public void setInliningEnabled(boolean enabled) {
        this.enableInlining = enabled;
    }
    
    public boolean isConstantFoldingEnabled() {
        return enableConstantFolding && optimizationLevel.getLevel() >= OptimizationLevel.BASIC.getLevel();
    }
    
    public void setConstantFoldingEnabled(boolean enabled) {
        this.enableConstantFolding = enabled;
    }
    
    public boolean isDeadCodeEliminationEnabled() {
        return enableDeadCodeElimination && optimizationLevel.getLevel() >= OptimizationLevel.BASIC.getLevel();
    }
    
    public void setDeadCodeEliminationEnabled(boolean enabled) {
        this.enableDeadCodeElimination = enabled;
    }
    
    public String getTargetVersion() {
        return targetVersion;
    }
    
    public void setTargetVersion(String version) {
        this.targetVersion = version;
    }
    
    public boolean isSourceDebugExtensionEnabled() {
        return generateSourceDebugExtension && enableDebugInfo;
    }
    
    public void setSourceDebugExtensionEnabled(boolean enabled) {
        this.generateSourceDebugExtension = enabled;
    }
    
    public String getOutputDir() {
        return outputDir;
    }
    
    public void setOutputDir(String dir) {
        this.outputDir = dir;
    }
    
    /**
     * Print configuration summary
     */
    public void printSummary() {
        System.out.println("=== Firefly Compiler Configuration ===");
        System.out.println("Optimization Level: " + optimizationLevel.getDescription());
        System.out.println("Log Level: " + logLevel.getLabel());
        System.out.println("Debug Info: " + (enableDebugInfo ? "Enabled" : "Disabled"));
        System.out.println("Line Numbers: " + (enableLineNumbers ? "Enabled" : "Disabled"));
        System.out.println("Caching: " + (enableCaching ? "Enabled (" + cacheSize + " items)" : "Disabled"));
        System.out.println("Inlining: " + (isInliningEnabled() ? "Enabled" : "Disabled"));
        System.out.println("Target JVM: " + targetVersion);
        System.out.println("Output Directory: " + outputDir);
        System.out.println("========================================");
    }
}
