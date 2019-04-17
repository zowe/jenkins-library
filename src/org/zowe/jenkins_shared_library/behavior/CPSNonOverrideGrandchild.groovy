/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.behavior

/**
 * This demostrate after the children class calling super.method(), the method is altered by CPS
 * plugin, and the context to super is lost.
 *
 * But super on construction method is working properly.
 *
 *
 * When calling (new NonCPSNonOverrideGrandchild()).test():
 *
 * Expected to see:
 * NonCPSBase construction
 * NonCPSChild construction
 * NonCPSNonOverrideGrandchild construction
 * NonCPSNonOverrideGrandchild.test() started
 * NonCPSChild.test() started
 * NonCPSBase.test()
 * NonCPSChild.test() done
 * NonCPSNonOverrideGrandchild.test() done
 *
 * Actual we get if we capture and ignore the error:
 * NonCPSBase construction
 * NonCPSChild construction
 * NonCPSGrandchild construction
 * NonCPSGrandchild.test() started
 * NonCPSChild.test() started
 * NonCPSChild.test() started
 * NonCPSChild.test() started
 * NonCPSChild.test() started
 * NonCPSChild.test() started
 * NonCPSChild.test() started
 * ... infinite loop
 */
class CPSNonOverrideGrandchild extends CPSNonOverrideChild {
    CPSNonOverrideGrandchild(steps) {
        super(steps)
        steps.echo "CPSNonOverrideGrandchild construction"
    }

    void test() {
        steps.echo "CPSNonOverrideGrandchild.test() started"
        // steps.echo "super = ${super}"
        // steps.echo "super.getClass = ${super.getClass()}"
        // steps.echo "super.metaClass = ${super.metaClass}"
        // super.metaClass.methods.name.unique().each{
        //     steps.echo "- ${it}(); "
        // }
        super.test()
        steps.echo "CPSNonOverrideGrandchild.test() done"
    }
}
