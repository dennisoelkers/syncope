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
package org.apache.syncope.common.types;

import java.text.ParseException;

import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.AbstractBaseBean;
import org.apache.syncope.common.types.AuditElements.Category;
import org.apache.syncope.common.types.AuditElements.Result;

public class AuditLoggerName extends AbstractBaseBean {

    private static final long serialVersionUID = -647989486671786839L;

    private final Category category;

    private final Enum<?> subcategory;

    private final Result result;

    public AuditLoggerName(final Category category, final Enum<?> subcategory, final Result result)
            throws IllegalArgumentException {

        if (category == null || subcategory == null || result == null) {
            throw new IllegalArgumentException("Null values not permitted");
        }

        if (!category.getSubCategoryElements().contains(subcategory)) {
            throw new IllegalArgumentException(category.name() + " does not contain " + subcategory.name());
        }

        this.category = category;
        this.subcategory = subcategory;
        this.result = result;
    }

    public Category getCategory() {
        return category;
    }

    public Result getResult() {
        return result;
    }

    public Enum<?> getSubcategory() {
        return subcategory;
    }

    public String toLoggerName() {
        return new StringBuilder().append(LoggerType.AUDIT.getPrefix()).append('.').
                append(category.name()).append('.').
                append(subcategory.name()).append('.').
                append(result.name()).toString();
    }

    @SuppressWarnings("unchecked")
    public static AuditLoggerName fromLoggerName(final String loggerName)
            throws IllegalArgumentException, ParseException {

        if (StringUtils.isBlank(loggerName)) {
            throw new IllegalArgumentException("Null value not permitted");
        }

        if (!loggerName.startsWith(LoggerType.AUDIT.getPrefix())) {
            throw new ParseException("Audit logger name must start with " + LoggerType.AUDIT.getPrefix(), 0);
        }

        String[] splitted = loggerName.split("\\.");
        if (splitted == null || splitted.length < 5) {
            throw new ParseException("Unparsable logger name", 0);
        }

        Category category = Category.valueOf(splitted[2]);
        Enum<?> subcategory = Enum.valueOf(category.getSubCategory(), splitted[3]);
        Result result = Result.valueOf(splitted[4]);

        return new AuditLoggerName(category, subcategory, result);
    }
}
