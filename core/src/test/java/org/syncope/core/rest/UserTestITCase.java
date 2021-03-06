/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.syncope.core.rest;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.ExpectedException;
import org.springframework.web.client.HttpStatusCodeException;
import org.syncope.client.mod.AttributeMod;
import org.syncope.client.mod.MembershipMod;
import org.syncope.client.mod.UserMod;
import org.syncope.client.to.AttributeTO;
import org.syncope.client.search.AttributeCond;
import org.syncope.client.to.MembershipTO;
import org.syncope.client.search.NodeCond;
import org.syncope.client.to.TaskTO;
import org.syncope.client.to.UserTO;
import org.syncope.client.to.WorkflowActionsTO;
import org.syncope.client.validation.SyncopeClientCompositeErrorException;
import org.syncope.client.validation.SyncopeClientException;
import org.syncope.core.persistence.beans.user.SyncopeUser;
import org.syncope.core.workflow.Constants;
import org.syncope.types.SyncopeClientExceptionType;

public class UserTestITCase extends AbstractTest {

    private UserTO getSampleTO(final String email) {
        UserTO userTO = new UserTO();
        userTO.setPassword("password");

        AttributeTO usernameTO = new AttributeTO();
        usernameTO.setSchema("username");
        usernameTO.addValue(email);
        userTO.addAttribute(usernameTO);

        AttributeTO firstnameTO = new AttributeTO();
        firstnameTO.setSchema("firstname");
        firstnameTO.addValue(email);
        userTO.addAttribute(firstnameTO);

        AttributeTO surnameTO = new AttributeTO();
        surnameTO.setSchema("surname");
        surnameTO.addValue("Surname");
        userTO.addAttribute(surnameTO);

        AttributeTO typeTO = new AttributeTO();
        typeTO.setSchema("type");
        typeTO.addValue("a type");
        userTO.addAttribute(typeTO);

        AttributeTO userIdTO = new AttributeTO();
        userIdTO.setSchema("userId");
        userIdTO.addValue(email);
        userTO.addAttribute(userIdTO);

        AttributeTO emailTO = new AttributeTO();
        emailTO.setSchema("email");
        emailTO.addValue(email);
        userTO.addAttribute(emailTO);

        AttributeTO loginDateTO = new AttributeTO();
        loginDateTO.setSchema("loginDate");
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        loginDateTO.addValue(sdf.format(new Date()));
        userTO.addAttribute(loginDateTO);

        return userTO;
    }

    @Test
    public final void createUserWithNoPropagation() {
        // get task list
        List<TaskTO> tasks = Arrays.asList(
                restTemplate.getForObject(
                BASE_URL + "task/list", TaskTO[].class));

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long maxId = Long.MIN_VALUE;
        for (TaskTO task : tasks) {
            if (task.getId() > maxId) {
                maxId = task.getId();
            }
        }

        // create a new user
        UserTO userTO = new UserTO();

        AttributeTO attributeTO = new AttributeTO();
        attributeTO.setSchema("firstname");
        attributeTO.addValue("xxx");
        userTO.addAttribute(attributeTO);

        attributeTO = new AttributeTO();
        attributeTO.setSchema("surname");
        attributeTO.addValue("xxx");
        userTO.addAttribute(attributeTO);

        attributeTO = new AttributeTO();
        attributeTO.setSchema("userId");
        attributeTO.addValue("xxx@xxx.xxx");
        userTO.addAttribute(attributeTO);

        attributeTO = new AttributeTO();
        attributeTO.setSchema("username");
        attributeTO.addValue("xxx");
        userTO.addAttribute(attributeTO);

        userTO.setPassword("xxx");
        userTO.addResource("ws-target-resource-nopropagation");

        restTemplate.postForObject(BASE_URL + "user/create"
                + "?mandatoryResources=ws-target-resource-nopropagation",
                userTO, UserTO.class);

        // get the new task list
        tasks = Arrays.asList(
                restTemplate.getForObject(
                BASE_URL + "task/list", TaskTO[].class));

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long newMaxId = Long.MIN_VALUE;
        for (TaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }

        assertTrue(newMaxId > maxId);

        // get last task
        TaskTO taskTO = restTemplate.getForObject(
                BASE_URL + "task/read/{taskId}", TaskTO.class, newMaxId);

        assertNotNull(taskTO);
        assertTrue(taskTO.getExecutions().isEmpty());
    }

