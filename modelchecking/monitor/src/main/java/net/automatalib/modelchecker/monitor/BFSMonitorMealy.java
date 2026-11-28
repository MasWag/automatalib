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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;

import de.learnlib.tooling.annotation.builder.GenerateBuilder;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.modelchecking.ModelChecker;
import net.automatalib.ts.output.DeterministicTransitionOutputTS;

/**
 * Sequential BFS for Mealy machines.
 *
 * @param <I>
 *         the system input type
 * @param <O>
 *         the system output type
 * @param <A>
 *         the observation type
 */
public class BFSMonitorMealy<I, O, A>
        extends AbstractBFSMonitorModelChecker<I, MealyMachine<?, I, ?, O>, A, MealyMachine<?, I, ?, O>>
        implements ModelChecker.MealyModelChecker<I, O, DFA<?, A>, MealyMachine<?, I, ?, O>> {

    private final BiFunction<? super I, ? super O, ? extends A> observation;
    private Collection<? super O> skipOutputs;

    public BFSMonitorMealy(BiFunction<? super I, ? super O, ? extends A> observation) {
        this.observation = observation;
        this.skipOutputs = Collections.emptySet();
    }

    /**
     * Constructs a monitor checker with the given skipped outputs.
     *
     * @param observation
     *         the function mapping each system edge to a property input
     * @param skipOutputs
     *         the outputs whose transitions are omitted; the collection is copied
     */
    @GenerateBuilder(defaults = BuilderDefaults.class)
    public BFSMonitorMealy(BiFunction<? super I, ? super O, ? extends A> observation,
                           Collection<? super O> skipOutputs) {
        this.observation = observation;
        this.skipOutputs = Collections.unmodifiableSet(new HashSet<>(skipOutputs));
    }

    /**
     * Returns the unmodifiable collection of skipped outputs.
     */
    @Override
    public Collection<? super O> getSkipOutputs() {
        return skipOutputs;
    }

    /**
     * Copies the given collection of outputs whose transitions are omitted.
     */
    @Override
    public void setSkipOutputs(Collection<? super O> skipOutputs) {
        this.skipOutputs = Collections.unmodifiableSet(new HashSet<>(skipOutputs));
    }

    @Override
    protected DeterministicTransitionOutputTS<?, I, ?, A> system(MealyMachine<?, I, ?, O> model, Collection<I> inputs) {
        return MonitorUtil.mealy(model, skipOutputs, observation);
    }

    @Override
    protected MealyMachine<?, I, ?, O> witness(MealyMachine<?, I, ?, O> model, List<I> inputs) {
        return MonitorUtil.mealyWitness(model, inputs);
    }
}
