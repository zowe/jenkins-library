/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.docker.arguments

import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.GenericSetupStageArguments

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.docker.DockerPipeline#setup(org.zowe.jenkins_shared_library.pipelines.docker.arguments.DockerSetupArguments)}
 * method.
 */
class DockerSetupArguments extends GenericSetupStageArguments {
    /**
     * Docker registry configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.npm.Registry#init(Map)}.</p>
     */
    Map docker
}