    @Test
    @ExpectedException(value = SyncopeClientCompositeErrorException.class)
    public final void createWithException() {
        AttributeTO attributeTO = new AttributeTO();
        attributeTO.setSchema("userId");
        attributeTO.addValue("userId@nowhere.org");

        UserTO newUserTO = new UserTO();
        newUserTO.addAttribute(attributeTO);

        restTemplate.postForObject(BASE_URL + "user/create",
                newUserTO, UserTO.class);
    }

    @Test
    public final void create() {
        // get task list
        List<TaskTO> tasks = Arrays.asList(
                restTemplate.getForObject(
                BASE_URL + "task/list", TaskTO[].class));

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long maxId = Long.MIN_VALUE;
        for (TaskTO task : tasks) {
            if (task.getId() > maxId) {
                maxId = task.getId();
            }
        }

        UserTO userTO = getSampleTO("a.b@c.com");

        // add a membership
        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.addMembership(membershipTO);

        // add an attribute with no values: must be ignored
        AttributeTO nullValueAttributeTO = new AttributeTO();
        nullValueAttributeTO.setSchema("subscriptionDate");
        nullValueAttributeTO.setValues(null);
        membershipTO.addAttribute(nullValueAttributeTO);

        // add an attribute with a non-existing schema: must be ignored
        AttributeTO attrWithInvalidSchemaTO = new AttributeTO();
        attrWithInvalidSchemaTO.setSchema("invalid schema");
        attrWithInvalidSchemaTO.addValue("a value");
        userTO.addAttribute(attrWithInvalidSchemaTO);

        // add an attribute with null value: must be ignored
        nullValueAttributeTO = new AttributeTO();
        nullValueAttributeTO.setSchema("activationDate");
        nullValueAttributeTO.addValue(null);
        userTO.addAttribute(nullValueAttributeTO);

        // 1. create user
        UserTO newUserTO = restTemplate.postForObject(
                BASE_URL + "user/create?mandatoryRoles=8",
                userTO, UserTO.class);

        assertNotNull(newUserTO);
        assertFalse(newUserTO.getAttributes().contains(
                attrWithInvalidSchemaTO));

        WorkflowActionsTO workflowActions = restTemplate.getForObject(
                BASE_URL + "user/actions/{userId}", WorkflowActionsTO.class,
                newUserTO.getId());
        assertTrue(workflowActions.getActions().equals(
                Collections.singleton(Constants.ACTION_ACTIVATE)));

        // 2. activate user
        newUserTO = restTemplate.postForObject(BASE_URL + "user/activate",
                newUserTO, UserTO.class);
        assertEquals("active",
                restTemplate.getForObject(BASE_URL + "user/status/"
                + newUserTO.getId(), String.class));

        // get the new task list
        tasks = Arrays.asList(
                restTemplate.getForObject(
                BASE_URL + "task/list", TaskTO[].class));

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // get max task id
        long newMaxId = Long.MIN_VALUE;
        for (TaskTO task : tasks) {
            if (task.getId() > newMaxId) {
                newMaxId = task.getId();
            }
        }

        assertTrue(newMaxId > maxId);

        // get last task
        TaskTO taskTO = restTemplate.getForObject(
                BASE_URL + "task/read/{taskId}", TaskTO.class, newMaxId);

        assertNotNull(taskTO);
        assertFalse(taskTO.getExecutions().isEmpty());

        // 3. verify password
        Boolean verify = restTemplate.getForObject(
                BASE_URL + "user/verifyPassword/{userId}?password=password",
                Boolean.class, newUserTO.getId());
        assertTrue(verify);
        verify = restTemplate.getForObject(
                BASE_URL + "user/verifyPassword/{userId}?password=passwordXX",
                Boolean.class, newUserTO.getId());
        assertFalse(verify);

        // 4. try (and fail) to create another user with same (unique) values
        userTO = getSampleTO("pippo@c.com");
        for (AttributeTO attr : userTO.getAttributes()) {
            if ("userId".equals(attr.getSchema())) {
                attr.getValues().clear();
                attr.addValue("a.b@c.com");
            }
        }

        SyncopeClientException sce = null;
        try {
            restTemplate.postForObject(BASE_URL + "user/create",
                    userTO, UserTO.class);
        } catch (SyncopeClientCompositeErrorException e) {
            sce = e.getException(
                    SyncopeClientExceptionType.DuplicateUniqueValue);
        }
        assertNotNull(sce);
    }

