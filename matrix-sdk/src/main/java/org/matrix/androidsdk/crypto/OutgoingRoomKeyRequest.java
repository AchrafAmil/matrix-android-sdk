/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Represents an outgoing room key request
 */
public class OutgoingRoomKeyRequest implements Serializable {

    public enum RequestState {
        // request not yet sent
        UNSENT,
        // request sent, awaiting reply
        SENT,

        // sending failed
        FAILED
    }

    // Unique id for this request. Used for both
    // an id within the request for later pairing with a cancellation, and for
    // the transaction id when sending the to_device messages to our local
    public String mRequestId;

    // transaction id for the cancellation, if any
    public String mCancellationTxnId;

    // list of recipients for the request
    public List<Map<String, String>> mRecipients;

    // RequestBody
    public Map<String, String> mRequestBody;

    // current state of this request (states are defined
    public RequestState mState;

    public OutgoingRoomKeyRequest(Map<String, String> requestBody, List<Map<String, String>> recipients, String requestId, RequestState state) {
        mRequestBody = requestBody;
        mRecipients = recipients;
        mRequestId = requestId;
        mState = state;
    }

    /**
     * @return the room id
     */
    public String getRoomId() {
        if (null != mRequestBody) {
            return mRequestBody.get("room_id");
        }

        return null;
    }

    /**
     * @return the session id
     */
    public String getSessionId() {
        if (null != mRequestBody) {
            return mRequestBody.get("session_id");
        }

        return null;
    }
}

