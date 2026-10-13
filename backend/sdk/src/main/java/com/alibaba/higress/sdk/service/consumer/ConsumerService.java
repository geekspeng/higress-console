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

import java.util.List;
import java.util.Map;

import com.alibaba.higress.sdk.model.CommonPageQuery;
import com.alibaba.higress.sdk.model.PaginatedResult;
import com.alibaba.higress.sdk.model.WasmPluginInstanceScope;
import com.alibaba.higress.sdk.model.consumer.AllowList;
import com.alibaba.higress.sdk.model.consumer.AllowListOperation;
import com.alibaba.higress.sdk.model.consumer.Consumer;

public interface ConsumerService {

    Consumer addOrUpdate(Consumer consumer);

    PaginatedResult<Consumer> list(CommonPageQuery query);

    Consumer query(String consumerName);

    void delete(String consumerName);

    List<AllowList> listAllowLists();

    AllowList getAllowList(Map<WasmPluginInstanceScope, String> targets);

    void updateAllowList(AllowListOperation operation, AllowList allowList);

    /**
     * Rewrites legacy key-auth credential values that carry the
     * {@code "Bearer "} auth-scheme transport prefix into the logical form the
     * plugin matches after extraction-time stripping. Covers the global
     * consumer registry and every route/domain-level instance of the plugin.
     *
     * @return true when at least one instance was updated and persisted
     */
    boolean normalizeLegacyCredentialValues();
}
