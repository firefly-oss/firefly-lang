package com.firefly.intellij.lsp;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import com.firefly.intellij.FireflyLanguage;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Completion contributor that integrates LSP completion with IntelliJ's completion UI.
 */
public class FireflyCompletionContributor extends CompletionContributor {
    
    private static final Logger LOG = Logger.getInstance(FireflyCompletionContributor.class);
    private static final int TIMEOUT_SECONDS = 3;
    
    public FireflyCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(FireflyLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                   @NotNull ProcessingContext context,
                                                   @NotNull CompletionResultSet result) {
                        addLSPCompletions(parameters, result);
                    }
                });
    }
    
    private void addLSPCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        Editor editor = parameters.getEditor();
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null) {
            return;
        }
        
        FireflyLSPClient lspClient = parameters.getOriginalFile().getProject().getService(FireflyLSPClient.class);
        if (lspClient == null || !lspClient.isInitialized()) {
            LOG.debug("LSP client not initialized, skipping completion");
            return;
        }
        
        LanguageServer languageServer = lspClient.getLanguageServer();
        if (languageServer == null) {
            return;
        }
        
        try {
            // Get cursor position
            int offset = parameters.getOffset();
            Document document = editor.getDocument();
            int line = document.getLineNumber(offset);
            int lineStartOffset = document.getLineStartOffset(line);
            int character = offset - lineStartOffset;
            
            // Create completion params
            CompletionParams completionParams = new CompletionParams();
            TextDocumentIdentifier textDocument = new TextDocumentIdentifier(virtualFile.getUrl());
            completionParams.setTextDocument(textDocument);
            completionParams.setPosition(new Position(line, character));
            
            // Request completions from LSP
            CompletableFuture<Either<List<CompletionItem>, CompletionList>> future =
                    languageServer.getTextDocumentService().completion(completionParams);
            
            Either<List<CompletionItem>, CompletionList> completionResult = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (completionResult == null) {
                return;
            }
            
            List<CompletionItem> items;
            if (completionResult.isLeft()) {
                items = completionResult.getLeft();
            } else {
                items = completionResult.getRight().getItems();
            }
            
            // Convert LSP completion items to IntelliJ lookup elements
            for (CompletionItem item : items) {
                LookupElement lookupElement = createLookupElement(item);
                result.addElement(lookupElement);
            }
            
        } catch (Exception e) {
            LOG.error("Error getting completions from LSP", e);
        }
    }
    
    /**
     * Convert LSP CompletionItem to IntelliJ LookupElement.
     */
    private LookupElement createLookupElement(CompletionItem item) {
        LookupElementBuilder builder = LookupElementBuilder.create(item.getLabel());
        
        // Add type text (detail)
        if (item.getDetail() != null && !item.getDetail().isEmpty()) {
            builder = builder.withTypeText(item.getDetail(), true);
        }
        
        // Add icon based on kind
        Icon icon = getIconForKind(item.getKind());
        if (icon != null) {
            builder = builder.withIcon(icon);
        }
        
        // Add insert text
        String insertText = item.getInsertText();
        if (insertText != null && !insertText.isEmpty() && !insertText.equals(item.getLabel())) {
            builder = builder.withInsertHandler((insertContext, lookupElement) -> {
                // Replace the inserted text with the insert text
                Document document = insertContext.getDocument();
                int startOffset = insertContext.getStartOffset();
                int tailOffset = insertContext.getTailOffset();
                document.replaceString(startOffset, tailOffset, insertText);
                insertContext.getEditor().getCaretModel().moveToOffset(startOffset + insertText.length());
            });
        }
        
        // Add documentation
        if (item.getDocumentation() != null) {
            String doc = getDocumentationString(item.getDocumentation());
            if (doc != null && !doc.isEmpty()) {
                builder = builder.withTailText(" " + doc, true);
            }
        }
        
        return builder;
    }
    
    /**
     * Get icon for completion item kind.
     */
    private Icon getIconForKind(CompletionItemKind kind) {
        // IntelliJ provides standard icons through AllIcons
        // For now, return null and let IntelliJ use default icons
        // You can customize this to use specific icons for different kinds
        return null;
    }
    
    /**
     * Extract documentation string from Either type.
     */
    private String getDocumentationString(Either<String, MarkupContent> documentation) {
        if (documentation.isLeft()) {
            return documentation.getLeft();
        } else {
            MarkupContent markup = documentation.getRight();
            return markup.getValue();
        }
    }
}

