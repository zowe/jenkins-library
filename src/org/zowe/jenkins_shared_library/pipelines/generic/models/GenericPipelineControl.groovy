/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.generic.models

import org.zowe.jenkins_shared_library.pipelines.base.models.PipelineControl
import org.zowe.jenkins_shared_library.pipelines.base.models.Stage

/**
 * Additional control variables for a {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline}
 */
class GenericPipelineControl extends PipelineControl {
    /**
     * The build stage
     */
    Stage build

    /**
     * Test stages that occur before deploy.
     *
     * <p>Test stages require that the build was successful.</p>
     */
    List<Stage> preDeployTests = []

    /**
     * Versioning stage.
     *
     * <p>This stage requires the build to be successful and for tests to be stable (if they were executed)</p>
     */
    Stage version

    /**
     * Deploy stage.
     *
     * <p>This stage requires the build to be successful and for tests to be stable (if they were executed)</p>
     */
    Stage deploy
}
