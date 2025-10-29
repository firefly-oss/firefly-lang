package com.firefly.intellij.lsp;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Startup activity to initialize the Firefly LSP client.
 * This runs after the project is opened and initialized.
 */
public class FireflyLSPStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(FireflyLSPStartupActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        LOG.info("Starting Firefly LSP client for project: " + project.getName());

        // Get LSP client service and start it
        FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
        if (lspClient != null) {
            lspClient.start();

            // Register document listener for file synchronization
            FireflyDocumentListener documentListener = new FireflyDocumentListener(project);
            project.getMessageBus().connect().subscribe(
                    FileEditorManagerListener.FILE_EDITOR_MANAGER,
                    documentListener
            );

            // Register document change listener
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                    documentListener,
                    project
            );

            LOG.info("Firefly LSP document listeners registered");
        } else {
            LOG.error("Failed to get FireflyLSPClient service");
        }

        return null;
    }
}

