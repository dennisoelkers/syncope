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
package org.apache.syncope.core.persistence.beans;

import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.syncope.common.types.AttributeSchemaType;
import org.apache.syncope.core.persistence.validation.attrvalue.ParsingValidationException;
import org.apache.syncope.core.persistence.validation.entity.AttrValueCheck;
import org.apache.syncope.core.util.DataFormat;

@MappedSuperclass
@AttrValueCheck
public abstract class AbstractAttrValue extends AbstractBaseBean {

    private static final long serialVersionUID = -9141923816611244785L;

    private String stringValue;

    @Temporal(TemporalType.TIMESTAMP)
    private Date dateValue;

    @Basic
    @Min(0)
    @Max(1)
    private Integer booleanValue;

    private Long longValue;

    private Double doubleValue;

    public abstract Long getId();

    public Boolean getBooleanValue() {
        return booleanValue == null
                ? null
                : isBooleanAsInteger(booleanValue);
    }

    public void setBooleanValue(final Boolean booleanValue) {
        this.booleanValue = booleanValue == null
                ? null
                : getBooleanAsInteger(booleanValue);
    }

    public Date getDateValue() {
        return dateValue == null
                ? null
                : new Date(dateValue.getTime());
    }

    public void setDateValue(final Date dateValue) {
        this.dateValue = dateValue == null
                ? null
                : new Date(dateValue.getTime());
    }

    public Double getDoubleValue() {
        return doubleValue;
    }

    public void setDoubleValue(final Double doubleValue) {
        this.doubleValue = doubleValue;
    }

    public Long getLongValue() {
        return longValue;
    }

    public void setLongValue(final Long longValue) {
        this.longValue = longValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(final String stringValue) {
        this.stringValue = stringValue;
    }

    public <T extends AbstractAttrValue> void parseValue(final AbstractSchema schema, final String value)
            throws ParsingValidationException {

        Exception exception = null;

        switch (schema.getType()) {

            case Boolean:
                this.setBooleanValue(Boolean.parseBoolean(value));
                break;

            case Long:
                try {
                    this.setLongValue(schema.getConversionPattern() == null
                            ? Long.valueOf(value)
                            : DataFormat.parseNumber(value, schema.getConversionPattern()).longValue());
                } catch (Exception pe) {
                    exception = pe;
                }
                break;

            case Double:
                try {
                    this.setDoubleValue(schema.getConversionPattern() == null
                            ? Double.valueOf(value)
                            : DataFormat.parseNumber(value, schema.getConversionPattern()).doubleValue());
                } catch (Exception pe) {
                    exception = pe;
                }
                break;

            case Date:
                try {
                    this.setDateValue(schema.getConversionPattern() == null
                            ? DataFormat.parseDate(value)
                            : new Date(DataFormat.parseDate(value, schema.getConversionPattern()).getTime()));
                } catch (Exception pe) {
                    exception = pe;
                }
                break;


            case String:
            case Enum:
            default:
                this.setStringValue(value);
        }

        if (exception != null) {
            throw new ParsingValidationException("While trying to parse '" + value + "' as " + schema.getName(),
                    exception);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) (booleanValue != null
                ? getBooleanValue()
                : (dateValue != null
                ? getDateValue()
                : (doubleValue != null
                ? getDoubleValue()
                : (longValue != null
                ? getLongValue()
                : stringValue))));
    }

    public String getValueAsString() {
        String result = null;

        final AttributeSchemaType type = getAttribute() == null || getAttribute().getSchema() == null
                || getAttribute().getSchema().getType() == null
                ? AttributeSchemaType.String
                : getAttribute().getSchema().getType();

        switch (type) {

            case Boolean:
                result = getBooleanValue().toString();
                break;

            case Long:
                result = getAttribute().getSchema().getConversionPattern() == null
                        ? getLongValue().toString()
                        : DataFormat.format(getLongValue(), getAttribute().getSchema().getConversionPattern());
                break;

            case Double:
                result = getAttribute().getSchema().getConversionPattern() == null
                        ? getDoubleValue().toString()
                        : DataFormat.format(getDoubleValue(), getAttribute().getSchema().getConversionPattern());
                break;

            case Date:
                result = getAttribute().getSchema().getConversionPattern() == null
                        ? DataFormat.format(getDateValue())
                        : DataFormat.format(getDateValue(), false, getAttribute().getSchema().getConversionPattern());
                break;

            default:
                // applied to String and Enum SchemaType
                result = getStringValue();
                break;
        }

        return result;
    }

    public abstract <T extends AbstractAttr> T getAttribute();

    public abstract <T extends AbstractAttr> void setAttribute(T attribute);

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
}
