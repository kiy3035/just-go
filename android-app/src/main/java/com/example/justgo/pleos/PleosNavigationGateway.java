package com.example.justgo.pleos;

import android.content.Context;
import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.constants.WaypointIndex;
import ai.pleos.playground.navi.data.RequestWaypointInfo;
import ai.pleos.playground.navi.data.NavigationScreenRequest;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;

public final class PleosNavigationGateway implements NavigationGateway {
    private final NaviHelper naviHelper;
    private Listener listener;
    private boolean connected;
    private boolean released;

    private final NaviHelperEventListener naviEventListener = new NaviHelperEventListener() {
        @Override public void onRouteStarted(RouteStartInfo routeStartInfo) {
            if (listener != null) listener.onRouteStarted();
        }

        @Override public void onRoutingCompleted() {
            if (listener == null) return;
            try {
                naviHelper.requestNavigationScreen(NavigationScreenRequest.Foreground.INSTANCE);
                // 화면 전환 요청까지 접수된 뒤에만 완료로 알린다. 완료 콜백이 먼저
                // 전달되면 Activity가 대화상자를 닫아 이후 화면 전환 실패를 놓친다.
                listener.onRoutingCompleted();
            } catch (RuntimeException error) {
                listener.onError("경로 탐색은 완료됐지만 차량 내비 화면을 열지 못했습니다.");
            }
        }

        @Override public void onError(NaviErrorCode errorCode) {
            if (listener != null) listener.onError("차량 내비가 경로 요청을 처리하지 못했습니다. (" + errorCode + ")");
        }
    };

    public PleosNavigationGateway(Context context) {
        naviHelper = new NaviHelper(context.getApplicationContext());
    }

    @Override public void initialize(Listener listener) {
        this.listener = listener;
        naviHelper.initialize(
                () -> {
                    if (!released) {
                        connected = true;
                        naviHelper.addListener(naviEventListener);
                        listener.onConnected();
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    if (!released) listener.onConnectionFailed();
                    return Unit.INSTANCE;
                });
    }

    @Override public boolean isConnected() {
        return connected && !released;
    }

    @Override public void startRoute(List<Stop> stops) {
        if (!isConnected()) {
            notifyError("차량 내비에 연결되지 않았습니다.");
            return;
        }
        final NavigationRoutePlan.Plan plan;
        try {
            plan = NavigationRoutePlan.from(stops);
        } catch (IllegalArgumentException error) {
            notifyError(error.getMessage());
            return;
        }

        try {
            Stop destination = plan.destination();
            RouteInfo destinationInfo = new RouteInfo(
                    destination.latitude(), destination.longitude(), null, null,
                    destination.name(), "", "", "0", RouteOption.RECOMMENDED, "", false);
            if (plan.waypoints().isEmpty()) {
                naviHelper.requestRoute(destinationInfo);
                return;
            }
            List<RequestWaypointInfo> waypointInfo = new ArrayList<>();
            for (int index = 0; index < plan.waypoints().size(); index++) {
                Stop waypoint = plan.waypoints().get(index);
                waypointInfo.add(new RequestWaypointInfo(
                        waypoint.latitude(), waypoint.longitude(), waypoint.name(),
                        index == 0 ? WaypointIndex.FIRST : WaypointIndex.SECOND,
                        "", "", "0", ""));
            }
            naviHelper.requestRouteWithWaypoints(
                    new RouteWithWaypointsRequest(destinationInfo, waypointInfo));
        } catch (RuntimeException error) {
            notifyError("차량 내비 경로 요청을 시작하지 못했습니다.");
        }
    }

    private void notifyError(String message) {
        if (listener != null) listener.onError(message);
    }

    @Override public void release() {
        if (released) return;
        released = true;
        if (connected) naviHelper.removeListener(naviEventListener);
        connected = false;
        listener = null;
        naviHelper.release();
    }
}
