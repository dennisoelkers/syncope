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
import java.util.List;
import java.util.Set;
import javax.persistence.EntityExistsException;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.syncope.common.to.BulkAction;
import org.apache.syncope.common.to.BulkActionRes;
import org.apache.syncope.common.to.ConnObjectTO;
import org.apache.syncope.common.to.ResourceTO;
import org.apache.syncope.common.types.AttributableType;
import org.apache.syncope.common.types.MappingPurpose;
import org.apache.syncope.common.types.SyncopeClientExceptionType;
import org.apache.syncope.common.validation.SyncopeClientCompositeErrorException;
import org.apache.syncope.common.validation.SyncopeClientException;
import org.apache.syncope.core.connid.ConnObjectUtil;
import org.apache.syncope.core.init.ImplementationClassNamesLoader;
import org.apache.syncope.core.persistence.beans.AbstractAttributable;
import org.apache.syncope.core.persistence.beans.AbstractMappingItem;
import org.apache.syncope.core.persistence.beans.ConnInstance;
import org.apache.syncope.core.persistence.beans.ExternalResource;
import org.apache.syncope.core.persistence.dao.ConnInstanceDAO;
import org.apache.syncope.core.persistence.dao.NotFoundException;
import org.apache.syncope.core.persistence.dao.ResourceDAO;
import org.apache.syncope.core.persistence.dao.RoleDAO;
import org.apache.syncope.core.persistence.dao.UserDAO;
import org.apache.syncope.core.propagation.ConnectorFactory;
import org.apache.syncope.core.propagation.Connector;
import org.apache.syncope.core.rest.data.ResourceDataBinder;
import org.apache.syncope.core.util.AttributableUtil;
import org.apache.syncope.core.util.MappingUtil;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/resource")
public class ResourceController extends AbstractTransactionalController<ResourceTO> {

    @Autowired
    private ResourceDAO resourceDAO;

    @Autowired
    private ConnInstanceDAO connInstanceDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private RoleDAO roleDAO;

    @Autowired
    private ResourceDataBinder binder;

    @Autowired
    private ImplementationClassNamesLoader classNamesLoader;

    /**
     * ConnectorObject util.
     */
    @Autowired
    private ConnObjectUtil connObjectUtil;

    @Autowired
    private ConnectorFactory connFactory;

    @PreAuthorize("hasRole('RESOURCE_CREATE')")
    @RequestMapping(method = RequestMethod.POST, value = "/create")
    public ResourceTO create(final HttpServletResponse response, @RequestBody final ResourceTO resourceTO) {
        if (StringUtils.isBlank(resourceTO.getName())) {
            SyncopeClientCompositeErrorException sccee =
                    new SyncopeClientCompositeErrorException(HttpStatus.BAD_REQUEST);
            SyncopeClientException sce = new SyncopeClientException(SyncopeClientExceptionType.RequiredValuesMissing);
            sce.addElement("Resource name");
            sccee.addException(sce);
            throw sccee;
        }

        if (resourceDAO.find(resourceTO.getName()) != null) {
            throw new EntityExistsException("Resource '" + resourceTO.getName() + "'");
        }

        ExternalResource resource = resourceDAO.save(binder.create(resourceTO));

        response.setStatus(HttpServletResponse.SC_CREATED);
        return binder.getResourceTO(resource);
    }

    @PreAuthorize("hasRole('RESOURCE_UPDATE')")
    @RequestMapping(method = RequestMethod.POST, value = "/update")
    public ResourceTO update(@RequestBody final ResourceTO resourceTO) {
        ExternalResource resource = resourceDAO.find(resourceTO.getName());
        if (resource == null) {
            throw new NotFoundException("Resource '" + resourceTO.getName() + "'");
        }

        resource = binder.update(resource, resourceTO);
        resource = resourceDAO.save(resource);

        return binder.getResourceTO(resource);
    }

    @PreAuthorize("hasRole('RESOURCE_DELETE')")
    @RequestMapping(method = RequestMethod.GET, value = "/delete/{resourceName}")
    public ResourceTO delete(@PathVariable("resourceName") final String resourceName) {
        ExternalResource resource = resourceDAO.find(resourceName);
        if (resource == null) {
            throw new NotFoundException("Resource '" + resourceName + "'");
        }

        ResourceTO resourceToDelete = binder.getResourceTO(resource);

        resourceDAO.delete(resourceName);

        return resourceToDelete;
    }

    @PreAuthorize("hasRole('RESOURCE_READ')")
    @Transactional(readOnly = true)
    @RequestMapping(method = RequestMethod.GET, value = "/read/{resourceName}")
    public ResourceTO read(@PathVariable("resourceName") final String resourceName) {
        ExternalResource resource = resourceDAO.find(resourceName);
        if (resource == null) {
            throw new NotFoundException("Resource '" + resourceName + "'");
        }

        return binder.getResourceTO(resource);
    }

    @PreAuthorize("hasRole('RESOURCE_READ')")
    @RequestMapping(method = RequestMethod.GET, value = "/propagationActionsClasses")
    public ModelAndView getPropagationActionsClasses() {
        Set<String> actionsClasses = classNamesLoader.getClassNames(
                ImplementationClassNamesLoader.Type.PROPAGATION_ACTIONS);

        return new ModelAndView().addObject(actionsClasses);
    }

