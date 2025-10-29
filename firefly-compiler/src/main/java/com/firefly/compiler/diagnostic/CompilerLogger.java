package com.firefly.compiler.diagnostic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Professional logging system for Firefly compiler.
 * Provides structured logging with levels: DEBUG, INFO, WARN, ERROR.
 * 
 * Features:
 * - Timestamp logging
 * - Categorized messages by component
 * - Level-based filtering
 * - Performance tracking
 */
public class CompilerLogger {
    
    public enum LogLevel {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO"),
        WARN(2, "WARN"),
        ERROR(3, "ERROR");
        
        private final int level;
        private final String label;
        
        LogLevel(int level, String label) {
            this.level = level;
            this.label = label;
        }
        
        public int getLevel() { return level; }
        public String getLabel() { return label; }
    }
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    private final String component;
    private LogLevel minLevel;
    private boolean includeTimestamp;
    private boolean colorized;
    
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    
    public CompilerLogger(String component) {
        this(component, LogLevel.INFO, true, true);
    }
    
    public CompilerLogger(String component, LogLevel minLevel, 
                         boolean includeTimestamp, boolean colorized) {
        this.component = component;
        this.minLevel = minLevel;
        this.includeTimestamp = includeTimestamp;
        this.colorized = colorized;
    }
    
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }
    
    public void debug(String message, Object... args) {
        log(LogLevel.DEBUG, String.format(message, args));
    }
    
    public void info(String message) {
        log(LogLevel.INFO, message);
    }
    
    public void info(String message, Object... args) {
        log(LogLevel.INFO, String.format(message, args));
    }
    
    public void warn(String message) {
        log(LogLevel.WARN, message);
    }
    
    public void warn(String message, Object... args) {
        log(LogLevel.WARN, String.format(message, args));
    }
    
    public void warn(String message, Throwable throwable) {
        logWithException(LogLevel.WARN, message, throwable);
    }
    
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }
    
    public void error(String message, Object... args) {
        log(LogLevel.ERROR, String.format(message, args));
    }
    
    public void error(String message, Throwable throwable) {
        logWithException(LogLevel.ERROR, message, throwable);
    }
    
    private void log(LogLevel level, String message) {
        if (level.getLevel() < minLevel.getLevel()) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Add timestamp if enabled
        if (includeTimestamp) {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            sb.append(formatLevel(level)).append("[").append(timestamp).append("] ");
        } else {
            sb.append(formatLevel(level)).append("[").append(level.getLabel()).append("] ");
        }
        
        // Add component
        sb.append(String.format("[%-25s] ", component));
        
        // Add message
        sb.append(message);
        
        // Output
        if (level == LogLevel.ERROR) {
            System.err.println(sb.toString());
        } else {
            System.out.println(sb.toString());
        }
    }
    
    private void logWithException(LogLevel level, String message, Throwable throwable) {
        log(level, message);
        if (level == LogLevel.ERROR) {
            throwable.printStackTrace(System.err);
        } else {
            throwable.printStackTrace(System.out);
        }
    }
    
    private String formatLevel(LogLevel level) {
        if (!colorized) {
            return String.format("%-6s", level.getLabel());
        }
        
        String color = switch(level) {
            case DEBUG -> ANSI_CYAN;
            case INFO -> ANSI_WHITE;
            case WARN -> ANSI_YELLOW;
            case ERROR -> ANSI_RED;
        };
        
        return color + String.format("%-6s", level.getLabel()) + ANSI_RESET;
    }
    
    public void setMinLevel(LogLevel level) {
        this.minLevel = level;
    }
    
    public LogLevel getMinLevel() {
        return minLevel;
    }
    
    /**
     * Performance tracking helper
     */
    public static class PerformanceTimer {
        private final CompilerLogger logger;
        private final String operation;
        private final long startTime;
        
        public PerformanceTimer(CompilerLogger logger, String operation) {
            this.logger = logger;
            this.operation = operation;
            this.startTime = System.currentTimeMillis();
        }
        
        public void complete() {
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("%s completed in %dms", operation, duration);
        }
        
        public long getDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }
}
