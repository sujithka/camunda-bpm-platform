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
package org.camunda.bpm.engine.impl.cmd;

import java.io.Serializable;
import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;

/**
 * Command to delete a process definition form a deployment.
 *
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class DeleteProcessDefinitionCmd implements Command<Void>, Serializable {

  private final String processDefinitionId;

  public DeleteProcessDefinitionCmd(String toDeletedProcDef) {
    this.processDefinitionId = toDeletedProcDef;
  }

  @Override
  public Void execute(CommandContext commandContext) {
    ensureNotNull("processDefinitionId", processDefinitionId);

    ProcessDefinitionEntity processDefinition = commandContext.getProcessDefinitionManager()
                                                .findLatestProcessDefinitionById(processDefinitionId);
    ensureNotNull("No process definition found with id '" + processDefinitionId + "'", "processDefinition", processDefinition);

    for(CommandChecker checker : commandContext.getProcessEngineConfiguration().getCommandCheckers()) {
      checker.checkDeleteProcessDefinitionById(processDefinitionId);
    }

    ProcessEngineConfigurationImpl processEngineConfiguration = commandContext.getProcessEngineConfiguration();
    //at first delete proc def from cache
    processEngineConfiguration.getDeploymentCache().removeProcessDefinition(processDefinitionId);
    //at second delete proc def from db
    commandContext.getDbEntityManager().delete(ProcessDefinitionEntity.class, "deleteProcessDefinitionsById", processDefinitionId);
    return null;
  }
}
