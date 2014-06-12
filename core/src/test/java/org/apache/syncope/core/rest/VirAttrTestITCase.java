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

import static org.apache.syncope.core.rest.AbstractTest.RESOURCE_NAME_LDAP;
import static org.apache.syncope.core.rest.AbstractTest.attributeMod;
import static org.apache.syncope.core.rest.AbstractTest.attributeTO;
import static org.apache.syncope.core.rest.UserTestITCase.getUniqueSampleTO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;
import org.apache.syncope.common.mod.AttributeMod;
import org.apache.syncope.common.mod.UserMod;
import org.apache.syncope.common.to.AttributeTO;
import org.apache.syncope.common.to.ConnInstanceTO;
import org.apache.syncope.common.to.ConnObjectTO;
import org.apache.syncope.common.to.MappingItemTO;
import org.apache.syncope.common.to.MappingTO;
import org.apache.syncope.common.to.MembershipTO;
import org.apache.syncope.common.to.PropagationRequestTO;
import org.apache.syncope.common.to.ResourceTO;
import org.apache.syncope.common.to.RoleTO;
import org.apache.syncope.common.to.UserTO;
import org.apache.syncope.common.types.AttributableType;
import org.apache.syncope.common.types.ConnConfProperty;
import org.apache.syncope.common.types.IntMappingType;
import org.apache.syncope.common.types.MappingPurpose;
import org.apache.syncope.common.types.PropagationTaskExecStatus;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.jdbc.core.JdbcTemplate;

@FixMethodOrder(MethodSorters.JVM)
public class VirAttrTestITCase extends AbstractTest {

