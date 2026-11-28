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

import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import info.scce.addlib.dd.bdd.BDD;
import info.scce.addlib.dd.bdd.BDDManager;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.alphabet.impl.NamedBitSet;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ADDDFATest {

    private enum State { START, ACCEPT, DEAD }

    @Test
    public void testConcurrentEvaluationAndLifetime() {
        final BDDManager guards = new BDDManager();
        final ADDDFA<State, Integer> dfa;
        try (ADDDFABuilder<State, Integer> builder = new ADDDFABuilder<>(Alphabets.integers(0, 255))) {
            final BDD lastProposition = guards.ithVar(255);
            final BDD notLastProposition = lastProposition.not();
            builder.withState(State.START, false).withState(State.ACCEPT, true).withState(State.DEAD, false)
                   .withInitial(State.START)
                   .withTransition(State.START, lastProposition, State.ACCEPT)
                   .withTransition(State.START, notLastProposition, State.START);
            lastProposition.recursiveDeref();
            notLastProposition.recursiveDeref();
            Assert.assertEquals(guards.checkZeroRef(), 0L);
            dfa = builder.create();
            Assert.expectThrows(IllegalStateException.class, builder::create);
            Assert.expectThrows(IllegalStateException.class, () -> builder.withState(State.DEAD, true));
        } finally {
            guards.quit();
        }
        final BitSet bits = new BitSet();
        bits.set(255);
        final NamedBitSet<Integer> lastPropositionTrue = new NamedBitSet<>(Alphabets.integers(0, 255), bits);
        final NamedBitSet<Integer> allPropositionsFalse = new NamedBitSet<>(Alphabets.integers(0, 255), new BitSet());
        try (dfa) {
            Assert.assertEquals(dfa.size(), 3);
            Assert.assertEquals(dfa.getInitialState(), State.START);
            Assert.assertTrue(dfa.isAccepting(State.ACCEPT));
            Assert.assertFalse(dfa.isAccepting(State.START));
            Assert.assertNull(dfa.getTransition(State.DEAD, lastPropositionTrue));
            final ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                final CompletableFuture<?>[] futures = new CompletableFuture<?>[100];
                for (int i = 0; i < futures.length; i++) {
                    futures[i] = CompletableFuture.runAsync(() -> {
                        for (int j = 0; j < 100; j++) {
                            Assert.assertEquals(dfa.getTransition(State.START, lastPropositionTrue), State.ACCEPT);
                            Assert.assertEquals(dfa.getTransition(State.START, allPropositionsFalse), State.START);
                        }
                    }, pool);
                }
                CompletableFuture.allOf(futures).join();
            } finally {
                pool.shutdownNow();
            }
        }
        dfa.close();
        Assert.expectThrows(IllegalStateException.class, () -> dfa.getTransition(State.START, lastPropositionTrue));
        Assert.expectThrows(IllegalStateException.class, () -> dfa.getSuccessor(State.START));
        Assert.expectThrows(IllegalStateException.class, dfa::getStates);
        Assert.expectThrows(IllegalStateException.class, dfa::getInitialState);
        Assert.expectThrows(IllegalStateException.class, () -> dfa.isAccepting(State.START));
    }

    @Test
    public void testReorderedValuationAlphabet() {
        final BDDManager guards = new BDDManager();
        try {
            final BDD a = guards.ithVar(0);
            try (ADDDFABuilder<String, String> builder = new ADDDFABuilder<>(Alphabets.fromArray("a", "b"))) {
                builder.withState("waiting", false).withState("done", true)
                       .withInitial("waiting").withTransition("waiting", a, "done");
                a.recursiveDeref();
                try (ADDDFA<String, String> dfa = builder.create()) {
                    final BitSet bits = new BitSet();
                    bits.set(1);
                    final NamedBitSet<String> input = new NamedBitSet<>(Alphabets.fromArray("b", "a"), bits);
                    Assert.assertEquals(dfa.getTransition("waiting", input), "done");
                    Assert.assertNull(dfa.getTransition("done", input));
                    Assert.assertNull(dfa.getTransition("waiting",
                                                       new NamedBitSet<>(Alphabets.fromArray("a"), new BitSet())));
                    Assert.assertTrue(dfa.isAccepting("done"));
                    Assert.expectThrows(UnsupportedOperationException.class, () -> dfa.getStates().clear());
                }
            }
            Assert.assertEquals(guards.checkZeroRef(), 0L);
        } finally {
            guards.quit();
        }
    }

    @Test
    public void testInvalidTransitionsAndFailureCleanup() {
        final BDDManager guards = new BDDManager();
        final BDDManager other = new BDDManager();
        final ADDDFABuilder<String, String> builder = new ADDDFABuilder<>(Alphabets.fromArray("a", "b"));
        try (builder) {
            builder.withState("s", false).withState("t", true);
            final BDD a = guards.ithVar(0);
            final BDD b = guards.ithVar(1);
            final BDD both = a.and(b);
            final BDD notBoth = both.not();
            final BDD outOfRangeVariable = guards.ithVar(2);
            final BDD guardWithOutOfRangeVariable = a.ite(b, outOfRangeVariable);
            final BDD zero = guards.readLogicZero();
            final BDD aFromOtherManager = other.ithVar(0);
            try {
                builder.withTransition("s", both, "t");
                Assert.expectThrows(IllegalArgumentException.class, () -> builder.withTransition("s", a, "t"));
                Assert.expectThrows(IllegalArgumentException.class, () -> builder.withTransition("s", aFromOtherManager, "s"));
                Assert.expectThrows(IllegalArgumentException.class, () -> builder.withTransition("s", guardWithOutOfRangeVariable, "t"));
                Assert.expectThrows(IllegalArgumentException.class, () -> builder.withTransition("missing", a, "s"));
                Assert.expectThrows(IllegalArgumentException.class, () -> builder.withTransition("s", a, "missing"));
                Assert.expectThrows(IllegalArgumentException.class, () -> builder.withInitial("missing"));
                builder.withTransition("s", zero, "t").withTransition("s", zero, "s")
                       .withTransition("s", notBoth, "s");
                try (ADDDFA<String, String> dfa = builder.create()) {
                    for (int value = 0; value < 4; value++) {
                        final NamedBitSet<String> input = new NamedBitSet<>(Alphabets.fromArray("a", "b"),
                                                                          BitSet.valueOf(new long[] {value}));
                        Assert.assertEquals(dfa.getTransition("s", input), value == 3 ? "t" : "s");
                    }
                }
            } finally {
                a.recursiveDeref();
                b.recursiveDeref();
                both.recursiveDeref();
                notBoth.recursiveDeref();
                outOfRangeVariable.recursiveDeref();
                guardWithOutOfRangeVariable.recursiveDeref();
                zero.recursiveDeref();
                aFromOtherManager.recursiveDeref();
            }
            Assert.assertEquals(guards.checkZeroRef(), 0L);
            Assert.assertEquals(other.checkZeroRef(), 0L);
        } finally {
            guards.quit();
            other.quit();
        }
    }

    @Test
    public void testUnfinishedBuilderAndMissingInitialState() {
        final ADDDFABuilder<String, String> abandoned = new ADDDFABuilder<>(Alphabets.fromArray("a"));
        abandoned.withState("s", false);
        abandoned.close();
        abandoned.close();
        Assert.expectThrows(IllegalStateException.class, abandoned::create);
        Assert.expectThrows(IllegalStateException.class, () -> abandoned.withInitial(null));
        try (ADDDFABuilder<String, String> builder = new ADDDFABuilder<>(Alphabets.fromArray("a"))) {
            builder.withState("s", true).withState("s", false).withInitial("s").withInitial(null);
            Assert.expectThrows(NullPointerException.class, () -> builder.withState(null, false));
            try (ADDDFA<String, String> dfa = builder.create()) {
                Assert.assertNull(dfa.getInitialState());
                Assert.assertFalse(dfa.isAccepting("s"));
                Assert.assertEquals(dfa.size(), 1);
                Assert.assertNull(dfa.getTransition("s", new NamedBitSet<>(Alphabets.fromArray("a"), new BitSet())));
            }
        }
        try (ADDDFABuilder<String, String> builder = new ADDDFABuilder<>(Alphabets.fromArray())) {
            try (ADDDFA<String, String> dfa = builder.create()) {
                Assert.assertTrue(dfa.getStates().isEmpty());
                Assert.assertNull(dfa.getInitialState());
            }
        }
    }

    @Test
    public void testIndependentLifetimesAndConstantGuards() {
        final BDDManager guards = new BDDManager();
        final BDD one = guards.readOne();
        final BDD zero = guards.readLogicZero();
        try (ADDDFABuilder<String, String> first = new ADDDFABuilder<>(Alphabets.fromArray());
             ADDDFABuilder<String, String> second = new ADDDFABuilder<>(Alphabets.fromArray())) {
            first.withState("s", true).withTransition("s", zero, "s").withTransition("s", one, "s");
            second.withState("t", false).withTransition("t", one, "t");
            try (ADDDFA<String, String> dfa = first.create()) {
                second.close();
                Assert.assertEquals(dfa.getTransition("s", new NamedBitSet<>(Alphabets.fromArray(), new BitSet())),
                                    "s");
            }
        } finally {
            one.recursiveDeref();
            zero.recursiveDeref();
            Assert.assertEquals(guards.checkZeroRef(), 0L);
            guards.quit();
        }
    }

    @Test
    public void testGuardsFromDifferentManagersAndReorderedVariables() {
        final BDDManager first = new BDDManager();
        final BDDManager second = new BDDManager();
        try (ADDDFABuilder<String, String> builder = new ADDDFABuilder<>(Alphabets.fromArray("a", "b"))) {
            final BDD a = first.ithVar(0);
            final BDD b = first.ithVar(1);
            final BDD both = a.and(b);
            final BDD otherA = second.ithVar(0);
            final BDD notA = otherA.not();
            try {
                Assert.assertTrue(first.setVariableOrder(new int[] {1, 0}));
                builder.withState("s", false).withState("yes", true).withState("no", false)
                       .withTransition("s", both, "yes").withTransition("s", notA, "no");
                try (ADDDFA<String, String> dfa = builder.create()) {
                    for (int value = 0; value < 4; value++) {
                        final NamedBitSet<String> input = new NamedBitSet<>(Alphabets.fromArray("a", "b"),
                                                                          BitSet.valueOf(new long[] {value}));
                        Assert.assertEquals(dfa.getTransition("s", input),
                                            (value & 1) == 0 ? "no" : value == 3 ? "yes" : null);
                    }
                }
            } finally {
                a.recursiveDeref();
                b.recursiveDeref();
                both.recursiveDeref();
                otherA.recursiveDeref();
                notA.recursiveDeref();
            }
            Assert.assertEquals(first.checkZeroRef(), 0L);
            Assert.assertEquals(second.checkZeroRef(), 0L);
        } finally {
            first.quit();
            second.quit();
        }
    }
}
