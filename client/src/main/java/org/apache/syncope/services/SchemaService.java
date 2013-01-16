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
package org.apache.syncope.services;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.syncope.client.to.AbstractSchemaTO;
import org.apache.syncope.types.AttributableType;

@Path("schemas/{kind}/{type}")
public interface SchemaService {

    enum SchemaType {

        NORMAL("schema"),
        DERIVED("derivedSchema"),
        VIRTUAL("virtualSchema");

        private final String name;

        private SchemaType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @POST
    <T extends AbstractSchemaTO> T create(@PathParam("kind") AttributableType kind,
            @PathParam("type") SchemaType type,
            T schemaTO);

    @DELETE
    @Path("{name}")
    <T extends AbstractSchemaTO> T delete(@PathParam("kind") AttributableType kind,
            @PathParam("type") SchemaType type,
            @PathParam("name") String schemaName);

    @GET
    <T extends AbstractSchemaTO> List<T> list(@PathParam("kind") AttributableType kind,
            @PathParam("type") SchemaType type);

    @GET
    @Path("{name}")
    <T extends AbstractSchemaTO> T read(@PathParam("kind") AttributableType kind,
            @PathParam("type") SchemaType type,
            @PathParam("name") String schemaName);

    @PUT
    @Path("{name}")
    <T extends AbstractSchemaTO> T update(@PathParam("kind") AttributableType kind,
            @PathParam("type") SchemaType type,
            @PathParam("name") String schemaName,
            T schemaTO);
}