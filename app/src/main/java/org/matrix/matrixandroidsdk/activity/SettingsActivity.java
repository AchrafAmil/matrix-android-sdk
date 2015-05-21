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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager;
import org.matrix.matrixandroidsdk.util.ResourceUtils;
import org.matrix.matrixandroidsdk.util.UIUtils;

import java.util.Collection;
import java.util.HashMap;

public class SettingsActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "SettingsActivity";

    private static final int REQUEST_IMAGE = 0;

    // stored the updated thumbnails URI by session
    private HashMap<MXSession, Uri> mTmpThumbnailUriBySession = new HashMap<MXSession, Uri>();

    // linear layout by session
    // each profile has a dedicated session.
    private HashMap<MXSession, LinearLayout> mLinearLayoutBySession = new HashMap<MXSession, LinearLayout>();

    private MXSession mUpdatingSession = null;

    private MXMediasCache mMediasCache;

    void refreshProfileThumbnail(MXSession session, LinearLayout baseLayout) {
        ImageView avatarView = (ImageView) baseLayout.findViewById(R.id.imageView_avatar);
        Uri newAvatarUri = mTmpThumbnailUriBySession.get(session);
        String avatarUrl = session.getMyUser().avatarUrl;

        if (null != newAvatarUri) {
            avatarView.setImageURI(newAvatarUri);
        } else if (avatarUrl == null) {
            avatarView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        } else {
            int size = getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);
            mMediasCache.loadAvatarThumbnail(avatarView, avatarUrl, size);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        mMediasCache = Matrix.getInstance(this).getMediasCache();

        // add any known session
        LinearLayout globalLayout = (LinearLayout)findViewById(R.id.settings_layout);
        TextView profileHeader = (TextView)findViewById(R.id.settings_profile_information_header);
        int pos = globalLayout.indexOfChild(profileHeader);

        for(MXSession session : Matrix.getMXSessions(this)) {
            final MXSession fSession = session;

            LinearLayout profileLayout = (LinearLayout)getLayoutInflater().inflate(R.layout.account_section_settings, null);
            mLinearLayoutBySession.put(session, profileLayout);

            pos++;
            globalLayout.addView(profileLayout, pos);
            refreshProfileThumbnail(session, profileLayout);

            ImageView avatarView = (ImageView)profileLayout.findViewById(R.id.imageView_avatar);

            avatarView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mUpdatingSession = fSession;
                    Intent fileIntent = new Intent(Intent.ACTION_PICK);
                    fileIntent.setType("image/*");
                    startActivityForResult(fileIntent, REQUEST_IMAGE);
                }
            });

            MyUser myUser = session.getMyUser();

            TextView matrixIdTextView = (TextView) profileLayout.findViewById(R.id.textView_matrix_id);
            matrixIdTextView.setText(myUser.userId);

            final Button saveButton = (Button) profileLayout.findViewById(R.id.button_save);

            EditText displayNameEditText = (EditText) profileLayout.findViewById(R.id.editText_displayName);
            displayNameEditText.setText(myUser.displayname);
            displayNameEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateSaveButton(saveButton);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveChanges(fSession);
                }
            });
        }

        // Config information

        String versionName = "";

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (Exception e) {

        }

        TextView consoleVersionTextView = (TextView) findViewById(R.id.textView_matrixConsoleVersion);
        consoleVersionTextView.setText(getString(R.string.settings_config_console_version, versionName));

        TextView sdkVersionTextView = (TextView) findViewById(R.id.textView_matrixSDKVersion);
        sdkVersionTextView.setText(getString(R.string.settings_config_sdk_version, versionName));

        TextView buildNumberTextView = (TextView) findViewById(R.id.textView_matrixBuildNumber);
        buildNumberTextView.setText(getString(R.string.settings_config_build_number, ""));

        TextView userIdTextView = (TextView) findViewById(R.id.textView_configUsers);
        String config = "";

        int sessionIndex = 1;

        Collection<MXSession> sessions = Matrix.getMXSessions(this);

        for(MXSession session : sessions) {

            if (sessions.size() > 1) {
                config += "\nAccount " + sessionIndex + " : \n";
                sessionIndex++;
            }

            config += String.format(getString(R.string.settings_config_home_server), session.getCredentials().homeServer);
            config += "\n";

            config += String.format(getString(R.string.settings_config_user_id), session.getMyUser().userId);

            if (sessions.size() > 1) {
                config += "\n";
            }
        }

        userIdTextView.setText(config);

        // room settings
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        listenBoxUpdate(preferences, R.id.checkbox_useGcm, getString(R.string.settings_key_use_gcm), false);
        listenBoxUpdate(preferences, R.id.checkbox_displayAllEvents, getString(R.string.settings_key_display_all_events), false);
        listenBoxUpdate(preferences, R.id.checkbox_hideUnsupportedEvenst, getString(R.string.settings_key_hide_unsupported_events), true);
        listenBoxUpdate(preferences, R.id.checkbox_sortByLastSeen, getString(R.string.settings_key_sort_by_last_seen), true);
        listenBoxUpdate(preferences, R.id.checkbox_displayLeftMembers, getString(R.string.settings_key_display_left_members), false);
        listenBoxUpdate(preferences, R.id.checkbox_displayPublicRooms, getString(R.string.settings_key_display_public_rooms_recents), true);

        final Button clearCacheButton = (Button) findViewById(R.id.button_clear_cache);

        String cacheSize = android.text.format.Formatter.formatFileSize(this, mMediasCache.cacheSize(this));
        clearCacheButton.setText(getString(R.string.clear_cache)  + " (" + cacheSize + ")");

        clearCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediasCache.clearCache(SettingsActivity.this);

                String cacheSize = android.text.format.Formatter.formatFileSize(SettingsActivity.this, mMediasCache.cacheSize(SettingsActivity.this));
                clearCacheButton.setText(getString(R.string.clear_cache)  + " (" + cacheSize + ")");
            }
        });


        final GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(this).getSharedGcmRegistrationManager();

        final EditText appIDEditText = (EditText)findViewById(R.id.editText_gcm_app_id);
        appIDEditText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                gcmRegistrationManager.setPusherAppId(appIDEditText.getText().toString());
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        final EditText senderIDEditText = (EditText)findViewById(R.id.editText_gcm_sender_id);
        senderIDEditText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                gcmRegistrationManager.setSenderId(senderIDEditText.getText().toString());
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        final EditText pusherUrlEditText = (EditText)findViewById(R.id.editText_gcm_pusher_url);
        pusherUrlEditText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                gcmRegistrationManager.setPusherUrl(pusherUrlEditText.getText().toString());
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        final EditText pusherProfileEditText = (EditText)findViewById(R.id.editText_gcm_pusher_profile_tag);
        pusherProfileEditText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                gcmRegistrationManager.setPusherFileTag(pusherProfileEditText.getText().toString());
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        refreshGCMEntries();
    }

    private void listenBoxUpdate(final SharedPreferences preferences, final int boxId, final String preferenceKey, boolean defaultValue) {
        final CheckBox checkBox = (CheckBox) findViewById(boxId);
        checkBox.setChecked(preferences.getBoolean(preferenceKey, defaultValue));
        checkBox.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean(preferenceKey, checkBox.isChecked());
                        editor.commit();

                        // GCM case
                        if (boxId == R.id.checkbox_useGcm) {
                            refreshGCMEntries();
                            if (checkBox.isChecked()) {
                                // Do something here
                            } else {
                                // do something here
                            }
                        }
                    }
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPresenceManager.advertiseAllUnavailableAfterDelay();
    }

    private void refreshGCMEntries() {
        GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(this).getSharedGcmRegistrationManager();

        Boolean debugMode = (0 != ( getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));

        TextView textView  = (TextView)findViewById(R.id.textView_gcm_app_id);
        textView.setVisibility(debugMode ? View.VISIBLE : View.GONE);
        EditText editText = (EditText)findViewById(R.id.editText_gcm_app_id);
        editText.setVisibility(debugMode ? View.VISIBLE : View.GONE);
        editText.setText(gcmRegistrationManager.pusherAppId());

        textView  = (TextView)findViewById(R.id.textView_gcm_sender_id);
        textView.setVisibility(debugMode ? View.VISIBLE : View.GONE);
        editText = (EditText)findViewById(R.id.editText_gcm_sender_id);
        editText.setVisibility(debugMode ? View.VISIBLE : View.GONE);
        editText.setText(gcmRegistrationManager.senderId());

        editText = (EditText)findViewById(R.id.editText_gcm_pusher_url);
        editText.setText(gcmRegistrationManager.pusherUrl());

        editText = (EditText)findViewById(R.id.editText_gcm_pusher_profile_tag);
        editText.setText(gcmRegistrationManager.pusherFileTag());
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.advertiseAllOnline();

        for(MXSession session : Matrix.getMXSessions(this)) {
            final MyUser myUser = session.getMyUser();
            final MXSession fSession = session;

            final LinearLayout linearLayout = mLinearLayoutBySession.get(fSession);

            final View refreshingView = linearLayout.findViewById(R.id.profile_mask);
            refreshingView.setVisibility(View.VISIBLE);

            session.getProfileApiClient().displayname(myUser.userId, new SimpleApiCallback<String>(this) {
                @Override
                public void onSuccess(String displayname) {

                    if ((null != displayname) && !displayname.equals(myUser.displayname)) {
                        myUser.displayname = displayname;
                        EditText displayNameEditText = (EditText) linearLayout.findViewById(R.id.editText_displayName);
                        displayNameEditText.setText(myUser.displayname);
                    }

                    fSession.getProfileApiClient().avatarUrl(myUser.userId, new SimpleApiCallback<String>(this) {
                        @Override
                        public void onSuccess(String avatarUrl) {
                            if ((null != avatarUrl) && !avatarUrl.equals(myUser.avatarUrl)) {
                                mTmpThumbnailUriBySession.remove(fSession);

                                myUser.avatarUrl = avatarUrl;
                                refreshProfileThumbnail(fSession, linearLayout);
                            }

                            refreshingView.setVisibility(View.GONE);
                        }
                    });
                }
            });
        }

        // refresh the cache size
        Button clearCacheButton = (Button) findViewById(R.id.button_clear_cache);
        String cacheSize = android.text.format.Formatter.formatFileSize(this, mMediasCache.cacheSize(this));
        clearCacheButton.setText(getString(R.string.clear_cache)  + " (" + cacheSize + ")");

        refreshGCMEntries();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK) {
                mTmpThumbnailUriBySession.put(mUpdatingSession, data.getData());

                final LinearLayout linearLayout = mLinearLayoutBySession.get(mUpdatingSession);
                ImageView avatarView = (ImageView) linearLayout.findViewById(R.id.imageView_avatar);
                avatarView.setImageURI(data.getData());

                final Button saveButton = (Button) linearLayout.findViewById(R.id.button_save);
                saveButton.setEnabled(true); // Enable the save button if it wasn't already
            }

            mUpdatingSession = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (areChanges()) {
            // The user is trying to leave with unsaved changes. Warn about that
            new AlertDialog.Builder(this)
                    .setMessage(R.string.message_unsaved_changes)
                    .setPositiveButton(R.string.stay, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(R.string.leave, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SettingsActivity.super.onBackPressed();
                        }
                    })
                    .create()
                    .show();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveChanges(final MXSession session) {
        LinearLayout linearLayout = mLinearLayoutBySession.get(session);
        EditText displayNameEditText = (EditText) linearLayout.findViewById(R.id.editText_displayName);

        // Save things
        final String nameFromForm = displayNameEditText.getText().toString();

        final ApiCallback<Void> changeCallback = UIUtils.buildOnChangeCallback(this);

        final MyUser myUser = session.getMyUser();
        final Button saveButton = (Button) linearLayout.findViewById(R.id.button_save);

        if (UIUtils.hasFieldChanged(myUser.displayname, nameFromForm)) {
            myUser.updateDisplayName(nameFromForm, new SimpleApiCallback<Void>(changeCallback) {
                @Override
                public void onSuccess(Void info) {
                    super.onSuccess(info);
                    updateSaveButton(saveButton);
                }
            });
        }

        Uri newAvatarUri = mTmpThumbnailUriBySession.get(session);

        if (newAvatarUri != null) {
            Log.d(LOG_TAG, "Selected image to upload: " + newAvatarUri);
            ResourceUtils.Resource resource = ResourceUtils.openResource(this, newAvatarUri);
            if (resource == null) {
                Toast.makeText(SettingsActivity.this,
                        getString(R.string.settings_failed_to_upload_avatar),
                        Toast.LENGTH_LONG).show();
                return;
            }

            final ProgressDialog progressDialog = ProgressDialog.show(this, null, getString(R.string.message_uploading), true);

            session.getContentManager().uploadContent(resource.contentStream, resource.mimeType, null, new ContentManager.UploadCallback() {
                @Override
                public void onUploadProgress(String anUploadId, int percentageProgress) {
                    progressDialog.setMessage(getString(R.string.message_uploading) + " (" + percentageProgress + "%)");
                }

                @Override
                public void onUploadComplete(String anUploadId, ContentResponse uploadResponse, String serverErrorMessage)  {
                    if (uploadResponse == null) {
                        Toast.makeText(SettingsActivity.this,
                                getString(R.string.settings_failed_to_upload_avatar),
                                Toast.LENGTH_LONG).show();
                    }
                    else {
                        Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                        myUser.updateAvatarUrl(uploadResponse.contentUri, new SimpleApiCallback<Void>(changeCallback) {
                            @Override
                            public void onSuccess(Void info) {
                                super.onSuccess(info);
                                // Reset this because its being set is how we know there's been a change
                                mTmpThumbnailUriBySession.remove(session);
                                updateSaveButton(saveButton);
                            }
                        });
                    }
                    progressDialog.dismiss();
                }
            });
        }
    }

    private void updateSaveButton(final Button button) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                button.setEnabled(areChanges());
            }
        });
    }

    private boolean areChanges() {
        if (mTmpThumbnailUriBySession.size() != 0) {
            return true;
        }

        for(MXSession session : Matrix.getMXSessions(this)) {
            LinearLayout linearLayout = mLinearLayoutBySession.get(session);
            EditText displayNameEditText = (EditText) linearLayout.findViewById(R.id.editText_displayName);

           if (UIUtils.hasFieldChanged(session.getMyUser().displayname, displayNameEditText.getText().toString())) {
               return true;
           }
        }

        return false;
    }
}
