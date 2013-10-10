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
package org.apache.syncope.core.persistence.dao.impl;

import org.apache.syncope.core.persistence.beans.AbstractAttr;
import org.apache.syncope.core.persistence.dao.AttrDAO;
import org.springframework.stereotype.Repository;

@Repository
public class AttrDAOImpl extends AbstractDAOImpl implements AttrDAO {

    @Override
    public <T extends AbstractAttr> T find(final Long id, final Class<T> reference) {
        return entityManager.find(reference, id);
    }

    @Override
    public <T extends AbstractAttr> void delete(final Long id, final Class<T> reference) {
        T attribute = find(id, reference);
        if (attribute == null) {
            return;
        }

        delete(attribute);
    }

    @Override
    public <T extends AbstractAttr> void delete(final T attribute) {
        if (attribute.getOwner() != null) {
            attribute.getOwner().removeAttr(attribute);
        }

        entityManager.remove(attribute);
    }
}
