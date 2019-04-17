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

import com.cloudbees.groovy.cps.NonCPS

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
