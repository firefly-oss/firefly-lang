package com.firefly.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Workspace Service for Firefly Language Server.
 * 
 * <p>Handles workspace-level operations:
 * <ul>
 *   <li>Configuration changes</li>
 *   <li>File system changes</li>
 *   <li>Workspace symbols</li>
 * </ul>
 */
public class FireflyWorkspaceService implements WorkspaceService {
    
    private static final Logger logger = LoggerFactory.getLogger(FireflyWorkspaceService.class);

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        logger.info("Configuration changed: {}", params.getSettings());
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        logger.info("Watched files changed: {} files", params.getChanges().size());
    }
}

