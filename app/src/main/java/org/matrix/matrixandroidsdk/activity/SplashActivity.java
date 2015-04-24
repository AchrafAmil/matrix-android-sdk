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
package org.matrix.matrixandroidsdk.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.matrixandroidsdk.ErrorListener;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager;
import org.matrix.matrixandroidsdk.services.EventStreamService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class SplashActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "SplashActivity";

    private Collection<MXSession> mSessions;
    private GcmRegistrationManager mGcmRegistrationManager;

    private boolean mInitialSyncComplete = false;
    private boolean mPusherRegistrationComplete = false;

    private HashMap<MXSession, IMXEventListener> mListeners;
    private HashMap<MXSession, IMXEventListener> mDoneListeners;

    private void finishIfReady() {
        Log.e(LOG_TAG, "finishIfReady " + mInitialSyncComplete + " " + mPusherRegistrationComplete);

        if (mInitialSyncComplete && mPusherRegistrationComplete) {
            Log.e(LOG_TAG, "finishIfRead start HomeActivity");

            // Go to the home page
            startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            SplashActivity.this.finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.e(LOG_TAG, "onCreate");

        setContentView(R.layout.activity_splash);

        mSessions =  Matrix.getInstance(getApplicationContext()).getSessions();

        if (mSessions == null) {
            Log.e(LOG_TAG, "onCreate no Sessions");
            finish();
            return;
        }

        mListeners = new HashMap<MXSession, IMXEventListener>();
        mDoneListeners = new HashMap<MXSession, IMXEventListener>();

        ArrayList<String> matrixIds = new ArrayList<String>();

        for(MXSession session : mSessions) {
            final MXSession fSession = session;

            final IMXEventListener eventListener = new MXEventListener() {
                @Override
                public void onInitialSyncComplete() {
                    super.onInitialSyncComplete();
                    Boolean noMoreListener;

                    Log.e(LOG_TAG, "Session " + fSession.getCredentials().userId + " is initialized");

                    synchronized(mListeners) {
                        mDoneListeners.put(fSession, mListeners.get(fSession));
                        // do not remove the listeners here
                        // it crashes the application because of the upper loop
                        //fSession.getDataHandler().removeListener(mListeners.get(fSession));
                        // remove from the pendings list

                        mListeners.remove(fSession);
                        noMoreListener = mInitialSyncComplete = (mListeners.size() == 0);
                    }

                    if (noMoreListener) {
                        finishIfReady();
                    }
                }
            };

            if (!fSession.getDataHandler().isInitialSyncComplete()) {
                mListeners.put(fSession, eventListener);
                fSession.getDataHandler().addListener(eventListener);

                // Set the main error listener
                fSession.setFailureCallback(new ErrorListener(this));

                // session to activate
                matrixIds.add(session.getCredentials().userId);
            }
        }

        synchronized(mListeners) {
            mInitialSyncComplete = (mListeners.size() == 0);
        }

        if (EventStreamService.getInstance() == null) {
            // Start the event stream service
            Intent intent = new Intent(this, EventStreamService.class);
            intent.putExtra(EventStreamService.EXTRA_MATRIX_IDS, matrixIds.toArray(new String[matrixIds.size()]));
            intent.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.START.ordinal());
            startService(intent);
        } else {
            EventStreamService.getInstance().startAccounts(matrixIds);
        }

        mGcmRegistrationManager = Matrix.getInstance(getApplicationContext())
                .getSharedGcmRegistrationManager();
        mPusherRegistrationComplete = mGcmRegistrationManager.isRegistred();
        mGcmRegistrationManager.setListener(new GcmRegistrationManager.GcmRegistrationIdListener() {
            @Override
            public void onPusherRegistered() {
                mPusherRegistrationComplete = true;
                finishIfReady();
            }
        });
        mGcmRegistrationManager.registerPusherInBackground();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Collection<MXSession> sessions = mDoneListeners.keySet();

        for(MXSession session : sessions) {
            session.getDataHandler().removeListener(mDoneListeners.get(session));
        }

        mGcmRegistrationManager.setListener(null);
    }
}
