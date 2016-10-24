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

    <T extends Serializable> void executeAllInDaemon(Action<T> action, Iterable<T> subjects) {
        Iterator<T> itr = subjects == null ? Iterators.<T>emptyIterator() : subjects.iterator();
        if (itr.hasNext()) {
            T subject = itr.next();
            getWorkerDaemonAdapter().executeInDaemon(getProject().getProjectDir(), getDaemonOptions(action.getClass(), subject.getClass()), action, subjects);
        }
    }

    <T extends Serializable> void executeInDaemon(Action<T> action, T subject) {
        getWorkerDaemonAdapter().executeInDaemon(getProject().getProjectDir(), getDaemonOptions(action.getClass(), subject.getClass()), action, subject);
    }

    <T extends Serializable, R extends Serializable> List<R> executeAllInDaemon(Transformer<R, T> transformer, Iterable<T> subjects) {
        Iterator<T> itr = subjects == null ? Iterators.<T>emptyIterator() : subjects.iterator();
        if (itr.hasNext()) {
            T subject = itr.next();
            return getWorkerDaemonAdapter().executeInDaemon(getProject().getProjectDir(), getDaemonOptions(transformer.getClass(), subject.getClass()), transformer, subjects);
        } else {
            return Lists.newArrayList();
        }
    }

    <T extends Serializable, R extends Serializable> R executeInDaemon(Transformer<R, T> transformer, T subject) {
        return getWorkerDaemonAdapter().executeInDaemon(getProject().getProjectDir(), getDaemonOptions(transformer.getClass(), subject.getClass()), transformer, subject);
    }

    private DaemonForkOptions getDaemonOptions(Class<?> actionClass, Class<? extends Serializable> subjectClass) {
        ImmutableList.Builder<File> classpathBuilder = ImmutableList.builder();
        if (classpath != null) {
            classpathBuilder.addAll(classpath.getFiles());
        }

        classpathBuilder.add(ClasspathUtil.getClasspathForClass(Action.class));
        classpathBuilder.add(ClasspathUtil.getClasspathForClass(actionClass));
        if (subjectClass.getClassLoader() != null) {
            classpathBuilder.add(ClasspathUtil.getClasspathForClass(subjectClass));
        }
        Iterable<File> daemonClasspath = classpathBuilder.build();

        ImmutableList.Builder<String> sharedPackagesBuilder = ImmutableList.builder();
        if (sharedPackages != null) {
            sharedPackagesBuilder.addAll(sharedPackages);
        }

        if (actionClass.getPackage() != null) {
            sharedPackagesBuilder.add(actionClass.getPackage().getName());
        }
        if (subjectClass.getPackage() != null) {
            sharedPackagesBuilder.add(subjectClass.getPackage().getName());
        }
        sharedPackagesBuilder.add("org.gradle.api");
        Iterable<String> daemonSharedPackages = sharedPackagesBuilder.build();

        return new DaemonForkOptions(forkOptions.getMinHeapSize(), forkOptions.getMaxHeapSize(), forkOptions.getAllJvmArgs(), daemonClasspath, daemonSharedPackages);
    }


}
