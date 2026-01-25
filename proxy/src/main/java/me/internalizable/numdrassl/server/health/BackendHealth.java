package me.internalizable.numdrassl.server.health;

public class BackendHealth {

    public Status status;
    public long statusExpire;

    public BackendHealth(Status status, long statusExpire) {
        this.status = status;
        this.statusExpire = statusExpire;
    }

    public enum Status {
        ONLINE,
        OFFLINE;
    }
}


