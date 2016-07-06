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

import java.util.List;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.impl.pvm.delegate.SignallableActivityBehavior;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import static org.camunda.bpm.engine.test.api.runtime.migration.ModifiableBpmnModelInstance.modify;
import org.camunda.bpm.engine.test.bpmn.executionlistener.RecorderExecutionListener;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.TerminateEventDefinition;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
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

  //========================================================================================================================
  //=======================================================MODELS===========================================================
  //========================================================================================================================

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask("sendTask")
        .camundaClass(SendMessageDelegate.class.getName())
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .endEvent("endEvent")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .moveToLastGateway()
      .receiveTask("recieveTask")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .message(MESSAGE)
      .endEvent("terminateEnd")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .done();

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK_AND_USER_TASK_AFTER_SEND = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask("sendTask")
        .camundaClass(SendMessageDelegate.class.getName())
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .userTask("userTask")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .endEvent()
      .moveToLastGateway()
      .receiveTask("recieveTask")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .message(MESSAGE)
      .endEvent("terminateEnd")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .done();

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK_WITHOUT_END_AFTER_SEND = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask("sendTask")
        .camundaClass(SendMessageDelegate.class.getName())
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .moveToLastGateway()
      .receiveTask("recieveTask")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .message(MESSAGE)
      .endEvent("terminateEnd")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .done();

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask("sendTask")
        .camundaClass(SendMessageDelegate.class.getName())
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
        .boundaryEvent("boundary")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
        .timerWithDuration("PT5S")
        .endEvent("endEventBoundary")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .moveToNode("sendTask")
      .endEvent("endEvent")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .moveToNode("fork")
      .receiveTask("recieveTask")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .message(MESSAGE)
      .endEvent("terminateEnd")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .done();

  public static final BpmnModelInstance PROCESS_WITH_SUBPROCESS_AND_DELEGATE_MSG_SEND = modify(Bpmn.createExecutableProcess("process")
      .startEvent()
      .subProcess()
        .embeddedSubProcess()
          .startEvent()
          .userTask()
          .serviceTask("sendTask")
            .camundaClass(SendMessageDelegate.class.getName())
            .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
          .endEvent("endEventSubProc")
            .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
          .subProcessDone()
      .endEvent()
          .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .done())
      .addSubProcessTo("process")
        .triggerByEvent()
        .embeddedSubProcess()
          .startEvent("startSubEvent")
            .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
            .message(MESSAGE)
          .endEvent("endEventSubEvent")
            .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .done();

  public static final BpmnModelInstance PROCESS_WITH_SEND_TASK_AND_BOUNDARY_RECIEVE_TASK = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
        .userTask()
        .endEvent()
          .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .moveToLastGateway()
      .sendTask("sendTask")
          .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
        .camundaClass(SignalDelegate.class.getName())
        .boundaryEvent("boundary")
          .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
        .message(MESSAGE)
        .endEvent("endEventBoundary")
          .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .moveToNode("sendTask")
      .endEvent("endEvent")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .done();

  public static final BpmnModelInstance PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE_WITHOUT_END = Bpmn.createExecutableProcess("process")
      .startEvent()
      .parallelGateway("fork")
      .userTask()
      .sendTask("sendTask")
        .camundaClass(SendMessageDelegate.class.getName())
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
        .boundaryEvent("boundary")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
        .timerWithDuration("PT5S")
        .endEvent("endEventBoundary")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .moveToNode("fork")
      .receiveTask("recieveTask")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .message(MESSAGE)
      .endEvent("terminateEnd")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .done();

  public static final BpmnModelInstance PROCESS_WITH_SEND_TASK_AND_BOUNDARY_RECIEVE_TASK_WITHOUT_PARALLEL = Bpmn.createExecutableProcess("process")
      .startEvent()
      .sendTask("sendTask")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
        .camundaClass(SignalDelegate.class.getName())
        .boundaryEvent("boundary")
          .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
        .message(MESSAGE)
        .endEvent("endEventBoundary")
          .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_END, RecorderExecutionListener.class.getName())
      .moveToNode("sendTask")
      .endEvent("endEvent")
        .camundaExecutionListenerClass(RecorderExecutionListener.EVENTNAME_START, RecorderExecutionListener.class.getName())
      .done();


  //========================================================================================================================
  //=========================================================INIT===========================================================
  //========================================================================================================================

  static {
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK, "terminateEnd");
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK_AND_USER_TASK_AFTER_SEND, "terminateEnd");
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE, "terminateEnd");
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITHOUT_END_AFTER_SEND, "terminateEnd");
    initEndEvent(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE_WITHOUT_END, "terminateEnd");
  }

  public static void initEndEvent(BpmnModelInstance modelInstance, String endEventId) {
    EndEvent endEvent = modelInstance.getModelElementById(endEventId);
    TerminateEventDefinition terminateDefinition = modelInstance.newInstance(TerminateEventDefinition.class);
    endEvent.addChildElement(terminateDefinition);
  }
  
  //========================================================================================================================
  //=======================================================TESTS============================================================
  //========================================================================================================================

  private void checkRecordedEvents(String ...activityIds) {
    List<RecorderExecutionListener.RecordedEvent> recordedEvents = RecorderExecutionListener.getRecordedEvents();
    assertEquals(activityIds.length, recordedEvents.size());

    for (int i = 0; i < activityIds.length; i++) {
      assertEquals(activityIds[i], recordedEvents.get(i).getActivityId());
    }
  }

  private void testParallelTerminationWithSend(BpmnModelInstance modelInstance) {
    RecorderExecutionListener.clear();
    RuntimeService runtimeService = processEngineRule.getRuntimeService();
    TaskService taskService = processEngineRule.getTaskService();

    // given
    testHelper.deploy(modelInstance);
    runtimeService.startProcessInstanceByKey("process");

    Task task = taskService.createTaskQuery().singleResult();

    // when
    taskService.complete(task.getId());

    // then
    Assert.assertEquals(0, runtimeService.createProcessInstanceQuery().count());
    checkRecordedEvents("recieveTask", "sendTask", "terminateEnd");
  }

  @Test
  public void testTriggerParallelTerminateEndEvent() throws Exception {
    testParallelTerminationWithSend(PROCESS_WITH_CANCELING_RECIEVE_TASK);
  }

  @Test
  public void testTriggerParallelTerminateEndEventWithUserTask() throws Exception {
    testParallelTerminationWithSend(PROCESS_WITH_CANCELING_RECIEVE_TASK_AND_USER_TASK_AFTER_SEND);
  }

  @Test
  public void testTriggerParallelTerminateEndEventWithoutEndAfterSend() throws Exception {
    testParallelTerminationWithSend(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITHOUT_END_AFTER_SEND);
  }

  @Test
  public void testTriggerParallelTerminateEndEventWithSendAsScope() throws Exception {
    testParallelTerminationWithSend(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE);
  }

  @Test
  public void testTriggerParallelTerminateEndEventWithSendAsScopeWithoutEnd() throws Exception {
    testParallelTerminationWithSend(PROCESS_WITH_CANCELING_RECIEVE_TASK_WITH_SEND_AS_SCOPE_WITHOUT_END);
  }

  @Test
  public void testSendMessageInSubProcess() throws Exception {
    RecorderExecutionListener.clear();
    RuntimeService runtimeService = processEngineRule.getRuntimeService();
    TaskService taskService = processEngineRule.getTaskService();

    // given
    testHelper.deploy(PROCESS_WITH_SUBPROCESS_AND_DELEGATE_MSG_SEND);
    runtimeService.startProcessInstanceByKey("process");

    Task task = taskService.createTaskQuery().singleResult();

    // when
    taskService.complete(task.getId());

    // then
    Assert.assertEquals(0, runtimeService.createProcessInstanceQuery().count());
    checkRecordedEvents("sendTask", "startSubEvent", "endEventSubEvent");
  }

  @Test
  public void testSendTaskWithBoundaryRecieveTask() throws Exception {
    RecorderExecutionListener.clear();
    RuntimeService runtimeService = processEngineRule.getRuntimeService();

    // given
    testHelper.deploy(PROCESS_WITH_SEND_TASK_AND_BOUNDARY_RECIEVE_TASK);
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey("process");

    Execution activity = runtimeService.createExecutionQuery().activityId("sendTask").singleResult();
    runtimeService.signal(activity.getId());

    // then
    List<String> activities = runtimeService.getActiveActivityIds(procInst.getId());
    Assert.assertNotNull(activities);
    Assert.assertEquals(1, activities.size());
    checkRecordedEvents("sendTask", "boundary", "endEventBoundary");
  }

  @Test
  public void testSendTaskWithBoundaryRecieveTaskWithoutParallel() throws Exception {
    RecorderExecutionListener.clear();
    RuntimeService runtimeService = processEngineRule.getRuntimeService();

    // given
    testHelper.deploy(PROCESS_WITH_SEND_TASK_AND_BOUNDARY_RECIEVE_TASK_WITHOUT_PARALLEL);
    runtimeService.startProcessInstanceByKey("process");

    Execution activity = runtimeService.createExecutionQuery().activityId("sendTask").singleResult();
    runtimeService.signal(activity.getId());

    // then
    checkRecordedEvents("sendTask", "boundary", "endEventBoundary");
  }

  //========================================================================================================================
  //===================================================STATIC CLASSES=======================================================
  //========================================================================================================================
  public static class SendMessageDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) throws Exception {
      RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
      runtimeService.correlateMessage(MESSAGE);
    }
  }

  public static class SignalDelegate implements SignallableActivityBehavior {
    @Override
    public void signal(ActivityExecution execution, String signalEvent, Object signalData) throws Exception {
      RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
      runtimeService.correlateMessage(MESSAGE);
    }
    @Override
    public void execute(ActivityExecution execution) throws Exception {
    }
  }
}
