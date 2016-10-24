/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.daemon;

import org.gradle.api.Action;
import org.gradle.api.Transformer;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public interface WorkerDaemonAdapter {

    /**
     * Executes a set of actions that should be performed in a daemonized process.  Throws an exception if any action fails.
     *
     * @param workingDir - The working directory of the daemonized process.
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param action - The implementation of the action to perform.
     * @param subjects - The set of subjects to perform the action against.
     */
    <T extends Serializable> void executeAllInDaemon(File workingDir, DaemonForkOptions forkOptions, Action<T> action, Iterable<T> subjects);

    /**
     * Executes a single action that should be performed in a daemonized process.  Throws an exception if the action fails.
     *
     * @param workingDir - The working directory of the daemonized process.
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param action - The implementation of the action to perform.
     * @param subject - The subject to perform the action against.
     */
    <T extends Serializable> void executeInDaemon(File workingDir, DaemonForkOptions forkOptions, Action<T> action, T subject);

    /**
     * Executes a batch of transformations to be performed in a daemonized process. Throws an exception if any transformation fails.
     *
     * @param workingDir - The working directory of the daemonized process.
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param transformer - The transformation that should be performed.
     * @param subjects - The set of inputs to the transformations.
     * @return - A list of results from the transformations.
     */
    <T extends Serializable, R extends Serializable> List<R> executeAllInDaemon(File workingDir, DaemonForkOptions forkOptions, Transformer<R, T> transformer, Iterable<T> subjects);

    /**
     * Executes a single transformation to be performed in a daemonized process. Throws an exception if the transformation fails.
     *
     * @param workingDir - The working directory of the daemonized process.
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param transformer - The transformation that should be performed.
     * @param subject - The input to the transformation.
     * @return - The result of the transformation.
     */
    <T extends Serializable, R extends Serializable> R executeInDaemon(File workingDir, DaemonForkOptions forkOptions, Transformer<R, T> transformer, T subject);
}
