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
 * This demostrate after the children class calling super.method(), the method is altered by CPS
 * plugin, and the context to super is lost.
 *
 * @Note But super on construction method is working properly.
 *
 * <p>When calling {@code (new CPSGrandchild()).test()}:</p>
 *
 * <p>Expected to see:
 * <pre>
 * CPSBase construction
 * CPSChild construction
 * CPSGrandchild construction
 * CPSGrandchild.test() started
 * CPSChild.test() started
 * CPSBase.test()
 * CPSChild.test() done
 * CPSGrandchild.test() done
 * </pre></p>
 *
 * <p>Actual we get if we capture and ignore the error:
 * <pre>
 * CPSBase construction
 * CPSChild construction
 * CPSGrandchild construction
 * CPSGrandchild.test() started
 * CPSChild.test() started
 * CPSChild.test() started
 * CPSChild.test() started
 * CPSChild.test() started
 * CPSChild.test() started
 * CPSChild.test() started
 * //... infinite loop
 * </pre></p>
 */
class CPSGrandchild extends CPSChild {
    CPSGrandchild(steps) {
        super(steps)
        steps.echo "CPSGrandchild construction"
    }

    @Override
    void test() {
        steps.echo "CPSGrandchild.test() started"
        // steps.echo "super = ${super}"
        // steps.echo "super.getClass = ${super.getClass()}"
        // steps.echo "super.metaClass = ${super.metaClass}"
        // super.metaClass.methods.name.unique().each{
        //     steps.echo "- ${it}(); "
        // }
        super.test()
        steps.echo "CPSGrandchild.test() done"
    }
}
