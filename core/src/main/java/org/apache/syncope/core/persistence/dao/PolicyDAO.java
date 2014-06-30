/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.persistence.dao;

import java.util.List;

import org.apache.syncope.common.types.PolicyType;
import org.apache.syncope.core.persistence.beans.AccountPolicy;
import org.apache.syncope.core.persistence.beans.ExternalResource;
import org.apache.syncope.core.persistence.beans.PasswordPolicy;
import org.apache.syncope.core.persistence.beans.Policy;
import org.apache.syncope.core.persistence.beans.PushPolicy;
import org.apache.syncope.core.persistence.beans.SyncPolicy;

public interface PolicyDAO extends DAO {

    Policy find(Long id);

    List<? extends Policy> find(PolicyType type);

    List<AccountPolicy> findByResource(ExternalResource resource);

    PasswordPolicy getGlobalPasswordPolicy();

    AccountPolicy getGlobalAccountPolicy();

    SyncPolicy getGlobalSyncPolicy();

    PushPolicy getGlobalPushPolicy();

    List<Policy> findAll();

    <T extends Policy> T save(T policy);

    <T extends Policy> void delete(T policy);
}
