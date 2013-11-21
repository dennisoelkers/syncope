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
package org.apache.syncope.core.sync.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.syncope.common.mod.ReferenceMod;
import org.apache.syncope.common.mod.RoleMod;
import org.apache.syncope.common.types.ConflictResolutionAction;
import org.apache.syncope.common.types.SyncPolicySpec;
import org.apache.syncope.common.types.TraceLevel;
import org.apache.syncope.core.persistence.beans.Entitlement;
import org.apache.syncope.core.persistence.beans.ExternalResource;
import org.apache.syncope.core.persistence.beans.SyncPolicy;
import org.apache.syncope.core.persistence.beans.SyncTask;
import org.apache.syncope.core.persistence.beans.TaskExec;
import org.apache.syncope.core.persistence.beans.role.RMapping;
import org.apache.syncope.core.persistence.beans.user.UMapping;
import org.apache.syncope.core.persistence.dao.EntitlementDAO;
import org.apache.syncope.core.persistence.dao.NotFoundException;
import org.apache.syncope.core.persistence.dao.ResourceDAO;
import org.apache.syncope.core.propagation.ConnectorFactory;
import org.apache.syncope.core.propagation.Connector;
import org.apache.syncope.core.quartz.AbstractTaskJob;
import org.apache.syncope.core.rest.controller.UnauthorizedRoleException;
import org.apache.syncope.core.sync.SyncActions;
import org.apache.syncope.core.sync.SyncResult;
import org.apache.syncope.core.util.ApplicationContextProvider;
import org.apache.syncope.core.workflow.role.RoleWorkflowAdapter;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Job for executing synchronization tasks.
 *
 * @see AbstractTaskJob
 * @see SyncTask
 */
public class SyncJob extends AbstractTaskJob {

    /**
     * ConnInstance loader.
     */
    @Autowired
    private ConnectorFactory connFactory;

    /**
     * Resource DAO.
     */
    @Autowired
    private ResourceDAO resourceDAO;

    /**
     * Entitlement DAO.
     */
    @Autowired
    private EntitlementDAO entitlementDAO;

    /**
     * Role workflow adapter.
     */
    @Autowired
    private RoleWorkflowAdapter rwfAdapter;

    /**
     * SyncJob actions.
     */
    private SyncActions actions;

    public void setActions(final SyncActions actions) {
        this.actions = actions;
    }

