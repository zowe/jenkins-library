/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.email

/**
 * Arguments available to the {@link Email#send(EmailArguments)} method.
 */
class EmailArguments {
    /**
     * Should the recipient providers be added?
     */
    boolean addProviders = true

    /**
     * Is the body in HTML format
     */
    boolean html = true

    /**
     * If attach build log
     */
    boolean attachLog = false

    /**
     * HTML message content.
     */
    String body

    /**
     * A tag that will be added in the subject of the email.
     *
     * <p>This value will be the first text content in the subject formatted as: {@code "[$subjectTag]"}</p>
     */
    String subjectTag = "BUILD"

    /**
     * A list of additional emails, separated by a comma, that will receive the email.
     *
     * <p>The string provided follows the convention of the <a href="https://jenkins.io/doc/pipeline/steps/email-ext/">
     * Extended Email Plugin</a></p>
     */
    String to = ""
}
