package com.example.justgo.pleos;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.content.pm.PackageManager;

public final class DrivingRestrictionsMonitor {
    public interface Callback {
        void onRestrictionsChanged(boolean restricted);
    }

    private final Context context;
    private Car car;
    private CarUxRestrictionsManager manager;
    private CarUxRestrictionsManager.OnUxRestrictionsChangedListener listener;

    public DrivingRestrictionsMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start(Callback callback) {
        stop();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            callback.onRestrictionsChanged(false);
            return;
        }
        try {
            car = Car.createCar(context);
            if (car == null) {
                callback.onRestrictionsChanged(true);
                return;
            }
            manager = (CarUxRestrictionsManager) car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
            if (manager == null) {
                callback.onRestrictionsChanged(true);
                return;
            }
            listener = restrictions -> callback.onRestrictionsChanged(requiresOptimization(restrictions));
            manager.registerListener(listener);
            callback.onRestrictionsChanged(requiresOptimization(manager.getCurrentCarUxRestrictions()));
        } catch (RuntimeException | LinkageError error) {
            stop();
            callback.onRestrictionsChanged(true);
        }
    }

    public void stop() {
        if (manager != null && listener != null) {
            try {
                manager.unregisterListener();
            } catch (RuntimeException ignored) {
                // Car service may already be disconnected during teardown.
            }
        }
        listener = null;
        manager = null;
        if (car != null) {
            car.disconnect();
            car = null;
        }
    }

    private static boolean requiresOptimization(CarUxRestrictions restrictions) {
        return restrictions == null || restrictions.isRequiresDistractionOptimization();
    }
}
