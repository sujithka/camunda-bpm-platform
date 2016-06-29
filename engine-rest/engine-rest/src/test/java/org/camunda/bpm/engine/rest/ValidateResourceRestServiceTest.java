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
package org.camunda.bpm.engine.rest;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.rest.dto.repository.ValidationDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.helper.MockProvider;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;

/**
 * @author kristin.polenz
 */
public class ValidateResourceRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String RESOURCE_URL = TEST_RESOURCE_ROOT_PATH + "/resource";
  protected static final String VALIDATION_URL = RESOURCE_URL + "/validate";

  protected RepositoryService mockRepositoryService;
  protected ValidationDto mockValidation;

  protected static final BpmnModelInstance BPMN_PROCESS = Bpmn.createExecutableProcess().startEvent().endEvent().done();

  protected static final BpmnModelInstance INVALID_BPMN_PROCESS = Bpmn.createExecutableProcess()
      .startEvent().serviceTask().endEvent().done();


  @Before
  public void setUpRuntimeData() {
    mockRepositoryService = mock(RepositoryService.class);

    when(processEngine.getRepositoryService()).thenReturn(mockRepositoryService);
  }

  private InputStream createMockResource(String data) {
    // do not close the input stream, will be done in implementation  
    InputStream resource = new ByteArrayInputStream(data.getBytes());
    return resource;
  }

  private InputStream createMockResourceBpmnData(BpmnModelInstance resource) {
    // do not close the input stream, will be done in implementation
    return new ByteArrayInputStream(Bpmn.convertToString(resource).getBytes());
  }

  @Test
  public void testValidateResource() throws Exception {
    mockValidation = MockProvider.createMockValidation();

    Response response = given()
      .multiPart("data", "unspecified", createMockResourceBpmnData(BPMN_PROCESS))
      .multiPart("deployment-name", MockProvider.EXAMPLE_DEPLOYMENT_ID)
    .expect()
      .statusCode(Status.OK.getStatusCode())
    .when()
      .post(VALIDATION_URL);

    JsonPath path = from(response.asString());
    String returnedDeploymentName = path.get("deploymentName");
    String returnedExceptionType = path.get("exceptionType");
    String returnedExceptionMessage = path.get("exceptionMessage");

    assertEquals(mockValidation.getDeploymentName(), returnedDeploymentName);
    assertNull(returnedExceptionType);
    assertNull(returnedExceptionMessage);
  }

  @Test
  public void testValidateResourceWithoutData() throws Exception {
    given()
        .multiPart("data", "unspecified", createMockResource(""))
        .multiPart("deployment-name", MockProvider.EXAMPLE_DEPLOYMENT_ID)
      .expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("type", equalTo(InvalidRequestException.class.getSimpleName()))
        .body("message", equalTo("No resources contained in the form upload."))
      .when()
        .post(VALIDATION_URL);
  }

  @Test
  public void testValidateResourceWithAnyData() throws Exception {
    mockValidation = MockProvider.createMockValidation();

    // no exception, because the given resource data is not executable
    // and will be ignored from the BPMN parser
    Response response = given()
        .multiPart("data", "unspecified", createMockResource("some data"))
        .multiPart("deployment-name", MockProvider.EXAMPLE_DEPLOYMENT_ID)
      .expect()
        .statusCode(Status.OK.getStatusCode())
      .when()
        .post(VALIDATION_URL);

      JsonPath path = from(response.asString());
      String returnedDeploymentName = path.get("deploymentName");
      String returnedExceptionType = path.get("exceptionType");
      String returnedExceptionMessage = path.get("exceptionMessage");

      assertEquals(mockValidation.getDeploymentName(), returnedDeploymentName);
      assertNull(returnedExceptionType);
      assertNull(returnedExceptionMessage);
  }

  @Test
  public void testValidateResourceWithInvalidBpmnProcess() throws Exception {
    mockValidation = MockProvider.createMockValidationWithException();

    doThrow(new ProcessEngineException("ENGINE-09005 Could not parse BPMN process. Errors:"))
      .when(mockRepositoryService).validateResource(eq(MockProvider.EXAMPLE_DEPLOYMENT_ID), any(InputStream.class));

    Response response = given()
        .multiPart("data", "unspecified", createMockResourceBpmnData(INVALID_BPMN_PROCESS))
        .multiPart("deployment-name", MockProvider.EXAMPLE_DEPLOYMENT_ID)
      .expect()
        .statusCode(Status.OK.getStatusCode())
      .when()
        .post(VALIDATION_URL);

      JsonPath path = from(response.asString());
      String returnedDeploymentName = path.get("deploymentName");
      String returnedExceptionType = path.get("exceptionType");
      String returnedExceptionMessage = path.get("exceptionMessage");

      assertEquals(mockValidation.getDeploymentName(), returnedDeploymentName);
      assertEquals(mockValidation.getExceptionType(), returnedExceptionType);
      assertTrue(mockValidation.getExceptionMessage().contains(returnedExceptionMessage));
  }

}
