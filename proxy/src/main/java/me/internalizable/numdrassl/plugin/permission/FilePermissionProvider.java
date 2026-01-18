package me.internalizable.numdrassl.plugin.permission;

import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.PermissionProvider;
import me.internalizable.numdrassl.api.permission.Tristate;
import me.internalizable.numdrassl.api.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default file-based permission provider for Numdrassl using YAML format.
 *
 * <p>Stores player permissions and groups in YAML files on disk.</p>
 *
 * <h2>File Structure</h2>
 * <pre>
 * permissions/
 *   players/
 *     {uuid}.yml          - Individual player permissions
 *   groups/
 *     default.yml         - Default group (all players inherit)
 *     admin.yml           - Admin group
 *     ...
 *   player-groups.yml     - Maps players to groups
 * </pre>
 *
 * <h2>Player File Format (players/{uuid}.yml)</h2>
 * <pre>
 * permissions:
 *   - numdrassl.command.server
 *   - numdrassl.command.help
 *   - -numdrassl.command.stop    # Negated permission (explicit deny)
 * </pre>
 *
 * <h2>Group File Format (groups/{name}.yml)</h2>
 * <pre>
 * name: default
 * default: true
 * permissions:
 *   - numdrassl.command.help
 *   - numdrassl.command.server
 * </pre>
 *
 * <h2>Player Groups File (player-groups.yml)</h2>
 * <pre>
 * players:
 *   550e8400-e29b-41d4-a716-446655440000:
 *     - admin
 *     - vip
 *   6ba7b810-9dad-11d1-80b4-00c04fd430c8:
 *     - moderator
 * </pre>
 *
 * <p>This provider is intended as a simple default. For production use,
 * consider using LuckPerms or another full-featured permission plugin.</p>
 */
