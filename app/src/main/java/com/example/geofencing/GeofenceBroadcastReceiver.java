package com.example.geofencing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceBroadcastReceive";

    public static String currentGeofenceId = "NONE";
    private final ImageButton arButton = MapsActivity.goToARbutton;

    @Override
    public void onReceive(Context context, Intent intent) {

        NotificationHelper notificationHelper = new NotificationHelper(context);

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        if (geofencingEvent.hasError()) {
            Log.d(TAG, "onReceive: Error receiving geofence event...");
            return;
        }

        List<Geofence> geofenceList = geofencingEvent.getTriggeringGeofences();
        for (Geofence geofence : geofenceList) {
            currentGeofenceId = geofence.getRequestId();
            Log.d(TAG, "onReceive: " + geofence.getRequestId());
        }

        int transitionType = geofencingEvent.getGeofenceTransition();

        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                arButton.setEnabled(true);
                arButton.setClickable(true);
                Toast.makeText(context, "Ar is now available", Toast.LENGTH_SHORT).show();
                notificationHelper.sendHighPriorityNotification("Landmark Nearby", "AR can now be enabled", ArActivity.class);
                break;
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                arButton.setEnabled(true);
                arButton.setClickable(true);
                //Toast.makeText(context, "AR Still Available", Toast.LENGTH_SHORT).show();
                //notificationHelper.sendHighPriorityNotification("Still in landmark zone - You may still use AR", "", MapsActivity.class);
                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                currentGeofenceId = "NONE";
                arButton.setEnabled(false);
                arButton.setClickable(false);
                Toast.makeText(context, "Leaving area of interest. Ar no longer available", Toast.LENGTH_SHORT).show();
                //notificationHelper.sendHighPriorityNotification("Leaving AR Zone", "", MapsActivity.class);
                break;
        }

    }
}
