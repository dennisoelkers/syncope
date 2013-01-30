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
package org.apache.syncope.console;

import org.junit.Before;
import org.junit.Test;

public class EditProfileTestITCase extends AbstractTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp(BASE_URL, "*firefox");
    }

    @Test
    public void selfRegistration() {
        selenium.open("/syncope-console/");

        selenium.click("//div/span/span/a");

        selenium.waitForCondition("selenium.isElementPresent(\"//span[contains(text(),'Attributes')]\");", "30000");

        selenium.click("css=a.w_close");

        // only to have some "Logout" availabe for @After
        selenium.type("name=userId", "user1");
        selenium.type("name=password", "password");
        selenium.click("name=:submit");

        selenium.waitForPageToLoad("30000");
    }

    @Test
    public void editUserProfile() {
        selenium.open("/syncope-console/");
        selenium.type("name=userId", "user1");
        selenium.type("name=password", "password");
        selenium.click("name=:submit");
        selenium.waitForPageToLoad("30000");

        selenium.click("css=img[alt=\"Schema\"]");
        selenium.waitForPageToLoad("30000");

        selenium.click("//div/ul/li[10]/div/div/a/span");

        selenium.waitForCondition("selenium.isElementPresent(\"//span[contains(text(),'Attributes')]\");", "30000");
        selenium.waitForCondition("selenium.isElementPresent(\"//input[@value='user1']\");", "30000");

        selenium.click("css=a.w_close");
    }
}
