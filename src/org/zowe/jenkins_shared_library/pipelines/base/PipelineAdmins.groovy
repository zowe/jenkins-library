/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.base

import org.zowe.jenkins_shared_library.pipelines.base.exceptions.AdminInitializationException
import org.zowe.jenkins_shared_library.pipelines.base.models.PipelineAdmin

/**
 * Stores the list of admins for a pipeline.
 */
class PipelineAdmins {
    /**
     * The map of available admins.
     *
     * <p>The map is keyed on User ID</p>
     */
    private final Map<String, PipelineAdmin> _admins = [:]

    /**
     * Define admin users to the pipeline.
     *
     * @param admins A list of user ids to add to the pipeline.
     * @throws AdminInitializationException when encountering an error creating an admin user.
     */
    void add(String... admins) throws AdminInitializationException {
        for (String admin : admins) {
            _admins.putAt(admin, new PipelineAdmin(admin))
        }
    }

    /**
     * Get a particular admin from the User ID.
     * @param id The User ID to lookup
     * @return An object representing the admin user or null if none was found.
     */
    PipelineAdmin get(String id) {
        return _admins.get(id)
    }

    /**
     * Gets the admin emails as a CC list.
     * @return a CC list of admin emails.
     */
    String getCCList() {
        _getEmailList("cc")
    }

    /**
     * Gets the admin User IDs as a comma separated list.
     *
     * <p>This list can be used in an input step</p>
     * @return Comma separate list of admin User IDs.
     */
    String getCommaSeparated() {
        String commaSeparated = ""
        boolean first = true
        _admins.each { key, value ->
            commaSeparated += (!first ? "," : "") + value.userID
            first = false
        }

        return commaSeparated
    }

    /**
     * Gets the admin emails as a TO list.
     * @return a TO list of admin emails.
     */
    String getEmailList() {
        return _getEmailList()
    }

    /**
     * Gets the number of admins defined.
     * @return The number of admins defined
     */
    int getSize() {
        return _admins.size()
    }

    /**
     * Generic method to formulate the syntax of the email list.
     * @param prefix The prefix for the email. If specified this will be present as {@code "$prefix:$email"}
     *               for each email address.
     * @return A comma separated list of emails suitable for {@link org.zowe.jenkins_shared_library.pipelines.base.arguments.EmailArguments#to}
     */
    private String _getEmailList(String prefix = null) {
        String emailList = ""
        boolean first = true
        _admins.each { key, value ->
            emailList += (!first ? "," : "") + "${prefix ? "$prefix: " : ""}${value.email}"
            first = false
        }

        return emailList
    }
}
