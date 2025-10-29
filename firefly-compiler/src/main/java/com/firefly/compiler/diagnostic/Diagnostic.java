package com.firefly.compiler.diagnostic;

import com.firefly.compiler.ast.SourceLocation;

/**
 * Represents a diagnostic message (error, warning, info) from the compiler.
 */
public class Diagnostic {
    
    public enum Severity {
        ERROR,
        WARNING,
        INFO,
        HINT
    }
    
    private final Severity severity;
    private final String code;
    private final String message;
    private final SourceLocation location;
    private final String suggestion;
    
    public Diagnostic(Severity severity, String code, String message, SourceLocation location) {
        this(severity, code, message, location, null);
    }
    
    public Diagnostic(Severity severity, String code, String message, SourceLocation location, String suggestion) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.location = location;
        this.suggestion = suggestion;
    }
    
    public Severity getSeverity() {
        return severity;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public SourceLocation getLocation() {
        return location;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
    
    public boolean hasLocation() {
        return location != null && !location.equals(SourceLocation.unknown());
    }
    
    public boolean hasSuggestion() {
        return suggestion != null && !suggestion.isEmpty();
    }
    
    public boolean isError() {
        return severity == Severity.ERROR;
    }
    
    public boolean isWarning() {
        return severity == Severity.WARNING;
    }
    
    public boolean isInfo() {
        return severity == Severity.INFO;
    }
    
    public String getHint() {
        return suggestion;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Severity and code
        sb.append(severity).append(" [").append(code).append("]: ");
        
        // Location
        if (hasLocation()) {
            sb.append(location.fileName())
              .append(":")
              .append(location.line())
              .append(":")
              .append(location.column())
              .append(": ");
        }
        
        // Message
        sb.append(message);
        
        // Suggestion
        if (hasSuggestion()) {
            sb.append("\n  Suggestion: ").append(suggestion);
        }
        
        return sb.toString();
    }
}
