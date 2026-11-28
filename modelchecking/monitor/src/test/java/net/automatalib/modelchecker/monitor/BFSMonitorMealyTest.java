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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.modelchecking.ModelChecker;
import net.automatalib.util.automaton.random.RandomAutomata;
import net.automatalib.word.Word;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BFSMonitorMealyTest {

    private static final List<Character> INPUTS = MonitorTestUtil.INPUTS;

    // Semantic cases use a direct executor; concurrency is covered with real pools separately.
    @DataProvider
    public static Object[][] checkers() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "checkers")
    public void testMealyBuilderSkipOutputs(boolean parallel) {
        final CompactMealy<Character, String> model = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int initial = model.addInitialState();
        model.addTransition(initial, 'a', initial, "skip");
        final CompactDFA<String> property = new CompactDFA<>(Alphabets.fromArray("skip"));
        property.addInitialState(true);
        final ModelChecker.MealyModelChecker<Character, String, DFA<?, String>, MealyMachine<?, Character, ?, String>>
                defaultChecker;
        final ModelChecker.MealyModelChecker<Character, String, DFA<?, String>, MealyMachine<?, Character, ?, String>>
                skippingChecker;
        if (parallel) {
            final ParallelBFSMonitorMealyBuilder<Character, String, String> builder =
                    new ParallelBFSMonitorMealyBuilder<Character, String, String>().withObservation((i, o) -> o)
                                                                                   .withExecutor(Runnable::run);
            defaultChecker = builder.create();
            skippingChecker = builder.withSkipOutputs(Set.of("skip")).create();
        } else {
            final BFSMonitorMealyBuilder<Character, String, String> builder =
                    new BFSMonitorMealyBuilder<Character, String, String>().withObservation((i, o) -> o);
            defaultChecker = builder.create();
            skippingChecker = builder.withSkipOutputs(Set.of("skip")).create();
        }
        Assert.assertTrue(defaultChecker.getSkipOutputs().isEmpty());
        Assert.assertNotNull(defaultChecker.findCounterExample(model, INPUTS, property));
        Assert.assertNull(skippingChecker.findCounterExample(model, INPUTS, property));
    }

    @Test(dataProvider = "checkers")
    public void testNullOutputs(boolean parallel) {
        final CompactMealy<Character, String> model = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int initial = model.addInitialState();
        model.addTransition(initial, 'a', initial, null);
        final CompactDFA<Boolean> property = new CompactDFA<>(Alphabets.fromArray(false, true));
        property.addInitialState(true);
        final ModelChecker.MealyModelChecker<Character, String, DFA<?, Boolean>, MealyMachine<?, Character, ?, String>>
                checker = parallel ?
                        new ParallelBFSMonitorMealyBuilder<Character, String, Boolean>()
                                .withObservation((i, o) -> o == null).withExecutor(Runnable::run).create() :
                        new BFSMonitorMealyBuilder<Character, String, Boolean>()
                                .withObservation((i, o) -> o == null).create();
        Assert.assertNotNull(checker.findCounterExample(model, INPUTS, property));
        checker.setSkipOutputs(Collections.singleton(null));
        Assert.assertNull(checker.findCounterExample(model, INPUTS, property));
    }

    @Test(dataProvider = "checkers")
    public void testOriginalOutputsAndCharacterLabels(boolean parallel) {
        final CompactMealy<Character, String> model = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int s0 = model.addInitialState();
        final int s1 = model.addState();
        model.addTransition(s0, 'a', s1, "aa");
        model.addTransition(s1, 'b', s1, "ab");
        // Label-to-AP compilation belongs to the caller. Character projections use String.valueOf.
        final Map<String, Function<? super String, ?>> labels = Map.of("last", o -> o.charAt(1));
        final CompactDFA<Boolean> property = new CompactDFA<>(Alphabets.fromArray(false, true));
        final int good = property.addInitialState(true);
        MonitorTestUtil.connect(property, good, false, good);
        final ModelChecker.MealyModelChecker<Character, String, DFA<?, Boolean>, MealyMachine<?, Character, ?, String>>
                checker = checker(parallel, (i, o) -> String.valueOf(labels.get("last").apply(o)).equals("b"));
        final MealyMachine<?, Character, ?, String> witness =
                checker.findCounterExample(model, INPUTS, property);
        Assert.assertEquals(witness.computeOutput(Word.fromString("ab")), Word.fromSymbols("aa", "ab"));
        Assert.assertNull(witness.computeOutput(Word.epsilon()));
        Assert.assertNull(witness.computeOutput(Word.fromString("a")));
        Assert.assertNull(witness.computeOutput(Word.fromString("aba")));
        checker.setSkipOutputs(Set.of("ab"));
        Assert.assertNull(checker.findCounterExample(model, INPUTS, property));
        checker.setSkipOutputs(Set.of("aa"));
        final ModelChecker.MealyModelChecker<Character, String, DFA<?, Boolean>, MealyMachine<?, Character, ?, String>>
                skipped = checker(parallel, (i, o) -> {
                    throw new AssertionError("Skipped output observed");
                });
        skipped.setSkipOutputs(Set.of("aa"));
        Assert.assertNull(skipped.findCounterExample(model, INPUTS, property));
        final CompactDFA<Boolean> absent = new CompactDFA<>(Alphabets.fromArray(false, true));
        Assert.assertEquals(checker.findCounterExample(model, INPUTS, absent).computeOutput(Word.epsilon()),
                            Word.epsilon());
        final CompactMealy<Character, String> empty = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        Assert.assertNull(checker.findCounterExample(empty, INPUTS, absent));
    }

    @Test(dataProvider = "checkers")
    public void testWitnessOfTerminalOnlyWitness(boolean parallel) {
        final CompactMealy<Character, String> model = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int start = model.addInitialState();
        final int middle = model.addState();
        final int end = model.addState();
        model.addTransition(start, 'a', middle, "x");
        model.addTransition(middle, 'b', end, "y");
        final CompactDFA<Boolean> property = new CompactDFA<>(Alphabets.fromArray(false, true));
        final int good = property.addInitialState(true);
        MonitorTestUtil.connect(property, good, false, good);
        final MealyMachine<?, Character, ?, String> first =
                checker(parallel, (i, o) -> o.equals("y")).findCounterExample(model, INPUTS, property);
        Assert.assertNotNull(first);
        Assert.assertEquals(first.computeOutput(Word.fromString("ab")), Word.fromSymbols("x", "y"));
        Assert.assertNull(first.computeOutput(Word.fromString("a")));
        final MealyMachine<?, Character, ?, String> second =
                checker(parallel, (i, o) -> o.equals("x")).findCounterExample(first, INPUTS, property);
        Assert.assertNotNull(second);
        Assert.assertEquals(second.computeOutput(Word.fromString("a")), Word.fromSymbols("x"));
        Assert.assertNull(second.computeOutput(Word.epsilon()));
        Assert.assertNull(second.computeOutput(Word.fromString("ab")));
    }

    @Test
    public void testMealyDifferential() {
        final ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            final Random random = new Random(91917);
            final Alphabet<Character> alphabet = Alphabets.fromCollection(INPUTS);
            for (int trial = 0; trial < 60; trial++) {
                final CompactMealy<Character, Character> model =
                        RandomAutomata.randomMealy(random, 5, alphabet, INPUTS, false);
                final CompactDFA<Character> property = RandomAutomata.randomDFA(random, 5, alphabet, false);
                property.setAccepting(property.getInitialState(), trial % 7 != 0);
                for (int state : model.getStates()) {
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
                final BFSMonitorMealy<Character, Character, Character> sequential = new BFSMonitorMealy<>((i, o) -> o);
                final ParallelBFSMonitorMealy<Character, Character, Character> parallel =
                        new ParallelBFSMonitorMealy<>((i, o) -> o, pool);
                if (trial % 2 == 0) {
                    sequential.setSkipOutputs(Set.of('b'));
                    parallel.setSkipOutputs(Set.of('b'));
                }
                final MealyMachine<?, Character, ?, Character> sequentialWitness =
                        sequential.findCounterExample(model, INPUTS, property);
                final MealyMachine<?, Character, ?, Character> parallelWitness =
                        parallel.findCounterExample(model, INPUTS, property);
                Assert.assertEquals(parallelWitness == null, sequentialWitness == null);
                if (sequentialWitness != null) {
                    Assert.assertEquals(sequentialWitness.size(), parallelWitness.size());
                    final Word<Character> word = MonitorTestUtil.witnessWord(sequentialWitness, INPUTS);
                    final Word<Character> parallelWord = MonitorTestUtil.witnessWord(parallelWitness, INPUTS);
                    Assert.assertNotNull(model.getState(parallelWord));
                    Assert.assertEquals(parallelWitness.computeOutput(parallelWord), model.computeOutput(parallelWord));
                    Assert.assertFalse(property.accepts(model.computeOutput(parallelWord)));
                    Assert.assertNotNull(model.getState(word));
                    Assert.assertEquals(sequentialWitness.computeOutput(word), model.computeOutput(word));
                    Assert.assertFalse(property.accepts(model.computeOutput(word)));
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static ModelChecker.MealyModelChecker<Character, String, DFA<?, Boolean>, MealyMachine<?, Character, ?, String>> checker(
            boolean parallel, BiFunction<Character, String, Boolean> observation) {
        return parallel ? new ParallelBFSMonitorMealy<>(observation, Runnable::run) : new BFSMonitorMealy<>(observation);
    }
}
