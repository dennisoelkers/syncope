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
import static org.junit.Assert.fail;

import java.util.List;

import org.apache.syncope.common.to.VirtualSchemaTO;
import org.apache.syncope.common.types.AttributableType;
import org.apache.syncope.common.types.EntityViolationType;
import org.apache.syncope.common.types.SchemaType;
import org.apache.syncope.common.types.SyncopeClientExceptionType;
import org.apache.syncope.common.validation.SyncopeClientCompositeErrorException;
import org.apache.syncope.common.validation.SyncopeClientException;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.http.HttpStatus;

@FixMethodOrder(MethodSorters.JVM)
public class VirtualSchemaTestITCase extends AbstractTest {

    @Test
    public void list() {
        @SuppressWarnings("unchecked")
        List<VirtualSchemaTO> vSchemas = (List<VirtualSchemaTO>) schemaService.list(AttributableType.USER,
                SchemaType.VIRTUAL);
        assertFalse(vSchemas.isEmpty());
        for (VirtualSchemaTO vSchemaTO : vSchemas) {
            assertNotNull(vSchemaTO);
        }
    }

    @Test
    public void read() {
        VirtualSchemaTO vSchemaTO = schemaService.read(AttributableType.MEMBERSHIP, SchemaType.VIRTUAL,
                "mvirtualdata");
        assertNotNull(vSchemaTO);
    }

    @Test
    public void create() {
        VirtualSchemaTO schema = new VirtualSchemaTO();
        schema.setName("virtual");

        VirtualSchemaTO actual = createSchema(AttributableType.USER, SchemaType.VIRTUAL, schema);
        assertNotNull(actual);

        actual = schemaService.read(AttributableType.USER, SchemaType.VIRTUAL, actual.getName());
        assertNotNull(actual);
    }

    @Test
    public void delete() {
        VirtualSchemaTO schema = schemaService.read(AttributableType.ROLE, SchemaType.VIRTUAL,
                "rvirtualdata");
        assertNotNull(schema);

        schemaService.delete(AttributableType.ROLE, SchemaType.VIRTUAL,
                schema.getName());

        Throwable t = null;
        try {
            schemaService.read(AttributableType.ROLE, SchemaType.VIRTUAL, "rvirtualdata");
        } catch (SyncopeClientCompositeErrorException e) {
            t = e;
            assertNotNull(e.getException(SyncopeClientExceptionType.NotFound));
        }
        assertNotNull(t);
    }

    @Test
    public void issueSYNCOPE323() {
        VirtualSchemaTO actual = schemaService.read(AttributableType.MEMBERSHIP, SchemaType.VIRTUAL, "mvirtualdata");
        assertNotNull(actual);

        try {
            createSchema(AttributableType.MEMBERSHIP, SchemaType.VIRTUAL, actual);
            fail();
        } catch (SyncopeClientCompositeErrorException scce) {
            assertEquals(HttpStatus.CONFLICT, scce.getStatusCode());
            assertTrue(scce.hasException(SyncopeClientExceptionType.EntityExists));
        }

        actual.setName(null);
        try {
            createSchema(AttributableType.MEMBERSHIP, SchemaType.VIRTUAL, actual);
            fail();
        } catch (SyncopeClientCompositeErrorException scce) {
            assertEquals(HttpStatus.BAD_REQUEST, scce.getStatusCode());
            assertTrue(scce.hasException(SyncopeClientExceptionType.RequiredValuesMissing));
        }
    }

    @Test
    public void issueSYNCOPE418() {
        VirtualSchemaTO schema = new VirtualSchemaTO();
        schema.setName("http://schemas.examples.org/security/authorization/organizationUnit");

        try {
            createSchema(AttributableType.MEMBERSHIP, SchemaType.VIRTUAL, schema);
            fail();
        } catch (SyncopeClientCompositeErrorException scce) {
            SyncopeClientException sce = scce.getException(SyncopeClientExceptionType.InvalidMVirSchema);

            assertNotNull(sce.getElements());
            assertEquals(1, sce.getElements().size());
            assertTrue(sce.getElements().iterator().next().contains(EntityViolationType.InvalidName.name()));
        }
    }
}
