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
package org.apache.syncope.core.persistence.beans;

import javax.persistence.Entity;
import javax.persistence.Transient;
import org.apache.syncope.common.types.MatchingRule;
import org.apache.syncope.common.types.UnmatchingRule;

@Entity
public class PushTask extends AbstractSyncTask {

    private static final long serialVersionUID = -4141057723006682564L;

    @Transient
    private static UnmatchingRule DEF_UNMATCHIG_RULE = UnmatchingRule.ASSIGN;

    @Transient
    private static MatchingRule DEF_MATCHIG_RULE = MatchingRule.UPDATE;

    private String userFilter;

    private String roleFilter;

    /**
     * Default constructor.
     */
    public PushTask() {
        super("org.apache.syncope.core.sync.impl.PushJob");
    }

    public String getUserFilter() {
        return userFilter;
    }

    public void setUserFilter(final String filter) {
        this.userFilter = filter;
    }

    public String getRoleFilter() {
        return roleFilter;
    }

    public void setRoleFilter(final String roleFilter) {
        this.roleFilter = roleFilter;
    }

    @Override
    public UnmatchingRule getUnmatchigRule() {
        return this.unmatchigRule == null ? DEF_UNMATCHIG_RULE : unmatchigRule;
    }

    @Override
    public MatchingRule getMatchigRule() {
        return this.matchigRule == null ? DEF_MATCHIG_RULE : this.matchigRule;
    }
}
