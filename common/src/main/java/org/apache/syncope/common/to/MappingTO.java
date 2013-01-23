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
package org.apache.syncope.common.to;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.syncope.common.AbstractBaseBean;
import org.apache.syncope.common.types.IntMappingType;

public class MappingTO extends AbstractBaseBean {

    private static final long serialVersionUID = 8447688036282611118L;

    private String accountLink;

    private List<MappingItemTO> items;

    public MappingTO() {
        super();

        items = new ArrayList<MappingItemTO>();
    }

    public String getAccountLink() {
        return accountLink;
    }

    public void setAccountLink(String accountLink) {
        this.accountLink = accountLink;
    }

    @SuppressWarnings("unchecked")
    public <T extends MappingItemTO> T getAccountIdItem() {
        T accountIdItem = null;
        for (MappingItemTO item : getItems()) {
            if (item.isAccountid()) {
                accountIdItem = (T) item;
            }
        }
        return accountIdItem;
    }

    protected <T extends MappingItemTO> boolean addAccountIdItem(final T accountIdItem) {
        if (IntMappingType.UserVirtualSchema == accountIdItem.getIntMappingType()
                || IntMappingType.RoleVirtualSchema == accountIdItem.getIntMappingType()
                || IntMappingType.MembershipVirtualSchema == accountIdItem.getIntMappingType()
                || IntMappingType.Password == accountIdItem.getIntMappingType()) {

            throw new IllegalArgumentException("Virtual attributes cannot be set as accountId");
        }
        if (IntMappingType.Password == accountIdItem.getIntMappingType()) {
            throw new IllegalArgumentException("Password attributes cannot be set as accountId");
        }

        accountIdItem.setExtAttrName("__NAME__");
        accountIdItem.setAccountid(true);

        return this.addItem(accountIdItem);
    }

    public boolean setAccountIdItem(final MappingItemTO accountIdItem) {
        if (accountIdItem == null) {
            return this.removeItem(getAccountIdItem());
        } else {
            return addAccountIdItem(accountIdItem);
        }
    }

    public MappingItemTO getPasswordItem() {
        MappingItemTO passwordItem = null;
        for (MappingItemTO item : getItems()) {
            if (item.isPassword()) {
                passwordItem = item;
            }
        }
        return passwordItem;
    }

    public boolean setPasswordItem(final MappingItemTO passwordItem) {
        if (passwordItem == null) {
            return this.removeItem(getPasswordItem());
        } else {
            passwordItem.setExtAttrName("__PASSWORD__");
            passwordItem.setPassword(true);
            return addItem(passwordItem);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends MappingItemTO> List<T> getItems() {
        return (List<T>) items;
    }

    public boolean addItem(final MappingItemTO item) {
        return item == null ? false : this.items.contains(item) || this.items.add(item);
    }

    public boolean removeItem(final MappingItemTO item) {
        return this.items.remove(item);
    }

    public void setItems(final Collection<MappingItemTO> items) {
        if (this.items != items) {
            this.items.clear();
            if (items != null && !items.isEmpty()) {
                this.items.addAll(items);
            }
        }
    }
}
