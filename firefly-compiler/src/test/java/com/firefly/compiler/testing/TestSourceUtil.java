package com.firefly.compiler.testing;

public final class TestSourceUtil {
    private TestSourceUtil() {}

    public static String normalize(String moduleSuffix, String source, boolean allowTraitsAndImpls) {
        String s = source == null ? "" : source;
        // Legacy replacements
        s = s.replaceAll("(?m)^\\s*package\\s+", "module ");
        s = s.replaceAll("(?m)^\\s*import\\s+", "use ");

        // Ensure module declaration
        String header = "module tests::" + moduleSuffix + "\n\n";
        if (!s.trim().startsWith("module ")) {
            s = header + s;
        }

        boolean containsTraitOrImpl = s.matches("(?s).*^\\s*(trait|impl)\\b.*");
        if (allowTraitsAndImpls && containsTraitOrImpl) {
            // Leave as-is (top-level trait/impl/data/struct)
            return s;
        }

        // Split header (module + optional use block) from body
        int insertPos = findInsertPositionAfterUses(s);
        String prelude = s.substring(0, insertPos);
        String body = s.substring(insertPos);

        // Transform function lines in body: add visibility and convert `= expr` to block
        String transformed = transformFunctions(body);

        // Wrap in class Test
        return prelude + "class Test {\n" + transformed + "\n}\n";
    }

    private static int findInsertPositionAfterUses(String s) {
        String[] lines = s.split("\n", -1);
        int pos = 0;
        int i = 0;
        // module line
        if (lines.length > 0) {
            pos += lines[0].length() + 1;
            i = 1;
        }
        // subsequent use lines
        while (i < lines.length) {
            String line = lines[i];
            if (line.trim().startsWith("use ")) {
                pos += line.length() + 1;
                i++;
            } else {
                break;
            }
        }
        return pos;
    }

    private static String transformFunctions(String body) {
        StringBuilder out = new StringBuilder();
        String[] lines = body.split("\n", -1);
        for (String raw : lines) {
            String line = raw;
            String trimmed = line.trim();
            boolean isFn = trimmed.startsWith("fn ") || trimmed.startsWith("async fn ");
            if (isFn) {
                // Ensure visibility 'pub '
                int indentLen = line.indexOf(trimmed);
                String indent = indentLen >= 0 ? line.substring(0, indentLen) : "";
                if (trimmed.startsWith("async fn ")) {
                    line = indent + "pub " + trimmed;
                } else if (trimmed.startsWith("fn ")) {
                    line = indent + "pub " + trimmed;
                }
                // Convert inline form `= expr` to block if no '{' present
                if (!line.contains("{")) {
                    int eq = line.lastIndexOf('=');
                    if (eq > 0) {
                        String head = line.substring(0, eq).trim();
                        String expr = line.substring(eq + 1).trim();
                        if (expr.endsWith(";")) {
                            expr = expr.substring(0, expr.length() - 1).trim();
                        }
                        line = indent + head + " { " + expr + " }";
                    }
                }
            }
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }
}
