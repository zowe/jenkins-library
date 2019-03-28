/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.base.models

import org.zowe.jenkins_shared_library.pipelines.base.arguments.StageArguments
import org.zowe.jenkins_shared_library.pipelines.base.enums.StageStatus

/**
 * A stage that will be executed in the Jenkins pipeline.
 */
class Stage {
    /**
     * The arguments passed into the {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline#createStage(org.zowe.jenkins_shared_library.pipelines.base.arguments.StageArguments)}
     * method.
     */
    StageArguments args

    /**
     * The current result of the build at the end of stage execution.
     */
    String endOfStepBuildStatus

    /**
     * The first exception thrown by this stage.
     */
    protected Exception _exception

    /**
     * Set the exception only if one doesn't exist.
     *
     * @param e The exception to set.
     */
    void setException(Exception e) {
        if (!_exception) {
            _exception = e
        }
    }

    /**
     * Get the exception thrown.
     * @return The exception thrown.
     */
    Exception getException() {
        return _exception
    }

    /**
     * The closure function that represents the complete stage operation.
     *
     * <p>This includes the stage operation provided by a pipeline and any internal operations
     * done by this package.</p>
     */
    Closure execute

    /**
     * Was the stage skipped by a build parameter?
     *
     * <p>If the stage skip build parameter is true for this stage, then this variable will become
     * true before stage execution.</p>
     *
     * @default false
     */
    boolean isSkippedByParam = false

    /**
     * The name of the stage.
     */
    String name

    /**
     * The next stage to execute in the pipeline.
     *
     * <p>If this property is null, then this stage is the last one to execute.</p>
     */
    Stage next

    /**
     * The ordinal of the stage in the pipeline execution flow.
     */
    int order

    /**
     * The execution status of a stage
     */
    StageStatus status = StageStatus.CREATE
}
