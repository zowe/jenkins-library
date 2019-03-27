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
 * Arguments available to the {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline#endBase(EndArguments)}
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

    // @FUTURE allow any type of filepath by just copying everything to the temp directory
    // @FUTURE regardless of the file descriptor. This isn't done now because the .. structure
    // @FUTURE could prove difficult to copy and this is good enough for MVP.
    /**
     * An array of folders to archive.
     *
     * <p>The build will attempt to archive all folders listed. If a folder doesn't exist, the
     * pipeline will ignore the log and will not modify the build result.</p>
     *
     * <p>If a folder in this array starts with a {@literal "/"}, the pipeline will copy the folder
     * into a temporary directory inside the project, retaining the folder structure. This is
     * necessary because folders outside the project workspace cannot be archived. The solution is
     * to move them into the workspace via the method mentioned above. As such, the leading
     * {@literal "/"} should be used for any logs that you wish to capture residing outside the
     * project workspace.</p>
     *
     * <p>If the directory contains a parent directory indicator , {@literal ".."}, the pipeline
     * will not attempt to archive the folder. Instead the pipeline will log the reason to the
     * console and continue to the next log, if any. This restriction exists to protect against
     * archiving files outside the workspace. For any logs inside the workspace, you must use the
     * full relative path that goes directly to the folder.</p>
     */
    String[] archiveFolders
}
