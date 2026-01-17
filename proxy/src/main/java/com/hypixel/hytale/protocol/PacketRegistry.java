/*
 * Decompiled with CFR 0.152.
 */
package com.hypixel.hytale.protocol;

import com.hypixel.hytale.protocol.io.ValidationResult;
import com.hypixel.hytale.protocol.packets.auth.AuthGrant;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import com.hypixel.hytale.protocol.packets.auth.ConnectAccept;
import com.hypixel.hytale.protocol.packets.auth.PasswordAccepted;
import com.hypixel.hytale.protocol.packets.auth.PasswordRejected;
import com.hypixel.hytale.protocol.packets.auth.PasswordResponse;
import com.hypixel.hytale.protocol.packets.auth.ServerAuthToken;
import com.hypixel.hytale.protocol.packets.auth.Status;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import com.hypixel.hytale.protocol.packets.connection.Ping;
import com.hypixel.hytale.protocol.packets.connection.Pong;
import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import com.hypixel.hytale.protocol.packets.interface_.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PacketRegistry {
    private static final Map<Integer, PacketInfo> BY_ID = new HashMap<Integer, PacketInfo>();
    private static final Map<Integer, PacketInfo> BY_ID_UNMODIFIABLE = Collections.unmodifiableMap(BY_ID);
    private static final Map<Class<? extends Packet>, Integer> BY_TYPE = new HashMap<Class<? extends Packet>, Integer>();

    private PacketRegistry() {
    }

    private static void register(int id, String name, Class<? extends Packet> type, int fixedBlockSize, int maxSize, boolean compressed, BiFunction<ByteBuf, Integer, ValidationResult> validate, BiFunction<ByteBuf, Integer, Packet> deserialize) {
        PacketInfo existing = BY_ID.get(id);
        if (existing != null) {
            throw new IllegalStateException("Duplicate packet ID " + id + ": '" + name + "' conflicts with '" + existing.name() + "'");
        }
        PacketInfo info = new PacketInfo(id, name, type, fixedBlockSize, maxSize, compressed, validate, deserialize);
        BY_ID.put(id, info);
        BY_TYPE.put(type, id);
    }

    @Nullable
    public static PacketInfo getById(int id) {
        return BY_ID.get(id);
    }

    @Nullable
    public static Integer getId(Class<? extends Packet> type) {
        return BY_TYPE.get(type);
    }

    @Nonnull
    public static Map<Integer, PacketInfo> all() {
        return BY_ID_UNMODIFIABLE;
    }

    static {
        PacketRegistry.register(0, "Connect", Connect.class, 82, 38161, false, Connect::validateStructure, Connect::deserialize);
        PacketRegistry.register(1, "Disconnect", Disconnect.class, 2, 16384007, false, Disconnect::validateStructure, Disconnect::deserialize);
        PacketRegistry.register(2, "Ping", Ping.class, 29, 29, false, Ping::validateStructure, Ping::deserialize);
        PacketRegistry.register(3, "Pong", Pong.class, 20, 20, false, Pong::validateStructure, Pong::deserialize);
        PacketRegistry.register(10, "Status", Status.class, 9, 2587, false, Status::validateStructure, Status::deserialize);
        PacketRegistry.register(11, "AuthGrant", AuthGrant.class, 1, 49171, false, AuthGrant::validateStructure, AuthGrant::deserialize);
        PacketRegistry.register(12, "AuthToken", AuthToken.class, 1, 49171, false, AuthToken::validateStructure, AuthToken::deserialize);
        PacketRegistry.register(13, "ServerAuthToken", ServerAuthToken.class, 1, 32851, false, ServerAuthToken::validateStructure, ServerAuthToken::deserialize);
        PacketRegistry.register(14, "ConnectAccept", ConnectAccept.class, 1, 70, false, ConnectAccept::validateStructure, ConnectAccept::deserialize);
        PacketRegistry.register(15, "PasswordResponse", PasswordResponse.class, 1, 70, false, PasswordResponse::validateStructure, PasswordResponse::deserialize);
        PacketRegistry.register(16, "PasswordAccepted", PasswordAccepted.class, 0, 0, false, PasswordAccepted::validateStructure, PasswordAccepted::deserialize);
        PacketRegistry.register(17, "PasswordRejected", PasswordRejected.class, 5, 74, false, PasswordRejected::validateStructure, PasswordRejected::deserialize);
        PacketRegistry.register(18, "ClientReferral", ClientReferral.class, 1, 5141, false, ClientReferral::validateStructure, ClientReferral::deserialize);
        PacketRegistry.register(210, "ServerMessage", ServerMessage.class, 2, 0x64000000, false, ServerMessage::validateStructure, ServerMessage::deserialize);
        PacketRegistry.register(211, "ChatMessage", ChatMessage.class, 1, 16384006, false, ChatMessage::validateStructure, ChatMessage::deserialize);
    }

    public record PacketInfo(int id, @Nonnull String name, @Nonnull Class<? extends Packet> type, int fixedBlockSize, int maxSize, boolean compressed, @Nonnull BiFunction<ByteBuf, Integer, ValidationResult> validate, @Nonnull BiFunction<ByteBuf, Integer, Packet> deserialize) {
    }
}