    /**
     * Create a textual report of the synchronization, based on the trace level.
     *
     * @param syncResults Sync results
     * @param syncTraceLevel Sync trace level
     * @param dryRun dry run?
     * @return report as string
     */
    protected String createReport(final List<SyncResult> syncResults, final TraceLevel syncTraceLevel,
            final boolean dryRun) {

        if (syncTraceLevel == TraceLevel.NONE) {
            return null;
        }

        StringBuilder report = new StringBuilder();

        if (dryRun) {
            report.append("==>Dry run only, no modifications were made<==\n\n");
        }

        List<SyncResult> uSuccCreate = new ArrayList<SyncResult>();
        List<SyncResult> uFailCreate = new ArrayList<SyncResult>();
        List<SyncResult> uSuccUpdate = new ArrayList<SyncResult>();
        List<SyncResult> uFailUpdate = new ArrayList<SyncResult>();
        List<SyncResult> uSuccDelete = new ArrayList<SyncResult>();
        List<SyncResult> uFailDelete = new ArrayList<SyncResult>();
        List<SyncResult> rSuccCreate = new ArrayList<SyncResult>();
        List<SyncResult> rFailCreate = new ArrayList<SyncResult>();
        List<SyncResult> rSuccUpdate = new ArrayList<SyncResult>();
        List<SyncResult> rFailUpdate = new ArrayList<SyncResult>();
        List<SyncResult> rSuccDelete = new ArrayList<SyncResult>();
        List<SyncResult> rFailDelete = new ArrayList<SyncResult>();

        for (SyncResult syncResult : syncResults) {
            switch (syncResult.getStatus()) {
                case SUCCESS:
                    switch (syncResult.getOperation()) {
                        case CREATE:
                            switch (syncResult.getSubjectType()) {
                                case USER:
                                    uSuccCreate.add(syncResult);
                                    break;

                                case ROLE:
                                    rSuccCreate.add(syncResult);
                                    break;

                                default:
                            }
                            break;

                        case UPDATE:
                            switch (syncResult.getSubjectType()) {
                                case USER:
                                    uSuccUpdate.add(syncResult);
                                    break;

                                case ROLE:
                                    rSuccUpdate.add(syncResult);
                                    break;

                                default:
                            }
                            break;

                        case DELETE:
                            switch (syncResult.getSubjectType()) {
                                case USER:
                                    uSuccDelete.add(syncResult);
                                    break;

                                case ROLE:
                                    rSuccDelete.add(syncResult);
                                    break;

                                default:
                            }
                            break;

                        default:
                    }
                    break;

                case FAILURE:
                    switch (syncResult.getOperation()) {
                        case CREATE:
                            switch (syncResult.getSubjectType()) {
                                case USER:
                                    uFailCreate.add(syncResult);
                                    break;

                                case ROLE:
                                    rFailCreate.add(syncResult);
                                    break;

                                default:
                            }
                            break;

                        case UPDATE:
                            switch (syncResult.getSubjectType()) {
                                case USER:
                                    uFailUpdate.add(syncResult);
                                    break;

                                case ROLE:
                                    rFailUpdate.add(syncResult);
                                    break;

                                default:
                            }
                            break;

                        case DELETE:
                            switch (syncResult.getSubjectType()) {
                                case USER:
                                    uFailDelete.add(syncResult);
                                    break;

                                case ROLE:
                                    rFailDelete.add(syncResult);
                                    break;

                                default:
                            }
                            break;

                        default:
                    }
                    break;

                default:
            }
        }

        // Summary, also to be included for FAILURE and ALL, so create it anyway.
        report.append("Users ").
                append("[created/failures]: ").append(uSuccCreate.size()).append('/').append(uFailCreate.size()).
                append(' ').
                append("[updated/failures]: ").append(uSuccUpdate.size()).append('/').append(uFailUpdate.size()).
                append(' ').
                append("[deleted/failures]: ").append(uSuccDelete.size()).append('/').append(uFailDelete.size()).
                append('\n');
        report.append("Roles ").
                append("[created/failures]: ").append(rSuccCreate.size()).append('/').append(rFailCreate.size()).
                append(' ').
                append("[updated/failures]: ").append(rSuccUpdate.size()).append('/').append(rFailUpdate.size()).
                append(' ').
                append("[deleted/failures]: ").append(rSuccDelete.size()).append('/').append(rFailDelete.size());

        // Failures
        if (syncTraceLevel == TraceLevel.FAILURES || syncTraceLevel == TraceLevel.ALL) {
            if (!uFailCreate.isEmpty()) {
                report.append("\n\nUsers failed to create: ");
                report.append(SyncResult.produceReport(uFailCreate, syncTraceLevel));
            }
            if (!uFailUpdate.isEmpty()) {
                report.append("\nUsers failed to update: ");
                report.append(SyncResult.produceReport(uFailUpdate, syncTraceLevel));
            }
            if (!uFailDelete.isEmpty()) {
                report.append("\nUsers failed to delete: ");
                report.append(SyncResult.produceReport(uFailDelete, syncTraceLevel));
            }

            if (!rFailCreate.isEmpty()) {
                report.append("\n\nRoles failed to create: ");
                report.append(SyncResult.produceReport(rFailCreate, syncTraceLevel));
            }
            if (!rFailUpdate.isEmpty()) {
                report.append("\nRoles failed to update: ");
                report.append(SyncResult.produceReport(rFailUpdate, syncTraceLevel));
            }
            if (!rFailDelete.isEmpty()) {
                report.append("\nRoles failed to delete: ");
                report.append(SyncResult.produceReport(rFailDelete, syncTraceLevel));
            }
        }

        // Succeeded, only if on 'ALL' level
        if (syncTraceLevel == TraceLevel.ALL) {
            report.append("\n\nUsers created:\n")
                    .append(SyncResult.produceReport(uSuccCreate, syncTraceLevel))
                    .append("\nUsers updated:\n")
                    .append(SyncResult.produceReport(uSuccUpdate, syncTraceLevel))
                    .append("\nUsers deleted:\n")
                    .append(SyncResult.produceReport(uSuccDelete, syncTraceLevel));
            report.append("\n\nRoles created:\n")
                    .append(SyncResult.produceReport(rSuccCreate, syncTraceLevel))
                    .append("\nRoles updated:\n")
                    .append(SyncResult.produceReport(rSuccUpdate, syncTraceLevel))
                    .append("\nRoles deleted:\n")
                    .append(SyncResult.produceReport(rSuccDelete, syncTraceLevel));
        }

        return report.toString();
    }

