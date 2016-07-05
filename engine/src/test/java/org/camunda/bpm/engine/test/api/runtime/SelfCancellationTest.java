/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.runtime;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.TerminateEventDefinition;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * Tests for when delegate code synchronously cancels the activity instance it belongs to.
 *
 * @author Thorben Lindhauer
 */
public class SelfCancellationTest {

  protected static final String MESSAGE = "Message";

  public ProcessEngineRule processEngineRule = new ProvidedProcessEngineRule();
  public ProcessEngineTestRule testHelper = new ProcessEngineTestRule(processEngineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(processEngineRule).around(testHelper);

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask()
        .camundaClass(SendMessageDelegate.class.getName())
      .endEvent()
      .moveToLastGateway()
      .receiveTask()
      .message(MESSAGE)
      .endEvent("terminateEnd")
      .done();


  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK_AND_USER_TASK_AFTER_SEND = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask()
        .camundaClass(SendMessageDelegate.class.getName())
      .userTask()
      .endEvent()
      .moveToLastGateway()
      .receiveTask()
      .message(MESSAGE)
      .endEvent("terminateEnd")
      .done();

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask()
        .camundaClass(SendMessageDelegate.class.getName())
      .moveToLastGateway()
      .receiveTask()
      .message(MESSAGE)
      .endEvent("terminateEnd")
      .done();

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK_WITHOUT_END_AFTER_SEND = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask()
        .camundaClass(SendMessageDelegate.class.getName())
//        .boundaryEvent("boundary")
//        .timerWithDuration("PT5S")
      .endEvent()
      .moveToLastGateway()
      .receiveTask()
      .message(MESSAGE)
      .endEvent("terminateEnd")
      .done();

  static {
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK, "terminateEnd");
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK_AND_USER_TASK_AFTER_SEND, "terminateEnd");
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE, "terminateEnd");
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITHOUT_END_AFTER_SEND, "terminateEnd");
  }

  public static void initEndEvent(BpmnModelInstance modelInstance, String endEventId) {
    EndEvent endEvent = modelInstance.getModelElementById(endEventId);
    TerminateEventDefinition terminateDefinition = modelInstance.newInstance(TerminateEventDefinition.class);
    endEvent.addChildElement(terminateDefinition);
  }

  @BeforeClass
  public static void init() throws Exception {
    org.h2.tools.Server.createWebServer("-web").start();
  }

  @Test
  public void testTriggerParallelTerminateEndEvent() throws Exception {
    RuntimeService runtimeService = processEngineRule.getRuntimeService();
    TaskService taskService = processEngineRule.getTaskService();

    // given
    testHelper.deploy(PROCESS_WITH_CANCELING_RECIEVE_TASK);
    runtimeService.startProcessInstanceByKey("process");

    Task task = taskService.createTaskQuery().singleResult();

    // when
    taskService.complete(task.getId());

    // then
    Assert.assertEquals(0, runtimeService.createProcessInstanceQuery().count());
  }

  @Test
  public void testTriggerParallelTerminateEndEventWithUserTask() throws Exception {
    RuntimeService runtimeService = processEngineRule.getRuntimeService();
    TaskService taskService = processEngineRule.getTaskService();

    // given
    testHelper.deploy(PROCESS_WITH_CANCELING_RECIEVE_TASK_AND_USER_TASK_AFTER_SEND);
    runtimeService.startProcessInstanceByKey("process");

    Task task = taskService.createTaskQuery().singleResult();

    // when
    taskService.complete(task.getId());

    // then
    Assert.assertEquals(0, runtimeService.createProcessInstanceQuery().count());
  }


  @Test
  public void testTriggerParallelTerminateEndEventWithoutEndAfterSend() throws Exception {
    RuntimeService runtimeService = processEngineRule.getRuntimeService();
    TaskService taskService = processEngineRule.getTaskService();

    // given
    testHelper.deploy(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE);
    runtimeService.startProcessInstanceByKey("process");

    Task task = taskService.createTaskQuery().singleResult();

    // when
    taskService.complete(task.getId());

    // then
    Assert.assertEquals(0, runtimeService.createProcessInstanceQuery().count());
  }

  @Test
  public void testTriggerParallelTerminateEndEventWithSendAsScope() throws Exception {
    RuntimeService runtimeService = processEngineRule.getRuntimeService();
    TaskService taskService = processEngineRule.getTaskService();

    // given
    testHelper.deploy(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITHOUT_END_AFTER_SEND);
    runtimeService.startProcessInstanceByKey("process");

    Task task = taskService.createTaskQuery().singleResult();

    // when
    taskService.complete(task.getId());

    // then
    Assert.assertEquals(0, runtimeService.createProcessInstanceQuery().count());
  }

  public static class SendMessageDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
      RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
      runtimeService.correlateMessage(MESSAGE);
    }

  }
}
