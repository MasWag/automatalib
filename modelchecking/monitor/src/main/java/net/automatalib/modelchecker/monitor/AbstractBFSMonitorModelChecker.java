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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

import net.automatalib.common.util.Holder;
import net.automatalib.common.util.Pair;
import net.automatalib.graph.IndefiniteGraph;
import net.automatalib.util.graph.traversal.GraphTraversal;
import net.automatalib.util.graph.traversal.GraphTraversalAction;
import net.automatalib.util.graph.traversal.GraphTraversalVisitor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Breadth-first monitor checking using the shared graph traversal.
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
public abstract class AbstractBFSMonitorModelChecker<I, M, A, R> extends AbstractMonitorModelChecker<I, M, A, R> {

    @Override
    protected <S, P> @Nullable Node<S, P, I> search(Node<S, P, I> initial,
                                                    Function<Node<S, P, I>, Iterable<Node<S, P, I>>> expand) {
        final Set<Pair<S, P>> seen = new HashSet<>();
        assert initial.product != null;
        seen.add(initial.product);
        final IndefiniteGraph<Node<S, P, I>, Node<S, P, I>> graph = new IndefiniteGraph<>() {

            @Override
            public Iterator<Node<S, P, I>> getOutgoingEdgesIterator(Node<S, P, I> node) {
                return expand.apply(node).iterator();
            }

            @Override
            public Iterator<Node<S, P, I>> iterator() {
                throw new UnsupportedOperationException("On-the-fly product");
            }

            @Override
            public Node<S, P, I> getTarget(Node<S, P, I> edge) {
                return edge;
            }
        };
        final Holder<@Nullable Node<S, P, I>> result = new Holder<>(null);
        GraphTraversal.breadthFirst(graph,
                                    initial,
                                    new GraphTraversalVisitor<Node<S, P, I>, Node<S, P, I>, @Nullable Void>() {

                                        @Override
                                        public GraphTraversalAction processEdge(Node<S, P, I> source,
                                                                                @Nullable Void data,
                                                                                Node<S, P, I> edge,
                                                                                Node<S, P, I> target,
                                                                                Holder<@Nullable Void> holder) {
                                            if (target.product == null) {
                                                result.value = target;
                                                return GraphTraversalAction.ABORT_TRAVERSAL;
                                            }
                                            return seen.add(target.product) ?
                                                    GraphTraversalAction.EXPLORE :
                                                    GraphTraversalAction.IGNORE;
                                        }
                                    });
        return result.value;
    }
}
