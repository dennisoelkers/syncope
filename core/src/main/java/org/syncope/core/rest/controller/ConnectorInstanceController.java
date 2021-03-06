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
package org.syncope.core.rest.controller;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import java.util.List;
import javassist.NotFoundException;
import javax.servlet.http.HttpServletResponse;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorKey;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;
import org.syncope.client.to.ConnectorBundleTO;
import org.syncope.client.to.ConnectorInstanceTO;
import org.syncope.client.validation.SyncopeClientCompositeErrorException;
import org.syncope.core.persistence.ConnectorInstanceLoader;
import org.syncope.core.persistence.beans.ConnectorInstance;
import org.syncope.core.persistence.dao.ConnectorInstanceDAO;
import org.syncope.core.persistence.dao.MissingConfKeyException;
import org.syncope.core.persistence.propagation.ConnectorFacadeProxy;
import org.syncope.core.rest.data.ConnectorInstanceDataBinder;

@Controller
@RequestMapping("/connector")
public class ConnectorInstanceController extends AbstractController {

    @Autowired
    private ConnectorInstanceDAO connectorInstanceDAO;

    @Autowired
    private ConnectorInstanceDataBinder binder;

    @PreAuthorize("hasRole('CONNECTOR_CREATE')")
    @RequestMapping(method = RequestMethod.POST,
    value = "/create")
    public ConnectorInstanceTO create(final HttpServletResponse response,
            @RequestBody final ConnectorInstanceTO connectorTO)
            throws SyncopeClientCompositeErrorException, NotFoundException,
            MissingConfKeyException {

        LOG.debug("ConnectorInstance create called with configuration {}",
                connectorTO);

        ConnectorInstance connectorInstance = null;
        try {
            connectorInstance = binder.getConnectorInstance(connectorTO);
        } catch (SyncopeClientCompositeErrorException e) {
            LOG.error("Could not create for " + connectorTO, e);

            throw e;
        }

        // Everything went out fine, we can flush to the database
        // and register the new connector instance for later usage
        connectorInstance = connectorInstanceDAO.save(connectorInstance);

        response.setStatus(HttpServletResponse.SC_CREATED);
        return binder.getConnectorInstanceTO(connectorInstance);
    }

    @PreAuthorize("hasRole('CONNECTOR_UPDATE')")
    @RequestMapping(method = RequestMethod.POST,
    value = "/update")
    public ConnectorInstanceTO update(
            @RequestBody final ConnectorInstanceTO connectorTO)
            throws SyncopeClientCompositeErrorException, NotFoundException,
            MissingConfKeyException {

        LOG.debug("Connector update called with configuration {}", connectorTO);

        ConnectorInstance connectorInstance;
        try {
            connectorInstance = binder.updateConnectorInstance(
                    connectorTO.getId(), connectorTO);
        } catch (SyncopeClientCompositeErrorException e) {
            LOG.error("Could not create for " + connectorTO, e);

            throw e;
        }

        // Everything went out fine, we can flush to the database
        // and register the new connector instance for later usage
        connectorInstance = connectorInstanceDAO.save(connectorInstance);

        return binder.getConnectorInstanceTO(connectorInstance);
    }

    @PreAuthorize("hasRole('CONNECTOR_DELETE')")
    @RequestMapping(method = RequestMethod.DELETE,
    value = "/delete/{connectorId}")
    public void delete(@PathVariable("connectorId") Long connectorId)
            throws NotFoundException {

        ConnectorInstance connectorInstance =
                connectorInstanceDAO.find(connectorId);

        if (connectorInstance == null) {
            LOG.error("Could not find connector '" + connectorId + "'");

            throw new NotFoundException(String.valueOf(connectorId));
        }

        connectorInstanceDAO.delete(connectorId);
    }

    @PreAuthorize("hasRole('CONNECTOR_LIST')")
    @RequestMapping(method = RequestMethod.GET,
    value = "/list")
    public List<ConnectorInstanceTO> list() {
        List<ConnectorInstance> connectorInstances =
                connectorInstanceDAO.findAll();

        List<ConnectorInstanceTO> connectorInstanceTOs =
                new ArrayList<ConnectorInstanceTO>();
        for (ConnectorInstance connector : connectorInstances) {
            connectorInstanceTOs.add(binder.getConnectorInstanceTO(connector));
        }

        return connectorInstanceTOs;
    }

    @PreAuthorize("hasRole('CONNECTOR_READ')")
    @RequestMapping(method = RequestMethod.GET,
    value = "/read/{connectorId}")
    public ConnectorInstanceTO read(
            @PathVariable("connectorId") Long connectorId)
            throws NotFoundException {

        ConnectorInstance connectorInstance =
                connectorInstanceDAO.find(connectorId);

        if (connectorInstance == null) {
            LOG.error("Could not find connector '" + connectorId + "'");

            throw new NotFoundException(String.valueOf(connectorId));
        }

        return binder.getConnectorInstanceTO(connectorInstance);
    }

    @PreAuthorize("hasRole('CONNECTOR_READ')")
    @RequestMapping(method = RequestMethod.GET,
    value = "/check/{connectorId}")
    public ModelAndView check(@PathVariable("connectorId") String connectorId) {
        ConnectorFacadeProxy connector =
                ConnectorInstanceLoader.getConnector(connectorId);

        ModelAndView mav = new ModelAndView();

        Boolean verify = Boolean.FALSE;
        try {
            if (connector != null) {
                connector.validate();
                verify = Boolean.TRUE;
            }
        } catch (RuntimeException ignore) {
            LOG.warn("Connector validation failed", ignore);
        }

        mav.addObject(verify);

        return mav;
    }

    @PreAuthorize("hasRole('CONNECTOR_READ')")
    @RequestMapping(method = RequestMethod.GET,
    value = "/getBundles")
    public List<ConnectorBundleTO> getBundles()
            throws NotFoundException, MissingConfKeyException {

        ConnectorInfoManager manager =
                ConnectorInstanceLoader.getConnectorManager();

        List<ConnectorInfo> bundles = manager.getConnectorInfos();

        if (bundles != null) {
            LOG.debug("#Bundles: {}", bundles.size());

            for (ConnectorInfo bundle : bundles) {
                LOG.debug("Bundle: {}", bundle.getConnectorDisplayName());
            }
        }

        ConnectorBundleTO connectorBundleTO;
        ConnectorKey key;
        ConfigurationProperties properties;

        List<ConnectorBundleTO> connectorBundleTOs =
                new ArrayList<ConnectorBundleTO>();
        for (ConnectorInfo bundle : bundles) {
            connectorBundleTO = new ConnectorBundleTO();
            connectorBundleTO.setDisplayName(bundle.getConnectorDisplayName());

            key = bundle.getConnectorKey();

            LOG.debug("\nBundle name: {}"
                    + "\nBundle version: {}"
                    + "\nBundle class: {}",
                    new Object[]{
                        key.getBundleName(),
                        key.getBundleVersion(),
                        key.getConnectorName()});

            connectorBundleTO.setBundleName(key.getBundleName());
            connectorBundleTO.setConnectorName(key.getConnectorName());
            connectorBundleTO.setVersion(key.getBundleVersion());

            properties = bundle.createDefaultAPIConfiguration().
                    getConfigurationProperties();

            connectorBundleTO.setProperties(properties.getPropertyNames());

            LOG.debug("Bundle properties: {}",
                    connectorBundleTO.getProperties());

            connectorBundleTOs.add(connectorBundleTO);
        }

        return connectorBundleTOs;
    }
}
