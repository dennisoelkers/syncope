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
package org.apache.syncope.core.persistence.beans.role;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import org.apache.syncope.core.persistence.beans.AbstractAttributable;
import org.apache.syncope.core.persistence.beans.AbstractDerAttr;
import org.apache.syncope.core.persistence.beans.AbstractDerSchema;

@Entity
public class RDerAttr extends AbstractDerAttr {

    private static final long serialVersionUID = 8007080005675899946L;

    @ManyToOne
    private SyncopeRole owner;

    @Column(nullable = false)
    @OneToOne(cascade = CascadeType.MERGE)
    private RDerAttrTemplate template;

    @SuppressWarnings("unchecked")
    @Override
    public <T extends AbstractAttributable> T getOwner() {
        return (T) owner;
    }

    @Override
    public <T extends AbstractAttributable> void setOwner(final T owner) {
        if (!(owner instanceof SyncopeRole)) {
            throw new ClassCastException("expected type SyncopeRole, found: " + owner.getClass().getName());
        }

        this.owner = (SyncopeRole) owner;
    }

    public RDerAttrTemplate getTemplate() {
        return template;
    }

    public void setTemplate(final RDerAttrTemplate template) {
        this.template = template;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends AbstractDerSchema> T getSchema() {
        return template == null ? null : (T) template.getSchema();
    }
}
