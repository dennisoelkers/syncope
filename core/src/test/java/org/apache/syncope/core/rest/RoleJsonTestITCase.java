package org.apache.syncope.core.rest;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.syncope.services.RoleService;

public class RoleJsonTestITCase extends AbstractRoleTestITCase {

    @Override
    public void setupService() {
        super.rs = createServiceInstance(RoleService.class);
        setupJSON(WebClient.client(this.rs));
    }
}
