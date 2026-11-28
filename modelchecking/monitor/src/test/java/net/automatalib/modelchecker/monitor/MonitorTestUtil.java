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
import java.util.function.Predicate;

import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.graph.TransitionEdge;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.graph.Graph;
import net.automatalib.util.graph.Path;
import net.automatalib.util.graph.ShortestPaths;
import net.automatalib.word.Word;
import org.testng.Assert;

final class MonitorTestUtil {

    static final List<Character> INPUTS = List.of('a', 'b');

    private MonitorTestUtil() {}

    static CompactDFA<Character> system() {
        final CompactDFA<Character> result = new CompactDFA<>(Alphabets.fromCollection(INPUTS));
        final int state = result.addInitialState(true);
        connect(result, state, 'a', state);
        connect(result, state, 'b', state);
        return result;
    }

    static <I> void connect(CompactDFA<I> dfa, int source, I input, int target) {
        dfa.setTransition(source, input, Integer.valueOf(target));
    }

    static <S, I, T, O> Word<I> witnessWord(MealyMachine<S, I, T, O> witness, Collection<? extends I> inputs) {
        final Graph<S, TransitionEdge<I, T>> graph = witness.transitionGraphView(inputs);
        final Path<S, TransitionEdge<I, T>> path =
                ShortestPaths.shortestPath(graph,
                                          witness.getInitialState(),
                                          witness.size() - 1,
                                          (Predicate<S>) state -> graph.getOutgoingEdges(state).isEmpty());
        Assert.assertNotNull(path);
        return path.stream().map(TransitionEdge::input).collect(Word.collector());
    }
}
