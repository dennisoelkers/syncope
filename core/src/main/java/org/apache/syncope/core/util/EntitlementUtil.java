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
package org.apache.syncope.core.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.syncope.common.SyncopeConstants;

import org.apache.syncope.core.persistence.beans.Entitlement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for manipulating entitlements.
 */
public final class EntitlementUtil {

    private static final Pattern ROLE_ENTITLEMENT_NAME_PATTERN = Pattern.compile("^ROLE_([\\d])+");

    private static final Logger LOG = LoggerFactory.getLogger(EntitlementUtil.class);

    public static String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? SyncopeConstants.ANONYMOUS_USER : authentication.getName();
    }

    public static Set<String> getOwnedEntitlementNames() {
        final Set<String> result = new HashSet<String>();

        final SecurityContext ctx = SecurityContextHolder.getContext();

        if (ctx != null && ctx.getAuthentication() != null && ctx.getAuthentication().getAuthorities() != null) {
            for (GrantedAuthority authority : ctx.getAuthentication().getAuthorities()) {
                result.add(authority.getAuthority());
            }
        }

        return result;
    }

    public static String getEntitlementNameFromRoleId(final Long roleId) {
        return "ROLE_" + roleId;
    }

    public static boolean isRoleEntitlement(final String entitlementName) {
        return ROLE_ENTITLEMENT_NAME_PATTERN.matcher(entitlementName).matches();
    }

    public static Long getRoleId(final String entitlementName) {
        Long result = null;

        if (isRoleEntitlement(entitlementName)) {
            try {
                result = Long.valueOf(entitlementName.substring(entitlementName.indexOf("_") + 1));
            } catch (Exception e) {
                LOG.error("unable to parse {} to Long", entitlementName, e);
            }
        }

        return result;
    }

    public static Set<Long> getRoleIds(final Set<String> entitlements) {
        Set<Long> result = new HashSet<Long>();

        for (String entitlement : entitlements) {
            if (isRoleEntitlement(entitlement)) {
                Long roleId = getRoleId(entitlement);
                if (roleId != null) {
                    result.add(roleId);
                }
            }
        }

        return result;
    }

    public static Set<Long> getRoleIds(final List<Entitlement> entitlements) {
        Set<String> names = new HashSet<String>(entitlements.size());
        for (Entitlement entitlement : entitlements) {
            names.add(entitlement.getName());
        }
        return getRoleIds(names);
    }

    /**
     * Extend the current authentication context to include the given role.
     *
     * @param roleId role id
     */
    public static void extendAuthContext(final Long roleId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>(auth.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(EntitlementUtil.getEntitlementNameFromRoleId(roleId)));
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                auth.getPrincipal(), auth.getCredentials(), authorities);
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    /**
     * Private default constructor, for static-only classes.
     */
    private EntitlementUtil() {
    }
}
