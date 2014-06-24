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
package org.apache.syncope.core.persistence.beans.conf;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import org.apache.syncope.core.persistence.beans.AbstractAttr;
import org.apache.syncope.core.persistence.beans.AbstractAttrValue;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class CAttrValue extends AbstractAttrValue {

    private static final long serialVersionUID = -6259576015647897446L;

    @Id
    private Long id;

    @ManyToOne
    @NotNull
    private CAttr attribute;

    @Override
    public Long getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends AbstractAttr> T getAttribute() {
        return (T) attribute;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends AbstractAttr> void setAttribute(final T attribute) {
        if (!(attribute instanceof CAttr)) {
            throw new ClassCastException("expected type CAttr, found: " + attribute.getClass().getName());
        }
        this.attribute = (CAttr) attribute;
    }
}
