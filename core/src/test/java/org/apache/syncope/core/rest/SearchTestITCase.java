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
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.syncope.common.search.AttributableCond;
import org.apache.syncope.common.search.AttributeCond;
import org.apache.syncope.common.search.EntitlementCond;
import org.apache.syncope.common.search.NodeCond;
import org.apache.syncope.common.search.ResourceCond;
import org.apache.syncope.common.services.InvalidSearchConditionException;
import org.apache.syncope.common.to.RoleTO;
import org.apache.syncope.common.to.UserTO;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.JVM)
public class SearchTestITCase extends AbstractTest {

    @Test
    public void searchUser() throws InvalidSearchConditionException {
        // LIKE
        AttributeCond fullnameLeafCond1 = new AttributeCond(AttributeCond.Type.LIKE);
        fullnameLeafCond1.setSchema("fullname");
        fullnameLeafCond1.setExpression("%o%");

        AttributeCond fullnameLeafCond2 = new AttributeCond(AttributeCond.Type.LIKE);
        fullnameLeafCond2.setSchema("fullname");
        fullnameLeafCond2.setExpression("%i%");

        NodeCond searchCondition = NodeCond.getAndCond(NodeCond.getLeafCond(fullnameLeafCond1), NodeCond.getLeafCond(
                fullnameLeafCond2));

        assertTrue(searchCondition.isValid());

        List<UserTO> matchedUsers = userService.search(searchCondition);
        
        assertNotNull(matchedUsers);
        assertFalse(matchedUsers.isEmpty());
        for (UserTO user : matchedUsers) {
            assertNotNull(user);
        }

        // ISNULL
        AttributeCond isNullCond = new AttributeCond(AttributeCond.Type.ISNULL);
        isNullCond.setSchema("loginDate");
        searchCondition = NodeCond.getLeafCond(isNullCond);

        matchedUsers = userService.search(searchCondition);
        assertNotNull(matchedUsers);
        assertFalse(matchedUsers.isEmpty());

        Set<Long> userIds = new HashSet<Long>(matchedUsers.size());
        for (UserTO user : matchedUsers) {
            userIds.add(user.getId());
        }
        assertTrue(userIds.contains(2L));
        assertTrue(userIds.contains(3L));
    }

    @Test
    public void searchByUsernameAndId() throws InvalidSearchConditionException {
        final AttributableCond usernameLeafCond = new AttributableCond(AttributableCond.Type.EQ);
        usernameLeafCond.setSchema("username");
        usernameLeafCond.setExpression("rossini");

        final AttributableCond idRightCond = new AttributableCond(AttributableCond.Type.LT);
        idRightCond.setSchema("id");
        idRightCond.setExpression("2");

        final NodeCond searchCondition = NodeCond.getAndCond(NodeCond.getLeafCond(usernameLeafCond), NodeCond.
                getLeafCond(idRightCond));

        assertTrue(searchCondition.isValid());

        final List<UserTO> matchingUsers = userService.search(searchCondition);

        assertNotNull(matchingUsers);
        assertEquals(1, matchingUsers.size());
        assertEquals("rossini", matchingUsers.iterator().next().getUsername());
        assertEquals(1L, matchingUsers.iterator().next().getId());
    }

    @Test
    public void searchByRolenameAndId() throws InvalidSearchConditionException {
        final AttributableCond rolenameLeafCond = new AttributableCond(AttributableCond.Type.EQ);
        rolenameLeafCond.setSchema("name");
        rolenameLeafCond.setExpression("root");

        final AttributableCond idRightCond = new AttributableCond(AttributableCond.Type.LT);
        idRightCond.setSchema("id");
        idRightCond.setExpression("2");

        final NodeCond searchCondition = NodeCond.getAndCond(NodeCond.getLeafCond(rolenameLeafCond),
                NodeCond.getLeafCond(idRightCond));

        assertTrue(searchCondition.isValid());

        final List<RoleTO> matchingRoles = roleService.search(searchCondition);

        assertNotNull(matchingRoles);
        assertEquals(1, matchingRoles.size());
        assertEquals("root", matchingRoles.iterator().next().getName());
        assertEquals(1L, matchingRoles.iterator().next().getId());
    }

    @Test
    public void searchUserByResourceName() throws InvalidSearchConditionException {
        ResourceCond ws2 = new ResourceCond();
        ws2.setResourceName("ws-target-resource2");

        ResourceCond ws1 = new ResourceCond();
        ws1.setResourceName(RESOURCE_NAME_MAPPINGS2);

        NodeCond searchCondition = NodeCond.getAndCond(NodeCond.getNotLeafCond(ws2), NodeCond.getLeafCond(ws1));

        assertTrue(searchCondition.isValid());

        List<UserTO> matchedUsers = userService.search(searchCondition);
        assertNotNull(matchedUsers);
        assertFalse(matchedUsers.isEmpty());

        Set<Long> userIds = new HashSet<Long>(matchedUsers.size());
        for (UserTO user : matchedUsers) {
            userIds.add(user.getId());
        }

        assertEquals(1, userIds.size());
        assertTrue(userIds.contains(2L));
    }

