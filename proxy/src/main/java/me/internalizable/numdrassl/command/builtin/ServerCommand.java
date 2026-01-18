package me.internalizable.numdrassl.command.builtin;

import me.internalizable.numdrassl.api.Numdrassl;
import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

/**
 * Built-in server command for listing available servers and transferring players.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /server} - Lists all available servers</li>
 *   <li>{@code /server <name>} - Transfers the player to the specified server</li>
 * </ul>
 */
public class ServerCommand implements Command {

    @Override
    @Nonnull
    public String getName() {
        return "server";
    }

    @Override
    public String getDescription() {
        return "List servers or transfer to a server";
    }

    @Override
    public String getUsage() {
        return "/server [name]";
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        ProxyServer proxy = Numdrassl.getProxy();
        if (proxy == null) {
            return CommandResult.failure("Proxy not initialized");
        }

        // No arguments - list servers
        if (args.length == 0) {
            return listServers(source, proxy);
        }

        // With argument - transfer to server
        return transferToServer(source, proxy, args[0]);
    }

    private CommandResult listServers(CommandSource source, ProxyServer proxy) {
        Collection<RegisteredServer> servers = proxy.getAllServers();

        if (servers.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("No servers available."));
            return CommandResult.success();
        }

        // Header
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold(">> ")
                .yellow("Available Servers")
                .gold(" <<"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        // Get current server for player (if applicable)
        Optional<RegisteredServer> currentServer = source.asPlayer()
                .flatMap(Player::getCurrentServer);

        for (RegisteredServer server : servers) {
            String name = server.getName();
            int playerCount = server.getPlayerCount();
            boolean isCurrent = currentServer.isPresent()
                    && currentServer.get().getName().equalsIgnoreCase(name);

            ChatMessageBuilder line = ChatMessageBuilder.create();

            if (isCurrent) {
                line.green("  > ")
                    .green(name)
                    .darkGray(" (")
                    .aqua(String.valueOf(playerCount))
                    .darkGray(playerCount == 1 ? " player" : " players")
                    .darkGray(") ")
                    .green("[current]");
            } else {
                line.gray("  - ")
                    .white(name)
                    .darkGray(" (")
                    .yellow(String.valueOf(playerCount))
                    .darkGray(playerCount == 1 ? " player" : " players")
                    .darkGray(")");
            }

            source.sendMessage(line);
        }

        // Footer
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        if (source.asPlayer().isPresent()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .gray("  Tip: Use ")
                    .yellow("/server <name>")
                    .gray(" to switch."));
        }

        return CommandResult.success();
    }

    private CommandResult transferToServer(CommandSource source, ProxyServer proxy, String serverName) {
        // Only players can transfer
        Optional<Player> playerOpt = source.asPlayer();
        if (playerOpt.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Only players can switch servers."));
            return CommandResult.failure("Only players can switch servers");
        }

        Player player = playerOpt.get();

        // Check if server exists
        Optional<RegisteredServer> serverOpt = proxy.getServer(serverName);
        if (serverOpt.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Unknown server: ")
                    .red(serverName));
            source.sendMessage(ChatMessageBuilder.create()
                    .gray("  Use ")
                    .yellow("/server")
                    .gray(" to see available servers."));
            return CommandResult.failure("Unknown server: " + serverName);
        }

        RegisteredServer targetServer = serverOpt.get();

        // Check if already on that server
        Optional<RegisteredServer> currentServer = player.getCurrentServer();
        if (currentServer.isPresent() && currentServer.get().getName().equalsIgnoreCase(serverName)) {
            source.sendMessage(ChatMessageBuilder.create()
                    .yellow("[!] ")
                    .gray("You are already connected to ")
                    .yellow(serverName)
                    .gray("."));
            return CommandResult.failure("Already connected to " + serverName);
        }

        // Initiate transfer
        source.sendMessage(ChatMessageBuilder.create()
                .gold("[*] ")
                .gray("Connecting to ")
                .green(targetServer.getName())
                .gray("..."));

        player.transfer(targetServer).thenAccept(result -> {
            if (!result.isSuccess()) {
                player.sendMessage(ChatMessageBuilder.create()
                        .red("[X] ")
                        .gray("Transfer failed: ")
                        .red(result.getMessage()));
            }
        });

        return CommandResult.success();
    }
}

