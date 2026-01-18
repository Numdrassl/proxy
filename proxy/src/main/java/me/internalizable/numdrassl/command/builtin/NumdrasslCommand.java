package me.internalizable.numdrassl.command.builtin;

import me.internalizable.numdrassl.api.Numdrassl;
import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.plugin.permission.FilePermissionProvider;
import me.internalizable.numdrassl.plugin.permission.NumdrasslPermissionManager;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * Main Numdrassl command with subcommands.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /numdrassl} - Show help</li>
 *   <li>{@code /numdrassl version} - Show version info</li>
 *   <li>{@code /numdrassl perm ...} - Permission management</li>
 * </ul>
 */
public class NumdrasslCommand implements Command {

    private static final String PERMISSION_BASE = "numdrassl.command.numdrassl";
    private static final String PERMISSION_PERM = "numdrassl.command.permission";

    @Override
    @Nonnull
    public String getName() {
        return "numdrassl";
    }

    @Override
    @Nonnull
    public String getDescription() {
        return "Numdrassl proxy management commands";
    }

    @Override
    @Nonnull
    public String getUsage() {
        return "/numdrassl <version|perm|reload> [args...]";
    }

    @Override
    @Nonnull
    public String getPermission() {
        return PERMISSION_BASE;
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        if (args.length == 0) {
            return showHelp(source);
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = dropFirst(args);

        return switch (subCommand) {
            case "version", "ver", "v" -> showVersion(source);
            case "perm", "perms", "permission", "permissions" -> handlePermissions(source, subArgs);
            case "reload" -> handleReload(source);
            default -> showHelp(source);
        };
    }

    // ==================== Version ====================

    private CommandResult showVersion(CommandSource source) {
        sendMessage(source, ChatMessageBuilder.create()
            .gold("Numdrassl Proxy ")
            .yellow("v" + Numdrassl.getProxy().getVersion()));
        sendMessage(source, ChatMessageBuilder.create()
            .gray("A QUIC reverse proxy for Hytale"));
        return CommandResult.success();
    }

    // ==================== Reload ====================

    private CommandResult handleReload(CommandSource source) {
        if (!source.hasPermission(PERMISSION_PERM)) {
            return CommandResult.noPermission();
        }

        var manager = Numdrassl.getProxy().getPermissionManager();
        if (manager instanceof NumdrasslPermissionManager npm) {
            FilePermissionProvider provider = npm.getFileProvider();
            provider.onUnregister();
            provider.onRegister();

            sendMessage(source, ChatMessageBuilder.create()
                .green("Numdrassl configuration reloaded."));
            return CommandResult.success();
        }

        return CommandResult.failure("Cannot reload - custom permission provider in use");
    }

    // ==================== Permission Subcommand ====================

    private CommandResult handlePermissions(CommandSource source, String[] args) {
        if (!source.hasPermission(PERMISSION_PERM)) {
            return CommandResult.noPermission();
        }

        if (args.length == 0) {
            return showPermHelp(source);
        }

        String action = args[0].toLowerCase();
        String[] actionArgs = dropFirst(args);

        return switch (action) {
            case "user", "player" -> handleUser(source, actionArgs);
            case "group" -> handleGroup(source, actionArgs);
            case "reload" -> handlePermReload(source);
            default -> showPermHelp(source);
        };
    }

    // ==================== User Commands ====================

    private CommandResult handleUser(CommandSource source, String[] args) {
        if (args.length < 2) {
            sendMessage(source, ChatMessageBuilder.create()
                .yellow("Usage: ")
                .white("/numdrassl perm user <player> <info|add|remove|addgroup|removegroup> [args]"));
            return CommandResult.success();
        }

        String playerName = args[0];
        String action = args[1].toLowerCase();

        Player player = Numdrassl.getProxy().getPlayer(playerName).orElse(null);
        UUID uuid;

        if (player != null) {
            uuid = player.getUniqueId();
        } else {
            try {
                uuid = UUID.fromString(playerName);
            } catch (IllegalArgumentException e) {
                sendMessage(source, ChatMessageBuilder.create()
                    .red("Player '")
                    .yellow(playerName)
                    .red("' not found. Use UUID for offline players."));
                return CommandResult.failure("Player not found");
            }
        }

        FilePermissionProvider provider = getFileProvider();
        if (provider == null) {
            return CommandResult.failure("File permission provider not available");
        }

        String[] actionArgs = dropFirst(dropFirst(args));

        return switch (action) {
            case "info" -> showUserInfo(source, uuid, playerName, provider);
            case "add" -> addUserPermission(source, uuid, playerName, actionArgs, provider);
            case "remove" -> removeUserPermission(source, uuid, playerName, actionArgs, provider);
            case "addgroup" -> addUserToGroup(source, uuid, playerName, actionArgs, provider);
            case "removegroup" -> removeUserFromGroup(source, uuid, playerName, actionArgs, provider);
            default -> {
                sendMessage(source, ChatMessageBuilder.create()
                    .red("Unknown action: ")
                    .yellow(action));
                yield CommandResult.failure("Unknown action");
            }
        };
    }

    private CommandResult showUserInfo(CommandSource source, UUID uuid, String name, FilePermissionProvider provider) {
        Set<String> groups = provider.getPlayerGroups(uuid);

        sendMessage(source, ChatMessageBuilder.create()
            .gold("--- Permission Info for ")
            .yellow(name)
            .gold(" ---"));

        sendMessage(source, ChatMessageBuilder.create()
            .gray("UUID: ")
            .white(uuid.toString()));

        sendMessage(source, ChatMessageBuilder.create()
            .gray("Groups: ")
            .green(String.join(", ", groups)));

        return CommandResult.success();
    }

    private CommandResult addUserPermission(CommandSource source, UUID uuid, String name,
                                            String[] args, FilePermissionProvider provider) {
        if (args.length < 1) {
            sendMessage(source, ChatMessageBuilder.create()
                .red("Usage: /numdrassl perm user ")
                .yellow(name)
                .red(" add <permission>"));
            return CommandResult.failure("Missing permission");
        }

        String permission = args[0];
        boolean deny = permission.startsWith("-");
        if (deny) {
            permission = permission.substring(1);
        }

        provider.setPlayerPermission(uuid, permission, !deny);

        sendMessage(source, ChatMessageBuilder.create()
            .green(deny ? "Denied" : "Granted")
            .gray(" permission ")
            .yellow(permission)
            .gray(" to ")
            .white(name));

        return CommandResult.success();
    }

    private CommandResult removeUserPermission(CommandSource source, UUID uuid, String name,
                                               String[] args, FilePermissionProvider provider) {
        if (args.length < 1) {
            sendMessage(source, ChatMessageBuilder.create()
                .red("Usage: /numdrassl perm user ")
                .yellow(name)
                .red(" remove <permission>"));
            return CommandResult.failure("Missing permission");
        }

        String permission = args[0];
        provider.removePlayerPermission(uuid, permission);

        sendMessage(source, ChatMessageBuilder.create()
            .green("Removed")
            .gray(" permission ")
            .yellow(permission)
            .gray(" from ")
            .white(name));

        return CommandResult.success();
    }

    private CommandResult addUserToGroup(CommandSource source, UUID uuid, String name,
                                         String[] args, FilePermissionProvider provider) {
        if (args.length < 1) {
            sendMessage(source, ChatMessageBuilder.create()
                .red("Usage: /numdrassl perm user ")
                .yellow(name)
                .red(" addgroup <group>"));
            return CommandResult.failure("Missing group");
        }

        String group = args[0];
        provider.addPlayerToGroup(uuid, group);

        sendMessage(source, ChatMessageBuilder.create()
            .green("Added ")
            .white(name)
            .green(" to group ")
            .yellow(group));

        return CommandResult.success();
    }

    private CommandResult removeUserFromGroup(CommandSource source, UUID uuid, String name,
                                              String[] args, FilePermissionProvider provider) {
        if (args.length < 1) {
            sendMessage(source, ChatMessageBuilder.create()
                .red("Usage: /numdrassl perm user ")
                .yellow(name)
                .red(" removegroup <group>"));
            return CommandResult.failure("Missing group");
        }

        String group = args[0];
        provider.removePlayerFromGroup(uuid, group);

        sendMessage(source, ChatMessageBuilder.create()
            .green("Removed ")
            .white(name)
            .green(" from group ")
            .yellow(group));

        return CommandResult.success();
    }

    // ==================== Group Commands ====================

    private CommandResult handleGroup(CommandSource source, String[] args) {
        if (args.length < 2) {
            sendMessage(source, ChatMessageBuilder.create()
                .yellow("Usage: ")
                .white("/numdrassl perm group <group> <add|remove> <permission>"));
            return CommandResult.success();
        }

        String group = args[0];
        String action = args[1].toLowerCase();

        FilePermissionProvider provider = getFileProvider();
        if (provider == null) {
            return CommandResult.failure("File permission provider not available");
        }

        String[] actionArgs = dropFirst(dropFirst(args));

        return switch (action) {
            case "add" -> addGroupPermission(source, group, actionArgs, provider);
            case "remove" -> removeGroupPermission(source, group, actionArgs, provider);
            default -> {
                sendMessage(source, ChatMessageBuilder.create()
                    .red("Unknown action: ")
                    .yellow(action)
                    .red(". Use 'add' or 'remove'."));
                yield CommandResult.failure("Unknown action");
            }
        };
    }

    private CommandResult addGroupPermission(CommandSource source, String group,
                                             String[] args, FilePermissionProvider provider) {
        if (args.length < 1) {
            sendMessage(source, ChatMessageBuilder.create()
                .red("Usage: /numdrassl perm group ")
                .yellow(group)
                .red(" add <permission>"));
            return CommandResult.failure("Missing permission");
        }

        String permission = args[0];
        boolean deny = permission.startsWith("-");
        if (deny) {
            permission = permission.substring(1);
        }

        provider.setGroupPermission(group, permission, !deny);

        sendMessage(source, ChatMessageBuilder.create()
            .green(deny ? "Denied" : "Granted")
            .gray(" permission ")
            .yellow(permission)
            .gray(" to group ")
            .white(group));

        return CommandResult.success();
    }

    private CommandResult removeGroupPermission(CommandSource source, String group,
                                                String[] args, FilePermissionProvider provider) {
        if (args.length < 1) {
            sendMessage(source, ChatMessageBuilder.create()
                .red("Usage: /numdrassl perm group ")
                .yellow(group)
                .red(" remove <permission>"));
            return CommandResult.failure("Missing permission");
        }

        String permission = args[0];
        provider.removeGroupPermission(group, permission);

        sendMessage(source, ChatMessageBuilder.create()
            .green("Removed")
            .gray(" permission ")
            .yellow(permission)
            .gray(" from group ")
            .white(group));

        return CommandResult.success();
    }

    // ==================== Permission Reload ====================

    private CommandResult handlePermReload(CommandSource source) {
        var manager = Numdrassl.getProxy().getPermissionManager();
        if (manager instanceof NumdrasslPermissionManager npm) {
            FilePermissionProvider provider = npm.getFileProvider();
            provider.onUnregister();
            provider.onRegister();

            sendMessage(source, ChatMessageBuilder.create()
                .green("Permissions reloaded from disk."));
            return CommandResult.success();
        }

        return CommandResult.failure("Cannot reload - custom permission provider in use");
    }

    // ==================== Help ====================

    private CommandResult showHelp(CommandSource source) {
        sendMessage(source, ChatMessageBuilder.create()
            .gold("--- Numdrassl Commands ---"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl version")
            .gray(" - Show version info"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm ...")
            .gray(" - Permission management"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl reload")
            .gray(" - Reload configuration"));

        return CommandResult.success();
    }

    private CommandResult showPermHelp(CommandSource source) {
        sendMessage(source, ChatMessageBuilder.create()
            .gold("--- Permission Commands ---"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm user <player> info")
            .gray(" - Show player info"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm user <player> add <perm>")
            .gray(" - Grant permission"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm user <player> remove <perm>")
            .gray(" - Remove permission"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm user <player> addgroup <group>")
            .gray(" - Add to group"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm user <player> removegroup <group>")
            .gray(" - Remove from group"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm group <group> add <perm>")
            .gray(" - Add group permission"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm group <group> remove <perm>")
            .gray(" - Remove group permission"));
        sendMessage(source, ChatMessageBuilder.create()
            .yellow("/numdrassl perm reload")
            .gray(" - Reload from disk"));

        return CommandResult.success();
    }

    // ==================== Helpers ====================

    private FilePermissionProvider getFileProvider() {
        var manager = Numdrassl.getProxy().getPermissionManager();
        if (manager instanceof NumdrasslPermissionManager npm) {
            return npm.getFileProvider();
        }
        return null;
    }

    private void sendMessage(CommandSource source, ChatMessageBuilder builder) {
        source.sendMessage(builder);
    }

    private String[] dropFirst(String[] arr) {
        if (arr.length <= 1) {
            return new String[0];
        }
        String[] result = new String[arr.length - 1];
        System.arraycopy(arr, 1, result, 0, result.length);
        return result;
    }
}

