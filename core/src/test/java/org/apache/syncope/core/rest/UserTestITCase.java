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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.AccessControlException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;

import org.apache.syncope.common.mod.AttributeMod;
import org.apache.syncope.common.mod.MembershipMod;
import org.apache.syncope.common.mod.UserMod;
import org.apache.syncope.common.services.PolicyService;
import org.apache.syncope.common.services.ResourceService;
import org.apache.syncope.common.services.UserService;
import org.apache.syncope.common.services.UserWorkflowService;
import org.apache.syncope.common.to.AttributeTO;
import org.apache.syncope.common.to.BulkAction;
import org.apache.syncope.common.to.BulkActionRes;
import org.apache.syncope.common.to.BulkActionRes.Status;
import org.apache.syncope.common.to.ConfigurationTO;
import org.apache.syncope.common.to.ConnObjectTO;
import org.apache.syncope.common.to.MappingItemTO;
import org.apache.syncope.common.to.MappingTO;
import org.apache.syncope.common.to.MembershipTO;
import org.apache.syncope.common.to.PasswordPolicyTO;
import org.apache.syncope.common.to.PropagationRequestTO;
import org.apache.syncope.common.to.PropagationStatusTO;
import org.apache.syncope.common.to.PropagationTargetsTO;
import org.apache.syncope.common.to.PropagationTaskTO;
import org.apache.syncope.common.to.ResourceTO;
import org.apache.syncope.common.to.RoleTO;
import org.apache.syncope.common.to.UserTO;
import org.apache.syncope.common.to.WorkflowFormPropertyTO;
import org.apache.syncope.common.to.WorkflowFormTO;
import org.apache.syncope.common.types.AttributableType;
import org.apache.syncope.common.types.CipherAlgorithm;
import org.apache.syncope.common.types.IntMappingType;
import org.apache.syncope.common.types.MappingPurpose;
import org.apache.syncope.common.types.PolicyType;
import org.apache.syncope.common.types.PropagationTaskExecStatus;
import org.apache.syncope.common.types.ClientExceptionType;
import org.apache.syncope.common.types.TaskType;
import org.apache.syncope.common.util.AttributableOperations;
import org.apache.syncope.common.validation.SyncopeClientException;
import org.apache.syncope.core.persistence.beans.user.SyncopeUser;
import org.apache.syncope.core.persistence.dao.NotFoundException;
import org.apache.syncope.core.workflow.ActivitiDetector;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

@FixMethodOrder(MethodSorters.JVM)
public class UserTestITCase extends AbstractTest {

    private static final String RESOURCE_NAME_LDAP = "resource-ldap";

    private static final String RESOURCE_NAME_TESTDB = "resource-testdb";

    private static final String RESOURCE_NAME_CSV = "resource-csv";

    private ConnObjectTO readConnectorObject(final String resourceName, final Long userId) {
        return resourceService.getConnectorObject(resourceName, AttributableType.USER, userId);
    }

    private String getStringAttribute(final ConnObjectTO connObjectTO, final String attrName) {
        return connObjectTO.getAttrMap().get(attrName).getValues().get(0);
    }

    private boolean getBooleanAttribute(final ConnObjectTO connObjectTO, final String attrName) {
        return Boolean.parseBoolean(getStringAttribute(connObjectTO, attrName));
    }

    private long getMaxTaskId(final List<PropagationTaskTO> tasks) {
        long newMaxId = Long.MIN_VALUE;
        for (PropagationTaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }
        return newMaxId;
    }

    public static UserTO getUniqueSampleTO(final String email) {
        return getSampleTO(getUUIDString() + email);
    }

    public static UserTO getSampleTO(final String email) {
        String uid = email;
        UserTO userTO = new UserTO();
        userTO.setPassword("password123");
        userTO.setUsername(uid);

        userTO.getAttrs().add(attributeTO("fullname", uid));
        userTO.getAttrs().add(attributeTO("firstname", uid));
        userTO.getAttrs().add(attributeTO("surname", "surname"));
        userTO.getAttrs().add(attributeTO("type", "a type"));
        userTO.getAttrs().add(attributeTO("userId", uid));
        userTO.getAttrs().add(attributeTO("email", uid));
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        userTO.getAttrs().add(attributeTO("loginDate", sdf.format(new Date())));
        userTO.getDerAttrs().add(attributeTO("cn", null));
        userTO.getVirAttrs().add(attributeTO("virtualdata", "virtualvalue"));
        return userTO;
    }

    @Test
    public void selfRead() {
        UserService userService2 = clientFactory.create("rossini", ADMIN_PWD).getService(UserService.class);

        try {
            userService2.read(1L);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getType().getResponseStatus().getStatusCode());
        } catch (AccessControlException e) {
            // Will be thrown by cxf service
            assertNotNull(e);
        }

        UserTO userTO = userService2.readSelf();
        assertEquals("rossini", userTO.getUsername());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void createUserWithNoPropagation() {
        // get task list
        List<PropagationTaskTO> tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        long maxId = getMaxTaskId(tasks);

        // create a new user
        UserTO userTO = getUniqueSampleTO("xxx@xxx.xxx");

        userTO.setPassword("password123");
        userTO.getResources().add("ws-target-resource-nopropagation");

        createUser(userTO);

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        long newMaxId = getMaxTaskId(tasks);

        assertTrue(newMaxId > maxId);

        // get last task
        PropagationTaskTO taskTO = taskService.read(newMaxId);

        assertNotNull(taskTO);
        assertTrue(taskTO.getExecutions().isEmpty());
    }

