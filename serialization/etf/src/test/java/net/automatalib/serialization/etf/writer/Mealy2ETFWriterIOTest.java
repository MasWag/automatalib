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
package net.automatalib.serialization.etf.writer;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.common.util.IOUtil;
import net.automatalib.common.util.io.UnclosableOutputStream;
import net.automatalib.util.automaton.builder.AutomatonBuilders;
import net.automatalib.util.automaton.random.RandomAutomata;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for Mealy2ETFWriterIO.
 */
public class Mealy2ETFWriterIOTest {

    @Test
    public void testWrite() throws Exception {
        try (Reader r = IOUtil.asBufferedUTF8Reader(DFA2ETFWriterTest.class.getResourceAsStream("/IO-testWrite.etf"));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            final Alphabet<Character> alphabet = Alphabets.characters('a', 'c');

            final Random random = new Random(0);
            final MealyMachine<?, Character, ?, Character> automaton =
                    RandomAutomata.randomMealy(random, 20, alphabet, alphabet);

            Mealy2ETFWriterIO.<Character, Character>getInstance().writeModel(baos, automaton, alphabet);

            final String expected = IOUtil.toString(r);

            Assert.assertEquals(baos.toString(StandardCharsets.UTF_8), expected);
        }
    }

    @Test
    public void doNotCloseOutputStreamTest() {
        final Alphabet<Character> alphabet = Alphabets.characters('a', 'c');
        final MealyMachine<?, Character, ?, Character> automaton =
                RandomAutomata.randomMealy(new Random(0), 10, alphabet, alphabet);

        Mealy2ETFWriterIO.<Character, Character>getInstance()
                         .writeModel(new UnclosableOutputStream(OutputStream.nullOutputStream()), automaton, alphabet);
    }

    @Test
    public void testWriteOutputLabels() throws Exception {
        try (Reader r = IOUtil.asBufferedUTF8Reader(Mealy2ETFWriterIOTest.class.getResourceAsStream(
                "/IO-testWriteOutputLabels.etf")); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            final Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
            final MealyMachine<?, Character, ?, String> automaton =
                    AutomatonBuilders.forMealy(new CompactMealy<Character, String>(alphabet))
                                     .withInitial("q0")
                                     .from("q0").on('a').withOutput("xa").to("q1")
                                     .from("q0").on('b').withOutput("xb").loop()
                                     .from("q1").on('a').withOutput("yb").loop()
                                     .create();
            final Map<String, Function<String, Character>> labels = new LinkedHashMap<>();
            labels.put("o_0", o -> o.charAt(0));
            labels.put("o_1", o -> o.charAt(1));

            Mealy2ETFWriterIO.<Character, String>withOutputLabels(labels).writeModel(baos, automaton, alphabet);

            final String expected = IOUtil.toString(r);

            Assert.assertEquals(baos.toString(StandardCharsets.UTF_8), expected);
        }
    }

    @Test
    public void testIllegalOutputLabels() {
        for (String label : new String[] {"output", "input", "id", "letter", "1st", "a-b", "a b", ""}) {
            Assert.assertThrows(IllegalArgumentException.class,
                                () -> Mealy2ETFWriterIO.withOutputLabels(Map.of(label, Function.identity())));
        }
    }
}
