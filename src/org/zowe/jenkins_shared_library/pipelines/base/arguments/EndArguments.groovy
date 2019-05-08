/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.base.arguments

/**
 * Arguments available to the {@link jenkins_shared_library.pipelines.base.Pipeline#endBase(jenkins_shared_library.pipelines.base.arguments.EndArguments)}
 * method.
 */
class EndArguments {
    /**
     * A closure that always is executed pipeline completion.
     *
     * <p>This closure will always be executed after logs are collected and just prior to the
     * status email send.</p>
     */
    Closure always
}
