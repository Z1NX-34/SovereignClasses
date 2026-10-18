package com.sovereign.classes.client;

/** What the server last told this client about the local player's choices. */
public final class ClientState {
    private ClientState() {}
    public static volatile String race = "", cls = "";
}
