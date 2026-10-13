/*
 * Copyright (c) 2022-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.sdk.service.consumer;

import java.util.Collections;
import java.util.List;

import com.alibaba.higress.sdk.model.WasmPluginInstance;
import com.alibaba.higress.sdk.model.consumer.AllowList;
import com.alibaba.higress.sdk.model.consumer.AllowListOperation;
import com.alibaba.higress.sdk.model.consumer.Consumer;

interface CredentialHandler {

    String getType();

    String getPluginName();

    boolean isConsumerInUse(String consumerName, List<WasmPluginInstance> instances);

    List<Consumer> extractConsumers(WasmPluginInstance instance);

    void initDefaultGlobalConfigs(WasmPluginInstance instance);

    boolean saveConsumer(WasmPluginInstance instance, Consumer consumer);

    boolean deleteConsumer(WasmPluginInstance globalInstance, String consumerName);

    List<String> getAllowedConsumers(WasmPluginInstance instance);

    /**
     * Consumer group names allowed on the instance's target
     * ({@code allow_groups}). Only the key-auth plugin supports groups today;
     * other handlers have none.
     */
    default List<String> getAllowGroups(WasmPluginInstance instance) {
        return Collections.emptyList();
    }

    /**
     * Raw credential (API key) values allowed on the instance's target
     * ({@code allow_credentials}). Only the key-auth plugin supports raw
     * credential allow lists today; other handlers have none.
     */
    default List<String> getAllowCredentials(WasmPluginInstance instance) {
        return Collections.emptyList();
    }

    void updateAllowList(AllowListOperation operation, WasmPluginInstance instance, AllowList allowList);

    /**
     * Rewrites legacy credential values that carry the {@code "Bearer "}
     * auth-scheme transport prefix into the logical form matched by the plugin
     * after extraction-time stripping. Only header-sourced consumer values are
     * rewritten (query values are matched literally); route-level
     * {@code allow_credentials} entries are normalized unconditionally.
     *
     * @return true when the instance configuration was changed and needs to be
     *         persisted again
     */
    default boolean normalizeCredentialValues(WasmPluginInstance instance) {
        return false;
    }
}
