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

/**
 * Child class.
 *
 * @see CPSGrandchild
 */
class CPSChild extends CPSBase {
    CPSChild(steps) {
        super(steps)
        steps.echo "CPSChild construction"
    }

    @Override
    void test() {
        steps.echo "CPSChild.test() started"
        super.test()
        steps.echo "CPSChild.test() done"
    }
}
