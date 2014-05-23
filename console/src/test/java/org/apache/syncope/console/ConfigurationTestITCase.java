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

public class ConfigurationTestITCase extends AbstractTest {

    @Test
    public void browseCreateModal() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//a[contains(text(),'Create new configuration')]");

        selenium.waitForCondition("selenium.isElementPresent(\"//input[@name='key:textField']\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//iframe\");", "30000");
        selenium.selectFrame("index=0");

        selenium.type("name=key:textField", "test1");
        selenium.type("name=value:textField", "value1");
        selenium.click("name=apply");

        seleniumDriver.switchTo().defaultContent();

        selenium.waitForCondition("selenium.isTextPresent(\"Operation executed successfully\");", "30000");
    }

    @Test
    public void browseEditModal() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//tr/td[3]/div/span[12]/a");

        selenium.waitForCondition("selenium.isElementPresent(\"//input[@name='key:textField']\");", "30000");

        assertEquals("authentication.statuses", selenium.getAttribute("//input[@name='key:textField']@value"));

        selenium.click("css=a.w_close");
    }

    @Test
    public void browsePasswordPolicy() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[2]/a");
        selenium.click("//div[@id='password']/span/div/a");

        selenium.waitForCondition("selenium.isElementPresent(\"//input[@name='id:textField']\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//iframe\");", "30000");
        selenium.selectFrame("index=0");

        selenium.type("name=description:textField", "new description");
        selenium.click("//div[2]/form/div[3]/input[@type='submit']");

        seleniumDriver.switchTo().defaultContent();

        selenium.waitForCondition("selenium.isTextPresent(\"new description\");", "30000");
    }

    @Test
    public void browseWorkflowDef() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[4]/a/span");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='workflow']/div/span/img\");", "30000");
    }

    @Test
    public void delete() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//tr[6]/td[3]/div/span[14]/a");

        assertTrue(selenium.getConfirmation().equals("Do you really want to delete the selected item(s)?"));

        selenium.waitForCondition("selenium.isTextPresent(" + "\"Operation executed successfully\");", "30000");
    }

    @Test
    public void setLogLevel() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[5]/a");

        selenium.select("//div[@id='core']/div/span/table/tbody/tr/td[2]/select", "label=ERROR");

        selenium.waitForCondition("selenium.isTextPresent(\"Operation executed successfully\");", "30000");
    }

    @Test
    public void createNotification() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[3]/a");

        selenium.click("//a[contains(text(),'Create new notification')]");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div/div/div/div/label\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//input[@name='sender:textField']\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//iframe\");", "30000");
        selenium.selectFrame("index=0");

        selenium.type("name=sender:textField", "test@syncope.it");

        selenium.type("name=subject:textField", "test@syncope.it");

        selenium.select("//div[2]/form/div[3]/div/div/div[3]/div[2]/span/select", "label=UserSchema");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div/div/div[4]/div[2]/span/select/option[2]\");", "30000");

        selenium.select("//div[2]/form/div[3]/div/div/div[4]/div[2]/span/select", "label=fullname");

        selenium.select("//div[2]/form/div[3]/div/div/div[5]/div[2]/span/select", "label=optin");

        selenium.select("//div[2]/form/div[3]/div/div/div[6]/div[2]/span/select", "label=ALL");

        selenium.click("//div[2]/form/div[3]/ul/li[3]/a/span");

        selenium.click("//div[2]/form/div[3]/ul/li[2]/a/span");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:type:dropDownChoiceField']"
                + "/option[text()='PROPAGATION']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:type:dropDownChoiceField']",
                "label=PROPAGATION");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:category:dropDownChoiceField']"
                + "/option[text()='role']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:category:dropDownChoiceField']",
                "label=role");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:subcategory:dropDownChoiceField']"
                + "/option[text()='resource-db-sync']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:subcategory:dropDownChoiceField']",
                "label=resource-db-sync");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//input[@name='eventSelection:eventsContainer:eventsPanel:failureGroup']\");",
                "30000");

        selenium.click("//div[@class='eventSelectionWidzard']/div[2]/div[3]/span/div/input");

        selenium.click("//div[2]/form/div[3]/ul/li[4]/a/span");

        selenium.click("//div[2]/form/div[3]/div[4]/div/div/span/input");

        selenium.type(
                "name=staticRecipients:multiValueContainer:view:0:panel:textField", "syncope445@syncope.apache.org");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div[4]/div/div[2]/label\");", "30000");

        selenium.click("//div[2]/form/div[4]/input");

        seleniumDriver.switchTo().defaultContent();
    }

    @Test
    public void createDisabledNotification() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[3]/a");

        selenium.click("//a[contains(text(),'Create new notification')]");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div/div/div/div/label\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//input[@name='sender:textField']\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//iframe\");", "30000");
        selenium.selectFrame("index=0");

        selenium.type("name=sender:textField", "test@syncope.it");

        selenium.type("name=sender:textField", "test@syncope.it");

        selenium.select("//div[2]/form/div[3]/div/div/div[3]/div[2]/span/select", "label=UserSchema");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div/div/div[4]/div[2]/span/select/option[2]\");", "30000");

        selenium.select("//div[2]/form/div[3]/div/div/div[4]/div[2]/span/select", "label=fullname");

        selenium.select("//div[2]/form/div[3]/div/div/div[5]/div[2]/span/select", "label=optin");

        selenium.select("//div[2]/form/div[3]/div/div/div[6]/div[2]/span/select", "label=ALL");

        // disable notification
        selenium.click("//div[2]/form/div[3]/div/div/div[7]/div[2]/span/input");

        selenium.click("//div[2]/form/div[3]/ul/li[3]/a/span");

        selenium.click("//div[2]/form/div[3]/ul/li[2]/a/span");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:type:dropDownChoiceField']"
                + "/option[text()='PROPAGATION']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:type:dropDownChoiceField']",
                "label=PROPAGATION");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:category:dropDownChoiceField']"
                + "/option[text()='role']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:category:dropDownChoiceField']",
                "label=role");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:subcategory:dropDownChoiceField']"
                + "/option[text()='resource-db-sync']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:subcategory:dropDownChoiceField']",
                "label=resource-db-sync");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//input[@name='eventSelection:eventsContainer:eventsPanel:failureGroup']\");",
                "30000");

        selenium.click("//div[@class='eventSelectionWidzard']/div[2]/div[3]/span/div/input");

        selenium.click("//div[2]/form/div[3]/ul/li[4]/a/span");

        selenium.click("//div[2]/form/div[3]/div[4]/div/div/span/input");

        selenium.type(
                "name=staticRecipients:multiValueContainer:view:0:panel:textField", "syncope492@syncope.apache.org");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div[4]/div/div[2]/label\");", "30000");

        selenium.click("//div[2]/form/div[4]/input");

        seleniumDriver.switchTo().defaultContent();
    }

    @Test
    public void issueSYNCOPE189() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//a[contains(text(),'Create new configuration')]");

        selenium.waitForCondition("selenium.isElementPresent(\"//input[@name='key:textField']\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//iframe\");", "30000");
        selenium.selectFrame("index=0");

        selenium.keyPressNative("27");

        seleniumDriver.switchTo().defaultContent();
    }

    @Test
    public void issueSYNCOPE446() {
        selenium.click("css=img[alt=\"Configuration\"]");

        selenium.waitForCondition("selenium.isElementPresent(\"//div[@id='tabs']\");", "30000");

        selenium.click("//div[@id='tabs']/ul/li[3]/a");

        selenium.click("//a[contains(text(),'Create new notification')]");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div/div/div/div/label\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//input[@name='sender:textField']\");", "30000");

        selenium.waitForCondition("selenium.isElementPresent(\"//iframe\");", "30000");
        selenium.selectFrame("index=0");

        selenium.type("name=sender:textField", "syncope446@syncope.it");

        selenium.type("name=subject:textField", "Test issue Syncope 446");

        selenium.select("//div[2]/form/div[3]/div/div/div[3]/div[2]/span/select", "label=UserSchema");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//div[2]/form/div[3]/div/div/div[4]/div[2]/span/select/option[2]\");", "30000");

        selenium.select("//div[2]/form/div[3]/div/div/div[4]/div[2]/span/select", "label=email");

        selenium.select("//div[2]/form/div[3]/div/div/div[5]/div[2]/span/select", "label=optin");

        selenium.select("//div[2]/form/div[3]/div/div/div[6]/div[2]/span/select", "label=ALL");

        selenium.click("//div[2]/form/div[3]/ul/li[3]/a/span");

        selenium.click("//div[2]/form/div[3]/ul/li[2]/a/span");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:type:dropDownChoiceField']"
                + "/option[text()='REST']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:type:dropDownChoiceField']",
                "label=REST");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='eventSelection:categoryContainer:category:dropDownChoiceField']"
                + "/option[text()='RoleController']\");",
                "30000");

        selenium.select(
                "//select[@name='eventSelection:categoryContainer:category:dropDownChoiceField']",
                "label=RoleController");

        selenium.waitForCondition("selenium.isElementPresent("
                + "\"//input[@name='eventSelection:eventsContainer:eventsPanel:successGroup']\");",
                "30000");

        selenium.click("//div[@class='eventSelectionWidzard']/div[2]/div[3]/span/div/input");

        selenium.click("//div[2]/form/div[3]/ul/li[3]/a/span");
        selenium.click("//div[2]/form/div[3]/div[3]/span/div[4]/div/span/input");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='aboutContainer:roleAbout:searchFormContainer:searchView:0:type']"
                + "/option[text()='ENTITLEMENT']\");",
                "30000");

        selenium.select(
                "//select[@name='aboutContainer:roleAbout:searchFormContainer:searchView:0:type']",
                "label=ENTITLEMENT");

        selenium.waitForCondition("selenium.isElementPresent(\""
                + "//select[@name='aboutContainer:roleAbout:searchFormContainer:searchView:0:property']"
                + "/option[text()='ROLE_CREATE']\");",
                "30000");

        selenium.select(
                "//select[@name='aboutContainer:roleAbout:searchFormContainer:searchView:0:property']",
                "label=ROLE_CREATE");
        
        selenium.click("//div[2]/form/div[3]/ul/li[4]/a/span");
        
        selenium.click("//input[@name='recipientsContainer:checkRecipients:checkboxField']");

        selenium.type(
                "name=staticRecipients:multiValueContainer:view:0:panel:textField", "syncope446@syncope.apache.org");

        selenium.click("//div[2]/form/div[4]/input");

        seleniumDriver.switchTo().defaultContent();
    }
}
