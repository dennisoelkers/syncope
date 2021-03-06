/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.syncope.console.pages;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.authorization.strategies.role.metadata.MetaDataRoleAuthorizationStrategy;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxButton;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.syncope.client.to.DerivedSchemaTO;
import org.syncope.console.rest.SchemaRestClient;

/**
 * Modal window with Schema form.
 */
public class DerivedSchemaModalPage extends BaseModalPage {

    public enum Entity {

        USER, ROLE, MEMBERSHIP

    };
    private TextField name;

    private TextField expression;

    private AjaxButton submit;

    private Entity entity;

    @SpringBean
    private SchemaRestClient restClient;

    public DerivedSchemaModalPage(final Schema basePage,
            final ModalWindow window,
            DerivedSchemaTO schema,
            final boolean createFlag) {

        if (schema == null) {
            schema = new DerivedSchemaTO();
        }

        Form schemaForm = new Form("SchemaForm");

        schemaForm.setModel(new CompoundPropertyModel(schema));

        name = new TextField("name");
        name.setRequired(true);

        expression = new TextField("expression");
        expression.setRequired(true);

        name.setEnabled(createFlag);

        submit = new IndicatingAjaxButton("submit", new Model(
                getString("submit"))) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                if (getEntity() == Entity.USER) {

                    if (createFlag) {
                        restClient.createUserDerivedSchema(
                                (DerivedSchemaTO) form.getDefaultModelObject());
                    } else {
                        restClient.updateUserDerivedSchema(
                                (DerivedSchemaTO) form.getDefaultModelObject());
                    }
                } else if (getEntity() == Entity.ROLE) {
                    if (createFlag) {
                        restClient.createRoleDerivedSchema(
                                (DerivedSchemaTO) form.getDefaultModelObject());
                    } else {
                        restClient.updateRoleDerivedSchema(
                                (DerivedSchemaTO) form.getDefaultModelObject());
                    }
                } else if (getEntity() == Entity.MEMBERSHIP) {
                    if (createFlag) {
                        restClient.createMembershipDerivedSchema(
                                (DerivedSchemaTO) form.getDefaultModelObject());
                    } else {
                        restClient.updateMembershipDerivedSchema(
                                (DerivedSchemaTO) form.getDefaultModelObject());
                    }
                }
                basePage.setOperationResult(true);

                window.close(target);
            }

            @Override
            protected void onError(final AjaxRequestTarget target,
                    final Form form) {

                target.addComponent(feedbackPanel);
            }
        };

        String allowedRoles;

        if (createFlag) {
            allowedRoles = xmlRolesReader.getAllAllowedRoles("Schema",
                    "create");
        } else {
            allowedRoles = xmlRolesReader.getAllAllowedRoles("Schema",
                    "update");
        }

        MetaDataRoleAuthorizationStrategy.authorize(submit, ENABLE,
                allowedRoles);

        schemaForm.add(name);
        schemaForm.add(expression);

        schemaForm.add(submit);

        add(schemaForm);
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }
}
