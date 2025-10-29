package com.firefly.intellij.lsp;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.firefly.intellij.FireflyFileType;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Listener that synchronizes document changes with the LSP server.
 * Handles file open, close, and change events.
 */
public class FireflyDocumentListener implements FileEditorManagerListener, DocumentListener {
    
    private static final Logger LOG = Logger.getInstance(FireflyDocumentListener.class);
    private final Project project;
    
    public FireflyDocumentListener(Project project) {
        this.project = project;
    }
    
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!isFireflyFile(file)) {
            return;
        }
        
        FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
        if (lspClient == null || !lspClient.isInitialized()) {
            return;
        }
        
        LanguageServer languageServer = lspClient.getLanguageServer();
        if (languageServer == null) {
            return;
        }
        
        try {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
                return;
            }
            
            // Notify LSP server of document open
            DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
            TextDocumentItem textDocument = new TextDocumentItem();
            textDocument.setUri(file.getUrl());
            textDocument.setLanguageId("firefly");
            textDocument.setVersion((int) document.getModificationStamp());
            textDocument.setText(document.getText());
            params.setTextDocument(textDocument);
            
            languageServer.getTextDocumentService().didOpen(params);
            LOG.debug("Notified LSP of file open: " + file.getUrl());
            
        } catch (Exception e) {
            LOG.error("Error notifying LSP of file open", e);
        }
    }
    
    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!isFireflyFile(file)) {
            return;
        }
        
        FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
        if (lspClient == null || !lspClient.isInitialized()) {
            return;
        }
        
        LanguageServer languageServer = lspClient.getLanguageServer();
        if (languageServer == null) {
            return;
        }
        
        try {
            // Notify LSP server of document close
            DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
            TextDocumentIdentifier textDocument = new TextDocumentIdentifier(file.getUrl());
            params.setTextDocument(textDocument);
            
            languageServer.getTextDocumentService().didClose(params);
            LOG.debug("Notified LSP of file close: " + file.getUrl());
            
        } catch (Exception e) {
            LOG.error("Error notifying LSP of file close", e);
        }
    }
    
    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        Document document = event.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        
        if (file == null || !isFireflyFile(file)) {
            return;
        }
        
        FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
        if (lspClient == null || !lspClient.isInitialized()) {
            return;
        }
        
        LanguageServer languageServer = lspClient.getLanguageServer();
        if (languageServer == null) {
            return;
        }
        
        try {
            // Notify LSP server of document change
            DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
            VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
            textDocument.setUri(file.getUrl());
            textDocument.setVersion((int) document.getModificationStamp());
            params.setTextDocument(textDocument);
            
            TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
            change.setText(document.getText());
            params.setContentChanges(Collections.singletonList(change));
            
            languageServer.getTextDocumentService().didChange(params);
            LOG.debug("Notified LSP of document change: " + file.getUrl());
            
        } catch (Exception e) {
            LOG.error("Error notifying LSP of document change", e);
        }
    }
    
    /**
     * Check if the file is a Firefly file.
     */
    private boolean isFireflyFile(VirtualFile file) {
        return file.getFileType() instanceof FireflyFileType;
    }
}

