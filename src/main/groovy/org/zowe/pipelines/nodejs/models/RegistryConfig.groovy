/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs.models

/**
 * Registry connection information.
 */
class RegistryConfig {
    /**
     * The url of the registry.
     *
     * <p>If this is null, the default registry (per npm commands) will be referenced</p>
     */
    String url

    /**
     * The scope associated with the registry url
     *
     * <p>If this is null, no scope will be referenced</p>
     */
    String scope

    /**
     * The email address of the user.
     */
    String email

    /**
     * ID of credentials in the Jenkins secure credential store.
     *
     * <p>The username and password should be stored under this ID</p>
     */
    String credentialsId
}
