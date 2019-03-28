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

import org.zowe.jenkins_shared_library.pipelines.base.exceptions.ProtectedBranchException
import org.zowe.jenkins_shared_library.pipelines.base.interfaces.ProtectedBranchProperties

/**
 * Manages the protected branches of a Pipeline.
 * @param <T> This type ensures that the branch properties implement the {@link ProtectedBranchProperties}
 *            interface and all branches are of the same property.
 */
final class ProtectedBranches<T extends ProtectedBranchProperties> implements Serializable {
    /**
     * The class object used to instantiate a new protected branch value.
     */
    final Class<T> propertyClass

    /**
     * The mapping of protected branches.
     */
    private HashMap<String, T> _protectedBranches = new HashMap()

    /**
     * Constructs the class with the specified factory class.
     *
     * @param propertyClass The class that is used to instantiate a new property object.
     */
    ProtectedBranches(final Class<T> propertyClass) {
        this.propertyClass = propertyClass
    }

    /**
     * Adds a branch object as protected.
     * @param branch The properties of a branch that is protected.
     * @return The object that was added.
     * @throws ProtectedBranchException when a branch is already protected.
     */
    T add(T branch) throws ProtectedBranchException {
        if (_protectedBranches.hasProperty(branch.name)) {
            throw new ProtectedBranchException("${branch.name} already exists as a protected branch.")
        }

        return _protectedBranches.put(branch.name, branch)
    }

    /**
     * Adds a list of branches to the map.
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
     * @param branch The branch to add.
     * @return The object that was added
     */
    T addMap(Map branch) {
        return add(this.propertyClass.newInstance(branch))
    }

    /**
     * Adds a list of branches to the protected maps. The elements of the list must follow the syntax
     * of the Groovy Map Object Constructor.
     * @param branches The branches to add as protected.
     */
    void addMap(List<Map> branches) {
        for (Map branch : branches) {
            addMap(branch)
        }
    }

    /**
     * Gets a protected branch's properties from the map.
     * @param branchName The name of the branch to retrieve
     * @return The object for the branch name or null if there is no branch of the corresponding name.
     */
    T get(String branchName) {
        return _protectedBranches.get(branchName)
    }

    /**
     * Checks if a given branch name is protected.
     * @param branchName The name of the branch to check.
     * @return True if the branch is protected, false otherwise.
     */
    boolean isProtected(String branchName) {
        return _protectedBranches.containsKey(branchName)
    }

    /**
     * Removes a branch from the protected list.
     * @param branchName The name of the branch to remove.
     * @return The object that was removed or null if none was removed
     */
    T remove(String branchName) {
        return _protectedBranches.remove(branchName)
    }
}
