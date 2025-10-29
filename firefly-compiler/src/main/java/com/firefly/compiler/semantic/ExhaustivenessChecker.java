package com.firefly.compiler.semantic;

import com.firefly.compiler.ast.Pattern;
import com.firefly.compiler.ast.expr.MatchExpr;
import com.firefly.compiler.ast.pattern.*;
import com.firefly.compiler.diagnostic.DiagnosticReporter;

import java.util.*;

/**
 * Checks if match expressions are exhaustive (cover all possible cases).
 */
public class ExhaustivenessChecker {
    
    private final DiagnosticReporter reporter;
    
    public ExhaustivenessChecker(DiagnosticReporter reporter) {
        this.reporter = reporter;
    }
    
    /**
     * Check if a match expression is exhaustive.
     * Returns true if all cases are covered, false otherwise.
     */
    public boolean checkExhaustiveness(MatchExpr matchExpr) {
        List<MatchExpr.MatchArm> arms = matchExpr.getArms();
        
        if (arms.isEmpty()) {
            reporter.error("EXHAUSTIVE001",
                "Match expression has no arms",
                matchExpr.getLocation());
            return false;
        }
        
        // Check if there's a wildcard or variable pattern (catches all)
        for (MatchExpr.MatchArm arm : arms) {
            if (isCatchAllPattern(arm.getPattern())) {
                // If there's a guard, it's not truly catch-all
                if (arm.getGuard() == null) {
                    return true;
                }
            }
        }
        
        // For now, we'll do a simple check:
        // - If there's a wildcard/variable pattern without guard, it's exhaustive
        // - Otherwise, we warn that exhaustiveness checking is incomplete
        
        reporter.warning("EXHAUSTIVE002",
            "Exhaustiveness checking is not yet complete. " +
            "Consider adding a wildcard pattern (_) as the last arm to ensure all cases are covered.",
            matchExpr.getLocation());
        
        return true; // Don't fail compilation, just warn
    }
    
    /**
     * Check if a pattern catches all possible values.
     */
    private boolean isCatchAllPattern(Pattern pattern) {
        if (pattern instanceof WildcardPattern) {
            return true;
        }
        
        if (pattern instanceof VariablePattern) {
            return true;
        }
        
        if (pattern instanceof OrPattern) {
            OrPattern orPattern = (OrPattern) pattern;
            return isCatchAllPattern(orPattern.getLeft()) || 
                   isCatchAllPattern(orPattern.getRight());
        }
        
        return false;
    }
    
    /**
     * Check if patterns in match arms overlap (useful for detecting unreachable code).
     */
    public void checkForUnreachablePatterns(MatchExpr matchExpr) {
        List<MatchExpr.MatchArm> arms = matchExpr.getArms();
        
        for (int i = 0; i < arms.size(); i++) {
            MatchExpr.MatchArm arm = arms.get(i);
            
            // If we find a catch-all pattern without guard, all subsequent patterns are unreachable
            if (isCatchAllPattern(arm.getPattern()) && arm.getGuard() == null) {
                if (i < arms.size() - 1) {
                    reporter.warning("EXHAUSTIVE003",
                        "Unreachable pattern: previous pattern catches all cases",
                        arms.get(i + 1).getPattern().getLocation());
                }
                break;
            }
        }
    }
    
    /**
     * Analyze patterns to determine if they're mutually exclusive.
     * This is a simplified version - full implementation would require type information.
     */
    public boolean patternsOverlap(Pattern p1, Pattern p2) {
        // Wildcard overlaps with everything
        if (p1 instanceof WildcardPattern || p2 instanceof WildcardPattern) {
            return true;
        }
        
        // Variable patterns overlap with everything
        if (p1 instanceof VariablePattern || p2 instanceof VariablePattern) {
            return true;
        }
        
        // Literal patterns overlap only if they're the same literal
        if (p1 instanceof LiteralPattern && p2 instanceof LiteralPattern) {
            LiteralPattern lit1 = (LiteralPattern) p1;
            LiteralPattern lit2 = (LiteralPattern) p2;
            return lit1.getLiteral().getValue().equals(lit2.getLiteral().getValue());
        }
        
        // Different pattern types generally don't overlap
        // (except for the catch-all cases above)
        return false;
    }
}

