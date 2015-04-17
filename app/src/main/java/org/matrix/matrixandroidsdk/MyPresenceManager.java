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
package org.matrix.matrixandroidsdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.matrixandroidsdk.contacts.Contact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton class for handling the current user's presence.
 */
public class MyPresenceManager {
    private static final String LOG_TAG = "MyPresenceManager";

    // The delay we wait for before actually advertising in case it changes in the meantime.
    // This is useful when saying we're unavailable on an activity's onPause to differentiate between
    // backgrounding the app and just switching activities.
    private static final int DELAY_TS = 3000;

    // Array of presence states ordered by priority. If the current device thinks our user is online,
    // it will disregard a presence event saying the user is unavailable and advertise that they are in
    // fact online as a correction.
    private static String[] orderedPresenceArray = new String[] {
            User.PRESENCE_ONLINE,
            User.PRESENCE_UNAVAILABLE,
            User.PRESENCE_OFFLINE
    };
    // We need the reverse structure to associate an order to a given presence state
    private static Map<String, Integer> presenceOrderMap = new HashMap<String, Integer>();
    static {
        for (int i = 0; i < orderedPresenceArray.length; i++) {
            presenceOrderMap.put(orderedPresenceArray[i], i);
        }
    }

    private static HashMap<MXSession, MyPresenceManager> instances = new HashMap<MXSession, MyPresenceManager>();

    private MyUser myUser;
    private Handler mHandler;
    private String latestAdvertisedPresence; // Presence we're advertising
    private String tmpPresence;

    private MyPresenceManager(Context context, MXSession session) {
        myUser = session.getMyUser();
        mHandler = new Handler(Looper.getMainLooper());

        myUser.addEventListener(new MXEventListener() {
            @Override
            public void onPresenceUpdate(final String accountId, Event event, User user) {
                myUser.presence = user.presence;

                // If the received presence is the same as the last one we've advertised, this must be
                // the event stream sending back our own event => nothing more to do
                if (!user.presence.equals(latestAdvertisedPresence)) {
                    // If we're here, the presence event comes from another of this user's devices. If it's saying for example that it's
                    // offline but we're currently online, our presence takes precedence; in which case, we broadcast the correction
                    Integer newPresenceOrder = presenceOrderMap.get(user.presence);
                    if (newPresenceOrder != null) {
                        int ourPresenceOrder = presenceOrderMap.get(latestAdvertisedPresence);
                        // If the new presence is further down the order list, we correct it
                        if (newPresenceOrder > ourPresenceOrder) {
                            advertisePresence(latestAdvertisedPresence);
                        }
                    }
                }
            }
        });
    }

    /**
     * Create an instance without any check.
     * @param context
     * @param session
     * @return
     */
    private static MyPresenceManager createInstance(Context context, MXSession session) {
        MyPresenceManager instance = new MyPresenceManager(context, session);
        instances.put(session, instance);
        return instance;
    }

    /**
     * Search a presence manager from a dedicated session
     * @param context the context
     * @param session the session
     * @return the linked presence manager
     */
    public static synchronized MyPresenceManager getInstance(Context context, MXSession session) {
        MyPresenceManager instance = instances.get(session);
        if (instance == null) {
            instance = createInstance(context, session);
        }
        return instance;
    }

    /**
     * Create an MyPresenceManager instance for each session if it was not yet done.
     * @param context the context
     * @param sessions the sessions
     */
    public static synchronized void createPresenceManager(Context context, Collection<MXSession> sessions) {
        for(MXSession session : sessions) {
            if (!instances.containsKey(session)) {
                createInstance(context, session);
            }
        }
    }

    /**
     * Remove a presence manager for a session.
     * @param session the session
     */
    public static synchronized void remove(MXSession session) {
        instances.remove(session);
    }

    /**
     * Send the advertise presence message.
     * @param presence the presence message.
     */
    private void advertisePresence(String presence) {
        latestAdvertisedPresence = presence;
        tmpPresence = presence;

        // Only do it if different
        if (!presence.equals(myUser.presence)) {
            Log.d(LOG_TAG, "Advertising presence " + presence);
            myUser.updatePresence(presence, null, null);
        }
    }

    /**
     * Send the advertise presence message after delay.
     * @param presence the presence message.
     */
    private void advertisePresenceAfterDelay(final String presence) {
        tmpPresence = presence;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Only advertise if the presence hasn't changed in the meantime
                if (presence.equals(tmpPresence)) {
                    advertisePresence(presence);
                }
            }
        }, DELAY_TS);
    }

    /**
     * Send the online event to each known MyPresenceManager
     */
    public static void advertiseAllOnline() {
        Collection<MyPresenceManager> values = instances.values();

        for(MyPresenceManager myPresenceManager : values) {
            myPresenceManager.advertiseOnline();
        }
    }

    /**
     * Send the online message.
     */
    public void advertiseOnline() {
        advertisePresence(User.PRESENCE_ONLINE);
    }

    /**
     * Send the offline event to each known MyPresenceManager.
     */
    public static void advertiseAllOffline() {
        Collection<MyPresenceManager> values = instances.values();

        for(MyPresenceManager myPresenceManager : values) {
            myPresenceManager.advertiseOffline();
        }
    }

    /**
     * Send the offline message.
     */
    public void advertiseOffline() {
        advertisePresence(User.PRESENCE_OFFLINE);
    }

    /**
     * Send the Unavailable event to each known MyPresenceManager.
     */
    public static void advertiseAllUnavailableAfterDelay() {
        Collection<MyPresenceManager> values = instances.values();

        for(MyPresenceManager myPresenceManager : values) {
            myPresenceManager.advertiseUnavailableAfterDelay();
        }
    }

    /**
     * Send the Unavailable mesage after delay
     */
    public void advertiseUnavailableAfterDelay() {
        // If we've advertised that we're offline, we can't go straight to unavailable
        // This avoids the case where the user logs out, advertising OFFLINE, but then leaves the
        // activity, advertising UNAVAILABLE
        if (!User.PRESENCE_OFFLINE.equals(latestAdvertisedPresence)) {
            advertisePresenceAfterDelay(User.PRESENCE_UNAVAILABLE);
        }
    }
}
