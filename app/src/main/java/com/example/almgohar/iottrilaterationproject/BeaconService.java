package com.example.almgohar.iottrilaterationproject;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;

import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;


import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class BeaconService extends Service implements BeaconConsumer{
    final String TAG = "AppLogs";
    BeaconManager beaconManager;
    private DatabaseReference databaseRef;

    ArrayList<Beacon> seenBeacons = new ArrayList<>();
    ArrayList<String> seenMacs = new ArrayList<>();
    boolean tutorialEnded = false;
    int powerThreshold = -80;
    int insideDuration = 0;
    int outsideDuration = 0;
    int tutorialDuration = 10; // Should be 5400
    int beaconsCount = 1; // Should be 4
    String nearestMac = null;

    String lastState = "Out";
    Date lastTime = null;

    public void showToast(final String text){
        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(BeaconService.this.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void uploadStatus(Status currentStatus){
        databaseRef.child("users").child("user_" + currentStatus.userID).setValue(currentStatus);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        databaseRef = FirebaseDatabase.getInstance().getReference();
        uploadStatus(new Status(1, false, 0.0));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));

        beaconManager.bind(this);
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onBeaconServiceConnect() {
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
            //beaconManager.startRangingBeaconsInRegion(new Region());
        }
        catch (RemoteException e) {

        }

        beaconManager.setRangeNotifier(new RangeNotifier() {
           @Override
           public void didRangeBeaconsInRegion(final Collection<Beacon> beacons, Region region) {
               if(tutorialEnded){
                   return;
               }

               if(insideDuration + outsideDuration >= tutorialDuration){
                   double insidePercent = insideDuration / tutorialDuration;
                   if(insidePercent > 0.5){
                       showToast("Attended");
                       uploadStatus(new Status(1, true, insideDuration));
                   }
                   else{
                       showToast("Absent");
                       uploadStatus(new Status(1, false, insideDuration));
                   }

                   tutorialEnded = true;
               }

               Thread thread = new Thread(new Runnable() {
                   @Override
                   public void run() {
                       try {
                           if (beacons.size() > 0) {
                               Beacon currentBeacon = beacons.iterator().next();
                               String mac = currentBeacon.getBluetoothAddress();

                               if(nearestMac == null) {
                                   if (!seenMacs.contains(mac)) {
                                       seenBeacons.add(currentBeacon);
                                       seenMacs.add(mac);

                                       if (seenBeacons.size() == beaconsCount) {
                                           int maxPower = seenBeacons.get(0).getRssi();
                                           nearestMac = seenBeacons.get(0).getBluetoothAddress();

                                           for (int i = 1; i < seenBeacons.size(); i++) {
                                               if (seenBeacons.get(i).getRssi() > maxPower) {
                                                   maxPower = seenBeacons.get(i).getRssi();
                                                   nearestMac = seenBeacons.get(i).getBluetoothAddress();
                                               }
                                           }
                                       }
                                   }
                               }

                               else{
                                   if(mac.equals(nearestMac)){
                                       Date currentTime = Calendar.getInstance().getTime();
                                       int timeDelta = -1;

                                       if(lastTime != null){
                                           long diffInMs = currentTime.getTime() - lastTime.getTime();
                                           timeDelta = (int) TimeUnit.MILLISECONDS.toSeconds(diffInMs);
                                       }

                                       if(currentBeacon.getRssi() < powerThreshold){
                                           Log.v(TAG, "Outside, RSSI : "+currentBeacon.getRssi());

                                           if(lastState.equals("In")){
                                               showToast("Exited the tutorial");
                                           }

                                           lastState = "Out";
                                           if(timeDelta > -1){
                                               outsideDuration += timeDelta;
                                           }
                                       }
                                       else{
                                           Log.v(TAG, "Inside, RSSI : "+currentBeacon.getRssi());

                                           if(lastState.equals("Out")){
                                               showToast("Entered the tutorial");
                                           }

                                           lastState = "In";
                                           if(timeDelta > -1){
                                               insideDuration += timeDelta;
                                           }
                                       }

                                       lastTime = currentTime;
                                   }
                               }
                           }
                       }
                       catch (Exception e) {
                           e.printStackTrace();
                       }
                   }

               });
               thread.start();
           }
        }

        );
        beaconManager.setMonitorNotifier(new
             MonitorNotifier() {
                 @Override
                 public void didEnterRegion(Region region) {
                     Log.i(TAG, "I just saw an beacon for the first time!");
                 }

                 @Override
                 public void didExitRegion(Region region) {
                     Log.i(TAG, "I no longer see an beacon");
                 }

                 @Override
                 public void didDetermineStateForRegion(int state, Region region) {
                     Log.i(TAG, "I have just switched from seeing/not seeing beacons: " + state);
                 }
             }

        );
    }
}
