/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library

import java.util.logging.Level

/**
 * Constants used by Jenkins Shared Library
 */
class Constants {
    /**
     * Default logging level. Default value is {@code Level.INFO}.
     */
    static Level DEFAULT_LOGGING_LEVEL = Level.INFO

    /**
     * Repository name of this jenkins library. Default value is {@code zowe/jenkins-library}.
     */
    static String REPOSITORY_JENKINS_LIBRARY = 'zowe/jenkins-library'
}
