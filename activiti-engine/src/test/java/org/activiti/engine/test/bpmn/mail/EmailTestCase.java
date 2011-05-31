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

package org.activiti.engine.test.bpmn.mail;

import org.activiti.engine.impl.test.PluggableActivitiTestCase;
import org.subethamail.wiser.Wiser;


/**
 * @author Joram Barrez
 */
public class EmailTestCase extends PluggableActivitiTestCase {
  
 protected Wiser wiser;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    wiser = new Wiser();
    wiser.setPort(5025);
    wiser.start();
  }
  
  @Override
  protected void tearDown() throws Exception {
    wiser.stop();
    
    // Fix for slow Jenkins
    while (wiser.getServer().isRunning()) {
      Thread.sleep(100L);
    }
    
    super.tearDown();
  }

}
