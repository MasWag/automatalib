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
package net.automatalib.modelchecker.ltsmin.ltl;

import java.util.Map;
import java.util.function.Function;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.modelchecker.ltsmin.LTSminUtil;
import net.automatalib.modelchecking.Lasso.MealyLasso;
import net.automatalib.util.automaton.builder.AutomatonBuilders;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for LTSminLTLIO with output labels.
 */
public class LTSminLTLIOOutputLabelsTest {

    private final Alphabet<String> inputs = Alphabets.closedCharStringRange('a', 'b');

    private final Map<String, Function<String, Character>> labels =
            Map.of("c0", o -> o.charAt(0), "c1", o -> o.charAt(1));

    @BeforeClass
    public void setupBeforeClass() {
        if (!LTSminUtil.supports(AbstractLTSminLTL.REQUIRED_VERSION)) {
            throw new SkipException("LTSmin not installed in proper version");
        }
    }

    @Test
    public void testOutputLabels() {
        final MealyMachine<?, String, ?, String> mealy =
                AutomatonBuilders.forMealy(new CompactMealy<String, String>(inputs))
                                 .withInitial("q0")
                                 .from("q0").on("a").withOutput("aa").loop()
                                 .from("q0").on("b").withOutput("cb").loop()
                                 .create();
        final LTSminLTLIO<String, String> modelChecker = new LTSminLTLIOBuilder<String, String>()
                .withString2Input(s -> s)
                .withString2Output(s -> s)
                .withOutputLabels(labels)
                .create();

        Assert.assertNull(modelChecker.findCounterExample(mealy, inputs, "[] (c0 == \"c\" -> c1 == \"b\")"));
        final MealyLasso<String, String> ce = modelChecker.findCounterExample(mealy, inputs, "[] (c1 == \"a\")");
        Assert.assertNotNull(ce);
        Assert.assertTrue(ce.getOutput().stream().anyMatch("cb"::equals));
    }
}
