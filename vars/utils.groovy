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
 * Create a conditional build stage
 * 
 * @param  name    Stage name
 * @param  execute condition to run this stage, should return a boolean value
 * @param  block   stage build code
 * @return
 */
def conditionalStage(name, execute, block) {
  return stage(name, execute ? block : { echo "skipped stage $name" })
}
