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

/**
 * Constants used by Jenkins Shared Library Pipelines
 */
class Constants {
    /**
     * Text used for the CI SKIP commit.
     *
     * @Default {@code "[ci skip]"}
     */
    static String CI_SKIP = "[ci skip]"

    /**
     * Default size of build history.
     *
     * @Default {@code 5}
     */
    static Integer DEFAULT_BUILD_HISTORY = 5

    /**
     * Default size of build history for protected branches.
     *
     * @Default {@code 20}
     */
    static Integer DEFAULT_BUILD_HISTORY_FOR_PROTECTED_BRANCH = 20

    /**
     * Default branch release tag.
     *
     * @Default {@code "snapshot"}
     */
    static String DEFAULT_BRANCH_RELEASE_TAG = "snapshot"

    /**
     * Images embedded in notification emails depending on the status of the build.
     */
    static Map<String, List<String>> notificationImages = [
        SUCCESS : [
            'https://i.imgur.com/ixx5WSq.png', /*happy seal*/
            'https://i.imgur.com/jiCQkYj.png'  /*happy puppy*/
        ],
        UNSTABLE: [
            'https://i.imgur.com/fV89ZD8.png',  /*not sure if*/
            'https://media.giphy.com/media/rmRUASq4WujsY/giphy.gif' /*f1 tires fly off*/
        ],
        FAILURE : [
            'https://i.imgur.com/iQ4DuYL.png',  /*this is fine fire */
            'https://media.giphy.com/media/3X0nMYG46US2c/giphy.gif' /*terminator sink into lava*/
        ],
        ABORTED : [
            'https://i.imgur.com/Zq0iBJK.jpg' /* surprised pikachu */
        ]
    ]

    /**
     * Default artifact NPM tag for non-release build. Default value is {@code "snapshot"}.
     */
    static String DEFAULT_NPM_NON_RELEASE_TAG = 'snapshot'
}