    @Test
    public void issueSYNCOPE16() {
        UserTO userTO = getUniqueSampleTO("issue16@apache.org");

        MembershipTO membershipTO = new MembershipTO();
        membershipTO.setRoleId(8L);
        userTO.addMembership(membershipTO);

        // 1. create user
        UserTO actual = createUser(userTO);
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
    public void issueSYNCOPE260() {
        // ----------------------------------
        // create user and check virtual attribute value propagation
        // ----------------------------------
        UserTO userTO = getUniqueSampleTO("260@a.com");
        userTO.addResource(RESOURCE_NAME_WS2);

        userTO = createUser(userTO);
        assertNotNull(userTO);
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals(RESOURCE_NAME_WS2, userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        ConnObjectTO connObjectTO =
                resourceService.getConnectorObject(RESOURCE_NAME_WS2, AttributableType.USER, userTO.getId());
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
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals(RESOURCE_NAME_WS2, userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        connObjectTO = resourceService.getConnectorObject(RESOURCE_NAME_WS2, AttributableType.USER, userTO.getId());
        assertNotNull(connObjectTO);
        assertEquals("virtualvalue2", connObjectTO.getAttributeMap().get("NAME").getValues().get(0));
        // ----------------------------------

        // ----------------------------------
        // suspend/reactivate user and check virtual attribute value (unchanged)
        // ----------------------------------
        userTO = userService.suspend(userTO.getId());
        assertEquals("suspended", userTO.getStatus());

        connObjectTO = resourceService.getConnectorObject(RESOURCE_NAME_WS2, AttributableType.USER, userTO.getId());
        assertNotNull(connObjectTO);
        assertFalse(connObjectTO.getAttributeMap().get("NAME").getValues().isEmpty());
        assertEquals("virtualvalue2", connObjectTO.getAttributeMap().get("NAME").getValues().get(0));

        userTO = userService.reactivate(userTO.getId());
        assertEquals("active", userTO.getStatus());

        connObjectTO = resourceService.getConnectorObject(RESOURCE_NAME_WS2, AttributableType.USER, userTO.getId());
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
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals(RESOURCE_NAME_WS2, userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        connObjectTO = resourceService.getConnectorObject(RESOURCE_NAME_WS2, AttributableType.USER, userTO.getId());
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
        assertFalse(userTO.getPropagationStatusTOs().isEmpty());
        assertEquals(RESOURCE_NAME_WS2, userTO.getPropagationStatusTOs().get(0).getResource());
        assertEquals(PropagationTaskExecStatus.SUBMITTED, userTO.getPropagationStatusTOs().get(0).getStatus());

        connObjectTO = resourceService.getConnectorObject(RESOURCE_NAME_WS2, AttributableType.USER, userTO.getId());
        assertNotNull(connObjectTO);

        // attribute "name" mapped on virtual attribute "virtualdata" should be reset
        assertTrue(connObjectTO.getAttributeMap().get("NAME").getValues() == null
                || connObjectTO.getAttributeMap().get("NAME").getValues().isEmpty());
        // ----------------------------------
    }

    @Test
    public void virAttrCache() {
        UserTO userTO = getUniqueSampleTO("virattrcache@apache.org");
        userTO.getVirtualAttributes().clear();

        AttributeTO virAttrTO = new AttributeTO();
        virAttrTO.setSchema("virtualdata");
        virAttrTO.addValue("virattrcache");
        userTO.addVirtualAttribute(virAttrTO);

        userTO.getMemberships().clear();
        userTO.getResources().clear();
        userTO.addResource(RESOURCE_NAME_DBVIRATTR);

        // 1. create user
        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        // 2. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertEquals("virattrcache", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));

        // ----------------------------------------
        // 3. update virtual attribute
        // ----------------------------------------
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

        String value = jdbcTemplate.queryForObject(
                "SELECT USERNAME FROM testsync WHERE ID=?", String.class, actual.getId());
        assertEquals("virattrcache", value);

        jdbcTemplate.update("UPDATE testsync set USERNAME='virattrcache2' WHERE ID=?", actual.getId());

        value = jdbcTemplate.queryForObject(
                "SELECT USERNAME FROM testsync WHERE ID=?", String.class, actual.getId());
        assertEquals("virattrcache2", value);
        // ----------------------------------------

        // 4. check for cached attribute value
        actual = userService.read(actual.getId());
        assertEquals("virattrcache", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));

        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());

        AttributeMod virtualdata = new AttributeMod();
        virtualdata.setSchema("virtualdata");
        virtualdata.addValueToBeAdded("virtualupdated");

        userMod.addVirtualAttributeToBeRemoved("virtualdata");
        userMod.addVirtualAttributeToBeUpdated(virtualdata);

        // 5. update virtual attribute
        actual = userService.update(actual.getId(), userMod);
        assertNotNull(actual);

        // 6. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertNotNull(actual);
        assertEquals("virtualupdated", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));
    }

    @Test
    public void issueSYNCOPE397() {
        ResourceTO csv = resourceService.read(RESOURCE_NAME_CSV);

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
        userTO.getDerivedAttributes().clear();
        userTO.getVirtualAttributes().clear();

        userTO.addDerivedAttribute(attributeTO("csvuserid", null));
        userTO.addDerivedAttribute(attributeTO("cn", null));
        userTO.addVirtualAttribute(attributeTO("virtualdata", "test@testone.org"));
        // assign resource-csv to user
        userTO.addResource(RESOURCE_NAME_CSV);
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
        userMod.addResourceToBeAdded(RESOURCE_NAME_WS2);
        //modify virtual attribute
        userMod.addVirtualAttributeToBeRemoved("virtualdata");
        userMod.addVirtualAttributeToBeUpdated(attributeMod("virtualdata", "test@testoneone.com"));

        // check Syncope change password
        PropagationRequestTO pwdPropRequest = new PropagationRequestTO();
        pwdPropRequest.setOnSyncope(true);
        pwdPropRequest.addResource(RESOURCE_NAME_WS2);
        userMod.setPwdPropRequest(pwdPropRequest);

        toBeUpdated = userService.update(userMod.getId(), userMod);
        assertNotNull(toBeUpdated);
        assertEquals("test@testoneone.com", toBeUpdated.getVirtualAttributes().get(0).getValues().get(0));
        // check if propagates correctly with assertEquals on size of tasks list
        assertEquals(2, toBeUpdated.getPropagationStatusTOs().size());
    }

    @Test
    public void issueSYNCOPE442() {
        UserTO userTO = getUniqueSampleTO("syncope442@apache.org");
        userTO.getVirtualAttributes().clear();

        AttributeTO virAttrTO = new AttributeTO();
        virAttrTO.setSchema("virtualdata");
        virAttrTO.addValue("virattrcache");
        userTO.addVirtualAttribute(virAttrTO);

        userTO.getMemberships().clear();
        userTO.getResources().clear();
        userTO.addResource(RESOURCE_NAME_DBVIRATTR);

        // 1. create user
        UserTO actual = createUser(userTO);
        assertNotNull(actual);

        // 2. check for virtual attribute value
        actual = userService.read(actual.getId());
        assertEquals("virattrcache", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));

        // ----------------------------------------
        // 3. force cache expiring without any modification
        // ----------------------------------------
        String jdbcURL = null;
        ConnInstanceTO connInstanceBean = connectorService.readByResource(RESOURCE_NAME_DBVIRATTR);
        for (ConnConfProperty prop : connInstanceBean.getConfiguration()) {
            if ("jdbcUrlTemplate".equals(prop.getSchema().getName())) {
                jdbcURL = prop.getValues().iterator().next().toString();
                prop.setValues(Collections.singletonList("jdbc:h2:tcp://localhost:9092/xxx"));
            }
        }

        connectorService.update(connInstanceBean.getId(), connInstanceBean);

        UserMod userMod = new UserMod();
        userMod.setId(actual.getId());

        AttributeMod virtualdata = new AttributeMod();
        virtualdata.setSchema("virtualdata");
        virtualdata.addValueToBeAdded("virtualupdated");

        userMod.addVirtualAttributeToBeRemoved("virtualdata");
        userMod.addVirtualAttributeToBeUpdated(virtualdata);

        actual = userService.update(actual.getId(), userMod);
        assertNotNull(actual);
        // ----------------------------------------

        // ----------------------------------------
        // 4. update virtual attribute
        // ----------------------------------------
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

        String value = jdbcTemplate.queryForObject(
                "SELECT USERNAME FROM testsync WHERE ID=?", String.class, actual.getId());
        assertEquals("virattrcache", value);

        jdbcTemplate.update("UPDATE testsync set USERNAME='virattrcache2' WHERE ID=?", actual.getId());

        value = jdbcTemplate.queryForObject(
                "SELECT USERNAME FROM testsync WHERE ID=?", String.class, actual.getId());
        assertEquals("virattrcache2", value);
        // ----------------------------------------

        actual = userService.read(actual.getId());
        assertEquals("virattrcache", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));

