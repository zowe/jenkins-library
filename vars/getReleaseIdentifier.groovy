/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018
 */

/**
 * Get Release Identifier
 *
 * Based on branch name sanitized, remove all special characters except for:
 * numbers, alphabets, dash
 *
 * Example output:
 *    - SNAPSHOT             default release name
 *    - PR-13                pull request #13
 *
 * @param  defaultBranch='master'     The default branch name. If branch name is not same as this,
 *                                    the identifier will include sanitized branch name
 * @param  defaultIdentifier='SNAPSHOT' The default identifier name. If branch name is same as ${defaultBranch},
 *                                    the identifier will be ${defaultIdentifier}
 * @return                            branch identifier
 */
def call(String defaultBranch='master', String defaultIdentifier='SNAPSHOT') {
  def result = defaultIdentifier
  def branch = env.BRANCH_NAME

  // sanitize branch name
  if (branch.startsWith('origin/')) {
    branch = branch.substring(7)
  }
  branch = branch.replaceAll(/[^a-zA-Z0-9]/, '-').replaceAll(/[\-]+/, '-').toUpperCase()

  if (branch != defaultBranch.toUpperCase()) {
    result = branch
  }

  return result
}
