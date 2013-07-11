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
package org.apache.syncope.core.notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.syncope.common.SyncopeConstants;
import org.apache.syncope.common.to.UserTO;
import org.apache.syncope.common.types.AttributableType;
import org.apache.syncope.common.types.IntMappingType;
import org.apache.syncope.core.connid.ConnObjectUtil;
import org.apache.syncope.core.persistence.beans.Notification;
import org.apache.syncope.core.persistence.beans.NotificationTask;
import org.apache.syncope.core.persistence.beans.SyncopeConf;
import org.apache.syncope.core.persistence.beans.TaskExec;
import org.apache.syncope.core.persistence.beans.user.SyncopeUser;
import org.apache.syncope.core.persistence.beans.user.UAttr;
import org.apache.syncope.core.persistence.beans.user.UDerAttr;
import org.apache.syncope.core.persistence.beans.user.UVirAttr;
import org.apache.syncope.core.persistence.dao.AttributableSearchDAO;
import org.apache.syncope.core.persistence.dao.ConfDAO;
import org.apache.syncope.core.persistence.dao.EntitlementDAO;
import org.apache.syncope.core.persistence.dao.NotFoundException;
import org.apache.syncope.core.persistence.dao.NotificationDAO;
import org.apache.syncope.core.persistence.dao.TaskDAO;
import org.apache.syncope.core.persistence.dao.UserDAO;
import org.apache.syncope.core.rest.data.UserDataBinder;
import org.apache.syncope.core.util.AttributableUtil;
import org.apache.syncope.core.util.EntitlementUtil;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.velocity.VelocityEngineUtils;

/**
 * Create notification tasks that will be executed by NotificationJob.
 *
 * @see NotificationTask
 */
