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
 * Child class.
 *
 * @see {@link NonCPSGrandchild}
 */
class NonCPSChild extends NonCPSBase {
    NonCPSChild(steps) {
        super(steps)
        steps.echo "NonCPSChild construction"
    }

    @NonCPS
    @Override
    void test() {
        steps.echo "NonCPSChild.test() started"
        super.test()
        steps.echo "NonCPSChild.test() done"
    }
}