    @Test
    public final void createWithRequiredValueMissing() {
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
            restTemplate.postForObject(
                    BASE_URL + "user/create?mandatoryRoles=8",
                    userTO, UserTO.class);
        } catch (SyncopeClientCompositeErrorException e) {
            ex = e;
        }
        assertNotNull(ex);
        assertNotNull(ex.getException(
                SyncopeClientExceptionType.RequiredValuesMissing));

        AttributeTO fType = new AttributeTO();
        fType.setSchema("type");
        fType.addValue("F");
        userTO.addAttribute(fType);

        AttributeTO firstname = null;
        for (AttributeTO attributeTO : userTO.getAttributes()) {
            if ("firstname".equals(attributeTO.getSchema())) {
                firstname = attributeTO;
            }
        }
        userTO.removeAttribute(firstname);

        // 2. create user without firstname (mandatory when type == 'F')
        ex = null;
        try {
            restTemplate.postForObject(
                    BASE_URL + "user/create?mandatoryRoles=8",
                    userTO, UserTO.class);
        } catch (SyncopeClientCompositeErrorException e) {
            ex = e;
        }
        assertNotNull(ex);
        assertNotNull(ex.getException(
                SyncopeClientExceptionType.RequiredValuesMissing));
    }

    @Test
    public final void delete() {
        try {
            restTemplate.delete(BASE_URL + "user/delete/{userId}", 0);
        } catch (HttpStatusCodeException e) {
            assertEquals(e.getStatusCode(), HttpStatus.NOT_FOUND);
        }

        UserTO userTO = getSampleTO("qqgf.z@nn.com");

        userTO = restTemplate.postForObject(BASE_URL + "user/create",
                userTO, UserTO.class);
        userTO = restTemplate.postForObject(BASE_URL + "user/activate",
                userTO, UserTO.class);

        restTemplate.delete(BASE_URL + "user/delete/{userId}", userTO.getId());
        try {
            restTemplate.getForObject(BASE_URL + "user/read/{userId}.json",
                    UserTO.class, userTO.getId());
        } catch (HttpStatusCodeException e) {
            assertEquals(e.getStatusCode(), HttpStatus.NOT_FOUND);
        }
    }

    @Test
    public final void count() {
        Integer count = restTemplate.getForObject(
                BASE_URL + "user/count.json", Integer.class);
        assertNotNull(count);
        assertTrue(count > 0);
    }

    @Test
    public final void searchCount() {
        AttributeCond isNullCond = new AttributeCond(AttributeCond.Type.ISNULL);
        isNullCond.setSchema("loginDate");
        NodeCond searchCond = NodeCond.getLeafCond(isNullCond);

        Integer count = restTemplate.postForObject(
                BASE_URL + "user/search/count.json", searchCond, Integer.class);
        assertNotNull(count);
        assertTrue(count > 0);
    }

    @Test
    public final void list() {
        List<UserTO> users = Arrays.asList(
                restTemplate.getForObject(
                BASE_URL + "user/list.json", UserTO[].class));
        assertNotNull(users);
        assertFalse(users.isEmpty());
        for (UserTO user : users) {
            assertNotNull(user);
        }
    }

    @Test
    public final void paginatedList() {
        List<UserTO> users = Arrays.asList(restTemplate.getForObject(
                BASE_URL + "user/list/{page}/{size}.json",
                UserTO[].class, 1, 2));

        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(2, users.size());

        for (UserTO user : users) {
            assertNotNull(user);
        }

        users = Arrays.asList(restTemplate.getForObject(
                BASE_URL + "user/list/{page}/{size}.json",
                UserTO[].class, 2, 2));

        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(2, users.size());

        users = Arrays.asList(restTemplate.getForObject(
                BASE_URL + "user/list/{page}/{size}.json",
                UserTO[].class, 100, 2));

        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    public final void read() {
        UserTO userTO = restTemplate.getForObject(
                BASE_URL + "user/read/{userId}.json", UserTO.class, 1);

        assertNotNull(userTO);
        assertNotNull(userTO.getAttributes());
        assertFalse(userTO.getAttributes().isEmpty());
    }

    @Test
    public final void token() {
        UserTO userTO = getSampleTO("d.e@f.com");

        userTO = restTemplate.postForObject(BASE_URL + "user/create",
                userTO, UserTO.class);
        userTO = restTemplate.postForObject(BASE_URL + "user/activate",
                userTO, UserTO.class);
        assertNull(userTO.getToken());

        userTO = restTemplate.getForObject(
                BASE_URL + "user/generateToken/{userId}",
                UserTO.class, userTO.getId());
        assertNotNull(userTO.getToken());

        userTO = restTemplate.postForObject(BASE_URL + "user/verifyToken",
                userTO, UserTO.class);
        assertNull(userTO.getToken());
    }

    @Test
    public final void search() {
        // LIKE
        AttributeCond usernameLeafCond1 =
                new AttributeCond(AttributeCond.Type.LIKE);
        usernameLeafCond1.setSchema("username");
        usernameLeafCond1.setExpression("%o%");

        AttributeCond usernameLeafCond2 =
                new AttributeCond(AttributeCond.Type.LIKE);
        usernameLeafCond2.setSchema("username");
        usernameLeafCond2.setExpression("%i%");

        NodeCond searchCondition = NodeCond.getAndCond(
                NodeCond.getLeafCond(usernameLeafCond1),
                NodeCond.getLeafCond(usernameLeafCond2));

        assertTrue(searchCondition.checkValidity());

        List<UserTO> matchedUsers = Arrays.asList(
                restTemplate.postForObject(
                BASE_URL + "user/search",
                searchCondition, UserTO[].class));
        assertNotNull(matchedUsers);
        assertFalse(matchedUsers.isEmpty());
        for (UserTO user : matchedUsers) {
            assertNotNull(user);
        }

        // ISNULL
        AttributeCond isNullCond = new AttributeCond(AttributeCond.Type.ISNULL);
        isNullCond.setSchema("loginDate");
        searchCondition = NodeCond.getLeafCond(isNullCond);

        matchedUsers = Arrays.asList(
                restTemplate.postForObject(BASE_URL + "user/search",
                searchCondition, UserTO[].class));
        assertNotNull(matchedUsers);
        assertFalse(matchedUsers.isEmpty());
        Set<Long> userIds = new HashSet<Long>(matchedUsers.size());
        for (UserTO user : matchedUsers) {
            userIds.add(user.getId());
        }
        assertEquals(2, userIds.size());
        assertTrue(userIds.contains(2L));
        assertTrue(userIds.contains(3L));
    }

    @Test
    public final void paginatedSearch() {
        // LIKE
        AttributeCond usernameLeafCond1 =
                new AttributeCond(AttributeCond.Type.LIKE);
        usernameLeafCond1.setSchema("username");
        usernameLeafCond1.setExpression("%o%");

        AttributeCond usernameLeafCond2 =
                new AttributeCond(AttributeCond.Type.LIKE);
        usernameLeafCond2.setSchema("username");
        usernameLeafCond2.setExpression("%i%");

        NodeCond searchCondition = NodeCond.getAndCond(
                NodeCond.getLeafCond(usernameLeafCond1),
                NodeCond.getLeafCond(usernameLeafCond2));

        assertTrue(searchCondition.checkValidity());

        List<UserTO> matchedUsers = Arrays.asList(restTemplate.postForObject(
                BASE_URL + "user/search/{page}/{size}",
                searchCondition, UserTO[].class, 1, 2));
        assertNotNull(matchedUsers);

        assertFalse(matchedUsers.isEmpty());
        for (UserTO user : matchedUsers) {
            assertNotNull(user);
        }

        // ISNULL
        AttributeCond isNullCond = new AttributeCond(AttributeCond.Type.ISNULL);
        isNullCond.setSchema("loginDate");
        searchCondition = NodeCond.getLeafCond(isNullCond);

        matchedUsers = Arrays.asList(restTemplate.postForObject(
                BASE_URL + "user/search/{page}/{size}",
                searchCondition, UserTO[].class, 1, 2));

        assertNotNull(matchedUsers);
        assertFalse(matchedUsers.isEmpty());
        Set<Long> userIds = new HashSet<Long>(matchedUsers.size());
        for (UserTO user : matchedUsers) {
            userIds.add(user.getId());
        }
        assertEquals(2, userIds.size());
        assertTrue(userIds.contains(2L));
        assertTrue(userIds.contains(3L));
    }

    @Test
    public final void update() {
        UserTO userTO = getSampleTO("g.h@t.com");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        AttributeTO membershipAttr = new AttributeTO();
        membershipAttr.setSchema("subscriptionDate");
        membershipAttr.addValue("2009-08-18T16:33:12.203+0200");
        membershipTO.addAttribute(membershipAttr);
        userTO.addMembership(membershipTO);

        userTO = restTemplate.postForObject(BASE_URL + "user/create",
                userTO, UserTO.class);
        userTO = restTemplate.postForObject(BASE_URL + "user/activate",
                userTO, UserTO.class);

        assertTrue(userTO.getDerivedAttributes().isEmpty());
        assertEquals(1, userTO.getMemberships().size());

        AttributeMod attributeMod = new AttributeMod();
        attributeMod.setSchema("subscriptionDate");
        attributeMod.addValueToBeAdded("2010-08-18T16:33:12.203+0200");

        MembershipMod membershipMod = new MembershipMod();
        membershipMod.setRole(8L);
        membershipMod.addAttributeToBeUpdated(attributeMod);

        attributeMod = new AttributeMod();
        attributeMod.setSchema("userId");
        attributeMod.addValueToBeAdded("t.w@spre.net");

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());
        userMod.setPassword("newPassword");
        userMod.addAttributeToBeRemoved("userId");
        userMod.addAttributeToBeUpdated(attributeMod);
        userMod.addDerivedAttributeToBeAdded("cn");
        userMod.addMembershipToBeAdded(membershipMod);
        userMod.addMembershipToBeRemoved(
                userTO.getMemberships().iterator().next().getId());

        userTO = restTemplate.postForObject(BASE_URL + "user/update",
                userMod, UserTO.class);

        SyncopeUser passwordTestUser = new SyncopeUser();
        passwordTestUser.setPassword("newPassword");
        assertEquals(passwordTestUser.getPassword(),
                userTO.getPassword());

        assertEquals(1, userTO.getMemberships().size());
        assertEquals(1, userTO.getMemberships().iterator().next().
                getAttributes().size());
        assertFalse(userTO.getDerivedAttributes().isEmpty());
        boolean attributeFound = false;
        for (AttributeTO attributeTO : userTO.getAttributes()) {
            if ("userId".equals(attributeTO.getSchema())) {
                attributeFound = true;

                assertEquals(Collections.singletonList("t.w@spre.net"),
                        attributeTO.getValues());
            }
        }
        assertTrue(attributeFound);
    }
}
