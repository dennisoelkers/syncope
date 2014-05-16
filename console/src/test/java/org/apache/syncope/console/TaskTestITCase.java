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

import org.junit.Test;

public class TaskTestITCase extends AbstractTest {

    @Test
    public void execute() {
        selenium.click("css=img[alt=\"Tasks\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[4]/a");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[4]/span/div/span/span/span/form/span/table/tbody/tr/td[9]/div/span[6]/a/img\");", "30000");

        selenium.click("//div[4]/span/div/span/span/span/form/span/table/tbody/tr/td[9]/div/span[6]/a/img");

        selenium.waitForCondition("selenium.isTextPresent(" + "\"Operation executed successfully\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[4]/span/div/span/span/span/form/span/table/tbody/tr/td[9]/div/span[12]/a/img\");", "30000");

        selenium.click("//div[4]/span/div/span/span/span/form/span/table/tbody/tr/td[9]/div/span[12]/a/img");

        selenium.waitForCondition("selenium.isElementPresent(\"//iframe\");", "30000");
        selenium.selectFrame("index=0");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[2]/ul/li[3]/a\");", "30000");

        selenium.click("//div[2]/form/div[2]/ul/li[3]/a");

        assertTrue(selenium.isElementPresent("//div[2]/form/div[2]/div[3]/span/table/tbody/tr/td[2]"));

        seleniumDriver.switchTo().defaultContent();

        selenium.click("css=a.w_close");
    }

    @Test
    public void delete() {
        selenium.click("css=img[alt=\"Tasks\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[3]/a");

        selenium.waitForCondition("selenium.isElementPresent(\"xpath=(//img[@alt='delete icon'])[6]\");", "30000");

        selenium.click("xpath=(//img[@alt='delete icon'])[6]"); // 

        selenium.waitForCondition("selenium.isTextPresent(\"Operation executed successfully\");", "30000");
    }

    @Test
    public void issueSYNCOPE148() {
        selenium.click("css=img[alt=\"Tasks\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[3]/a/span");
        selenium.click("//a[contains(text(),'Create new task')]");

        selenium.waitForCondition(
                "selenium.isElementPresent(\"//div[2]/form/div[2]/div/div/span/div/div[2]/div/label\");", "30000");

        selenium.selectFrame("index=0");

        selenium.click("//div[2]/form/div[3]/input[2]");

        seleniumDriver.switchTo().defaultContent();

        selenium.waitForCondition("selenium.isTextPresent(\"Id\");", "30000");
    }
}
