/*
 * Copyright 2015 OpenMarket Ltd
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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.ErrorListener;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ViewedRoomTracker;
import org.matrix.matrixandroidsdk.fragments.ConsoleMessageListFragment;
import org.matrix.matrixandroidsdk.fragments.ImageSizeSelectionDialogFragment;
import org.matrix.matrixandroidsdk.fragments.MembersInvitationDialogFragment;
import org.matrix.matrixandroidsdk.fragments.RoomMembersDialogFragment;
import org.matrix.matrixandroidsdk.services.EventStreamService;
import org.matrix.matrixandroidsdk.util.NotificationUtils;
import org.matrix.matrixandroidsdk.util.RageShake;
import org.matrix.matrixandroidsdk.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a single room with messages.
 */
public class RoomActivity extends MXCActionBarActivity {

    public static final String EXTRA_ROOM_ID = "org.matrix.matrixandroidsdk.RoomActivity.EXTRA_ROOM_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGE_LIST = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGE_LIST";
    private static final String TAG_FRAGMENT_MEMBERS_DIALOG = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MEMBERS_DIALOG";
    private static final String TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG";
    private static final String TAG_FRAGMENT_ATTACHMENTS_DIALOG = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_ATTACHMENTS_DIALOG";
    private static final String TAG_FRAGMENT_IMAGE_SIZE_DIALOG = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_IMAGE_SIZE_DIALOG";


    private static final String LOG_TAG = "RoomActivity";
    private static final int TYPING_TIMEOUT_MS = 10000;

    private static final String PENDING_THUMBNAIL_URL = "PENDING_THUMBNAIL_URL";
    private static final String PENDING_MEDIA_URL = "PENDING_MEDIA_URL";
    private static final String PENDING_MIMETYPE = "PENDING_MIMETYPE";

    private static final String CAMERA_VALUE_TITLE = "attachment"; // Samsung devices need a filepath to write to or else won't return a Uri (!!!)

    // defines the command line operations
    // the user can write theses messages to perform some room events
    private static final String CMD_CHANGE_DISPLAY_NAME = "/nick";
    private static final String CMD_EMOTE = "/me";
    private static final String CMD_JOIN_ROOM = "/join";
    private static final String CMD_KICK_USER = "/kick";
    private static final String CMD_BAN_USER = "/ban";
    private static final String CMD_UNBAN_USER = "/unban";
    private static final String CMD_SET_USER_POWER_LEVEL = "/op";
    private static final String CMD_RESET_USER_POWER_LEVEL = "/deop";

    private static final int REQUEST_FILES = 0;
    private static final int TAKE_IMAGE = 1;
    private static final int CREATE_DOCUMENT = 2;

    // max image sizes
    private static final int LARGE_IMAGE_SIZE  = 1024;
    private static final int MEDIUM_IMAGE_SIZE = 768;
    private static final int SMALL_IMAGE_SIZE  = 512;

    private ConsoleMessageListFragment mConsoleMessageListFragment;
    private MXSession mSession;
    private Room mRoom;
    private String mMyUserId;

    private MXLatestChatMessageCache mLatestChatMessageCache;
    private MXMediasCache mMediasCache;

    private ImageButton mSendButton;
    private ImageButton mAttachmentButton;
    private EditText mEditText;

    private View mImagePreviewLayout;
    private ImageView mImagePreviewView;
    private ImageButton mImagePreviewButton;

    private String mPendingImageUrl;
    private String mPendingMediaUrl;
    private String mPendingMimeType;

    private String mLatestTakePictureCameraUri; // has to be String not Uri because of Serializable

    // typing event management
    private Timer mTypingTimer = null;
    private TimerTask mTypingTimerTask;
    private long  mLastTypingDate = 0;

    private Boolean mIgnoreTextUpdate = false;