        // ----------------------------------------
        // 5. restore connector
        // ----------------------------------------
        for (ConnConfProperty prop : connInstanceBean.getConfiguration()) {
            if ("jdbcUrlTemplate".equals(prop.getSchema().getName())) {
                prop.setValues(Collections.singletonList(jdbcURL));
            }
        }

        connectorService.update(connInstanceBean.getId(), connInstanceBean);
        // ----------------------------------------

        actual = userService.read(actual.getId());
        assertEquals("virattrcache2", actual.getVirtualAttributeMap().get("virtualdata").getValues().get(0));
    }

    @Test
    public void issueSYNCOPE436() {
        UserTO userTO = getUniqueSampleTO("syncope436@syncope.apache.org");
        userTO.getMemberships().clear();
        userTO.getResources().clear();
        userTO.addResource(RESOURCE_NAME_LDAP);
        userTO.addVirtualAttribute(attributeTO("virtualReadOnly", "readOnly"));
        userTO = createUser(userTO);
        //Finding no values because the virtual attribute is readonly 
        assertTrue(userTO.getVirtualAttributeMap().get("virtualReadOnly").getValues().isEmpty());
    }

    @Test
    public void issueSYNCOPE453() {
        final String resourceName = "issueSYNCOPE453-Res-" + getUUIDString();
        final String roleName = "issueSYNCOPE453-Role-" + getUUIDString();

        // -------------------------------------------
        // Create a resource ad-hoc
        // -------------------------------------------
        final ResourceTO resourceTO = new ResourceTO();

        resourceTO.setName(resourceName);
        resourceTO.setConnectorId(107L);

        MappingTO mapping = new MappingTO();

        MappingItemTO item = new MappingItemTO();
        item.setIntAttrName("aLong");
        item.setIntMappingType(IntMappingType.UserSchema);
        item.setPurpose(MappingPurpose.PROPAGATION);
        item.setAccountid(true);
        mapping.setAccountIdItem(item);

        item = new MappingItemTO();
        item.setExtAttrName("USERNAME");
        item.setIntAttrName("username");
        item.setIntMappingType(IntMappingType.Username);
        item.setPurpose(MappingPurpose.PROPAGATION);
        mapping.getItems().add(item);

        item = new MappingItemTO();
        item.setExtAttrName("EMAIL");
        item.setIntAttrName("rvirtualdata");
        item.setIntMappingType(IntMappingType.RoleVirtualSchema);
        item.setPurpose(MappingPurpose.PROPAGATION);
        mapping.getItems().add(item);

        resourceTO.setUmapping(mapping);
        assertNotNull(getObject(resourceService.create(resourceTO), ResourceTO.class, resourceService));
        // -------------------------------------------

        // -------------------------------------------
        // Create a role ad-hoc
        // -------------------------------------------
        RoleTO roleTO = new RoleTO();
        roleTO.setName(roleName);
        roleTO.setParent(8L);
        roleTO.addVirtualAttribute(attributeTO("rvirtualdata", "ml@role.it"));
        roleTO.getResources().add(RESOURCE_NAME_LDAP);
        roleTO = createRole(roleService, roleTO);
        assertEquals(1, roleTO.getVirtualAttributes().size());
        assertEquals("ml@role.it", roleTO.getVirtualAttributes().get(0).getValues().get(0));
        // -------------------------------------------

        // -------------------------------------------
        // Create new user
        // -------------------------------------------
        UserTO userTO = getUniqueSampleTO("syncope453@syncope.apache.org");
        userTO.addAttribute(attributeTO("aLong", "123"));
        userTO.getResources().clear();
        userTO.getResources().add(resourceName);
        userTO.getVirtualAttributes().clear();
        userTO.getDerivedAttributes().clear();
        userTO.getMemberships().clear();

        final MembershipTO membership = new MembershipTO();
        membership.setRoleId(roleTO.getId());
        membership.getVirtualAttributes().add(attributeTO("mvirtualdata", "mvirtualvalue"));
        userTO.getMemberships().add(membership);

        userTO = createUser(userTO);
        assertEquals(2, userTO.getPropagationStatusTOs().size());
        assertTrue(userTO.getPropagationStatusTOs().get(0).getStatus().isSuccessful());
        assertTrue(userTO.getPropagationStatusTOs().get(1).getStatus().isSuccessful());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(testDataSource);

        final Map<String, Object> actuals = jdbcTemplate.queryForMap(
                "SELECT id, surname, email FROM testsync WHERE id=?",
                new Object[] { Integer.parseInt(userTO.getAttributeMap().get("aLong").getValues().get(0)) });

        assertEquals(userTO.getAttributeMap().get("aLong").getValues().get(0), actuals.get("id").toString());
        assertEquals("ml@role.it", actuals.get("email"));
        // -------------------------------------------

        // -------------------------------------------
        // Delete resource and role ad-hoc
        // -------------------------------------------
        resourceService.delete(resourceName);
        roleService.delete(roleTO.getId());
        // -------------------------------------------
    }

    @Test
    public void issueSYNCOPE459() {
        UserTO userTO = getUniqueSampleTO("syncope459@apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getVirtualAttributes().clear();

        final AttributeTO virtualReadOnly = attributeTO("virtualReadOnly", "");
        virtualReadOnly.getValues().clear();

        userTO.addVirtualAttribute(virtualReadOnly);

        userTO = createUser(userTO);

        assertNotNull(userTO.getVirtualAttributeMap().get("virtualReadOnly"));

        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        AttributeMod virtualdata = new AttributeMod();
        virtualdata.setSchema("virtualdata");

        userMod.addVirtualAttributeToBeUpdated(virtualdata);

        userTO = userService.update(userTO.getId(), userMod);
        assertNotNull(userTO.getVirtualAttributeMap().get("virtualdata"));
    }

    @Test
    public void issueSYNCOPE501() {
        // 1. create user and propagate him on resource-db-virattr
        UserTO userTO = getUniqueSampleTO("syncope501@apache.org");
        userTO.getResources().clear();
        userTO.getMemberships().clear();
        userTO.getVirtualAttributes().clear();
        
        userTO.getResources().add(RESOURCE_NAME_DBVIRATTR);

        // virtualdata is mapped with username
        final AttributeTO virtualData = attributeTO("virtualdata", "syncope501@apache.org");
        userTO.getVirtualAttributes().add(virtualData);

        userTO = createUser(userTO);

        assertNotNull(userTO.getVirtualAttributeMap().get("virtualdata"));
        assertEquals("syncope501@apache.org", userTO.getVirtualAttributeMap().get("virtualdata").getValues().get(0));

        // 2. update virtual attribute
        UserMod userMod = new UserMod();
        userMod.setId(userTO.getId());

        PropagationRequestTO propagationRequestTO = new PropagationRequestTO();
        propagationRequestTO.setResources(Collections.<String>emptySet());
        propagationRequestTO.setOnSyncope(Boolean.FALSE);

        userMod.setPwdPropRequest(propagationRequestTO);

        // change virtual attribute value
        final AttributeMod virtualDataMod = new AttributeMod();
        virtualDataMod.setSchema("virtualdata");
        virtualDataMod.addValueToBeAdded("syncope501_updated@apache.org");
        virtualDataMod.addValueToBeRemoved("syncope501@apache.org");
        userMod.addVirtualAttributeToBeUpdated(virtualDataMod);

        userTO = userService.update(userMod.getId(), userMod);
        assertNotNull(userTO);
        
        // 3. check that user virtual attribute has been really updated 
        assertFalse(userTO.getVirtualAttributeMap().get("virtualdata").getValues().isEmpty());
        assertEquals("syncope501_updated@apache.org", userTO.getVirtualAttributeMap().get("virtualdata").getValues().
                get(0));
    }
}
