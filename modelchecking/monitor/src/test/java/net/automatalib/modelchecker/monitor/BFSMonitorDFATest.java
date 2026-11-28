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

import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.alphabet.impl.NamedBitSet;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.common.util.collection.IterableUtil;
import net.automatalib.exception.ModelCheckingException;
import net.automatalib.modelchecking.ModelChecker;
import net.automatalib.util.automaton.fsa.DFAs;
import net.automatalib.util.automaton.random.RandomAutomata;
import net.automatalib.word.Word;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BFSMonitorDFATest {

    private static final List<Character> INPUTS = MonitorTestUtil.INPUTS;

    // Semantic cases use a direct executor; concurrency is covered with real pools separately.
    @DataProvider
    public static Object[][] checkers() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "checkers")
    public void testEmptyAndUndefined(boolean parallel) {
        final ModelChecker.DFAModelChecker<Character, DFA<?, Character>, DFA<?, Character>> checker =
                checker(parallel, Function.identity());
        final CompactDFA<Character> property = new CompactDFA<>(Alphabets.fromCollection(INPUTS));
        Assert.assertTrue(checker.findCounterExample(MonitorTestUtil.system(), INPUTS, property)
                                 .accepts(Word.epsilon()));
        final int initial = property.addInitialState(false);
        Assert.assertTrue(checker.findCounterExample(MonitorTestUtil.system(), INPUTS, property)
                                 .accepts(Word.epsilon()));
        property.setAccepting(initial, true);
        final DFA<?, Character> witness = checker.findCounterExample(MonitorTestUtil.system(), INPUTS, property);
        Assert.assertFalse(witness.accepts(Word.epsilon()));
        Assert.assertTrue(witness.accepts(Word.fromLetter('a')) || witness.accepts(Word.fromLetter('b')));
    }

    @Test(dataProvider = "checkers")
    public void testPartialAndRejectingSystemEdges(boolean parallel) {
        final CompactDFA<Character> model = MonitorTestUtil.system();
        final int rejecting = model.addState(false);
        MonitorTestUtil.connect(model, model.getInitialState(), 'a', rejecting);
        model.removeAllTransitions(model.getInitialState(), 'b');
        final CompactDFA<Character> property = new CompactDFA<>(Alphabets.fromCollection(INPUTS));
        property.addInitialState(true);
        Assert.assertNull(BFSMonitorDFATest.<Character>checker(parallel, i -> {
            throw new AssertionError("Omitted edge observed");
        }).findCounterExample(model, INPUTS, property));
        MonitorTestUtil.connect(model, rejecting, 'b', model.getInitialState());
        final ModelChecker.DFAModelChecker<Character, DFA<?, Character>, DFA<?, Character>> checker =
                checker(parallel, Function.identity());
        Assert.expectThrows(ModelCheckingException.class, () -> checker.findCounterExample(model, INPUTS, property));
        final CompactDFA<Character> empty = new CompactDFA<>(Alphabets.fromCollection(INPUTS));
        Assert.expectThrows(ModelCheckingException.class, () -> checker.findCounterExample(empty, INPUTS, property));
    }

    @Test
    public void testDifferentialAndShortest() {
        final ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            final Random random = new Random(728191);
            final Alphabet<Character> alphabet = Alphabets.fromCollection(INPUTS);
            for (int trial = 0; trial < 100; trial++) {
                final CompactDFA<Character> model = RandomAutomata.randomDFA(random, 8, alphabet, false);
                final CompactDFA<Character> property = RandomAutomata.randomDFA(random, 8, alphabet, false);
                property.setAccepting(property.getInitialState(), true);
                for (int state : model.getStates()) {
                    model.setAccepting(state, true);
                    for (char input : INPUTS) {
                        if (random.nextBoolean()) {
                            model.removeAllTransitions(state, input);
                        }
                        // Rejecting states cannot lead back to acceptance in a prefix-closed property.
                        if (!property.isAccepting(state) || random.nextBoolean()) {
                            property.removeAllTransitions(state, input);
                        }
                    }
                }
                final DFA<?, Character> sequentialWitness =
                        new BFSMonitorDFA<Character, Character>(Function.identity()).findCounterExample(model,
                                                                                                        INPUTS,
                                                                                                        property);
                final DFA<?, Character> parallelWitness =
                        new ParallelBFSMonitorDFA<Character, Character>(Function.identity(), pool).findCounterExample(
                                model,
                                INPUTS,
                                property);
                // Complement completes undefined property edges into accepting violations.
                final CompactDFA<Character> violations = DFAs.and(model, DFAs.complement(property, alphabet), alphabet);
                Assert.assertEquals(sequentialWitness == null, DFAs.acceptsEmptyLanguage(violations));
                Assert.assertEquals(parallelWitness == null, sequentialWitness == null);
                if (sequentialWitness != null) {
                    boolean sequentialAccepted = false;
                    boolean parallelAccepted = false;
                    final int length = sequentialWitness.size() - 1;
                    Assert.assertEquals(parallelWitness.size(), sequentialWitness.size());
                    // Independent exhaustive oracle up to the shortest witness length.
                    for (int n = 0; n <= length; n++) {
                        for (List<Character> tuple : IterableUtil.allTuples(INPUTS, n, n)) {
                            final Word<Character> word = Word.fromList(tuple);
                            if (n < length) {
                                Assert.assertFalse(model.accepts(word) && !property.accepts(word));
                            }
                            sequentialAccepted |= sequentialWitness.accepts(word);
                            parallelAccepted |= parallelWitness.accepts(word);
                            if (sequentialWitness.accepts(word) || parallelWitness.accepts(word)) {
                                Assert.assertTrue(model.accepts(word));
                                Assert.assertFalse(property.accepts(word));
                            }
                        }
                    }
                    Assert.assertTrue(sequentialAccepted);
                    Assert.assertTrue(parallelAccepted);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test(dataProvider = "checkers")
    public void testStopsAtViolatingEdge(boolean parallel) {
        final CompactDFA<Character> property = new CompactDFA<>(Alphabets.fromCollection(INPUTS));
        property.addInitialState(true);
        final Function<Character, Character> observation = input -> {
            if (input == 'b') {
                throw new AssertionError("Evaluated an edge after a known violation");
            }
            return input;
        };
        Assert.assertTrue(checker(parallel, observation).findCounterExample(MonitorTestUtil.system(), INPUTS, property)
                                                       .accepts(Word.fromLetter('a')));
    }

    @Test(dataProvider = "checkers")
    public void testLargePropositionDomain(boolean parallel) {
        final Alphabet<Integer> propositions = Alphabets.integers(0, 255);
        final BitSet bits = new BitSet();
        bits.set(255);
        final NamedBitSet<Integer> valuation = new NamedBitSet<>(propositions, bits);
        final DFA<Integer, NamedBitSet<Integer>> property = new DFA<>() {

            @Override
            public Collection<Integer> getStates() {
                throw new AssertionError("Property states must not be enumerated");
            }

            @Override
            public Integer getInitialState() {
                return 0;
            }

            @Override
            public boolean isAccepting(Integer state) {
                return state == 0;
            }

            @Override
            public Integer getTransition(Integer state, NamedBitSet<Integer> input) {
                return input.contains(255) ? 1 : 0;
            }
        };
        // A one-transition counterexample has two states: the initial and terminal states.
        Assert.assertEquals(checker(parallel, i -> valuation).findCounterExample(MonitorTestUtil.system(),
                                                                               INPUTS,
                                                                               property).size(), 2);
    }

    private static <A> ModelChecker.DFAModelChecker<Character, DFA<?, A>, DFA<?, Character>> checker(
            boolean parallel, Function<Character, A> observation) {
        return parallel ? new ParallelBFSMonitorDFABuilder<Character, A>().withObservation(observation)
                                                                         .withExecutor(Runnable::run)
                                                                         .create() :
                new BFSMonitorDFABuilder<Character, A>().withObservation(observation).create();
    }
}
