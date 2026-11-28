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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import info.scce.addlib.dd.xdd.XDD;
import info.scce.addlib.dd.xdd.XDDManager;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.NamedBitSet;
import net.automatalib.automaton.fsa.DFA;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A DFA with ADD transition functions. Valuations are matched by proposition name, independently of alphabet order;
 * missing transitions return {@code null}. States and propositions must have stable equality and hash codes.
 * <p>
 * Create instances with {@link ADDDFABuilder}. Each instance owns its native manager and supports concurrent reads.
 * Close it to release all native resources. Closing is idempotent; subsequent queries are rejected.
 *
 * @param <S>
 *         state type
 * @param <P>
 *         proposition type
 */
@SuppressWarnings("PMD.AvoidSynchronizedStatement") // Serialize native access and closing on the owned manager.
public final class ADDDFA<S, P> implements DFA<S, NamedBitSet<P>>, AutoCloseable {

    private final XDDManager<Optional<S>> manager;
    private final Alphabet<P> propositions;
    private final Map<S, XDD<Optional<S>>> transitions;
    private final Set<S> accepting;
    private final @Nullable S initial;
    private boolean closed;

    ADDDFA(XDDManager<Optional<S>> manager,
           Alphabet<P> propositions,
           Map<S, XDD<Optional<S>>> transitions,
           Set<S> accepting,
           @Nullable S initial) {
        this.manager = manager;
        this.propositions = propositions;
        this.transitions = Map.copyOf(transitions);
        this.accepting = Set.copyOf(accepting);
        this.initial = initial;
    }

    @Override
    public Collection<S> getStates() {
        synchronized (manager) {
            ensureOpen();
            return transitions.keySet();
        }
    }

    @Override
    public @Nullable S getInitialState() {
        synchronized (manager) {
            ensureOpen();
            return initial;
        }
    }

    @Override
    public boolean isAccepting(S state) {
        synchronized (manager) {
            ensureOpen();
            return accepting.contains(state);
        }
    }

    @Override
    public @Nullable S getTransition(S state, NamedBitSet<P> input) {
        synchronized (manager) {
            ensureOpen();
            XDD<Optional<S>> node = transitions.get(state);
            if (node == null) {
                return null;
            }
            while (!node.isConstant()) {
                node = input.contains(propositions.getSymbol(node.readIndex())) ? node.t() : node.e();
            }
            return node.v().orElse(null);
        }
    }

    @Override
    public S getSuccessor(S transition) {
        synchronized (manager) {
            ensureOpen();
            return transition;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ADDDFA is closed");
        }
    }

    @Override
    public void close() {
        synchronized (manager) {
            if (!closed) {
                closed = true;
                try {
                    for (XDD<Optional<S>> root : transitions.values()) {
                        root.recursiveDeref();
                    }
                } finally {
                    manager.quit();
                }
            }
        }
    }
}
