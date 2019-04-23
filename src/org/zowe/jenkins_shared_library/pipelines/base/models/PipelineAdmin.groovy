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

import hudson.model.User
import hudson.tasks.Mailer
import org.zowe.jenkins_shared_library.pipelines.base.exceptions.AdminInitializationException

/**
 * An admin of the pipeline.
 *
 * <p>This should be a user that is trusted to perform admin operations of a pipeline, such as
 * approving a deploy. An admin user will always receive an email on completion of a {@link Branch}
 * build.</p>
 */
final class PipelineAdmin {
    /**
     * The id of the user in Jenkins
     */
    final String userID

    /**
     * The email address of the user
     */
    final String email

    /**
     * The full name of the user
     */
    final String name

    /**
     * Initializes a pipeline admin.
     * @param userId The userId of the admin in Jenkins.
     * @throws AdminInitializationException when an error occurs during initialization.
     */
    PipelineAdmin(String userId) throws AdminInitializationException {
        this.userID = userId

        User u = User.getById(userId, false)

        if (!u) {
            throw new AdminInitializationException("Unable to find user!", userId)
        }

        name = u.getFullName()

        email = u.getProperty(Mailer.UserProperty.class).address

        if (!email) {
            throw new AdminInitializationException("Email address is not defined!", userId)
        }
    }
}
