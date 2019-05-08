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

import org.zowe.jenkins_shared_library.pipelines.base.exceptions.BranchException
import org.zowe.jenkins_shared_library.pipelines.base.interfaces.BranchProperties

/**
 * Manages the branches of a Pipeline.
 *
 * @see jenkins_shared_library.pipelines.base.interfaces.BranchProperties
 *
 * @param <T> This type ensures that the branch properties implement the {@link BranchProperties}
 *            interface and all branches are of the same property.
 */
final class Branches<T extends BranchProperties> implements Serializable {
    /**
     * The class object used to instantiate a new branch value.
     */
    final Class<T> propertyClass

    /**
     * The mapping of branches.
     */
    private HashMap<String, T> _branches = new HashMap()

    /**
     * Constructs the class with the specified factory class.
     *
     * @param propertyClass The class that is used to instantiate a new property object.
     */
    Branches(final Class<T> propertyClass) {
        this.propertyClass = propertyClass
    }

    /**
     * Adds a branch object as protected.
     *
     * @Note If the branch is already defined, the config will be overwritten.
     *
     * @param branch The properties of a branch that is protected.
     * @return The object that was added.
     * @throws BranchException when a branch is already protected.
     */
    T add(T branch) throws BranchException {
        return _branches.put(branch.name, branch)
    }

    /**
     * Adds a list of branches to the map.
     *
     * @param branches The branches to add as protected.
     */
    void add(List<T> branches) {
        for (T branch : branches) {
            add(branch)
        }
    }

    /**
     * Add a branch map into the object. This map must follow the syntax of the Groovy Map Object
     * Constructor.
     *
     * @param branch The branch to add.
     * @return The object that was added
     */
    T addMap(Map branch) {
        return add(this.propertyClass.newInstance(branch))
    }

    /**
     * Adds a list of branches to the protected maps. The elements of the list must follow the syntax
     * of the Groovy Map Object Constructor.
     *
     * @param branches The branches to add as protected.
     */
    void addMap(List<Map> branches) {
        for (Map branch : branches) {
            addMap(branch)
        }
    }

    /**
     * Gets a protected branch's properties from the map.
     *
     * @Note: This method is used to access the property based on raw branch name or name pattern.
     * Usually we should use {@link #getByPattern(String)} to find the branch properties.
     *
     * @param branchName The name or the pattern of the branch to retrieve
     * @return The object for the branch name or null if there is no branch of the corresponding name.
     */
    T get(String branchName) {
        return _branches.get(branchName)
    }

    /**
     * Gets a protected branch's properties from the map by name pattern.
     *
     * @Note: This is the recommended method to find a branch property.
     *
     * @param branchName The name of the branch to retrieve
     * @return The object for the branch name or null if there is no branch of the corresponding name.
     */
    T getByPattern(String branchName) {
        def result

        for (def b in _branches) {
            if (branchName.matches(b.key)) {
                result = b.value
                break
            }
        }

        return result
    }

    /**
     * Checks if a given branch name is protected.
     * @param branchName The name of the branch to check.
     * @return True if the branch is protected, false otherwise.
     */
    boolean isProtected(String branchName) {
        def branch = this.getByPattern(branchName)
        return branch && branch.getIsProtected()
    }

    /**
     * Removes a branch from the protected list.
     * @param branchName The name of the branch to remove.
     * @return The object that was removed or null if none was removed
     */
    T remove(String branchName) {
        return _branches.remove(branchName)
    }
}
