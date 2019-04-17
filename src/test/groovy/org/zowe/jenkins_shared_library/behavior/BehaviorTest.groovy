/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

import java.util.logging.Logger
import org.hamcrest.collection.IsMapContaining
import org.junit.*
import org.zowe.jenkins_shared_library.behavior.CPSGrandchild
import org.zowe.jenkins_shared_library.behavior.NonCPSGrandchild
import org.zowe.jenkins_shared_library.integrationtest.MockJenkinsSteps
import org.zowe.jenkins_shared_library.Utils
import static groovy.test.GroovyAssert.*
import static org.hamcrest.CoreMatchers.*;

/**
 * Test {@link org.zowe.jenkins_shared_library.behavior}
 */
class BehaviorTest {
    /**
     * Logger object
     */
    static transient Logger logger

    /**
     * Init dependencies
     */
    @BeforeClass
    public static void initDependencies() {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())
    }

    @Test
    void testNonCPSGrandchild() {
        def steps = new MockJenkinsSteps()
        def a = new NonCPSGrandchild(steps)
        a.test()
    }

    @Test
    void testCPSGrandchild() {
        def steps = new MockJenkinsSteps()
        def a = new CPSGrandchild(steps)
        a.test()
    }
}
