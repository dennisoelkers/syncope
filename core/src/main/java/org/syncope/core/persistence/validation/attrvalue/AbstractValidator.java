/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.syncope.core.persistence.validation.attrvalue;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.syncope.core.persistence.beans.AbstractSchema;
import org.syncope.core.persistence.beans.AbstractAttrValue;

public abstract class AbstractValidator implements Validator {

    protected final AbstractSchema schema;

    public AbstractValidator(final AbstractSchema schema) {
        this.schema = schema;
    }

    @Override
    public <T extends AbstractAttrValue> T getValue(final String value,
            T attributeValue)
            throws ParseException, InvalidAttrValueException {

        attributeValue = parseValue(value, attributeValue);
        doValidate(attributeValue);

        return attributeValue;
    }

    private <T extends AbstractAttrValue> T parseValue(final String value,
            T attributeValue)
            throws ParseException {

        Exception exception = null;

        switch (schema.getType()) {

            case String:
                attributeValue.setStringValue(value);
                break;

            case Boolean:
                attributeValue.setBooleanValue(Boolean.parseBoolean(value));
                break;

            case Long:
                try {
                    attributeValue.setLongValue(Long.valueOf(
                            ((DecimalFormat) schema.getFormatter()).parse(
                            value).longValue()));
                } catch (java.text.ParseException pe) {
                    exception = pe;
                }
                break;

            case Double:
                try {
                    attributeValue.setDoubleValue(Double.valueOf(
                            ((DecimalFormat) schema.getFormatter()).parse(
                            value).doubleValue()));
                } catch (java.text.ParseException pe) {
                    exception = pe;
                }
                break;

            case Date:
                try {
                    attributeValue.setDateValue(new Date(
                            ((SimpleDateFormat) schema.getFormatter()).parse(
                            value).getTime()));
                } catch (java.text.ParseException pe) {
                    exception = pe;
                }
                break;
        }

        if (exception != null) {
            throw new ParseException("While trying to parse '" + value + "'",
                    exception);
        }

        return attributeValue;
    }

    protected abstract <T extends AbstractAttrValue> void doValidate(
            T attributeValue)
            throws InvalidAttrValueException;
}
