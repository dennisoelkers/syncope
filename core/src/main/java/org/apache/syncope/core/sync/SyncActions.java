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
package org.apache.syncope.core.sync;

import org.apache.syncope.common.mod.AbstractAttributableMod;
import org.apache.syncope.common.to.AbstractAttributableTO;
import org.apache.syncope.core.sync.impl.AbstractSyncopeResultHandler;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.quartz.JobExecutionException;

/**
 * Interface for actions to be performed during SyncJob execution.
 */
public interface SyncActions extends AbstractSyncActions<AbstractSyncopeResultHandler<?, ?>> {

    /**
     * Action to be executed before to create a synchronized user / role locally.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject user / role to be created
     * @return synchronization information used for user status evaluation and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> SyncDelta beforeCreate(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject) throws JobExecutionException;

    /**
     * Action to be executed before creating (and linking to the resource) a synchronized user / role locally.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject user / role to be created
     * @return synchronization information used for user status evaluation and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> SyncDelta beforeAssign(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject) throws JobExecutionException;

    /**
     * Action to be executed before unlinking resource from the synchronized user / role and de-provisioning.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject user / role to be created
     * @return synchronization information used for user status evaluation and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> SyncDelta beforeUnassign(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject) throws JobExecutionException;

    /**
     * Action to be executed before de-provisioning action only.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject user / role to be created
     * @return synchronization information used for user status evaluation and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> SyncDelta beforeDeprovision(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject) throws JobExecutionException;

    /**
     * Action to be executed before unlinking resource from the synchronized user / role.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject user / role to be created
     * @return synchronization information used for user status evaluation and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> SyncDelta beforeUnlink(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject) throws JobExecutionException;

    /**
     * Action to be executed before linking resource to the synchronized user / role.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject user / role to be created
     * @return synchronization information used for user status evaluation and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> SyncDelta beforeLink(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject) throws JobExecutionException;

    /**
     * Action to be executed before to update a synchronized user / role locally.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject local user / role information
     * @param subjectMod modification
     * @return synchronization information used for logging and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure.
     */
    <T extends AbstractAttributableTO, K extends AbstractAttributableMod> SyncDelta beforeUpdate(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject,
            final K subjectMod)
            throws JobExecutionException;

    /**
     * Action to be executed before to delete a synchronized user / role locally.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information
     * @param subject local user / role to be deleted
     * @return synchronization information used for logging and to be passed to the 'after' method.
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> SyncDelta beforeDelete(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject) throws JobExecutionException;

    /**
     * Action to be executed after each local user / role synchronization.
     *
     * @param handler synchronization handler being executed.
     * @param delta retrieved synchronization information (may be modified by 'beforeCreate/beforeUpdate/beforeDelete')
     * @param subject synchronized local user / role
     * @param result global synchronization results at the current synchronization step
     * @throws JobExecutionException in case of generic failure
     */
    <T extends AbstractAttributableTO> void after(
            final AbstractSyncopeResultHandler<?, ?> handler,
            final SyncDelta delta,
            final T subject,
            final SyncResult result) throws JobExecutionException;
}
