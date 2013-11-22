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
package org.apache.syncope.core.rest.controller;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityExistsException;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.syncope.common.to.VirtualSchemaTO;
import org.apache.syncope.common.types.SyncopeClientExceptionType;
import org.apache.syncope.common.validation.SyncopeClientCompositeErrorException;
import org.apache.syncope.common.validation.SyncopeClientException;
import org.apache.syncope.core.persistence.beans.AbstractVirSchema;
import org.apache.syncope.core.persistence.dao.NotFoundException;
import org.apache.syncope.core.persistence.dao.VirSchemaDAO;
import org.apache.syncope.core.rest.data.VirtualSchemaDataBinder;
import org.apache.syncope.core.util.AttributableUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/virtualSchema")
public class VirtualSchemaController extends AbstractTransactionalController<VirtualSchemaTO> {

    @Autowired
    private VirSchemaDAO virSchemaDAO;

    @Autowired
    private VirtualSchemaDataBinder binder;

    @PreAuthorize("hasRole('SCHEMA_CREATE')")
    @RequestMapping(method = RequestMethod.POST, value = "/{kind}/create")
    public VirtualSchemaTO create(final HttpServletResponse response,
            @RequestBody final VirtualSchemaTO virSchemaTO, @PathVariable("kind") final String kind) {

        if (StringUtils.isBlank(virSchemaTO.getName())) {
            SyncopeClientCompositeErrorException sccee =
                    new SyncopeClientCompositeErrorException(HttpStatus.BAD_REQUEST);
            SyncopeClientException sce = new SyncopeClientException(SyncopeClientExceptionType.RequiredValuesMissing);
            sce.addElement("Virtual schema name");
            sccee.addException(sce);
            throw sccee;
        }

        AttributableUtil attrUtil = getAttributableUtil(kind);

        if (virSchemaDAO.find(virSchemaTO.getName(), attrUtil.virSchemaClass()) != null) {
            throw new EntityExistsException(attrUtil.schemaClass().getSimpleName()
                    + " '" + virSchemaTO.getName() + "'");
        }

        AbstractVirSchema virSchema = virSchemaDAO.save(binder.create(virSchemaTO, attrUtil.newVirSchema()));
        response.setStatus(HttpServletResponse.SC_CREATED);
        return binder.getVirtualSchemaTO(virSchema);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{kind}/list")
    public List<VirtualSchemaTO> list(@PathVariable("kind") final String kind) {
        Class<? extends AbstractVirSchema> reference = getAttributableUtil(kind).virSchemaClass();
        List<? extends AbstractVirSchema> virAttrSchemas = virSchemaDAO.findAll(reference);

        List<VirtualSchemaTO> virtualSchemaTOs = new ArrayList<VirtualSchemaTO>(virAttrSchemas.size());
        for (AbstractVirSchema virSchema : virSchemaDAO.findAll(reference)) {
            virtualSchemaTOs.add(binder.getVirtualSchemaTO(virSchema));
        }
        return virtualSchemaTOs;
    }

    @PreAuthorize("hasRole('SCHEMA_READ')")
    @RequestMapping(method = RequestMethod.GET, value = "/{kind}/read/{virtualSchema}")
    public VirtualSchemaTO read(@PathVariable("kind") final String kind,
            @PathVariable("virtualSchema") final String virtualSchemaName) {

        Class<? extends AbstractVirSchema> reference = getAttributableUtil(kind).virSchemaClass();
        AbstractVirSchema virtualSchema = virSchemaDAO.find(virtualSchemaName, reference);
        if (virtualSchema == null) {
            throw new NotFoundException("Virtual schema '" + virtualSchemaName + "'");
        }
        return binder.getVirtualSchemaTO(virtualSchema);
    }

    @PreAuthorize("hasRole('SCHEMA_UPDATE')")
    @RequestMapping(method = RequestMethod.POST, value = "/{kind}/update")
    public VirtualSchemaTO update(@RequestBody final VirtualSchemaTO virtualSchemaTO,
            @PathVariable("kind") final String kind) {

        Class<? extends AbstractVirSchema> reference = getAttributableUtil(kind).virSchemaClass();
        AbstractVirSchema virtualSchema = virSchemaDAO.find(virtualSchemaTO.getName(), reference);
        if (virtualSchema == null) {
            throw new NotFoundException("Virtual schema is null");
        }

        virtualSchema = binder.update(virtualSchemaTO, virtualSchema);
        return binder.getVirtualSchemaTO(virSchemaDAO.save(virtualSchema));
    }

    @PreAuthorize("hasRole('SCHEMA_DELETE')")
    @RequestMapping(method = RequestMethod.GET, value = "/{kind}/delete/{schema}")
    public VirtualSchemaTO delete(@PathVariable("kind") final String kind,
            @PathVariable("schema") final String virtualSchemaName) {

        Class<? extends AbstractVirSchema> reference = getAttributableUtil(kind).virSchemaClass();
        AbstractVirSchema virSchema = virSchemaDAO.find(virtualSchemaName, reference);
        if (virSchema == null) {
            throw new NotFoundException("Virtual schema '" + virtualSchemaName + "'");
        }

        VirtualSchemaTO schemaToDelete = binder.getVirtualSchemaTO(virSchema);
        virSchemaDAO.delete(virtualSchemaName, getAttributableUtil(kind));
        return schemaToDelete;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected VirtualSchemaTO resolveReference(final Method method, final Object... args)
            throws UnresolvedReferenceException {

        String kind = null;
        String name = null;

        if (ArrayUtils.isNotEmpty(args)) {
            for (int i = 0; (name == null || kind == null) && i < args.length; i++) {
                if (args[i] instanceof String) {
                    if (kind == null) {
                        kind = (String) args[i];
                    } else {
                        name = (String) args[i];
                    }
                } else if (args[i] instanceof VirtualSchemaTO) {
                    name = ((VirtualSchemaTO) args[i]).getName();
                }
            }
        }

        if (name != null) {
            try {
                return binder.getVirtualSchemaTO(virSchemaDAO.find(name, getAttributableUtil(kind).virSchemaClass()));
            } catch (Throwable ignore) {
                LOG.debug("Unresolved reference", ignore);
                throw new UnresolvedReferenceException(ignore);
            }
        }

        throw new UnresolvedReferenceException();
    }
}
