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
package net.automatalib.addlib;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import info.scce.addlib.dd.bdd.BDD;
import info.scce.addlib.dd.xdd.XDD;
import info.scce.addlib.dd.xdd.XDDManager;
import info.scce.addlib.traverser.PreorderTraverser;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.MapAlphabet;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Fluent, single-use builder for an {@link ADDDFA}. Use try-with-resources and register nonnull states before
 * referring to them. State and proposition equality and hash codes must remain stable.
 * <p>
 * Builder operations are serialized. A backend supporting independent managers (such as CUDD) is required.
 * All operations except {@link #close()} throw {@link IllegalStateException} after creation or closing.
 *
 * @param <S>
 *         state type
 * @param <P>
 *         proposition type
 */
@SuppressWarnings("PMD.AvoidSynchronizedStatement") // Coordinate copying with the caller's BDD manager.
public final class ADDDFABuilder<S, P> implements AutoCloseable {

    private final Alphabet<P> propositions;
    private final XDDManager<Optional<S>> manager;
    private final Map<S, XDD<Optional<S>>> transitions = new HashMap<>();
    private final Set<S> accepting = new HashSet<>();
    private @Nullable S initial;
    private boolean consumed;

    /**
     * Creates a builder with its own native ADD manager.
     *
     * @param propositions
     *         duplicate-free proposition alphabet in guard variable order; copied
     * @throws IllegalArgumentException
     *         if the alphabet contains duplicate propositions
     */
    public ADDDFABuilder(Alphabet<P> propositions) {
        this.propositions = new MapAlphabet<>(propositions);
        for (int i = 0; i < this.propositions.size(); i++) {
            if (this.propositions.getSymbolIndex(this.propositions.getSymbol(i)) != i) {
                throw new IllegalArgumentException("Duplicate proposition");
            }
        }
        this.manager = new XDDManager<>();
    }

    /**
     * Registers a state, or updates its acceptance if already registered.
     *
     * @param state
     *         nonnull state
     * @param isAccepting
     *         whether the state accepts
     * @return this builder
     */
    public ADDDFABuilder<S, P> withState(S state, boolean isAccepting) {
        synchronized (manager) {
            ensureOpen();
            Objects.requireNonNull(state);
            if (!transitions.containsKey(state)) {
                transitions.put(state, manager.constant(Optional.empty()));
            }
            if (isAccepting) {
                accepting.add(state);
            } else {
                accepting.remove(state);
            }
            return this;
        }
    }

    /**
     * Sets the initial state. By default, no initial state is set.
     *
     * @param state
     *         registered state, or {@code null} to clear the initial state
     * @return this builder
     * @throws IllegalArgumentException
     *         if the state is not registered
     */
    public ADDDFABuilder<S, P> withInitial(@Nullable S state) {
        synchronized (manager) {
            ensureOpen();
            if (state != null) {
                getRegisteredTransitions(state);
            }
            initial = state;
            return this;
        }
    }

    /**
     * Adds a guarded transition. False guards have no effect. Overlap is rejected even for equal destinations.
     * Rejected transitions leave the builder unchanged and usable.
     * <p>
     * Guard variable indices must match the supplied proposition order and lie within the alphabet; unused manager
     * variables are allowed. BDDs carry no proposition alphabet, so callers must ensure this correspondence.
     * <p>
     * The guard is copied immediately and remains caller-owned; after this call, callers may release it and quit its
     * manager. During copying, the guard must be live and all other access to its manager must synchronize on that
     * manager. This method acquires the guard manager before the private builder manager; callers may already hold
     * the guard manager lock. Different transitions may use different BDD managers with the same variable order.
     * Callers holding multiple guard manager locks must impose a consistent order on those locks.
     *
     * @param source
     *         registered source state
     * @param guard
     *         live, caller-owned Boolean decision diagram
     * @param target
     *         registered destination state
     * @return this builder
     * @throws IllegalArgumentException
     *         if a state is unregistered, a guard variable is outside the alphabet, or outgoing guards overlap
     */
    public ADDDFABuilder<S, P> withTransition(S source, BDD guard, S target) {
        synchronized (guard.ddManager()) {
            synchronized (manager) {
                ensureOpen();
                final XDD<Optional<S>> previous = getRegisteredTransitions(source);
                getRegisteredTransitions(target);
                final XDD<Optional<S>> copied = copyGuard(guard, target);
                try {
                    // Ensure the disjointness for determinism
                    final XDD<Optional<S>> intersection = intersectDomains(previous, copied);
                    try {
                        if (hasNonEmptyLeaf(intersection)) {
                            throw new IllegalArgumentException("Overlapping outgoing guards");
                        }
                    } finally {
                        intersection.recursiveDeref();
                    }
                    final XDD<Optional<S>> combined = mergeDisjointTransitions(previous, copied);
                    transitions.put(source, combined);
                    previous.recursiveDeref();
                } finally {
                    copied.recursiveDeref();
                }
                return this;
            }
        }
    }

    private XDD<Optional<S>> intersectDomains(XDD<Optional<S>> transitions, XDD<Optional<S>> domain) {
        // Retains the left-hand successors on the common domain.
        return transitions.apply((successor, domainSuccessor) -> domainSuccessor.isPresent() ? successor : Optional.empty(),
                                 domain);
    }

    private XDD<Optional<S>> mergeDisjointTransitions(XDD<Optional<S>> left, XDD<Optional<S>> right) {
        return left.apply((existing, added) -> existing.isPresent() ? existing : added, right);
    }

    private XDD<Optional<S>> copyGuard(BDD guard, S target) {
        // Both managers are held by withTransition, in guard-before-builder order.
        final Map<GuardKey, XDD<Optional<S>>> cache = new HashMap<>();
        final BDD one = guard.ddManager().readOne();
        try {
            final XDD<Optional<S>> result = copyGuard(guard, false, one, target, cache);
            result.ref();
            return result;
        } finally {
            one.recursiveDeref();
            for (XDD<Optional<S>> node : cache.values()) {
                node.recursiveDeref();
            }
        }
    }

    private XDD<Optional<S>> copyGuard(BDD guard,
                                       boolean negate,
                                       BDD one,
                                       S target,
                                       Map<GuardKey, XDD<Optional<S>>> cache) {
        // A complemented edge toggles the interpretation of both children, not their borrowed references.
        final GuardKey key = new GuardKey(guard.ptr(), negate);
        XDD<Optional<S>> result = cache.get(key);
        if (result == null) {
            if (guard.isConstant()) {
                final boolean value = guard.equals(one) ^ negate;
                result = manager.constant(value ? Optional.of(target) : Optional.empty());
            } else {
                final int index = guard.readIndex();
                if (index < 0 || index >= propositions.size()) {
                    throw new IllegalArgumentException("Guard variable outside proposition alphabet");
                }
                final boolean complement = negate != guard.isComplement();
                final XDD<Optional<S>> thenNode = copyGuard(guard.t(), complement, one, target, cache);
                final XDD<Optional<S>> elseNode = copyGuard(guard.e(), complement, one, target, cache);
                result = manager.ithVar(index, thenNode, elseNode);
            }
            cache.put(key, result);
        }
        return result;
    }

    private boolean hasNonEmptyLeaf(XDD<Optional<S>> root) {
        for (XDD<Optional<S>> node : new PreorderTraverser<>(root)) {
            if (node.isConstant() && node.v().isPresent()) {
                return true;
            }
        }
        return false;
    }

    private XDD<Optional<S>> getRegisteredTransitions(S state) {
        final XDD<Optional<S>> root = transitions.get(Objects.requireNonNull(state));
        if (root == null) {
            throw new IllegalArgumentException("Unregistered state: " + state);
        }
        return root;
    }

    private void ensureOpen() {
        if (consumed) {
            throw new IllegalStateException("ADDDFABuilder is closed or consumed");
        }
    }

    /**
     * Creates the automaton and transfers ownership of the builder's native manager to it. This consumes the builder;
     * subsequent calls other than {@link #close()} throw {@link IllegalStateException}.
     *
     * @return the automaton, which must be closed by its caller
     */
    public ADDDFA<S, P> create() {
        synchronized (manager) {
            ensureOpen();
            final ADDDFA<S, P> result = new ADDDFA<>(manager, propositions, transitions, accepting, initial);
            consumed = true;
            transitions.clear();
            return result;
        }
    }

    /**
     * Releases the native resources of an unfinished builder. Does nothing after creation or closing; an automaton
     * returned by {@link #create()} must be closed separately.
     */
    @Override
    public void close() {
        synchronized (manager) {
            if (!consumed) {
                consumed = true;
                try {
                    for (XDD<Optional<S>> root : transitions.values()) {
                        root.recursiveDeref();
                    }
                } finally {
                    transitions.clear();
                    manager.quit();
                }
            }
        }
    }

    private record GuardKey(long pointer, boolean negate) {}
}
