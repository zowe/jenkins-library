/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.email

class Email {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def o = new Email(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    Email(steps) {
        this.steps = steps
    }

    /**
     * Send an email.
     *
     * <p>The email will contain {@code [args.tag]} as the first string content followed by the
     * job name and build number</p>
     *
     * @param args Arguments available to the email command.
     */
    final void send(EmailArguments args) {
        def subject = "[$args.subjectTag] Job '${steps.env.JOB_NAME} [${steps.env.BUILD_NUMBER}]'"

        steps.echo "Sending Email\n" +
                   "Subject: $subject\n" +
                   "Body:\n${args.body}"

        // send the email
        steps.emailext(
            subject            : subject,
            to                 : args.to,
            body               : args.body,
            mimeType           : args.html ? "text/html" : "text/plain",
            attachLog          : args.attachLog,
            recipientProviders : args.addProviders ? [[$class: 'DevelopersRecipientProvider'],
                                                     [$class: 'UpstreamComitterRecipientProvider'],
                                                     [$class: 'CulpritsRecipientProvider'],
                                                     [$class: 'RequesterRecipientProvider']] : []
        )
    }

    /**
     * Send an HTML email.
     *
     * @param args A map that can be instantiated as {@link EmailArguments}.
     * @see #send(EmailArguments)
     */
    final void send(Map args) {
        send(args as EmailArguments)
    }
}
