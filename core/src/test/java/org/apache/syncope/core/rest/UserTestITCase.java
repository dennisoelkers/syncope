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
package org.apache.syncope.core.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.syncope.client.mod.AttributeMod;
import org.apache.syncope.client.mod.MembershipMod;
import org.apache.syncope.client.mod.UserMod;
import org.apache.syncope.client.to.AttributeTO;
import org.apache.syncope.client.to.ConfigurationTO;
import org.apache.syncope.client.to.ConnObjectTO;
import org.apache.syncope.client.to.MembershipTO;
import org.apache.syncope.client.to.PasswordPolicyTO;
import org.apache.syncope.client.to.PolicyTO;
import org.apache.syncope.client.to.PropagationTO;
import org.apache.syncope.client.to.PropagationTaskTO;
import org.apache.syncope.client.to.ResourceTO;
import org.apache.syncope.client.to.UserTO;
import org.apache.syncope.client.to.WorkflowFormPropertyTO;
import org.apache.syncope.client.to.WorkflowFormTO;
import org.apache.syncope.client.util.AttributableOperations;
import org.apache.syncope.client.validation.SyncopeClientCompositeErrorException;
import org.apache.syncope.client.validation.SyncopeClientException;
import org.apache.syncope.core.persistence.beans.user.SyncopeUser;
import org.apache.syncope.core.workflow.ActivitiDetector;
import org.apache.syncope.types.AttributableType;
import org.apache.syncope.types.CipherAlgorithm;
import org.apache.syncope.types.PolicyType;
import org.apache.syncope.types.PropagationTaskExecStatus;
import org.apache.syncope.types.SyncopeClientExceptionType;
import org.apache.syncope.types.TaskType;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;

@FixMethodOrder(MethodSorters.JVM)
public class UserTestITCase extends AbstractTest {

    private ConnObjectTO readUserConnObj(String resourceName, String userId) {
        return resourceService.getConnector(resourceName, AttributableType.USER, userId);
    }

    public static UserTO getUniqueSampleTO(final String email) {
        return getSampleTO(getUUIDString() + email);
    }

    public static UserTO getSampleTO(final String email) {
        String uid = email;
        UserTO userTO = new UserTO();
        userTO.setPassword("password123");
        userTO.setUsername(uid);

        userTO.addAttribute(attributeTO("fullname", uid));
        userTO.addAttribute(attributeTO("firstname", uid));
        userTO.addAttribute(attributeTO("surname", "surname"));
        userTO.addAttribute(attributeTO("type", "a type"));
        userTO.addAttribute(attributeTO("userId", uid));
        userTO.addAttribute(attributeTO("email", uid));
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        userTO.addAttribute(attributeTO("loginDate", sdf.format(new Date())));
        userTO.addDerivedAttribute(attributeTO("cn", null));
        userTO.addVirtualAttribute(attributeTO("virtualdata", "virtualvalue"));
        return userTO;
    }

    @Test
    public void selfRead() {
        super.setupRestTemplate("user1", ADMIN_PWD);

        try {
            userService.read(1l);
            fail();
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }

        UserTO userTO = userService.readSelf();
        assertEquals("user1", userTO.getUsername());
    }

