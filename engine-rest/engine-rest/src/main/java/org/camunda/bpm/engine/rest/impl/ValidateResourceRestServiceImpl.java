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
package org.camunda.bpm.engine.rest.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.camunda.bpm.engine.rest.ValidateResourceRestService;
import org.camunda.bpm.engine.rest.dto.repository.ValidationDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.mapper.MultipartFormData;
import org.camunda.bpm.engine.rest.mapper.MultipartFormData.FormPart;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ValidateResourceRestServiceImpl extends AbstractRestProcessEngineAware implements ValidateResourceRestService {

  public final static String DEPLOYMENT_NAME = "deployment-name";

  protected static final Set<String> RESERVED_KEYWORDS = new HashSet<String>();

  static {
    RESERVED_KEYWORDS.add(DEPLOYMENT_NAME);
  }

	public ValidateResourceRestServiceImpl(String engineName, ObjectMapper objectMapper) {
    super(engineName, objectMapper);
  }

  public ValidationDto validateResource(UriInfo uriInfo, MultipartFormData payload) {
    String deploymentName = "";
    ByteArrayInputStream bais = null;

    Set<String> partNames = payload.getPartNames();

    for (String name : partNames) {
      FormPart part = payload.getNamedPart(name);

      if (!RESERVED_KEYWORDS.contains(name)) {
        bais = new ByteArrayInputStream(part.getBinaryContent());
      }
    }

    if (payload.getNamedPart(DEPLOYMENT_NAME) != null) {
      FormPart part = payload.getNamedPart(DEPLOYMENT_NAME);
      deploymentName = part.getTextContent();
    }

    if (bais != null && bais.available() != 0) {
      return validateBpmnProcess(deploymentName, bais);
    } else {
      throw new InvalidRequestException(Status.BAD_REQUEST, "No resources contained in the form upload.");
    }
  }

  private ValidationDto validateBpmnProcess(String deploymentName, InputStream bais) {
    ValidationDto dto = new ValidationDto();

    try {
      processEngine.getRepositoryService().validateResource(deploymentName, bais);
    } catch(Exception e) {
      dto.setExceptionType(e.getClass().getName());
      dto.setExceptionMessage(e.getMessage());
      dto.setExeptionCause(e.getCause());
    }

    dto.setDeploymentName(deploymentName);
    return dto;
  }

}
