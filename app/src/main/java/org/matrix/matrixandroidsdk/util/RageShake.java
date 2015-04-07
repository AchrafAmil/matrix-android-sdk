/*
 * Copyright 2014 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.matrixandroidsdk.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;


public class RageShake implements SensorEventListener {
    private static final String LOG_TAG = "RageShake";

    private static RageShake instance;

    private Context mContext;

    // weak refs so dead dialogs can be GCed
    private List<WeakReference<Dialog>> mDialogs;
    
    protected RageShake() {
        mDialogs = new ArrayList<WeakReference<Dialog>>();

        // Samsung devices for some reason seem to be less sensitive than others so the threshold is being
        // lowered for them. A possible lead for a better formula is the fact that the sensitivity detected 
        // with the calculated force below seems to relate to the sample rate: The higher the sample rate,
        // the higher the sensitivity.
        String model = Build.MODEL.trim();
        // S3, S1(Brazil), Galaxy Pocket
        if ("GT-I9300".equals(model) || "GT-I9000B".equals(model) || "GT-S5300B".equals(model)) {
            threshold = 20.0f;
        }
    }

    public synchronized static RageShake getInstance() {
        if (instance == null) {
            instance = new RageShake();
        }
        return instance;
    }

    public void registerDialog(Dialog d) {
        mDialogs.add(new WeakReference<Dialog>(d));
    }

    public void sendBugReport() {
        Bitmap screenShot = this.takeScreenshot();

        if (null != screenShot) {
            try {
                // store the file in shared place
                String path = MediaStore.Images.Media.insertImage(mContext.getContentResolver(), screenShot, "screenshot-" + new Date(), null);

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/html");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"rageshake@matrix.org"});
                intent.putExtra(Intent.EXTRA_SUBJECT, "Matrix bug report");

                String message = "Something went wrong on my Matrix client : \n\n\n";
                message += "-----> my comments <-----\n\n\n";
                message += "------------------------------\n";

                message += "Application info\n";

                MXSession session = Matrix.getInstance(mContext).getDefaultSession();
                MyUser mMyUser = session.getMyUser();

                message += "userId : "+ mMyUser.userId + "\n";
                message += "displayname : " + mMyUser.displayname + "\n";
                message += "\n";

                message += "homeServer :" + session.getCredentials().homeServer + "\n";

                message += "\n";

                String versionName = "";

                try {
                    PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                    versionName = pInfo.versionName;
                } catch (Exception e) {

                }

                message += "matrixConsole version: " + versionName + "\n";
                message += "SDK version:  " + versionName + "\n";

                message += "\n\n\n";

                String log = LogUtilities.getLogCatError();

                if (null != log) {
                    message += log;
                }

                intent.putExtra(Intent.EXTRA_TEXT, message);

                // attachments
                intent.setType("image/jpg");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));

                String logCat = LogUtilities.getLogCatDebug();
                if (logCat != null) {
                    try {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        GZIPOutputStream gzip = new GZIPOutputStream(os);
                        gzip.write(logCat.getBytes());
                        gzip.finish();

                        File debugLogFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "logs-" + new Date() + ".gz");
                        FileOutputStream fos = new FileOutputStream(debugLogFile);
                        os.writeTo(fos);

                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(debugLogFile));
                    }
                    catch (IOException e) {
                        Log.e(LOG_TAG,""+e);
                    }
                }


                ConsoleApplication.getCurrentActivity().startActivity(intent);
            } catch (Exception e) {
            }
        }
    }

    public void promptForReport() {
        // Cannot prompt for bug, no active activity.
        if (ConsoleApplication.getCurrentActivity() == null) {
            return;
        }

        // The user is trying to leave with unsaved changes. Warn about that
        new AlertDialog.Builder(ConsoleApplication.getCurrentActivity())
                .setMessage("You seem to be shaking the phone in frustration. Would you like to submit a bug report?")
                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        sendBugReport();
                    }
                })
                .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();

    }

    private Bitmap takeScreenshot() {
        if (ConsoleApplication.getCurrentActivity() == null) {
            return null;
        }
        
        // get content view
        View contentView = ConsoleApplication.getCurrentActivity().findViewById(android.R.id.content);
        if (contentView == null) {
            Log.e(LOG_TAG, "Cannot find content view on " + ConsoleApplication.getCurrentActivity() + ". Cannot take screenshot.");
            return null;
        }
        
        // get the root view to snapshot
        View rootView = contentView.getRootView();
        if (rootView == null) {
            Log.e(LOG_TAG, "Cannot find root view on " + ConsoleApplication.getCurrentActivity() + ". Cannot take screenshot.");
            return null;
        }
        // refresh it
        rootView.setDrawingCacheEnabled(false);
        rootView.setDrawingCacheEnabled(true);
        
        try {
            Bitmap baseScreen = rootView.getDrawingCache();
            
            // loop the dialogs and prune old/not visible ones
            List<Dialog> onScreenDialogs = new ArrayList<Dialog>();
            for (int i=0; i<mDialogs.size(); i++) {
                WeakReference<Dialog> wfd = mDialogs.get(i);
                Dialog d = wfd.get();
                if (d == null || (d != null && !d.isShowing())) {
                    Log.d(LOG_TAG,  "Discarding empty/null dialog. "+d);
                    mDialogs.remove(i);
                    i--;
                    continue;
                }
                onScreenDialogs.add(d);
            }
        
            if (onScreenDialogs.size() == 0) {
                Log.d(LOG_TAG, "No on screen dialogs.");
                return baseScreen;
            }
            else {
                // use a canvas to draw on top of the base screen.
                Canvas c = new Canvas(baseScreen);
                for (Dialog d : onScreenDialogs) {
                    if (d.getWindow() != null && d.getWindow().getAttributes() != null) {
                        View dialogView = d.getWindow().peekDecorView();
                        Bitmap dialogBitmap = null;
                        // get the dialog bitmap
                        if (dialogView != null) {
                            dialogView.setDrawingCacheEnabled(false);
                            dialogView.setDrawingCacheEnabled(true);
                            dialogBitmap = dialogView.getDrawingCache();
                        }
                        if (dialogBitmap == null) {
                            Log.w(LOG_TAG, "Cannot get dialog bitmap.");
                            continue;
                        }
                        
                        // draw it to the canvas in the right place
                        WindowManager.LayoutParams params = d.getWindow().getAttributes();
                        int x = params.x;
                        int y = params.y;
                        int w = dialogView.getWidth();
                        int h = dialogView.getHeight();
                        int gravity = params.gravity;
                        Log.d(LOG_TAG, "Dialog x "+x+" y "+y+" w "+w+" h "+h+" gravity "+gravity);
                        if (x == 0 && y == 0 && w < baseScreen.getWidth() && h < baseScreen.getHeight()) {
                            switch (gravity) {
                            case Gravity.CENTER:
                                // mid-point - 1/2
                                x = baseScreen.getWidth()/2 - (w/2);
                                y = baseScreen.getHeight()/2 - (h/2);
                                break;
                            default:
                                Log.w(LOG_TAG, "Unhandled gravity: "+gravity);
                                break;
                            }
                        }
                        c.drawBitmap(dialogBitmap, x, y, null);
                        Log.d(LOG_TAG, "Drew a dialog to the canvas");
                    }
                }
                return baseScreen;
            }
        }
        catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "Cannot get drawing cache for "+ ConsoleApplication.getCurrentActivity() +" OOM.");
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Cannot get snapshot of screen: "+e);
        }
        return null;
    }

    /**
     * start the sensor detector
     */
    public void start(Context context) {

        mContext = context;

        SensorManager sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) {
            Log.e(LOG_TAG, "No accelerometer in this device. Cannot use rage shake.");
            return;
        }
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // don't care
    }

    private long now = 0;
    private long timeDiff = 0;
    private long lastUpdate = 0;
    private long lastShake = 0;

    private float x = 0;
    private float y = 0;
    private float z = 0;
    private float lastX = 0;
    private float lastY = 0;
    private float lastZ = 0;
    private float force = 0;
    
    private float threshold = 35.0f;
    
    private long intervalNanos = 1000 * 1000 * 10000; // 10sec
    
    private long timeToNextShakeMs = 10 * 1000;
    private long lastShakeTimestamp = 0L;
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        
        now = event.timestamp;
        
        x = event.values[0];
        y = event.values[1];
        z = event.values[2];

        if (lastUpdate == 0) {
            // set some default vals
            lastUpdate = now;
            lastShake = now;
            lastX = x;
            lastY = y;
            lastZ = z;
        }
        else {
            timeDiff = now - lastUpdate;
            
            if (timeDiff > 0) { 
                force = Math.abs(x + y + z - lastX - lastY - lastZ);
                if (Float.compare(force, threshold) >0 ) {
                    if (now - lastShake >= intervalNanos && (System.currentTimeMillis() - lastShakeTimestamp) > timeToNextShakeMs) { 
                         Log.d(LOG_TAG, "Shaking detected.");
                         lastShakeTimestamp = System.currentTimeMillis();
                         promptForReport();
                    }
                    else {
                        Log.d(LOG_TAG, "Suppress shaking - not passed interval. Ms to go: "+(timeToNextShakeMs - 
                                (System.currentTimeMillis() - lastShakeTimestamp))+" ms");
                    }
                    lastShake = now;
                }
                lastX = x;
                lastY = y;
                lastZ = z;
                lastUpdate = now; 
            }
        }
    }

}
