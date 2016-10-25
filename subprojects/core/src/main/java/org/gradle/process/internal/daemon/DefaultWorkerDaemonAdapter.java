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

package org.gradle.process.internal.daemon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.daemon.DaemonForkOptions;
import org.gradle.process.daemon.WorkerDaemonAdapter;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

public class DefaultWorkerDaemonAdapter implements WorkerDaemonAdapter {
    private final WorkerDaemonFactory workerDaemonFactory;

    public DefaultWorkerDaemonAdapter(WorkerDaemonFactory workerDaemonFactory) {
        this.workerDaemonFactory = workerDaemonFactory;
    }

    @Override
    public <T extends Serializable> void executeAllInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Action<T>> actionClass, Iterable<T> subjects) {
        Iterator<T> itr = subjects == null ? Iterators.<T>emptyIterator() : subjects.iterator();
        if (itr.hasNext()) {
            Class<? extends Serializable> subjectClass = itr.next().getClass();
            WorkerDaemon daemon = workerDaemonFactory.getDaemon(forkOptions.getWorkingDir(), toDaemonOptions(actionClass, subjectClass, forkOptions, classpath, sharedPackages));
            WrappedDaemonAction<T> workerAction = new WrappedDaemonAction<T>(actionClass);
            List<Throwable> failures = Lists.newArrayList();
            for (T subject : subjects) {
                try {
                    executeAction(daemon, workerAction, subject);
                } catch (Throwable t) {
                    failures.add(t);
                }
            }

            if (failures.size() > 0) {
                throw new DefaultMultiCauseException("Failed to execute all actions.", failures);
            }
        }
    }

    @Override
    public <T extends Serializable> void executeInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Action<T>> actionClass, T subject) {
        executeAllInDaemon(forkOptions, classpath, sharedPackages, actionClass, Lists.<T>newArrayList(subject));
    }

    private <T extends Serializable> void executeAction(WorkerDaemon daemon, WrappedDaemonAction<T> workerAction, final T subject) {
        SubjectSpec<T> spec = new SubjectSpec<T>(subject);
        WorkerDaemonResult result = daemon.execute(workerAction, spec);
        if (!result.isSuccess()) {
            throw UncheckedException.throwAsUncheckedException(result.getException());
        }
    }

    @Override
    public <T extends Serializable, R extends Serializable> List<R> transformAllInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Transformer<R, T>> transformerClass, Iterable<T> subjects) {
        List<R> results = Lists.newArrayList();
        Iterator<T> itr = subjects == null ? Iterators.<T>emptyIterator() : subjects.iterator();
        if (itr.hasNext()) {
            Class<? extends Serializable> subjectClass = itr.next().getClass();
            WorkerDaemon daemon = workerDaemonFactory.getDaemon(forkOptions.getWorkingDir(), toDaemonOptions(transformerClass, subjectClass, forkOptions, classpath, sharedPackages));
            WrappedDaemonTransformer<R, T> workerTransform = new WrappedDaemonTransformer<R, T>(transformerClass);
            List<Throwable> failures = Lists.newArrayList();
            for (T subject : subjects) {
                try {
                    R result = executeTransform(daemon, workerTransform, subject);
                    results.add(result);
                } catch(Throwable t) {
                    failures.add(t);
                }
            }

            if (failures.size() > 0) {
                throw new DefaultMultiCauseException("Failed to execute all transforms.", failures);
            }
        }
        return results;
    }

    @Override
    public <T extends Serializable, R extends Serializable> R transformInDaemon(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Transformer<R, T>> transformerClass, T subject) {
        List<R> results = transformAllInDaemon(forkOptions, classpath, sharedPackages, transformerClass, Lists.<T>newArrayList(subject));
        if (results.size() > 0) {
            return results.get(0);
        } else {
            return null;
        }
    }

    private <T extends Serializable, R extends Serializable> R executeTransform(WorkerDaemon daemon, WrappedDaemonTransformer<R, T> workerTransform, final T subject) {
        SubjectSpec<T> spec = new SubjectSpec<T>(subject);
        WrappedDaemonResult<R> result = (WrappedDaemonResult<R>) daemon.execute(workerTransform, spec);
        if (!result.isSuccess()) {
            throw UncheckedException.throwAsUncheckedException(result.getException());
        }
        return result.getResult();
    }

    private DaemonForkOptions toDaemonOptions(Class<?> actionClass, Class<? extends Serializable> subjectClass, JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages) {
        ImmutableList.Builder<File> classpathBuilder = ImmutableList.builder();
        if (classpath != null) {
            classpathBuilder.addAll(classpath);
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

    private static class SubjectSpec<T extends Serializable> implements WorkSpec {
        final T subject;

        public SubjectSpec(T subject) {
            this.subject = subject;
        }

        public T getSubject() {
            return subject;
        }
    }

    private static class WrappedDaemonAction<T extends Serializable> implements WorkerDaemonAction<SubjectSpec<T>> {
        final Class<? extends Action<T>> actionClass;

        public WrappedDaemonAction(Class<? extends Action<T>> actionClass) {
            this.actionClass = actionClass;
        }

        @Inject
        Instantiator getInstantiator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkerDaemonResult execute(SubjectSpec<T> spec) {
            try {
                Action<T> action = actionClass.newInstance();
                action.execute(spec.getSubject());
                return new WorkerDaemonResult(true, null);
            } catch (Throwable t) {
                return new WorkerDaemonResult(true, t);
            }
        }
    }

    private static class WrappedDaemonResult<R extends Serializable> extends WorkerDaemonResult {
        final R result;

        public WrappedDaemonResult(@Nullable R result, boolean didWork, @Nullable Throwable exception) {
            super(didWork, exception);
            this.result = result;
        }

        public R getResult() {
            return result;
        }
    }

    private static class WrappedDaemonTransformer<R extends Serializable, T extends Serializable> implements WorkerDaemonAction<SubjectSpec<T>> {
        final Class<? extends Transformer<R, T>> transformerClass;

        public WrappedDaemonTransformer(Class<? extends Transformer<R, T>> transformerClass) {
            this.transformerClass = transformerClass;
        }

        @Inject
        Instantiator getInstantiator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkerDaemonResult execute(SubjectSpec<T> spec) {
            try {
                Transformer<R, T> transformer = transformerClass.newInstance();
                R result = transformer.transform(spec.getSubject());
                return new WrappedDaemonResult<R>(result, true, null);
            } catch (Throwable t) {
                return new WrappedDaemonResult<R>(null, true, t);
            }
        }
    }
}
