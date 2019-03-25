/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base

import org.zowe.pipelines.base.models.*
import org.zowe.pipelines.base.exceptions.*

/**
 * Create the various stages of a pipeline.
 *
 * <h4>Creating a generic pipeline</h4>
 *
 * <pre>
 * {@code
 *     Stages stages = new Stages()
 *
 *     // Add some stages
 *     // Your stages should be filled out, just using the map stage constructor
 *     // here.
 *     stages.add(name: "Stage 1")
 *     stages.add(name: "Stage 2")
 *
 *     // Execute the stages
 *     stages.execute()
 *
 *     // Stage 1 will be executed
 *     // then Stage 2 will be executed
 * }
 * </pre>
 */
final class Stages {
    /**
     * The first failing stage if there is one.
     */
    private Stage _firstFailingStage

    /**
     * The first stage that is to be executed.
     */
    private Stage _firstStageToExecute

    /**
     * A reference to the stage that was last added to the {@link #_stages} map. This is needed
     * so that we can properly set {@link Stage#next} of the last added stage when a new stage
     * is added.
     */
    private Stage _lastAddedStage

    /**
     * A hash map of the stages in the pipeline.
     *
     * A map key is the name of the stage stored in the value.
     */
    private HashMap<String, Stage> _stages = new HashMap()

    /**
     * Add a stage to be executed.
     *
     * <p>
     *     The stage will be added at the end of the list of stages to be executed.
     * </p>
     *
     * <h5>Method Chaining</h5>
     *
     * Multiple calls to the add method can be done in a single run.
     *
     * <pre>
     *     Stages stages = new Stages()
     *     stages.add(name: "Stage 1").add(name: "Stage 2")
     * </pre>
     *
     * @param stage The stage to add to the pipeline.
     * @return A reference to this class instance for method chaining.
     *
     * @throws StageException when adding a stage that has the same name as another stage.
     */
    Stages add(Stage stage) throws StageException {

        // First validate if the stage name doesn't exist
        if (_stages.containsKey(stage.name)) {
            if (_firstStageToExecute == null) {
                // This is a condition that indicates that our logic is most likely broken
                throw new StageException("First stage was not set but stages already had values in the map", stage.name)
            }

            // The first stage should be setup, othewise a stage exception will be
            // thrown before we get into here. So in setup, we should create the exception
            // to be thrown later.
            firstFailingStage = firstStageToExecute
            firstFailingStage.exception = new StageException("Duplicate stage name: \"${stage.name}\"", _firstFailingStage.name)
        } else {
            // Add stage to map
            _stages.put(stage.name, stage)
        }

        // Set the next stage from the current stage
        if (_lastAddedStage) {
            _lastAddedStage.next = stage
        }

        // If the first stage hasn't been created yet, set it here
        if (!_firstStageToExecute) {
            _firstStageToExecute = stage
        }

        // Set the new current stage to this stage
        _lastAddedStage = stage

        // Return this for chaining
        return this
    }

    /**
     * Executes the pipeline stages in the order that they were added.
     */
    void execute() {
        Stage stage = _firstStageToExecute

        // Loop while we have a stage
        while (stage) {
            // Execute the stage
            stage.execute()

            // Move to the next stage
            stage = stage.next
        }
    }

    /**
     * Get the first failing stage of the pipeline.
     *
     * @return The first failing stage or null if there is none.
     */
    Stage getFirstFailingStage() {
        return _firstFailingStage
    }

    /**
     * Get the first stage of the pipeline. (I.E. the first stage added with {@link #add(Stage)}
     *
     * @return The first pipeline stage that is executed.
     */
    Stage getFirstStageToExecute() {
        return _firstStageToExecute
    }

    /**
     * Gets a stage from the pipeline by name.
     *
     * @param stageName The name of a stage.
     * @return The stage for the given name
     */
    Stage getStage(String stageName) {
        return _stages.get(stageName)
    }

    /**
     * Set the value of the first failing stage.
     *
     * <p>
     *     If the first failing stage is set, this method will not overwrite that value. Instead it
     *     will silently pass.
     * </p>
     *
     * @param stage A stage that was added using {@link #add(Stage)}
     * @throws StageException when a stage is passed that was not added with {@link #add(Stage)}
     */
    void setFirstFailingStage(Stage stage) {
        // Only set the first failing stage if it is currently empty. All other calls
        // will be treated as nothing happened
        if (!_firstFailingStage) {
            if (!_stages.containsValue(stage)) {
                throw new StageException("First failing stage cannot be set to a stage absent from the map.", stage.name)
            }

            _firstFailingStage = stage
        }
    }

    /**
     * Gets the number of stages added to the pipeline.
     * @return The number of stages added to the pipeline.
     */
    int size() {
        return _stages.size()
    }
}
