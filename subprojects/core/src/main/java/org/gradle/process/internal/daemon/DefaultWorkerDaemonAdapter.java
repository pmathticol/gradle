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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.UncheckedException;
import org.gradle.process.daemon.DaemonForkOptions;
import org.gradle.process.daemon.WorkerDaemonAdapter;

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
    public <T extends Serializable> void executeInDaemon(File workingDir, DaemonForkOptions forkOptions, Action<T> action, Iterable<T> subjects) {
        Iterator<T> itr = subjects == null ? Iterators.<T>emptyIterator() : subjects.iterator();
        if (itr.hasNext()) {
            WorkerDaemon daemon = workerDaemonFactory.getDaemon(workingDir, forkOptions);
            WrappedDaemonAction<T> workerAction = new WrappedDaemonAction<T>(action);
            for (T subject : subjects) {
                executeAction(daemon, workerAction, subject);
            }
        }
    }

    @Override
    public <T extends Serializable> void executeInDaemon(File workingDir, DaemonForkOptions forkOptions, Action<T> action, T subject) {
        executeInDaemon(workingDir, forkOptions, action, Lists.<T>newArrayList(subject));
    }

    private <T extends Serializable> void executeAction(WorkerDaemon daemon, WrappedDaemonAction<T> workerAction, final T subject) {
        SubjectSpec<T> spec = new SubjectSpec<T>(subject);
        WorkerDaemonResult result = daemon.execute(workerAction, spec);
        if (!result.isSuccess()) {
            throw UncheckedException.throwAsUncheckedException(result.getException());
        }
    }

    @Override
    public <T extends Serializable, R extends Serializable> List<R> executeInDaemon(File workingDir, DaemonForkOptions forkOptions, Transformer<R, T> transformer, Iterable<T> subjects) {
        List<R> results = Lists.newArrayList();
        Iterator<T> itr = subjects == null ? Iterators.<T>emptyIterator() : subjects.iterator();
        if (itr.hasNext()) {
            WorkerDaemon daemon = workerDaemonFactory.getDaemon(workingDir, forkOptions);
            WrappedDaemonTransformer<R, T> workerTransform = new WrappedDaemonTransformer<R, T>(transformer);
            for (T subject : subjects) {
                R result = executeTransform(daemon, workerTransform, subject);
                results.add(result);
            }
        }
        return results;
    }

    @Override
    public <T extends Serializable, R extends Serializable> R executeInDaemon(File workingDir, DaemonForkOptions forkOptions, Transformer<R, T> transformer, T subject) {
        List<R> results = executeInDaemon(workingDir, forkOptions, transformer, Lists.<T>newArrayList(subject));
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
        final Action<T> action;

        public WrappedDaemonAction(Action<T> action) {
            this.action = action;
        }

        @Override
        public WorkResult execute(SubjectSpec<T> spec) {
            try {
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
        final Transformer<R, T> transformer;

        public WrappedDaemonTransformer(Transformer<R, T> transformer) {
            this.transformer = transformer;
        }

        @Override
        public WorkResult execute(SubjectSpec<T> spec) {
            try {
                R result = transformer.transform(spec.getSubject());
                return new WrappedDaemonResult<R>(result, true, null);
            } catch (Throwable t) {
                return new WrappedDaemonResult<R>(null, true, t);
            }
        }
    }
}
