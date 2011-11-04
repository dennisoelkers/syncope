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
package org.syncope.core.rest;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.syncope.client.mod.SchedTaskMod;
import org.syncope.client.to.TaskExecTO;
import org.syncope.client.to.PropagationTaskTO;
import org.syncope.client.to.SchedTaskTO;
import org.syncope.client.to.SyncTaskTO;
import org.syncope.client.to.TaskTO;
import org.syncope.client.validation.SyncopeClientCompositeErrorException;
import org.syncope.types.PropagationTaskExecStatus;
import org.syncope.types.SyncopeClientExceptionType;

public class TaskTestITCase extends AbstractTest {

    @Test
    public final void create() {
        SyncTaskTO task = new SyncTaskTO();
        task.setResource("ws-target-resource-2");
        task.addDefaultResource("ws-target-resource-2");
        task.addDefaultRole(8L);

        SyncTaskTO actual = restTemplate.postForObject(
                BASE_URL + "task/create/sync",
                task, SyncTaskTO.class);
        assertNotNull(actual);

        task = restTemplate.getForObject(
                BASE_URL + "task/read/{taskId}", SyncTaskTO.class,
                actual.getId());
        assertNotNull(task);
        assertEquals(actual.getId(), task.getId());
        assertEquals(actual.getJobClassName(), task.getJobClassName());
    }

    @Test
    public final void update() {
        SchedTaskTO task = restTemplate.getForObject(
                BASE_URL + "task/read/{taskId}", SchedTaskTO.class,
                5);
        assertNotNull(task);

        SchedTaskMod taskMod = new SchedTaskMod();
        taskMod.setId(5);
        taskMod.setCronExpression(null);

        SchedTaskTO actual = restTemplate.postForObject(
                BASE_URL + "task/update/sched",
                taskMod, SchedTaskTO.class);
        assertNotNull(actual);
        assertEquals(task.getId(), actual.getId());
        assertNull(actual.getCronExpression());
    }

    @Test
    public final void count() {
        Integer count = restTemplate.getForObject(
                BASE_URL + "task/propagation/count.json", Integer.class);
        assertNotNull(count);
        assertTrue(count > 0);
    }

    @Test
    public final void list() {
        List<PropagationTaskTO> tasks = Arrays.asList(
                restTemplate.getForObject(
                BASE_URL + "task/propagation/list", PropagationTaskTO[].class));
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        for (TaskTO task : tasks) {
            assertNotNull(task);
        }
    }

    @Test
    public final void paginatedList() {
        List<PropagationTaskTO> tasks = Arrays.asList(restTemplate.getForObject(
                BASE_URL + "task/propagation/list/{page}/{size}.json",
                PropagationTaskTO[].class, 1, 2));

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        assertEquals(2, tasks.size());

        for (TaskTO task : tasks) {
            assertNotNull(task);
        }

        tasks = Arrays.asList(restTemplate.getForObject(
                BASE_URL + "task/propagation/list/{page}/{size}.json",
                PropagationTaskTO[].class, 2, 2));

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        for (TaskTO task : tasks) {
            assertNotNull(task);
        }

        tasks = Arrays.asList(restTemplate.getForObject(
                BASE_URL + "task/propagation/list/{page}/{size}.json",
                PropagationTaskTO[].class, 100, 2));

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    public final void listExecutions() {
        List<TaskExecTO> executions = Arrays.asList(
                restTemplate.getForObject(
                BASE_URL + "task/propagation/execution/list",
                TaskExecTO[].class));
        assertNotNull(executions);
        assertFalse(executions.isEmpty());
        for (TaskExecTO execution : executions) {
            assertNotNull(execution);
        }
    }

    @Test
    public final void read() {
        PropagationTaskTO taskTO = restTemplate.getForObject(
                BASE_URL + "task/read/{taskId}", PropagationTaskTO.class, 3);

        assertNotNull(taskTO);
        assertNotNull(taskTO.getExecutions());
        assertTrue(taskTO.getExecutions().isEmpty());
    }

    @Test
    public final void readExecution() {
        TaskExecTO taskTO = restTemplate.getForObject(
                BASE_URL + "task/execution/read/{taskId}",
                TaskExecTO.class, 1);
        assertNotNull(taskTO);
    }

    @Test
    public final void deal() {
        try {
            restTemplate.delete(BASE_URL + "task/delete/{taskId}", 0);
        } catch (HttpStatusCodeException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }

        TaskExecTO execution = restTemplate.postForObject(
                BASE_URL + "task/execute/{taskId}", null,
                TaskExecTO.class, 1);
        assertEquals(PropagationTaskExecStatus.SUBMITTED.toString(),
                execution.getStatus());

        Exception exception = null;
        try {
            restTemplate.delete(BASE_URL + "task/delete/{taskId}", 1);
        } catch (SyncopeClientCompositeErrorException scce) {
            assertTrue(scce.hasException(
                    SyncopeClientExceptionType.IncompletePropagationTaskExec));
            exception = scce;
        }
        assertNotNull(exception);

        execution = restTemplate.getForObject(
                BASE_URL + "task/execution/report/{executionId}"
                + "?executionStatus=SUCCESS&message=OK",
                TaskExecTO.class, execution.getId());
        assertEquals(PropagationTaskExecStatus.SUCCESS.toString(),
                execution.getStatus());
        assertEquals("OK", execution.getMessage());

        restTemplate.delete(BASE_URL + "task/delete/{taskId}", 1);
        try {
            restTemplate.getForObject(
                    BASE_URL + "task/execution/read/{executionId}",
                    TaskExecTO.class, execution.getId());
        } catch (HttpStatusCodeException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    public void sync() {
        Integer usersPre = restTemplate.getForObject(
                BASE_URL + "user/count.json", Integer.class);
        assertNotNull(usersPre);

        TaskExecTO execution = restTemplate.postForObject(
                BASE_URL + "task/execute/{taskId}", null,
                TaskExecTO.class, 4);
        assertEquals("JOB_FIRED", execution.getStatus());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }

        Integer usersPost = restTemplate.getForObject(
                BASE_URL + "user/count.json", Integer.class);
        assertNotNull(usersPost);

        assertTrue("Expected " + (usersPre + 10) + ", found " + usersPost,
                usersPost == usersPre + 10);
    }

    @Test
    public void issue196() {
        TaskExecTO execution = restTemplate.postForObject(
                BASE_URL + "task/execute/{taskId}", null,
                TaskExecTO.class, 6);
        assertNotNull(execution);
        assertEquals(0, execution.getId());
        assertNotNull(execution.getTask());
    }

    @Test
    public void dryRun() {
        TaskExecTO execution = restTemplate.postForObject(
                BASE_URL + "task/execute/{taskId}?dryRun=true", null,
                TaskExecTO.class, 4);
        assertNotNull(execution);
        assertEquals("JOB_FIRED", execution.getStatus());
    }
}
