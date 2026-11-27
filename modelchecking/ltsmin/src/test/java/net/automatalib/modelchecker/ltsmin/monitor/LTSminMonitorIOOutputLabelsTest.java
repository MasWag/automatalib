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
package net.automatalib.modelchecker.ltsmin.monitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.modelchecker.ltsmin.LTSminUtil;
import net.automatalib.util.automaton.builder.AutomatonBuilders;
import net.automatalib.util.automaton.random.RandomAutomata;
import net.automatalib.word.Word;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for LTSminMonitorIO with output labels.
 */
public class LTSminMonitorIOOutputLabelsTest {

    private static final Pattern LABEL_ATOM = Pattern.compile("(c[01]) == \"([a-c])\"");

    private final Alphabet<String> inputs = Alphabets.closedCharStringRange('a', 'b');

    private final Map<String, Function<String, Character>> labels = new LinkedHashMap<>();

    private final List<String> outputs = new ArrayList<>();

    @BeforeClass
    public void setupBeforeClass() {
        if (!LTSminUtil.supports(AbstractLTSminMonitor.REQUIRED_VERSION)) {
            throw new SkipException("LTSmin not installed in proper version");
        }
        labels.put("c0", o -> o.charAt(0));
        labels.put("c1", o -> o.charAt(1));
        for (char c0 : new char[] {'a', 'b', 'c'}) {
            for (char c1 : new char[] {'a', 'b'}) {
                outputs.add("" + c0 + c1);
            }
        }
    }

    private LTSminMonitorIO<String, String> monitor() {
        return new LTSminMonitorIOBuilder<String, String>().withString2Input(s -> s)
                                                           .withString2Output(s -> s)
                                                           .withOutputLabels(labels)
                                                           .create();
    }

    private String toOutputFormula(String formula) {
        final Matcher m = LABEL_ATOM.matcher(formula);
        final StringBuilder result = new StringBuilder();
        while (m.find()) {
            final int index = m.group(1).charAt(1) - '0';
            final char value = m.group(2).charAt(0);
            final String disjunction = outputs.stream()
                                              .filter(o -> o.charAt(index) == value)
                                              .map(o -> "output == \"" + o + '"')
                                              .collect(Collectors.joining(" || ", "(", ")"));
            m.appendReplacement(result, Matcher.quoteReplacement("()".equals(disjunction) ? "false" : disjunction));
        }
        m.appendTail(result);
        return result.toString();
    }

    @Test
    public void testOutputLabels() {
        final MealyMachine<?, String, ?, String> mealy =
                AutomatonBuilders.forMealy(new CompactMealy<String, String>(inputs))
                                 .withInitial("q0")
                                 .from("q0").on("a").withOutput("aa").to("q1")
                                 .from("q0").on("b").withOutput("ba").loop()
                                 .from("q1").on("a").withOutput("ab").loop()
                                 .from("q1").on("b").withOutput("ba").to("q0")
                                 .create();
        final LTSminMonitorIO<String, String> modelChecker = monitor();

        Assert.assertNull(modelChecker.findCounterExample(mealy, inputs, "[] (c0 == \"a\" || c0 == \"b\")"));
        Assert.assertNull(modelChecker.findCounterExample(mealy, inputs, "[] (c0 == \"b\" -> c1 == \"a\")"));

        final MealyMachine<?, String, ?, String> ce =
                modelChecker.findCounterExample(mealy, inputs, "[] (c1 == \"a\")");
        Assert.assertNotNull(ce);
        Assert.assertEquals(ce.computeOutput(Word.fromSymbols("a", "a")), Word.fromSymbols("aa", "ab"));
    }

    @Test
    public void testAgreesWithOutputFormulae() {
        final List<String> formulae = List.of("[] (c0 == \"a\" || c0 == \"b\")",
                                              "[] (c1 == \"a\")",
                                              "[] !(c0 == \"c\" && c1 == \"b\")",
                                              "[] (c0 == \"a\" -> X (c1 == \"b\"))",
                                              "(c1 == \"a\") U (c0 == \"c\")",
                                              "[] (input == \"a\" -> c0 == \"a\" || c1 == \"a\")");
        final LTSminMonitorIO<String, String> withLabels = monitor();
        final LTSminMonitorIO<String, String> withoutLabels =
                new LTSminMonitorIOBuilder<String, String>().withString2Input(s -> s).withString2Output(s -> s).create();
        final Random random = new Random(42);
        int violated = 0;
        int satisfied = 0;

        for (int i = 0; i < 20; i++) {
            final CompactMealy<String, String> mealy =
                    RandomAutomata.randomMealy(random, 1 + random.nextInt(6), inputs, outputs);
            for (String formula : formulae) {
                final MealyMachine<?, String, ?, String> ce1 = withLabels.findCounterExample(mealy, inputs, formula);
                final MealyMachine<?, String, ?, String> ce2 =
                        withoutLabels.findCounterExample(mealy, inputs, toOutputFormula(formula));
                Assert.assertEquals(ce1 == null, ce2 == null, formula + " on " + mealy.size() + " states");
                if (ce1 == null) {
                    satisfied++;
                } else {
                    violated++;
                }
            }
        }
        Assert.assertTrue(violated > 0 && satisfied > 0);
    }
}
