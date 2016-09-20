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

import org.camunda.bpm.engine.impl.event.MessageEventHandler;


/**
 * @author Daniel Meyer
 */
public class MessageEventSubscriptionEntity extends EventSubscriptionEntity {

  private static final long serialVersionUID = 1L;

  public MessageEventSubscriptionEntity(ExecutionEntity executionEntity) {
    super(executionEntity);
    eventType = MessageEventHandler.EVENT_HANDLER_TYPE;
  }

  public MessageEventSubscriptionEntity() {
    eventType = MessageEventHandler.EVENT_HANDLER_TYPE;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName()
           + "[id=" + id
           + ", eventType=" + eventType
           + ", eventName=" + eventName
           + ", executionId=" + executionId
           + ", processInstanceId=" + processInstanceId
           + ", activityId=" + activityId
           + ", configuration=" + configuration
           + ", revision=" + revision
           + ", created=" + created
           + "]";
  }

}
