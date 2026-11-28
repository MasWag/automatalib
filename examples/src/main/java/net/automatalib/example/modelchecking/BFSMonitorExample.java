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
package net.automatalib.example.modelchecking;

import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

import info.scce.addlib.dd.bdd.BDD;
import info.scce.addlib.dd.bdd.BDDManager;
import net.automatalib.addlib.ADDDFA;
import net.automatalib.addlib.ADDDFABuilder;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.alphabet.impl.NamedBitSet;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.modelchecker.monitor.BFSMonitorMealy;
import net.automatalib.modelchecker.monitor.BFSMonitorMealyBuilder;
import net.automatalib.modelchecker.monitor.ParallelBFSMonitorMealy;
import net.automatalib.modelchecker.monitor.ParallelBFSMonitorMealyBuilder;
import net.automatalib.modelchecking.ModelChecker;
import net.automatalib.util.automaton.builder.AutomatonBuilders;
import net.automatalib.word.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks finite monitor properties with sequential and parallel BFS. Requires the ADDLib CUDD backend.
 */
public final class BFSMonitorExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(BFSMonitorExample.class);
    private static final Alphabet<String> PROPOSITIONS = Alphabets.fromArray("outputIsThree", "outputIsFour");

    private BFSMonitorExample() {
        // prevent instantiation
    }

    public static void main(String[] args) {
        final Alphabet<Character> inputs = Alphabets.characters('a', 'c');

        // @formatter:off
        final MealyMachine<?, Character, ?, Character> mealy =
                AutomatonBuilders.<Character, Character>newMealy(inputs)
                        .withInitial("q0")
                        .from("q0").on('a').withOutput('1').to("q1")
                        .from("q1").on('b').withOutput('2').to("q2")
                        .from("q2").on('c').withOutput('3').to("q0")
                        .create();
        // @formatter:on

        // Only property observations change; counterexamples retain the original Mealy outputs.
        final BiFunction<Character, Character, NamedBitSet<String>> observation = (input, output) -> {
            final BitSet bits = new BitSet(PROPOSITIONS.size());
            bits.set(PROPOSITIONS.getSymbolIndex("outputIsThree"), output == '3');
            bits.set(PROPOSITIONS.getSymbolIndex("outputIsFour"), output == '4');
            return new NamedBitSet<>(PROPOSITIONS, bits);
        };

        try (ADDDFA<String, String> noOutputThree = forbidProposition("outputIsThree");
             ADDDFA<String, String> noOutputFour = forbidProposition("outputIsFour")) {
            final ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                final BFSMonitorMealy<Character, Character, NamedBitSet<String>> sequential =
                        new BFSMonitorMealyBuilder<Character, Character, NamedBitSet<String>>()
                                .withObservation(observation).create();
                final ParallelBFSMonitorMealy<Character, Character, NamedBitSet<String>> parallel =
                        new ParallelBFSMonitorMealyBuilder<Character, Character, NamedBitSet<String>>()
                                .withObservation(observation).withExecutor(executor).create();

                for (ModelChecker.MealyModelChecker<Character, Character, DFA<?, NamedBitSet<String>>,
                        MealyMachine<?, Character, ?, Character>> checker : List.of(sequential, parallel)) {
                    final String algorithm = checker.getClass().getSimpleName();
                    LOGGER.info("{}: no output 4 is satisfied: {}",
                                algorithm,
                                checker.findCounterExample(mealy, inputs, noOutputFour) == null);
                    final MealyMachine<?, Character, ?, Character> counterexample =
                            checker.findCounterExample(mealy, inputs, noOutputThree);
                    LOGGER.info("{}: no output 3 is satisfied: {}", algorithm, counterexample == null);
                    if (counterexample != null) {
                        // The first output 3 occurs on a, b, c; the witness is finite.
                        final Word<Character> word = Word.fromString("abc");
                        LOGGER.info("{}: counterexample inputs: {}, outputs: {}",
                                    algorithm, word, counterexample.computeOutput(word));
                    }
                }
            } finally {
                // Checks finish all worker tasks before returning, including on failure.
                executor.shutdown();
            }
        }
    }

    private static ADDDFA<String, String> forbidProposition(String proposition) {
        final BDDManager guards = new BDDManager();
        try (ADDDFABuilder<String, String> builder = new ADDDFABuilder<>(PROPOSITIONS)) {
            final BDD forbidden = guards.ithVar(PROPOSITIONS.getSymbolIndex(proposition));
            try {
                final BDD allowed = forbidden.not();
                try {
                    // Stay accepting while the proposition is false. A missing transition rejects the prefix.
                    // The builder copies the guard and manages its own native resources.
                    return builder.withState("ok", true).withInitial("ok")
                                  .withTransition("ok", allowed, "ok").create();
                } finally {
                    allowed.recursiveDeref();
                }
            } finally {
                forbidden.recursiveDeref();
            }
        } finally {
            guards.quit();
        }
    }
}