    @Transactional(readOnly = true)
    @RequestMapping(method = RequestMethod.GET, value = "/list")
    public List<ResourceTO> list(@RequestParam(required = false, value = "connInstanceId") final Long connInstanceId) {
        List<ExternalResource> resources;

        if (connInstanceId == null) {
            resources = resourceDAO.findAll();
        } else {
            ConnInstance connInstance = connInstanceDAO.find(connInstanceId);
            resources = connInstance.getResources();
        }

        List<ResourceTO> result = binder.getResourceTOs(resources);

        return result;
    }

    @PreAuthorize("hasRole('RESOURCE_GETCONNECTOROBJECT')")
    @Transactional(readOnly = true)
    @RequestMapping(method = RequestMethod.GET, value = "/{resourceName}/read/{type}/{id}")
    public ConnObjectTO getConnectorObject(@PathVariable("resourceName") final String resourceName,
            @PathVariable("type") final AttributableType type, @PathVariable("id") final Long id) {

        ExternalResource resource = resourceDAO.find(resourceName);
        if (resource == null) {
            throw new NotFoundException("Resource '" + resourceName + "'");
        }

        AbstractAttributable attributable = null;
        switch (type) {
            case USER:
                attributable = userDAO.find(id);
                break;

            case ROLE:
                attributable = roleDAO.find(id);
                break;

            case MEMBERSHIP:
            default:
                throw new IllegalArgumentException("Not supported for MEMBERSHIP");
        }
        if (attributable == null) {
            throw new NotFoundException(type + " " + id);
        }

        final AttributableUtil attrUtil = AttributableUtil.getInstance(type);

        AbstractMappingItem accountIdItem = attrUtil.getAccountIdItem(resource);
        if (accountIdItem == null) {
            throw new NotFoundException("AccountId mapping for " + type + " " + id + " on resource '" + resourceName
                    + "'");
        }
        final String accountIdValue =
                MappingUtil.getAccountIdValue(attributable, resource, attrUtil.getAccountIdItem(resource));

        final ObjectClass objectClass = AttributableType.USER == type ? ObjectClass.ACCOUNT : ObjectClass.GROUP;

        final Connector connector = connFactory.getConnector(resource);
        final ConnectorObject connectorObject = connector.getObject(objectClass, new Uid(accountIdValue),
                connector.getOperationOptions(attrUtil.getMappingItems(resource, MappingPurpose.BOTH)));
        if (connectorObject == null) {
            throw new NotFoundException("Object " + accountIdValue + " with class " + objectClass
                    + "not found on resource " + resourceName);
        }

        final Set<Attribute> attributes = connectorObject.getAttributes();
        if (AttributeUtil.find(Uid.NAME, attributes) == null) {
            attributes.add(connectorObject.getUid());
        }
        if (AttributeUtil.find(Name.NAME, attributes) == null) {
            attributes.add(connectorObject.getName());
        }

        return connObjectUtil.getConnObjectTO(connectorObject);
    }

    @PreAuthorize("hasRole('CONNECTOR_READ')")
    @RequestMapping(method = RequestMethod.POST, value = "/check")
    @Transactional(readOnly = true)
    public ModelAndView check(@RequestBody final ResourceTO resourceTO) {
        final ConnInstance connInstance = binder.getConnInstance(resourceTO);

        final Connector connector = connFactory.createConnector(connInstance, connInstance.getConfiguration());

        boolean result;
        try {
            connector.test();
            result = true;
        } catch (Exception e) {
            LOG.error("Test connection failure {}", e);
            result = false;
        }

        return new ModelAndView().addObject(result);
    }

    @PreAuthorize("hasRole('RESOURCE_DELETE') and #bulkAction.operation == #bulkAction.operation.DELETE")
    @RequestMapping(method = RequestMethod.POST, value = "/bulk")
    public BulkActionRes bulkAction(@RequestBody final BulkAction bulkAction) {
        BulkActionRes res = new BulkActionRes();

        switch (bulkAction.getOperation()) {
            case DELETE:
                for (String name : bulkAction.getTargets()) {
                    try {
                        res.add(delete(name).getName(), BulkActionRes.Status.SUCCESS);
                    } catch (Exception e) {
                        LOG.error("Error performing delete for resource {}", name, e);
                        res.add(name, BulkActionRes.Status.FAILURE);
                    }
                }
                break;
            default:
        }

        return res;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResourceTO resolveReference(final Method method, final Object... args)
            throws UnresolvedReferenceException {

        String name = null;

        if (ArrayUtils.isNotEmpty(args)) {
            for (int i = 0; name == null && i < args.length; i++) {
                if (args[i] instanceof String) {
                    name = (String) args[i];
                } else if (args[i] instanceof ResourceTO) {
                    name = ((ResourceTO) args[i]).getName();
                }
            }
        }

        if (name != null) {
            try {
                return binder.getResourceTO(resourceDAO.find(name));
            } catch (Throwable ignore) {
                LOG.debug("Unresolved reference", ignore);
                throw new UnresolvedReferenceException(ignore);
            }
        }

        throw new UnresolvedReferenceException();
    }
}