    @Test
    public void paginatedSearch() throws InvalidSearchConditionException {
        // LIKE
        AttributeCond fullnameLeafCond1 = new AttributeCond(AttributeCond.Type.LIKE);
        fullnameLeafCond1.setSchema("fullname");
        fullnameLeafCond1.setExpression("%o%");

        AttributeCond fullnameLeafCond2 = new AttributeCond(AttributeCond.Type.LIKE);
        fullnameLeafCond2.setSchema("fullname");
        fullnameLeafCond2.setExpression("%i%");

        NodeCond searchCondition = NodeCond.getAndCond(NodeCond.getLeafCond(fullnameLeafCond1), NodeCond.getLeafCond(
                fullnameLeafCond2));

        assertTrue(searchCondition.isValid());

        List<UserTO> matchedUsers = userService.search(searchCondition, 1, 2);
        assertNotNull(matchedUsers);

        assertFalse(matchedUsers.isEmpty());
        for (UserTO user : matchedUsers) {
            assertNotNull(user);
        }

        // ISNULL
        AttributeCond isNullCond = new AttributeCond(AttributeCond.Type.ISNULL);
        isNullCond.setSchema("loginDate");
        searchCondition = NodeCond.getLeafCond(isNullCond);

        matchedUsers = userService.search(searchCondition, 1, 2);

        assertNotNull(matchedUsers);
        assertFalse(matchedUsers.isEmpty());
        Set<Long> userIds = new HashSet<Long>(matchedUsers.size());
        for (UserTO user : matchedUsers) {
            userIds.add(user.getId());
        }
        assertEquals(2, userIds.size());
    }

    @Test
    public void searchCount() throws InvalidSearchConditionException {
        AttributeCond isNullCond = new AttributeCond(AttributeCond.Type.ISNULL);
        isNullCond.setSchema("loginDate");
        NodeCond searchCond = NodeCond.getLeafCond(isNullCond);

        Integer count = userService.searchCount(searchCond);
        assertNotNull(count);
        assertTrue(count > 0);
    }

    @Test
    public void searchByBooleanAttributableCond() throws InvalidSearchConditionException {
        final AttributableCond cond = new AttributableCond(AttributableCond.Type.EQ);
        cond.setSchema("inheritAttrs");
        cond.setExpression("true");

        final NodeCond searchCondition = NodeCond.getLeafCond(cond);

        final List<RoleTO> matchingRoles = roleService.search(searchCondition);
        assertNotNull(matchingRoles);
        assertFalse(matchingRoles.isEmpty());
    }

    @Test
    public void searchByEntitlement() throws InvalidSearchConditionException {
        final EntitlementCond userListCond = new EntitlementCond();
        userListCond.setExpression("USER_LIST");

        final EntitlementCond userReadcond = new EntitlementCond();
        userReadcond.setExpression("USER_READ");

        final NodeCond searchCondition = NodeCond.getAndCond(NodeCond.getLeafCond(userListCond),
                NodeCond.getLeafCond(userReadcond));
        assertTrue(searchCondition.isValid());

        final List<RoleTO> matchingRoles = roleService.search(searchCondition);
        assertNotNull(matchingRoles);
        assertFalse(matchingRoles.isEmpty());
    }

    @Test
    public void searchByRelationshipAttributableCond() throws InvalidSearchConditionException {
        final AttributableCond userOwnerCond = new AttributableCond(AttributableCond.Type.EQ);
        userOwnerCond.setSchema("userOwner");
        userOwnerCond.setExpression("5");

        final AttributableCond ppolicyCond = new AttributableCond(AttributableCond.Type.ISNOTNULL);
        ppolicyCond.setSchema("passwordPolicy");

        final NodeCond searchCondition = NodeCond.getAndCond(NodeCond.getLeafCond(userOwnerCond),
                NodeCond.getLeafCond(ppolicyCond));

        assertTrue(searchCondition.isValid());

        final List<RoleTO> matchingRoles = roleService.search(searchCondition);

        assertNotNull(matchingRoles);
        assertEquals(1, matchingRoles.size());
        assertEquals("director", matchingRoles.iterator().next().getName());
        assertEquals(6L, matchingRoles.iterator().next().getId());
    }
}
