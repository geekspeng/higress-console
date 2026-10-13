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

import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.ALLOW;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.ALLOW_CREDENTIALS;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.ALLOW_GROUPS;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.CONSUMERS;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.CONSUMER_CREDENTIAL;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.CONSUMER_CREDENTIALS;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.CONSUMER_GROUP;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.CONSUMER_NAME;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.CONSUMER_SOURCE;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.GLOBAL_AUTH;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.IN_HEADER;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.IN_QUERY;
import static com.alibaba.higress.sdk.constant.plugin.config.KeyAuthConfig.KEYS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.alibaba.higress.sdk.constant.plugin.BuiltInPluginName;
import com.alibaba.higress.sdk.model.WasmPluginInstance;
import com.alibaba.higress.sdk.model.consumer.AllowList;
import com.alibaba.higress.sdk.model.consumer.AllowListOperation;
import com.alibaba.higress.sdk.model.consumer.Consumer;
import com.alibaba.higress.sdk.model.consumer.CredentialType;
import com.alibaba.higress.sdk.model.consumer.KeyAuthCredential;
import com.alibaba.higress.sdk.model.consumer.KeyAuthCredentialSource;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class KeyAuthCredentialHandler implements CredentialHandler {

    private static final String BEARER_TOKEN_PREFIX = "Bearer ";

    @Override
    public String getType() {
        return CredentialType.KEY_AUTH;
    }

    @Override
    public String getPluginName() {
        return BuiltInPluginName.KEY_AUTH;
    }

    @Override
    public boolean isConsumerInUse(String consumerName, List<WasmPluginInstance> instances) {
        if (CollectionUtils.isEmpty(instances)) {
            return false;
        }
        for (WasmPluginInstance instance : instances) {
            Map<String, Object> configurations = instance.getConfigurations();
            if (MapUtils.isEmpty(configurations)) {
                continue;
            }
            Object allowObj = configurations.get(ALLOW);
            if (allowObj instanceof List<?>) {
                List<?> allowList = (List<?>)allowObj;
                if (allowList.contains(consumerName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Consumer> extractConsumers(WasmPluginInstance instance) {
        if (MapUtils.isEmpty(instance.getConfigurations())) {
            return Lists.newArrayList();
        }
        Object consumersObj = instance.getConfigurations().get(CONSUMERS);
        if (!(consumersObj instanceof List<?>)) {
            return Lists.newArrayList();
        }
        List<?> consumerList = (List<?>)consumersObj;
        List<Consumer> consumers = new ArrayList<>(consumerList.size());
        for (Object consumerObj : consumerList) {
            if (!(consumerObj instanceof Map<?, ?>)) {
                continue;
            }

            Map<String, Object> consumerMap = (Map<String, Object>)consumerObj;
            Consumer consumer = extractConsumer(consumerMap);
            if (consumer != null) {
                consumers.add(consumer);
            }
        }
        return consumers;
    }

    @Override
    public void initDefaultGlobalConfigs(WasmPluginInstance instance) {
        Map<String, Object> configurations = instance.getConfigurations();
        if (MapUtils.isEmpty(configurations)) {
            configurations = new LinkedHashMap<>();
            instance.setConfigurations(configurations);
        }
        configurations.putIfAbsent(GLOBAL_AUTH, false);
        configurations.putIfAbsent(ALLOW, Collections.emptyList());

        // TODO: Remove this after plugin upgrade.
        // Add a dummy key to the global keys list because the plugin requires at least one global key.
        configurations.put(KEYS, Lists.newArrayList("x-higress-dummy-key"));

        configurations.computeIfAbsent(CONSUMERS, k -> new ArrayList<>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean saveConsumer(WasmPluginInstance instance, Consumer consumer) {
        if (CollectionUtils.isEmpty(consumer.getCredentials())) {
            return false;
        }

        KeyAuthCredential keyAuthCredential = (KeyAuthCredential)consumer.getCredentials().stream()
            .filter(c -> CredentialType.KEY_AUTH.equals(c.getType())).findFirst().orElse(null);
        if (keyAuthCredential == null) {
            return deleteConsumer(instance, consumer.getName());
        }

        Map<String, Object> configurations = instance.getConfigurations();
        if (MapUtils.isEmpty(configurations)) {
            initDefaultGlobalConfigs(instance);
            configurations = instance.getConfigurations();
            assert MapUtils.isNotEmpty(configurations);
        }

        Object consumersObj = configurations.computeIfAbsent(CONSUMERS, k -> new ArrayList<>());
        List<Object> consumers;
        if (consumersObj instanceof List) {
            consumers = new ArrayList<>((List<Object>)consumersObj);
        } else {
            consumers = new ArrayList<>();
        }

        Map<String, Object> consumerConfig = null;
        for (Object consumerObj : consumers) {
            if (!(consumerObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> consumerMap = (Map<String, Object>)consumerObj;
            Consumer existedConsumer = extractConsumer(consumerMap);
            if (existedConsumer == null) {
                continue;
            }
            if (consumer.getName().equals(existedConsumer.getName())) {
                consumerConfig = consumerMap;
            } else if (hasSameCredential(existedConsumer, keyAuthCredential)) {
                throw new IllegalArgumentException(
                    "Key auth credential already in use by consumer: " + existedConsumer.getName());
            }
        }

        if (consumerConfig == null) {
            consumerConfig = new HashMap<>();
            consumerConfig.put(CONSUMER_NAME, consumer.getName());
            consumers.add(consumerConfig);
        } else {
            keyAuthCredential = mergeExistedConfig(keyAuthCredential, consumerConfig);
        }

        // group: optional tenant identifier forwarded to ai-quota via X-Mse-Consumer-Group.
        // Set explicitly so credential rotation can clear it by passing an empty value.
        if (StringUtils.isNotBlank(consumer.getGroup())) {
            consumerConfig.put(CONSUMER_GROUP, consumer.getGroup());
        } else {
            consumerConfig.remove(CONSUMER_GROUP);
        }

        keyAuthCredential.validate(false);

        KeyAuthCredentialSource sourceEnum = KeyAuthCredentialSource.parse(keyAuthCredential.getSource());
        if (sourceEnum == null) {
            throw new IllegalArgumentException("Invalid key auth credential source: " + keyAuthCredential.getSource());
        }
        String key = keyAuthCredential.getKey();
        List<String> credentials = keyAuthCredential.getValues();
        switch (sourceEnum) {
            case BEARER:
            case HEADER:
                consumerConfig.put(IN_HEADER, true);
                consumerConfig.put(IN_QUERY, false);
                if (sourceEnum == KeyAuthCredentialSource.BEARER) {
                    // The plugin strips the auth-scheme prefix when extracting the
                    // credential from the header, so the stored value is the logical
                    // credential; no transport prefix is added here.
                    key = HttpHeaders.AUTHORIZATION;
                }
                break;
            case QUERY:
                consumerConfig.put(IN_HEADER, false);
                consumerConfig.put(IN_QUERY, true);
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported key auth credential source: " + keyAuthCredential.getSource());
        }
        consumerConfig.put(KEYS, Lists.newArrayList(key));
        consumerConfig.put(CONSUMER_CREDENTIALS, credentials);
        consumerConfig.remove(CONSUMER_CREDENTIAL);
        // Console-only marker keeping the source choice stable across read/modify
        // cycles (a custom header named "Authorization" must not read back as BEARER).
        consumerConfig.put(CONSUMER_SOURCE, sourceEnum.name());

        configurations.put(CONSUMERS, consumers);
        configurations.put(GLOBAL_AUTH, false);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean deleteConsumer(WasmPluginInstance globalInstance, String consumerName) {
        Map<String, Object> globalConfigurations = globalInstance.getConfigurations();
        if (MapUtils.isEmpty(globalConfigurations)) {
            return false;
        }
        Object consumersObj = globalConfigurations.get(CONSUMERS);
        if (!(consumersObj instanceof List)) {
            return false;
        }
        boolean deleted = false;
        List<Object> consumers = new ArrayList<>((List<Object>)consumersObj);
        for (int i = consumers.size() - 1; i >= 0; --i) {
            Object consumerObj = consumers.get(i);
            if (!(consumerObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> consumerMap = (Map<String, Object>)consumerObj;
            if (consumerName.equals(consumerMap.get(CONSUMER_NAME))) {
                consumers.remove(i);
                deleted = true;
            }
        }
        if (deleted) {
            globalConfigurations.put(CONSUMERS, consumers);
        }
        return deleted;
    }

    @Override
    public List<String> getAllowedConsumers(WasmPluginInstance instance) {
        return getStringList(instance, ALLOW);
    }

    @Override
    public List<String> getAllowGroups(WasmPluginInstance instance) {
        return getStringList(instance, ALLOW_GROUPS);
    }

    @Override
    public List<String> getAllowCredentials(WasmPluginInstance instance) {
        return getStringList(instance, ALLOW_CREDENTIALS);
    }

    private static List<String> getStringList(WasmPluginInstance instance, String key) {
        Map<String, Object> configurations = instance.getConfigurations();
        if (MapUtils.isEmpty(configurations)) {
            return Lists.newArrayList();
        }

        Object valueObj = configurations.get(key);
        if (!(valueObj instanceof List<?>)) {
            return Lists.newArrayList();
        }
        List<?> valueList = (List<?>)valueObj;
        return valueList.stream().filter(a -> a instanceof String).map(a -> (String)a).collect(Collectors.toList());
    }

    @Override
    public void updateAllowList(AllowListOperation operation, WasmPluginInstance instance, AllowList allowList) {
        Map<String, Object> configurations = instance.getConfigurations();
        if (MapUtils.isEmpty(configurations)) {
            configurations = new HashMap<>();
            instance.setConfigurations(configurations);
        }

        List<String> newAllowList = getAllowedConsumers(instance);
        List<String> newGroups = getAllowGroups(instance);
        List<String> newCredentials = getAllowCredentials(instance);
        switch (operation) {
            case ADD:
                addAllIfAbsent(newAllowList, allowList.getConsumerNames());
                break;
            case REMOVE:
                if (allowList.getConsumerNames() != null) {
                    newAllowList.removeAll(allowList.getConsumerNames());
                }
                break;
            case REPLACE:
                newAllowList = allowList.getConsumerNames();
                // allow_groups / allow_credentials are platform-level authorization
                // capability fields: REPLACE carrying null means "not issued this
                // time", keeping the route instance's current value; carrying a
                // non-null value (including an empty array) replaces it.
                if (allowList.getConsumerGroups() != null) {
                    newGroups = allowList.getConsumerGroups();
                }
                if (allowList.getCredentials() != null) {
                    newCredentials = allowList.getCredentials();
                }
                break;
            case TOGGLE_ONLY:
                if (CollectionUtils.isEmpty(newAllowList)) {
                    newAllowList = new ArrayList<>();
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported operation: " + operation);
        }
        // Do not write the key when the list is empty: the plugin rejects the whole
        // configuration when an allow list field exists but is an empty array.
        putNonEmptyList(configurations, ALLOW, newAllowList);
        putNonEmptyList(configurations, ALLOW_GROUPS, newGroups);
        putNonEmptyList(configurations, ALLOW_CREDENTIALS, newCredentials);

        // The plugin also rejects rule-level configs that carry none of the three
        // allow lists, so an enabled instance must keep at least one non-empty
        // list. Disabled instances are fine: the plugin skips disabled rules.
        if (Boolean.TRUE.equals(instance.getEnabled())
            && CollectionUtils.isEmpty((List<?>)configurations.get(ALLOW))
            && CollectionUtils.isEmpty((List<?>)configurations.get(ALLOW_GROUPS))
            && CollectionUtils.isEmpty((List<?>)configurations.get(ALLOW_CREDENTIALS))) {
            throw new IllegalArgumentException(
                "At least one of consumer names, consumer groups and API keys must be configured when authentication is enabled.");
        }
    }

    private static void addAllIfAbsent(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (!target.contains(value)) {
                target.add(value);
            }
        }
    }

    private static void putNonEmptyList(Map<String, Object> configurations, String key, List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            configurations.remove(key);
        } else {
            configurations.put(key, values);
        }
    }

    private Consumer extractConsumer(Map<String, Object> consumerMap) {
        if (MapUtils.isEmpty(consumerMap)) {
            return null;
        }

        String name = MapUtils.getString(consumerMap, CONSUMER_NAME);
        if (StringUtils.isBlank(name)) {
            return null;
        }

        KeyAuthCredential credential = parseCredential(consumerMap);
        if (credential == null) {
            return null;
        }

        Consumer consumer = new Consumer();
        consumer.setName(name);
        consumer.setCredentials(Lists.newArrayList(credential));
        // Read the group back for list display and edit form echo (saveConsumer
        // uses an empty value to clear the group).
        String group = MapUtils.getString(consumerMap, CONSUMER_GROUP);
        if (StringUtils.isNotBlank(group)) {
            consumer.setGroup(group);
        }
        return consumer;
    }

    private boolean hasSameCredential(Consumer existedConsumer, KeyAuthCredential credential) {
        if (credential == null || existedConsumer == null) {
            return false;
        }
        KeyAuthCredential existedCredential = (KeyAuthCredential)existedConsumer.getCredentials().stream()
            .filter(c -> CredentialType.KEY_AUTH.equals(c.getType())).findFirst().orElse(null);
        if (existedCredential == null) {
            return false;
        }
        if (!StringUtils.equalsIgnoreCase(credential.getSource(), existedCredential.getSource())) {
            return false;
        }
        if (!StringUtils.equals(credential.getKey(), existedCredential.getKey())) {
            return false;
        }
        if (CollectionUtils.isEmpty(credential.getValues()) || CollectionUtils.isEmpty(existedCredential.getValues())) {
            return false;
        }
        for (String value : credential.getValues()) {
            if (existedCredential.getValues().contains(value)) {
                return true;
            }
        }
        return false;
    }

    private KeyAuthCredential mergeExistedConfig(KeyAuthCredential keyAuthCredential,
        Map<String, Object> consumerConfig) {
        KeyAuthCredential existedCredential = parseCredential(consumerConfig);
        if (existedCredential == null) {
            return keyAuthCredential;
        }

        return new KeyAuthCredential(
            StringUtils.firstNonBlank(keyAuthCredential.getSource(), existedCredential.getSource()),
            StringUtils.firstNonBlank(keyAuthCredential.getKey(), existedCredential.getKey()),
            CollectionUtils.isNotEmpty(keyAuthCredential.getValues()) ? keyAuthCredential.getValues()
                : existedCredential.getValues());
    }

    private static KeyAuthCredential parseCredential(Map<String, Object> consumerMap) {
        Object keyObj = MapUtils.getObject(consumerMap, KEYS);
        if (!(keyObj instanceof List<?>)) {
            return null;
        }

        List<?> keyList = (List<?>)keyObj;
        if (keyList.isEmpty()) {
            return null;
        }

        String key = null;
        for (Object keyItemObj : keyList) {
            if (!(keyItemObj instanceof String)) {
                continue;
            }
            String keyItem = (String)keyItemObj;
            if (StringUtils.isNotBlank(keyItem)) {
                key = keyItem;
            }
        }
        if (key == null) {
            return null;
        }

        Boolean inHeader = MapUtils.getBoolean(consumerMap, IN_HEADER);
        Boolean inQuery = MapUtils.getBoolean(consumerMap, IN_QUERY);

        List<String> credentials = new ArrayList<>();
        Object credentialsObj = consumerMap.get(CONSUMER_CREDENTIALS);
        if (credentialsObj instanceof List<?>) {
            List<?> credentialsList = (List<?>)credentialsObj;
            for (Object credentialObj : credentialsList) {
                if (credentialObj instanceof String) {
                    credentials.add((String)credentialObj);
                }
            }
        }
        {
            // TODO: To be removed later.
            String credential = MapUtils.getString(consumerMap, CONSUMER_CREDENTIAL);
            if (StringUtils.isNotBlank(credential) && !credentials.contains(credential)) {
                credentials.add(credential);
            }
        }

        KeyAuthCredentialSource source;
        KeyAuthCredentialSource explicitSource =
            KeyAuthCredentialSource.parse(MapUtils.getString(consumerMap, CONSUMER_SOURCE));
        if (explicitSource != null) {
            // Explicit console marker wins: a custom header named "Authorization"
            // must keep its HEADER source across read/modify cycles.
            source = explicitSource;
            if (source == KeyAuthCredentialSource.BEARER) {
                key = null;
            }
        } else if (Boolean.TRUE.equals(inHeader)) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key)) {
                // Legacy fallback for records written before the explicit source
                // field: key=Authorization marks the BEARER source. The plugin
                // strips the auth-scheme prefix at extraction, so stored values
                // are logical credentials; prefixed legacy values are read back
                // as-is and normalized by normalizeCredentialValues at startup.
                source = KeyAuthCredentialSource.BEARER;
                key = null;
            } else {
                source = KeyAuthCredentialSource.HEADER;
            }
        } else if (Boolean.TRUE.equals(inQuery)) {
            source = KeyAuthCredentialSource.QUERY;
        } else {
            return null;
        }
        return new KeyAuthCredential(source.name(), key, credentials);
    }

    @Override
    public boolean normalizeCredentialValues(WasmPluginInstance instance) {
        Map<String, Object> configurations = instance.getConfigurations();
        if (MapUtils.isEmpty(configurations)) {
            return false;
        }
        boolean consumersChanged = normalizeLegacyBearerPrefixedConsumers(configurations);
        boolean allowChanged = normalizeLegacyBearerPrefixedAllowCredentials(configurations);
        return consumersChanged || allowChanged;
    }

    /**
     * Strips the {@code "Bearer "} auth-scheme prefix from consumer credential
     * values. Only header-sourced consumers (in_header + key=Authorization, the
     * console's BEARER marker) are rewritten: the plugin strips prefixes only
     * when extracting from headers, while query values match literally and
     * could legitimately start with {@code "Bearer "}.
     */
    private boolean normalizeLegacyBearerPrefixedConsumers(Map<String, Object> configurations) {
        Object consumersObj = configurations.get(CONSUMERS);
        if (!(consumersObj instanceof List<?>)) {
            return false;
        }
        Set<String> claimed = new HashSet<>();
        boolean changed = false;
        for (Object item : (List<?>)consumersObj) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> consumer = (Map<String, Object>)item;
            if (!Boolean.TRUE.equals(MapUtils.getBoolean(consumer, IN_HEADER))
                || !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(authorizationKeyOf(consumer.get(KEYS)))) {
                continue;
            }
            List<String> rawValues = new ArrayList<>();
            Object credentialsObj = consumer.get(CONSUMER_CREDENTIALS);
            if (credentialsObj instanceof List<?>) {
                for (Object valueObj : (List<?>)credentialsObj) {
                    if (valueObj instanceof String) {
                        rawValues.add((String)valueObj);
                    }
                }
            }
            String singular = MapUtils.getString(consumer, CONSUMER_CREDENTIAL);
            if (StringUtils.isNotBlank(singular) && !rawValues.contains(singular)) {
                rawValues.add(singular);
            }
            if (rawValues.isEmpty()) {
                continue;
            }

            List<String> normalized = new ArrayList<>(rawValues.size());
            for (String value : rawValues) {
                String stripped = stripBearerPrefix(value);
                if (claimed.add(stripped)) {
                    if (!stripped.equals(value)) {
                        changed = true;
                    }
                    normalized.add(stripped);
                } else if (stripped.equals(value)) {
                    // Exact duplicate of an already-claimed logical value. The
                    // plugin rejects duplicate registry entries outright; keep the
                    // value (dropping it could leave the consumer credential-less)
                    // unless it is a repeat within the same consumer.
                    log.error("Consumer {} keeps credential {} that duplicates an already claimed logical value; "
                        + "the key-auth plugin will reject this configuration until the duplicates are merged.",
                        consumer.get(CONSUMER_NAME), value);
                    if (!normalized.contains(value)) {
                        normalized.add(value);
                    }
                } else {
                    // The logical value is owned by another entry; keeping the raw
                    // value preserves a distinct (if now unmatched) registry key.
                    log.warn("Consumer {} keeps legacy credential {} whose logical value {} is already claimed; "
                        + "requests carrying it will not authenticate until the conflict is resolved.",
                        consumer.get(CONSUMER_NAME), value, stripped);
                    normalized.add(value);
                    claimed.add(value);
                }
            }
            if (!normalized.equals(rawValues)) {
                consumer.put(CONSUMER_CREDENTIALS, normalized);
                consumer.remove(CONSUMER_CREDENTIAL);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Strips the {@code "Bearer "} prefix from route-level
     * {@code allow_credentials} entries and dedupes the result. Entries always
     * target extracted credential values, which are logical for header-sourced
     * consumers.
     */
    private boolean normalizeLegacyBearerPrefixedAllowCredentials(Map<String, Object> configurations) {
        Object allowObj = configurations.get(ALLOW_CREDENTIALS);
        if (!(allowObj instanceof List<?>)) {
            return false;
        }
        List<Object> normalized = new ArrayList<>();
        boolean changed = false;
        for (Object item : (List<?>)allowObj) {
            String value = item instanceof String ? (String)item : null;
            String stripped = value != null ? stripBearerPrefix(value) : null;
            Object effective = stripped != null ? stripped : item;
            if (stripped != null && !stripped.equals(value)) {
                changed = true;
            }
            if (!normalized.contains(effective)) {
                normalized.add(effective);
            } else if (stripped != null) {
                changed = true;
            }
        }
        if (changed) {
            configurations.put(ALLOW_CREDENTIALS, normalized);
        }
        return changed;
    }

    private static String authorizationKeyOf(Object keysObj) {
        if (!(keysObj instanceof List<?>)) {
            return null;
        }
        for (Object item : (List<?>)keysObj) {
            if (item instanceof String && StringUtils.isNotBlank((String)item)) {
                return (String)item;
            }
        }
        return null;
    }

    private static String stripBearerPrefix(String value) {
        if (value.length() > BEARER_TOKEN_PREFIX.length()
            && value.regionMatches(true, 0, BEARER_TOKEN_PREFIX, 0, BEARER_TOKEN_PREFIX.length())) {
            return value.substring(BEARER_TOKEN_PREFIX.length()).trim();
        }
        return value;
    }
}
