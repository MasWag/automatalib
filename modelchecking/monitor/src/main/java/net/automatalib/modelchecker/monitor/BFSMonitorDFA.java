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
import java.util.function.Function;

import de.learnlib.tooling.annotation.builder.GenerateBuilder;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.modelchecking.ModelChecker;
import net.automatalib.ts.output.DeterministicTransitionOutputTS;

/**
 * Sequential BFS for nonempty prefix-closed system DFAs.
 *
 * @param <I>
 *         the system input type
 * @param <A>
 *         the observation type
 */
public class BFSMonitorDFA<I, A> extends AbstractBFSMonitorModelChecker<I, DFA<?, I>, A, DFA<?, I>>
        implements ModelChecker.DFAModelChecker<I, DFA<?, A>, DFA<?, I>> {

    private final Function<? super I, ? extends A> observation;

    /**
     * Constructs a monitor checker.
     *
     * @param observation
     *         the function mapping each system edge to a property input
     */
    @GenerateBuilder
    public BFSMonitorDFA(Function<? super I, ? extends A> observation) {
        this.observation = observation;
    }

    @Override
    protected DeterministicTransitionOutputTS<?, I, ?, A> system(DFA<?, I> model, Collection<I> inputs) {
        return MonitorUtil.dfa(model, inputs, observation);
    }

    @Override
    protected DFA<?, I> witness(DFA<?, I> model, List<I> inputs) {
        return MonitorUtil.dfaWitness(inputs);
    }
}
