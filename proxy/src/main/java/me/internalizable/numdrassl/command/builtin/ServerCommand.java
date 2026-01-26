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
        return "/server <server-name> [target-player]";
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

        // Has target-player argument - transfer target to server
        if (args.length > 1) {
            // Find target player
            Optional<Player> targetOpt = proxy.getPlayer(args[1]);
            if (targetOpt.isEmpty()) {
                source.sendMessage(ChatMessageBuilder.create()
                        .red("[X] ")
                        .gray("Player is not online: ")
                        .red(args[1]));
                return CommandResult.success();
            }

            // Console OR player can execute this
            return transferToServerInternal(source, targetOpt.get(), proxy, args[0]);
        }

        // Only players can use this version
        Optional<Player> playerOpt = source.asPlayer();
        if (playerOpt.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Only players can switch servers."));
            return CommandResult.success();
        }

        return transferToServerInternal(source, playerOpt.get(), proxy, args[0]);
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
                    .yellow("/server <server-name> [target-player]")
                    .gray(" to switch."));
        }

        return CommandResult.success();
    }

    private CommandResult transferToServer(CommandSource source, ProxyServer proxy, String serverName) {
        // Only players can use /server <server>
        Optional<Player> playerOpt = source.asPlayer();
        if (playerOpt.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Only players can switch servers."));
            return CommandResult.success();
        }

        // Transfer the executing player
        return transferToServerInternal(source, playerOpt.get(), proxy, serverName);
    }

    private CommandResult transferToServerInternal(CommandSource sender, Player targetPlayer, ProxyServer proxy, String serverName) {
        // Check if the target server exists
        Optional<RegisteredServer> serverOpt = proxy.getServer(serverName);
        if (serverOpt.isEmpty()) {
            sender.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Unknown server: ")
                    .red(serverName));
            sender.sendMessage(ChatMessageBuilder.create()
                    .gray("  Use ")
                    .yellow("/server")
                    .gray(" to see available servers."));
            return CommandResult.success();
        }

        RegisteredServer targetServer = serverOpt.get();

        // Check if the target player is already connected to that server
        Optional<RegisteredServer> currentServer = targetPlayer.getCurrentServer();
        if (currentServer.isPresent() && currentServer.get().getName().equalsIgnoreCase(serverName)) {
            sender.sendMessage(ChatMessageBuilder.create()
                    .yellow("[!] ")
                    .gray("Player ")
                    .yellow(targetPlayer.getUsername())
                    .gray(" is already connected to ")
                    .yellow(serverName)
                    .gray("."));
            return CommandResult.success();
        }

        // Inform the sender that the transfer is starting
        sender.sendMessage(ChatMessageBuilder.create()
                .gold("[*] ")
                .gray("Connecting ")
                .yellow(targetPlayer.getUsername())
                .gray(" to ")
                .green(targetServer.getName())
                .gray("..."));

        // Start the server transfer
        targetPlayer.transfer(targetServer).thenAccept(result -> {
            if (!result.isSuccess()) {
                // Transfer failed -> notify the sender
                sender.sendMessage(ChatMessageBuilder.create()
                        .red("[X] ")
                        .gray("Transfer failed: ")
                        .red(result.getMessage()));
            }
        });

        return CommandResult.success();
    }
}

