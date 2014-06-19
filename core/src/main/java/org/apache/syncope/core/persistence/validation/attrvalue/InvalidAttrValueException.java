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
package org.apache.syncope.core.persistence.validation.attrvalue;

import javax.validation.ValidationException;

import org.apache.syncope.core.persistence.beans.AbstractAttrValue;

public class InvalidAttrValueException extends ValidationException {

    private static final long serialVersionUID = -5023202610580202148L;

    public InvalidAttrValueException(final String errorMessage) {
        super(errorMessage);
    }

    public InvalidAttrValueException(final String errorMessage, final Throwable t) {
        super(errorMessage, t);
    }

    public InvalidAttrValueException(final AbstractAttrValue value) {
        this("Could not validate " + value.getValue());
    }

    public InvalidAttrValueException(final AbstractAttrValue value, Throwable t) {
        this("Could not validate " + value.getValue(), t);
    }
}
