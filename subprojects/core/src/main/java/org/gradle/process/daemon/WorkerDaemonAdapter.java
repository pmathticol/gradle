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
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public interface WorkerDaemonAdapter {

    /**
     * Executes a set of actions that should be performed in a daemonized process.  Actions are executed
     * sequentially and synchronously.  All actions will be processed and any failures that occur will be
     * thrown in a {@link MultiCauseException}.
     *
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param classpath - The classpath required to run the action.
     * @param sharedPackages - The packages that should be visible in the daemon process.
     * @param actionClass - The implementation class of the action to perform.
     * @param subjects - The set of subjects to perform the action against.
     */
    <T extends Serializable> void executeAllInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Action<T>> actionClass, Iterable<T> subjects);

    /**
     * Executes a single action that should be performed in a daemonized process.  Throws an exception if the action fails.
     *
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param classpath - The classpath required to run the action.
     * @param sharedPackages - The packages that should be visible in the daemon process.
     * @param actionClass - The implementation class of the action to perform.
     * @param subject - The subject to perform the action against.
     */
    <T extends Serializable> void executeInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Action<T>> actionClass, T subject);

    /**
     * Executes a set of transforms that should be performed in a daemonized process.  Transforms are executed
     * sequentially and synchronously.  All transforms will be processed and any failures that occur will be
     * thrown in a {@link MultiCauseException}.
     *
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param classpath - The classpath required to run the action.
     * @param sharedPackages - The packages that should be visible in the daemon process.
     * @param transformerClass - The implementation class of the transformer to invoke.
     * @param subjects - The set of inputs to the transformations.
     * @return - A list of results from the transformations.
     */
    <T extends Serializable, R extends Serializable> List<R> transformAllInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Transformer<R, T>> transformerClass, Iterable<T> subjects);

    /**
     * Executes a single transformation to be performed in a daemonized process. Throws an exception if the transformation fails.
     *
     * @param forkOptions - The process-related options that the daemon should be compatible with.
     * @param classpath - The classpath required to run the action.
     * @param sharedPackages - The packages that should be visible in the daemon process.
     * @param transformerClass - The implementation class of the transformer to invoke.
     * @param subject - The input to the transformation.
     * @return - The result of the transformation.
     */
    <T extends Serializable, R extends Serializable> R transformInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Transformer<R, T>> transformerClass, T subject);
}
