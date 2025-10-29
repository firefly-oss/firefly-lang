package com.firefly.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Firefly Language Server
 * 
 * <p>Implements the Language Server Protocol (LSP) for Firefly, providing:
 * <ul>
 *   <li>Syntax error diagnostics</li>
 *   <li>Code completion</li>
 *   <li>Go to definition</li>
 *   <li>Hover information</li>
 *   <li>Document symbols</li>
 *   <li>Formatting</li>
 * </ul>
 * 
 * <p>This enables IDE support for IntelliJ IDEA, VS Code, and other LSP-compatible editors.
 */
public class FireflyLanguageServer implements LanguageServer, LanguageClientAware {
    
    private static final Logger logger = LoggerFactory.getLogger(FireflyLanguageServer.class);
    
    private final FireflyTextDocumentService textDocumentService;
    private final FireflyWorkspaceService workspaceService;
    private LanguageClient client;
    private int errorCode = 1;

    public FireflyLanguageServer() {
        this.textDocumentService = new FireflyTextDocumentService(this);
        this.workspaceService = new FireflyWorkspaceService();
        logger.info("Firefly Language Server initialized");
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        logger.info("Initializing Firefly Language Server");
        logger.info("Client: {}", params.getClientInfo());
        logger.info("Root URI: {}", params.getRootUri());

        // Server capabilities
        ServerCapabilities capabilities = new ServerCapabilities();
        
        // Text document sync - full sync for now
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        
        // Completion support
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setResolveProvider(false);
        completionOptions.setTriggerCharacters(Arrays.asList(".", ":"));
        capabilities.setCompletionProvider(completionOptions);
        
        // Hover support
        capabilities.setHoverProvider(true);
        
        // Definition support
        capabilities.setDefinitionProvider(true);
        
        // Document symbol support
        capabilities.setDocumentSymbolProvider(true);
        
        // Formatting support
        capabilities.setDocumentFormattingProvider(true);
        
        // Signature help
        SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions();
        signatureHelpOptions.setTriggerCharacters(Arrays.asList("(", ","));
        capabilities.setSignatureHelpProvider(signatureHelpOptions);

        // References support
        capabilities.setReferencesProvider(true);

        InitializeResult result = new InitializeResult(capabilities);
        
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setName("Firefly Language Server");
        serverInfo.setVersion("1.0-Alpha");
        result.setServerInfo(serverInfo);
        
        logger.info("Firefly Language Server initialized successfully");
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        logger.info("Shutting down Firefly Language Server");
        errorCode = 0;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        logger.info("Exiting Firefly Language Server with code: {}", errorCode);
        System.exit(errorCode);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        logger.info("Connected to language client");
    }

    public LanguageClient getClient() {
        return client;
    }

    @Override
    public void setTrace(SetTraceParams params) {
        // Set trace level for debugging
        logger.info("Trace level set to: {}", params.getValue());
    }

    /**
     * Main entry point for the Language Server.
     * 
     * <p>Starts the server and listens for LSP messages on stdin/stdout.
     */
    public static void main(String[] args) {
        try {
            logger.info("Starting Firefly Language Server...");
            
            FireflyLanguageServer server = new FireflyLanguageServer();
            
            // Use stdin/stdout for communication
            InputStream in = System.in;
            OutputStream out = System.out;
            
            // Launch the server
            var launcher = LSPLauncher.createServerLauncher(
                server,
                in,
                out
            );
            
            // Connect to the client
            LanguageClient client = launcher.getRemoteProxy();
            server.connect(client);
            
            // Start listening
            logger.info("Firefly Language Server started, listening for requests...");
            launcher.startListening().get();
            
        } catch (Exception e) {
            logger.error("Fatal error in Firefly Language Server", e);
            System.exit(1);
        }
    }
}

