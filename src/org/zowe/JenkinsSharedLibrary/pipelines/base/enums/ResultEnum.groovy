/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.base.enums

/**
 * Enumeration of the various build statuses accepted by Jenkins
 */
enum ResultEnum {

    /**
     * Successful build
     */
    SUCCESS("SUCCESS"),

    /**
     * Skipped build
     */
    NOT_BUILT("NOT_BUILT"),

    /**
     * Unstable Build
     */
    UNSTABLE("UNSTABLE"),

    /**
     * Failed Build
     */
    FAILURE("FAILURE"),

    /**
     * Aborted Build
     */
    ABORTED("ABORTED");

    /**
     * Initialize an enum with a value.
     * @param v The string value of the enum.
     */
    private ResultEnum(String v) {
        value = v
    }
    private String value

    /**
     * Get the value of the enum
     * @return The value of the enum.
     */
    String getValue() {
        return value
    }
}
