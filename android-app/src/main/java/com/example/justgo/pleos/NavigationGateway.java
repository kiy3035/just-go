package com.example.justgo.pleos;

import java.util.List;

public interface NavigationGateway {
    interface Listener {
        void onConnected();
        void onConnectionFailed();
        void onRouteStarted();
        void onRoutingCompleted();
        void onError(String message);
    }

    enum StopType { WAYPOINT, DESTINATION }

    record Stop(int sequence, StopType type, String name, double latitude, double longitude) { }

    void initialize(Listener listener);

    boolean isConnected();

    void startRoute(List<Stop> stops);

    void release();
}
