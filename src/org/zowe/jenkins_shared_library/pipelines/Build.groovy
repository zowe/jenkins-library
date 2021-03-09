/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.pipelines

import groovy.util.logging.Log
import com.cloudbees.groovy.cps.NonCPS
import hudson.tasks.test.AbstractTestResultAction
@Grab('org.apache.commons:commons-text:1.6')
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4
import org.zowe.jenkins_shared_library.Constants
import org.zowe.jenkins_shared_library.pipelines.Constants as PipelineConstants
import org.zowe.jenkins_shared_library.scm.Constants as SCMConstants
import hudson.model.Cause
import hudson.triggers.TimerTrigger

/**
 * This class extends Jenkins current build functions.
 *
 * @Example
 * <pre>
 *     // init Build instance
 *     def build = new Build(currentBuild)
 *     // show change summary
 *     echo build.getChangeSummary()
 *     // show test summary
 *     echo build.getTestSummary()
 * </pre>
 */
@Log
class Build {
    /**
     * Reference to the Jenkins build variable.
     */
    def _build

    /**
     * Constructs the class.
     *
     * @Example
     * <pre>
     * def build = new Build(currentBuild)
     * </pre>
     *
     * @param build    Normally should be "currentBuild" (org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper)
     */
    Build(def build) {
        this._build = build
    }

    /**
     * Render list of changes for a build.
     *
     * @Note This method will omit any changes reported from the shared pipeline library. These changes
     * aren't relevant to dependent project builds so they provide no value to include.
     *
     * @return An HTML string that represents list of changes
     */
    String getChangeSummary() {
        String changeString = ""

        // Loop through each change present in the change set
        for (def changeLog : this._build.changeSets) {
            def browser = changeLog.browser

            // Add each item in the change set to the list
            for (def entry : changeLog.items) {
                def link = browser.getChangeSetLink(entry).toString()

                // Exclude any changes from the jenkins library project
                if (link.contains(Constants.REPOSITORY_JENKINS_LIBRARY)) {
                    continue
                }

                changeString += "<li><b>${entry.author}</b>: ${entry.msgEscaped} "
                if (link) {
                    changeString += "(<a href=\"$link\">${entry.commitId.take(SCMConstants.COMMIT_ID_LENGTH)}</a>)"
                }
                changeString += "</li>"
            }
        }

        if (changeString.length() == 0) {
            changeString = "No new changes"
        } else {
            changeString = "<ul>$changeString</ul>"
        }

        return "<h3>Change Summary</h3><p>$changeString</p>"
    }

    // NonCPS informs jenkins to not save variable state that would resolve in a
    // java.io.NotSerializableException on the TestResults class
    /**
     * Gets a test summary string.
     *
     * @Note This method was created using {@literal @NonCPS} because some of the operations performed cannot be
     * serialized. The {@literal @NonCPS} annotation tells jenkins to not save the variable state of this
     * function on shutdown. Failure to run in this mode causes a java.io.NotSerializableException
     * in this method.
     *
     * @return An HTML string of test results.
     */
    @NonCPS
    String getTestSummary() {
        def testResultAction = this._build.rawBuild.getAction(AbstractTestResultAction.class)
        def text = "<h3>Test Results</h3>"

        if (testResultAction != null) {
            def total = testResultAction.getTotalCount()
            def failed = testResultAction.getFailCount()
            def skipped = testResultAction.getSkipCount()

            // Create an overall summary
            text += "<p style=\"font-size: 16px;\">Passed: <span style=\"font-weight: bold; color: green\">${total - failed - skipped}</span>, "
            text += "Failed: <span style=\"font-weight: bold; color: ${failed == 0 ? "green" : "red"}\">${failed}</span>"

            if (skipped > 0) {
                text += ", Skipped: <span style=\"font-weight: bold; color: #027b77\">${skipped}</span>"
            }
            text += "</p>"

            // Now output failing results
            if (failed > 0) {
                // If there are more failures than this value, then we will only output
                // this number of failures to save on email size.
                def maxTestOutput = 5

                text += "<h4>Failing Tests</h4>"

                def codeStart = "<code style=\"white-space: pre-wrap; display: inline-block; vertical-align: top; margin-left: 10px; color: red\">"
                def failedTests = testResultAction.getFailedTests()
                def failedTestsListCount = failedTests.size() // Don't trust that failed == failedTests.size()

                // Loop through all tests or the first maxTestOutput, whichever is smallest
                for (int i = 0; i < maxTestOutput && i < failedTestsListCount; i++) {
                    def test = failedTests.get(i)

                    text += "<p style=\"margin-top: 5px; margin-bottom: 0px; border-bottom: solid 1px black; padding-bottom: 5px;"

                    if (i == 0) {
                        text += "border-top: solid 1px black; padding-top: 5px;"
                    }

                    text += "\"><b>Failed:</b> ${test.fullDisplayName}"

                    // Add error details
                    if (test.errorDetails) {
                        text += "<br/><b>Details:</b>${codeStart}${escapeHtml4(test.errorDetails)}</code>"
                    }

                    // Add stack trace
                    if (test.errorStackTrace) {
                        text += "<br/><b>Stacktrace:</b>${codeStart}${escapeHtml4(test.errorStackTrace)}</code>"
                    }

                    text += "</p>"
                }

                if (maxTestOutput < failedTestsListCount) {
                    text += "<p>...For the remaining failures, view the build output</p>"
                }
            }
        } else {
            text += "<p>No test results were found for this build.</p>"
        }

        return text
    }

    Integer identifyRootCause() {
        def cause = _build.rawBuild.getCauses()[0]
        while (cause instanceof hudson.model.Cause$UpstreamCause) {
            cause = cause.upstreamRun.getCauses()[0]
            log.finer("Upstream cause detail: ${cause.properties.shortDescription}")
        }
        log.finer("Root cause found!")
        return identifyCause(cause)
    }

    Integer identifySurfaceCause() {
        def cause = _build.rawBuild.getCauses()[0]
        log.finer("Surface cause detail: ${cause.properties.shortDescription}")
        return identifyCause(cause)
    }

    private Integer identifyCause(Cause c) {
        log.finer("DEBUG Cause properties are: ${c.properties}")
        def causeID
        if (c.toString().contains("BranchEventCause")) {
            causeID = PipelineConstants.BRANCHEVENT_CAUSE_ID      //PR open or updated
        }
        else if (c instanceof hudson.model.Cause$UserIdCause) {
            causeID = PipelineConstants.USERID_CAUSE_ID           // jenkins user trigger
        }
        else if (c.toString().contains("BranchIndexingCause")) {
            causeID = PipelineConstants.BRANCHINDEXING_CAUSE_ID   //'Scan Repository Now' action
        }
        else if (c instanceof hudson.model.Cause$RemoteCause) {
            causeID = PipelineConstants.REMOTE_CAUSE_ID
        }
        else if (c instanceof hudson.triggers.TimerTrigger$TimerTriggerCause) {
            causeID = PipelineConstants.TIMER_CAUSE_ID            // timer trigger
        }
        else if (c instanceof hudson.model.Cause$UpstreamCause) {
            causeID = PipelineConstants.UPSTREAM_CAUSE_ID         // upstream project trigger
        } 
        else { 
            causeID = PipelineConstants.UNKNOWN_CAUSE_ID 
        }
        
        return causeID
    }
}
