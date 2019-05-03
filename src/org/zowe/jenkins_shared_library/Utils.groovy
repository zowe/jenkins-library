/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library

import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import org.apache.commons.lang3.StringEscapeUtils
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException

/**
 * Various static methods which doesn't have a class.
 *
 * NOTICE: any method which getting complicated should be extracted as an independent Class.
 */
class Utils {
    /**
     * Read resource file
     * @param  path        path to the resource file
     * @return             the file content
     * @throws IOException if failed to read the file
     */
    static String loadResource(String path) throws IOException {
        return new File(path).getText('UTF-8');
    }

    /**
     * Sanitize branch name so it can only contains:
     * - letters
     * - numbers
     * - dash
     *
     * @param  branch      branch name
     * @return             sanitized branch name
     */
    static String sanitizeBranchName(String branch) {
        if (branch.startsWith('origin/')) {
            branch = branch.substring(7)
        }
        branch = branch.replaceAll(/[^a-zA-Z0-9]/, '-')
                       .replaceAll(/[\-]+/, '-')
                       .toLowerCase()

        return branch
    }

    /**
     * Parse semantic version into trunks.
     *
     * @param  version     version to parse
     * @return             a map with major, minor, path, prerelease and metadata keys.
     */
    static Map parseSemanticVersion(String version) {
        // https://github.com/semver/semver/issues/232
        def matches = version =~ /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(\.(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*)?(\+[0-9a-zA-Z-]+(\.[0-9a-zA-Z-]+)*)?$/
        // println "${matches.matches()}: ${matches[0].size()}"
        if (matches.matches() && matches[0] && matches[0].size() == 10) {
            Map trunks = [:]
            trunks['major'] = matches[0][1].toInteger()
            trunks['minor'] = matches[0][2].toInteger()
            trunks['patch'] = matches[0][3].toInteger()
            trunks['prerelease'] = matches[0][4] ? (matches[0][4].startsWith('-') ? matches[0][4].substring(1) : matches[0][4]) : ''
            trunks['metadata'] = matches[0][8] ? (matches[0][8].startsWith('+') ? matches[0][8].substring(1) : matches[0][8]) : ''

            return trunks
        } else {
            throw new InvalidArgumentException('version', "Version \"${version}\" is not a valid semantic version.")
        }
    }

    /**
     * Convert a version bump
     *
     * This should have same logic as `npm version`.
     *
     * @param  versionTrunks    version trunks map, returned from parseSemanticVersion
     * @param  bump             new expected version, for example, patch
     * @return                  interpreted semantic version, for exmaple, 1.2.4
     */
    static String interpretSemanticVersionBump(Map versionTrunks, String bump) {
        bump = bump.toLowerCase()

        String semver = ''

        if (bump == 'patch') {
            semver = "${versionTrunks['major']}" +
                         ".${versionTrunks['minor']}" +
                         ".${versionTrunks['patch'] + 1}"
        } else if (bump == 'minor') {
            semver = "${versionTrunks['major']}" +
                         ".${versionTrunks['minor'] + 1}" +
                         ".${versionTrunks['patch']}"
        } else if (bump == 'major') {
            semver = "${versionTrunks['major'] + 1}" +
                         ".${versionTrunks['minor']}" +
                         ".${versionTrunks['patch']}"
        } else if (bump =~ /[0-9]+\.[0-9]+\.[0-9]+/) {
            semver = bump
        } else {
            throw new InvalidArgumentException('bump', "Version \"${bump}\" is not accepted.")
        }

        return semver
    }

    /**
     * Get current timestamp in a format of YYYYMMDDHHMMSSMMM.
     *
     * The result only includes numbers.
     *
     * @return  timestamp string
     */
    static String getTimestamp(String format = "yyyyMMddHHmmss") {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format))
        // return Instant.now().toString().replaceAll(/[^0-9]/, '')
    }

    /**
     * Get Build Identifier
     *
     * Example output:
     *    - 20180101.010101-pr-11-1       PR-11 branch build #1 at 20180101.010101
     *    - 20180101.010101-13            master branch build #13 at 20180101.010101
     *    - 20180101-010101-13            master branch build #13 using "%Y%m%d-%H%M%S" format
     *
     * @param  env                        Jenkins environment variable
     * @param  includeTimestamp           Boolean|String, default is true. If add timstamp to the build identifier, or specify a
     *                                    formatting string. Support format is using Java DateTimeFormatter:
     *                                    https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
     * @param  excludeBranches            List|String, The branches should not be excluded from identifier. Set it to
     *                                    '__ALL__' to completely exclude branch name from the
     *                                    identifier.
     * @param  includeBuildNumber         Boolean, default is true. If add build number to the build identifier
     * @return                            build identifier
     */
    static String getBuildIdentifier(env, Map args = [:]) {
        def defaultTimestamp = "yyyyMMdd.HHmmss"
        def now = LocalDateTime.now()
        def ts = '';
        def result = []

        // check args and provide default values
        def includeTimestamp = args.containsKey('includeTimestamp') ? args['includeTimestamp'] : true;
        List excludeBranches = []
        if (args.containsKey('excludeBranches')) {
            if (args['excludeBranches'] instanceof String) {
                args['excludeBranches'].split(',').each { it ->
                    excludeBranches << it.trim().toLowerCase()
                }
            } else if (args['excludeBranches'] instanceof List) {
                args['excludeBranches'].each { it ->
                    excludeBranches << it.toLowerCase()
                }
            }
        }
        def includeBuildNumber = args.containsKey('includeBuildNumber') ? args['includeBuildNumber'] : true;

        // handle includeTimestamp
        if (includeTimestamp instanceof String && includeTimestamp != '') {
            ts = now.format(DateTimeFormatter.ofPattern(includeTimestamp))
        } else if (includeTimestamp instanceof Boolean && includeTimestamp) {
            ts = now.format(DateTimeFormatter.ofPattern(defaultTimestamp))
        }
        if (ts) {
            result << ts
        }

        // handle excludeBranches
        if (!(excludeBranches.size() == 1 && excludeBranches[0] == '__all__') &&
            env && env.BRANCH_NAME
        ) {
            def branch = env.BRANCH_NAME
            if (!excludeBranches.contains(branch)) {
                result << sanitizeBranchName(branch)
            }
        }

        // handle includeBuildNumber
        if (includeBuildNumber && env && env.BUILD_NUMBER) {
            result << env.BUILD_NUMBER
        }

        return result.join('-')
    }

    /**
     * Get release identifier from branch name
     *
     * Example output:
     *    - SNAPSHOT             default release name on master branch
     *    - pr-13                pull request #13
     *
     * Usage examples:
     * - getReleaseIdentifier(env, ['master': 'snapshot', 'staging': 'latest'])
     *
     * @param  mappings                  mappings of branch name and identifier
     * @return                           branch identifier
     */
    static String getReleaseIdentifier(env, Map mappings = ['master': 'SNAPSHOT']) {
        def branch = sanitizeBranchName(env.BRANCH_NAME)
        def result = branch

        for (entry in mappings) {
            if (branch == entry.key.toLowerCase()) {
                result = entry.value
                break
            }
        }

        return result
    }

    /**
     * Convert a map to URL query string
     * @param  params     map to hold the URL parameters
     * @return            URL query string
     */
    static String getUriQueryString(Map params = [:]) {
        // convert params to querystring
        return params.collect { k, v -> k + '=' + URLEncoder.encode("${v}", 'UTF-8') }.join('&')
    }

    /**
     * Escape special characters in text to make it safe to put in XML
     * @param  params     text to escape
     * @return            escaped text
     */
    static String escapeXml(String text) {
        return StringEscapeUtils.escapeXml11(text)
    }

    /**
     * Get logger
     * @param  name    name of the logger
     * @return         a Logger instance
     */
    static Logger getLogger(String name = '') {
        // reformat the logging
        System.setProperty('java.util.logging.SimpleFormatter.format',
              '[%1$tF %1$tT] [%4$-7s] %5$s %n');

        // get configured logging level
        String logLevel = System.getProperty("logLevel")
        Level level = logLevel ? Level.parse(logLevel) : Constants.DEFAULT_LOGGING_LEVEL

        // init logger
        Logger logger = Logger.getLogger(name)
        logger.setUseParentHandlers(false);
        logger.setLevel(level);

        // remove existing console handler
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            if (handler.getClass() == ConsoleHandler.class) {
                logger.removeHandler(handler);
            }
        }

        // init console handler and attach to logger
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        logger.addHandler(consoleHandler);

        return logger
    }

    /**
     * [waitForInput description]
     * @param  jenkinsSteps    jenkins steps object
     * @param  args            arguments for the input:
     *                         - timeout        map of timeout, example [time: 2, unit: 'MINUTES']
     *                         - message        message of the input
     *                         - proceedButton  proceed button text
     * @return     [description]
     */
    static Map waitForInput(jenkinsSteps, Map args = [:]) {
        Map result = [
            timeout: false,
            proceed: false,
            user: null
        ]
        // define default values
        if (!args.containsKey('timeout')) {
            args['timeout'] = [time: 2, unit: 'MINUTES']
        }
        if (!args.containsKey('message')) {
            args['message'] = 'Please confirm to proceed:'
        }
        if (!args.containsKey('proceedButton')) {
            args['proceedButton'] = 'Proceed'
        }
        def varName = "INPUT_CONFIRM_${getTimestamp()}"

        try {
            jenkinsSteps.timeout(args['timeout']) {
                result['user'] = jenkinsSteps.input(
                    message             : args['message'],
                    ok                  : args['proceedButton'],
                    submitterParameter  : varName
                )
                result['proceed'] = true
            }
        } catch(err) { // timeout reached or input false
            result['user'] = err.getCauses()[0].getUser().toString()
            if ('SYSTEM' == result['user']) { // SYSTEM means timeout.
                result['timeout'] = true
                result['user'] = 'TIMEOUT'
            }
        }

        return result
    }
}