    protected void setRoleOwners(final SyncopeSyncResultHandler handler)
            throws UnauthorizedRoleException, NotFoundException {

        for (Map.Entry<Long, String> entry : handler.getRoleOwnerMap().entrySet()) {
            RoleMod roleMod = new RoleMod();
            roleMod.setId(entry.getKey());

            if (StringUtils.isBlank(entry.getValue())) {
                roleMod.setRoleOwner(null);
                roleMod.setUserOwner(null);
            } else {
                Long userId = handler.findMatchingAttributableId(ObjectClass.ACCOUNT, entry.getValue());
                if (userId == null) {
                    Long roleId = handler.findMatchingAttributableId(ObjectClass.GROUP, entry.getValue());
                    if (roleId != null) {
                        roleMod.setRoleOwner(new ReferenceMod(roleId));
                    }
                } else {
                    roleMod.setUserOwner(new ReferenceMod(userId));
                }
            }

            rwfAdapter.update(roleMod);
        }
    }

    @Override
    protected String doExecute(final boolean dryRun) throws JobExecutionException {
        // PRE: grant all authorities (i.e. setup the SecurityContextHolder)
        final List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();

        for (Entitlement entitlement : entitlementDAO.findAll()) {
            authorities.add(new SimpleGrantedAuthority(entitlement.getName()));
        }

        final UserDetails userDetails = new User("admin", "FAKE_PASSWORD", true, true, true, true, authorities);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, "FAKE_PASSWORD", authorities));

        try {
            return executeWithSecurityContext(dryRun);
        } finally {
            // POST: clean up the SecurityContextHolder
            SecurityContextHolder.clearContext();
        }
    }

    protected String executeWithSecurityContext(final boolean dryRun) throws JobExecutionException {
        if (!(task instanceof SyncTask)) {
            throw new JobExecutionException("Task " + taskId + " isn't a SyncTask");
        }
        final SyncTask syncTask = (SyncTask) this.task;

        Connector connector;
        try {
            connector = connFactory.getConnector(syncTask.getResource());
        } catch (Exception e) {
            final String msg = String.format("Connector instance bean for resource %s and connInstance %s not found",
                    syncTask.getResource(), syncTask.getResource().getConnector());

            throw new JobExecutionException(msg, e);
        }

        UMapping uMapping = syncTask.getResource().getUmapping();
        if (uMapping != null && uMapping.getAccountIdItem() == null) {
            throw new JobExecutionException("Invalid user account id mapping for resource " + syncTask.getResource());
        }
        RMapping rMapping = syncTask.getResource().getRmapping();
        if (rMapping != null && rMapping.getAccountIdItem() == null) {
            throw new JobExecutionException("Invalid role account id mapping for resource " + syncTask.getResource());
        }
        if (uMapping == null && rMapping == null) {
            return "No mapping configured for both users and roles: aborting...";
        }

        LOG.debug("Execute synchronization with token {}", syncTask.getResource().getUsyncToken());

        final List<SyncResult> results = new ArrayList<SyncResult>();

        final SyncPolicy syncPolicy = syncTask.getResource().getSyncPolicy();
        final ConflictResolutionAction resAct = syncPolicy == null || syncPolicy.getSpecification() == null
                ? ConflictResolutionAction.IGNORE
                : ((SyncPolicySpec) syncPolicy.getSpecification()).getConflictResolutionAction();

        // Prepare handler for SyncDelta objects
        final SyncopeSyncResultHandler handler =
                (SyncopeSyncResultHandler) ((DefaultListableBeanFactory) ApplicationContextProvider.
                getApplicationContext().getBeanFactory()).createBean(
                        SyncopeSyncResultHandler.class, AbstractBeanDefinition.AUTOWIRE_BY_NAME, false);
        handler.setConnector(connector);
        handler.setActions(actions);
        handler.setDryRun(dryRun);
        handler.setResAct(resAct);
        handler.setResults(results);
        handler.setSyncTask(syncTask);

        actions.beforeAll(handler);
        try {
            SyncToken latestUSyncToken = null;
            if (uMapping != null && !syncTask.isFullReconciliation()) {
                latestUSyncToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
            }
            SyncToken latestRSyncToken = null;
            if (rMapping != null && !syncTask.isFullReconciliation()) {
                latestRSyncToken = connector.getLatestSyncToken(ObjectClass.GROUP);
            }

            if (syncTask.isFullReconciliation()) {
                if (uMapping != null) {
                    connector.getAllObjects(ObjectClass.ACCOUNT, handler,
                            connector.getOperationOptions(uMapping.getItems()));
                }
                if (rMapping != null) {
                    connector.getAllObjects(ObjectClass.GROUP, handler,
                            connector.getOperationOptions(rMapping.getItems()));
                }
            } else {
                if (uMapping != null) {
                    connector.sync(ObjectClass.ACCOUNT, syncTask.getResource().getUsyncToken(), handler,
                            connector.getOperationOptions(uMapping.getItems()));
                }
                if (rMapping != null) {
                    connector.sync(ObjectClass.GROUP, syncTask.getResource().getRsyncToken(), handler,
                            connector.getOperationOptions(rMapping.getItems()));
                }
            }

            if (!dryRun && !syncTask.isFullReconciliation()) {
                try {
                    ExternalResource resource = resourceDAO.find(syncTask.getResource().getName());
                    if (uMapping != null) {
                        resource.setUsyncToken(latestUSyncToken);
                    }
                    if (rMapping != null) {
                        resource.setRsyncToken(latestRSyncToken);
                    }
                    resourceDAO.save(resource);
                } catch (Exception e) {
                    throw new JobExecutionException("While updating SyncToken", e);
                }
            }
        } catch (Exception e) {
            throw new JobExecutionException("While syncing on connector", e);
        }

        try {
            setRoleOwners(handler);
        } catch (Exception e) {
            LOG.error("While setting role owners", e);
        }

        actions.afterAll(handler, results);

        final String result = createReport(results, syncTask.getResource().getSyncTraceLevel(), dryRun);

        LOG.debug("Sync result: {}", result);

        return result;
    }

    @Override
    protected boolean hasToBeRegistered(final TaskExec execution) {
        SyncTask syncTask = (SyncTask) task;

        // True if either failed and failures have to be registered, or if ALL has to be registered.
        return (Status.valueOf(execution.getStatus()) == Status.FAILURE
                && syncTask.getResource().getSyncTraceLevel().ordinal() >= TraceLevel.FAILURES.ordinal())
                || syncTask.getResource().getSyncTraceLevel().ordinal() >= TraceLevel.SUMMARY.ordinal();
    }
}
