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

package org.matrix.androidsdk.crypto.algorithms.olm;

import android.text.TextUtils;

import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.MXDecryptionException;
import org.matrix.androidsdk.crypto.MXEventDecryptionResult;
import org.matrix.androidsdk.rest.model.OlmEventContent;
import org.matrix.androidsdk.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * An interface for encrypting data
 */
public class MXOlmDecryption implements IMXDecrypting {
    private static final String LOG_TAG = "MXOlmDecryption";

    // The olm device interface
    private MXOlmDevice mOlmDevice;

    // the matrix session
    private MXSession mSession;

    @Override
    public void initWithMatrixSession(MXSession matrixSession) {
        mSession = matrixSession;
        mOlmDevice = matrixSession.getCrypto().getOlmDevice();
    }

    @Override
    public MXEventDecryptionResult decryptEvent(Event event, String timeline) throws MXDecryptionException {
        // sanity check
        if (null == event) {
            Log.e(LOG_TAG, "## decryptEvent() : null event");
            return null;
        }

        OlmEventContent olmEventContent = JsonUtils.toOlmEventContent(event.getWireContent().getAsJsonObject());

        String deviceKey = olmEventContent.sender_key;
        Map<String, Object> ciphertext = olmEventContent.ciphertext;

        if (null == ciphertext) {
            Log.e(LOG_TAG, "## decryptEvent() : missing cipher text");
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_CIPHER_TEXT_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.MISSING_CIPHER_TEXT_REASON));
        }

        if (!ciphertext.containsKey(mOlmDevice.getDeviceCurve25519Key())) {
            Log.e(LOG_TAG, "## decryptEvent() : our device " + mOlmDevice.getDeviceCurve25519Key() + " is not included in recipients. Event " + event.getContentAsJsonObject());
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.NOT_INCLUDE_IN_RECIPIENTS_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.NOT_INCLUDED_IN_RECIPIENT_REASON));
        }

        // The message for myUser
        Map<String, Object> message = (Map<String, Object>) ciphertext.get(mOlmDevice.getDeviceCurve25519Key());
        String payloadString = decryptMessage(message, deviceKey);

        if (null == payloadString) {
            Log.e(LOG_TAG, "## decryptEvent() Failed to decrypt Olm event (id= " + event.eventId + " ) from " + deviceKey);
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_ENCRYPTED_MESSAGE_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.BAD_ENCRYPTED_MESSAGE_REASON));
        }

        JsonElement payload = new JsonParser().parse(JsonUtils.convertFromUTF8(payloadString));

        if (null != payload) {
            JsonObject payloadAsJSon = payload.getAsJsonObject();

            if (!payloadAsJSon.has("recipient")) {
                String reason = String.format(MXCryptoError.ERROR_MISSING_PROPERTY_REASON, "recipient");
                Log.e(LOG_TAG, "## decryptEvent() : " + reason);
                throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_PROPERTY_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, reason));
            } else {
                String recipient = null;
                try {
                    recipient = payloadAsJSon.get("recipient").getAsString();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## decryptEvent() : failed to get recipient " + e.getMessage());
                }

                if (!TextUtils.equals(recipient, mSession.getMyUserId())) {
                    Log.e(LOG_TAG, "## decryptEvent() : Event " + event.eventId + ": Intended recipient " + recipient + " does not match our id " + mSession.getMyUserId());
                    throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_RECIPIENT_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.BAD_RECIPIENT_REASON, recipient)));
                }
            }

            if (!payloadAsJSon.has("recipient_keys")) {
                Log.e(LOG_TAG, "## decryptEvent() : Olm event (id=" + event.eventId + ") contains no " + "'recipient_keys' property; cannot prevent unknown-key attack");
                throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_PROPERTY_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.ERROR_MISSING_PROPERTY_REASON, "recipient_keys")));
            } else {
                String ed25519 = null;

                try {
                    ed25519 = payloadAsJSon.getAsJsonObject("recipient_keys").get("ed25519").getAsString();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## decryptEvent() : failed to get recipient_keys" + e.getMessage());
                }

                if (!TextUtils.equals(ed25519, mOlmDevice.getDeviceEd25519Key())) {
                    Log.e(LOG_TAG, "## decryptEvent() : Event " + event.eventId + ": Intended recipient ed25519 key " + ed25519 + " did not match ours");
                    throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_RECIPIENT_KEY_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.BAD_RECIPIENT_KEY_REASON));
                }
            }

            if (!payloadAsJSon.has("sender")) {
                Log.e(LOG_TAG, "## decryptEvent() : Olm event (id=" + event.eventId + ") contains no " + "'sender' property; cannot prevent unknown-key attack");
                throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_PROPERTY_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.ERROR_MISSING_PROPERTY_REASON, "sender")));
            } else {
                String sender = null;

                try {
                    sender = payloadAsJSon.get("sender").getAsString();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## decryptEvent() : failed to get sender " + e.getMessage());
                }

                if (!TextUtils.equals(sender, event.getSender())) {
                    Log.e(LOG_TAG, "Event " + event.eventId + ": original sender " + sender + " does not match reported sender " + event.getSender());
                    throw new MXDecryptionException(new MXCryptoError(MXCryptoError.FORWARDED_MESSAGE_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.FORWARDED_MESSAGE_REASON, sender)));
                }
            }

            String expectedRoomId = null;

            if (payloadAsJSon.has("room_id")) {
                try {
                    expectedRoomId = payloadAsJSon.get("room_id").getAsString();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## decryptEvent() : " + e.getMessage());
                }
            }

            if (!TextUtils.equals(event.roomId, expectedRoomId)) {
                Log.e(LOG_TAG, "## decryptEvent() : Event " + event.eventId + ": original room " + expectedRoomId + " does not match reported room " + event.roomId);
                throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_ROOM_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.BAD_ROOM_REASON, expectedRoomId)));
            }
        }

        MXEventDecryptionResult result = new MXEventDecryptionResult();

        try {
            Map<String, String> keysClaimed = JsonUtils.getGson(false).fromJson(payload.getAsJsonObject().get("keys"), new TypeToken<Map<String, String>>() {
            }.getType());

            result.mClearEvent = payload;
            result.mSenderCurve25519Key = deviceKey;
            result.mClaimedEd25519Key = keysClaimed.get("ed25519");
        } catch (Exception e) {
            Log.e(LOG_TAG, "## decryptEvent failed " + e.getMessage());
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.UNABLE_TO_DECRYPT_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.MISSING_CIPHER_TEXT_REASON));
        }

        return result;
    }

    @Override
    public void onRoomKeyEvent(Event event) {
        // No impact for olm
    }

    @Override
    public void onNewSession(String senderKey, String sessionId) {
        // No impact for olm
    }

    @Override
    public boolean hasKeysForKeyRequest(IncomingRoomKeyRequest request) {
        return false;
    }

    @Override
    public void shareKeysWithDevice(IncomingRoomKeyRequest request) {
    }

    /**
     * Attempt to decrypt an Olm message.
     *
     * @param theirDeviceIdentityKey the Curve25519 identity key of the sender.
     * @param message                message object, with 'type' and 'body' fields.
     * @return payload, if decrypted successfully.
     */
    private String decryptMessage(Map<String, Object> message, String theirDeviceIdentityKey) {
        Set<String> sessionIdsSet = mOlmDevice.getSessionIds(theirDeviceIdentityKey);

        ArrayList<String> sessionIds;

        if (null == sessionIdsSet) {
            sessionIds = new ArrayList<>();
        } else {
            sessionIds = new ArrayList<>(sessionIdsSet);
        }

        String messageBody = (String) message.get("body");
        Integer messageType = null;

        Object typeAsVoid = message.get("type");

        if (null != typeAsVoid) {
            if (typeAsVoid instanceof Double) {
                messageType = new Integer(((Double) typeAsVoid).intValue());
            } else if (typeAsVoid instanceof Integer) {
                messageType = (Integer) typeAsVoid;
            } else if (typeAsVoid instanceof Long) {
                messageType = new Integer(((Long) typeAsVoid).intValue());
            }
        }

        if ((null == messageBody) || (null == messageType)) {
            return null;
        }

        // Try each session in turn
        // decryptionErrors = {};
        for (String sessionId : sessionIds) {
            String payload = mOlmDevice.decryptMessage(messageBody, messageType, sessionId, theirDeviceIdentityKey);

            if (null != payload) {
                Log.d(LOG_TAG, "## decryptMessage() : Decrypted Olm message from " + theirDeviceIdentityKey + " with session " + sessionId);
                return payload;
            } else {
                boolean foundSession = mOlmDevice.matchesSession(theirDeviceIdentityKey, sessionId, messageType, messageBody);

                if (foundSession) {
                    // Decryption failed, but it was a prekey message matching this
                    // session, so it should have worked.
                    Log.e(LOG_TAG, "## decryptMessage() : Error decrypting prekey message with existing session id " + sessionId + ":TODO");
                    return null;
                }
            }
        }

        if (messageType != 0) {
            // not a prekey message, so it should have matched an existing session, but it
            // didn't work.

            if (sessionIds.size() == 0) {
                Log.e(LOG_TAG, "## decryptMessage() : No existing sessions");
            } else {
                Log.e(LOG_TAG, "## decryptMessage() : Error decrypting non-prekey message with existing sessions");
            }

            return null;
        }

        // prekey message which doesn't match any existing sessions: make a new
        // session.
        Map<String, String> res = mOlmDevice.createInboundSession(theirDeviceIdentityKey, messageType, messageBody);

        if (null == res) {
            Log.e(LOG_TAG, "## decryptMessage() :  Error decrypting non-prekey message with existing sessions");
            return null;
        }

        Log.d(LOG_TAG, "## decryptMessage() :  Created new inbound Olm session get id " + res.get("session_id") + " with " + theirDeviceIdentityKey);

        return res.get("payload");
    }
}
