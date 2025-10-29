package com.firefly.intellij.lsp;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Documentation provider that integrates LSP hover with IntelliJ's quick documentation.
 */
public class FireflyDocumentationProvider extends AbstractDocumentationProvider {
    
    private static final Logger LOG = Logger.getInstance(FireflyDocumentationProvider.class);
    private static final int TIMEOUT_SECONDS = 3;
    
    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element == null) {
            return null;
        }
        
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }
        
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        
        FireflyLSPClient lspClient = element.getProject().getService(FireflyLSPClient.class);
        if (lspClient == null || !lspClient.isInitialized()) {
            LOG.debug("LSP client not initialized, skipping documentation");
            return null;
        }
        
        LanguageServer languageServer = lspClient.getLanguageServer();
        if (languageServer == null) {
            return null;
        }
        
        try {
            // Get element position
            int offset = element.getTextOffset();
            Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
            if (document == null) {
                return null;
            }
            
            int line = document.getLineNumber(offset);
            int lineStartOffset = document.getLineStartOffset(line);
            int character = offset - lineStartOffset;
            
            // Create hover params
            HoverParams hoverParams = new HoverParams();
            TextDocumentIdentifier textDocument = new TextDocumentIdentifier(virtualFile.getUrl());
            hoverParams.setTextDocument(textDocument);
            hoverParams.setPosition(new Position(line, character));
            
            // Request hover from LSP
            CompletableFuture<Hover> future = languageServer.getTextDocumentService().hover(hoverParams);
            Hover hover = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (hover == null) {
                return null;
            }
            
            // Convert hover to documentation
            return convertHoverToDocumentation(hover, element.getText());
            
        } catch (Exception e) {
            LOG.error("Error getting hover from LSP", e);
            return null;
        }
    }
    
    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        // Use the same documentation for quick navigation
        return generateDoc(element, originalElement);
    }
    
    /**
     * Convert LSP Hover to IntelliJ documentation HTML.
     */
    private String convertHoverToDocumentation(Hover hover, String elementText) {
        Either<List<Either<String, MarkedString>>, MarkupContent> contents = hover.getContents();
        
        if (contents == null) {
            return null;
        }
        
        StringBuilder doc = new StringBuilder();
        
        // Start documentation
        doc.append(DocumentationMarkup.DEFINITION_START);
        
        if (contents.isLeft()) {
            // List of strings or marked strings
            List<Either<String, MarkedString>> list = contents.getLeft();
            for (Either<String, MarkedString> item : list) {
                if (item.isLeft()) {
                    doc.append(escapeHtml(item.getLeft()));
                } else {
                    MarkedString markedString = item.getRight();
                    String value = markedString.getValue();
                    if (value != null) {
                        doc.append(escapeHtml(value));
                    }
                }
                doc.append("<br/>");
            }
        } else {
            // MarkupContent
            MarkupContent markup = contents.getRight();
            String value = markup.getValue();
            
            if (markup.getKind().equals(MarkupKind.MARKDOWN)) {
                // Convert markdown to HTML
                doc.append(convertMarkdownToHtml(value));
            } else {
                // Plain text
                doc.append("<pre>").append(escapeHtml(value)).append("</pre>");
            }
        }
        
        doc.append(DocumentationMarkup.DEFINITION_END);
        
        return doc.toString();
    }
    
    /**
     * Convert markdown to HTML.
     * This is a simple conversion - for production, consider using a markdown library.
     */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        boolean inList = false;
        
        for (String line : lines) {
            // Code blocks
            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    html.append("</pre>");
                    inCodeBlock = false;
                } else {
                    html.append("<pre>");
                    inCodeBlock = true;
                }
                continue;
            }
            
            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }
            
            // Headers
            if (line.startsWith("### ")) {
                html.append("<h3>").append(escapeHtml(line.substring(4))).append("</h3>");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(escapeHtml(line.substring(3))).append("</h2>");
            } else if (line.startsWith("# ")) {
                html.append("<h1>").append(escapeHtml(line.substring(2))).append("</h1>");
            }
            // Bold
            else if (line.startsWith("**") && line.endsWith("**")) {
                html.append("<b>").append(escapeHtml(line.substring(2, line.length() - 2))).append("</b>");
            }
            // Lists
            else if (line.startsWith("- ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(escapeHtml(line.substring(2))).append("</li>");
            } else {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                if (!line.trim().isEmpty()) {
                    html.append("<p>").append(escapeHtml(line)).append("</p>");
                }
            }
        }
        
        if (inCodeBlock) {
            html.append("</pre>");
        }
        if (inList) {
            html.append("</ul>");
        }
        
        return html.toString();
    }
    
    /**
     * Escape HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

