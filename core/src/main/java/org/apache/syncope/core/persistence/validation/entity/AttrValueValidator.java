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
package org.apache.syncope.core.persistence.validation.entity;

import javax.validation.ConstraintValidatorContext;
import org.apache.syncope.common.types.EntityViolationType;
import org.apache.syncope.core.persistence.beans.AbstractAttrUniqueValue;
import org.apache.syncope.core.persistence.beans.AbstractAttrValue;
import org.apache.syncope.core.persistence.beans.AbstractNormalSchema;

public class AttrValueValidator extends AbstractValidator<AttrValueCheck, AbstractAttrValue> {

    @Override
    public boolean isValid(final AbstractAttrValue object, final ConstraintValidatorContext context) {
        boolean isValid;

        if (object == null) {
            isValid = true;
        } else {
            int nonNullVales = 0;
            if (object.getBooleanValue() != null) {
                nonNullVales++;
            }
            if (object.getDateValue() != null) {
                nonNullVales++;
            }
            if (object.getDoubleValue() != null) {
                nonNullVales++;
            }
            if (object.getLongValue() != null) {
                nonNullVales++;
            }
            if (object.getStringValue() != null) {
                nonNullVales++;
            }
            isValid = nonNullVales == 1;

            if (!isValid) {
                LOG.error("More than one non-null value for " + object);

                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        getTemplate(EntityViolationType.MoreThanOneNonNull, "More than one non-null value found")).
                        addNode(object.getClass().getSimpleName().replaceAll("\\n", " ")).addConstraintViolation();

            } else if (object instanceof AbstractAttrUniqueValue) {
                AbstractNormalSchema uniqueValueSchema = ((AbstractAttrUniqueValue) object).getSchema();
                AbstractNormalSchema attrSchema = object.getAttribute().getSchema();

                isValid = uniqueValueSchema.equals(attrSchema);

                if (!isValid) {
                    LOG.error("Unique value schema for " + object.getClass().getSimpleName() + "[" + object.getId()
                            + "]" + " is " + uniqueValueSchema + ", while owning attribute schema is " + attrSchema);

                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(
                            getTemplate(EntityViolationType.valueOf("Invalid" + attrSchema.getClass().getSimpleName()),
                            "Unique value schema is " + uniqueValueSchema
                            + ", while owning attribute schema is " + attrSchema)).addNode("schema").
                            addConstraintViolation();
                }
            }
        }

        return isValid;
    }
}
