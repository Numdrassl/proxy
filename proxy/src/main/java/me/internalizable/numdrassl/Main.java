package me.internalizable.numdrassl;

import me.internalizable.numdrassl.api.ProxyAPI;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.PacketListener;
import me.internalizable.numdrassl.l4.Layer4UdpProxy;
import me.internalizable.numdrassl.server.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Numdrassl - Hytale QUIC Proxy Server
 * Similar to BungeeCord/Velocity for Minecraft, but for Hytale's QUIC protocol.
 *
 * Supports two modes:
 * - Layer 7 (default): Full packet inspection and modification. Requires backend modification.
 * - Layer 4 (--l4 flag): Transparent UDP forwarding. Works with unmodified backend but cannot inspect packets.
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static ProxyServer proxyServer;
    private static Layer4UdpProxy l4Proxy;

    public static void main(String[] args) {
        // Check for Layer 4 mode
        boolean layer4Mode = false;
        for (String arg : args) {
            if ("--l4".equals(arg) || "--layer4".equals(arg) || "--transparent".equals(arg)) {
                layer4Mode = true;
                break;
            }
        }

        if (layer4Mode) {
            startLayer4Proxy(args);
        } else {
            startLayer7Proxy(args);
        }
    }

    private static void startLayer4Proxy(String[] args) {
        LOGGER.info("===========================================");
        LOGGER.info("  Numdrassl - Layer 4 UDP Proxy");
        LOGGER.info("  (Transparent mode - no packet inspection)");
        LOGGER.info("===========================================");

        try {
            // Load configuration
            Path configPath = Paths.get("config", "proxy.yml");
            ProxyConfig config = ProxyConfig.load(configPath);
            LOGGER.info("Configuration loaded from: {}", configPath);

            var backend = config.getDefaultBackend();
            if (backend == null) {
                LOGGER.error("No default backend configured!");
                System.exit(1);
                return;
            }

            l4Proxy = new Layer4UdpProxy(
                config.getBindAddress(),
                config.getBindPort(),
                backend.getHost(),
                backend.getPort()
            );

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown signal received");
                if (l4Proxy != null) {
                    l4Proxy.stop();
                }
            }));

            l4Proxy.start();

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            LOGGER.error("Fatal error starting Layer 4 proxy", e);
            System.exit(1);
        }
    }

    private static void startLayer7Proxy(String[] args) {
        LOGGER.info("===========================================");
        LOGGER.info("  Numdrassl - Hytale QUIC Proxy Server");
        LOGGER.info("  (Layer 7 mode - full packet inspection)");
        LOGGER.info("===========================================");
        LOGGER.info("");
        LOGGER.info("NOTE: This mode requires backend server modification.");
        LOGGER.info("      If authentication fails, try Layer 4 mode: --l4");
        LOGGER.info("");

        try {
            // Load configuration
            Path configPath = Paths.get("config", "proxy.yml");
            ProxyConfig config = ProxyConfig.load(configPath);
            LOGGER.info("Configuration loaded from: {}", configPath);

            // Create and start proxy server
            proxyServer = new ProxyServer(config);

            // Initialize the public API
            ProxyAPI.init(proxyServer);

            // Register example listener for demonstration
            proxyServer.getEventManager().registerListener(new ExamplePacketListener());

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown signal received");
                if (proxyServer != null && proxyServer.isRunning()) {
                    proxyServer.stop();
                }
            }));

            // Start the server
            proxyServer.start();

            // Start console command handler
            startConsoleHandler();

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            LOGGER.error("Fatal error starting proxy server", e);
            System.exit(1);
        }
    }

    private static void startConsoleHandler() {
        Thread consoleThread = new Thread(() -> {
            try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                LOGGER.info("Console ready. Type 'help' for available commands.");

                while (proxyServer != null && proxyServer.isRunning()) {
                    if (!scanner.hasNextLine()) {
                        Thread.sleep(100);
                        continue;
                    }

                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\s+");
                    String command = parts[0].toLowerCase();

                    switch (command) {
                        case "help":
                            LOGGER.info("Available commands:");
                            LOGGER.info("  auth login  - Authenticate proxy with Hytale (for client auth)");
                            LOGGER.info("  auth status - Show authentication status");
                            LOGGER.info("  auth secret - Show proxy secret for backend configuration");
                            LOGGER.info("  sessions    - List active sessions");
                            LOGGER.info("  stop        - Stop the proxy server");
                            break;

                        case "auth":
                            handleAuthCommand(parts);
                            break;

                        case "sessions":
                            var sessions = proxyServer.getSessionManager().getAllSessions();
                            LOGGER.info("Active sessions: {}", sessions.size());
                            for (var session : sessions) {
                                LOGGER.info("  - Session {}: {} ({}) -> {}",
                                    session.getSessionId(),
                                    session.getPlayerName() != null ? session.getPlayerName() : "unknown",
                                    session.getPlayerUuid() != null ? session.getPlayerUuid() : "unknown",
                                    session.getCurrentBackend() != null ? session.getCurrentBackend().getName() : "none");
                            }
                            break;

                        case "stop":
                            LOGGER.info("Stopping proxy server...");
                            proxyServer.stop();
                            System.exit(0);
                            break;

                        default:
                            LOGGER.warn("Unknown command: {}. Type 'help' for available commands.", command);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Console handler error", e);
            }
        }, "Console-Handler");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private static void handleAuthCommand(String[] parts) {
        if (parts.length < 2) {
            LOGGER.info("Usage: auth <login|status|secret>");
            return;
        }

        String subCommand = parts[1].toLowerCase();
        switch (subCommand) {
            case "login":
                var authenticator = proxyServer.getAuthenticator();
                if (authenticator == null) {
                    LOGGER.error("Authenticator not available");
                    return;
                }

                if (authenticator.isAuthenticated()) {
                    LOGGER.info("Proxy is already authenticated as: {} ({})",
                        authenticator.getProfileUsername(), authenticator.getProfileUuid());
                    return;
                }

                LOGGER.info("Starting OAuth device code flow...");
                authenticator.startDeviceCodeFlow().thenAccept(deviceCode -> {
                    if (deviceCode == null) {
                        LOGGER.error("Failed to start device code flow");
                        return;
                    }

                    LOGGER.info("===========================================");
                    LOGGER.info("  To authenticate, visit:");
                    LOGGER.info("  {}", deviceCode.verificationUri);
                    LOGGER.info("");
                    LOGGER.info("  Enter code: {}", deviceCode.userCode);
                    LOGGER.info("===========================================");

                    // Poll for completion
                    authenticator.pollDeviceCode(deviceCode.deviceCode, deviceCode.interval)
                        .thenAccept(success -> {
                            if (success) {
                                LOGGER.info("Authentication successful!");
                                LOGGER.info("Proxy is now authenticated as: {} ({})",
                                    authenticator.getProfileUsername(), authenticator.getProfileUuid());
                            } else {
                                LOGGER.error("Authentication failed!");
                            }
                        });
                });
                break;

            case "status":
                var auth = proxyServer.getAuthenticator();
                if (auth != null && auth.isAuthenticated()) {
                    LOGGER.info("Proxy authenticated as: {} ({})",
                        auth.getProfileUsername(), auth.getProfileUuid());
                    LOGGER.info("Certificate fingerprint: {}", auth.getProxyFingerprint());
                } else {
                    LOGGER.info("Proxy is NOT authenticated with Hytale");
                    LOGGER.info("Use 'auth login' to authenticate");
                }
                LOGGER.info("");
                LOGGER.info("Backend authentication: Secret-based (HMAC referral)");
                break;

            case "secret":
                byte[] secret = proxyServer.getBackendConnector().getProxySecret();
                if (secret != null) {
                    String secretStr = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
                    LOGGER.info("Current proxy secret (Base64): {}", secretStr);
                    LOGGER.info("Configure this same secret on your backend servers.");
                } else {
                    LOGGER.warn("No proxy secret configured!");
                }
                break;

            default:
                LOGGER.info("Usage: auth <login|status|secret>");
        }
    }

    /**
     * Get the running proxy server instance
     */
    public static ProxyServer getProxyServer() {
        return proxyServer;
    }

    /**
     * Example packet listener demonstrating how to intercept packets
     */
    private static class ExamplePacketListener implements PacketListener {

        private static final Logger LOGGER = LoggerFactory.getLogger(ExamplePacketListener.class);

        @Override
        public void onSessionCreated(me.internalizable.numdrassl.session.ProxySession session) {
            LOGGER.info("New player session: {} from {}",
                session.getSessionId(), session.getClientAddress());
        }

        @Override
        public void onSessionClosed(me.internalizable.numdrassl.session.ProxySession session) {
            LOGGER.info("Player session closed: {} ({})",
                session.getSessionId(),
                session.getPlayerName() != null ? session.getPlayerName() : "unknown");
        }

        // Example: Intercept chat messages
        @Override
        @SuppressWarnings("unchecked")
        public <T extends com.hypixel.hytale.protocol.Packet> T onClientPacket(
                me.internalizable.numdrassl.event.PacketEvent<T> event) {

            // Check if it's a chat message packet
            if (event.getPacket() instanceof com.hypixel.hytale.protocol.packets.interface_.ChatMessage) {
                com.hypixel.hytale.protocol.packets.interface_.ChatMessage chat =
                    (com.hypixel.hytale.protocol.packets.interface_.ChatMessage) event.getPacket();

                String playerName = event.getSession().getPlayerName();
                LOGGER.info("[CHAT] {}: {}", playerName != null ? playerName : "unknown", chat.message);

                // Handle /server command
                if (chat.message != null && chat.message.toLowerCase().startsWith("/server")) {
                    handleServerCommand(event.getSession(), chat.message);
                    event.setCancelled(true);
                    return null;
                }
            }

            return event.getPacket();
        }

        /**
         * Handle the /server command - show available servers or switch server
         */
        private void handleServerCommand(me.internalizable.numdrassl.session.ProxySession session, String message) {
            String[] parts = message.split("\\s+");

            if (parts.length == 1) {
                // Just "/server" - show list of available servers
                showServerList(session);
            } else {
                // "/server <name>" - try to switch to that server
                String serverName = parts[1];
                switchServer(session, serverName);
            }
        }

        /**
         * Show the list of available servers to the player
         */
        private void showServerList(me.internalizable.numdrassl.session.ProxySession session) {
            var config = session.getProxyServer().getConfig();
            var backends = config.getBackends();
            var currentBackend = session.getCurrentBackend();

            // Build formatted message with children for different colors
            java.util.List<com.hypixel.hytale.protocol.FormattedMessage> children = new java.util.ArrayList<>();

            // Header: "Available Servers:" in yellow/gold, bold
            children.add(createFormattedText("Available Servers:\n", "#FFAA00", true, false));

            for (var backend : backends) {
                String name = backend.getName();
                boolean isDefault = backend.isDefaultServer();
                boolean isCurrent = currentBackend != null && currentBackend.getName().equals(name);

                if (isCurrent) {
                    // Green arrow for current server
                    children.add(createFormattedText("▸ " + name, "#55FF55", false, false));
                    children.add(createFormattedText(" (current)", "#AAAAAA", false, false));
                } else {
                    // Gray for other servers
                    children.add(createFormattedText("  " + name, "#AAAAAA", false, false));
                }

                if (isDefault) {
                    children.add(createFormattedText(" [default]", "#555555", false, false));
                }

                children.add(createFormattedText("\n", null, false, false));
            }

            // Footer with usage hint
            children.add(createFormattedText("\nUse ", "#AAAAAA", false, false));
            children.add(createFormattedText("/server <name>", "#FFAA00", false, false));
            children.add(createFormattedText(" to switch servers.", "#AAAAAA", false, false));

            sendFormattedServerMessage(session, children);
        }

        /**
         * Attempt to switch the player to a different server
         */
        private void switchServer(me.internalizable.numdrassl.session.ProxySession session, String serverName) {
            var proxyServer = session.getProxyServer();
            var config = proxyServer.getConfig();
            var targetBackend = config.getBackendByName(serverName);

            if (targetBackend == null) {
                java.util.List<com.hypixel.hytale.protocol.FormattedMessage> children = new java.util.ArrayList<>();
                children.add(createFormattedText("Server '", "#FF5555", false, false));
                children.add(createFormattedText(serverName, "#FFAA00", false, false));
                children.add(createFormattedText("' not found. Use ", "#FF5555", false, false));
                children.add(createFormattedText("/server", "#FFAA00", false, false));
                children.add(createFormattedText(" to see available servers.", "#FF5555", false, false));
                sendFormattedServerMessage(session, children);
                return;
            }

            var currentBackend = session.getCurrentBackend();
            if (currentBackend != null && currentBackend.getName().equalsIgnoreCase(serverName)) {
                java.util.List<com.hypixel.hytale.protocol.FormattedMessage> children = new java.util.ArrayList<>();
                children.add(createFormattedText("You are already connected to ", "#FF5555", false, false));
                children.add(createFormattedText(serverName, "#FFAA00", false, false));
                children.add(createFormattedText("!", "#FF5555", false, false));
                sendFormattedServerMessage(session, children);
                return;
            }

            // Send switching message to player
            java.util.List<com.hypixel.hytale.protocol.FormattedMessage> children = new java.util.ArrayList<>();
            children.add(createFormattedText("Connecting to ", "#AAAAAA", false, false));
            children.add(createFormattedText(serverName, "#55FF55", true, false));
            children.add(createFormattedText("...", "#AAAAAA", false, false));
            sendFormattedServerMessage(session, children);

            // Initiate server switch using PlayerTransfer
            LOGGER.info("Session {}: Player {} initiating transfer to server: {}",
                session.getSessionId(), session.getPlayerName(), serverName);

            me.internalizable.numdrassl.server.PlayerTransfer transfer =
                new me.internalizable.numdrassl.server.PlayerTransfer(proxyServer);

            transfer.transfer(session, targetBackend)
                .thenAccept(result -> {
                    if (!result.isSuccess()) {
                        LOGGER.warn("Session {}: Transfer failed: {}",
                            session.getSessionId(), result.getMessage());
                        java.util.List<com.hypixel.hytale.protocol.FormattedMessage> errorChildren = new java.util.ArrayList<>();
                        errorChildren.add(createFormattedText("Failed to switch servers: ", "#FF5555", false, false));
                        errorChildren.add(createFormattedText(result.getMessage(), "#AAAAAA", false, true));
                        sendFormattedServerMessage(session, errorChildren);
                    } else {
                        LOGGER.info("Session {}: Transfer initiated successfully", session.getSessionId());
                    }
                });
        }

        /**
         * Create a FormattedMessage with the specified text, color, and styling
         */
        private com.hypixel.hytale.protocol.FormattedMessage createFormattedText(
                String text, String hexColor, boolean bold, boolean italic) {
            return new com.hypixel.hytale.protocol.FormattedMessage(
                text,           // rawText
                null,           // messageId
                null,           // children
                null,           // params
                null,           // messageParams
                hexColor,       // color (hex string like "#FFAA00")
                bold ? com.hypixel.hytale.protocol.MaybeBool.True : com.hypixel.hytale.protocol.MaybeBool.Null,   // bold
                italic ? com.hypixel.hytale.protocol.MaybeBool.True : com.hypixel.hytale.protocol.MaybeBool.Null, // italic
                com.hypixel.hytale.protocol.MaybeBool.Null,  // monospace
                com.hypixel.hytale.protocol.MaybeBool.Null,  // underlined
                null,           // link
                false           // markupEnabled
            );
        }

        /**
         * Send a ServerMessage with multiple formatted children to the player
         */
        private void sendFormattedServerMessage(
                me.internalizable.numdrassl.session.ProxySession session,
                java.util.List<com.hypixel.hytale.protocol.FormattedMessage> children) {

            // Create parent FormattedMessage with children array
            com.hypixel.hytale.protocol.FormattedMessage formattedMessage =
                new com.hypixel.hytale.protocol.FormattedMessage(
                    null,           // rawText (null since we use children)
                    null,           // messageId
                    children.toArray(new com.hypixel.hytale.protocol.FormattedMessage[0]),  // children
                    null,           // params
                    null,           // messageParams
                    null,           // color
                    com.hypixel.hytale.protocol.MaybeBool.Null,  // bold
                    com.hypixel.hytale.protocol.MaybeBool.Null,  // italic
                    com.hypixel.hytale.protocol.MaybeBool.Null,  // monospace
                    com.hypixel.hytale.protocol.MaybeBool.Null,  // underlined
                    null,           // link
                    false           // markupEnabled
                );

            // Create the ServerMessage packet
            com.hypixel.hytale.protocol.packets.interface_.ServerMessage serverMessage =
                new com.hypixel.hytale.protocol.packets.interface_.ServerMessage(
                    com.hypixel.hytale.protocol.packets.interface_.ChatType.Chat,
                    formattedMessage
                );

            // Send it to the client
            session.sendToClient(serverMessage);
            LOGGER.debug("Sent formatted ServerMessage to session {}", session.getSessionId());
        }
    }
}
