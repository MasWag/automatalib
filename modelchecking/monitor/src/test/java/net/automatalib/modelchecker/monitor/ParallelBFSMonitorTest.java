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

import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.word.Word;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ParallelBFSMonitorTest {

    private static final List<Character> INPUTS = MonitorTestUtil.INPUTS;

    @Test
    public void testRejectionPreservesWorkerFailure() {
        final CompactMealy<Character, String> model = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int start = model.addInitialState();
        final int left = model.addState();
        final int right = model.addState();
        model.addTransition(start, 'a', left, "ok");
        model.addTransition(start, 'b', right, "ok");
        model.addTransition(left, 'a', left, "fail");
        final CompactDFA<String> property = new CompactDFA<>(Alphabets.fromArray("ok"));
        final int good = property.addInitialState(true);
        MonitorTestUtil.connect(property, good, "ok", good);
        final AtomicInteger submissions = new AtomicInteger();
        // Complete each submitted task before rejecting the third submission to fix the failure ordering.
        final Executor executor = task -> {
            if (submissions.incrementAndGet() == 3) {
                throw new RejectedExecutionException("Expected rejection");
            }
            task.run();
        };
        final ParallelBFSMonitorMealy<Character, String, String> checker = new ParallelBFSMonitorMealy<>((i, o) -> {
            if (o.equals("fail")) {
                throw new IllegalStateException("Expected worker failure");
            }
            return o;
        }, executor);
        final RejectedExecutionException failure = Assert.expectThrows(RejectedExecutionException.class,
                                                                       () -> checker.findCounterExample(model,
                                                                                                        INPUTS,
                                                                                                        property));
        Assert.assertEquals(failure.getSuppressed().length, 1);
        Assert.assertTrue(failure.getSuppressed()[0] instanceof CompletionException);
        Assert.assertTrue(failure.getSuppressed()[0].getCause() instanceof IllegalStateException);
    }

    @Test
    public void testParallelMealyWitness() {
        final CompactMealy<Character, String> model = createModel();
        final CompactDFA<Boolean> property = createProperty();
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final ParallelBFSMonitorMealy<Character, String, Boolean> checker =
                    new ParallelBFSMonitorMealy<>((i, o) -> {
                        if (o.equals("fail")) {
                            throw new AssertionError("Skipped output observed");
                        }
                        return o.equals("ab");
                    }, pool);
            checker.setSkipOutputs(Set.of("fail"));
            final MealyMachine<?, Character, ?, String> witness =
                    checker.findCounterExample(model, INPUTS, property);
            Assert.assertEquals(witness.computeOutput(Word.fromString("bb")), Word.fromSymbols("aa", "ab"));
            Assert.assertNull(witness.computeOutput(Word.fromString("b")));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testQuiescenceAfterEvaluationFailure() {
        final CompactMealy<Character, String> model = createModel();
        final CompactDFA<Boolean> property = createProperty();
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final CyclicBarrier barrier = new CyclicBarrier(2);
            final AtomicBoolean completed = new AtomicBoolean();
            final ParallelBFSMonitorMealy<Character, String, Boolean> failing =
                    new ParallelBFSMonitorMealy<>((i, o) -> {
                        if (!o.equals("aa")) {
                            try {
                                barrier.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            } catch (BrokenBarrierException | TimeoutException e) {
                                throw new AssertionError(e);
                            }
                            if (o.equals("fail")) {
                                throw new IllegalStateException("Expected evaluation failure");
                            }
                            completed.set(true);
                        }
                        return false;
                    }, pool);
            Assert.expectThrows(CompletionException.class,
                                () -> failing.findCounterExample(model, INPUTS, property));
            Assert.assertTrue(completed.get());
            Assert.assertFalse(pool.isShutdown());
        } finally {
            pool.shutdownNow();
        }
    }

    private static CompactMealy<Character, String> createModel() {
        final CompactMealy<Character, String> model = new CompactMealy<>(Alphabets.fromCollection(INPUTS));
        final int start = model.addInitialState();
        final int left = model.addState();
        final int right = model.addState();
        model.addTransition(start, 'a', left, "aa");
        model.addTransition(start, 'b', right, "aa");
        model.addTransition(left, 'a', left, "fail");
        model.addTransition(right, 'b', right, "ab");
        return model;
    }

    private static CompactDFA<Boolean> createProperty() {
        final CompactDFA<Boolean> property = new CompactDFA<>(Alphabets.fromArray(false, true));
        final int good = property.addInitialState(true);
        MonitorTestUtil.connect(property, good, false, good);
        return property;
    }
}
