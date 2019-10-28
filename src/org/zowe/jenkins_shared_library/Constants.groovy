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
 * Constants used by Jenkins Shared Library.
 *
 * @Note The default account information for various services we are using are also defined here.
 * <strong>We strongly recommended the library consumer to use these constants. This will make us
 * really easy to migrate service to another location.</strong>
 */
class Constants {
    /**
     * Default logging level.
     *
     * @Default {@code Level.INFO}
     */
    public static Level DEFAULT_LOGGING_LEVEL = Level.INFO

    /**
     * Repository name of this jenkins library.
     *
     * @Default {@code "zowe/jenkins-library"}
     */
    public static String REPOSITORY_JENKINS_LIBRARY = 'zowe/jenkins-library'

    /**
     * Default GitHub robot account email
     *
     * @Default {@code "zowe.robot@gmail.com"}
     */
    public static String DEFAULT_GITHUB_ROBOT_EMAIL = 'zowe.robot@gmail.com'

    /**
     * Default GitHub robot account Jenkins username/password credential ID
     *
     * @Default {@code "zowe-robot-github"}
     */
    public static String DEFAULT_GITHUB_ROBOT_CREDENTIAL = 'zowe-robot-github'

    /**
     * Default Artifactory URL
     *
     * @Default {@code "https://gizaartifactory.jfrog.io/gizaartifactory"}
     */
    public static String DEFAULT_ARTIFACTORY_URL = 'https://gizaartifactory.jfrog.io/gizaartifactory'

    /**
     * Default Artifactory robot account Jenkins username/password credential ID
     *
     * @Default {@code "GizaArtifactory"}
     */
    public static String DEFAULT_ARTIFACTORY_ROBOT_CREDENTIAL = 'GizaArtifactory'

    /**
     * Default SonarQube server ID defined on Jenkins
     *
     * @Default {@code "sonar-default-server"}
     */
    public static String DEFAULT_SONARQUBE_SERVER = 'sonar-default-server'

    /**
     * Default SonarQube scanner tool ID defined on Jenkins
     *
     * @Default {@code "sonar-scanner-3.2.0"}
     */
    public static String DEFAULT_SONARQUBE_SCANNER_TOOL = 'sonar-scanner-3.2.0'

    /**
     * Default if SonarQube server supports branch scanning
     *
     * @Default {@code false}
     */
    public static Boolean DEFAULT_SONARQUBE_ALLOW_BRANCH = false

    /**
     * Default if pipeline should fail the build if code doesn't meet Sonar Scan
     * Quality Gate.
     *
     * @Default {@code false}
     */
    public static Boolean DEFAULT_SONARQUBE_FAIL_BUILD = false

    /**
     * Default SonarCloud server ID defined on Jenkins
     *
     * @Default {@code "sonarcloud-server"}
     */
    public static String DEFAULT_LFJ_SONARCLOUD_SERVER = 'sonarcloud-server'

    /**
     * Default SonarCloud scanner tool ID defined on Jenkins
     *
     * @Default {@code "sonar-scanner-4.0.0"}
     */
    public static String DEFAULT_LFJ_SONARCLOUD_SCANNER_TOOL = 'sonar-scanner-4.0.0'

    /**
     * Default if SonarCloud server supports branch scanning
     *
     * @Default {@code true}
     */
    public static Boolean DEFAULT_LFJ_SONARCLOUD_ALLOW_BRANCH = true

    /**
     * Default if pipeline should fail the build if code doesn't meet SonarCloud
     * Scan Quality Gate.
     *
     * @Default {@code false}
     */
    public static Boolean DEFAULT_LFJ_SONARCLOUD_FAIL_BUILD = true

    /**
     * Default PAX Packaging server host name
     *
     * @Default {@code "zzow01.zowe.marist.cloud"}
     */
    public static String DEFAULT_PAX_PACKAGING_SSH_HOST = 'zzow01.zowe.marist.cloud'

    /**
     * Default PAX Packaging server port
     *
     * @Default {@code "22"}
     */
    public static String DEFAULT_PAX_PACKAGING_SSH_PORT = '22'

    /**
     * Default PAX Packaging server credential defined on Jenkins
     *
     * @Default {@code "ssh-marist-server-zzow01"}
     */
    public static String DEFAULT_PAX_PACKAGING_SSH_CREDENTIAL = 'ssh-marist-server-zzow01'

    /**
     * Default PAX Packaging server default working space
     *
     * @Default {@code "/ZOWE/tmp"}
     */
    public static String DEFAULT_PAX_PACKAGING_REMOTE_WORKSPACE = '/ZOWE/tmp'

    /**
     * Default NPM private registry url for npm install
     *
     * @Default {@code "https://gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-release/"}
     */
    public static String DEFAULT_NPM_PRIVATE_REGISTRY_INSTALL = 'https://gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-release/'

    /**
     * Default NPM private registry url for npm publish
     *
     * @Default {@code "https://gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-local-release/"}
     */
    public static String DEFAULT_NPM_PRIVATE_REGISTRY_PUBLISH = 'https://gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-local-release/'

    /**
     * Default NPM private registry robot account email
     *
     * @Default {@code "giza-jenkins@gmail.com"}
     */
    public static String DEFAULT_NPM_PRIVATE_REGISTRY_EMAIL = 'giza-jenkins@gmail.com'

    /**
     * Default NPM private registry robot account credential on Jenkins
     *
     * @Default {@code "GizaArtifactory"}
     */
    public static String DEFAULT_NPM_PRIVATE_REGISTRY_CREDENTIAL = 'GizaArtifactory'

    /**
     * Default GnuPG Code Signing Key Passphrase Jenkins username/password credential
     *
     * @Default {@code "code-signing-key-passphrase-jack"}
     */
    public static String DEFAULT_GPG_CODE_SIGNING_KEY_PASSPHRASE = 'code-signing-key-passphrase-jack'

    /**
     * Default GnuPG Code Signing Private Key Jenkins Secret file credential
     *
     * @Default {@code "code-signing-key-private-jack"}
     */
    public static String DEFAULT_GPG_CODE_SIGNING_PRIVATE_KEY_FILE = 'code-signing-key-private-jack'
}
