package me.internalizable.numdrassl;

import me.internalizable.numdrassl.api.ProxyAPI;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.command.ConsoleCommandSource;
import me.internalizable.numdrassl.command.NumdrasslCommandManager;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.server.ProxyCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * Main entry point for the Numdrassl proxy server.
 *
 * <p>Numdrassl is a QUIC reverse proxy for Hytale, similar to BungeeCord/Velocity
 * for Minecraft. It provides full packet inspection and modification capabilities.</p>
 */
public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final Path CONFIG_PATH = Paths.get("config", "proxy.yml");

    private static ProxyCore proxyCore;

    private Main() {}

    public static void main(String[] args) {
        printBanner();

        try {
            initialize();
            registerShutdownHook();
            start();
            awaitTermination();
        } catch (Exception e) {
            LOGGER.error("Fatal error during proxy startup", e);
            System.exit(1);
        }
    }

    private static void printBanner() {
        LOGGER.info("===========================================");
        LOGGER.info("  Numdrassl - Hytale QUIC Proxy Server");
        LOGGER.info("===========================================");
        LOGGER.info("");
        LOGGER.info("NOTE: Backend servers require the Bridge plugin.");
        LOGGER.info("");
    }

    private static void initialize() throws Exception {
        ProxyConfig config = ProxyConfig.load(CONFIG_PATH);
        LOGGER.info("Configuration loaded from: {}", CONFIG_PATH);

        proxyCore = new ProxyCore(config);
        ProxyAPI.init(proxyCore);
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown signal received");
            shutdown();
        }, "Shutdown-Hook"));
    }

    private static void start() throws Exception {
        proxyCore.start();
        startConsoleHandler();
    }

    private static void shutdown() {
        if (proxyCore != null && proxyCore.isRunning()) {
            proxyCore.stop();
        }
    }

    private static void awaitTermination() throws InterruptedException {
        Thread.currentThread().join();
    }

    private static void startConsoleHandler() {
        Thread consoleThread = new Thread(Main::runConsoleLoop, "Console-Handler");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private static void runConsoleLoop() {
        try (Scanner scanner = new Scanner(System.in)) {
            LOGGER.info("Console ready. Type 'help' for available commands.");

            while (isRunning()) {
                if (!scanner.hasNextLine()) {
                    sleep(100);
                    continue;
                }

                String input = scanner.nextLine().trim();
                if (!input.isEmpty()) {
                    executeCommand(input);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Console handler error", e);
        }
    }

    private static boolean isRunning() {
        return proxyCore != null && proxyCore.isRunning();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void executeCommand(String commandLine) {
        NumdrasslCommandManager commandManager =
                (NumdrasslCommandManager) proxyCore.getApiProxy().getCommandManager();

        CommandResult result = commandManager.execute(ConsoleCommandSource.getInstance(), commandLine);

        if (!result.isSuccess() && result.getMessage() != null) {
            if (result.getStatus() == CommandResult.Status.NOT_FOUND) {
                LOGGER.warn("Unknown command. Type 'help' for available commands.");
            } else {
                LOGGER.warn(result.getMessage());
            }
        }
    }

    /**
     * Get the running proxy core instance.
     *
     * @return the proxy core, or null if not started
     */
    public static ProxyCore getProxyCore() {
        return proxyCore;
    }
}
