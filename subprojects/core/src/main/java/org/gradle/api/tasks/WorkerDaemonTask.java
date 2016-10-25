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

package org.gradle.api.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.daemon.WorkerDaemonAdapter;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.process.daemon.DaemonForkOptions;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

public abstract class WorkerDaemonTask extends DefaultTask {
    private JavaForkOptions forkOptions = new DefaultJavaForkOptions(getFileResolver());
    private List<String> sharedPackages;
    private FileCollection classpath;

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected WorkerDaemonAdapter getWorkerDaemonAdapter() {
        throw new UnsupportedOperationException();
    }

    public JavaForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(JavaForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    public List<String> getSharedPackages() {
        return sharedPackages;
    }

    public void setSharedPackages(List<String> sharedPackages) {
        this.sharedPackages = sharedPackages;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    protected <T extends Serializable> void executeAllInDaemon(Class<? extends Action<T>> actionClass, Iterable<T> subjects) {
        getWorkerDaemonAdapter().executeAllInDaemon(forkOptions, classpath, sharedPackages, actionClass, subjects);
    }

    protected <T extends Serializable> void executeInDaemon(Class<? extends Action<T>> actionClass, T subject) {
        getWorkerDaemonAdapter().executeInDaemon(forkOptions, classpath, sharedPackages, actionClass, subject);
    }

    protected <T extends Serializable, R extends Serializable> List<R> transformAllInDaemon(Class<? extends Transformer<R, T>> transformerClass, Iterable<T> subjects) {
        return getWorkerDaemonAdapter().transformAllInDaemon(forkOptions, classpath, sharedPackages, transformerClass, subjects);
    }

    protected <T extends Serializable, R extends Serializable> R transformInDaemon(Class<? extends Transformer<R, T>> transformerClass, T subject) {
        return getWorkerDaemonAdapter().transformInDaemon(forkOptions, classpath, sharedPackages, transformerClass, subject);
    }
}
