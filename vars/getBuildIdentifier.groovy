/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2019
 */

/**
 * Get Build Identifier
 *
 * Example output:
 *    - 20180101.010101-pr-11-1       PR-11 branch build #1 at 20180101.010101
 *    - 20180101.010101-13            master branch build #13 at 20180101.010101
 *    - 20180101-010101-13            master branch build #13 using "%Y%m%d-%H%M%S" format
 *
 * @param  includeTimestamp=true      If add timstamp to the build identifier, or specify a 
 *                                    formatting string. Support format is using "date" command.
 *                                    http://man7.org/linux/man-pages/man1/date.1.html
 * @param  defaultBranch='master'     The default branch name. If branch name is not same as this,
 *                                    the identifier will include sanitized branch name. Set it to
 *                                    '__EXCLUDE__' to completely exclude branch name from the
 *                                    identifier.
 * @param  includeBuildNumber=true    If add build number to the build identifier
 * @return                            build identifier
 */
def call(includeTimestamp=true, String defaultBranch='master', Boolean includeBuildNumber=true) {
  def defaultTimestamp = "%Y%m%d.%H%M%S"
  def ts = '';
  def result = ''

  if (includeTimestamp) {
    def includeTimestampType = includeTimestamp.getClass().toString()
    if (includeTimestampType.endsWith('String')) {
      ts = sh(script: "date +${includeTimestamp}", returnStdout: true).trim()
    } else if (includeTimestampType.endsWith('Boolean')) {
      ts = sh(script: "date +${defaultTimestamp}", returnStdout: true).trim()
    }
    if (ts) {
      result += ts
    }
  }

  def branch = env.BRANCH_NAME
  if (defaultBranch != '__EXCLUDE__') {
    if (branch.startsWith('origin/')) {
      branch = branch.substring(7)
    }
    if (branch != defaultBranch) {
      branch = branch.replaceAll(/[^a-zA-Z0-9]/, '-').replaceAll(/[\-]+/, '-').toLowerCase()
      if (result != '') {
        result += '-'
      }
      result += branch
    }
  }

  if (includeBuildNumber) {
    if (result != '') {
      result += '-'
    }
    result += env.BUILD_NUMBER
  }

  return result
}
