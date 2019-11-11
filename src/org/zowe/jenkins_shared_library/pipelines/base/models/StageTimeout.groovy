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

import java.util.concurrent.TimeUnit

/**
 * Stage timeout information.
 */
class StageTimeout {
    /**
     * The amount of time a stage is allowed to execute.
     *
     * @default 10
     */
    long time = 10

    /**
     * The unit of measurement for {@link #time}
     *
     * @default {@link java.util.concurrent.TimeUnit#MINUTES}
     */
    TimeUnit unit = TimeUnit.MINUTES

    /**
     * Add a timeout to a timeout
     * @param value The timeout to add
     * @return A new timeout object representing the operation. The returned value's
     *         unit will be that of the input time.
     */
    StageTimeout add(StageTimeout value) {
        return new StageTimeout(
                unit: value.unit,
                time: unit.convert(time, value.unit) + value.time
        )
    }

    /**
     * Add using a map of a timeout.
     *
     * @param value The StageTimeout map to construct
     * @return A new timeout object representing the operation
     * @see #add(jenkins_shared_library.pipelines.base.models.StageTimeout)
     */
    StageTimeout add(Map value) {
        return add(new StageTimeout(value))
    }

    /**
     * Subtracts a timeout from a timeout
     * @param value The timeout to subtract
     * @return A new timeout object representing the operation. The returned value's
     *         unit will be that of the input StageTimeout.
     */
    StageTimeout subtract(StageTimeout value) {
        return new StageTimeout(
                unit: value.unit,
                time: unit.convert(time, value.unit) - value.time
        )
    }

    /**
     * Subtract using a map of a timeout.
     *
     * @param value The StageTimeout map to construct
     * @return A new timeout object representing the operation
     * @see #subtract(jenkins_shared_library.pipelines.base.models.StageTimeout)
     */
    StageTimeout subtract(Map value) {
        return subtract(new StageTimeout(value))
    }

    /**
     * Outputs the timeout in the form of "{@link #time} {@link #unit}"
     * @return The string representation of this object.
     */
    String toString() {
        return "${time} ${unit}"
    }
}
