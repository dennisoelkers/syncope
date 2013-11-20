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
package org.apache.syncope.core.workflow;

import java.io.OutputStream;
import java.util.List;
import org.apache.syncope.common.mod.AbstractAttributableMod;
import org.apache.syncope.common.to.WorkflowFormTO;
import org.apache.syncope.core.persistence.dao.NotFoundException;

public interface WorkflowAdapter {

    /**
     * Give the class to be instantiated and invoked by SpringContextInitializer for loading anything needed by this
     * adapter.
     *
     * @return null if no init is needed or the WorkflowLoader class for handling initialization
     * @see org.apache.syncope.core.init.SpringContextInitializer
     */
    Class<? extends WorkflowInstanceLoader> getLoaderClass();

    /**
     * Export workflow definition.
     *
     * @param format export format
     * @param os export stream
     * @throws WorkflowException workflow exception
     */
    void exportDefinition(WorkflowDefinitionFormat format, OutputStream os) throws WorkflowException;

    /**
     * Export workflow graphical representation (if available).
     *
     * @param os export stream
     * @throws WorkflowException workflow exception
     */
    void exportDiagram(OutputStream os) throws WorkflowException;

    /**
     * Update workflow definition.
     *
     * @param format import format
     * @param definition definition
     * @throws WorkflowException workflow exception
     */
    void importDefinition(WorkflowDefinitionFormat format, String definition) throws WorkflowException;

    /**
     * Get all defined forms for current workflow process instances.
     *
     * @return list of defined forms
     */
    List<WorkflowFormTO> getForms();

    /**
     * Gets all forms with the given name for the given workflowId(include historical forms).
     *
     * @param workflowId workflow id.
     * @param name form name.
     * @return forms (if present), otherwise an empty list.
     * @throws NotFoundException definition not found exception
     * @throws WorkflowException workflow exception
     */
    List<WorkflowFormTO> getForms(String workflowId, String name);

    /**
     * Get form for given workflowId (if present).
     *
     * @param workflowId workflow id
     * @return form (if present), otherwise null
     * @throws NotFoundException definition not found exception
     * @throws WorkflowException workflow exception
     */
    WorkflowFormTO getForm(String workflowId) throws NotFoundException, WorkflowException;

    /**
     * Claim a form for a given user.
     *
     * @param taskId Workflow task to which the form is associated
     * @param username claiming username
     * @return updated form
     * @throws NotFoundException not found exception
     * @throws WorkflowException workflow exception
     */
    WorkflowFormTO claimForm(String taskId, String username) throws NotFoundException, WorkflowException;

    /**
     * Submit a form.
     *
     * @param form to be submitted
     * @param username submitting username
     * @return user updated by this form submit
     * @throws NotFoundException not found exception
     * @throws WorkflowException workflow exception
     */
    WorkflowResult<? extends AbstractAttributableMod> submitForm(WorkflowFormTO form, String username)
            throws NotFoundException, WorkflowException;
}
