/* Copyright (C) 2013-2026 TU Dortmund University
 * This file is part of AutomataLib <https://automatalib.net>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.automatalib.modelchecker.monitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

import net.automatalib.common.util.Pair;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Level-synchronous BFS: every returned witness has minimum input length. Model/property reads and observation
 * evaluation must support concurrent calls. The caller owns the executor and must not invoke this checker from a worker
 * of a saturated bounded executor. All submitted work is joined before returning, including on evaluation failure.
 *
 * @param <I>
 *         the system input type
 * @param <M>
 *         the system model type
 * @param <A>
 *         the observation type
 * @param <R>
 *         the finite counterexample type
 */
public abstract class AbstractParallelBFSMonitorModelChecker<I, M, A, R>
        extends AbstractMonitorModelChecker<I, M, A, R> {

    private final Executor executor;

    protected AbstractParallelBFSMonitorModelChecker(Executor executor) {
        this.executor = executor;
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // Drain accepted work before rethrowing submission failures.
    protected <S, P> @Nullable Node<S, P, I> search(Node<S, P, I> initial,
                                                    Function<Node<S, P, I>, Iterable<Node<S, P, I>>> expand) {
        final Set<Pair<S, P>> seen = new HashSet<>();
        assert initial.product != null;
        seen.add(initial.product);
        List<Node<S, P, I>> frontier = List.of(initial);
        while (!frontier.isEmpty()) {
            final List<CompletableFuture<List<Node<S, P, I>>>> tasks = new ArrayList<>(frontier.size());
            RuntimeException failure = null;
            try {
                for (Node<S, P, I> node : frontier) {
                    tasks.add(CompletableFuture.supplyAsync(() -> {
                        final List<Node<S, P, I>> successors = new ArrayList<>();
                        for (Node<S, P, I> successor : expand.apply(node)) {
                            if (successor.product == null) {
                                return List.of(successor);
                            }
                            successors.add(successor);
                        }
                        return successors;
                    }, executor));
                }
            } catch (RuntimeException submissionFailure) {
                failure = submissionFailure;
            } finally {
                try {
                    CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
                } catch (CompletionException evaluationFailure) {
                    if (failure == null) {
                        failure = evaluationFailure;
                    } else {
                        failure.addSuppressed(evaluationFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
            final List<Node<S, P, I>> nextFrontier = new ArrayList<>();
            for (CompletableFuture<List<Node<S, P, I>>> task : tasks) {
                for (Node<S, P, I> node : task.join()) {
                    if (node.product == null) {
                        return node;
                    }
                    if (seen.add(node.product)) {
                        nextFrontier.add(node);
                    }
                }
            }
            frontier = nextFrontier;
        }
        return null;
    }
}
