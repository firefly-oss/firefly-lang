import * as path from 'path';
import * as fs from 'fs';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    Executable
} from 'vscode-languageclient/node';

let client: LanguageClient;
let outputChannel: vscode.OutputChannel;

export function activate(context: vscode.ExtensionContext) {
    // Create output channel immediately
    outputChannel = vscode.window.createOutputChannel('Firefly Language Server');
    outputChannel.appendLine('🔥 Firefly Language Support is now active!');
    console.log('Firefly Language Support is now active!');

    // Get configuration
    const config = vscode.workspace.getConfiguration('firefly');
    let serverPath = config.get<string>('languageServer.path');

    // If no custom path, try to find the server
    if (!serverPath || serverPath === '') {
        serverPath = findLSPServer();
    }

    // Check if server exists
    if (!serverPath || !fs.existsSync(serverPath)) {
        const message = 'Firefly Language Server not found. LSP features (diagnostics, completion, hover) will not be available.';
        outputChannel.appendLine('❌ ' + message);
        outputChannel.appendLine('');
        outputChannel.appendLine('Searched locations:');
        outputChannel.appendLine('  1. Workspace: firefly-lsp/target/firefly-lsp.jar');
        outputChannel.appendLine('  2. Home: ~/.firefly/firefly-lsp.jar');
        outputChannel.appendLine('');
        outputChannel.appendLine('To build the LSP server, run:');
        outputChannel.appendLine('  mvn clean package -DskipTests -pl firefly-lsp -am');
        outputChannel.appendLine('');
        outputChannel.appendLine('✅ Syntax highlighting will still work without LSP server.');

        vscode.window.showWarningMessage(
            message,
            'Build LSP Server',
            'Open Settings',
            'Show Output',
            'Dismiss'
        ).then(selection => {
            if (selection === 'Build LSP Server') {
                vscode.window.showInformationMessage(
                    'Run this command in the firefly-lang directory:\nmvn clean package -DskipTests -pl firefly-lsp -am'
                );
            } else if (selection === 'Open Settings') {
                vscode.commands.executeCommand('workbench.action.openSettings', 'firefly.languageServer.path');
            } else if (selection === 'Show Output') {
                outputChannel.show();
            }
        });
        console.warn('LSP Server not found. Syntax highlighting will still work.');
        return;
    }

    outputChannel.appendLine('✅ Found LSP server at: ' + serverPath);
    console.log('Using Firefly Language Server at: ' + serverPath);

    outputChannel.appendLine('🚀 Starting Firefly Language Server...');
    vscode.window.showInformationMessage('Firefly Language Server starting...');

    // Server options - launch the Java LSP server
    const serverOptions: ServerOptions = {
        run: createExecutable(serverPath),
        debug: createExecutable(serverPath)
    };

    // Client options
    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'firefly' }],
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.fly')
        },
        outputChannel: outputChannel,
        traceOutputChannel: vscode.window.createOutputChannel('Firefly Language Server Trace'),
        revealOutputChannelOn: 4 // RevealOutputChannelOn.Never
    };

    // Create the language client
    client = new LanguageClient(
        'fireflyLanguageServer',
        'Firefly Language Server',
        serverOptions,
        clientOptions
    );

    // Start the client (and server)
    outputChannel.appendLine('⏳ Initializing LSP client...');

    client.start().then(() => {
        outputChannel.appendLine('');
        outputChannel.appendLine('✅ Firefly Language Server is ready!');
        outputChannel.appendLine('');
        outputChannel.appendLine('Available LSP features:');
        outputChannel.appendLine('  • Real-time error diagnostics');
        outputChannel.appendLine('  • Intelligent code completion');
        outputChannel.appendLine('  • Hover documentation');
        outputChannel.appendLine('  • Go to definition');
        outputChannel.appendLine('  • Find references');
        outputChannel.appendLine('  • Document symbols (outline)');
        outputChannel.appendLine('  • Signature help (parameter hints)');
        outputChannel.appendLine('  • Code formatting');
        outputChannel.appendLine('');
        outputChannel.appendLine('🔥 Happy coding with Firefly!');

        console.log('Firefly Language Server is ready!');
        vscode.window.showInformationMessage('Firefly Language Server is ready! LSP features enabled.');
    }).catch((error: Error) => {
        outputChannel.appendLine('');
        outputChannel.appendLine('❌ Failed to start Firefly Language Server');
        outputChannel.appendLine('Error: ' + error.message);
        outputChannel.appendLine('');
        outputChannel.appendLine('Troubleshooting:');
        outputChannel.appendLine('  1. Check that Java 17+ is installed: java -version');
        outputChannel.appendLine('  2. Verify LSP server exists: ls -lh ' + serverPath);
        outputChannel.appendLine('  3. Try rebuilding: mvn clean package -DskipTests -pl firefly-lsp -am');
        outputChannel.appendLine('  4. Check the "Firefly Language Server Trace" output for details');
        outputChannel.show();

        console.error('Failed to start Firefly Language Server:', error);
        vscode.window.showErrorMessage('Failed to start Firefly Language Server: ' + error.message, 'Show Output').then(selection => {
            if (selection === 'Show Output') {
                outputChannel.show();
            }
        });
    });

    console.log('Firefly Language Server starting...');
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}

function createExecutable(serverPath: string): Executable {
    return {
        command: 'java',
        args: [
            '-jar',
            serverPath
        ],
        options: {
            env: process.env
        }
    };
}

/**
 * Find the LSP server JAR file.
 * Tries multiple locations in order:
 * 1. Workspace root (for development)
 * 2. User home directory (for installed version)
 */
function findLSPServer(): string | undefined {
    // Try workspace root first
    const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
    if (workspaceRoot) {
        const workspaceLSP = path.join(workspaceRoot, 'firefly-lsp', 'target', 'firefly-lsp.jar');
        if (fs.existsSync(workspaceLSP)) {
            return workspaceLSP;
        }
    }

    // Try user home directory
    const homeDir = process.env.HOME || process.env.USERPROFILE;
    if (homeDir) {
        const homeLSP = path.join(homeDir, '.firefly', 'firefly-lsp.jar');
        if (fs.existsSync(homeLSP)) {
            return homeLSP;
        }
    }

    return undefined;
}

