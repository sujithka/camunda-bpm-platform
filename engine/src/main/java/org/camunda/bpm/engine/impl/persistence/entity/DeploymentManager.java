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

package org.camunda.bpm.engine.impl.persistence.entity;

import java.util.List;

import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.DeploymentQueryImpl;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.auth.ResourceAuthorizationProvider;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.dmn.entity.repository.DecisionDefinitionManager;
import org.camunda.bpm.engine.impl.persistence.AbstractManager;
import org.camunda.bpm.engine.impl.persistence.deploy.DeploymentCache;
import org.camunda.bpm.engine.repository.CaseDefinition;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;


/**
 * @author Tom Baeyens
 * @author Deivarayan Azhagappan
 */
public class DeploymentManager extends AbstractManager {

  public void insertDeployment(DeploymentEntity deployment) {
    getDbEntityManager().insert(deployment);
    createDefaultAuthorizations(deployment);

    for (ResourceEntity resource : deployment.getResources().values()) {
      resource.setDeploymentId(deployment.getId());
      getResourceManager().insertResource(resource);
    }

    Context
      .getProcessEngineConfiguration()
      .getDeploymentCache()
      .deploy(deployment);
  }

  public void deleteDeployment(String deploymentId, boolean cascade) {
    deleteDeployment(deploymentId, cascade, false);
  }

  public void deleteDeployment(String deploymentId, boolean cascade, boolean skipCustomListeners) {
    List<ProcessDefinition> processDefinitions = getProcessDefinitionManager().findProcessDefinitionsByDeploymentId(deploymentId);

    for (ProcessDefinition processDefinition : processDefinitions) {
      getProcessDefinitionManager()
              .deleteProcessDefinition(processDefinition,
                      processDefinition.getId(), cascade, skipCustomListeners);
    }

    if (cascade) {
      // delete historic job logs (for example for timer start event jobs)
      getHistoricJobLogManager().deleteHistoricJobLogsByDeploymentId(deploymentId);
    }

    deleteCaseDeployment(deploymentId, cascade);

    deleteDecisionDeployment(deploymentId, cascade);
    deleteDecisionRequirementDeployment(deploymentId);

    getResourceManager().deleteResourcesByDeploymentId(deploymentId);

    deleteAuthorizations(Resources.DEPLOYMENT, deploymentId);
    getDbEntityManager().delete(DeploymentEntity.class, "deleteDeployment", deploymentId);

  }

  protected void deleteCaseDeployment(String deploymentId, boolean cascade) {
    ProcessEngineConfigurationImpl processEngineConfiguration = Context.getProcessEngineConfiguration();
    if (processEngineConfiguration.isCmmnEnabled()) {
      List<CaseDefinition> caseDefinitions = getCaseDefinitionManager().findCaseDefinitionByDeploymentId(deploymentId);

      if (cascade) {

        // delete case instances
        for (CaseDefinition caseDefinition: caseDefinitions) {
          String caseDefinitionId = caseDefinition.getId();

          getCaseInstanceManager()
            .deleteCaseInstancesByCaseDefinition(caseDefinitionId, "deleted deployment", true);

        }
      }

      // delete case definitions from db
      getCaseDefinitionManager()
        .deleteCaseDefinitionsByDeploymentId(deploymentId);

      for (CaseDefinition caseDefinition : caseDefinitions) {
        String processDefinitionId = caseDefinition.getId();

        // remove case definitions from cache:
        Context
          .getProcessEngineConfiguration()
          .getDeploymentCache()
          .removeCaseDefinition(processDefinitionId);
      }
    }
  }

