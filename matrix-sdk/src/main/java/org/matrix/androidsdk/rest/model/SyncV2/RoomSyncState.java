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
package org.matrix.androidsdk.rest.model.SyncV2;

import org.matrix.androidsdk.rest.model.Event;

import java.util.List;

// RoomSyncState represents the state updates for a room during server sync v2.
public class RoomSyncState implements java.io.Serializable {

    /**
     * List of state events (array of Event). The resulting state corresponds to the *start* of the timeline.
     */
    public List<Event> events;

}