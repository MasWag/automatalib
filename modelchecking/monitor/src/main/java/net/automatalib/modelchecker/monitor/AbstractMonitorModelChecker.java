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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import net.automatalib.automaton.fsa.DFA;
import net.automatalib.common.util.Pair;
import net.automatalib.common.util.collection.AbstractSimplifiedIterator;
import net.automatalib.modelchecking.ModelChecker;
import net.automatalib.ts.output.DeterministicTransitionOutputTS;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Checks a prefix-closed property on synchronous observations, one per system edge. The property is assumed
 * prefix-closed; neither its states nor its alphabet are enumerated. Models, properties, input symbols and observation
 * functions must remain stable during a call. System adapters expose eligible edges through
 * {@link DeterministicTransitionOutputTS}; absent or omitted edges have no transition. Each eligible edge carries
 * one observation, evaluated once when the transition is obtained.
 *
 * @param <I>
 *         system input
 * @param <M>
 *         system model
 * @param <A>
 *         observation
 * @param <R>
 *         finite counterexample
 */
public abstract class AbstractMonitorModelChecker<I, M, A, R> implements ModelChecker<I, M, DFA<?, A>, R> {

    /**
     * Adapts the model to a transition system whose outputs are property observations. Ineligible edges must be
     * omitted before evaluating their observations.
     *
     * @param model
     *         the model to adapt
     * @param inputs
     *         the inputs considered during the search
     *
     * @return the transition system used for product exploration
     */
    protected abstract DeterministicTransitionOutputTS<?, I, ?, A> system(M model, Collection<I> inputs);

    /**
     * Constructs a finite counterexample from a violating input sequence, which may be empty.
     *
     * @param model
     *         the original model
     * @param inputs
     *         the violating input sequence in traversal order
     *
     * @return the counterexample for the given sequence
     */
    protected abstract R witness(M model, List<I> inputs);

    /**
     * Searches the product from an accepting node. Only nodes with a non-null product may be expanded; a null product
     * denotes a violation. Implementations must handle cycles and retain the predecessor chain of the returned node.
     * Concurrent searches must finish all expansion work before returning, including on failure.
     *
     * @param initial
     *         the accepting initial product node
     * @param expand
     *         lazily supplies the successors of an accepting node
     * @param <S>
     *         the system state type
     * @param <P>
     *         the property state type
     *
     * @return a violating node, or {@code null} if no violation is reachable
     */
    protected abstract <S, P> @Nullable Node<S, P, I> search(Node<S, P, I> initial,
                                                             Function<Node<S, P, I>, Iterable<Node<S, P, I>>> expand);

    /**
     * {@inheritDoc}
     * <p>
     * The property must be prefix-closed. A missing property transition rejects the sequence.
     * Model restrictions are checked before searching. If the resulting transition system has no initial state,
     * no counterexample is returned. If it has an initial state but the property's initial state is missing
     * or rejecting, an empty counterexample is returned.
     */
    @Override
    public @Nullable R findCounterExample(M model, Collection<? extends I> inputs, DFA<?, A> property) {
        final List<I> inputSymbols = new ArrayList<>(inputs);
        return check(model, system(model, inputSymbols), inputSymbols, property);
    }

    private <S, T, P> @Nullable R check(M model,
                                       DeterministicTransitionOutputTS<S, I, T, A> system,
                                       List<I> inputs,
                                       DFA<P, A> property) {
        final S systemInitial = system.getInitialState();
        if (systemInitial == null) {
            return null;
        }
        final P propertyInitial = property.getInitialState();
        if (propertyInitial == null || !property.isAccepting(propertyInitial)) {
            return witness(model, Collections.emptyList());
        }
        final Node<S, P, I> violation = search(new Node<>(Pair.of(systemInitial, propertyInitial), null, null), node -> {
            assert node.product != null : "Only accepting product states are expanded";
            return () -> new AbstractSimplifiedIterator<Node<S, P, I>>() {

                private final Iterator<I> iterator = inputs.iterator();

                @Override
                protected boolean calculateNext() {
                    while (iterator.hasNext()) {
                        final I input = iterator.next();
                        final T transition = system.getTransition(node.product.getFirst(), input);
                        if (transition != null) {
                            final P propertySuccessor = property.getSuccessor(node.product.getSecond(),
                                    system.getTransitionOutput(transition));
                            final Pair<S, P> product = propertySuccessor == null || !property.isAccepting(propertySuccessor) ?
                                    null :
                                    Pair.of(system.getSuccessor(transition), propertySuccessor);
                            nextValue = new Node<>(product, node, input);
                            return true;
                        }
                    }
                    return false;
                }
            };
        });
        if (violation == null) {
            return null;
        }
        final List<I> word = new ArrayList<>();
        for (Node<S, P, I> node = violation; node.parent != null; node = node.parent) {
            @SuppressWarnings("nullness") // Every non-root node carries an input of type I.
            final I input = node.input;
            word.add(input);
        }
        Collections.reverse(word);
        return witness(model, word);
    }

    /**
     * A search node with a predecessor for reconstructing a witness. A {@code null} product denotes a violation.
     */
    @SuppressWarnings("PMD.PublicMemberInNonPublicType") // Accessors form the search API for external subclasses.
    protected static final class Node<S, P, I> {

        final @Nullable Pair<S, P> product;
        final @Nullable Node<S, P, I> parent;
        final @Nullable I input;

        Node(@Nullable Pair<S, P> product, @Nullable Node<S, P, I> parent, @Nullable I input) {
            this.product = product;
            this.parent = parent;
            this.input = input;
        }

        public boolean isViolation() {
            return product == null;
        }

        public @Nullable Pair<S, P> getProduct() {
            return product;
        }
    }

    static final class BuilderDefaults {

        private BuilderDefaults() {
            // prevent instantiation
        }

        static <O> Collection<? super O> skipOutputs() {
            return Collections.emptySet();
        }
    }
}
