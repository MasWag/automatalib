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
package net.automatalib.alphabet.impl;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Set;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.common.util.collection.BitSetIterator;
import net.automatalib.common.util.collection.IteratorUtil;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Immutable valuation of named propositions. Both alphabet order and bits are copied. Proposition objects must have
 * stable equality and hash codes. Equality and hash codes follow {@link Set}, independent of alphabet order and of
 * unused propositions.
 *
 * @param <P>
 *         proposition type
 */
public final class NamedBitSet<P> extends AbstractSet<P> {

    private final Alphabet<P> alphabet;
    private final BitSet bits;

    /**
     * Constructs a valuation by copying the proposition alphabet and bits.
     *
     * @param alphabet
     *         the ordered, duplicate-free proposition alphabet
     * @param bits
     *         the indices of the true propositions
     *
     * @throws IllegalArgumentException
     *         if the alphabet contains duplicates or a set bit lies outside the alphabet
     */
    public NamedBitSet(Alphabet<P> alphabet, BitSet bits) {
        this.alphabet = new MapAlphabet<>(alphabet);
        for (int i = 0; i < this.alphabet.size(); i++) {
            final P symbol = this.alphabet.getSymbol(i);
            if (this.alphabet.getSymbolIndex(symbol) != i) {
                throw new IllegalArgumentException("Duplicate proposition: " + symbol);
            }
        }
        this.bits = (BitSet) bits.clone();
        if (this.bits.length() > this.alphabet.size()) {
            throw new IllegalArgumentException("Bit outside proposition alphabet");
        }
    }

    /**
     * Returns the proposition domain.
     *
     * @return the immutable proposition alphabet
     */
    public Alphabet<P> getAlphabet() {
        return alphabet;
    }

    /**
     * Copies the valuation.
     *
     * @return a mutable copy of the valuation bits
     */
    public BitSet toBitSet() {
        return (BitSet) bits.clone();
    }

    @Override
    public boolean contains(@Nullable Object proposition) {
        @SuppressWarnings("unchecked") // MapAlphabet performs object-based lookups without casting the symbol.
        final P symbol = (P) proposition;
        return alphabet.containsSymbol(symbol) && bits.get(alphabet.getSymbolIndex(symbol));
    }

    @Override
    public int size() {
        return bits.cardinality();
    }

    @Override
    public Iterator<P> iterator() {
        return IteratorUtil.map(new BitSetIterator(bits), alphabet::getSymbol);
    }

}