    @Test
    public void issue172() {
        List<PasswordPolicyTO> policies = policyService.list(PolicyType.GLOBAL_PASSWORD);
        for (PasswordPolicyTO policyTO : policies) {
            policyService.delete(policyTO.getId());
        }

        try {
            UserTO userTO = getUniqueSampleTO("issue172@syncope.apache.org");
            createUser(userTO);
        } finally {
            for (PasswordPolicyTO policyTO : policies) {
                Response response = policyService.create(policyTO);
                PasswordPolicyTO cPolicyTO = adminClient.getObject(
                        response.getLocation(), PolicyService.class, PasswordPolicyTO.class);
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

        userTO.getAttrs().add(attributeTO("userId", userId));
        userTO.getAttrs().add(attributeTO("fullname", userId));
        userTO.getAttrs().add(attributeTO("surname", userId));

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertTrue(userTO.getResources().isEmpty());

        // 2. update assigning a resource forcing mandatory constraints: must fail with RequiredValuesMissing
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.getResourcesToAdd().add("ws-target-resource-2");

        try {
            userTO = userService.update(userMod.getId(), userMod);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.RequiredValuesMissing, e.getType());
        }

        // 3. update assigning a resource NOT forcing mandatory constraints
        // AND primary: must fail with PropagationException
        userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.getResourcesToAdd().add("ws-target-resource-1");

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO.getPropagationStatusTOs().get(0).getFailureReason());

        // 4. update assigning a resource NOT forcing mandatory constraints
        // BUT not primary: must succeed
        userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.getResourcesToAdd().add("resource-db");

        try {
            userTO = userService.update(userMod.getId(), userMod);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.InvalidSyncopeUser, e.getType());
        }
    }

    @Test
    public void testEnforceMandatoryCondition() {
        UserTO userTO = getUniqueSampleTO("enforce@apache.org");
        userTO.getResources().add("ws-target-resource-2");
        userTO.setPassword("newPassword");

        AttributeTO type = null;
        for (AttributeTO attr : userTO.getAttrs()) {
            if ("type".equals(attr.getSchema())) {
                type = attr;
            }
        }
        assertNotNull(type);
        userTO.getAttrs().remove(type);

        try {
            userTO = createUser(userTO);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.RequiredValuesMissing, e.getType());
        }

        userTO.getAttrs().add(type);
        userTO = createUser(userTO);
        assertNotNull(userTO);
    }

    @Test
    public void testEnforceMandatoryConditionOnDerived() {
        ResourceTO resourceTO = resourceService.read(RESOURCE_NAME_CSV);
        assertNotNull(resourceTO);
        resourceTO.setName("resource-csv-enforcing");
        resourceTO.setEnforceMandatoryCondition(true);

        Response response = resourceService.create(resourceTO);
        resourceTO = adminClient.getObject(response.getLocation(), ResourceService.class, ResourceTO.class);
        assertNotNull(resourceTO);

        UserTO userTO = getUniqueSampleTO("syncope222@apache.org");
        userTO.getResources().add(resourceTO.getName());
        userTO.setPassword("newPassword");

        try {
            userTO = createUser(userTO);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.RequiredValuesMissing, e.getType());
        }

        userTO.getDerAttrs().add(attributeTO("csvuserid", null));

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertEquals(Collections.singleton("resource-csv-enforcing"), userTO.getResources());
    }

    @Test
    public void createUserWithDbPropagation() {
        UserTO userTO = getUniqueSampleTO("yyy@yyy.yyy");
        userTO.getResources().add(RESOURCE_NAME_TESTDB);
        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getPropagationStatusTOs().size());
        assertTrue(userTO.getPropagationStatusTOs().get(0).getStatus().isSuccessful());
    }

    @Test(expected = SyncopeClientException.class)
    public void createWithInvalidPassword() {
        UserTO userTO = getSampleTO("invalidpasswd@syncope.apache.org");
        userTO.setPassword("pass");
        createUser(userTO);
    }

    @Test(expected = SyncopeClientException.class)
    public void createWithInvalidUsername() {
        UserTO userTO = getSampleTO("invalidusername@syncope.apache.org");
        userTO.setUsername("us");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(7L);

        userTO.getMemberships().add(membershipTO);

        createUser(userTO);
    }

    @Test(expected = SyncopeClientException.class)
    public void createWithInvalidPasswordByRes() {
        UserTO userTO = getSampleTO("invalidPwdByRes@passwd.com");

        // configured to be minLength=16
        userTO.setPassword("password1");

        userTO.getResources().add("ws-target-resource-nopropagation");

        createUser(userTO);
    }

    @Test(expected = SyncopeClientException.class)
    public void createWithInvalidPasswordByRole() {
        UserTO userTO = getSampleTO("invalidPwdByRole@passwd.com");

        // configured to be minLength=16
        userTO.setPassword("password1");

        final MembershipTO membership = new MembershipTO();
        membership.setRoleId(8L);

        userTO.getMemberships().add(membership);

        createUser(userTO);
    }

    @Test(expected = SyncopeClientException.class)
    public void createWithException() {
        UserTO newUserTO = new UserTO();
        newUserTO.getAttrs().add(attributeTO("userId", "userId@nowhere.org"));
        createUser(newUserTO);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void create() {
        // get task list
        List<PropagationTaskTO> tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        long maxId = getMaxTaskId(tasks);
        PropagationTaskTO taskTO = taskService.read(maxId);

        assertNotNull(taskTO);
        int maxTaskExecutions = taskTO.getExecutions().size();

        UserTO userTO = getUniqueSampleTO("a.b@c.com");

        // add a membership
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.getMemberships().add(membershipTO);

        // add an attribute with no values: must be ignored
        membershipTO.getAttrs().add(attributeTO("subscriptionDate", null));

        // add an attribute with a non-existing schema: must be ignored
        AttributeTO attrWithInvalidSchemaTO = attributeTO("invalid schema", "a value");
        userTO.getAttrs().add(attrWithInvalidSchemaTO);

        // add an attribute with null value: must be ignored
        userTO.getAttrs().add(attributeTO("activationDate", null));

        // 1. create user
        UserTO newUserTO = createUser(userTO);

        assertNotNull(newUserTO);

        // issue SYNCOPE-15
        assertNotNull(newUserTO.getCreationDate());
        assertNotNull(newUserTO.getCreator());
        assertNotNull(newUserTO.getLastChangeDate());
        assertNotNull(newUserTO.getLastModifier());
        assertEquals(newUserTO.getCreationDate(), newUserTO.getLastChangeDate());

        assertFalse(newUserTO.getAttrs().contains(attrWithInvalidSchemaTO));

        // check for changePwdDate
        assertNotNull(newUserTO.getCreationDate());

        // 2. check for virtual attribute value
        newUserTO = userService.read(newUserTO.getId());
        assertNotNull(newUserTO);

        assertNotNull(newUserTO.getVirAttrMap());
        assertNotNull(newUserTO.getVirAttrMap().get("virtualdata").getValues());
        assertFalse(newUserTO.getVirAttrMap().get("virtualdata").getValues().isEmpty());
        assertEquals("virtualvalue", newUserTO.getVirAttrMap().get("virtualdata").getValues().get(0));

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        long newMaxId = getMaxTaskId(tasks);

        // default configuration for ws-target-resource2:
        // only failed executions have to be registered
        // --> no more tasks/executions should be added
        assertEquals(newMaxId, maxId);

        // get last task
        taskTO = taskService.read(newMaxId);

        assertNotNull(taskTO);
        assertEquals(maxTaskExecutions, taskTO.getExecutions().size());

        // 3. verify password
        UserService userService1 = clientFactory.create(
                newUserTO.getUsername(), "password123").getService(UserService.class);
        try {
            UserTO user = userService1.readSelf();
            assertNotNull(user);
        } catch (AccessControlException e) {
            fail("Credentials should be valid and not cause AccessControlException");
        }

        UserService userService2 = clientFactory.create(
                newUserTO.getUsername(), "passwordXX").getService(UserService.class);
        try {
            userService2.readSelf();
            fail("Credentials are invalid, thus request should raise AccessControlException");
        } catch (AccessControlException e) {
            assertNotNull(e);
        }

        // 4. try (and fail) to create another user with same (unique) values
        userTO = getSampleTO(userTO.getUsername());
        AttributeTO userIdAttr = getManadatoryAttrByName(userTO.getAttrs(), "userId");
        userIdAttr.getValues().clear();
        userIdAttr.getValues().add("a.b@c.com");

        try {
            createUser(userTO);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.DataIntegrityViolation, e.getType());
        }
    }

    private AttributeTO getManadatoryAttrByName(List<AttributeTO> attributes, String attrName) {
        for (AttributeTO attr : attributes) {
            if (attrName.equals(attr.getSchema())) {
                return attr;
            }
        }
        throw new NotFoundException("Mandatory attribute " + attrName + " not found");
    }

    @Test
    public void createWithRequiredValueMissing() {
        UserTO userTO = getSampleTO("a.b@c.it");

        AttributeTO type = getManadatoryAttrByName(userTO.getAttrs(), "type");
        userTO.getAttrs().remove(type);

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.getMemberships().add(membershipTO);

        // 1. create user without type (mandatory by UserSchema)
        try {
            createUser(userTO);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.RequiredValuesMissing, e.getType());
        }

        userTO.getAttrs().add(attributeTO("type", "F"));

        AttributeTO surname = getManadatoryAttrByName(userTO.getAttrs(), "surname");
        userTO.getAttrs().remove(surname);

        // 2. create user without surname (mandatory when type == 'F')
        try {
            createUser(userTO);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.RequiredValuesMissing, e.getType());
        }
    }

    @Test
    public void createWithReject() {
        Assume.assumeTrue(ActivitiDetector.isActivitiEnabledForUsers());

        UserTO userTO = getUniqueSampleTO("createWithReject@syncope.apache.org");
        userTO.getResources().add(RESOURCE_NAME_TESTDB);

        // User with role 9 are defined in workflow as subject to approval
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(9L);
        userTO.getMemberships().add(membershipTO);

        // 1. create user with role 9
        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getMemberships().size());
        assertEquals(9, userTO.getMemberships().get(0).getRoleId());
        assertEquals("createApproval", userTO.getStatus());

        // 2. request if there is any pending task for user just created
        WorkflowFormTO form = userWorkflowService.getFormForUser(userTO.getId());
        assertNotNull(form);
        assertNotNull(form.getUserId());
        assertEquals(userTO.getId(), form.getUserId());
        assertNotNull(form.getTaskId());
        assertNull(form.getOwner());

        // 3. claim task from rossini, not in role 7 (designated for approval in workflow definition): fail
        UserWorkflowService userService2 = clientFactory.create(
                "rossini", ADMIN_PWD).getService(UserWorkflowService.class);

        try {
            userService2.claimForm(form.getTaskId());
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.Workflow, e.getType());
        }

        // 4. claim task from bellini, in role 7
        UserWorkflowService userService3 = clientFactory.create(
                "bellini", ADMIN_PWD).getService(UserWorkflowService.class);

        form = userService3.claimForm(form.getTaskId());
        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNotNull(form.getOwner());

        // 5. reject user
        Map<String, WorkflowFormPropertyTO> props = form.getPropertyMap();
        props.get("approve").setValue(Boolean.FALSE.toString());
        props.get("rejectReason").setValue("I don't like him.");
        form.setProperties(props.values());
        userTO = userService3.submitForm(form);
        assertNotNull(userTO);
        assertEquals("rejected", userTO.getStatus());

        // 6. check that rejected user was not propagated to external resource (SYNCOPE-364)
        JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);
        Exception exception = null;
        try {
            jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?",
                    new String[] { userTO.getUsername() }, Integer.class);
        } catch (EmptyResultDataAccessException e) {
            exception = e;
        }
        assertNotNull(exception);
    }

    @Test
    public void createWithApproval() {
        Assume.assumeTrue(ActivitiDetector.isActivitiEnabledForUsers());

        UserTO userTO = getUniqueSampleTO("createWithApproval@syncope.apache.org");
        userTO.getResources().add(RESOURCE_NAME_TESTDB);

        // User with role 9 are defined in workflow as subject to approval
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(9L);
        userTO.getMemberships().add(membershipTO);

        // 1. create user with role 9 (and verify that no propagation occurred)
        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getMemberships().size());
        assertEquals(9, userTO.getMemberships().get(0).getRoleId());
        assertEquals("createApproval", userTO.getStatus());
        assertEquals(Collections.singleton(RESOURCE_NAME_TESTDB), userTO.getResources());

        assertTrue(userTO.getPropagationStatusTOs().isEmpty());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

        Exception exception = null;
        try {
            jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?",
                    new String[] { userTO.getUsername() }, Integer.class);
        } catch (EmptyResultDataAccessException e) {
            exception = e;
        }
        assertNotNull(exception);

        // 2. request if there is any pending form for user just created
        List<WorkflowFormTO> forms = userWorkflowService.getForms();
        assertNotNull(forms);
        assertEquals(1, forms.size());

        WorkflowFormTO form = userWorkflowService.getFormForUser(userTO.getId());
        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNull(form.getOwner());

        // 4. claim task (from admin)
        form = userWorkflowService.claimForm(form.getTaskId());
        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNotNull(form.getOwner());

        // 5. approve user (and verify that propagation occurred)
        Map<String, WorkflowFormPropertyTO> props = form.getPropertyMap();
        props.get("approve").setValue(Boolean.TRUE.toString());
        form.setProperties(props.values());
        userTO = userWorkflowService.submitForm(form);
        assertNotNull(userTO);
        assertEquals("active", userTO.getStatus());
        assertEquals(Collections.singleton(RESOURCE_NAME_TESTDB), userTO.getResources());

        exception = null;
        try {
            final String username = jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?", String.class,
                    userTO.getUsername());
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
            userService.delete(0L);
        } catch (SyncopeClientException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getType().getResponseStatus().getStatusCode());
        }

        UserTO userTO = getSampleTO("qqgf.z@nn.com");

        // specify a propagation
        userTO.getResources().add(RESOURCE_NAME_TESTDB);

        userTO = createUser(userTO);

        long id = userTO.getId();

        userTO = userService.delete(id);

        assertNotNull(userTO);
        assertEquals(id, userTO.getId());
        assertTrue(userTO.getAttrs().isEmpty());

        // check for propagation result
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertTrue(userTO.getPropagationStatusTOs().get(0).getStatus().isSuccessful());

        try {
            userService.delete(userTO.getId());
        } catch (SyncopeClientException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getType().getResponseStatus().getStatusCode());
        }
    }

    @Test
    public void deleteByUsername() {
        UserTO userTO = getSampleTO("delete.by.username@apache.org");

        // specify a propagation
        userTO.getResources().add(RESOURCE_NAME_TESTDB);

        userTO = createUser(userTO);

        long id = userTO.getId();
        userTO = userService.read(id);
        userTO = userService.delete(userTO.getId());

        assertNotNull(userTO);
        assertEquals(id, userTO.getId());
        assertTrue(userTO.getAttrs().isEmpty());

        // check for propagation result
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertTrue(userTO.getPropagationStatusTOs().get(0).getStatus().isSuccessful());

        try {
            userService.read(userTO.getId());
        } catch (SyncopeClientException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getType().getResponseStatus().getStatusCode());
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
        UserTO userTO = userService.read(1L);

        assertNotNull(userTO);
        assertNotNull(userTO.getAttrs());
        assertFalse(userTO.getAttrs().isEmpty());
    }

    @Test
    public void readWithMailAddressAsUserName() {
        UserTO userTO = createUser(getUniqueSampleTO("mail@domain.org"));
        userTO = userService.read(userTO.getUsername());
        assertNotNull(userTO);
    }

    @Test
    public void updateWithouPassword() {
        UserTO userTO = getUniqueSampleTO("updatewithout@password.com");

        userTO = createUser(userTO);

        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.getDerAttrsToRemove().add("cn");

        userTO = userService.update(userMod.getId(), userMod);

        assertNotNull(userTO);
        assertNotNull(userTO.getDerAttrMap());
        assertFalse(userTO.getDerAttrMap().containsKey("cn"));
    }

    @Test(expected = SyncopeClientException.class)
    public void updateInvalidPassword() {
        UserTO userTO = getSampleTO("updateinvalid@password.com");

        userTO = createUser(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("pass");

        userService.update(userMod.getId(), userMod);
    }

    @Test(expected = SyncopeClientException.class)
    public void updateSamePassword() {
        UserTO userTO = getSampleTO("updatesame@password.com");

        userTO = createUser(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("password123");

        userService.update(userMod.getId(), userMod);
    }

    @Test
    public void update() {
        UserTO userTO = getUniqueSampleTO("g.h@t.com");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        membershipTO.getAttrs().add(attributeTO("subscriptionDate", "2009-08-18T16:33:12.203+0200"));
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);

        assertFalse(userTO.getDerAttrs().isEmpty());
        assertEquals(1, userTO.getMemberships().size());

        MembershipMod membershipMod = new MembershipMod();
        membershipMod.setRole(8L);
        membershipMod.getAttrsToUpdate().add(attributeMod("subscriptionDate", "2010-08-18T16:33:12.203+0200"));

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("new2Password");

        userMod.getAttrsToRemove().add("userId");
        String newUserId = getUUIDString() + "t.w@spre.net";
        userMod.getAttrsToUpdate().add(attributeMod("userId", newUserId));

        userMod.getAttrsToRemove().add("fullname");
        String newFullName = getUUIDString() + "g.h@t.com";
        userMod.getAttrsToUpdate().add(attributeMod("fullname", newFullName));

        userMod.getDerAttrsToAdd().add("cn");
        userMod.getMembershipsToAdd().add(membershipMod);
        userMod.getMembershipsToRemove().add(userTO.getMemberships().iterator().next().getId());

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        // issue SYNCOPE-15
        assertNotNull(userTO.getCreationDate());
        assertNotNull(userTO.getCreator());
        assertNotNull(userTO.getLastChangeDate());
        assertNotNull(userTO.getLastModifier());
        assertTrue(userTO.getCreationDate().before(userTO.getLastChangeDate()));

        SyncopeUser passwordTestUser = new SyncopeUser();
        passwordTestUser.setPassword("new2Password", CipherAlgorithm.SHA1, 0);
        assertEquals(passwordTestUser.getPassword(), userTO.getPassword());

        assertEquals(1, userTO.getMemberships().size());
        assertEquals(1, userTO.getMemberships().iterator().next().getAttrs().size());
        assertFalse(userTO.getDerAttrs().isEmpty());

        AttributeTO userIdAttr = getManadatoryAttrByName(userTO.getAttrs(), "userId");
        assertEquals(Collections.singletonList(newUserId), userIdAttr.getValues());

        AttributeTO fullNameAttr = getManadatoryAttrByName(userTO.getAttrs(), "fullname");
        assertEquals(Collections.singletonList(newFullName), fullNameAttr.getValues());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updatePasswordOnly() {
        List<PropagationTaskTO> beforeTasks = taskService.list(TaskType.PROPAGATION);
        assertNotNull(beforeTasks);
        assertFalse(beforeTasks.isEmpty());

        UserTO userTO = getUniqueSampleTO("pwdonly@t.com");
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        membershipTO.getAttrs().add(attributeTO("subscriptionDate", "2009-08-18T16:33:12.203+0200"));
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);

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

    @SuppressWarnings("unchecked")
    @Test
    public void verifyTaskRegistration() {
        // get task list
        List<PropagationTaskTO> tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        long maxId = getMaxTaskId(tasks);

        // --------------------------------------
        // Create operation
        // --------------------------------------
        UserTO userTO = getUniqueSampleTO("t@p.mode");

        // add a membership
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.getMemberships().add(membershipTO);

        // 1. create user
        userTO = createUser(userTO);
        assertNotNull(userTO);

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        long newMaxId = getMaxTaskId(tasks);

        // default configuration for ws-target-resource2:
        // only failed executions have to be registered
        // --> no more tasks/executions should be added
        assertEquals(newMaxId, maxId);

        // --------------------------------------
        // Update operation
        // --------------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        userMod.getAttrsToUpdate().add(attributeMod("surname", "surname"));

        userTO = userService.update(userMod.getId(), userMod);

        assertNotNull(userTO);

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        maxId = newMaxId;
        newMaxId = getMaxTaskId(tasks);

        // default configuration for ws-target-resource2:
        // all update executions have to be registered
        assertTrue(newMaxId > maxId);

        final PropagationTaskTO taskTO = taskService.read(newMaxId);

        assertNotNull(taskTO);
        assertEquals(1, taskTO.getExecutions().size());

        // --------------------------------------
        // Delete operation
        // --------------------------------------
        userService.delete(userTO.getId());

        // get the new task list
        tasks = taskService.list(TaskType.PROPAGATION);

        maxId = newMaxId;
        newMaxId = getMaxTaskId(tasks);

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
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);

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
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);

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
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);

        assertNotNull(userTO);
        assertEquals(ActivitiDetector.isActivitiEnabledForUsers()
                ? "active"
                : "created", userTO.getStatus());

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
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);

        assertNotNull(userTO);
        assertEquals(ActivitiDetector.isActivitiEnabledForUsers()
                ? "active"
                : "created", userTO.getStatus());

        userTO = userService.suspendByUsername(userTO.getUsername());

        assertNotNull(userTO);
        assertEquals("suspended", userTO.getStatus());

        userTO = userService.reactivateByUsername(userTO.getUsername());

        assertNotNull(userTO);
        assertEquals("active", userTO.getStatus());
    }

    @Test
    public void suspendReactivateOnResource() {
        // Assert resources are present
        ResourceTO dbTable = resourceService.read(RESOURCE_NAME_TESTDB);
        assertNotNull(dbTable);
        ResourceTO ldap = resourceService.read(RESOURCE_NAME_LDAP);
        assertNotNull(ldap);

        // Create user with reference to resources
        UserTO userTO = getUniqueSampleTO("suspreactonresource@syncope.apache.org");
        userTO.getMemberships().clear();
        userTO.getResources().clear();
        userTO.getResources().add(RESOURCE_NAME_TESTDB);
        userTO.getResources().add(RESOURCE_NAME_LDAP);
        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertEquals(ActivitiDetector.isActivitiEnabledForUsers()
                ? "active"
                : "created", userTO.getStatus());
        long userId = userTO.getId();

        // Suspend with effect on syncope, ldap and db => user should be suspended in syncope and all resources
        PropagationRequestTO propagationRequestTO = new PropagationRequestTO();
        propagationRequestTO.setOnSyncope(true);
        propagationRequestTO.getResources().add(RESOURCE_NAME_TESTDB);
        propagationRequestTO.getResources().add(RESOURCE_NAME_LDAP);
        userTO = userService.suspend(userId, propagationRequestTO);
        assertNotNull(userTO);
        assertEquals("suspended", userTO.getStatus());

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_TESTDB, userId);
        assertFalse(getBooleanAttribute(connObjectTO, OperationalAttributes.ENABLE_NAME));

        connObjectTO = readConnectorObject(RESOURCE_NAME_LDAP, userId);
        assertNotNull(connObjectTO);

        // Suspend and reactivate only on ldap => db and syncope should still show suspended
        propagationRequestTO = new PropagationRequestTO();
        propagationRequestTO.setOnSyncope(false);
        propagationRequestTO.getResources().add(RESOURCE_NAME_LDAP);
        userTO = userService.suspend(userId, propagationRequestTO);
        userTO = userService.reactivate(userId, propagationRequestTO);
        assertNotNull(userTO);
        assertEquals("suspended", userTO.getStatus());

        connObjectTO = readConnectorObject(RESOURCE_NAME_TESTDB, userId);
        assertFalse(getBooleanAttribute(connObjectTO, OperationalAttributes.ENABLE_NAME));

        // Reactivate on syncope and db => syncope and db should show the user as active
        propagationRequestTO = new PropagationRequestTO();
        propagationRequestTO.setOnSyncope(true);
        propagationRequestTO.getResources().add(RESOURCE_NAME_TESTDB);

        userTO = userService.reactivate(userId, propagationRequestTO);
        assertNotNull(userTO);
        assertEquals("active", userTO.getStatus());

        connObjectTO = readConnectorObject(RESOURCE_NAME_TESTDB, userId);
        assertTrue(getBooleanAttribute(connObjectTO, OperationalAttributes.ENABLE_NAME));
    }

    public void updateMultivalueAttribute() {
        UserTO userTO = getSampleTO("multivalue@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();

        userTO = createUser(userTO);
        assertNotNull(userTO);

        AttributeTO loginDate = userTO.getAttrMap().get("loginDate");
        assertNotNull(loginDate);
        assertEquals(1, loginDate.getValues().size());

        UserMod userMod = new UserMod();

        AttributeMod loginDateMod = new AttributeMod();
        loginDateMod.getValuesToBeAdded().add("2000-01-01");

        userMod.setId(userTO.getId());
        userMod.getAttrsToUpdate().add(loginDateMod);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        loginDate = userTO.getAttrMap().get("loginDate");
        assertNotNull(loginDate);
        assertEquals(2, loginDate.getValues().size());
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void issue213() {
        UserTO userTO = getUniqueSampleTO("issue213@syncope.apache.org");
        userTO.getResources().add(RESOURCE_NAME_TESTDB);

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertEquals(1, userTO.getResources().size());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

        String username = jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?", String.class,
                userTO.getUsername());

        assertEquals(userTO.getUsername(), username);

        UserMod userMod = new UserMod();

        userMod.setId(userTO.getId());
        userMod.getResourcesToRemove().add(RESOURCE_NAME_TESTDB);

        userTO = userService.update(userMod.getId(), userMod);

        assertTrue(userTO.getResources().isEmpty());

        jdbcTemplate.queryForObject("SELECT id FROM test WHERE id=?", String.class, userTO.getUsername());
    }

    @Test
    public void issue234() {
        UserTO inUserTO = getUniqueSampleTO("issue234@syncope.apache.org");
        inUserTO.getResources().add(RESOURCE_NAME_LDAP);

        UserTO userTO = createUser(inUserTO);
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
        original.getVirAttrs().clear();

        original = createUser(original);

        assertNotNull(original);

        assertTrue(original.getVirAttrs().isEmpty());

        UserTO toBeUpdated = userService.read(original.getId());

        AttributeTO virtual = attributeTO("virtualdata", "virtualvalue");
        toBeUpdated.getVirAttrs().add(virtual);

        // 2. try to update by adding a resource, but no password: must fail
        UserMod userMod = AttributableOperations.diff(toBeUpdated, original);

        assertNotNull(userMod);

        toBeUpdated = userService.update(userMod.getId(), userMod);

        assertNotNull(toBeUpdated);

        assertFalse(toBeUpdated.getVirAttrs().isEmpty());
        assertNotNull(toBeUpdated.getVirAttrs().get(0));

        assertEquals(virtual.getSchema(), toBeUpdated.getVirAttrs().get(0).getSchema());
    }

    @Test
    public final void issue280() {
        UserTO userTO = getUniqueSampleTO("issue280@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();

        userTO = createUser(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("123password");
        userMod.getResourcesToAdd().add(RESOURCE_NAME_TESTDB);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        final List<PropagationStatusTO> propagations = userTO.getPropagationStatusTOs();

        assertNotNull(propagations);
        assertEquals(1, propagations.size());

        final PropagationTaskExecStatus status = propagations.get(0).getStatus();
        final String resource = propagations.get(0).getResource();

        assertNotNull(status);
        assertEquals(RESOURCE_NAME_TESTDB, resource);
        assertTrue(status.isSuccessful());
    }

    @Test
    public void issue281() {
        UserTO userTO = getUniqueSampleTO("issue281@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getResources().add(RESOURCE_NAME_CSV);

        userTO = createUser(userTO);
        assertNotNull(userTO);

        final List<PropagationStatusTO> propagations = userTO.getPropagationStatusTOs();

        assertNotNull(propagations);
        assertEquals(1, propagations.size());

        final PropagationTaskExecStatus status = propagations.get(0).getStatus();
        final String resource = propagations.get(0).getResource();

        assertNotNull(status);
        assertEquals(RESOURCE_NAME_CSV, resource);
        assertFalse(status.isSuccessful());
    }

    @Test
    public void issue288() {
        UserTO userTO = getSampleTO("issue288@syncope.apache.org");
        userTO.getAttrs().add(attributeTO("aLong", "STRING"));

        try {
            createUser(userTO);
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.InvalidValues, e.getType());
        }
    }

    @Test
    public void roleAttrPropagation() {
        UserTO userTO = getUniqueSampleTO("checkRoleAttrPropagation@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();

        userTO.getDerAttrs().add(attributeTO("csvuserid", null));

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(1L);

        userTO.getMemberships().add(membershipTO);

        userTO.getResources().add(RESOURCE_NAME_CSV);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);
        assertNotNull(actual.getDerAttrMap().get("csvuserid"));

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);
        assertEquals("sx-dx", connObjectTO.getAttrMap().get("ROLE").getValues().get(0));
    }

    @Test
    public void membershipAttrPropagation() {
        UserTO userTO = getUniqueSampleTO("checkMembAttrPropagation@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();
        userTO.getDerAttrs().add(attributeTO("csvuserid", null));

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(1L);
        membershipTO.getAttrs().add(attributeTO("mderived_sx", "sx"));
        membershipTO.getAttrs().add(attributeTO("mderived_dx", "dx"));
        membershipTO.getDerAttrs().add(attributeTO("mderToBePropagated", null));
        userTO.getMemberships().add(membershipTO);

        userTO.getResources().add(RESOURCE_NAME_CSV);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);
        assertNotNull(actual.getDerAttrMap().get("csvuserid"));

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);
        assertEquals("sx-dx", connObjectTO.getAttrMap().get("MEMBERSHIP").getValues().get(0));
    }

    @Test
    public void issueSYNCOPE16() {
        UserTO userTO = getUniqueSampleTO("issue16@apache.org");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.getMemberships().add(membershipTO);

        // 1. create user
        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        // 2. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertEquals("virtualvalue", actual.getVirAttrMap().get("virtualdata").getValues().get(0));

        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());
        userMod.getVirAttrsToRemove().add("virtualdata");
        userMod.getVirAttrsToUpdate().add(attributeMod("virtualdata", "virtualupdated"));

        // 3. update virtual attribute
        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);

        // 4. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertEquals("virtualupdated", actual.getVirAttrMap().get("virtualdata").getValues().get(0));
    }

    @Test
    public void issueSYNCOPE108() {
        UserTO userTO = getUniqueSampleTO("syncope108@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();
        userTO.getDerAttrs().add(attributeTO("csvuserid", null));

        MembershipTO memb12 = new MembershipTO();
        memb12.setRoleId(12L);

        userTO.getMemberships().add(memb12);

        MembershipTO memb13 = new MembershipTO();
        memb13.setRoleId(13L);

        userTO.getMemberships().add(memb13);

        userTO.getResources().add(RESOURCE_NAME_CSV);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);
        assertEquals(2, actual.getMemberships().size());
        assertEquals(1, actual.getResources().size());

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);

        // -----------------------------------
        // Remove the first membership: de-provisioning shouldn't happen
        // -----------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());

        userMod.getMembershipsToRemove().add(actual.getMemberships().get(0).getId());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertEquals(1, actual.getMemberships().size());

        connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);
        // -----------------------------------

        // -----------------------------------
        // Remove the resource assigned directly: de-provisioning shouldn't happen
        // -----------------------------------
        userMod = new UserMod();
        userMod.setId(actual.getId());

        userMod.getResourcesToRemove().add(actual.getResources().iterator().next());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertEquals(1, actual.getMemberships().size());
        assertFalse(actual.getResources().isEmpty());

        connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);
        // -----------------------------------

        // -----------------------------------
        // Remove the first membership: de-provisioning should happen
        // -----------------------------------
        userMod = new UserMod();
        userMod.setId(actual.getId());

        userMod.getMembershipsToRemove().add(actual.getMemberships().get(0).getId());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertTrue(actual.getMemberships().isEmpty());
        assertTrue(actual.getResources().isEmpty());

        try {
            readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
            fail("Read should not succeeed");
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.NotFound, e.getType());
        }
    }

    @Test
    public void issueSYNCOPE111() {
        UserTO userTO = getUniqueSampleTO("syncope111@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();
        userTO.getDerAttrs().add(attributeTO("csvuserid", null));

        MembershipTO memb12 = new MembershipTO();
        memb12.setRoleId(12L);
        memb12.getAttrs().add(attributeTO("postalAddress", "postalAddress"));
        userTO.getMemberships().add(memb12);

        MembershipTO memb13 = new MembershipTO();
        memb13.setRoleId(13L);
        userTO.getMemberships().add(memb13);

        userTO.getResources().add(RESOURCE_NAME_LDAP);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);
        assertEquals(2, actual.getMemberships().size());

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_LDAP, actual.getId());
        assertNotNull(connObjectTO);

        AttributeTO postalAddress = connObjectTO.getAttrMap().get("postalAddress");
        assertNotNull(postalAddress);
        assertEquals(1, postalAddress.getValues().size());
        assertEquals("postalAddress", postalAddress.getValues().get(0));

        AttributeTO title = connObjectTO.getAttrMap().get("title");
        assertNotNull(title);
        assertEquals(2, title.getValues().size());
        assertTrue(title.getValues().contains("r12") && title.getValues().contains("r13"));

        // -----------------------------------
        // Remove the first membership and check for membership attr propagation and role attr propagation
        // -----------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());

        MembershipTO membershipTO = actual.getMemberships().get(0).getRoleId() == 12L
                ? actual.getMemberships().get(0)
                : actual.getMemberships().get(1);

        userMod.getMembershipsToRemove().add(membershipTO.getId());

        actual = userService.update(userMod.getId(), userMod);
        assertNotNull(actual);
        assertEquals(1, actual.getMemberships().size());

        connObjectTO = readConnectorObject(RESOURCE_NAME_LDAP, actual.getId());
        assertNotNull(connObjectTO);

        postalAddress = connObjectTO.getAttrMap().get("postalAddress");
        assertTrue(postalAddress == null || postalAddress.getValues().isEmpty()
                || StringUtils.hasText(postalAddress.getValues().get(0)));

        title = connObjectTO.getAttrMap().get("title");
        assertNotNull(title);
        assertEquals(1, title.getValues().size());
        assertTrue(title.getValues().contains("r13"));
        // -----------------------------------
    }

    @Test
    public void issueSYNCOPE185() {
        // 1. create user with LDAP resource, succesfully propagated
        UserTO userTO = getSampleTO("syncope185@syncope.apache.org");
        userTO.getVirAttrs().clear();
        userTO.getResources().add(RESOURCE_NAME_LDAP);

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals(RESOURCE_NAME_LDAP, userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUCCESS, userTO.getPropagationStatusTOs().get(0).getStatus());

        // 2. delete this user
        userService.delete(userTO.getId());

        // 3. try (and fail) to find this user on the external LDAP resource
        try {
            readConnectorObject(RESOURCE_NAME_LDAP, userTO.getId());
            fail("This entry should not be present on this resource");
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.NotFound, e.getType());
        }
    }

    @Test()
    public void issueSYNCOPE51() {
        ConfigurationTO defaultConfigurationTO = configurationService.read("password.cipher.algorithm");

        ConfigurationTO configurationTO = new ConfigurationTO();
        configurationTO.setKey("password.cipher.algorithm");
        configurationTO.setValue("MD5");

        configurationService.update(configurationTO.getKey(), configurationTO);
        ConfigurationTO newConfTO = configurationService.read(configurationTO.getKey());

        assertEquals(configurationTO, newConfTO);

        UserTO userTO = getSampleTO("syncope51@syncope.apache.org");
        userTO.setPassword("password");

        try {
            createUser(userTO);
            fail("Create user should not succeed");
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.NotFound, e.getType());
            assertTrue(e.getElements().iterator().next().toString().contains("MD5"));
        }

        configurationService.update(defaultConfigurationTO.getKey(), defaultConfigurationTO);
        ConfigurationTO oldConfTO = configurationService.read(defaultConfigurationTO.getKey());

        assertEquals(defaultConfigurationTO, oldConfTO);
    }

    @Test
    public void issueSYNCOPE260() {
        // ----------------------------------
        // create user and check virtual attribute value propagation
        // ----------------------------------
        UserTO userTO = getUniqueSampleTO("260@a.com");
        userTO.getResources().add("ws-target-resource-2");

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        ConnObjectTO connObjectTO = readConnectorObject("ws-target-resource-2", userTO.getId());
        assertNotNull(connObjectTO);
        assertEquals("virtualvalue", connObjectTO.getAttrMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // update user virtual attribute and check virtual attribute value update propagation
        // ----------------------------------
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        AttributeMod attrMod = new AttributeMod();
        attrMod.setSchema("virtualdata");
        attrMod.getValuesToBeRemoved().add("virtualvalue");
        attrMod.getValuesToBeAdded().add("virtualvalue2");

        userMod.getVirAttrsToUpdate().add(attrMod);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        connObjectTO = readConnectorObject("ws-target-resource-2", userTO.getId());
        assertNotNull(connObjectTO);
        assertEquals("virtualvalue2", connObjectTO.getAttrMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // suspend/reactivate user and check virtual attribute value (unchanged)
        // ----------------------------------
        userTO = userService.suspend(userTO.getId());
        assertEquals("suspended", userTO.getStatus());

        connObjectTO = readConnectorObject("ws-target-resource-2", userTO.getId());
        assertNotNull(connObjectTO);
        assertFalse(connObjectTO.getAttrMap().get("NAME").getValues().isEmpty());
        assertEquals("virtualvalue2", connObjectTO.getAttrMap().get("NAME").getValues().get(0));

        userTO = userService.reactivate(userTO.getId());
        assertEquals("active", userTO.getStatus());

        connObjectTO = readConnectorObject("ws-target-resource-2", userTO.getId());
        assertNotNull(connObjectTO);
        assertFalse(connObjectTO.getAttrMap().get("NAME").getValues().isEmpty());
        assertEquals("virtualvalue2", connObjectTO.getAttrMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // update user attribute and check virtual attribute value (unchanged)
        // ----------------------------------
        userMod = new UserMod();
        userMod.setId(userTO.getId());

        attrMod = new AttributeMod();
        attrMod.setSchema("surname");
        attrMod.getValuesToBeRemoved().add("Surname");
        attrMod.getValuesToBeAdded().add("Surname2");

        userMod.getAttrsToUpdate().add(attrMod);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        connObjectTO = readConnectorObject("ws-target-resource-2", userTO.getId());
        assertNotNull(connObjectTO);
        assertEquals("Surname2", connObjectTO.getAttrMap().get("SURNAME").getValues().get(0));

        // attribute "name" mapped on virtual attribute "virtualdata" shouldn't be changed
        assertFalse(connObjectTO.getAttrMap().get("NAME").getValues().isEmpty());
        assertEquals("virtualvalue2", connObjectTO.getAttrMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // remove user virtual attribute and check virtual attribute value (reset)
        // ----------------------------------
        userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.getVirAttrsToRemove().add("virtualdata");

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        assertTrue(userTO.getVirAttrs().isEmpty());
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals("ws-target-resource-2", userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        connObjectTO = readConnectorObject("ws-target-resource-2", userTO.getId());
        assertNotNull(connObjectTO);

        // attribute "name" mapped on virtual attribute "virtualdata" should be reset
        assertTrue(connObjectTO.getAttrMap().get("NAME").getValues() == null
                || connObjectTO.getAttrMap().get("NAME").getValues().isEmpty());
        // ----------------------------------
    }

    @Test
    public void issueSYNCOPE267() {
        // ----------------------------------
        // create user and check virtual attribute value propagation
        // ----------------------------------
        UserTO userTO = getUniqueSampleTO("syncope267@apache.org");
        userTO.getResources().clear();
        userTO.getResources().add("resource-db-virattr");

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals("resource-db-virattr", userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        ConnObjectTO connObjectTO = readConnectorObject("resource-db-virattr", userTO.getId());
        assertNotNull(connObjectTO);
        assertEquals("virtualvalue", connObjectTO.getAttrMap().get("USERNAME").getValues().get(0));
        // ----------------------------------

        userTO = userService.read(userTO.getId());

        assertNotNull(userTO);
        assertEquals(1, userTO.getVirAttrs().size());
        assertEquals("virtualvalue", userTO.getVirAttrs().get(0).getValues().get(0));
    }

    @Test
    public void issueSYNCOPE266() {
        UserTO userTO = getUniqueSampleTO("syncope266@apache.org");
        userTO.getResources().clear();

        userTO = createUser(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        // this resource has not a mapping for Password
        userMod.getResourcesToAdd().add("ws-target-resource-update");

        userTO = userService.update(userTO.getId(), userMod);
        assertNotNull(userTO);
    }

    @Test
    public void issueSYNCOPE279() {
        UserTO userTO = getUniqueSampleTO("syncope279@apache.org");
        userTO.getResources().clear();
        userTO.getResources().add("ws-target-resource-timeout");
        userTO = createUser(userTO);
        assertEquals("ws-target-resource-timeout", userTO.getPropagationStatusTOs().get(0).getResource());
        assertNotNull(userTO.getPropagationStatusTOs().get(0).getFailureReason());
        assertEquals(PropagationTaskExecStatus.UNSUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());
    }

    @Test
    public void issueSYNCOPE122() {
        // 1. create user on testdb and testdb2
        UserTO userTO = getUniqueSampleTO("syncope123@apache.org");
        userTO.getResources().clear();
        userTO.getResources().add(RESOURCE_NAME_TESTDB);
        userTO.getResources().add("resource-testdb2");

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertTrue(userTO.getResources().contains(RESOURCE_NAME_TESTDB));
        assertTrue(userTO.getResources().contains("resource-testdb2"));

        final String pwdOnSyncope = userTO.getPassword();

        ConnObjectTO userOnDb = resourceService.getConnectorObject(
                RESOURCE_NAME_TESTDB, AttributableType.USER, userTO.getId());
        final AttributeTO pwdOnTestDbAttr = userOnDb.getAttrMap().get(OperationalAttributes.PASSWORD_NAME);
        assertNotNull(pwdOnTestDbAttr);
        assertNotNull(pwdOnTestDbAttr.getValues());
        assertFalse(pwdOnTestDbAttr.getValues().isEmpty());
        final String pwdOnTestDb = pwdOnTestDbAttr.getValues().iterator().next();

        ConnObjectTO userOnDb2 = resourceService.getConnectorObject(
                "resource-testdb2", AttributableType.USER, userTO.getId());
        final AttributeTO pwdOnTestDb2Attr = userOnDb2.getAttrMap().get(OperationalAttributes.PASSWORD_NAME);
        assertNotNull(pwdOnTestDb2Attr);
        assertNotNull(pwdOnTestDb2Attr.getValues());
        assertFalse(pwdOnTestDb2Attr.getValues().isEmpty());
        final String pwdOnTestDb2 = pwdOnTestDb2Attr.getValues().iterator().next();

        // 2. request to change password only on testdb (no Syncope, no testdb2)
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword(getUUIDString());
        PropagationRequestTO pwdPropRequest = new PropagationRequestTO();
        pwdPropRequest.getResources().add(RESOURCE_NAME_TESTDB);
        userMod.setPwdPropRequest(pwdPropRequest);

        userTO = userService.update(userMod.getId(), userMod);

        // 3a. Chech that only a single propagation took place
        assertNotNull(userTO.getPropagationStatusTOs());
        assertEquals(1, userTO.getPropagationStatusTOs().size());
        assertEquals(RESOURCE_NAME_TESTDB, userTO.getPropagationStatusTOs().iterator().next().getResource());

        // 3b. verify that password hasn't changed on Syncope
        assertEquals(pwdOnSyncope, userTO.getPassword());

        // 3c. verify that password *has* changed on testdb
        userOnDb = resourceService.getConnectorObject(RESOURCE_NAME_TESTDB, AttributableType.USER, userTO.getId());
        final AttributeTO pwdOnTestDbAttrAfter = userOnDb.getAttrMap().get(OperationalAttributes.PASSWORD_NAME);
        assertNotNull(pwdOnTestDbAttrAfter);
        assertNotNull(pwdOnTestDbAttrAfter.getValues());
        assertFalse(pwdOnTestDbAttrAfter.getValues().isEmpty());
        assertNotEquals(pwdOnTestDb, pwdOnTestDbAttrAfter.getValues().iterator().next());

        // 3d. verify that password hasn't changed on testdb2
        userOnDb2 = resourceService.getConnectorObject("resource-testdb2", AttributableType.USER, userTO.getId());
        final AttributeTO pwdOnTestDb2AttrAfter = userOnDb2.getAttrMap().get(OperationalAttributes.PASSWORD_NAME);
        assertNotNull(pwdOnTestDb2AttrAfter);
        assertNotNull(pwdOnTestDb2AttrAfter.getValues());
        assertFalse(pwdOnTestDb2AttrAfter.getValues().isEmpty());
        assertEquals(pwdOnTestDb2, pwdOnTestDb2AttrAfter.getValues().iterator().next());
    }

    @Test
    public void isseSYNCOPE136AES() {
        // 1. read configured cipher algorithm in order to be able to restore it at the end of test
        ConfigurationTO pwdCipherAlgo = configurationService.read("password.cipher.algorithm");
        final String origpwdCipherAlgo = pwdCipherAlgo.getValue();

        // 2. set AES password cipher algorithm
        pwdCipherAlgo.setValue("AES");
        configurationService.update(pwdCipherAlgo.getKey(), pwdCipherAlgo);

        // 3. create user with no resources
        UserTO userTO = getUniqueSampleTO("syncope136_AES@apache.org");
        userTO.getResources().clear();

        userTO = userService.create(userTO).readEntity(UserTO.class);
        assertNotNull(userTO);

        // 4. update user, assign a propagation primary resource but don't provide any password
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.getResourcesToAdd().add("ws-target-resource-1");

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        // 5. verify that propagation was successful
        List<PropagationStatusTO> props = userTO.getPropagationStatusTOs();
        assertNotNull(props);
        assertEquals(1, props.size());
        PropagationStatusTO prop = props.iterator().next();
        assertNotNull(prop);
        assertEquals("ws-target-resource-1", prop.getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, prop.getStatus());

        // 6. restore initial cipher algorithm
        pwdCipherAlgo.setValue(origpwdCipherAlgo);
        configurationService.update(pwdCipherAlgo.getKey(), pwdCipherAlgo);
    }

    @Test
    public void isseSYNCOPE136Random() {
        // 1. create user with no resources
        UserTO userTO = getUniqueSampleTO("syncope136_Random@apache.org");
        userTO.getResources().clear();

        userTO = userService.create(userTO).readEntity(UserTO.class);
        assertNotNull(userTO);

        // 2. update user, assign a propagation primary resource but don't provide any password
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.getResourcesToAdd().add(RESOURCE_NAME_LDAP);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);

        // 3. verify that propagation was successful
        List<PropagationStatusTO> props = userTO.getPropagationStatusTOs();
        assertNotNull(props);
        assertEquals(1, props.size());
        PropagationStatusTO prop = props.iterator().next();
        assertNotNull(prop);
        assertEquals(RESOURCE_NAME_LDAP, prop.getResource());
        assertEquals(PropagationTaskExecStatus.SUCCESS, prop.getStatus());
    }

    @Test
    public void virAttrCache() {
        UserTO userTO = getUniqueSampleTO("virattrcache@apache.org");
        userTO.getVirAttrs().clear();

        AttributeTO virAttrTO = new AttributeTO();
        virAttrTO.setSchema("virtualdata");
        virAttrTO.getValues().add("virattrcache");
        userTO.getVirAttrs().add(virAttrTO);

        userTO.getMemberships().clear();
        userTO.getResources().clear();
        userTO.getResources().add("resource-db-virattr");

        // 1. create user
        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        // 2. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertEquals("virattrcache", actual.getVirAttrMap().get("virtualdata").getValues().get(0));

        Exception exception = null;
        try {
            final JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

            String value = jdbcTemplate.queryForObject(
                    "SELECT USERNAME FROM testsync WHERE ID=?", String.class, actual.getId());
            assertEquals("virattrcache", value);

            jdbcTemplate.update("UPDATE testsync set USERNAME='virattrcache2' WHERE ID=?", userTO.getId());

            value = jdbcTemplate.queryForObject(
                    "SELECT USERNAME FROM testsync WHERE ID=?", String.class, userTO.getId());
            assertEquals("virattrcache2", value);

        } catch (EmptyResultDataAccessException e) {
            exception = e;
        }
        assertNotNull(exception);

        // 2. check for cached attribute value
        actual = userService.read(actual.getId());
        assertEquals("virattrcache", actual.getVirAttrMap().get("virtualdata").getValues().get(0));

        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());

        AttributeMod virtualdata = new AttributeMod();
        virtualdata.setSchema("virtualdata");
        virtualdata.getValuesToBeAdded().add("virtualupdated");

        userMod.getVirAttrsToRemove().add("virtualdata");
        userMod.getVirAttrsToUpdate().add(virtualdata);

        // 3. update virtual attribute
        actual = userService.update(actual.getId(), userMod);
        assertNotNull(actual);

        // 4. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertEquals("virtualupdated", actual.getVirAttrMap().get("virtualdata").getValues().get(0));
    }

    @Test
    public void mappingPurpose() {
        UserTO userTO = getUniqueSampleTO("mpurpose@apache.org");

        AttributeTO csvuserid = new AttributeTO();
        csvuserid.setSchema("csvuserid");
        userTO.getDerAttrs().add(csvuserid);

        userTO.getResources().clear();
        userTO.getResources().add(RESOURCE_NAME_CSV);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        final ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNull(connObjectTO.getAttrMap().get("email"));
    }

    @Test
    public void issueSYNCOPE265() {
        for (long i = 1; i <= 5; i++) {
            UserMod userMod = new UserMod();
            userMod.setId(i);

            AttributeMod attributeMod = new AttributeMod();
            attributeMod.setSchema("type");
            attributeMod.getValuesToBeAdded().add("a type");

            userMod.getAttrsToRemove().add("type");
            userMod.getAttrsToUpdate().add(attributeMod);

            UserTO userTO = userService.update(i, userMod);
            assertEquals("a type", userTO.getAttrMap().get("type").getValues().get(0));
        }
    }

    @Test
    public void bulkActions() {
        final BulkAction bulkAction = new BulkAction();

        for (int i = 0; i < 10; i++) {
            UserTO userTO = getUniqueSampleTO("bulk_" + i + "@apache.org");
            bulkAction.getTargets().add(String.valueOf(createUser(userTO).getId()));
        }

        // check for a fail
        bulkAction.getTargets().add(String.valueOf(Long.MAX_VALUE));

        assertEquals(11, bulkAction.getTargets().size());

        bulkAction.setOperation(BulkAction.Type.SUSPEND);
        BulkActionRes res = userService.bulkAction(bulkAction);
        assertEquals(10, res.getResultByStatus(Status.SUCCESS).size());
        assertEquals(1, res.getResultByStatus(Status.FAILURE).size());
        assertEquals("suspended", userService.read(
                Long.parseLong(res.getResultByStatus(Status.SUCCESS).get(3).toString())).getStatus());

        bulkAction.setOperation(BulkAction.Type.REACTIVATE);
        res = userService.bulkAction(bulkAction);
        assertEquals(10, res.getResultByStatus(Status.SUCCESS).size());
        assertEquals(1, res.getResultByStatus(Status.FAILURE).size());
        assertEquals("active", userService.read(
                Long.parseLong(res.getResultByStatus(Status.SUCCESS).get(3).toString())).getStatus());

        bulkAction.setOperation(BulkAction.Type.DELETE);
        res = userService.bulkAction(bulkAction);
        assertEquals(10, res.getResultByStatus(Status.SUCCESS).size());
        assertEquals(1, res.getResultByStatus(Status.FAILURE).size());
    }

    @Test
    public void issueSYNCOPE354() {
        // change resource-ldap role mapping for including uniqueMember (need for assertions below)
        ResourceTO ldap = resourceService.read(RESOURCE_NAME_LDAP);
        for (MappingItemTO item : ldap.getRmapping().getItems()) {
            if ("description".equals(item.getExtAttrName())) {
                item.setExtAttrName("uniqueMember");
            }
        }
        resourceService.update(ldap.getName(), ldap);

        // 1. create role with LDAP resource
        RoleTO roleTO = new RoleTO();
        roleTO.setName("SYNCOPE354-" + getUUIDString());
        roleTO.setParent(8L);
        roleTO.getResources().add(RESOURCE_NAME_LDAP);

        roleTO = createRole(roleService, roleTO);
        assertNotNull(roleTO);

        // 2. create user with LDAP resource and membership of the above role
        UserTO userTO = getUniqueSampleTO("syncope354@syncope.apache.org");
        userTO.getResources().add(RESOURCE_NAME_LDAP);
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(roleTO.getId());
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);
        assertTrue(userTO.getResources().contains(RESOURCE_NAME_LDAP));

        // 3. read role on resource, check that user DN is included in uniqueMember
        ConnObjectTO connObj = resourceService.getConnectorObject(
                RESOURCE_NAME_LDAP, AttributableType.ROLE, roleTO.getId());
        assertNotNull(connObj);
        assertTrue(connObj.getAttrMap().get("uniqueMember").getValues().
                contains("uid=" + userTO.getUsername() + ",ou=people,o=isp"));

        // 4. remove membership
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.getMembershipsToRemove().add(userTO.getMemberships().iterator().next().getId());

        userTO = userService.update(userMod.getId(), userMod);
        assertTrue(userTO.getResources().contains(RESOURCE_NAME_LDAP));

        // 5. read role on resource, check that user DN was removed from uniqueMember
        connObj = resourceService.getConnectorObject(RESOURCE_NAME_LDAP, AttributableType.ROLE, roleTO.getId());
        assertNotNull(connObj);
        assertFalse(connObj.getAttrMap().get("uniqueMember").getValues().
                contains("uid=" + userTO.getUsername() + ",ou=people,o=isp"));

        // 6. restore original resource-ldap role mapping
        for (MappingItemTO item : ldap.getRmapping().getItems()) {
            if ("uniqueMember".equals(item.getExtAttrName())) {
                item.setExtAttrName("description");
            }
        }
        resourceService.update(ldap.getName(), ldap);
    }

    @Test
    public void issueSYNCOPE357() {
        // 1. create role with LDAP resource
        RoleTO roleTO = new RoleTO();
        roleTO.setName("SYNCOPE357-" + getUUIDString());
        roleTO.setParent(8L);
        roleTO.getResources().add(RESOURCE_NAME_LDAP);

        roleTO = createRole(roleService, roleTO);
        assertNotNull(roleTO);

        // 2. create user with membership of the above role
        UserTO userTO = getUniqueSampleTO("syncope357@syncope.apache.org");
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(roleTO.getId());
        userTO.getMemberships().add(membershipTO);

        userTO = createUser(userTO);
        assertTrue(userTO.getResources().contains(RESOURCE_NAME_LDAP));

        // 3. read user on resource
        ConnObjectTO connObj = resourceService.getConnectorObject(
                RESOURCE_NAME_LDAP, AttributableType.USER, userTO.getId());
        assertNotNull(connObj);

        // 4. remove role
        roleService.delete(roleTO.getId());

        // 5. try to read user on resource: fail
        try {
            resourceService.getConnectorObject(RESOURCE_NAME_LDAP, AttributableType.USER, userTO.getId());
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.NotFound, e.getType());
        }
    }

    @Test
    public void issueSYNCOPE373() {
        UserTO userTO = userService.readSelf();
        assertEquals(ADMIN_UNAME, userTO.getUsername());
    }

    @Test
    public void issueSYNCOPE383() {
        // 1. create user on testdb and testdb2
        UserTO userTO = getUniqueSampleTO("syncope383@apache.org");
        userTO.getResources().clear();
        userTO = createUser(userTO);
        assertNotNull(userTO);

        // 2. assign resource without specifying a new pwd and check propagation failure
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.getResourcesToAdd().add(RESOURCE_NAME_TESTDB);
        userTO = userService.update(userMod.getId(), userMod);
        assertEquals(RESOURCE_NAME_TESTDB, userTO.getResources().iterator().next());
        assertFalse(userTO.getPropagationStatusTOs().get(0).getStatus().isSuccessful());
        assertNotNull(userTO.getPropagationStatusTOs().get(0).getFailureReason());

        // 3. request to change password only on testdb
        userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword(getUUIDString());
        PropagationRequestTO pwdPropRequest = new PropagationRequestTO();
        pwdPropRequest.getResources().add(RESOURCE_NAME_TESTDB);
        userMod.setPwdPropRequest(pwdPropRequest);

        userTO = userService.update(userMod.getId(), userMod);
        assertEquals(RESOURCE_NAME_TESTDB, userTO.getResources().iterator().next());
        assertTrue(userTO.getPropagationStatusTOs().get(0).getStatus().isSuccessful());
    }

    @Test
    public void issueSYNCOPE397() {
        ResourceTO csv = resourceService.read(RESOURCE_NAME_CSV);
        // change mapping of resource-csv
        MappingTO mappingTO = csv.getUmapping();

        for (MappingItemTO item : csv.getUmapping().getItems()) {
            if ("email".equals(item.getIntAttrName())) {
                // unset internal attribute mail and set virtual attribute virtualdata as mapped to external email
                item.setIntMappingType(IntMappingType.UserVirtualSchema);
                item.setIntAttrName("virtualdata");
                item.setPurpose(MappingPurpose.BOTH);
                item.setExtAttrName("email");
            }
        }

        resourceService.update(csv.getName(), csv);
        csv = resourceService.read(RESOURCE_NAME_CSV);
        assertNotNull(csv.getUmapping());

        boolean found = false;
        for (MappingItemTO item : csv.getUmapping().getItems()) {
            if ("email".equals(item.getExtAttrName()) && "virtualdata".equals(item.getIntAttrName())) {
                found = true;
            }
        }

        assertTrue(found);

        // create a new user
        UserTO userTO = getUniqueSampleTO("syncope397@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();

        userTO.getDerAttrs().add(attributeTO("csvuserid", null));
        userTO.getDerAttrs().add(attributeTO("cn", null));
        userTO.getVirAttrs().add(attributeTO("virtualdata", "test@testone.org"));
        // assign resource-csv to user
        userTO.getResources().add(RESOURCE_NAME_CSV);
        // save user
        UserTO created = createUser(userTO);
        // make std controls about user
        assertNotNull(created);
        assertTrue(RESOURCE_NAME_CSV.equals(created.getResources().iterator().next()));
        // update user
        UserTO toBeUpdated = userService.read(created.getId());
        UserMod userMod = new UserMod();
        userMod.setId(toBeUpdated.getId());
        userMod.setPassword("password2");
        // assign new resource to user
        userMod.getResourcesToAdd().add("ws-target-resource-2");
        //modify virtual attribute
        userMod.getVirAttrsToRemove().add("virtualdata");
        userMod.getVirAttrsToUpdate().add(attributeMod("virtualdata", "test@testoneone.com"));
        // check Syncope change password

        PropagationRequestTO pwdPropRequest = new PropagationRequestTO();
        //change pwd on Syncope
        pwdPropRequest.getResources().add("ws-target-resource-2");
        //change pwd on Syncope
        pwdPropRequest.setOnSyncope(true);
        userMod.setPwdPropRequest(pwdPropRequest);
        toBeUpdated = userService.update(userMod.getId(), userMod);
        assertNotNull(toBeUpdated);
        assertEquals("test@testoneone.com", toBeUpdated.getVirAttrs().get(0).getValues().get(0));
        // check if propagates correctly with assertEquals on size of tasks list

        assertEquals(2, toBeUpdated.getPropagationStatusTOs().size());
    }

    @Test
    public void issueSYNCOPE402() {
        // 1. create an user with strict mandatory attributes only
        UserTO userTO = new UserTO();
        String userId = getUUIDString() + "syncope402@syncope.apache.org";
        userTO.setUsername(userId);
        userTO.setPassword("password");

        userTO.getAttrs().add(attributeTO("userId", userId));
        userTO.getAttrs().add(attributeTO("fullname", userId));
        userTO.getAttrs().add(attributeTO("surname", userId));

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertTrue(userTO.getResources().isEmpty());

        //2. update assigning a resource NOT forcing mandatory constraints
        // AND primary: must fail with PropagationException
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.getResourcesToAdd().add("ws-target-resource-1");
        userMod.getResourcesToAdd().add("resource-testdb");
        userTO = userService.update(userMod.getId(), userMod);
        assertEquals("ws-target-resource-1", userTO.getPropagationStatusTOs().get(1).getResource());
        assertNotNull(userTO.getPropagationStatusTOs().get(1).getFailureReason());
        assertEquals(PropagationTaskExecStatus.UNSUBMITTED, userTO.getPropagationStatusTOs().get(1).getStatus());
    }

    @Test
    public void issueSYNCOPE15() {
        Assume.assumeTrue(ActivitiDetector.isActivitiEnabledForUsers());

        UserTO userTO = getUniqueSampleTO("issueSYNCOPE15@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getVirAttrs().clear();
        userTO.getDerAttrs().clear();
        userTO.getMemberships().clear();

        // User with role 9 are defined in workflow as subject to approval
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(9L);
        userTO.getMemberships().add(membershipTO);

        // 1. create user with role 9 (and verify that no propagation occurred)
        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertNotEquals(0L, userTO.getId());
        assertNotNull(userTO.getCreationDate());
        assertNotNull(userTO.getCreator());
        assertNotNull(userTO.getLastChangeDate());
        assertNotNull(userTO.getLastModifier());
        assertEquals(userTO.getCreationDate(), userTO.getLastChangeDate());

        // 2. request if there is any pending form for user just created
        List<WorkflowFormTO> forms = userWorkflowService.getForms();
        assertEquals(1, forms.size());

        WorkflowFormTO form = userWorkflowService.getFormForUser(userTO.getId());
        assertNotNull(form);

        // 3. first claim ny bellini ....
        UserWorkflowService userService3 = clientFactory.create(
                "bellini", ADMIN_PWD).getService(UserWorkflowService.class);

        form = userService3.claimForm(form.getTaskId());
        assertNotNull(form);
        assertNotNull(form.getTaskId());
        assertNotNull(form.getOwner());

        // 4. second claim task by admin
        form = userWorkflowService.claimForm(form.getTaskId());
        assertNotNull(form);

        // 5. approve user
        final Map<String, WorkflowFormPropertyTO> props = form.getPropertyMap();
        props.get("approve").setValue(Boolean.TRUE.toString());
        form.setProperties(props.values());

        // 6. submit approve
        userTO = userWorkflowService.submitForm(form);
        assertNotNull(userTO);
        assertEquals(0, userWorkflowService.getForms().size());
        assertNull(userWorkflowService.getFormForUser(userTO.getId()));

        // 7. search approval into the history as well
        forms = userWorkflowService.getFormsByName(userTO.getId(), "Create approval");
        assertFalse(forms.isEmpty());

        int count = 0;
        for (WorkflowFormTO hform : forms) {
            if (form.getTaskId().equals(hform.getTaskId())) {
                count++;

                assertEquals("createApproval", hform.getKey());
                assertNotNull(hform.getCreateTime());
                assertNotNull(hform.getDueDate());
                assertTrue(Boolean.parseBoolean(hform.getPropertyMap().get("approve").getValue()));
                assertNull(hform.getPropertyMap().get("rejectReason").getValue());
            }
        }
        assertEquals(1, count);

        userService.delete(userTO.getId());

        try {
            userService.read(userTO.getId());
            fail();
        } catch (Exception ignore) {
            // ignore
        }

        try {
            userWorkflowService.getFormsByName(userTO.getId(), "Create approval");
            fail();
        } catch (Exception ignore) {
            // ignore
        }
    }

    @Test
    public void unlink() {
        UserTO userTO = getUniqueSampleTO("unlink@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();
        userTO.getDerAttrs().add(attributeTO("csvuserid", null));
        userTO.getResources().add(RESOURCE_NAME_CSV);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);

        PropagationTargetsTO res = new PropagationTargetsTO();
        res.getResources().add(RESOURCE_NAME_CSV);

        actual = userService.unlink(actual.getId(), res);
        assertNotNull(actual);
        assertTrue(actual.getResources().isEmpty());

        actual = userService.read(actual.getId());
        assertNotNull(actual);

        assertTrue(actual.getResources().isEmpty());

        connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);
    }

    @Test
    public void unassign() {
        UserTO userTO = getUniqueSampleTO("unassign@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();
        userTO.getDerAttrs().add(attributeTO("csvuserid", null));
        userTO.getResources().add(RESOURCE_NAME_CSV);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);

        PropagationTargetsTO res = new PropagationTargetsTO();
        res.getResources().add(RESOURCE_NAME_CSV);

        actual = userService.unassign(actual.getId(), res);
        assertNotNull(actual);
        assertTrue(actual.getResources().isEmpty());

        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertTrue(actual.getResources().isEmpty());

        try {
            readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
            fail();
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void deprovision() {
        UserTO userTO = getUniqueSampleTO("deprovision@syncope.apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getDerAttrs().clear();
        userTO.getVirAttrs().clear();
        userTO.getDerAttrs().add(attributeTO("csvuserid", null));
        userTO.getResources().add(RESOURCE_NAME_CSV);

        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        ConnObjectTO connObjectTO = readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
        assertNotNull(connObjectTO);

        PropagationTargetsTO res = new PropagationTargetsTO();
        res.getResources().add(RESOURCE_NAME_CSV);

        actual = userService.deprovision(actual.getId(), res);
        assertNotNull(actual);
        assertFalse(actual.getResources().isEmpty());

        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertFalse(actual.getResources().isEmpty());

        try {
            readConnectorObject(RESOURCE_NAME_CSV, actual.getId());
            fail();
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void issueSYNCOPE420() {
        UserTO userTO = getUniqueSampleTO("syncope420@syncope.apache.org");
        userTO.getAttrs().add(attributeTO("makeItDouble", "3"));

        userTO = createUser(userTO);
        assertEquals("6", userTO.getAttrMap().get("makeItDouble").getValues().get(0));

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.getAttrsToRemove().add("makeItDouble");
        userMod.getAttrsToUpdate().add(attributeMod("makeItDouble", "7"));

        userTO = userService.update(userMod.getId(), userMod);
        assertEquals("14", userTO.getAttrMap().get("makeItDouble").getValues().get(0));
    }

    @Test
    public void issueSYNCOPE426() {
        UserTO userTO = getUniqueSampleTO("syncope426@syncope.apache.org");
        userTO = createUser(userTO);
        assertNotNull(userTO);

        UserMod userMod = new UserMod();
        userMod.setPassword("anotherPassword123");
        userTO = userService.update(userTO.getId(), userMod);
        assertNotNull(userTO);
    }

}
