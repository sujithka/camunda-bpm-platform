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
package org.camunda.bpm.engine.impl.cmd;

import java.io.InputStream;
import java.io.Serializable;

import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParser;
import org.camunda.bpm.engine.impl.cfg.DefaultBpmnParseFactory;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;

/**
 * @author kristin.polenz
 */
public class ValidateResourceCmd implements Command<Void>, Serializable {

  private static final long serialVersionUID = 1L;

  protected String name;
  protected InputStream resource;

  public ValidateResourceCmd(String name, InputStream resource) {
    this.name = name;
    this.resource = resource;
  }

  public Void execute(CommandContext commandContext) {
    ProcessEngineConfigurationImpl processEngineConfiguration = commandContext.getProcessEngineConfiguration();

    new BpmnParser(processEngineConfiguration.getExpressionManager(), new DefaultBpmnParseFactory())
          .createParse()
          .sourceInputStream(resource)
          .deployment(new DeploymentEntity())
          .name(name)
          .execute();

    return null;
  }

}
