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
package org.apache.syncope.common.report;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.syncope.common.annotation.FormAttributeField;
import org.apache.syncope.common.search.NodeCond;
import org.apache.syncope.common.types.IntMappingType;

@XmlRootElement(name = "RoleReportletConfiguration")
@XmlType
public class RoleReportletConf extends AbstractReportletConf {

    private static final long serialVersionUID = -8488503068032439699L;

    @XmlEnum
    @XmlType(name = "RoleReportletConfigurationFeature")
    public enum Feature {

        id,
        name,
        roleOwner,
        userOwner,
        entitlements,
        users,
        resources

    }

    private NodeCond matchingCond;

    @FormAttributeField(schema = IntMappingType.RoleSchema)
    private List<String> attrs;

    @FormAttributeField(schema = IntMappingType.RoleDerivedSchema)
    private List<String> derAttrs;

    @FormAttributeField(schema = IntMappingType.RoleVirtualSchema)
    private List<String> virAttrs;

    private List<Feature> features;

    public RoleReportletConf() {
        super();
    }

    public RoleReportletConf(final String name) {
        super(name);

        attrs = new ArrayList<String>();
        derAttrs = new ArrayList<String>();
        virAttrs = new ArrayList<String>();
        features = new ArrayList<Feature>();
    }

    @XmlElementWrapper(name = "normalAttributes")
    @XmlElement(name = "attribute")
    public List<String> getAttrs() {
        return attrs;
    }

    public void setAttrs(final List<String> attrs) {
        this.attrs = attrs;
    }

    @XmlElementWrapper(name = "derivedAttributes")
    @XmlElement(name = "attribute")
    public List<String> getDerAttrs() {
        return derAttrs;
    }

    public void setDerAttrs(final List<String> derAttrs) {
        this.derAttrs = derAttrs;
    }

    @XmlElementWrapper(name = "features")
    @XmlElement(name = "feature")
    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(final List<Feature> features) {
        this.features = features;
    }

    public NodeCond getMatchingCond() {
        return matchingCond;
    }

    public void setMatchingCond(final NodeCond matchingCond) {
        this.matchingCond = matchingCond;
    }

    @XmlElementWrapper(name = "virtualAttributes")
    @XmlElement(name = "attribute")
    public List<String> getVirAttrs() {
        return virAttrs;
    }

    public void setVirAttrs(final List<String> virAttrs) {
        this.virAttrs = virAttrs;
    }
}
