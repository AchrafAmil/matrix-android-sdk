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

package org.matrix.matrixandroidsdk.contacts;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A simple contact class
 */
public class Contact {

    public String mContactId;
    public String mDisplayName;
    private String mUpperCaseDisplayName = "";
    private String mLowerCaseDisplayName = "";
    public String mThumbnailUri;
    public Bitmap mThumbnail = null;

    public ArrayList<String>mPhoneNumbers = new ArrayList<String>();
    public ArrayList<String>mEmails = new ArrayList<String>();
    public HashMap<String, String> mMatrixIdsByElement = null;

    /**
     * Check if some matrix IDs are linked to emails
     * @return true if some matrix IDs have been retrieved
     */
    public boolean hasMatridIds(Context context) {
        Boolean localUpdateOnly = (null != mMatrixIdsByElement);

        // the PIDs are not yet retrieved
        if (null == mMatrixIdsByElement) {
            mMatrixIdsByElement = new HashMap<String, String>();
        }

        if (couldContainMatridIds()) {
            PIDsRetriever.retrieveMatrixIds(context, this, localUpdateOnly);
        }

        return (mMatrixIdsByElement.size() != 0);
    }

    /**
     * Check if the contact could contain some matrix Ids
     * @return true if the contact could contain some matrix IDs
     */
    public boolean couldContainMatridIds() {
        return (0 != mEmails.size());
    }

    // assume that the search is performed on all the existing contacts
    // so apply upper / lower case only once
    static String mCurrentPattern = "";
    static String mUpperCasePattern = "";
    static String mLowerCasePattern = "";

    /**
     * test if some fields match with the pattern
     * @param pattern
     * @return
     */
    public boolean matchWithPattern(String pattern) {
        // no pattern -> true
        if (TextUtils.isEmpty(pattern)) {
            mCurrentPattern = "";
            mUpperCasePattern = "";
            mLowerCasePattern = "";
        }

        // no display name
        if (TextUtils.isEmpty(mDisplayName)) {
            return false;
        }

        if (TextUtils.isEmpty(mUpperCaseDisplayName)) {
            mUpperCaseDisplayName = mDisplayName.toLowerCase();
            mLowerCaseDisplayName = mDisplayName.toUpperCase();
        }

        if (!pattern.equals(mCurrentPattern)) {
            mCurrentPattern = pattern;
            mUpperCasePattern = pattern.toUpperCase();
            mLowerCasePattern = pattern.toLowerCase();
        }

        return (mUpperCaseDisplayName.indexOf(mUpperCasePattern) >= 0) || (mLowerCaseDisplayName.indexOf(mUpperCasePattern) >= 0);
    }

    /**
     * Returns the first retrieved matrix ID.
     * @return the first retrieved matrix ID.
     */
    public String getFirstMatrixId() {
        if (mMatrixIdsByElement.size() != 0) {
            return mMatrixIdsByElement.values().iterator().next();
        } else {
            return null;
        }
    }
}