    private MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            RoomActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    // The various events that could possibly change the room title
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        setTitle(mRoom.getName(mMyUserId));
                    }
                    else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
                        Log.e(LOG_TAG, "Updating room topic.");
                        RoomState roomState = JsonUtils.toRoomState(event.content);
                        setTopic(roomState.topic);
                    }
                }
            });
        }

        @Override
        public void onRoomInitialSyncComplete(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // set general room information
                    setTitle(mRoom.getName(mMyUserId));
                    setTopic(mRoom.getTopic());

                    mConsoleMessageListFragment.onInitialMessagesLoaded();
                }
            });
        }
    };

    /**
     * Laucnh the files selection intent
     */
    private void launchFileSelectionIntent() {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        fileIntent.setType("*/*");
        startActivityForResult(fileIntent, REQUEST_FILES);
    }

    /**
     * Launch the camera
     */
    private void launchCamera() {
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // the following is a fix for buggy 2.x devices
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, CAMERA_VALUE_TITLE + formatter.format(date));
        // The Galaxy S not only requires the name of the file to output the image to, but will also not
        // set the mime type of the picture it just took (!!!). We assume that the Galaxy S takes image/jpegs
        // so the attachment uploader doesn't freak out about there being no mimetype in the content database.
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri dummyUri = null;
        try {
            dummyUri = RoomActivity.this.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }
        catch (UnsupportedOperationException uoe) {
            Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI - no SD card? Attempting to insert into device storage.");
            try {
                dummyUri = RoomActivity.this.getContentResolver().insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Unable to insert camera URI into internal storage. Giving up. "+e);
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI. "+e);
        }
        if (dummyUri != null) {
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, dummyUri);
        }
        // Store the dummy URI which will be set to a placeholder location. When all is lost on samsung devices,
        // this will point to the data we're looking for.
        // Because Activities tend to use a single MediaProvider for all their intents, this field will only be the
        // *latest* TAKE_PICTURE Uri. This is deemed acceptable as the normal flow is to create the intent then immediately
        // fire it, meaning onActivityResult/getUri will be the next thing called, not another createIntentFor.
        RoomActivity.this.mLatestTakePictureCameraUri = dummyUri == null ? null : dummyUri.toString();

        startActivityForResult(captureIntent, TAKE_IMAGE);
    }

    private class ImageSize {
        public int mWidth;
        public int mHeight;

        public ImageSize(ImageSize other) {
            mWidth = other.mWidth;
            mHeight = other.mHeight;
        }

        public ImageSize(int width, int height) {
            mWidth = width;
            mHeight = height;
        }
    }

    /**
     * Resize an ImageSize to fit in a square area with maxSide side
     * @param originalSize the ImageSide to resize
     * @param maxSide the sqaure side.
     * @return the resize
     */
    private ImageSize resizeWithMaxSide(ImageSize originalSize, int maxSide)
    {
        ImageSize resized = new ImageSize(originalSize);

        if ((originalSize.mWidth > maxSide) && (originalSize.mHeight > maxSide))
        {
            double ratioX = ((double)maxSide) / ((double)originalSize.mWidth);
            double ratioY = ((double)maxSide) / ((double)originalSize.mHeight);

            double scale = Math.max(ratioX, ratioY);
            resized.mWidth  *= scale;
            resized.mHeight *= scale;
        }

        return resized;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        // the user has tapped on the "View" notification button
        if ((null != intent.getAction()) && (intent.getAction().startsWith(NotificationUtils.TAP_TO_VIEW_ACTION))) {
            // remove any pending notifications
            NotificationManager notificationsManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationsManager.cancelAll();
        }

        mPendingImageUrl = null;
        mPendingMediaUrl = null;
        mPendingMimeType = null;

        if (null != savedInstanceState) {
            if (savedInstanceState.containsKey(PENDING_THUMBNAIL_URL)) {
                mPendingImageUrl = savedInstanceState.getString(PENDING_THUMBNAIL_URL);
            }

            if (savedInstanceState.containsKey(PENDING_MEDIA_URL)) {
                mPendingMediaUrl = savedInstanceState.getString(PENDING_MEDIA_URL);
            }

            if (savedInstanceState.containsKey(PENDING_MIMETYPE)) {
                mPendingMimeType = savedInstanceState.getString(PENDING_MIMETYPE);
            }
        }

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        Log.i(LOG_TAG, "Displaying " + roomId);

        mEditText = (EditText) findViewById(R.id.editText_messageBox);

        mSendButton = (ImageButton) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // send the previewed image ?
                if (null != mPendingImageUrl) {
                    boolean sendMedia = true;

                    // check if the media could be resized
                    if ("image/jpeg".equals(mPendingMimeType)) {

                        System.gc();
                        FileInputStream imageStream = null;

                        try {
                            Uri uri = Uri.parse(mPendingMediaUrl);
                            final String filename = uri.getPath();
                            imageStream = new FileInputStream (new File(filename));

                            int fileSize =  imageStream.available();

                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                            // get the full size bitmap
                            Bitmap fullSizeBitmap = null;
                            try {
                                fullSizeBitmap = BitmapFactory.decodeStream(imageStream, null, options);
                            } catch (OutOfMemoryError e) {
                            }

                            ImageSize fullImageSize = new ImageSize(options.outWidth, options.outHeight);

                            imageStream.close();

                            // can be rescaled ?
                            if ((null != fullSizeBitmap) &&  (fullImageSize.mWidth > SMALL_IMAGE_SIZE) && (fullImageSize.mHeight > SMALL_IMAGE_SIZE)) {
                                ImageSize largeImageSize = null;

                                if ((fullImageSize.mWidth > LARGE_IMAGE_SIZE) && (fullImageSize.mHeight > LARGE_IMAGE_SIZE)) {
                                    largeImageSize = resizeWithMaxSide(fullImageSize, LARGE_IMAGE_SIZE);
                                }

                                ImageSize mediumImageSize = null;

                                if ((fullImageSize.mWidth > MEDIUM_IMAGE_SIZE) && (fullImageSize.mHeight > MEDIUM_IMAGE_SIZE)) {
                                    mediumImageSize = resizeWithMaxSide(fullImageSize, MEDIUM_IMAGE_SIZE);
                                }

                                ImageSize smallImageSize = resizeWithMaxSide(fullImageSize, SMALL_IMAGE_SIZE);

                                if ((fullImageSize.mWidth > MEDIUM_IMAGE_SIZE) && (fullImageSize.mHeight > MEDIUM_IMAGE_SIZE)) {
                                    mediumImageSize = resizeWithMaxSide(fullImageSize, MEDIUM_IMAGE_SIZE);
                                }

                                FragmentManager fm = getSupportFragmentManager();
                                ImageSizeSelectionDialogFragment fragment = (ImageSizeSelectionDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_IMAGE_SIZE_DIALOG);

                                if (fragment != null) {
                                    fragment.dismissAllowingStateLoss();
                                }

                                final ArrayList<String> textsList = new ArrayList<String>();
                                final ArrayList<ImageSize> sizesList = new ArrayList<ImageSize>();

                                textsList.add(getString(R.string.compression_opt_list_original) + " " + fullImageSize.mWidth + "x" + fullImageSize.mHeight + " (" + android.text.format.Formatter.formatFileSize(RoomActivity.this, fileSize) + ")");
                                sizesList.add(fullImageSize);

                                if (null != largeImageSize) {
                                    int estFileSize = largeImageSize.mWidth * largeImageSize.mHeight * 2 / 10 / 1024 * 1024;

                                    textsList.add(getString(R.string.compression_opt_list_large) + " " + largeImageSize.mWidth + "x" + largeImageSize.mHeight + " (" + android.text.format.Formatter.formatFileSize(RoomActivity.this, estFileSize) + ")");
                                    sizesList.add(largeImageSize);
                                }

                                if (null != mediumImageSize) {
                                    int estFileSize = mediumImageSize.mWidth * mediumImageSize.mHeight * 2 / 10 / 1024 * 1024;

                                    textsList.add(getString(R.string.compression_opt_list_medium) + " " + mediumImageSize.mWidth + "x" + mediumImageSize.mHeight + " (" + android.text.format.Formatter.formatFileSize(RoomActivity.this, estFileSize) + ")");
                                    sizesList.add(mediumImageSize);
                                }

                                if (null != smallImageSize) {
                                    int estFileSize = smallImageSize.mWidth * smallImageSize.mHeight * 2 / 10 / 1024 * 1024;

                                    textsList.add(getString(R.string.compression_opt_list_small) + " " + smallImageSize.mWidth + "x" + smallImageSize.mHeight + " (" + android.text.format.Formatter.formatFileSize(RoomActivity.this, estFileSize) + ")");
                                    sizesList.add(smallImageSize);
                                }

                                final Bitmap ffullSizeBitmap = fullSizeBitmap;

                                fragment = ImageSizeSelectionDialogFragment.newInstance(textsList);
                                fragment.setListener( new ImageSizeSelectionDialogFragment.ImageSizeListener() {
                                    @Override
                                    public void onSelected(int pos) {
                                        final int fPos = pos;

                                        RoomActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    // pos == 0 -> original
                                                    if (0 != fPos) {
                                                        ImageSize imageSize = sizesList.get(fPos);
                                                        Bitmap resizeBitmap = null;

                                                        try {
                                                            resizeBitmap = Bitmap.createScaledBitmap(ffullSizeBitmap, imageSize.mWidth, imageSize.mHeight, false);
                                                        } catch (OutOfMemoryError ex) {
                                                            ex = ex;
                                                        }

                                                        if (null != resizeBitmap) {
                                                            String bitmapURL = mMediasCache.saveBitmap(resizeBitmap, RoomActivity.this, null);

                                                            // try to reduce used memory
                                                            if (null != resizeBitmap) {
                                                                resizeBitmap.recycle();
                                                            }

                                                            if (null != bitmapURL) {
                                                                mPendingMediaUrl = bitmapURL;
                                                            }
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                }

                                                //
                                                mConsoleMessageListFragment.uploadImageContent(mPendingImageUrl, mPendingMediaUrl, mPendingMimeType);
                                                mPendingImageUrl = null;
                                                mPendingMediaUrl = null;
                                                mPendingMimeType = null;
                                                manageSendMoreButtons();
                                            }
                                        });
                                    }
                                });

                                fragment.show(fm, TAG_FRAGMENT_IMAGE_SIZE_DIALOG);
                                sendMedia = false;
                            }

                        } catch (Exception e) {
                            e = e;
                        }
                    }

                    if (sendMedia) {
                        mConsoleMessageListFragment.uploadImageContent(mPendingImageUrl, mPendingMediaUrl, mPendingMimeType);
                        mPendingImageUrl = null;
                        mPendingMediaUrl = null;
                        mPendingMimeType = null;
                        manageSendMoreButtons();
                    }
                } else {
                    String body = mEditText.getText().toString();
                    sendMessage(body);
                    mEditText.setText("");
                }
            }
        });

        mAttachmentButton = (ImageButton) findViewById(R.id.button_more);
        mAttachmentButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                FragmentManager fm = getSupportFragmentManager();
                IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_ATTACHMENTS_DIALOG);

                if (fragment != null) {
                    fragment.dismissAllowingStateLoss();
                }

                final Integer[] messages = new Integer[]{
                        R.string.option_send_files,
                        R.string.option_take_photo,
                };

                final Integer[] icons = new Integer[]{
                        R.drawable.ic_material_file,  // R.string.option_send_files
                        R.drawable.ic_material_camera, // R.string.action_members
                };


                fragment = IconAndTextDialogFragment.newInstance(icons, messages);
                fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
                    @Override
                    public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                        Integer selectedVal = messages[position];

                        if (selectedVal == R.string.option_send_files) {
                            RoomActivity.this.launchFileSelectionIntent();
                        } else if (selectedVal == R.string.option_take_photo) {
                            RoomActivity.this.launchCamera();
                        }
                    }
                });

                fragment.show(fm, TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG);
            }
        });

        mEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                MXLatestChatMessageCache latestChatMessageCache = RoomActivity.this.mLatestChatMessageCache;

                String textInPlace = latestChatMessageCache.getLatestText(RoomActivity.this, mRoom.getRoomId());

                // check if there is really an update
                // avoid useless updates (initializations..)
                if (!mIgnoreTextUpdate && !textInPlace.equals(mEditText.getText().toString())) {
                    latestChatMessageCache.updateLatestMessage(RoomActivity.this, mRoom.getRoomId(), mEditText.getText().toString());
                    handleTypingNotification(mEditText.getText().length() != 0);
                }

                manageSendMoreButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mSession = getSession(intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        mMyUserId = mSession.getCredentials().userId;

        CommonActivityUtils.resumeEventStream(this);

        mRoom = mSession.getDataHandler().getRoom(roomId);

        FragmentManager fm = getSupportFragmentManager();
        mConsoleMessageListFragment = (ConsoleMessageListFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGE_LIST);

        if (mConsoleMessageListFragment == null) {
            // this fragment displays messages and handles all message logic
            mConsoleMessageListFragment = ConsoleMessageListFragment.newInstance(mMyUserId, mRoom.getRoomId(), org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.anchor_fragment_messages, mConsoleMessageListFragment, TAG_FRAGMENT_MATRIX_MESSAGE_LIST).commit();
        }

        // set general room information
        setTitle(mRoom.getName(mMyUserId));
        setTopic(mRoom.getTopic());

        // listen for room name or topic changes
        mRoom.addEventListener(mEventListener);

        // The error listener needs the current activity
        mSession.setFailureCallback(new ErrorListener(this));

        mImagePreviewLayout = findViewById(R.id.room_image_preview_layout);
        mImagePreviewView   = (ImageView)findViewById(R.id.room_image_preview);
        mImagePreviewButton = (ImageButton)findViewById(R.id.room_image_preview_cancel_button);

        // the user cancels the image selection
        mImagePreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPendingImageUrl = null;
                mPendingMediaUrl = null;
                mPendingMimeType = null;
                manageSendMoreButtons();
            }
        });

        mLatestChatMessageCache = Matrix.getInstance(this).getDefaultLatestChatMessageCache();
        mMediasCache = Matrix.getInstance(this).getMediasCache();

        // some medias must be sent while opening the chat
        if (intent.hasExtra(HomeActivity.EXTRA_ROOM_INTENT)) {
            final Intent mediaIntent = intent.getParcelableExtra(HomeActivity.EXTRA_ROOM_INTENT);

            // sanity check
            if (null != mediaIntent) {
                mEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sendMediasIntent(mediaIntent);
                    }
                }, 1000);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        if (null != mPendingImageUrl) {
            savedInstanceState.putString(PENDING_THUMBNAIL_URL, mPendingImageUrl);
        }

        if (null != mPendingMediaUrl) {
            savedInstanceState.putString(PENDING_MEDIA_URL, mPendingMediaUrl);
        }

        if (null != mPendingMimeType) {
            savedInstanceState.putString(PENDING_MIMETYPE, mPendingMimeType);
        }
    }

    /**
     *
     */
    private void manageSendMoreButtons() {
        boolean hasText = mEditText.getText().length() > 0;
        boolean hasPreviewedMedia = (null != mPendingImageUrl);

        if (hasPreviewedMedia) {
            mMediasCache.loadBitmap(mImagePreviewView, mPendingImageUrl, 0, mPendingMimeType);
        }

        mImagePreviewLayout.setVisibility(hasPreviewedMedia ? View.VISIBLE : View.GONE);
        mEditText.setVisibility(hasPreviewedMedia ? View.INVISIBLE : View.VISIBLE);

        mSendButton.setVisibility((hasText || hasPreviewedMedia) ? View.VISIBLE : View.INVISIBLE);
        mAttachmentButton.setVisibility((hasText || hasPreviewedMedia)  ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        // add sanity check
        // the activity creation could have been cancelled because the roomId was missing
        if ((null != mRoom) && (null != mEventListener)) {
            mRoom.removeEventListener(mEventListener);
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPresenceManager.getInstance(this, mSession).advertiseUnavailableAfterDelay();
        // warn other member that the typing is ended
        cancelTypingNotification();
    }

    @Override
    public void finish() {
        super.finish();

        // do not reset ViewedRoomTracker in onPause
        // else the messages received while the application is in background
        // are marked as unread in the home/recents activity.
        // Assume that the finish method is the right place to manage it.
        ViewedRoomTracker.getInstance().setViewedRoomId(null);
        ViewedRoomTracker.getInstance().setMatrixId(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ViewedRoomTracker.getInstance().setViewedRoomId(mRoom.getRoomId());
        ViewedRoomTracker.getInstance().setMatrixId(mSession.getCredentials().userId);
        MyPresenceManager.getInstance(this, mSession).advertiseOnline();

        EventStreamService.cancelNotificationsForRoomId(mRoom.getRoomId());

        String cachedText = Matrix.getInstance(this).getDefaultLatestChatMessageCache().getLatestText(this, mRoom.getRoomId());

        if (!cachedText.equals(mEditText.getText().toString())) {
            mIgnoreTextUpdate = true;
            mEditText.setText("");
            mEditText.append(cachedText);
            mIgnoreTextUpdate = false;
        }

        manageSendMoreButtons();

        // refresh the UI : the timezone could have been updated
        mConsoleMessageListFragment.refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.room, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_invite_by_list) {
            FragmentManager fm = getSupportFragmentManager();

            MembersInvitationDialogFragment fragment = (MembersInvitationDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG);
            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }
            fragment = MembersInvitationDialogFragment.newInstance(mSession, mRoom.getRoomId());
            fragment.show(fm, TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG);
        } else if (id == R.id.ic_action_invite_by_name) {
            AlertDialog alert = CommonActivityUtils.createEditTextAlert(RoomActivity.this, RoomActivity.this.getResources().getString(R.string.title_activity_invite_user), RoomActivity.this.getResources().getString(R.string.room_creation_participants_hint), null, new CommonActivityUtils.OnSubmitListener() {
                @Override
                public void onSubmit(final String text) {
                    if (TextUtils.isEmpty(text)) {
                        return;
                    }

                    // get the user suffix
                    String homeServerSuffix = mMyUserId.substring(mMyUserId.indexOf(":"), mMyUserId.length());

                    ArrayList<String> userIDsList = CommonActivityUtils.parseUserIDsList(text, homeServerSuffix);

                    if (userIDsList.size() > 0) {
                        mRoom.invite(userIDsList, new SimpleApiCallback<Void>(RoomActivity.this) {
                            @Override
                            public void onSuccess(Void info) {
                                Toast.makeText(getApplicationContext(), "Sent invite to " + text.trim() + ".", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }

                @Override
                public void onCancelled() {

                }
            });

            alert.show();
        } else if (id ==  R.id.ic_action_members) {
            FragmentManager fm = getSupportFragmentManager();

            RoomMembersDialogFragment fragment = (RoomMembersDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_MEMBERS_DIALOG);
            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }
            fragment = RoomMembersDialogFragment.newInstance(mSession, mRoom.getRoomId());
            fragment.show(fm, TAG_FRAGMENT_MEMBERS_DIALOG);
        } else if (id ==  R.id.ic_action_room_info) {
            Intent startRoomInfoIntent = new Intent(RoomActivity.this, RoomInfoActivity.class);
            startRoomInfoIntent.putExtra(EXTRA_ROOM_ID, mRoom.getRoomId());
            startRoomInfoIntent.putExtra(EXTRA_MATRIX_ID, mMyUserId);
            startActivity(startRoomInfoIntent);
        } else if (id ==  R.id.ic_action_leave) {
            mRoom.leave(new SimpleApiCallback<Void>(RoomActivity.this) {
            });
            RoomActivity.this.finish();
        } else if (id ==  R.id.ic_action_settings) {
            RoomActivity.this.startActivity(new Intent(RoomActivity.this, SettingsActivity.class));
        } else if (id ==  R.id.ic_send_bug_report) {
            RageShake.getInstance().sendBugReport();
        }

        return super.onOptionsItemSelected(item);
    }

    private void setTopic(String topic) {
        if (null != this.getSupportActionBar()) {
            this.getSupportActionBar().setSubtitle(topic);
        }
    }

    /**
     * check if the text message is an IRC command.
     * If it is an IRC command, it is executed
     * @param body
     * @return true if body defines an IRC command
     */
    private boolean manageIRCCommand(String body) {
        boolean isIRCCmd = false;

        // check if it has the IRC marker
        if ((null != body) && (body.startsWith("/"))) {
            final ApiCallback callback = new SimpleApiCallback<Void>(this) {
                @Override
                public void onMatrixError(MatrixError e) {
                    if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                        Toast.makeText(RoomActivity.this, e.error, Toast.LENGTH_LONG).show();
                    }
                }
            };

            if (body.startsWith(CMD_CHANGE_DISPLAY_NAME)) {
                isIRCCmd = true;

                String newDisplayname = body.substring(CMD_CHANGE_DISPLAY_NAME.length()).trim();

                if (newDisplayname.length() > 0) {
                    MyUser myUser = mSession.getMyUser();

                    myUser.updateDisplayName(newDisplayname, callback);
                }
            } else if (body.startsWith(CMD_EMOTE)) {
                isIRCCmd = true;

                String message = body.substring(CMD_EMOTE.length()).trim();

                if (message.length() > 0) {
                    mConsoleMessageListFragment.sendEmote(message);
                }
            } else if (body.startsWith(CMD_JOIN_ROOM)) {
                isIRCCmd = true;

                String roomAlias = body.substring(CMD_JOIN_ROOM.length()).trim();

                if (roomAlias.length() > 0) {
                    mSession.joinRoom(roomAlias,new SimpleApiCallback<String>(this) {

                        @Override
                        public void onSuccess(String roomId) {
                            if (null != roomId) {
                                CommonActivityUtils.goToRoomPage(mSession, roomId, RoomActivity.this, null);
                            }
                        }
                    });
                }
            } else if (body.startsWith(CMD_KICK_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_KICK_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String kickedUserID = paramsList[0];

                if (kickedUserID.length() > 0) {
                    mRoom.kick(kickedUserID, callback);
                }
            } else if (body.startsWith(CMD_BAN_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_BAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String bannedUserID = paramsList[0];
                String reason = params.substring(bannedUserID.length()).trim();

                if (bannedUserID.length() > 0) {
                    mRoom.ban(bannedUserID, reason, callback);
                }
            } else if (body.startsWith(CMD_UNBAN_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_UNBAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String unbannedUserID = paramsList[0];

                if (unbannedUserID.length() > 0) {
                    mRoom.unban(unbannedUserID, callback);
                }
            } else if (body.startsWith(CMD_SET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = body.substring(CMD_SET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];
                String powerLevelsAsString  = params.substring(userID.length()).trim();

                try {
                    if ((userID.length() > 0) && (powerLevelsAsString.length() > 0)) {
                        mRoom.updateUserPowerLevels(userID, Integer.parseInt(powerLevelsAsString), callback);
                    }
                } catch(Exception e){

                }
            } else if (body.startsWith(CMD_RESET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = body.substring(CMD_RESET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];

                if (userID.length() > 0) {
                    mRoom.updateUserPowerLevels(userID, 0, callback);
                }
            }
        }

        return isIRCCmd;
    }

    private void sendMessage(String body) {
        if (!TextUtils.isEmpty(body)) {
            if (!manageIRCCommand(body)) {
                mConsoleMessageListFragment.sendTextMessage(body);
            }
        }
    }

    /**
     * Send a list of images from their URIs
     * @param mediaUris the media URIs
     */
    private void sendMedias(ArrayList<Uri> mediaUris) {
        final int mediaCount = mediaUris.size();

        for(Uri anUri : mediaUris) {
            final Uri mediaUri = anUri;

            RoomActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    ResourceUtils.Resource resource = ResourceUtils.openResource(RoomActivity.this, mediaUri);
                    if (resource == null) {
                        Toast.makeText(RoomActivity.this,
                                getString(R.string.message_failed_to_upload),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // save the file in the filesystem
                    String mediaUrl = mMediasCache.saveMedia(resource.contentStream, RoomActivity.this, null, resource.mimeType);
                    String mimeType = resource.mimeType;
                    Boolean isManaged = false;

                    if ((null != resource.mimeType) && resource.mimeType.startsWith("image/")) {
                        // manage except if there is an error
                        isManaged = true;

                        // try to retrieve the gallery thumbnail
                        // if the image comes from the gallery..
                        Bitmap thumbnailBitmap = null;

                        try {
                            ContentResolver resolver = getContentResolver();
                            List uriPath = mediaUri.getPathSegments();
                            long imageId = Long.parseLong((String) (uriPath.get(uriPath.size() - 1)));

                            thumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
                        } catch (Exception e) {

                        }

                        // no thumbnail has been found or the mimetype is unknown
                        if (null == thumbnailBitmap) {
                            // need to decompress the high res image
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            resource = ResourceUtils.openResource(RoomActivity.this, mediaUri);

                            // get the full size bitmap
                            Bitmap fullSizeBitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);

                            // create a thumbnail bitmap if there is none
                            if (null == thumbnailBitmap) {
                                if (fullSizeBitmap != null) {
                                    double fullSizeWidth = fullSizeBitmap.getWidth();
                                    double fullSizeHeight = fullSizeBitmap.getHeight();

                                    double thumbnailWidth = mConsoleMessageListFragment.getMaxThumbnailWith();
                                    double thumbnailHeight = mConsoleMessageListFragment.getMaxThumbnailHeight();

                                    if (fullSizeWidth > fullSizeHeight) {
                                        thumbnailHeight = thumbnailWidth * fullSizeHeight / fullSizeWidth;
                                    } else {
                                        thumbnailWidth = thumbnailHeight * fullSizeWidth / fullSizeHeight;
                                    }

                                    try {
                                        thumbnailBitmap = Bitmap.createScaledBitmap(fullSizeBitmap, (int) thumbnailWidth, (int) thumbnailHeight, false);
                                    } catch (OutOfMemoryError ex) {
                                    }
                                }
                            }

                            // unknown mimetype
                            if ((null == mimeType) || (mimeType.startsWith("image/"))) {
                                try {
                                    if (null != fullSizeBitmap) {
                                        Uri uri = Uri.parse(mediaUrl);

                                        if (null == mimeType) {
                                            // the images are save in jpeg format
                                            mimeType = "image/jpeg";
                                        }

                                        resource.contentStream.close();
                                        resource = ResourceUtils.openResource(RoomActivity.this, mediaUri);

                                        try {
                                            mMediasCache.saveMedia(resource.contentStream, RoomActivity.this, uri.getPath(), mimeType);
                                        } catch (OutOfMemoryError ex) {
                                        }

                                    } else {
                                        isManaged = false;
                                    }

                                    resource.contentStream.close();

                                } catch (Exception e) {
                                    isManaged = false;
                                }
                            }

                            // reduce the memory consumption
                            if (null  != fullSizeBitmap) {
                                fullSizeBitmap.recycle();
                                System.gc();
                            }
                        }

                        String thumbnailURL = mMediasCache.saveBitmap(thumbnailBitmap, RoomActivity.this, null);

                        if (null != thumbnailBitmap) {
                            thumbnailBitmap.recycle();
                        }

                        // is the image content valid ?
                        if (isManaged  && (null != thumbnailURL)) {

                            // if there is only one image
                            if (mediaCount == 1) {
                                // display an image preview before sending it
                                mPendingImageUrl = thumbnailURL;
                                mPendingMediaUrl = mediaUrl;
                                mPendingMimeType = mimeType;

                                mConsoleMessageListFragment.scrollToBottom();

                                manageSendMoreButtons();
                            } else {
                                mConsoleMessageListFragment.uploadImageContent(thumbnailURL, mediaUrl, mimeType);
                            }
                        }
                    }

                    // default behaviour
                    if ((!isManaged) && (null != mediaUrl)) {
                        String filename = "A file";

                        try {
                            ContentResolver resolver = getContentResolver();
                            List uriPath = mediaUri.getPathSegments();
                            filename = "";

                            if (mediaUri.toString().startsWith("content://")) {
                                Cursor cursor = null;
                                try {
                                    cursor = RoomActivity.this.getContentResolver().query(mediaUri, null, null, null, null);
                                    if (cursor != null && cursor.moveToFirst()) {
                                        filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                                    }
                                } catch (Exception e) {

                                }
                                finally {
                                    cursor.close();
                                }

                                if (filename.length() == 0) {
                                    filename = (String)uriPath.get(uriPath.size() - 1);
                                }
                            }

                        } catch (Exception e) {

                        }

                        mConsoleMessageListFragment.uploadMediaContent(mediaUrl, mimeType, filename);
                    }
                }
            });
        }
    }

    @SuppressLint("NewApi")
    private void sendMediasIntent(final Intent data) {
        // sanity check
        if ((null == data) && (null == mLatestTakePictureCameraUri)) {
            return;
        }

        ArrayList<Uri> uris = new ArrayList<Uri>();

        if (null != data) {
            ClipData clipData = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                clipData = data.getClipData();
            }

            // multiple data
            if (null != clipData) {
                int count = clipData.getItemCount();

                for (int i = 0; i < count; i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    Uri uri = item.getUri();

                    if (null != uri) {
                        uris.add(uri);
                    }
                }

            } else if (null != data.getData()) {
                uris.add(data.getData());
            }
        } else {
            uris.add( mLatestTakePictureCameraUri == null ? null : Uri.parse(mLatestTakePictureCameraUri));
            mLatestTakePictureCameraUri = null;
        }

        // check the extras
        if (0 == uris.size()) {
            Bundle bundle = data.getExtras();

            if (bundle.containsKey(Intent.EXTRA_STREAM)) {
                Object streamUri = bundle.get(Intent.EXTRA_STREAM);

                if (streamUri instanceof Uri) {
                    uris.add((Uri)streamUri);
                }
            } else if (bundle.containsKey(Intent.EXTRA_TEXT)) {
                this.sendMessage(bundle.getString(Intent.EXTRA_TEXT));
            }
        }

        if (0 != uris.size()) {
            sendMedias(uris);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if ((requestCode == REQUEST_FILES) || (requestCode == TAKE_IMAGE)) {
                sendMediasIntent(data);
            } else if (requestCode == CREATE_DOCUMENT) {
                Uri currentUri = data.getData();
                writeMediaUrl(currentUri);
            }
        }

        if (requestCode == CREATE_DOCUMENT) {
            mPendingMediaUrl = null;
            mPendingMimeType = null;
        }
    }

    /**
     *
     * @param message
     * @param mediaUrl
     * @param mediaMimeType
     */
    public void createDocument(Message message, final String mediaUrl, final String mediaMimeType) {
        String filename = "MatrixConsole_" + System.currentTimeMillis();

        MimeTypeMap mime = MimeTypeMap.getSingleton();
        filename += "." + mime.getExtensionFromMimeType(mediaMimeType);

        if (message instanceof FileMessage) {
            FileMessage fileMessage = (FileMessage)message;

            if (null != fileMessage.body) {
                filename = fileMessage.body;
            }
        }

        mPendingMediaUrl = mediaUrl;
        mPendingMimeType = mediaMimeType;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mediaMimeType)
                .putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(intent, CREATE_DOCUMENT);

    }


	private void writeMediaUrl(Uri destUri)
	{
		try{
			ParcelFileDescriptor pfd =
				this.getContentResolver().
                		openFileDescriptor(destUri, "w");

			FileOutputStream fileOutputStream =
                           new FileOutputStream(pfd.getFileDescriptor());

            String sourceFilePath = mMediasCache.mediaCacheFilename(this, mPendingMediaUrl, mPendingMimeType);

            InputStream inputStream = this.openFileInput(sourceFilePath);

            byte[] buffer = new byte[1024 * 10];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, len);
            }

			fileOutputStream.close();
			pfd.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    /**
     * send a typing event notification
     * @param isTyping typing param
     */
    void handleTypingNotification(boolean isTyping) {
        int notificationTimeoutMS = -1;
        if (isTyping) {
            // Check whether a typing event has been already reported to server (We wait for the end of the local timout before considering this new event)
            if (null != mTypingTimer) {
                // Refresh date of the last observed typing
                System.currentTimeMillis();
                mLastTypingDate = System.currentTimeMillis();
                return;
            }

            int timerTimeoutInMs = TYPING_TIMEOUT_MS;

            if (0 != mLastTypingDate) {
                long lastTypingAge = System.currentTimeMillis() - mLastTypingDate;
                if (lastTypingAge < timerTimeoutInMs) {
                    // Subtract the time interval since last typing from the timer timeout
                    timerTimeoutInMs -= lastTypingAge;
                } else {
                    timerTimeoutInMs = 0;
                }
            } else {
                // Keep date of this typing event
                mLastTypingDate = System.currentTimeMillis();
            }

            if (timerTimeoutInMs > 0) {
                mTypingTimer = new Timer();
                mTypingTimerTask = new TimerTask() {
                    public void run() {
                        if (mTypingTimerTask != null) {
                            mTypingTimerTask.cancel();
                            mTypingTimerTask = null;
                        }

                        if (mTypingTimer != null) {
                            mTypingTimer.cancel();
                            mTypingTimer = null;
                        }
                        // Post a new typing notification
                        RoomActivity.this.handleTypingNotification(0 != mLastTypingDate);
                    }
                };
                mTypingTimer.schedule(mTypingTimerTask, TYPING_TIMEOUT_MS);

                // Compute the notification timeout in ms (consider the double of the local typing timeout)
                notificationTimeoutMS = TYPING_TIMEOUT_MS * 2;
            } else {
                // This typing event is too old, we will ignore it
                isTyping = false;
            }
        }
        else {
            // Cancel any typing timer
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }

            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }
            // Reset last typing date
            mLastTypingDate = 0;
        }

        final boolean typingStatus = isTyping;

        mRoom.sendTypingNotification(typingStatus, notificationTimeoutMS, new SimpleApiCallback<Void>(RoomActivity.this) {
            @Override
            public void onSuccess(Void info) {
                // Reset last typing date
                mLastTypingDate = 0;
            }

            @Override
            public void onNetworkError(Exception e) {
                if (mTypingTimerTask != null) {
                    mTypingTimerTask.cancel();
                    mTypingTimerTask = null;
                }

                if (mTypingTimer != null) {
                    mTypingTimer.cancel();
                    mTypingTimer = null;
                }
                // do not send again
                // assume that the typing event is optional
            }
        });
    }

    void cancelTypingNotification() {
        if (0 != mLastTypingDate) {
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }
            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }

            mLastTypingDate = 0;

            mRoom.sendTypingNotification(false, -1, new SimpleApiCallback<Void>(RoomActivity.this) {
            });
        }
    }
}