    @Test
    public void createUserWithNoPropagation() {
        // get task list
        List<PropagationTaskTO> tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long maxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > maxId) {
                maxId = task.getId();
            }
        }

        // create a new user
        UserTO userTO = getUniqueSampleTO("xxx@xxx.xxx");

        userTO.setPassword("password123");
        userTO.addResource("ws-target-resource-nopropagation");

        userService.create(userTO);

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long newMaxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }

        assertTrue(newMaxId > maxId);

        // get last task
        PropagationTaskTO taskTO = taskService.read(TaskType.PROPAGATION, newMaxId);

        assertNotNull(taskTO);
        assertTrue(taskTO.getExecutions().isEmpty());
    }

    @Test
    /*
     * This test has been introduced to verify and solve the following issue:
     * http://code.google.com/p/syncope/issues/detail?id=172. Creations of a new user without having a global password
     * policy stored into the local repository used to fail with a null pointer exception. This bug has been fixed
     * introducing a simple control.
     */
    public void issue172() {
        List<PasswordPolicyTO> policies = policyService.listByType(PolicyType.GLOBAL_PASSWORD);
        for (PasswordPolicyTO policyTO : policies) {
            policyService.delete(PolicyType.PASSWORD, policyTO.getId());
        }

        try {
            UserTO userTO = getUniqueSampleTO("issue172@syncope.apache.org");
            userService.create(userTO);
        } finally {
            for (PasswordPolicyTO policyTO : policies) {
                PolicyTO cPolicyTO = policyService.create(PolicyType.GLOBAL_PASSWORD, policyTO);
                assertNotNull(cPolicyTO);
            }
        }
    }

    @Test
    public void issue186() {
        // 1. create an user with strict mandatory attributes only
        UserTO userTO = new UserTO();
        String userId = getUUIDString() + "issue186@syncope.apache.org";
        userTO.setUsername(userId);
        userTO.setPassword("password");

        userTO.addAttribute(attributeTO("userId", userId));
        userTO.addAttribute(attributeTO("fullname", userId));
        userTO.addAttribute(attributeTO("surname", userId));

        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertTrue(userTO.getResources().isEmpty());

        // 2. update assigning a resource forcing mandatory constraints: must
        // fail with RequiredValuesMissing
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.addResourceToBeAdded("ws-target-resource-2");

        SyncopeClientException sce = null;
        try {
            userTO = userService.update(userMod.getId(), userMod);
        } catch (SyncopeClientCompositeErrorException scce) {
            sce = scce.getException(SyncopeClientExceptionType.RequiredValuesMissing);
        }
        assertNotNull(sce);

        // 3. update assigning a resource NOT forcing mandatory constraints
        // AND primary: must fail with PropagationException
        userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.addResourceToBeAdded("ws-target-resource-1");

        sce = null;
        try {
            userTO = userService.update(userMod.getId(), userMod);
        } catch (SyncopeClientCompositeErrorException scce) {
            sce = scce.getException(SyncopeClientExceptionType.Propagation);
        }
        assertNotNull(sce);

        // 4. update assigning a resource NOT forcing mandatory constraints
        // BUT not primary: must succeed
        userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.addResourceToBeAdded("resource-db");

        sce = null;
        try {
            userTO = userService.update(userMod.getId(), userMod);
        } catch (SyncopeClientCompositeErrorException scce) {
            sce = scce.getException(SyncopeClientExceptionType.Propagation);
        }
        assertNull(sce);
    }

    @Test
    public void testEnforceMandatoryCondition() {
        UserTO userTO = getUniqueSampleTO("enforce@apache.org");
        userTO.addResource("ws-target-resource-2");
        userTO.setPassword("newPassword");

        AttributeTO type = null;
        for (AttributeTO attr : userTO.getAttributes()) {
            if ("type".equals(attr.getSchema())) {
                type = attr;
            }
        }
        assertNotNull(type);
        userTO.removeAttribute(type);

        SyncopeClientException sce = null;
        try {
            userTO = userService.create(userTO);
        } catch (SyncopeClientCompositeErrorException scce) {
            sce = scce.getException(SyncopeClientExceptionType.RequiredValuesMissing);
        }
        assertNotNull(sce);

        userTO.addAttribute(type);
        userTO = userService.create(userTO);
        assertNotNull(userTO);
    }

    @Test
    public void testEnforceMandatoryConditionOnDerived() {
        ResourceTO resourceTO = resourceService.read("resource-csv");
        assertNotNull(resourceTO);
        resourceTO.setName("resource-csv-enforcing");
        resourceTO.setEnforceMandatoryCondition(true);
        resourceTO = resourceService.create(resourceTO);
        assertNotNull(resourceTO);

        UserTO userTO = getUniqueSampleTO("syncope222@apache.org");
        userTO.addResource(resourceTO.getName());
        userTO.setPassword("newPassword");

        SyncopeClientException sce = null;
        try {
            userTO = userService.create(userTO);
        } catch (SyncopeClientCompositeErrorException scce) {
            sce = scce.getException(SyncopeClientExceptionType.RequiredValuesMissing);
        }
        assertNotNull(sce);

        userTO.addDerivedAttribute(attributeTO("csvuserid", null));

        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertEquals(Collections.singleton("resource-csv-enforcing"), userTO.getResources());
    }

    @Test
    public void issue147() {
        // 1. create an user without role nor resources
        UserTO userTO = getUniqueSampleTO("147@t.com");

        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertTrue(userTO.getResources().isEmpty());

        // 2. try to update by adding a resource, but no password: must fail
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.addResourceToBeAdded("ws-target-resource-2");

        SyncopeClientException sce = null;
        try {
            userTO = userService.update(userMod.getId(), userMod);
        } catch (SyncopeClientCompositeErrorException scce) {
            sce = scce.getException(SyncopeClientExceptionType.RequiredValuesMissing);
        }
        assertNotNull(sce);

        // 3. provide password: now update must work
        userMod.setPassword("newPassword");
        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        assertEquals(1, userTO.getResources().size());
    }

    @Test
    public void createUserWithDbPropagation() {
        UserTO userTO = getUniqueSampleTO("yyy@yyy.yyy");
        userTO.addResource("resource-testdb");
        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getPropagationTOs().size());
        assertTrue(userTO.getPropagationTOs().get(0).getStatus().isSuccessful());
    }

    @Test(expected = SyncopeClientCompositeErrorException.class)
    public void createWithInvalidPassword() {
        UserTO userTO = getSampleTO("invalidpasswd@syncope.apache.org");
        userTO.setPassword("pass");
        userService.create(userTO);
    }

    @Test(expected = SyncopeClientCompositeErrorException.class)
    public void createWithInvalidUsername() {
        UserTO userTO = getSampleTO("invalidusername@syncope.apache.org");
        userTO.setUsername("us");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(7L);

        userTO.addMembership(membershipTO);

        userService.create(userTO);
    }

    @Test(expected = SyncopeClientCompositeErrorException.class)
    public void createWithInvalidPasswordByRes() {
        UserTO userTO = getSampleTO("invalidPwdByRes@passwd.com");

        // configured to be minLength=16
        userTO.setPassword("password1");

        userTO.setResources(Collections.singleton("ws-target-resource-nopropagation"));

        userService.create(userTO);
    }

    @Test(expected = SyncopeClientCompositeErrorException.class)
    public void createWithInvalidPasswordByRole() {
        UserTO userTO = getSampleTO("invalidPwdByRole@passwd.com");

        // configured to be minLength=16
        userTO.setPassword("password1");

        final MembershipTO membership = new MembershipTO();
        membership.setRoleId(8L);

        userTO.addMembership(membership);

        userService.create(userTO);
    }

    @Test(expected = SyncopeClientCompositeErrorException.class)
    public void createWithException() {
        UserTO newUserTO = new UserTO();
        newUserTO.addAttribute(attributeTO("userId", "userId@nowhere.org"));
        userService.create(newUserTO);
    }

    @Test
    public void create() {
        // get task list
        List<PropagationTaskTO> tasks =
                taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long maxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > maxId) {
                maxId = task.getId();
            }
        }
        PropagationTaskTO taskTO = taskService.read(TaskType.PROPAGATION, maxId);

        assertNotNull(taskTO);
        int maxTaskExecutions = taskTO.getExecutions().size();

        UserTO userTO = getUniqueSampleTO("a.b@c.com");

        // add a membership
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.addMembership(membershipTO);

        // add an attribute with no values: must be ignored
        membershipTO.addAttribute(attributeTO("subscriptionDate", null));

        // add an attribute with a non-existing schema: must be ignored
        AttributeTO attrWithInvalidSchemaTO = attributeTO("invalid schema", "a value");
        userTO.addAttribute(attrWithInvalidSchemaTO);

        // add an attribute with null value: must be ignored
        userTO.addAttribute(attributeTO("activationDate", null));

        // 1. create user
        UserTO newUserTO = userService.create(userTO);

        assertNotNull(newUserTO);
        assertFalse(newUserTO.getAttributes().contains(attrWithInvalidSchemaTO));

        // check for changePwdDate
        assertNotNull(newUserTO.getCreationDate());

        // 2. check for virtual attribute value
        newUserTO = userService.read(newUserTO.getId());
        assertNotNull(newUserTO);

        assertNotNull(newUserTO.getVirtualAttributeMap());
        assertNotNull(newUserTO.getVirtualAttributeMap().get("virtualdata").getValues());
        assertFalse(newUserTO.getVirtualAttributeMap().get("virtualdata").getValues().isEmpty());
        assertEquals("virtualvalue", newUserTO.getVirtualAttributeMap().get("virtualdata").getValues().get(0));

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long newMaxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }

        // default configuration for ws-target-resource2:
        //             only failed executions have to be registered
        // --> no more tasks/executions should be added
        assertEquals(newMaxId, maxId);

        // get last task
        taskTO = taskService.read(TaskType.PROPAGATION, newMaxId);

        assertNotNull(taskTO);
        assertEquals(maxTaskExecutions, taskTO.getExecutions().size());

        // 3. verify password
        Boolean verify = userService.verifyPassword(newUserTO.getUsername(), "password123");
        assertTrue(verify);

        verify = userService.verifyPassword(newUserTO.getUsername(), "passwordXX");
        assertFalse(verify);

        // 4. try (and fail) to create another user with same (unique) values
        userTO = getSampleTO(userTO.getUsername());
        for (AttributeTO attr : userTO.getAttributes()) {
            if ("userId".equals(attr.getSchema())) {
                attr.getValues().clear();
                attr.addValue("a.b@c.com");
            }
        }

        SyncopeClientException sce = null;
        try {
            userService.create(userTO);
        } catch (SyncopeClientCompositeErrorException e) {
            sce = e.getException(SyncopeClientExceptionType.DataIntegrityViolation);
        }
        assertNotNull(sce);
    }

    @Test
    public void createWithRequiredValueMissing() {
        UserTO userTO = getSampleTO("a.b@c.it");

        AttributeTO type = null;
        for (AttributeTO attr : userTO.getAttributes()) {
            if ("type".equals(attr.getSchema())) {
                type = attr;
            }
        }
        assertNotNull(type);

        userTO.removeAttribute(type);

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.addMembership(membershipTO);

        SyncopeClientCompositeErrorException ex = null;
        try {
            // 1. create user without type (mandatory by UserSchema)
            userService.create(userTO);
        } catch (SyncopeClientCompositeErrorException e) {
            ex = e;
        }
        assertNotNull(ex);
        assertNotNull(ex.getException(SyncopeClientExceptionType.RequiredValuesMissing));

        userTO.addAttribute(attributeTO("type", "F"));

        AttributeTO surname = null;
        for (AttributeTO attributeTO : userTO.getAttributes()) {
            if ("surname".equals(attributeTO.getSchema())) {
                surname = attributeTO;
            }
        }
        userTO.removeAttribute(surname);

        // 2. create user without surname (mandatory when type == 'F')
        ex = null;
        try {
            userService.create(userTO);
        } catch (SyncopeClientCompositeErrorException e) {
            ex = e;
        }
        assertNotNull(ex);
        assertNotNull(ex.getException(SyncopeClientExceptionType.RequiredValuesMissing));
    }

    @Test
    public void createWithReject() {
        Assume.assumeTrue(ActivitiDetector.isActivitiEnabledForUsers());

        UserTO userTO = getUniqueSampleTO("createWithReject@syncope.apache.org");

        // User with role 9 are defined in workflow as subject to approval
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(9L);
        userTO.addMembership(membershipTO);

        // 1. create user with role 9
        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getMemberships().size());
        assertEquals(9, userTO.getMemberships().get(0).getRoleId());
        assertEquals("createApproval", userTO.getStatus());

        // 2. request if there is any pending task for user just created
        WorkflowFormTO form = userService.getFormForUser(userTO.getId());

        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNull(form.getOwner());

        // 3. claim task from user1, not in role 7 (designated for approval in workflow definition): fail
        super.setupRestTemplate("user1", ADMIN_PWD);

        SyncopeClientException sce = null;
        try {
            userService.claimForm(form.getTaskId());
        } catch (SyncopeClientCompositeErrorException scce) {
            sce = scce.getException(SyncopeClientExceptionType.Workflow);
        }
        assertNotNull(sce);

        // 4. claim task from user4, in role 7
        super.setupRestTemplate("user4", ADMIN_PWD);

        form = userService.claimForm(form.getTaskId());
        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNotNull(form.getOwner());

        // 5. reject user
        Map<String, WorkflowFormPropertyTO> props = form.getPropertyMap();
        props.get("approve").setValue(Boolean.FALSE.toString());
        props.get("rejectReason").setValue("I don't like him.");
        form.setProperties(props.values());
        userTO = userService.submitForm(form);
        assertNotNull(userTO);
        assertEquals("rejected", userTO.getStatus());

        // reset admin credentials for restTemplate
        super.resetRestTemplate();
    }

    @Test
    public void createWithApproval() {
        Assume.assumeTrue(ActivitiDetector.isActivitiEnabledForUsers());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

        UserTO userTO = getUniqueSampleTO("createWithApproval@syncope.apache.org");
        userTO.addResource("resource-testdb");

        // User with role 9 are defined in workflow as subject to approval
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(9L);
        userTO.addMembership(membershipTO);

        // 1. create user with role 9 (and verify that no propagation occurred)
        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getMemberships().size());
        assertEquals(9, userTO.getMemberships().get(0).getRoleId());
        assertEquals("createApproval", userTO.getStatus());
        assertEquals(Collections.singleton("resource-testdb"), userTO.getResources());

        assertTrue(userTO.getPropagationTOs().isEmpty());

        Exception exception = null;
        try {
            jdbcTemplate.queryForInt("SELECT id FROM test WHERE id=?", userTO.getUsername());
        } catch (EmptyResultDataAccessException e) {
            exception = e;
        }
        assertNotNull(exception);

        // 2. request if there is any pending form for user just created
        List<WorkflowFormTO> forms = userService.getForms();
        assertNotNull(forms);
        assertEquals(1, forms.size());

        WorkflowFormTO form = userService.getFormForUser(userTO.getId());
        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNull(form.getOwner());

        // 4. claim task (from admin)
        form = userService.claimForm(form.getTaskId());
        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNotNull(form.getOwner());

        // 5. approve user (and verify that propagation occurred)
        Map<String, WorkflowFormPropertyTO> props = form.getPropertyMap();
        props.get("approve").setValue(Boolean.TRUE.toString());
        form.setProperties(props.values());
        userTO = userService.submitForm(form);
        assertNotNull(userTO);
        assertEquals("active", userTO.getStatus());
        assertEquals(Collections.singleton("resource-testdb"), userTO.getResources());

        exception = null;
        try {
            final String username = jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?", String.class, userTO.
                    getUsername());
            assertEquals(userTO.getUsername(), username);
        } catch (EmptyResultDataAccessException e) {
            exception = e;
        }
        assertNull(exception);

        // 6. update user
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("anotherPassword123");

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
    }

    @Test
    public void delete() {
        try {
            userService.delete(0l);
        } catch (HttpStatusCodeException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }

        UserTO userTO = getSampleTO("qqgf.z@nn.com");

        // specify a propagation
        userTO.addResource("resource-testdb");

        userTO = userService.create(userTO);

        long id = userTO.getId();

        userTO = userService.delete(id);

        assertNotNull(userTO);
        assertEquals(id, userTO.getId());
        assertTrue(userTO.getAttributes().isEmpty());

        // check for propagation result
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertTrue(userTO.getPropagationTOs().get(0).getStatus().isSuccessful());

        try {
            userService.delete(userTO.getId());
        } catch (HttpStatusCodeException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    public void deleteByUsername() {
        UserTO userTO = getSampleTO("delete.by.username@apache.org");

        // specify a propagation
        userTO.addResource("resource-testdb");

        userTO = userService.create(userTO);

        long id = userTO.getId();
        userTO = userService.read(id);
        userTO = userService.delete(userTO.getId());

        assertNotNull(userTO);
        assertEquals(id, userTO.getId());
        assertTrue(userTO.getAttributes().isEmpty());

        // check for propagation result
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertTrue(userTO.getPropagationTOs().get(0).getStatus().isSuccessful());

        try {
            userService.read(userTO.getId());
        } catch (HttpStatusCodeException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    public void count() {
        Integer count = userService.count();
        assertNotNull(count);
        assertTrue(count > 0);
    }

    @Test
    public void list() {
        List<UserTO> users = userService.list();
        assertNotNull(users);
        assertFalse(users.isEmpty());
        for (UserTO user : users) {
            assertNotNull(user);
        }
    }

    @Test
    public void paginatedList() {
        List<UserTO> users = userService.list(1, 2);

        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(2, users.size());

        for (UserTO user : users) {
            assertNotNull(user);
        }

        users = userService.list(2, 2);

        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(2, users.size());

        users = userService.list(100, 2);

        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    public void read() {
        UserTO userTO = userService.read(1l);

        assertNotNull(userTO);
        assertNotNull(userTO.getAttributes());
        assertFalse(userTO.getAttributes().isEmpty());
    }

    @Test
    public void updateWithouPassword() {
        UserTO userTO = getUniqueSampleTO("updatewithout@password.com");

        userTO = userService.create(userTO);

        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.addDerivedAttributeToBeRemoved("cn");

        userTO = userService.update(userMod.getId(), userMod);

        assertNotNull(userTO);
        assertNotNull(userTO.getDerivedAttributeMap());
        assertFalse(userTO.getDerivedAttributeMap().containsKey("cn"));
    }

    @Test(expected = SyncopeClientCompositeErrorException.class)
    public void updateInvalidPassword() {
        UserTO userTO = getSampleTO("updateinvalid@password.com");

        userTO = userService.create(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("pass");

        userTO = userService.update(userMod.getId(), userMod);
    }

    @Test(expected = SyncopeClientCompositeErrorException.class)
    public void updateSamePassword() {
        UserTO userTO = getSampleTO("updatesame@password.com");

        userTO = userService.create(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("password123");

        userTO = userService.update(userMod.getId(), userMod);
    }

    @Test
    public void update() {
        UserTO userTO = getUniqueSampleTO("g.h@t.com");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        membershipTO.addAttribute(attributeTO("subscriptionDate", "2009-08-18T16:33:12.203+0200"));
        userTO.addMembership(membershipTO);

        userTO = userService.create(userTO);

        assertFalse(userTO.getDerivedAttributes().isEmpty());
        assertEquals(1, userTO.getMemberships().size());

        MembershipMod membershipMod = new MembershipMod();
        membershipMod.setRole(8L);
        membershipMod.addAttributeToBeUpdated(attributeMod("subscriptionDate", "2010-08-18T16:33:12.203+0200"));

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("new2Password");

        userMod.addAttributeToBeRemoved("userId");
        String newUserId = getUUIDString() + "t.w@spre.net";
        userMod.addAttributeToBeUpdated(attributeMod("userId", newUserId));

        userMod.addAttributeToBeRemoved("fullname");
        String newFullName = getUUIDString() + "g.h@t.com";
        userMod.addAttributeToBeUpdated(attributeMod("fullname", newFullName));

        userMod.addDerivedAttributeToBeAdded("cn");
        userMod.addMembershipToBeAdded(membershipMod);
        userMod.addMembershipToBeRemoved(userTO.getMemberships().iterator().next().getId());

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        SyncopeUser passwordTestUser = new SyncopeUser();
        passwordTestUser.setPassword("new2Password", CipherAlgorithm.SHA1, 0);
        assertEquals(passwordTestUser.getPassword(), userTO.getPassword());

        assertEquals(1, userTO.getMemberships().size());
        assertEquals(1, userTO.getMemberships().iterator().next().getAttributes().size());
        assertFalse(userTO.getDerivedAttributes().isEmpty());

        boolean userIdFound = false;
        boolean fullnameFound = false;

        for (AttributeTO attributeTO : userTO.getAttributes()) {
            if ("userId".equals(attributeTO.getSchema())) {
                userIdFound = true;
                assertEquals(Collections.singletonList(newUserId), attributeTO.getValues());
            }
            if ("fullname".equals(attributeTO.getSchema())) {
                fullnameFound = true;
                assertEquals(Collections.singletonList(newFullName), attributeTO.getValues());
            }
        }
        assertTrue(userIdFound);
        assertTrue(fullnameFound);
    }

    @Test
    public void updatePasswordOnly() {
        List<PropagationTaskTO> beforeTasks = taskService.list(TaskType.PROPAGATION);
        assertNotNull(beforeTasks);
        assertFalse(beforeTasks.isEmpty());

        UserTO userTO = getUniqueSampleTO("pwdonly@t.com");
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        membershipTO.addAttribute(attributeTO("subscriptionDate", "2009-08-18T16:33:12.203+0200"));
        userTO.addMembership(membershipTO);

        userTO = userService.create(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword123");

        userTO = userService.update(userMod.getId(), userMod);

        // check for changePwdDate
        assertNotNull(userTO.getChangePwdDate());

        SyncopeUser passwordTestUser = new SyncopeUser();
        passwordTestUser.setPassword("newPassword123", CipherAlgorithm.SHA1, 0);
        assertEquals(passwordTestUser.getPassword(), userTO.getPassword());

        List<PropagationTaskTO> afterTasks = taskService.list(TaskType.PROPAGATION);
        assertNotNull(afterTasks);
        assertFalse(afterTasks.isEmpty());

        assertTrue(beforeTasks.size() < afterTasks.size());
    }

    @Test
    public void verifyTaskRegistration() {
        // get task list
        List<PropagationTaskTO> tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long maxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > maxId) {
                maxId = task.getId();
            }
        }

        // --------------------------------------
        // Create operation
        // --------------------------------------

        UserTO userTO = getUniqueSampleTO("t@p.mode");

        // add a membership
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.addMembership(membershipTO);

        // 1. create user
        userTO = userService.create(userTO);
        assertNotNull(userTO);

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long newMaxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }

        // default configuration for ws-target-resource2:
        //             only failed executions have to be registered
        // --> no more tasks/executions should be added
        assertEquals(newMaxId, maxId);

        // --------------------------------------
        // Update operation
        // --------------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        userMod.addAttributeToBeUpdated(attributeMod("surname", "surname"));

        userTO = userService.update(userMod.getId(), userMod);

        assertNotNull(userTO);

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        // get max task id
        maxId = newMaxId;
        newMaxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }

        // default configuration for ws-target-resource2:
        //             all update executions have to be registered
        assertTrue(newMaxId > maxId);

        final PropagationTaskTO taskTO = taskService.read(TaskType.PROPAGATION, newMaxId);

        assertNotNull(taskTO);
        assertEquals(1, taskTO.getExecutions().size());

        // --------------------------------------
        // Delete operation
        // --------------------------------------
        userService.delete(userTO.getId());

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        // get max task id
        maxId = newMaxId;
        newMaxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }

        // default configuration for ws-target-resource2: no delete executions have to be registered
        // --> no more tasks/executions should be added
        assertEquals(newMaxId, maxId);
    }

    @Test
    public void createActivate() {
        Assume.assumeTrue(ActivitiDetector.isActivitiEnabledForUsers());

        UserTO userTO = getUniqueSampleTO("createActivate@syncope.apache.org");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(11L);
        userTO.addMembership(membershipTO);

        userTO = userService.create(userTO);

        assertNotNull(userTO);
        assertNotNull(userTO.getToken());
        assertNotNull(userTO.getTokenExpireTime());

        assertEquals("created", userTO.getStatus());

        userTO = userService.activate(userTO.getId(), userTO.getToken());

        assertNotNull(userTO);
        assertNull(userTO.getToken());
        assertNull(userTO.getTokenExpireTime());

        assertEquals("active", userTO.getStatus());
    }

    @Test
    public void createActivateByUsername() {
        Assume.assumeTrue(ActivitiDetector.isActivitiEnabledForUsers());

        UserTO userTO = getUniqueSampleTO("createActivateByUsername@syncope.apache.org");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(11L);
        userTO.addMembership(membershipTO);

        userTO = userService.create(userTO);

        assertNotNull(userTO);
        assertNotNull(userTO.getToken());
        assertNotNull(userTO.getTokenExpireTime());

        assertEquals("created", userTO.getStatus());

        userTO = userService.activateByUsername(userTO.getUsername(), userTO.getToken());


        assertNotNull(userTO);
        assertNull(userTO.getToken());
        assertNull(userTO.getTokenExpireTime());

        assertEquals("active", userTO.getStatus());
    }

    @Test
    public void suspendReactivate() {
        UserTO userTO = getUniqueSampleTO("suspendReactivate@syncope.apache.org");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(7L);
        userTO.addMembership(membershipTO);

        userTO = userService.create(userTO);

        assertNotNull(userTO);
        assertEquals(ActivitiDetector.isActivitiEnabledForUsers() ? "active" : "created", userTO.getStatus());

        userTO = userService.suspend(userTO.getId());

        assertNotNull(userTO);
        assertEquals("suspended", userTO.getStatus());

        userTO = userService.reactivate(userTO.getId());

        assertNotNull(userTO);
        assertEquals("active", userTO.getStatus());
    }

    @Test
    public void suspendReactivateByUsername() {
        UserTO userTO = getUniqueSampleTO("suspendReactivateByUsername@syncope.apache.org");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(7L);
        userTO.addMembership(membershipTO);

        userTO = userService.create(userTO);

        assertNotNull(userTO);
        assertEquals(ActivitiDetector.isActivitiEnabledForUsers() ? "active" : "created", userTO.getStatus());

        userTO = userService.suspendByUsername(userTO.getUsername());

        assertNotNull(userTO);
        assertEquals("suspended", userTO.getStatus());

        userTO = userService.reactivateByUsername(userTO.getUsername());

        assertNotNull(userTO);
        assertEquals("active", userTO.getStatus());
    }

    @Test
    public void suspendReactivateOnResource() {
        UserTO userTO = getUniqueSampleTO("suspreactonresource@syncope.apache.org");

        userTO.getMemberships().clear();
        userTO.getResources().clear();

        ResourceTO dbTable = resourceService.read("resource-testdb");

        assertNotNull(dbTable);
        userTO.addResource(dbTable.getName());

        ResourceTO ldap = resourceService.read("resource-ldap");

        assertNotNull(ldap);
        userTO.addResource(ldap.getName());

        userTO = userService.create(userTO);

        assertNotNull(userTO);
        assertEquals(ActivitiDetector.isActivitiEnabledForUsers() ? "active" : "created", userTO.getStatus());

        String query = "?resourceNames=" + dbTable.getName() + "&resourceNames=" + ldap.getName()
                + "&performLocally=true"; // check also performLocally

        userTO = userService.suspend(userTO.getId(), query);

        assertNotNull(userTO);
        assertEquals("suspended", userTO.getStatus());

        String dbTableUID = userTO.getUsername();
        assertNotNull(dbTableUID);

        ConnObjectTO connObjectTO = readUserConnObj(dbTable.getName(), dbTableUID);
        assertFalse(Boolean.parseBoolean(connObjectTO.getAttributeMap().get(OperationalAttributes.ENABLE_NAME).
                getValues().
                get(0)));

        String ldapUID = userTO.getUsername();
        assertNotNull(ldapUID);

        connObjectTO = readUserConnObj(ldap.getName(), ldapUID);
        assertNotNull(connObjectTO);

        query = "?resourceNames=" + ldap.getName() + "&performLocally=false"; // check also performLocally

        userTO = userService.reactivate(userTO.getId(), query);
        assertNotNull(userTO);
        assertEquals("suspended", userTO.getStatus());

        connObjectTO = readUserConnObj(dbTable.getName(), dbTableUID);
        assertFalse(Boolean.parseBoolean(connObjectTO.getAttributeMap().get(OperationalAttributes.ENABLE_NAME).
                getValues().
                get(0)));

        query = "?resourceNames=" + dbTable.getName() + "&performLocally=true"; // check also performLocally

        userTO = userService.reactivate(userTO.getId(), query);
        assertNotNull(userTO);
        assertEquals("active", userTO.getStatus());

        connObjectTO = readUserConnObj(dbTable.getName(), dbTableUID);
        assertTrue(Boolean.parseBoolean(connObjectTO.getAttributeMap().get(OperationalAttributes.ENABLE_NAME).
                getValues().
                get(0)));
    }

    public void updateMultivalueAttribute() {
        UserTO userTO = getSampleTO("multivalue@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getDerivedAttributes().clear();
        userTO.getVirtualAttributes().clear();

        userTO = userService.create(userTO);
        assertNotNull(userTO);

        AttributeTO loginDate = userTO.getAttributeMap().get("loginDate");
        assertNotNull(loginDate);
        assertEquals(1, loginDate.getValues().size());

        UserMod userMod = new UserMod();

        AttributeMod loginDateMod = new AttributeMod();
        loginDateMod.addValueToBeAdded("2000-01-01");

        userMod.setId(userTO.getId());
        userMod.addAttributeToBeUpdated(loginDateMod);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        loginDate = userTO.getAttributeMap().get("loginDate");
        assertNotNull(loginDate);
        assertEquals(2, loginDate.getValues().size());
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void issue213() {
        UserTO userTO = getUniqueSampleTO("issue213@syncope.apache.org");
        userTO.addResource("resource-testdb");

        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getResources().size());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

        String username =
                jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?", String.class, userTO.getUsername());

        assertEquals(userTO.getUsername(), username);

        UserMod userMod = new UserMod();

        userMod.setId(userTO.getId());
        userMod.addResourceToBeRemoved("resource-testdb");

        userTO = userService.update(userMod.getId(), userMod);

        assertTrue(userTO.getResources().isEmpty());

        jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?", String.class, userTO.getUsername());
    }

    @Test
    public void issue234() {
        UserTO inUserTO = getUniqueSampleTO("issue234@syncope.apache.org");
        inUserTO.addResource("resource-ldap");

        UserTO userTO = userService.create(inUserTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();

        userMod.setId(userTO.getId());
        userMod.setUsername("1" + userTO.getUsername());

        userTO = userService.update(userMod.getId(), userMod);

        assertNotNull(userTO);

        assertEquals("1" + inUserTO.getUsername(), userTO.getUsername());
    }

    @Test
    public void issue270() {
        // 1. create a new user without virtual attributes
        UserTO original = getUniqueSampleTO("issue270@syncope.apache.org");
        // be sure to remove all virtual attributes
        original.setVirtualAttributes(Collections.<AttributeTO>emptyList());

        original = userService.create(original);

        assertNotNull(original);

        assertTrue(original.getVirtualAttributes().isEmpty());

        UserTO toBeUpdated = userService.read(original.getId());

        AttributeTO virtual = attributeTO("virtualdata", "virtualvalue");
        toBeUpdated.addVirtualAttribute(virtual);

        // 2. try to update by adding a resource, but no password: must fail
        UserMod userMod = AttributableOperations.diff(toBeUpdated, original);

        assertNotNull(userMod);

        toBeUpdated = userService.update(userMod.getId(), userMod);

        assertNotNull(toBeUpdated);

        assertFalse(toBeUpdated.getVirtualAttributes().isEmpty());
        assertNotNull(toBeUpdated.getVirtualAttributes().get(0));

        assertEquals(virtual.getSchema(), toBeUpdated.getVirtualAttributes().get(0).getSchema());
    }

    @Test
    public final void issue280() {
        UserTO userTO = getUniqueSampleTO("issue280@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerivedAttributes().clear();

        userTO = userService.create(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("123password");
        userMod.addResourceToBeAdded("resource-testdb");

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        final List<PropagationTO> propagations = userTO.getPropagationTOs();

        assertNotNull(propagations);
        assertEquals(1, propagations.size());

        final PropagationTaskExecStatus status = propagations.get(0).getStatus();
        final String resource = propagations.get(0).getResourceName();

        assertNotNull(status);
        assertEquals("resource-testdb", resource);
        assertTrue(status.isSuccessful());
    }

    @Test
    public void issue281() {
        UserTO userTO = getUniqueSampleTO("issue281@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerivedAttributes().clear();
        userTO.addResource("resource-csv");

        userTO = userService.create(userTO);
        assertNotNull(userTO);

        final List<PropagationTO> propagations = userTO.getPropagationTOs();

        assertNotNull(propagations);
        assertEquals(1, propagations.size());

        final PropagationTaskExecStatus status = propagations.get(0).getStatus();
        final String resource = propagations.get(0).getResourceName();

        assertNotNull(status);
        assertEquals("resource-csv", resource);
        assertFalse(status.isSuccessful());
    }

    @Test
    public void issue288() {
        UserTO userTO = getSampleTO("issue288@syncope.apache.org");
        userTO.addAttribute(attributeTO("aLong", "STRING"));

        try {
            userService.create(userTO);
            fail();
        } catch (SyncopeClientCompositeErrorException sccee) {
            assertNotNull(sccee.getException(SyncopeClientExceptionType.InvalidValues));
        }
    }

    @Test
    public void roleAttrPropagation() {
        UserTO userTO = getUniqueSampleTO("checkRoleAttrPropagation@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerivedAttributes().clear();
        userTO.getVirtualAttributes().clear();

        userTO.addDerivedAttribute(attributeTO("csvuserid", null));

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(1L);

        userTO.addMembership(membershipTO);

        userTO.addResource("resource-csv");

        UserTO actual = userService.create(userTO);

        assertNotNull(actual);
        assertNotNull(actual.getDerivedAttributeMap().get("csvuserid"));

        String userId = actual.getDerivedAttributeMap().get("csvuserid").getValues().get(0);
        ConnObjectTO connObjectTO = readUserConnObj("resource-csv", userId);
        assertNotNull(connObjectTO);
        assertEquals("sx-dx", connObjectTO.getAttributeMap().get("ROLE").getValues().get(0));
    }

    @Test
    public void membershipAttrPropagation() {
        UserTO userTO = getUniqueSampleTO("checkMembAttrPropagation@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerivedAttributes().clear();
        userTO.getVirtualAttributes().clear();
        userTO.addDerivedAttribute(attributeTO("csvuserid", null));

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(1L);
        membershipTO.addAttribute(attributeTO("mderived_sx", "sx"));
        membershipTO.addAttribute(attributeTO("mderived_dx", "dx"));
        membershipTO.addDerivedAttribute(attributeTO("mderToBePropagated", null));
        userTO.addMembership(membershipTO);

        userTO.addResource("resource-csv");

        UserTO actual = userService.create(userTO);

        assertNotNull(actual);
        assertNotNull(actual.getDerivedAttributeMap().get("csvuserid"));

        String userId = actual.getDerivedAttributeMap().get("csvuserid").getValues().get(0);
        ConnObjectTO connObjectTO = readUserConnObj("resource-csv", userId);
        assertNotNull(connObjectTO);
        assertEquals("sx-dx", connObjectTO.getAttributeMap().get("MEMBERSHIP").getValues().get(0));
    }

    @Test
    public void issueSYNCOPE16() {
        UserTO userTO = getUniqueSampleTO("issue16@apache.org");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.addMembership(membershipTO);

        // 1. create user
        UserTO actual = userService.create(userTO);
        assertNotNull(actual);

        // 2. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertEquals("virtualvalue", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));

        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());
        userMod.addVirtualAttributeToBeRemoved("virtualdata");
        userMod.addVirtualAttributeToBeUpdated(attributeMod("virtualdata", "virtualupdated"));

        // 3. update virtual attribute
        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);

        // 4. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertEquals("virtualupdated", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));
    }

    @Test
    public void issueSYNCOPE108() {
        UserTO userTO = getUniqueSampleTO("syncope108@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerivedAttributes().clear();
        userTO.getVirtualAttributes().clear();
        userTO.addDerivedAttribute(attributeTO("csvuserid", null));

        MembershipTO memb12 = new MembershipTO();
        memb12.setRoleId(12L);

        userTO.addMembership(memb12);

        MembershipTO memb13 = new MembershipTO();
        memb13.setRoleId(13L);

        userTO.addMembership(memb13);

        userTO.addResource("resource-csv");

        UserTO actual = userService.create(userTO);

        assertNotNull(actual);
        assertEquals(2, actual.getMemberships().size());
        assertEquals(1, actual.getResources().size());

        String userId = actual.getDerivedAttributeMap().get("csvuserid").getValues().get(0);
        ConnObjectTO connObjectTO = readUserConnObj("resource-csv", userId);
        assertNotNull(connObjectTO);

        // -----------------------------------
        // Remove the first membership: de-provisioning shouldn't happen
        // -----------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());

        userMod.addMembershipToBeRemoved(actual.getMemberships().get(0).getId());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertEquals(1, actual.getMemberships().size());

        connObjectTO = readUserConnObj("resource-csv", userId);
        assertNotNull(connObjectTO);
        // -----------------------------------

        // -----------------------------------
        // Remove the resource assigned directly: de-provisioning shouldn't happen
        // -----------------------------------
        userMod = new UserMod();
        userMod.setId(actual.getId());

        userMod.addResourceToBeRemoved(actual.getResources().iterator().next());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertEquals(1, actual.getMemberships().size());
        assertFalse(actual.getResources().isEmpty());

        connObjectTO = readUserConnObj("resource-csv", userId);
        assertNotNull(connObjectTO);
        // -----------------------------------

        // -----------------------------------
        // Remove the first membership: de-provisioning should happen
        // -----------------------------------
        userMod = new UserMod();
        userMod.setId(actual.getId());

        userMod.addMembershipToBeRemoved(actual.getMemberships().get(0).getId());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertTrue(actual.getMemberships().isEmpty());
        assertTrue(actual.getResources().isEmpty());

        Throwable t = null;

        try {
            readUserConnObj("resource-csv", userId);
        } catch (SyncopeClientCompositeErrorException e) {
            assertNotNull(e.getException(SyncopeClientExceptionType.NotFound));
            t = e;
        }

        assertNotNull(t);
        // -----------------------------------
    }

    @Test
    public void issueSYNCOPE111() {
        UserTO userTO = getUniqueSampleTO("syncope111@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerivedAttributes().clear();
        userTO.getVirtualAttributes().clear();
        userTO.addDerivedAttribute(attributeTO("csvuserid", null));

        MembershipTO memb12 = new MembershipTO();
        memb12.setRoleId(12L);
        memb12.addAttribute(attributeTO("postalAddress", "postalAddress"));
        userTO.addMembership(memb12);

        MembershipTO memb13 = new MembershipTO();
        memb13.setRoleId(13L);
        userTO.addMembership(memb13);

        userTO.addResource("resource-ldap");

        UserTO actual = userService.create(userTO);
        assertNotNull(actual);
        assertEquals(2, actual.getMemberships().size());

        ConnObjectTO connObjectTO = readUserConnObj("resource-ldap", userTO.getUsername());
        assertNotNull(connObjectTO);

        AttributeTO postalAddress = connObjectTO.getAttributeMap().get("postalAddress");
        assertNotNull(postalAddress);
        assertEquals(1, postalAddress.getValues().size());
        assertEquals("postalAddress", postalAddress.getValues().get(0));

        AttributeTO title = connObjectTO.getAttributeMap().get("title");
        assertNotNull(title);
        assertEquals(2, title.getValues().size());
        assertTrue(title.getValues().contains("r12") && title.getValues().contains("r13"));

        // -----------------------------------
        // Remove the first membership and check for membership attr propagation and role attr propagation
        // -----------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());

        MembershipTO membershipTO = actual.getMemberships().get(0).getRoleId() == 12L
                ? actual.getMemberships().get(0) : actual.getMemberships().get(1);

        userMod.addMembershipToBeRemoved(membershipTO.getId());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertEquals(1, actual.getMemberships().size());

        connObjectTO = readUserConnObj("resource-ldap", userTO.getUsername());
        assertNotNull(connObjectTO);

        postalAddress = connObjectTO.getAttributeMap().get("postalAddress");
        assertTrue(postalAddress == null
                || postalAddress.getValues().isEmpty() || StringUtils.hasText(postalAddress.getValues().get(0)));

        title = connObjectTO.getAttributeMap().get("title");
        assertNotNull(title);
        assertEquals(1, title.getValues().size());
        assertTrue(title.getValues().contains("r13"));
        // -----------------------------------
    }

    @Test
    public void issueSYNCOPE185() {
        // 1. create user with LDAP resource, succesfully propagated
        UserTO userTO = getSampleTO("syncope185@syncope.apache.org");
        userTO.getVirtualAttributes().clear();
        userTO.addResource("resource-ldap");

        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertEquals("resource-ldap", userTO.getPropagationTOs().get(0).getResourceName());
        assertEquals(PropagationTaskExecStatus.SUCCESS, userTO.getPropagationTOs().get(0).getStatus());

        // 2. delete this user
        userService.delete(userTO.getId());

        // 3. try (and fail) to find this user on the external LDAP resource
        SyncopeClientException sce = null;
        try {
            readUserConnObj("resource-ldap", userTO.getUsername());
            fail("This entry should not be present on this resource");
        } catch (SyncopeClientCompositeErrorException sccee) {
            sce = sccee.getException(SyncopeClientExceptionType.NotFound);
        }
        assertNotNull(sce);
    }

    @Test()
    public void issueSYNCOPE51() {
        ConfigurationTO defaultConfigurationTO = configurationService.read("password.cipher.algorithm");

        ConfigurationTO configurationTO = new ConfigurationTO();
        configurationTO.setKey("password.cipher.algorithm");
        configurationTO.setValue("MD5");

        ConfigurationTO newConfTO = configurationService.update(configurationTO.getKey(), configurationTO);

        assertEquals(configurationTO, newConfTO);

        UserTO userTO = getSampleTO("syncope51@syncope.apache.org");
        userTO.setPassword("password");

        try {
            userService.create(userTO);
            fail();
        } catch (SyncopeClientCompositeErrorException e) {
            assertTrue(
                    e.getException(SyncopeClientExceptionType.NotFound).getElements().iterator().next().contains("MD5"));
        }

        ConfigurationTO oldConfTO = configurationService.update(defaultConfigurationTO.getKey(), defaultConfigurationTO);

        assertEquals(defaultConfigurationTO, oldConfTO);
    }

    @Test
    public void issueSYNCOPE260() {
        // ----------------------------------
        // create user and check virtual attribute value propagation
        // ----------------------------------
        UserTO userTO = getUniqueSampleTO("260@a.com");
        userTO.addResource("ws-target-resource-2");

        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationTOs().get(0).getResourceName());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationTOs().get(0).getStatus());

        ConnObjectTO connObjectTO = readUserConnObj("ws-target-resource-2", userTO.getUsername());
        assertNotNull(connObjectTO);
        assertEquals("virtualvalue", connObjectTO.getAttributeMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // update user virtual attribute and check virtual attribute value update propagation
        // ----------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        AttributeMod attrMod = new AttributeMod();
        attrMod.setSchema("virtualdata");
        attrMod.addValueToBeRemoved("virtualvalue");
        attrMod.addValueToBeAdded("virtualvalue2");

        userMod.addVirtualAttributeToBeUpdated(attrMod);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationTOs().get(0).getResourceName());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationTOs().get(0).getStatus());

        connObjectTO = readUserConnObj("ws-target-resource-2", userTO.getUsername());
        assertNotNull(connObjectTO);
        assertEquals("virtualvalue2", connObjectTO.getAttributeMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // suspend/reactivate user and check virtual attribute value (unchanged)
        // ----------------------------------
        userTO = userService.suspend(userTO.getId());
        assertEquals("suspended", userTO.getStatus());

        connObjectTO = readUserConnObj("ws-target-resource-2", userTO.getUsername());
        assertNotNull(connObjectTO);
        assertFalse(connObjectTO.getAttributeMap().get("NAME").getValues().isEmpty());
        assertEquals("virtualvalue2", connObjectTO.getAttributeMap().get("NAME").getValues().get(0));

        userTO = userService.reactivate(userTO.getId());
        assertEquals("active", userTO.getStatus());

        connObjectTO = readUserConnObj("ws-target-resource-2", userTO.getUsername());
        assertNotNull(connObjectTO);
        assertFalse(connObjectTO.getAttributeMap().get("NAME").getValues().isEmpty());
        assertEquals("virtualvalue2", connObjectTO.getAttributeMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // update user attribute and check virtual attribute value (unchanged)
        // ----------------------------------
        userMod = new UserMod();
        userMod.setId(userTO.getId());

        attrMod = new AttributeMod();
        attrMod.setSchema("surname");
        attrMod.addValueToBeRemoved("Surname");
        attrMod.addValueToBeAdded("Surname2");

        userMod.addAttributeToBeUpdated(attrMod);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationTOs().get(0).getResourceName());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationTOs().get(0).getStatus());

        connObjectTO = readUserConnObj("ws-target-resource-2", userTO.getUsername());
        assertNotNull(connObjectTO);
        assertEquals("Surname2", connObjectTO.getAttributeMap().get("SURNAME").getValues().get(0));

        // attribute "name" mapped on virtual attribute "virtualdata" shouldn't be changed
        assertFalse(connObjectTO.getAttributeMap().get("NAME").getValues().isEmpty());
        assertEquals("virtualvalue2", connObjectTO.getAttributeMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // remove user virtual attribute and check virtual attribute value (reset)
        // ----------------------------------
        userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.addVirtualAttributeToBeRemoved("virtualdata");

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        assertTrue(userTO.getVirtualAttributes().isEmpty());
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationTOs().get(0).getResourceName());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationTOs().get(0).getStatus());

        connObjectTO = readUserConnObj("ws-target-resource-2", userTO.getUsername());
        assertNotNull(connObjectTO);

        // attribute "name" mapped on virtual attribute "virtualdata" should be reset
        assertTrue(connObjectTO.getAttributeMap().get("NAME").getValues() == null
                || connObjectTO.getAttributeMap().get("NAME").getValues().isEmpty());
        // ----------------------------------
    }

    @Test
    public void issueSYNCOPE267() {
        // ----------------------------------
        // create user and check virtual attribute value propagation
        // ----------------------------------
        UserTO userTO = getUniqueSampleTO("syncope267@apache.org");
        userTO.getResources().clear();
        userTO.addResource("resource-db-virattr");

        userTO = userService.create(userTO);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationTOs().isEmpty());
        assertEquals("resource-db-virattr", userTO.getPropagationTOs().get(0).getResourceName());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationTOs().get(0).getStatus());

        ConnObjectTO connObjectTO = readUserConnObj("resource-db-virattr", String.valueOf(userTO.getId()));
        assertNotNull(connObjectTO);
        assertEquals("virtualvalue", connObjectTO.getAttributeMap().get("USERNAME").getValues().get(0));
        // ----------------------------------

        userTO = userService.read(userTO.getId());

        assertNotNull(userTO);
        assertEquals(1, userTO.getVirtualAttributes().size());
        assertEquals("virtualvalue", userTO.getVirtualAttributes().get(0).getValues().get(0));
    }

    @Test
    public void issueSYNCOPE266() {
        UserTO userTO = getSampleTO("syncope266@apache.org");
        userTO.getResources().clear();

        userTO = userService.create(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        // this resource has not a mapping for Password
        userMod.addResourceToBeAdded("ws-target-resource-update");

        userTO = userService.update(userTO.getId(), userMod);
        assertNotNull(userTO);
    }
}