public final class FilePermissionProvider implements PermissionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilePermissionProvider.class);
    private static final String PLAYERS_DIR = "players";
    private static final String GROUPS_DIR = "groups";
    private static final String PLAYER_GROUPS_FILE = "player-groups.yml";
    private static final String DEFAULT_GROUP = "default";

    private final Path dataDirectory;
    private final Yaml yaml;
    private final Map<String, GroupData> groupPermissions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerGroups = new ConcurrentHashMap<>();
    private final Map<UUID, Set<PermissionEntry>> playerPermissionCache = new ConcurrentHashMap<>();

    /**
     * Creates a new file permission provider.
     *
     * @param dataDirectory the directory to store permission files
     */
    public FilePermissionProvider(@Nonnull Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
            .resolve("permissions");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        this.yaml = new Yaml(options);
    }

    // ==================== PermissionProvider Implementation ====================

    @Override
    public void onRegister() {
        try {
            initializeDirectories();
            loadGroups();
            loadPlayerGroups();
            LOGGER.info("File permission provider initialized at: {}", dataDirectory);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize file permission provider", e);
        }
    }

    @Override
    public void onUnregister() {
        groupPermissions.clear();
        playerGroups.clear();
        playerPermissionCache.clear();
        LOGGER.info("File permission provider unregistered");
    }

    @Override
    @Nonnull
    public PermissionFunction createFunction(@Nonnull Player player) {
        Objects.requireNonNull(player, "player");
        UUID uuid = player.getUniqueId();

        // Load player permissions if not cached
        if (!playerPermissionCache.containsKey(uuid)) {
            loadPlayerPermissions(uuid);
        }

        return permission -> checkPermission(uuid, permission);
    }

    // ==================== Permission Checking ====================

    private Tristate checkPermission(UUID uuid, String permission) {
        // Check player-specific permissions first
        Set<PermissionEntry> playerPerms = playerPermissionCache.get(uuid);
        if (playerPerms != null) {
            Tristate result = checkInSet(playerPerms, permission);
            if (result != Tristate.UNDEFINED) {
                return result;
            }
        }

        // Check group permissions
        Set<String> groups = playerGroups.getOrDefault(uuid, Set.of(DEFAULT_GROUP));
        for (String group : groups) {
            GroupData groupData = groupPermissions.get(group);
            if (groupData != null) {
                Tristate result = checkInSet(groupData.permissions(), permission);
                if (result != Tristate.UNDEFINED) {
                    return result;
                }
            }
        }

        // Check default group
        if (!groups.contains(DEFAULT_GROUP)) {
            GroupData defaultGroup = groupPermissions.get(DEFAULT_GROUP);
            if (defaultGroup != null) {
                Tristate result = checkInSet(defaultGroup.permissions(), permission);
                if (result != Tristate.UNDEFINED) {
                    return result;
                }
            }
        }

        return Tristate.UNDEFINED;
    }

    private Tristate checkInSet(Set<PermissionEntry> entries, String permission) {
        // Check exact match
        for (PermissionEntry entry : entries) {
            if (entry.permission().equals(permission)) {
                return entry.value();
            }
        }

        // Check wildcard permissions (e.g., "numdrassl.command.*")
        for (PermissionEntry entry : entries) {
            if (entry.permission().endsWith(".*")) {
                String prefix = entry.permission().substring(0, entry.permission().length() - 1);
                if (permission.startsWith(prefix)) {
                    return entry.value();
                }
            }
            // Check full wildcard
            if (entry.permission().equals("*")) {
                return entry.value();
            }
        }

        return Tristate.UNDEFINED;
    }

    // ==================== Permission Management ====================

    /**
     * Sets a permission for a player.
     *
     * @param uuid the player's UUID
     * @param permission the permission string
     * @param value the permission value (true = grant, false = deny)
     */
    public void setPlayerPermission(@Nonnull UUID uuid, @Nonnull String permission, boolean value) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(permission, "permission");

        Set<PermissionEntry> perms = playerPermissionCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        // Remove existing entry for this permission
        perms.removeIf(e -> e.permission().equals(permission));

        // Add new entry
        perms.add(new PermissionEntry(permission, Tristate.fromBoolean(value)));

        // Save to disk
        savePlayerPermissions(uuid);
    }

    /**
     * Removes a permission from a player.
     *
     * @param uuid the player's UUID
     * @param permission the permission to remove
     */
    public void removePlayerPermission(@Nonnull UUID uuid, @Nonnull String permission) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(permission, "permission");

        Set<PermissionEntry> perms = playerPermissionCache.get(uuid);
        if (perms != null) {
            perms.removeIf(e -> e.permission().equals(permission));
            savePlayerPermissions(uuid);
        }
    }

    /**
     * Adds a player to a group.
     *
     * @param uuid the player's UUID
     * @param group the group name
     */
    public void addPlayerToGroup(@Nonnull UUID uuid, @Nonnull String group) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(group, "group");

        playerGroups.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(group);
        savePlayerGroups();
    }

    /**
     * Removes a player from a group.
     *
     * @param uuid the player's UUID
     * @param group the group name
     */
    public void removePlayerFromGroup(@Nonnull UUID uuid, @Nonnull String group) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(group, "group");

        Set<String> groups = playerGroups.get(uuid);
        if (groups != null) {
            groups.remove(group);
            savePlayerGroups();
        }
    }

    /**
     * Gets the groups a player belongs to.
     *
     * @param uuid the player's UUID
     * @return unmodifiable set of group names
     */
    @Nonnull
    public Set<String> getPlayerGroups(@Nonnull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Set<String> groups = playerGroups.get(uuid);
        return groups != null ? Collections.unmodifiableSet(groups) : Set.of(DEFAULT_GROUP);
    }

    /**
     * Sets a permission for a group.
     *
     * @param group the group name
     * @param permission the permission string
     * @param value the permission value
     */
    public void setGroupPermission(@Nonnull String group, @Nonnull String permission, boolean value) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(permission, "permission");

        GroupData data = groupPermissions.computeIfAbsent(group,
            k -> new GroupData(k, k.equals(DEFAULT_GROUP), ConcurrentHashMap.newKeySet()));

        data.permissions().removeIf(e -> e.permission().equals(permission));
        data.permissions().add(new PermissionEntry(permission, Tristate.fromBoolean(value)));
        saveGroup(group);
    }

    /**
     * Removes a permission from a group.
     *
     * @param group the group name
     * @param permission the permission to remove
     */
    public void removeGroupPermission(@Nonnull String group, @Nonnull String permission) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(permission, "permission");

        GroupData data = groupPermissions.get(group);
        if (data != null) {
            data.permissions().removeIf(e -> e.permission().equals(permission));
            saveGroup(group);
        }
    }

    // ==================== File I/O ====================

    private void initializeDirectories() throws IOException {
        Files.createDirectories(dataDirectory.resolve(PLAYERS_DIR));
        Files.createDirectories(dataDirectory.resolve(GROUPS_DIR));

        // Create default group if it doesn't exist
        Path defaultGroupFile = dataDirectory.resolve(GROUPS_DIR).resolve(DEFAULT_GROUP + ".yml");
        if (!Files.exists(defaultGroupFile)) {
            Map<String, Object> defaultGroupData = new LinkedHashMap<>();
            defaultGroupData.put("name", DEFAULT_GROUP);
            defaultGroupData.put("default", true);
            defaultGroupData.put("permissions", List.of(
                "numdrassl.command.help",
                "numdrassl.command.server"
            ));

            try (Writer writer = Files.newBufferedWriter(defaultGroupFile)) {
                yaml.dump(defaultGroupData, writer);
            }
            LOGGER.info("Created default group with basic permissions");
        }
    }

    private void loadGroups() {
        try {
            Path groupsDir = dataDirectory.resolve(GROUPS_DIR);
            if (!Files.exists(groupsDir)) return;

            Files.list(groupsDir)
                .filter(p -> p.toString().endsWith(".yml"))
                .forEach(this::loadGroupFile);
        } catch (IOException e) {
            LOGGER.error("Failed to load groups", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadGroupFile(Path file) {
        String groupName = file.getFileName().toString().replace(".yml", "");
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            if (data == null) {
                data = new HashMap<>();
            }

            String name = (String) data.getOrDefault("name", groupName);
            boolean isDefault = Boolean.TRUE.equals(data.get("default"));
            List<String> permList = (List<String>) data.getOrDefault("permissions", List.of());

            Set<PermissionEntry> perms = ConcurrentHashMap.newKeySet();
            for (String perm : permList) {
                if (perm.startsWith("-")) {
                    perms.add(new PermissionEntry(perm.substring(1), Tristate.FALSE));
                } else {
                    perms.add(new PermissionEntry(perm, Tristate.TRUE));
                }
            }

            groupPermissions.put(name, new GroupData(name, isDefault, perms));
            LOGGER.debug("Loaded group '{}' with {} permissions", name, perms.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load group file: {}", file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPlayerGroups() {
        Path file = dataDirectory.resolve(PLAYER_GROUPS_FILE);
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            if (data == null) return;

            Map<String, List<String>> players = (Map<String, List<String>>) data.get("players");
            if (players == null) return;

            for (Map.Entry<String, List<String>> entry : players.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    Set<String> groupSet = ConcurrentHashMap.newKeySet();
                    groupSet.addAll(entry.getValue());
                    playerGroups.put(uuid, groupSet);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid UUID in player-groups.yml: {}", entry.getKey());
                }
            }
            LOGGER.debug("Loaded {} player group assignments", playerGroups.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load player groups", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPlayerPermissions(UUID uuid) {
        Path file = dataDirectory.resolve(PLAYERS_DIR).resolve(uuid + ".yml");
        if (!Files.exists(file)) {
            playerPermissionCache.put(uuid, ConcurrentHashMap.newKeySet());
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            Set<PermissionEntry> perms = ConcurrentHashMap.newKeySet();

            if (data != null) {
                List<String> permList = (List<String>) data.getOrDefault("permissions", List.of());
                for (String perm : permList) {
                    if (perm.startsWith("-")) {
                        perms.add(new PermissionEntry(perm.substring(1), Tristate.FALSE));
                    } else {
                        perms.add(new PermissionEntry(perm, Tristate.TRUE));
                    }
                }
            }

            playerPermissionCache.put(uuid, perms);
            LOGGER.debug("Loaded {} permissions for player {}", perms.size(), uuid);
        } catch (IOException e) {
            LOGGER.error("Failed to load permissions for player {}", uuid, e);
            playerPermissionCache.put(uuid, ConcurrentHashMap.newKeySet());
        }
    }

    private void savePlayerPermissions(UUID uuid) {
        Path file = dataDirectory.resolve(PLAYERS_DIR).resolve(uuid + ".yml");
        Set<PermissionEntry> perms = playerPermissionCache.get(uuid);
        if (perms == null) return;

        try {
            List<String> permList = new ArrayList<>();
            for (PermissionEntry entry : perms) {
                if (entry.value() == Tristate.FALSE) {
                    permList.add("-" + entry.permission());
                } else if (entry.value() == Tristate.TRUE) {
                    permList.add(entry.permission());
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("permissions", permList);

            try (Writer writer = Files.newBufferedWriter(file)) {
                yaml.dump(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save permissions for player {}", uuid, e);
        }
    }

    private void savePlayerGroups() {
        Path file = dataDirectory.resolve(PLAYER_GROUPS_FILE);

        try {
            Map<String, List<String>> players = new LinkedHashMap<>();
            for (Map.Entry<UUID, Set<String>> entry : playerGroups.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    players.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("players", players);

            try (Writer writer = Files.newBufferedWriter(file)) {
                yaml.dump(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save player groups", e);
        }
    }

    private void saveGroup(String group) {
        Path file = dataDirectory.resolve(GROUPS_DIR).resolve(group + ".yml");
        GroupData data = groupPermissions.get(group);
        if (data == null) return;

        try {
            List<String> permList = new ArrayList<>();
            for (PermissionEntry entry : data.permissions()) {
                if (entry.value() == Tristate.FALSE) {
                    permList.add("-" + entry.permission());
                } else if (entry.value() == Tristate.TRUE) {
                    permList.add(entry.permission());
                }
            }

            Map<String, Object> yamlData = new LinkedHashMap<>();
            yamlData.put("name", data.name());
            yamlData.put("default", data.isDefault());
            yamlData.put("permissions", permList);

            try (Writer writer = Files.newBufferedWriter(file)) {
                yaml.dump(yamlData, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save group {}", group, e);
        }
    }

    // ==================== Internal Records ====================

    private record PermissionEntry(String permission, Tristate value) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PermissionEntry that)) return false;
            return permission.equals(that.permission);
        }

        @Override
        public int hashCode() {
            return permission.hashCode();
        }
    }

    private record GroupData(String name, boolean isDefault, Set<PermissionEntry> permissions) {}
}