  protected void deleteDecisionDeployment(String deploymentId, boolean cascade) {
    ProcessEngineConfigurationImpl processEngineConfiguration = Context.getProcessEngineConfiguration();
    if (processEngineConfiguration.isDmnEnabled()) {
      DecisionDefinitionManager decisionDefinitionManager = getDecisionDefinitionManager();
      List<DecisionDefinition> decisionDefinitions = decisionDefinitionManager.findDecisionDefinitionByDeploymentId(deploymentId);

      if(cascade) {
        // delete historic decision instances
        for(DecisionDefinition decisionDefinition : decisionDefinitions) {
          getHistoricDecisionInstanceManager().deleteHistoricDecisionInstancesByDecisionDefinitionId(decisionDefinition.getId());
        }
      }

      // delete decision definitions from db
      decisionDefinitionManager
        .deleteDecisionDefinitionsByDeploymentId(deploymentId);

      DeploymentCache deploymentCache = processEngineConfiguration.getDeploymentCache();

      for (DecisionDefinition decisionDefinition : decisionDefinitions) {
        String decisionDefinitionId = decisionDefinition.getId();

        // remove decision definitions from cache:
        deploymentCache
          .removeDecisionDefinition(decisionDefinitionId);
      }
    }
  }

  protected void deleteDecisionRequirementDeployment(String deploymentId) {
    ProcessEngineConfigurationImpl processEngineConfiguration = Context.getProcessEngineConfiguration();
    if (processEngineConfiguration.isDmnEnabled()) {
      DecisionDefinitionManager decisionDefinitionManager = getDecisionDefinitionManager();
      List<DecisionRequirementsDefinition> decisionRequirementsDefinitions = decisionDefinitionManager.findDecisionRequirementsDefinitionByDeploymentId(deploymentId);

      // delete decision requirements definitions from db
      decisionDefinitionManager.deleteDecisionRequirementsDefinitionsByDeploymentId(deploymentId);

      DeploymentCache deploymentCache = processEngineConfiguration.getDeploymentCache();

      for (DecisionRequirementsDefinition decisionRequirementsDefinition : decisionRequirementsDefinitions) {
        String decisionDefinitionId = decisionRequirementsDefinition.getId();

        // remove decision requirements definitions from cache:
        deploymentCache.removeDecisionRequirementsDefinition(decisionDefinitionId);
      }
    }
  }

  public DeploymentEntity findLatestDeploymentByName(String deploymentName) {
    List<?> list = getDbEntityManager().selectList("selectDeploymentsByName", deploymentName, 0, 1);
    if (list!=null && !list.isEmpty()) {
      return (DeploymentEntity) list.get(0);
    }
    return null;
  }

  public DeploymentEntity findDeploymentById(String deploymentId) {
    return getDbEntityManager().selectById(DeploymentEntity.class, deploymentId);
  }

  @SuppressWarnings("unchecked")
  public List<DeploymentEntity> findDeploymentsByIds(String... deploymentsIds) {
    return getDbEntityManager().selectList("selectDeploymentsByIds", deploymentsIds);
  }

  public long findDeploymentCountByQueryCriteria(DeploymentQueryImpl deploymentQuery) {
    configureQuery(deploymentQuery);
    return (Long) getDbEntityManager().selectOne("selectDeploymentCountByQueryCriteria", deploymentQuery);
  }

  @SuppressWarnings("unchecked")
  public List<Deployment> findDeploymentsByQueryCriteria(DeploymentQueryImpl deploymentQuery, Page page) {
    configureQuery(deploymentQuery);
    return getDbEntityManager().selectList("selectDeploymentsByQueryCriteria", deploymentQuery, page);
  }

  @SuppressWarnings("unchecked")
  public List<String> getDeploymentResourceNames(String deploymentId) {
    return getDbEntityManager().selectList("selectResourceNamesByDeploymentId", deploymentId);
  }

  @Override
  public void close() {
  }

  @Override
  public void flush() {
  }

  // helper /////////////////////////////////////////////////

  protected void createDefaultAuthorizations(DeploymentEntity deployment) {
    if(isAuthorizationEnabled()) {
      ResourceAuthorizationProvider provider = getResourceAuthorizationProvider();
      AuthorizationEntity[] authorizations = provider.newDeployment(deployment);
      saveDefaultAuthorizations(authorizations);
    }
  }

  protected void configureQuery(DeploymentQueryImpl query) {
    getAuthorizationManager().configureDeploymentQuery(query);
    getTenantManager().configureQuery(query);
  }

}
