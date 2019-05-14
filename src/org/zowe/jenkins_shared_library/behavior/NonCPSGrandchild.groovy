/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.behavior

import com.cloudbees.groovy.cps.NonCPS

/**
 * This demostrate after the children class calling super.method(), the remaining code of the children method will be skipped.
 *
 * <p>When calling {@code (new NonCPSGrandchild()).test()}:</p>
 *
 * <p>Expected to see:
 * <pre>
 * NonCPSBase construction
 * NonCPSChild construction
 * NonCPSGrandchild construction
 * NonCPSGrandchild.test() started
 * NonCPSChild.test() started
 * NonCPSBase.test()
 * NonCPSChild.test() done
 * NonCPSGrandchild.test() done
 * </pre></p>
 *
 * <p>Actual we get:
 * <pre>
 * NonCPSBase construction
 * NonCPSChild construction
 * NonCPSGrandchild construction
 * NonCPSGrandchild.test() started
 * NonCPSChild.test() started
 * NonCPSBase.test()
 * </pre></p>
 */
class NonCPSGrandchild extends NonCPSChild {
    NonCPSGrandchild(steps) {
        super(steps)
        steps.echo "NonCPSGrandchild construction"
    }

    @NonCPS
    @Override
    void test() {
        steps.echo "NonCPSGrandchild.test() started"
        // steps.echo "super = ${super}"
        // steps.echo "super.getClass = ${super.getClass()}"
        // steps.echo "super.metaClass = ${super.metaClass}"
        // super.metaClass.methods.name.unique().each{
        //     steps.echo "- ${it}(); "
        // }
        super.test()
        steps.echo "NonCPSGrandchild.test() done"
    }
}