@Transactional(rollbackFor = {Throwable.class})
public class NotificationManager {

    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NotificationManager.class);

    /**
     * Notification DAO.
     */
    @Autowired
    private NotificationDAO notificationDAO;

    /**
     * Configuration DAO.
     */
    @Autowired
    private ConfDAO confDAO;

    /**
     * User DAO.
     */
    @Autowired
    private UserDAO userDAO;

    /**
     * User data binder.
     */
    @Autowired
    private UserDataBinder userDataBinder;

    /**
     * User Search DAO.
     */
    @Autowired
    private AttributableSearchDAO searchDAO;

    /**
     * Task DAO.
     */
    @Autowired
    private TaskDAO taskDAO;

    /**
     * Velocity template engine.
     */
    @Autowired
    private VelocityEngine velocityEngine;

    @Autowired
    private EntitlementDAO entitlementDAO;

    @Autowired
    private ConnObjectUtil connObjectUtil;

    /**
     * Create a notification task.
     *
     * @param notification notification to take as model
     * @param user the user this task is about
     * @return notification task, fully populated
     */
    private NotificationTask getNotificationTask(final Notification notification, final SyncopeUser user) {
        connObjectUtil.retrieveVirAttrValues(user, AttributableUtil.getInstance(AttributableType.USER));

        final List<SyncopeUser> recipients = new ArrayList<SyncopeUser>();

        if (notification.getRecipients() != null) {
            recipients.addAll(searchDAO.<SyncopeUser>search(EntitlementUtil.getRoleIds(entitlementDAO.findAll()),
                    notification.getRecipients(), AttributableUtil.getInstance(AttributableType.USER)));
        }

        if (notification.isSelfAsRecipient()) {
            recipients.add(user);
        }

        final Set<String> recipientEmails = new HashSet<String>();
        final List<UserTO> recipientTOs = new ArrayList<UserTO>(recipients.size());
        for (SyncopeUser recipient : recipients) {
            connObjectUtil.retrieveVirAttrValues(recipient, AttributableUtil.getInstance(AttributableType.USER));

            String email = getRecipientEmail(notification.getRecipientAttrType(),
                    notification.getRecipientAttrName(), recipient);
            if (email == null) {
                LOG.warn("{} cannot be notified: {} not found", recipient, notification.getRecipientAttrName());
            } else {
                recipientEmails.add(email);
                recipientTOs.add(userDataBinder.getUserTO(recipient));
            }
        }

        NotificationTask task = new NotificationTask();
        task.setTraceLevel(notification.getTraceLevel());
        task.setRecipients(recipientEmails);
        task.setSender(notification.getSender());
        task.setSubject(notification.getSubject());

        final Map<String, Object> model = new HashMap<String, Object>();
        model.put("user", userDataBinder.getUserTO(user));
        model.put("syncopeConf", this.findAllSyncopeConfs());
        model.put("recipients", recipientTOs);
        model.put("events", notification.getEvents());

        String htmlBody;
        String textBody;
        try {
            htmlBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "mailTemplates/"
                    + notification.getTemplate() + ".html.vm", SyncopeConstants.DEFAULT_ENCODING, model);
            textBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "mailTemplates/"
                    + notification.getTemplate() + ".txt.vm", SyncopeConstants.DEFAULT_ENCODING, model);
        } catch (VelocityException e) {
            LOG.error("Could not get mail body", e);

            htmlBody = "";
            textBody = "";
        }
        task.setTextBody(textBody);
        task.setHtmlBody(htmlBody);

        return task;
    }

    /**
     * Create notification tasks for each notification matching the given user id and (some of) tasks performed.
     *
     * @param userId user id
     * @param performedTasks set of actions performed on given user id
     * @throws NotFoundException if user contained in the workflow result cannot be found
     */
    public void createTasks(final Long userId, final Set<String> performedTasks)
            throws NotFoundException {

        SyncopeUser user = userDAO.find(userId);
        if (user == null) {
            throw new NotFoundException("User " + userId);
        }

        for (Notification notification : notificationDAO.findAll()) {
            if (searchDAO.matches(user, notification.getAbout(), AttributableUtil.getInstance(AttributableType.USER))) {
                Set<String> events = new HashSet<String>(notification.getEvents());
                events.retainAll(performedTasks);

                if (events.isEmpty()) {
                    LOG.debug("No events found about {}", user);
                } else {
                    LOG.debug("Creating notification task for events {} about {}", events, user);
                    taskDAO.save(getNotificationTask(notification, user));
                }
            }
        }
    }

    private String getRecipientEmail(
            final IntMappingType recipientAttrType, final String recipientAttrName, final SyncopeUser user) {

        String email = null;

        switch (recipientAttrType) {
            case Username:
                email = user.getUsername();
                break;

            case UserSchema:
                UAttr attr = user.getAttribute(recipientAttrName);
                if (attr != null && !attr.getValuesAsStrings().isEmpty()) {
                    email = attr.getValuesAsStrings().get(0);
                }
                break;

            case UserVirtualSchema:
                UVirAttr virAttr = user.getVirtualAttribute(recipientAttrName);
                if (virAttr != null && !virAttr.getValues().isEmpty()) {
                    email = virAttr.getValues().get(0);
                }
                break;

            case UserDerivedSchema:
                UDerAttr derAttr = user.getDerivedAttribute(recipientAttrName);
                if (derAttr != null) {
                    email = derAttr.getValue(user.getAttributes());
                }
                break;

            default:
        }

        return email;
    }

    /**
     * Store execution of a NotificationTask.
     *
     * @param execution task execution.
     * @return merged task execution.
     */
    public TaskExec storeExec(final TaskExec execution) {
        NotificationTask task = taskDAO.find(execution.getTask().getId());
        task.addExec(execution);
        task.setExecuted(true);
        task = taskDAO.save(task);
        // NotificationTasks always have a single execution at most
        return task.getExecs().get(0);
    }

    /**
     * Mark NotificationTask with provided id as executed.
     *
     * @param taskId task to be updated
     */
    public void setTaskExecuted(final Long taskId) {
        NotificationTask task = taskDAO.find(taskId);
        task.setExecuted(true);
        taskDAO.save(task);
    }

    protected Map<String, String> findAllSyncopeConfs() {
        Map<String, String> syncopeConfMap = new HashMap<String, String>();
        for (SyncopeConf conf : confDAO.findAll()) {
            syncopeConfMap.put(conf.getKey(), conf.getValue());
        }
        return syncopeConfMap;
    }
}
