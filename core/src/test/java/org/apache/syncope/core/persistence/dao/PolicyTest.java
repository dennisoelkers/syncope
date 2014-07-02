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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.apache.syncope.common.types.PasswordPolicySpec;
import org.apache.syncope.common.types.PolicyType;
import org.apache.syncope.common.types.SyncPolicySpec;
import org.apache.syncope.core.persistence.beans.PasswordPolicy;
import org.apache.syncope.core.persistence.beans.Policy;
import org.apache.syncope.core.persistence.beans.SyncPolicy;
import org.apache.syncope.core.persistence.validation.entity.InvalidEntityException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class PolicyTest extends AbstractDAOTest {

    @Autowired
    private PolicyDAO policyDAO;

    @Test
    public void findAll() {
        List<Policy> policies = policyDAO.findAll();
        assertNotNull(policies);
        assertFalse(policies.isEmpty());
    }

    @Test
    public void findById() {
        Policy policy = policyDAO.find(1L);
        assertNotNull("findById did not work", policy);
    }

    @Test
    public void findByType() {
        List<? extends Policy> policies = policyDAO.find(PolicyType.SYNC);
        assertNotNull("findById did not work", policies);
        assertFalse(policies.isEmpty());
    }

    @Test
    public void findGlobalPasswordPolicy() {
        PasswordPolicy policy = policyDAO.getGlobalPasswordPolicy();
        assertNotNull("findById did not work", policy);

        assertEquals(PolicyType.GLOBAL_PASSWORD, policy.getType());

        assertEquals("invalid policy values", 8, (policy.getSpecification(PasswordPolicySpec.class)).getMinLength());
    }

    @Test(expected = InvalidEntityException.class)
    public void saveInvalidPolicy() {
        PasswordPolicySpec passwordPolicy = new PasswordPolicySpec();
        passwordPolicy.setMaxLength(8);
        passwordPolicy.setMinLength(6);

        SyncPolicy policy = new SyncPolicy();
        policy.setSpecification(passwordPolicy);
        policy.setDescription("sync policy");

        policyDAO.save(policy);
    }

    @Test(expected = InvalidEntityException.class)
    public void saveSecondPasswordPolicy() {
        PasswordPolicySpec passwordPolicy = new PasswordPolicySpec();
        passwordPolicy.setMaxLength(8);
        passwordPolicy.setMinLength(6);

        PasswordPolicy policy = new PasswordPolicy(true);
        policy.setSpecification(passwordPolicy);
        policy.setDescription("global password policy");

        policyDAO.save(policy);
    }

    @Test
    public void create() {
        SyncPolicy policy = new SyncPolicy();

        final String syncURuleName = "net.tirasa.sync.correlation.TirasaURule";
        final String syncRRuleName = "net.tirasa.sync.correlation.TirasaRRule";

        SyncPolicySpec syncPolicySpec = new SyncPolicySpec();
        syncPolicySpec.setUserJavaRule(syncURuleName);
        syncPolicySpec.setRoleJavaRule(syncRRuleName);

        policy.setSpecification(syncPolicySpec);
        policy.setDescription("Sync policy");

        policy = policyDAO.save(policy);

        assertNotNull(policy);
        assertEquals(PolicyType.SYNC, policy.getType());
        assertEquals(syncURuleName, (policy.getSpecification(SyncPolicySpec.class)).getUserJavaRule());
        assertEquals(syncRRuleName, (policy.getSpecification(SyncPolicySpec.class)).getRoleJavaRule());
    }

    @Test
    public void update() {
        PasswordPolicySpec specification = new PasswordPolicySpec();
        specification.setMaxLength(8);
        specification.setMinLength(6);

        Policy policy = policyDAO.getGlobalPasswordPolicy();
        assertNotNull(policy);
        policy.setSpecification(specification);

        policy = policyDAO.save(policy);

        assertNotNull(policy);
        assertEquals(PolicyType.GLOBAL_PASSWORD, policy.getType());
        assertEquals((policy.getSpecification(PasswordPolicySpec.class)).getMaxLength(), 8);
        assertEquals((policy.getSpecification(PasswordPolicySpec.class)).getMinLength(), 6);
    }

    @Test
    public void delete() {
        Policy policy = policyDAO.find(1L);
        assertNotNull("find to delete did not work", policy);

        policyDAO.delete(policy);

        Policy actual = policyDAO.find(1L);
        assertNull("delete did not work", actual);
    }
}
