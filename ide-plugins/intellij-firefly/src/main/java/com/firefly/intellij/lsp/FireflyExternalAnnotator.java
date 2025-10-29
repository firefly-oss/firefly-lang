package com.firefly.intellij.lsp;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * External annotator that displays LSP diagnostics in IntelliJ's editor.
 * This integrates LSP diagnostics with IntelliJ's problem view and error highlighting.
 */
public class FireflyExternalAnnotator extends ExternalAnnotator<FireflyExternalAnnotator.AnnotationInfo, List<Diagnostic>> {
    
    private static final Logger LOG = Logger.getInstance(FireflyExternalAnnotator.class);
    private static final int TIMEOUT_SECONDS = 5;
    
    /**
     * Information collected during the initial pass.
     */
    public static class AnnotationInfo {
        public final PsiFile file;
        public final Document document;
        public final String uri;
        
        public AnnotationInfo(PsiFile file, Document document, String uri) {
            this.file = file;
            this.document = document;
            this.uri = uri;
        }
    }
    
    @Override
    public @Nullable AnnotationInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        
        Document document = editor.getDocument();
        String uri = virtualFile.getUrl();
        
        return new AnnotationInfo(file, document, uri);
    }
    
    @Override
    public @Nullable List<Diagnostic> doAnnotate(AnnotationInfo info) {
        FireflyLSPClient lspClient = info.file.getProject().getService(FireflyLSPClient.class);
        if (lspClient == null || !lspClient.isInitialized()) {
            LOG.debug("LSP client not initialized, skipping annotation");
            return Collections.emptyList();
        }
        
        LanguageServer languageServer = lspClient.getLanguageServer();
        if (languageServer == null) {
            return Collections.emptyList();
        }
        
        try {
            // Notify LSP server of document change
            DidChangeTextDocumentParams changeParams = new DidChangeTextDocumentParams();
            VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
            textDocument.setUri(info.uri);
            textDocument.setVersion((int) info.document.getModificationStamp());
            changeParams.setTextDocument(textDocument);
            
            TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
            change.setText(info.document.getText());
            changeParams.setContentChanges(Collections.singletonList(change));
            
            languageServer.getTextDocumentService().didChange(changeParams);
            
            // Get diagnostics from the diagnostics holder
            List<Diagnostic> diagnostics = lspClient.getDiagnostics(info.uri);
            LOG.debug("Retrieved " + diagnostics.size() + " diagnostics for " + info.uri);
            return diagnostics;
            
        } catch (Exception e) {
            LOG.error("Error getting diagnostics from LSP", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public void apply(@NotNull PsiFile file, List<Diagnostic> diagnostics, @NotNull AnnotationHolder holder) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return;
        }
        
        Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document == null) {
            return;
        }
        
        for (Diagnostic diagnostic : diagnostics) {
            try {
                Range range = diagnostic.getRange();
                int startOffset = getOffset(document, range.getStart());
                int endOffset = getOffset(document, range.getEnd());
                
                // Ensure valid range
                if (startOffset < 0 || endOffset < 0 || startOffset > endOffset) {
                    LOG.warn("Invalid diagnostic range: " + range);
                    continue;
                }
                
                TextRange textRange = new TextRange(startOffset, endOffset);
                HighlightSeverity severity = convertSeverity(diagnostic.getSeverity());
                
                String message = diagnostic.getMessage();
                if (diagnostic.getCode() != null) {
                    message = "[" + diagnostic.getCode() + "] " + message;
                }
                
                holder.newAnnotation(severity, message)
                        .range(textRange)
                        .create();
                
            } catch (Exception e) {
                LOG.error("Error applying diagnostic: " + diagnostic, e);
            }
        }
    }
    
    /**
     * Convert LSP position to document offset.
     */
    private int getOffset(Document document, Position position) {
        int line = position.getLine();
        int character = position.getCharacter();
        
        if (line < 0 || line >= document.getLineCount()) {
            return -1;
        }
        
        int lineStartOffset = document.getLineStartOffset(line);
        int lineEndOffset = document.getLineEndOffset(line);
        int offset = lineStartOffset + character;
        
        // Clamp to line bounds
        return Math.min(offset, lineEndOffset);
    }
    
    /**
     * Convert LSP diagnostic severity to IntelliJ highlight severity.
     */
    private HighlightSeverity convertSeverity(DiagnosticSeverity severity) {
        if (severity == null) {
            return HighlightSeverity.INFORMATION;
        }
        
        return switch (severity) {
            case Error -> HighlightSeverity.ERROR;
            case Warning -> HighlightSeverity.WARNING;
            case Information -> HighlightSeverity.INFORMATION;
            case Hint -> HighlightSeverity.WEAK_WARNING;
        };
    }
}

