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

import org.apache.syncope.core.sync.SyncUtilities;

import static org.apache.syncope.core.sync.impl.AbstractSyncopeResultHandler.LOG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.syncope.common.mod.AbstractSubjectMod;
import org.apache.syncope.common.to.AbstractSubjectTO;
import org.apache.syncope.common.types.AuditElements;
import org.apache.syncope.common.types.AuditElements.Result;
import org.apache.syncope.common.types.ResourceOperation;
import org.apache.syncope.core.persistence.beans.SyncTask;
import org.apache.syncope.core.persistence.dao.NotFoundException;
import org.apache.syncope.core.persistence.dao.UserDAO;
import org.apache.syncope.core.propagation.PropagationException;
import org.apache.syncope.core.rest.controller.UnauthorizedRoleException;
import org.apache.syncope.core.rest.data.AttributableTransformer;
import org.apache.syncope.core.sync.SyncActions;
import org.apache.syncope.core.sync.SyncResult;
import org.apache.syncope.core.util.AttributableUtil;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractSubjectSyncResultHandler extends AbstractSyncopeResultHandler<SyncTask, SyncActions>
        implements SyncResultsHandler {

    @Autowired
    protected SyncUtilities syncUtilities;

    @Autowired
    protected AttributableTransformer attrTransformer;

    @Autowired
    protected UserDAO userDAO;

    protected abstract AttributableUtil getAttributableUtil();

    protected abstract String getName(AbstractSubjectTO subjectTO);

    protected abstract AbstractSubjectTO getSubjectTO(long id);

    protected abstract AbstractSubjectMod getSubjectMod(AbstractSubjectTO subjectTO, SyncDelta delta);

    protected abstract AbstractSubjectTO create(AbstractSubjectTO subjectTO, SyncDelta _delta, SyncResult result);

    protected abstract AbstractSubjectTO link(AbstractSubjectTO before, SyncResult result, boolean unlink)
            throws Exception;

    protected abstract AbstractSubjectTO update(AbstractSubjectTO before, AbstractSubjectMod subjectMod,
            SyncDelta delta, SyncResult result)
            throws Exception;

    protected abstract void deprovision(Long id, boolean unlink) throws Exception;

    protected abstract void delete(Long id);

    @Override
    public boolean handle(final SyncDelta delta) {
        try {
            if (profile.getResults() == null) {
                profile.setResults(new ArrayList<SyncResult>());
            }

            doHandle(delta, profile.getResults());
            return true;
        } catch (JobExecutionException e) {
            LOG.error("Synchronization failed", e);
            return false;
        }
    }

    protected List<SyncResult> assign(final SyncDelta delta, final AttributableUtil attrUtil)
            throws JobExecutionException {

        final AbstractSubjectTO subjectTO =
                connObjectUtil.getSubjectTO(delta.getObject(), profile.getSyncTask(), attrUtil);

        subjectTO.getResources().add(profile.getSyncTask().getResource().getName());

        if (!profile.getSyncTask().isPerformCreate()) {
            LOG.debug("SyncTask not configured for create");
            return Collections.<SyncResult>emptyList();
        }

        final SyncResult result = new SyncResult();
        result.setOperation(ResourceOperation.CREATE);
        result.setSubjectType(attrUtil.getType());
        result.setStatus(SyncResult.Status.SUCCESS);

        // Attributable transformation (if configured)
        AbstractSubjectTO transformed = attrTransformer.transform(subjectTO);
        LOG.debug("Transformed: {}", transformed);

        result.setName(getName(transformed));

        if (profile.isDryRun()) {
            result.setId(0L);
        } else {
            SyncDelta _delta = delta;
            for (SyncActions action : profile.getActions()) {
                _delta = action.beforeAssign(this.getProfile(), _delta, transformed);
            }

            create(transformed, _delta, attrUtil, "assign", result);
        }

        return Collections.singletonList(result);
    }

    protected List<SyncResult> create(final SyncDelta delta, final AttributableUtil attrUtil)
            throws JobExecutionException {

        if (!profile.getSyncTask().isPerformCreate()) {
            LOG.debug("SyncTask not configured for create");
            return Collections.<SyncResult>emptyList();
        }

        final AbstractSubjectTO subjectTO =
                connObjectUtil.getSubjectTO(delta.getObject(), profile.getSyncTask(), attrUtil);

        // Attributable transformation (if configured)
        AbstractSubjectTO transformed = attrTransformer.transform(subjectTO);
        LOG.debug("Transformed: {}", transformed);

        final SyncResult result = new SyncResult();
        result.setOperation(ResourceOperation.CREATE);
        result.setSubjectType(attrUtil.getType());
        result.setStatus(SyncResult.Status.SUCCESS);

        result.setName(getName(transformed));

        if (profile.isDryRun()) {
            result.setId(0L);
        } else {
            SyncDelta _delta = delta;
            for (SyncActions action : profile.getActions()) {
                _delta = action.beforeProvision(this.getProfile(), _delta, transformed);
            }

            create(transformed, _delta, attrUtil, "provision", result);
        }

        return Collections.<SyncResult>singletonList(result);
    }

    private void create(
            final AbstractSubjectTO subjectTO,
            final SyncDelta delta,
            final AttributableUtil attrUtil,
            final String operation,
            final SyncResult result)
            throws JobExecutionException {

        Object output;
        Result resultStatus;

        try {
            AbstractSubjectTO actual = create(subjectTO, delta, result);
            result.setName(getName(actual));
            output = actual;
            resultStatus = Result.SUCCESS;

            for (SyncActions action : profile.getActions()) {
                action.after(this.getProfile(), delta, actual, result);
            }
        } catch (PropagationException e) {
            // A propagation failure doesn't imply a synchronization failure.
            // The propagation exception status will be reported into the propagation task execution.
            LOG.error("Could not propagate {} {}", attrUtil.getType(), delta.getUid().getUidValue(), e);
            output = e;
            resultStatus = Result.FAILURE;
        } catch (Exception e) {
            result.setStatus(SyncResult.Status.FAILURE);
            result.setMessage(ExceptionUtils.getRootCauseMessage(e));
            LOG.error("Could not create {} {} ", attrUtil.getType(), delta.getUid().getUidValue(), e);
            output = e;
            resultStatus = Result.FAILURE;
        }

        audit(operation, resultStatus, null, output, delta);
    }

    protected List<SyncResult> update(SyncDelta delta, final List<Long> subjects, final AttributableUtil attrUtil)
            throws JobExecutionException {

        if (!profile.getSyncTask().isPerformUpdate()) {
            LOG.debug("SyncTask not configured for update");
            return Collections.<SyncResult>emptyList();
        }

        LOG.debug("About to update {}", subjects);

        List<SyncResult> updResults = new ArrayList<SyncResult>();

        for (Long id : subjects) {
            LOG.debug("About to update {}", id);

            Object output;
            AbstractSubjectTO before = null;
            Result resultStatus;

            final SyncResult result = new SyncResult();
            result.setOperation(ResourceOperation.UPDATE);
            result.setSubjectType(attrUtil.getType());
            result.setStatus(SyncResult.Status.SUCCESS);
            result.setId(id);

            before = getSubjectTO(id);

            if (before == null) {
                result.setStatus(SyncResult.Status.FAILURE);
                result.setMessage(String.format("Subject '%s(%d)' not found", attrUtil.getType().name(), id));
            } else {
                result.setName(getName(before));
            }

            if (!profile.isDryRun()) {
                if (before == null) {
                    resultStatus = Result.FAILURE;
                    output = null;
                } else {
                    try {
                        final AbstractSubjectMod attributableMod = getSubjectMod(before, delta);

                        // Attribute value transformation (if configured)
                        final AbstractSubjectMod actual = attrTransformer.transform(attributableMod);
                        LOG.debug("Transformed: {}", actual);

                        for (SyncActions action : profile.getActions()) {
                            delta = action.beforeUpdate(this.getProfile(), delta, before, attributableMod);
                        }

                        final AbstractSubjectTO updated = update(before, attributableMod, delta, result);

                        for (SyncActions action : profile.getActions()) {
                            action.after(this.getProfile(), delta, updated, result);
                        }

                        output = updated;
                        resultStatus = Result.SUCCESS;
                        result.setName(getName(updated));
                        LOG.debug("{} {} successfully updated", attrUtil.getType(), id);
                    } catch (PropagationException e) {
                        // A propagation failure doesn't imply a synchronization failure.
                        // The propagation exception status will be reported into the propagation task execution.
                        LOG.error("Could not propagate {} {}", attrUtil.getType(), delta.getUid().getUidValue(), e);
                        output = e;
                        resultStatus = Result.FAILURE;
                    } catch (Exception e) {
                        result.setStatus(SyncResult.Status.FAILURE);
                        result.setMessage(ExceptionUtils.getRootCauseMessage(e));
                        LOG.error("Could not update {} {}", attrUtil.getType(), delta.getUid().getUidValue(), e);
                        output = e;
                        resultStatus = Result.FAILURE;
                    }
                }
                audit("update", resultStatus, before, output, delta);
            }
            updResults.add(result);
        }
        return updResults;
    }

    protected List<SyncResult> deprovision(
            SyncDelta delta,
            final List<Long> subjects,
            final AttributableUtil attrUtil,
            final boolean unlink)
            throws JobExecutionException {

        if (!profile.getSyncTask().isPerformUpdate()) {
            LOG.debug("SyncTask not configured for update");
            return Collections.<SyncResult>emptyList();
        }

        LOG.debug("About to update {}", subjects);

        final List<SyncResult> updResults = new ArrayList<SyncResult>();

        for (Long id : subjects) {
            LOG.debug("About to unassign resource {}", id);

            Object output;
            Result resultStatus;

            final SyncResult result = new SyncResult();
            result.setOperation(ResourceOperation.DELETE);
            result.setSubjectType(attrUtil.getType());
            result.setStatus(SyncResult.Status.SUCCESS);
            result.setId(id);

            final AbstractSubjectTO before = getSubjectTO(id);

            if (before == null) {
                result.setStatus(SyncResult.Status.FAILURE);
                result.setMessage(String.format("Subject '%s(%d)' not found", attrUtil.getType().name(), id));
            }

            if (!profile.isDryRun()) {
                if (before == null) {
                    resultStatus = Result.FAILURE;
                    output = null;
                } else {
                    result.setName(getName(before));

                    try {
                        if (unlink) {
                            for (SyncActions action : profile.getActions()) {
                                action.beforeUnassign(this.getProfile(), delta, before);
                            }
                        } else {
                            for (SyncActions action : profile.getActions()) {
                                action.beforeDeprovision(this.getProfile(), delta, before);
                            }
                        }

                        deprovision(id, unlink);
                        output = getSubjectTO(id);

                        for (SyncActions action : profile.getActions()) {
                            action.after(this.getProfile(), delta, AbstractSubjectTO.class.cast(output), result);
                        }

                        resultStatus = Result.SUCCESS;
                        LOG.debug("{} {} successfully updated", attrUtil.getType(), id);
                    } catch (PropagationException e) {
                        // A propagation failure doesn't imply a synchronization failure.
                        // The propagation exception status will be reported into the propagation task execution.
                        LOG.error("Could not propagate {} {}", attrUtil.getType(), delta.getUid().getUidValue(), e);
                        output = e;
                        resultStatus = Result.FAILURE;
                    } catch (Exception e) {
                        result.setStatus(SyncResult.Status.FAILURE);
                        result.setMessage(ExceptionUtils.getRootCauseMessage(e));
                        LOG.error("Could not update {} {}", attrUtil.getType(), delta.getUid().getUidValue(), e);
                        output = e;
                        resultStatus = Result.FAILURE;
                    }
                }
                audit(unlink ? "unassign" : "deprovision", resultStatus, before, output, delta);
            }
            updResults.add(result);
        }

        return updResults;
    }

    protected List<SyncResult> link(
            SyncDelta delta,
            final List<Long> subjects,
            final AttributableUtil attrUtil,
            final boolean unlink)
            throws JobExecutionException {

        if (!profile.getSyncTask().isPerformUpdate()) {
            LOG.debug("SyncTask not configured for update");
            return Collections.<SyncResult>emptyList();
        }

        LOG.debug("About to update {}", subjects);

        final List<SyncResult> updResults = new ArrayList<SyncResult>();

        for (Long id : subjects) {
            LOG.debug("About to unassign resource {}", id);

            Object output;
            Result resultStatus;

            final SyncResult result = new SyncResult();
            result.setOperation(ResourceOperation.NONE);
            result.setSubjectType(attrUtil.getType());
            result.setStatus(SyncResult.Status.SUCCESS);
            result.setId(id);

            final AbstractSubjectTO before = getSubjectTO(id);

            if (before == null) {
                result.setStatus(SyncResult.Status.FAILURE);
                result.setMessage(String.format("Subject '%s(%d)' not found", attrUtil.getType().name(), id));
            }

            if (!profile.isDryRun()) {
                if (before == null) {
                    resultStatus = Result.FAILURE;
                    output = null;
                } else {
                    result.setName(getName(before));

                    try {
                        if (unlink) {
                            for (SyncActions action : profile.getActions()) {
                                action.beforeUnlink(this.getProfile(), delta, before);
                            }
                        } else {
                            for (SyncActions action : profile.getActions()) {
                                action.beforeLink(this.getProfile(), delta, before);
                            }
                        }

                        output = link(before, result, unlink);

                        for (SyncActions action : profile.getActions()) {
                            action.after(this.getProfile(), delta, AbstractSubjectTO.class.cast(output), result);
                        }

                        resultStatus = Result.SUCCESS;
                        LOG.debug("{} {} successfully updated", attrUtil.getType(), id);
                    } catch (PropagationException e) {
                        // A propagation failure doesn't imply a synchronization failure.
                        // The propagation exception status will be reported into the propagation task execution.
                        LOG.error("Could not propagate {} {}", attrUtil.getType(), delta.getUid().getUidValue(), e);
                        output = e;
                        resultStatus = Result.FAILURE;
                    } catch (Exception e) {
                        result.setStatus(SyncResult.Status.FAILURE);
                        result.setMessage(ExceptionUtils.getRootCauseMessage(e));
                        LOG.error("Could not update {} {}", attrUtil.getType(), delta.getUid().getUidValue(), e);
                        output = e;
                        resultStatus = Result.FAILURE;
                    }
                }
                audit(unlink ? "unlink" : "link", resultStatus, before, output, delta);
            }
            updResults.add(result);
        }

        return updResults;
    }

    protected List<SyncResult> delete(SyncDelta delta, final List<Long> subjects, final AttributableUtil attrUtil)
            throws JobExecutionException {

        if (!profile.getSyncTask().isPerformDelete()) {
            LOG.debug("SyncTask not configured for delete");
            return Collections.<SyncResult>emptyList();
        }

        LOG.debug("About to delete {}", subjects);

        List<SyncResult> delResults = new ArrayList<SyncResult>();

        for (Long id : subjects) {
            Object output;
            Result resultStatus = Result.FAILURE;

            AbstractSubjectTO before = null;
            final SyncResult result = new SyncResult();

            try {
                before = getSubjectTO(id);

                result.setId(id);
                result.setName(getName(before));
                result.setOperation(ResourceOperation.DELETE);
                result.setSubjectType(attrUtil.getType());
                result.setStatus(SyncResult.Status.SUCCESS);

                if (!profile.isDryRun()) {
                    for (SyncActions action : profile.getActions()) {
                        delta = action.beforeDelete(this.getProfile(), delta, before);
                    }

                    try {
                        delete(id);
                        output = null;
                        resultStatus = Result.SUCCESS;
                    } catch (Exception e) {
                        result.setStatus(SyncResult.Status.FAILURE);
                        result.setMessage(ExceptionUtils.getRootCauseMessage(e));
                        LOG.error("Could not delete {} {}", attrUtil.getType(), id, e);
                        output = e;
                    }

                    for (SyncActions action : profile.getActions()) {
                        action.after(this.getProfile(), delta, before, result);
                    }

                    audit("delete", resultStatus, before, output, delta);
                }

                delResults.add(result);

            } catch (NotFoundException e) {
                LOG.error("Could not find {} {}", attrUtil.getType(), id, e);
            } catch (UnauthorizedRoleException e) {
                LOG.error("Not allowed to read {} {}", attrUtil.getType(), id, e);
            } catch (Exception e) {
                LOG.error("Could not delete {} {}", attrUtil.getType(), id, e);
            }
        }

        return delResults;
    }

    /**
     * Look into SyncDelta and take necessary profile.getActions() (create / update / delete) on user(s)/role(s).
     *
     * @param delta returned by the underlying profile.getConnector()
     * @throws JobExecutionException in case of synchronization failure.
     */
    protected final void doHandle(final SyncDelta delta, final Collection<SyncResult> syncResults)
            throws JobExecutionException {

        final AttributableUtil attrUtil = getAttributableUtil();

        LOG.debug("Process {} for {} as {}",
                delta.getDeltaType(), delta.getUid().getUidValue(), delta.getObject().getObjectClass());

        final String uid = delta.getPreviousUid() == null
                ? delta.getUid().getUidValue()
                : delta.getPreviousUid().getUidValue();

        try {
            List<Long> subjectIds = syncUtilities.findExisting(
                    uid, delta.getObject(), profile.getSyncTask().getResource(), attrUtil);

            if (subjectIds.size() > 1) {
                switch (profile.getResAct()) {
                    case IGNORE:
                        throw new IllegalStateException("More than one match " + subjectIds);

                    case FIRSTMATCH:
                        subjectIds = subjectIds.subList(0, 1);
                        break;

                    case LASTMATCH:
                        subjectIds = subjectIds.subList(subjectIds.size() - 1, subjectIds.size());
                        break;

                    default:
                    // keep subjectIds as is
                }
            }

            if (SyncDeltaType.CREATE_OR_UPDATE == delta.getDeltaType()) {
                if (subjectIds.isEmpty()) {
                    switch (profile.getSyncTask().getUnmatchingRule()) {
                        case ASSIGN:
                            profile.getResults().addAll(assign(delta, attrUtil));
                            break;
                        case PROVISION:
                            profile.getResults().addAll(create(delta, attrUtil));
                            break;
                        default:
                        // do nothing
                    }
                } else {
                    switch (profile.getSyncTask().getMatchingRule()) {
                        case UPDATE:
                            profile.getResults().addAll(update(delta, subjectIds, attrUtil));
                            break;
                        case DEPROVISION:
                            profile.getResults().addAll(deprovision(delta, subjectIds, attrUtil, false));
                            break;
                        case UNASSIGN:
                            profile.getResults().addAll(deprovision(delta, subjectIds, attrUtil, true));
                            break;
                        case LINK:
                            profile.getResults().addAll(link(delta, subjectIds, attrUtil, false));
                            break;
                        case UNLINK:
                            profile.getResults().addAll(link(delta, subjectIds, attrUtil, true));
                            break;
                        default:
                        // do nothing
                    }
                }
            } else if (SyncDeltaType.DELETE == delta.getDeltaType()) {
                if (subjectIds.isEmpty()) {
                    LOG.debug("No match found for deletion");
                } else {
                    profile.getResults().addAll(delete(delta, subjectIds, attrUtil));
                }
            }
        } catch (IllegalStateException e) {
            LOG.warn(e.getMessage());
        }
    }

    private void audit(
            final String event,
            final Result result,
            final Object before,
            final Object output,
            final Object... input) {

        notificationManager.createTasks(
                AuditElements.EventCategoryType.SYNCHRONIZATION,
                getAttributableUtil().getType().name().toLowerCase(),
                profile.getSyncTask().getResource().getName(),
                event,
                result,
                before,
                output,
                input);

        auditManager.audit(
                AuditElements.EventCategoryType.SYNCHRONIZATION,
                getAttributableUtil().getType().name().toLowerCase(),
                profile.getSyncTask().getResource().getName(),
                event,
                result,
                before,
                output,
                input);
    }
}
