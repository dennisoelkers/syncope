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

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.apache.syncope.core.persistence.SysInfoListener;

/**
 * Abstract wrapper for common system information.
 */
@MappedSuperclass
@EntityListeners(value = SysInfoListener.class)
public abstract class AbstractSysInfo extends AbstractBaseBean {

    private static final long serialVersionUID = -4801685541488201219L;

    /**
     * Username of the user that has created this profile.
     * <br/>
     * Reference to existing user cannot be used: the creator can either be <tt>admin</tt> or was deleted.
     */
    @Column(nullable = false)
    private String creator;

    /**
     * Creation date.
     */
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date creationDate;

    /**
     * Username of the user that has performed the last modification to this profile.
     * <br/>
     * This field cannot be null: at creation time it needs to be initialized with the creator username.
     * <br/>
     * The modifier can be the user itself if the last performed change was a self-modification.
     * <br/>
     * Reference to existing user cannot be used: the creator can either be <tt>admin</tt> or was deleted.
     */
    @Column(nullable = false)
    private String lastModifier;

    /**
     * Last change date.
     * <br/>
     * This field cannot be null: at creation time it needs to be initialized with <tt>creationDate</tt> field value.
     */
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastChangeDate;

    public String getCreator() {
        return creator;
    }

    public void setCreator(final String creator) {
        this.creator = creator;
    }

    public Date getCreationDate() {
        return creationDate == null ? null : new Date(creationDate.getTime());
    }

    public void setCreationDate(final Date creationDate) {
        this.creationDate = creationDate == null ? null : new Date(creationDate.getTime());
    }

    public String getLastModifier() {
        return lastModifier;
    }

    public void setLastModifier(final String lastModifier) {
        this.lastModifier = lastModifier;
    }

    public Date getLastChangeDate() {
        return lastChangeDate == null ? creationDate : lastChangeDate;
    }

    public void setLastChangeDate(final Date lastChangeDate) {
        this.lastChangeDate = lastChangeDate == null ? null : new Date(lastChangeDate.getTime());
    }
}
