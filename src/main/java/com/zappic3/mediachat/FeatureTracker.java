package com.zappic3.mediachat;

public class FeatureTracker {
    private static boolean _isServer = false;
    private static boolean _isDedicatedServer = false;

    private FeatureTracker() {}

    public static boolean isServer() {
        return _isServer;
    }
    public static void isServer(boolean isServer) {
        _isServer = isServer;
    }

    public static boolean isDedicatedServer () {
        return _isServer;
    }
    public static void isDedicatedServer (boolean isDedicatedServer ) {
        _isDedicatedServer = isDedicatedServer;
    }

}
