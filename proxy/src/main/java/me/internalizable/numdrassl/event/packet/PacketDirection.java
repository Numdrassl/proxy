package me.internalizable.numdrassl.event.packet;

/**
 * Direction of packet flow through the proxy.
 */
public enum PacketDirection {
    /** Packet traveling from client to server (serverbound) */
    CLIENT_TO_SERVER,
    /** Packet traveling from server to client (clientbound) */
    SERVER_TO_CLIENT
}

