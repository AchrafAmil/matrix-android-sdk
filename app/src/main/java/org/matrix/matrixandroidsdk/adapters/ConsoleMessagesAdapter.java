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

package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.content.Intent;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;
import org.matrix.matrixandroidsdk.activity.ImageWebViewActivity;
import org.matrix.matrixandroidsdk.activity.MemberDetailsActivity;

/**
 * An adapter which can display room information.
 */
public class ConsoleMessagesAdapter extends MessagesAdapter {

    public ConsoleMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        super(session, context, mediasCache);
    }

    @Override
    public void onAvatarClick(String roomId, String userId){
        Intent startRoomInfoIntent = new Intent(mContext, MemberDetailsActivity.class);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_ROOM_ID, roomId);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MEMBER_ID, userId);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
        mContext.startActivity(startRoomInfoIntent);
    }

    @Override
    public void onImageClick(ImageMessage imageMessage, int maxImageWidth, int maxImageHeight, int rotationAngle){
        if (null != imageMessage.url) {
            Intent viewImageIntent = new Intent(mContext, ImageWebViewActivity.class);
            viewImageIntent.putExtra(ImageWebViewActivity.KEY_HIGHRES_IMAGE_URI, imageMessage.url);
            viewImageIntent.putExtra(ImageWebViewActivity.KEY_THUMBNAIL_WIDTH, maxImageWidth);
            viewImageIntent.putExtra(ImageWebViewActivity.KEY_THUMBNAIL_HEIGHT, maxImageHeight);
            viewImageIntent.putExtra(ImageWebViewActivity.KEY_IMAGE_ROTATION, rotationAngle);
            if (null != imageMessage.getMimeType()) {
                viewImageIntent.putExtra(ImageWebViewActivity.KEY_HIGHRES_MIME_TYPE, imageMessage.getMimeType());
            }
            mContext.startActivity(viewImageIntent);
        }
    }

    @Override
    public void onFileDownloaded(FileMessage fileMessage) {
        // save into the downloads
        String mediaPath = mMediasCache.mediaCacheFilename(ConsoleMessagesAdapter.this.mContext, fileMessage.url, fileMessage.getMimeType());

        if (null != mediaPath) {
            CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaPath, fileMessage.body, fileMessage.getMimeType());
        }
    }

    @Override
    public void onFileClick(FileMessage fileMessage) {
        if (null != fileMessage.url) {
            String mediaPath =  mMediasCache.mediaCacheFilename(ConsoleMessagesAdapter.this.mContext, fileMessage.url, fileMessage.getMimeType());

            // is the file already saved
            if (null != mediaPath) {
                String savedMediaPath = CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaPath, fileMessage.body, fileMessage.getMimeType());
                CommonActivityUtils.openMedia(ConsoleApplication.getCurrentActivity(), savedMediaPath, fileMessage.getMimeType());
            }
        }
    }
}
