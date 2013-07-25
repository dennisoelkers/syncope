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
package org.apache.syncope.core.persistence.relationships;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.syncope.common.types.AttributableType;
import org.apache.syncope.common.types.AttributeSchemaType;
import org.apache.syncope.core.persistence.beans.AbstractSchema;
import org.apache.syncope.core.persistence.beans.membership.MAttr;
import org.apache.syncope.core.persistence.beans.membership.MSchema;
import org.apache.syncope.core.persistence.beans.membership.Membership;
import org.apache.syncope.core.persistence.beans.user.SyncopeUser;
import org.apache.syncope.core.persistence.beans.user.UAttr;
import org.apache.syncope.core.persistence.beans.user.UAttrValue;
import org.apache.syncope.core.persistence.beans.user.UDerAttr;
import org.apache.syncope.core.persistence.beans.user.UDerSchema;
import org.apache.syncope.core.persistence.dao.AbstractDAOTest;
import org.apache.syncope.core.persistence.dao.AttrDAO;
import org.apache.syncope.core.persistence.dao.AttrValueDAO;
import org.apache.syncope.core.persistence.dao.DerAttrDAO;
import org.apache.syncope.core.persistence.dao.DerSchemaDAO;
import org.apache.syncope.core.persistence.dao.MembershipDAO;
import org.apache.syncope.core.persistence.dao.SchemaDAO;
import org.apache.syncope.core.persistence.dao.UserDAO;
import org.apache.syncope.core.util.AttributableUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class AttrTest extends AbstractDAOTest {

    @Autowired
    private AttrDAO attrDAO;

    @Autowired
    private DerAttrDAO derAttrDAO;

    @Autowired
    private AttrValueDAO attrValueDAO;

    @Autowired
    private SchemaDAO schemaDAO;

    @Autowired
    private DerSchemaDAO derSchemaDAO;

    @Autowired
    private MembershipDAO membershipDAO;

    @Autowired
    private UserDAO userDAO;

    @Test
    public void deleteAttribute() {
        attrDAO.delete(117L, UAttr.class);

        attrDAO.flush();

        assertNull(attrDAO.find(117L, UAttr.class));
        assertNull(attrValueDAO.find(28L, UAttrValue.class));
    }

    @Test
    public void deleteAttributeValue() {
        UAttrValue value = attrValueDAO.find(14L, UAttrValue.class);
        int attributeValueNumber = value.getAttribute().getValues().size();

        attrValueDAO.delete(value.getId(), UAttrValue.class);

        attrValueDAO.flush();

        assertNull(attrValueDAO.find(value.getId(), UAttrValue.class));

        UAttr attribute = attrDAO.find(104L, UAttr.class);
        assertEquals(attribute.getValues().size(), attributeValueNumber - 1);
    }

    @Test
    public void checkForEnumType() {
        MSchema schema = new MSchema();
        schema.setType(AttributeSchemaType.Enum);
        schema.setName("color");
        schema.setEnumerationValues("red" + AbstractSchema.enumValuesSeparator + "yellow");

        MSchema actualSchema = schemaDAO.save(schema);
        assertNotNull(actualSchema);

        Membership membership = membershipDAO.find(1L);
        assertNotNull(membership);

        MAttr attribute = new MAttr();
        attribute.setSchema(actualSchema);
        attribute.setOwner(membership);
        attribute.addValue("yellow", AttributableUtil.getInstance(AttributableType.MEMBERSHIP));
        membership.addAttribute(attribute);

        MAttr actualAttribute = attrDAO.save(attribute);
        assertNotNull(actualAttribute);

        membership = membershipDAO.find(1L);
        assertNotNull(membership);
        assertNotNull(membership.getAttribute(schema.getName()));
        assertNotNull(membership.getAttribute(schema.getName()).getValues());

        assertEquals(membership.getAttribute(schema.getName()).getValues().size(), 1);
    }

    @Test
    public void derAttrFromSpecialAttrs() {
        UDerSchema sderived = new UDerSchema();
        sderived.setName("sderived");
        sderived.setExpression("username + ' - ' + creationDate + '[' + failedLogins + ']'");

        sderived = derSchemaDAO.save(sderived);
        derSchemaDAO.flush();

        UDerSchema actual = derSchemaDAO.find("sderived", UDerSchema.class);
        assertNotNull("expected save to work", actual);
        assertEquals(sderived, actual);

        SyncopeUser owner = userDAO.find(3L);
        assertNotNull("did not get expected user", owner);

        UDerAttr derAttr = new UDerAttr();
        derAttr.setOwner(owner);
        derAttr.setDerivedSchema(sderived);

        derAttr = derAttrDAO.save(derAttr);
        derAttrDAO.flush();

        derAttr = derAttrDAO.find(derAttr.getId(), UDerAttr.class);
        assertNotNull("expected save to work", derAttr);

        String value = derAttr.getValue(owner.getAttributes());
        assertNotNull(value);
        assertFalse(value.isEmpty());
        assertTrue(value.startsWith("vivaldi - 2010-10-20"));
        assertTrue(value.endsWith("[0]"));
    }
}
