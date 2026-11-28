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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.common.util.Pair;
import net.automatalib.ts.output.DeterministicTransitionOutputTS;
import net.automatalib.word.Word;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MonitorUtilTest {

    private static final List<Character> INPUTS = MonitorTestUtil.INPUTS;

    @Test
    public void testNativeTransitionType() {
        final CompactMealy<Character, Character> model = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int initial = model.addInitialState();
        model.addTransition(initial, 'a', initial, 'b');
        final CompactDFA<Character> property = new CompactDFA<>(Alphabets.fromCollection(INPUTS));
        property.addInitialState(true);
        // Use CompactMealy's native transition type directly, without the monitor's Pair adapter.
        final AbstractBFSMonitorModelChecker<Character, CompactMealy<Character, Character>, Character, Word<Character>>
                checker = new AbstractBFSMonitorModelChecker<>() {

                    @Override
                    protected DeterministicTransitionOutputTS<?, Character, ?, Character> system(
                            CompactMealy<Character, Character> system, Collection<Character> inputs) {
                        return system;
                    }

                    @Override
                    protected Word<Character> witness(CompactMealy<Character, Character> system, List<Character> inputs) {
                        return Word.fromList(inputs);
                    }
                };
        Assert.assertEquals(checker.findCounterExample(model, INPUTS, property), Word.fromLetter('a'));
    }

    @Test
    public void testDFAAdapter() {
        final AtomicInteger mappings = new AtomicInteger();
        final CompactDFA<Character> dfa = MonitorTestUtil.system();
        final int rejecting = dfa.addState(false);
        MonitorTestUtil.connect(dfa, dfa.getInitialState(), 'b', rejecting);
        final DeterministicTransitionOutputTS<Integer, Character, Pair<Integer, String>, String> view =
                MonitorUtil.dfa(dfa, INPUTS, input -> {
                    mappings.incrementAndGet();
                    return String.valueOf(input);
                });
        final Integer initial = view.getInitialState();
        final Pair<Integer, String> transition = view.getTransition(initial, 'a');
        Assert.assertEquals(view.getSuccessor(transition), initial);
        // Reading the cached observation must not evaluate the mapping again.
        Assert.assertEquals(view.getTransitionOutput(transition), "a");
        Assert.assertEquals(view.getTransitionOutput(transition), "a");
        Assert.assertEquals(mappings.get(), 1);
        final List<String> trace = new ArrayList<>();
        Assert.assertTrue(view.trace(initial, Word.fromString("aa"), trace));
        Assert.assertEquals(trace, List.of("a", "a"));
        // One evaluation for the initial transition lookup and two for the trace "aa".
        Assert.assertEquals(mappings.get(), 3);
        Assert.assertFalse(view.trace(initial, Word.fromString("b"), trace));
        Assert.assertNull(view.getOutput(initial, 'b'));
        // Edges to rejecting states are excluded before observation evaluation, so the count stays unchanged.
        Assert.assertEquals(mappings.get(), 3);
    }

    @Test
    public void testMealyAdapter() {
        final AtomicInteger mappings = new AtomicInteger();
        final CompactMealy<Character, String> mealy = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int start = mealy.addInitialState();
        mealy.addTransition(start, 'a', start, "original");
        mealy.addTransition(start, 'b', start, "skip");
        final DeterministicTransitionOutputTS<Integer, Character, Pair<Integer, String>, String> mapped =
                MonitorUtil.mealy(mealy, Set.of("skip"), (input, output) -> {
                    mappings.incrementAndGet();
                    Assert.assertEquals(output, "original");
                    return input + ":" + output;
                });
        final List<String> trace = new ArrayList<>();
        Assert.assertFalse(mapped.trace(mapped.getInitialState(), Word.fromString("ab"), trace));
        Assert.assertEquals(trace, List.of("a:original"));
        Assert.assertEquals(mappings.get(), 1);
        Assert.assertEquals(mapped.getOutput(start, 'a'), "a:original");
        Assert.assertEquals(mappings.get(), 2);
        mealy.removeAllTransitions(start, 'a');
        Assert.assertNull(mapped.getTransition(start, 'a'));
        Assert.assertEquals(mappings.get(), 2);
    }
}
