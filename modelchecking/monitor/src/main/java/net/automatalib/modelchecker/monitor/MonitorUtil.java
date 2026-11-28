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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
import net.automatalib.common.util.collection.IntRange;
import net.automatalib.exception.ModelCheckingException;
import net.automatalib.ts.output.DeterministicTransitionOutputTS;
import net.automatalib.util.automaton.fsa.DFAs;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Shared system adapters and finite witnesses for both search algorithms. Adapters map only eligible edges and cache
 * each observation in its transition. Witnesses retain original inputs and outputs, with terminal-only acceptance
 * (and terminal-only computed Mealy output for LearnLib property oracles).
 */
final class MonitorUtil {

    private MonitorUtil() {
    }

    static <S, I, A> DeterministicTransitionOutputTS<S, I, Pair<S, A>, A> dfa(
            DFA<S, I> model,
            Collection<I> inputs,
            Function<? super I, ? extends A> observation) {
        final S initial = model.getInitialState();
        if (initial == null || !model.isAccepting(initial)) {
            throw new ModelCheckingException("DFA accepts the empty language, the LTS for such a DFA is not defined.");
        }
        if (!DFAs.isPrefixClosed(model, inputs)) {
            throw new ModelCheckingException("DFA is not prefix closed.");
        }
        return new DeterministicTransitionOutputTS<>() {

            @Override
            public S getInitialState() {
                return initial;
            }

            @Override
            public @Nullable Pair<S, A> getTransition(S state, I input) {
                final S next = model.getSuccessor(state, input);
                return next == null || !model.isAccepting(next) ? null : Pair.of(next, observation.apply(input));
            }

            @Override
            public S getSuccessor(Pair<S, A> transition) {
                return transition.getFirst();
            }

            @Override
            public A getTransitionOutput(Pair<S, A> transition) {
                return transition.getSecond();
            }
        };
    }

    static <S, I, T, O, A> DeterministicTransitionOutputTS<S, I, Pair<S, A>, A> mealy(
            MealyMachine<S, I, T, O> model,
            Collection<? super O> skipOutputs,
            BiFunction<? super I, ? super O, ? extends A> observation) {
        return new DeterministicTransitionOutputTS<>() {

            @Override
            public @Nullable S getInitialState() {
                return model.getInitialState();
            }

            @Override
            public @Nullable Pair<S, A> getTransition(S state, I input) {
                final T transition = model.getTransition(state, input);
                if (transition == null) {
                    return null;
                }
                final O output = model.getTransitionOutput(transition);
                return skipOutputs.contains(output) ?
                        null :
                        Pair.of(model.getSuccessor(transition), observation.apply(input, output));
            }

            @Override
            public S getSuccessor(Pair<S, A> transition) {
                return transition.getFirst();
            }

            @Override
            public A getTransitionOutput(Pair<S, A> transition) {
                return transition.getSecond();
            }
        };
    }

    static <I> DFA<Integer, I> dfaWitness(List<I> word) {
        return new DFA<>() {

            @Override
            public Integer getInitialState() {
                return 0;
            }

            @Override
            public Collection<Integer> getStates() {
                return new IntRange(0, word.size() + 1);
            }

            @Override
            public boolean isAccepting(Integer state) {
                return state == word.size();
            }

            @Override
            public @Nullable Integer getTransition(Integer state, I input) {
                return state < word.size() && Objects.equals(word.get(state), input) ? state + 1 : null;
            }
        };
    }

    static <S, I, T, O> MealyMachine<Integer, I, Integer, O> mealyWitness(MealyMachine<S, I, T, O> model,
                                                                       List<I> word) {
        // Executable prefixes may have no computed output in terminal-only witness models.
        final WordBuilder<O> outputBuilder = new WordBuilder<>(word.size());
        S state = Objects.requireNonNull(model.getInitialState());
        for (I input : word) {
            final T transition = Objects.requireNonNull(model.getTransition(state, input));
            outputBuilder.append(model.getTransitionOutput(transition));
            state = model.getSuccessor(transition);
        }
        final Word<O> outputs = outputBuilder.toWord();
        return new MealyMachine<>() {

            @Override
            public Integer getInitialState() {
                return 0;
            }

            @Override
            public Collection<Integer> getStates() {
                return new IntRange(0, word.size() + 1);
            }

            @Override
            public @Nullable Integer getTransition(Integer state, I input) {
                return state < word.size() && Objects.equals(word.get(state), input) ? state + 1 : null;
            }

            @Override
            public Integer getSuccessor(Integer transition) {
                return transition;
            }

            @Override
            public O getTransitionOutput(Integer transition) {
                return outputs.getSymbol(transition - 1);
            }

            @Override
            @SuppressWarnings("nullness") // LearnLib property oracles require null for proper prefixes.
            public Word<O> computeStateOutput(Integer state, Iterable<? extends I> input) {
                final Integer successor = getSuccessor(state, input);
                return successor != null && successor == word.size() ?
                        MealyMachine.super.computeStateOutput(state, input) :
                        null;
            }
        };
    }
}
