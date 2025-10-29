package com.firefly.intellij.lsp;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * LSP client for Firefly language server.
 * Manages connection to the Firefly LSP server.
 */
@Service(Service.Level.PROJECT)
public final class FireflyLSPClient implements LanguageClient {
    
    private static final Logger LOG = Logger.getInstance(FireflyLSPClient.class);

    private final Project project;
    private final Map<String, List<Diagnostic>> diagnosticsMap = new ConcurrentHashMap<>();
    private Process serverProcess;
    private LanguageServer languageServer;
    private boolean initialized = false;
    
    public FireflyLSPClient(Project project) {
        this.project = project;
    }
    
    /**
     * Start the LSP server.
     */
    public void start() {
        if (initialized) {
            LOG.info("LSP server already initialized");
            return;
        }
        
        try {
            // Find LSP server JAR
            String lspJarPath = findLSPServerJar();
            if (lspJarPath == null) {
                LOG.error("Firefly LSP server JAR not found");
                return;
            }
            
            LOG.info("Starting Firefly LSP server: " + lspJarPath);
            
            // Start server process
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-jar", lspJarPath
            );
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            serverProcess = processBuilder.start();
            
            // Create LSP launcher
            var launcher = LSPLauncher.createClientLauncher(
                    this,
                    serverProcess.getInputStream(),
                    serverProcess.getOutputStream()
            );
            
            languageServer = launcher.getRemoteProxy();
            launcher.startListening();
            
            // Initialize server
            InitializeParams initParams = new InitializeParams();
            initParams.setProcessId((int) ProcessHandle.current().pid());
            
            WorkspaceFolder workspaceFolder = new WorkspaceFolder();
            workspaceFolder.setUri(project.getBasePath());
            workspaceFolder.setName(project.getName());
            initParams.setWorkspaceFolders(java.util.List.of(workspaceFolder));
            
            ClientCapabilities capabilities = new ClientCapabilities();
            
            // Text document capabilities
            TextDocumentClientCapabilities textDocumentCapabilities = new TextDocumentClientCapabilities();
            textDocumentCapabilities.setSynchronization(new SynchronizationCapabilities(true, true, true));
            textDocumentCapabilities.setCompletion(new CompletionCapabilities(new CompletionItemCapabilities(true)));
            textDocumentCapabilities.setHover(new HoverCapabilities(true));
            textDocumentCapabilities.setDefinition(new DefinitionCapabilities(true));
            textDocumentCapabilities.setReferences(new ReferencesCapabilities(true));
            textDocumentCapabilities.setDocumentSymbol(new DocumentSymbolCapabilities(true));
            textDocumentCapabilities.setSignatureHelp(new SignatureHelpCapabilities(true));
            textDocumentCapabilities.setFormatting(new FormattingCapabilities(true));
            capabilities.setTextDocument(textDocumentCapabilities);
            
            // Workspace capabilities
            WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
            workspaceCapabilities.setApplyEdit(true);
            workspaceCapabilities.setWorkspaceFolders(true);
            capabilities.setWorkspace(workspaceCapabilities);
            
            initParams.setCapabilities(capabilities);
            
            CompletableFuture<InitializeResult> initFuture = languageServer.initialize(initParams);
            InitializeResult initResult = initFuture.get();
            
            languageServer.initialized(new InitializedParams());
            
            initialized = true;
            LOG.info("Firefly LSP server initialized successfully");
            LOG.info("Server capabilities: " + initResult.getCapabilities());
            
        } catch (IOException | InterruptedException | ExecutionException e) {
            LOG.error("Failed to start Firefly LSP server", e);
        }
    }
    
    /**
     * Stop the LSP server.
     */
    public void stop() {
        if (!initialized) {
            return;
        }
        
        try {
            if (languageServer != null) {
                languageServer.shutdown().get();
                languageServer.exit();
            }
            
            if (serverProcess != null && serverProcess.isAlive()) {
                serverProcess.destroy();
                serverProcess.waitFor();
            }
            
            initialized = false;
            LOG.info("Firefly LSP server stopped");
            
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error stopping LSP server", e);
        }
    }
    
    /**
     * Get the language server instance.
     */
    public LanguageServer getLanguageServer() {
        return languageServer;
    }
    
    /**
     * Check if server is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Find the LSP server JAR file.
     */
    private String findLSPServerJar() {
        // Try to find in project
        String projectPath = project.getBasePath();
        if (projectPath != null) {
            String lspJar = projectPath + "/firefly-lsp/target/firefly-lsp.jar";
            if (new File(lspJar).exists()) {
                return lspJar;
            }
        }
        
        // Try to find in user home
        String homeDir = System.getProperty("user.home");
        String lspJar = homeDir + "/.firefly/firefly-lsp.jar";
        if (new File(lspJar).exists()) {
            return lspJar;
        }
        
        return null;
    }
    
    // LanguageClient interface methods
    
    @Override
    public void telemetryEvent(Object object) {
        LOG.info("Telemetry event: " + object);
    }
    
    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        LOG.info("Diagnostics: " + diagnostics.getUri() + " - " + diagnostics.getDiagnostics().size() + " issues");

        // Store diagnostics for this URI
        diagnosticsMap.put(diagnostics.getUri(), new ArrayList<>(diagnostics.getDiagnostics()));
    }

    /**
     * Get diagnostics for a specific URI.
     */
    public List<Diagnostic> getDiagnostics(String uri) {
        return diagnosticsMap.getOrDefault(uri, Collections.emptyList());
    }
    
    @Override
    public void showMessage(MessageParams messageParams) {
        LOG.info("Server message: " + messageParams.getMessage());
        // TODO: Show message in IntelliJ
    }
    
    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        LOG.info("Server message request: " + requestParams.getMessage());
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void logMessage(MessageParams message) {
        switch (message.getType()) {
            case Error -> LOG.error("LSP: " + message.getMessage());
            case Warning -> LOG.warn("LSP: " + message.getMessage());
            case Info -> LOG.info("LSP: " + message.getMessage());
            case Log -> LOG.debug("LSP: " + message.getMessage());
        }
    }
}

