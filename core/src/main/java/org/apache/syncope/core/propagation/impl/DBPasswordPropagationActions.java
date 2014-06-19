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
package org.apache.syncope.core.propagation.impl;

import java.util.HashSet;
import java.util.Set;

import org.apache.syncope.common.types.AttributableType;
import org.apache.syncope.core.persistence.beans.PropagationTask;
import org.apache.syncope.core.persistence.beans.user.SyncopeUser;
import org.apache.syncope.core.persistence.dao.UserDAO;
import org.apache.syncope.core.propagation.DefaultPropagationActions;
import org.apache.syncope.core.propagation.PropagationTaskExecutor;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propagate a non-cleartext password out to a resource, if the PropagationManager has not already
 * added a password. 
 */
public class DBPasswordPropagationActions extends DefaultPropagationActions {

    @Autowired
    private UserDAO userDAO;

    @Transactional(readOnly = true)
    @Override
    public void before(final PropagationTask task, final ConnectorObject beforeObj) {
        super.before(task, beforeObj);
        
        if (AttributableType.USER == task.getSubjectType()) {
            SyncopeUser user = userDAO.find(task.getSubjectId());
            if (user != null && user.getPassword() != null) {
                Attribute missing = AttributeUtil.find(
                        PropagationTaskExecutor.MANDATORY_MISSING_ATTR_NAME,
                        task.getAttributes());
                if (missing != null && missing.getValue() != null && missing.getValue().size() == 1
                        && missing.getValue().get(0).equals(OperationalAttributes.PASSWORD_NAME)) {

                    Attribute passwordAttribute = AttributeBuilder.buildPassword(
                            new GuardedString(user.getPassword().toCharArray()));

                    Set<Attribute> attributes = new HashSet<Attribute>(task.getAttributes());
                    attributes.add(passwordAttribute);
                    attributes.remove(missing);
                    
                    Attribute hashedPasswordAttribute = 
                        AttributeBuilder.build(
                            AttributeUtil.createSpecialName("HASHED_PASSWORD"), Boolean.TRUE);
                    attributes.add(hashedPasswordAttribute);
                    
                    task.setAttributes(attributes);
                }
            }
        }
    }
    
}
