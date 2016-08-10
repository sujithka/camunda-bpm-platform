package org.camunda.bpm.engine.test.bpmn.async;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.IntermediateThrowEventImpl;
import org.camunda.bpm.model.bpmn.impl.instance.camunda.CamundaFailedJobRetryTimeCycleImpl;
import org.camunda.bpm.model.bpmn.instance.BpmnModelElementInstance;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaFailedJobRetryTimeCycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class RetryCmdDeployment {

  public static final String FAILING_EVENT = "failingEvent";
  public static final String PROCESS_ID = "failedIntermediateThrowingEventAsync";
  private static final String SCHEDULE = "R5/PT5M";
  private static final String PROCESS_ID_2 = "failingSignalProcess";
  private BpmnModelInstance[] bpmnModelInstances;

  public static RetryCmdDeployment deployment() {
    return new RetryCmdDeployment();
  }

  public static BpmnModelInstance prepareSignalEventProcess() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
          .intermediateThrowEvent(FAILING_EVENT)
            .camundaAsyncBefore(true)
            .signal("start")
        .endEvent()
        .done();
    return withRetryCycle(modelInstance,FAILING_EVENT);
  }


  public static BpmnModelInstance prepareSignalFailure() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_ID_2)
        .startEvent()
            .signal("start")
          .serviceTask()
            .camundaClass(FailingDelegate.class.getName())
        .endEvent()
        .done();
    return modelInstance;
  }

  private static BpmnModelInstance withRetryCycle(BpmnModelInstance modelInstance,String activityId) {
    CamundaFailedJobRetryTimeCycle result = modelInstance.newInstance(CamundaFailedJobRetryTimeCycle.class);
    result.setTextContent(SCHEDULE);
    ((IntermediateThrowEventImpl)modelInstance.getModelElementById(activityId)).builder().addExtensionElement(result);
    return modelInstance;
  }

  public static BpmnModelInstance prepareMessageEventProcess() {
    return Bpmn.createExecutableProcess("failedIntermediateThrowingEventAsync")
        .startEvent()
          .intermediateThrowEvent(FAILING_EVENT)
            .camundaAsyncBefore(true)

              .message("start")

        .endEvent()
        .done();
  }

  public static BpmnModelInstance prepareMessageFailure() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_ID_2)
        .startEvent()
          .message("start")
          .serviceTask()
            .camundaClass(FailingDelegate.class.getName())
        .endEvent()
        .done();
    return modelInstance;
  }

  public static BpmnModelInstance prepareEscalationEventProcess() {
    return Bpmn.createExecutableProcess("failedIntermediateThrowingEventAsync")
        .startEvent()
        .endEvent()
        .done();
  }

  public RetryCmdDeployment withEventProcess(BpmnModelInstance... bpmnModelInstances) {
    this.bpmnModelInstances = bpmnModelInstances;
    return this;
  }

  public static Collection<RetryCmdDeployment[]> asParameters(RetryCmdDeployment... deployments) {
    List<RetryCmdDeployment[]> deploymentList = new ArrayList<RetryCmdDeployment[]>();
    for (RetryCmdDeployment deployment : deployments) {
      deploymentList.add(new RetryCmdDeployment[]{ deployment });
    }

    return deploymentList;
  }

  public BpmnModelInstance[] getBpmnModelInstances() {
    return bpmnModelInstances;
  }

  public void setBpmnModelInstances(BpmnModelInstance[] bpmnModelInstances) {
    this.bpmnModelInstances = bpmnModelInstances;
  }
}
