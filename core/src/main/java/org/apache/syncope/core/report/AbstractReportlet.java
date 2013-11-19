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
package org.apache.syncope.core.report;

import org.apache.syncope.common.report.AbstractReportletConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public abstract class AbstractReportlet<T extends AbstractReportletConf> implements Reportlet<T> {

    /**
     * Logger.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractReportlet.class);

    protected T conf;

    public T getConf() {
        return conf;
    }

    @Override
    public void setConf(final T conf) {
        this.conf = conf;
    }

    protected abstract void doExtract(ContentHandler handler) throws SAXException, ReportException;

    @Override
    @Transactional(readOnly = true)
    public void extract(final ContentHandler handler) throws SAXException, ReportException {

        if (conf == null) {
            throw new ReportException(new IllegalArgumentException("No configuration provided"));
        }

        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "", ReportXMLConst.ATTR_NAME, ReportXMLConst.XSD_STRING, conf.getName());
        atts.addAttribute("", "", ReportXMLConst.ATTR_CLASS, ReportXMLConst.XSD_STRING, getClass().getName());
        handler.startElement("", "", ReportXMLConst.ELEMENT_REPORTLET, atts);

        doExtract(handler);

        handler.endElement("", "", ReportXMLConst.ELEMENT_REPORTLET);
    }
}
