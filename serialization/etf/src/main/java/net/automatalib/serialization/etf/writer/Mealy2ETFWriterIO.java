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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.concept.StateIDs;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.IOUtil;

/**
 * Write a Mealy machine with straightforward IO semantics.
 *
 * @param <I> the input type
 * @param <O> the output type
 */
public final class Mealy2ETFWriterIO<I, O> extends AbstractETFWriter<I, MealyMachine<?, I, ?, O>> {

    private static final Pattern LABEL_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Set<String> RESERVED_LABEL_NAMES = Set.of("id", "input", "output", "letter");

    private static final Mealy2ETFWriterIO<?, ?> INSTANCE = new Mealy2ETFWriterIO<>(Collections.emptyMap());

    private final Map<String, ? extends Function<? super O, ?>> outputLabels;

    private Mealy2ETFWriterIO(Map<String, ? extends Function<? super O, ?>> outputLabels) {
        this.outputLabels = outputLabels;
    }

    /**
     * Write the edge type. An edge has three edge labels: input, output, and the output labels.
     *
     * @param pw the Writer.
     */
    @Override
    protected void writeEdge(PrintWriter pw) {
        pw.println("begin edge");
        pw.println("input:input");
        pw.println("output:output");
        for (String label : outputLabels.keySet()) {
            pw.printf("%s:%s%n", label, label);
        }
        pw.println("end edge");
    }

    /**
     * Write ETF parts specific for Mealy machines with IO semantics.
     * <p>
     * Writes:
     *  - the initial state,
     *  - the valuations for the state ids,
     *  - the transitions,
     *  - the input alphabet (for the input labels on edges),
     *  - the output alphabet (for the output labels on edges),
     *  - the values of each output label.
     *
     * @param pw the Writer.
     * @param mealy the Mealy machine to write.
     * @param inputs the alphabet.
     */
    @Override
    protected void writeETF(PrintWriter pw, MealyMachine<?, I, ?, O> mealy, Alphabet<I> inputs) {
        writeETFInternal(pw, mealy, inputs);
    }

    private <S, T> void writeETFInternal(PrintWriter pw, MealyMachine<S, I, T, O> mealy, Alphabet<I> inputs) {
        final StateIDs<S> stateIDs = mealy.stateIDs();

        // write the initial state
        final S init = mealy.getInitialState();
        if (init != null) {
            pw.println("begin init");
            pw.printf("%d%n", stateIDs.getStateId(init));
            pw.println("end init");
        }

        // write the state ids
        pw.println("begin sort id");
        mealy.getStates().forEach(s -> pw.printf("\"%s\"%n", s));
        pw.println("end sort");

        // create a new (insertion stable) map that contains indices for the output alphabet
        final Map<O, Integer> outputIndices = new LinkedHashMap<>();

        // the same for the values of each output label
        final Map<String, Map<String, Integer>> labelIndices = new LinkedHashMap<>();
        outputLabels.keySet().forEach(l -> labelIndices.put(l, new LinkedHashMap<>()));

        // write the transitions
        pw.println("begin trans");
        for (S s : mealy.getStates()) {
            for (I i : inputs) {
                final T t = mealy.getTransition(s, i);
                if (t != null) {
                    final O o = mealy.getTransitionOutput(t);
                    outputIndices.computeIfAbsent(o, ii -> outputIndices.size());
                    final S n = mealy.getSuccessor(t);
                    pw.printf("%s/%s %d %d",
                              stateIDs.getStateId(s),
                              stateIDs.getStateId(n),
                              inputs.getSymbolIndex(i),
                              outputIndices.get(o));
                    for (Map.Entry<String, ? extends Function<? super O, ?>> e : outputLabels.entrySet()) {
                        final Map<String, Integer> indices = labelIndices.get(e.getKey());
                        final String value = String.valueOf(e.getValue().apply(o));
                        pw.printf(" %d", indices.computeIfAbsent(value, v -> indices.size()));
                    }
                    pw.println();
                }
            }
        }
        pw.println("end trans");

        // write the letters in the input alphabet
        pw.println("begin sort input");
        inputs.forEach(i -> pw.printf("\"%s\"%n", i));
        pw.println("end sort");

        // write the letters in the output alphabet
        pw.println("begin sort output");
        for (O o : outputIndices.keySet()) {
            pw.printf("\"%s\"%n", o);
        }
        pw.println("end sort");

        // write the values of each output label
        for (Map.Entry<String, Map<String, Integer>> e : labelIndices.entrySet()) {
            pw.printf("begin sort %s%n", e.getKey());
            for (String value : e.getValue().keySet()) {
                pw.printf("\"%s\"%n", value);
            }
            pw.println("end sort");
        }
    }

    @Override
    public void writeModel(OutputStream os, MealyMachine<?, I, ?, O> model, Alphabet<I> alphabet) {
        try (PrintWriter pw = new PrintWriter(IOUtil.asNonClosingUTF8Writer(os))) {
            write(pw, model, alphabet);
        }
    }

    @SuppressWarnings("unchecked")
    public static <I, O> Mealy2ETFWriterIO<I, O> getInstance() {
        return (Mealy2ETFWriterIO<I, O>) INSTANCE;
    }

    /**
     * Returns a writer that adds an edge label {@code l} with value {@code f(o)} for each entry {@code (l, f)} of the
     * given map, where {@code o} is the output of the transition. Formulae may refer to these labels, e.g.,
     * {@code l == "v"}.
     *
     * @param outputLabels
     *         the output labels
     * @param <I>
     *         input symbol type
     * @param <O>
     *         output symbol type
     *
     * @return the writer
     *
     * @throws IllegalArgumentException
     *         if a label is not an identifier or is a reserved name
     */
    public static <I, O> Mealy2ETFWriterIO<I, O> withOutputLabels(Map<String, ? extends Function<? super O, ?>> outputLabels) {
        for (String label : outputLabels.keySet()) {
            if (!LABEL_NAME.matcher(label).matches() || RESERVED_LABEL_NAMES.contains(label)) {
                throw new IllegalArgumentException("Illegal name of an output label: '" + label + '\'');
            }
        }
        return new Mealy2ETFWriterIO<>(outputLabels);
    }
}
