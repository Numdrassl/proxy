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

public class FindCommand implements Command {
    @Nonnull
    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getDescription() {
        return "Shows which server a player is currently on";
    }

    @Override
    public String getUsage() {
        return "/find <player-name>";
    }

    @Nonnull
    @Override
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        ProxyServer proxy = Numdrassl.getProxy();

        // No arguments
        if (args.length == 0) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("Usage: " + this.getUsage()));
            return CommandResult.success();
        }

        String playerName = args[0];

        if (proxy.getPlayer(playerName).isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("That player could not be found."));
            return CommandResult.success();
        }

        Player player = proxy.getPlayer(playerName).get();
        if (player.getCurrentServer().isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Unable to find the player's server."));
            return CommandResult.success();
        }

        RegisteredServer currentServer = player.getCurrentServer().get();
        source.sendMessage(ChatMessageBuilder.create()
                .green(playerName + " is currently on " + currentServer.getName()));

        return CommandResult.success();
    }
}
