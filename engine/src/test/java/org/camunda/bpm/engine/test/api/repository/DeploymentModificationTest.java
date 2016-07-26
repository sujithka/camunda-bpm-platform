/*
 * Copyright 2016 camunda services GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.bpm.engine.test.api.repository;

import java.io.Serializable;
import java.util.List;
import static junit.framework.TestCase.assertNotNull;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class DeploymentModificationTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  protected RepositoryService repositoryService;
  protected RuntimeService runtimeService;
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  @BeforeClass
  public static void initClass() throws Exception {
    org.h2.tools.Server.createWebServer("-web").start();
  }

  @Before
  public void initServices() {
    repositoryService = engineRule.getRepositoryService();
    runtimeService = engineRule.getRuntimeService();
    processEngineConfiguration = (ProcessEngineConfigurationImpl) engineRule.getProcessEngine().getProcessEngineConfiguration();
  }

  @Test
  public void testDeleteProcessDefinition() {
    // given deployment with two process definition in one xml model file
    Deployment deployment = repositoryService.createDeployment()
                                             .addClasspathResource("org/camunda/bpm/engine/test/repository/twoProcesses.bpmn20.xml")
                                              .deploy();
    List<ProcessDefinition> processDefinitions = repositoryService.createProcessDefinitionQuery().list();
    assertEquals(2, processDefinitions.size());

    //when a process definition is been deleted
    ProcessDefinition toDeletedProcDef = processDefinitions.get(0);
    //at first delete proc def from cache
    processEngineConfiguration.getDeploymentCache().removeProcessDefinition(toDeletedProcDef.getId());
    //at second delete proc def from db
    processEngineConfiguration.getCommandExecutorTxRequired().execute(new DeleteProcessDefinitionCmd(toDeletedProcDef.getId()));

    //then only one process definition should exist
    assertEquals(1, repositoryService.createProcessDefinitionQuery().count());

    //Clearing the cache and starting the existing process definition
    processEngineConfiguration.getDeploymentCache().discardProcessDefinitionCache();
    assertEquals(0, processEngineConfiguration.getDeploymentCache().getProcessDefinitionCache().size());
    ProcessInstanceWithVariables procInst = runtimeService.createProcessInstanceByKey("two").executeWithVariablesInReturn();
    assertNotNull(procInst);
    assert(procInst.getProcessDefinitionId().contains("two"));
    //should refill the cache
    assertEquals(1, processEngineConfiguration.getDeploymentCache().getProcessDefinitionCache().size());
    //The deleted process definition should not be recreated after the cache is refilled
    assertEquals(1, repositoryService.createProcessDefinitionQuery().count());

    //clean up
    repositoryService.deleteDeployment(deployment.getId());
    engineRule.getHistoryService().deleteHistoricProcessInstance(procInst.getId());
  }


  private class DeleteProcessDefinitionCmd implements Command<Void>, Serializable {
    private final String procDefId;

    public DeleteProcessDefinitionCmd(String procDefId) {
      this.procDefId = procDefId;
    }

    @Override
    public Void execute(CommandContext commandContext) {
      commandContext.getDbEntityManager().delete(ProcessDefinitionEntity.class, "deleteProcessDefinitionsById", procDefId);
      return null;
    }
  }
}
