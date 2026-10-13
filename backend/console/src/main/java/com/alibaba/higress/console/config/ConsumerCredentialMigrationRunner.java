/*
 * Copyright (c) 2022-2025 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.alibaba.higress.sdk.service.consumer.ConsumerService;

import lombok.extern.slf4j.Slf4j;

/**
 * Normalizes legacy {@code "Bearer "}-prefixed key-auth credential values
 * (stored by console versions before the plugin stripped the auth-scheme
 * prefix at extraction time) into the logical form the plugin now matches.
 * Runs once per startup; failures are logged and do not block console startup.
 */
@Slf4j
@Component
public class ConsumerCredentialMigrationRunner implements ApplicationRunner {

    private final ConsumerService consumerService;

    public ConsumerCredentialMigrationRunner(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (consumerService.normalizeLegacyCredentialValues()) {
                log.info("Legacy Bearer-prefixed key-auth credentials were normalized to logical values.");
            }
        } catch (Exception e) {
            log.error("Failed to normalize legacy key-auth credentials. Route authentication keeps failing for "
                + "consumers whose stored credentials still carry the \"Bearer \" transport prefix. Resolve the "
                + "reported conflicts (duplicate logical values must be merged manually) and restart the console.", e);
        }
    }
}
