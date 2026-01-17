package me.internalizable.numdrassl.command.builtin;

import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.auth.ProxyAuthenticator;
import me.internalizable.numdrassl.server.ProxyCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Built-in auth command for proxy authentication management.
 */
public class AuthCommand implements Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthCommand.class);
    private final ProxyCore proxyCore;

    public AuthCommand(ProxyCore proxyCore) {
        this.proxyCore = proxyCore;
    }

    @Override
    @Nonnull
    public String getName() {
        return "auth";
    }

    @Override
    public String getDescription() {
        return "Manage proxy authentication with Hytale";
    }

    @Override
    public String getUsage() {
        return "auth <login|status|secret>";
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        if (args.length < 1) {
            source.sendMessage("Usage: " + getUsage());
            return CommandResult.success();
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "login":
                return handleLogin(source);
            case "status":
                return handleStatus(source);
            case "secret":
                return handleSecret(source);
            default:
                source.sendMessage("Usage: " + getUsage());
                return CommandResult.success();
        }
    }

    private CommandResult handleLogin(CommandSource source) {
        ProxyAuthenticator authenticator = proxyCore.getAuthenticator();
        if (authenticator == null) {
            source.sendMessage("Authenticator not available");
            return CommandResult.failure("Authenticator not available");
        }

        if (authenticator.isAuthenticated()) {
            source.sendMessage("Proxy is already authenticated as: " +
                authenticator.getProfileUsername() + " (" + authenticator.getProfileUuid() + ")");
            return CommandResult.success();
        }

        source.sendMessage("Starting OAuth device code flow...");
        authenticator.startDeviceCodeFlow().thenAccept(deviceCode -> {
            if (deviceCode == null) {
                source.sendMessage("Failed to start device code flow");
                return;
            }

            source.sendMessage("===========================================");
            source.sendMessage("  To authenticate, visit:");
            source.sendMessage("  " + deviceCode.verificationUri());
            source.sendMessage("");
            source.sendMessage("  Enter code: " + deviceCode.userCode());
            source.sendMessage("===========================================");

            // Poll for completion
            authenticator.pollDeviceCode(deviceCode.deviceCode(), deviceCode.interval())
                .thenAccept(success -> {
                    if (success) {
                        source.sendMessage("Authentication successful!");
                        source.sendMessage("Proxy is now authenticated as: " +
                            authenticator.getProfileUsername() + " (" + authenticator.getProfileUuid() + ")");
                    } else {
                        source.sendMessage("Authentication failed!");
                    }
                });
        });

        return CommandResult.success();
    }

    private CommandResult handleStatus(CommandSource source) {
        ProxyAuthenticator auth = proxyCore.getAuthenticator();
        if (auth != null && auth.isAuthenticated()) {
            source.sendMessage("Proxy authenticated as: " +
                auth.getProfileUsername() + " (" + auth.getProfileUuid() + ")");
            source.sendMessage("Certificate fingerprint: " + auth.getProxyFingerprint());
        } else {
            source.sendMessage("Proxy is NOT authenticated with Hytale");
            source.sendMessage("Use 'auth login' to authenticate");
        }
        source.sendMessage("");
        source.sendMessage("Backend authentication: Secret-based (HMAC referral)");
        return CommandResult.success();
    }

    private CommandResult handleSecret(CommandSource source) {
        byte[] secret = proxyCore.getBackendConnector().getProxySecret();
        if (secret != null) {
            String secretStr = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            source.sendMessage("Current proxy secret (Base64): " + secretStr);
            source.sendMessage("Configure this same secret on your backend servers.");
        } else {
            source.sendMessage("No proxy secret configured!");
        }
        return CommandResult.success();
    }

    @Override
    @Nonnull
    public List<String> suggest(@Nonnull CommandSource source, @Nonnull String[] args) {
        if (args.length <= 1) {
            return Arrays.asList("login", "status", "secret");
        }
        return List.of();
    }
}

