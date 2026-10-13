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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alibaba.higress.sdk.service.consumer.ConsumerService;

public class ConsumerCredentialMigrationRunnerTest {

    private ConsumerService consumerService;

    private ConsumerCredentialMigrationRunner runner;

    @BeforeEach
    public void setUp() {
        consumerService = mock(ConsumerService.class);
        runner = new ConsumerCredentialMigrationRunner(consumerService);
    }

    @Test
    public void runDelegatesToConsumerService() {
        runner.run(null);
        verify(consumerService).normalizeLegacyCredentialValues();
    }

    @Test
    public void runSwallowsFailures() {
        when(consumerService.normalizeLegacyCredentialValues()).thenThrow(new RuntimeException("boom"));
        assertDoesNotThrow(() -> runner.run(null));
        verify(consumerService).normalizeLegacyCredentialValues();
    }
}
