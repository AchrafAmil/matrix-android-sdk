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
package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.BingRulesApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRulesResponse;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class BingRulesRestClient extends RestClient<BingRulesApi> {

    /**
     * {@inheritDoc}
     */
    public BingRulesRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, BingRulesApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    /**
     * Retrieve the bing rules list.
     *
     * @param callback the asynchronous callback.
     */
    public void getAllBingRules(final ApiCallback<BingRulesResponse> callback) {
        try {
            mApi.getAllBingRules(new Callback<BingRulesResponse>() {
                @Override
                public void success(BingRulesResponse bingRulesResponse, Response response) {
                    callback.onSuccess(bingRulesResponse);
                }

                @Override
                public void failure(RetrofitError error) {
                    callback.onUnexpectedError(error);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * @return the bing rules list.
     */
    public BingRulesResponse getAllBingRules() {
        return mApi.getAllBingRules();
    }

    /**
     * Update the rule enable status.
     *
     * @param Kind     the rule kind
     * @param ruleId   the rule id
     * @param status   the rule state
     * @param callback the asynchronous callback.
     */
    public void updateEnableRuleStatus(String Kind, String ruleId, boolean status, final ApiCallback<Void> callback) {
        try {
            mApi.updateEnableRuleStatus(Kind, ruleId, status, new Callback<Void>() {
                @Override
                public void success(Void voidObject, Response response) {
                    callback.onSuccess(voidObject);
                }

                @Override
                public void failure(RetrofitError error) {
                    callback.onUnexpectedError(error);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the rule actions lists.
     *
     * @param Kind     the rule kind
     * @param ruleId   the rule id
     * @param actions  the rule actions list
     * @param callback the asynchronous callback
     */
    public void updateRuleActions(String Kind, String ruleId, Object actions, final ApiCallback<Void> callback) {
        try {
            mApi.updateRuleActions(Kind, ruleId, actions, new Callback<Void>() {
                @Override
                public void success(Void voidObject, Response response) {
                    callback.onSuccess(voidObject);
                }

                @Override
                public void failure(RetrofitError error) {
                    callback.onUnexpectedError(error);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Delete a rule.
     *
     * @param Kind     the rule kind
     * @param ruleId   the rule id
     * @param callback the asynchronous callback
     */
    public void deleteRule(String Kind, String ruleId, final ApiCallback<Void> callback) {
        try {
            mApi.deleteRule(Kind, ruleId, new Callback<Void>() {
                @Override
                public void success(Void voidObject, Response response) {
                    callback.onSuccess(voidObject);
                }

                @Override
                public void failure(RetrofitError error) {
                    callback.onUnexpectedError(error);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Add a rule.
     *
     * @param rule     the rule
     * @param callback the asynchronous callback
     */
    public void addRule(BingRule rule, final ApiCallback<Void> callback) {
        try {
            mApi.addRule(rule.kind, rule.ruleId, rule.toJsonElement(), new Callback<Void>() {
                @Override
                public void success(Void voidObject, Response response) {
                    callback.onSuccess(voidObject);
                }

                @Override
                public void failure(RetrofitError error) {
                    callback.onUnexpectedError(error);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }
}
