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

import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

public class NamedBitSetTest {

    @Test
    public void testImmutabilityAndSetEquality() {
        final GrowingMapAlphabet<String> alphabet = new GrowingMapAlphabet<>();
        alphabet.addSymbol("a");
        alphabet.addSymbol("b");
        final BitSet bits = new BitSet();
        bits.set(1);
        final NamedBitSet<String> set = new NamedBitSet<>(alphabet, bits);
        bits.clear();
        alphabet.addSymbol("c");
        set.toBitSet().clear();
        Assert.assertEquals(set.getAlphabet().size(), 2);
        Assert.assertEquals(set, Set.of("b"));
        Assert.assertEquals(new HashSet<>(Set.of("b")), set);
        Assert.assertEquals(set.hashCode(), Set.of("b").hashCode());
        final BitSet reorderedBits = new BitSet();
        reorderedBits.set(0);
        Assert.assertEquals(set, new NamedBitSet<>(Alphabets.fromArray("b", "a", "z"), reorderedBits));
        final List<Runnable> mutations = List.of(() -> set.add("a"),
                                                () -> set.add("a"),
                                                () -> set.remove("b"),
                                                () -> set.removeAll(Set.of("b")),
                                                () -> set.retainAll(Set.of()),
                                                () -> set.removeIf(x -> true),
                                                set::clear);
        for (Runnable mutation : mutations) {
            Assert.expectThrows(UnsupportedOperationException.class, mutation::run);
            Assert.assertEquals(set, Set.of("b"));
            Assert.assertEquals(set.toBitSet(), BitSet.valueOf(new long[] {2}));
        }
        set.addAll(Set.of());
        set.remove("absent");
        set.removeAll(Set.of());
        set.retainAll(Set.of("b"));
        set.removeIf(x -> false);
        Assert.assertEquals(set, Set.of("b"));
        final Iterator<String> iterator = set.iterator();
        Assert.assertEquals(iterator.next(), "b");
        Assert.assertFalse(iterator.hasNext());
        Assert.expectThrows(NoSuchElementException.class, iterator::next);
        Assert.expectThrows(UnsupportedOperationException.class, iterator::remove);
        Assert.assertEquals(set, Set.of("b"));
        Assert.expectThrows(UnsupportedOperationException.class, () -> set.getAlphabet().clear());
        bits.set(9);
        Assert.expectThrows(IllegalArgumentException.class, () -> new NamedBitSet<>(alphabet, bits));
    }

    @Test
    public void testSparseIterationAndDuplicateAlphabet() {
        final BitSet bits = new BitSet();
        bits.set(0);
        bits.set(64);
        bits.set(255);
        final NamedBitSet<Integer> set = new NamedBitSet<>(Alphabets.integers(0, 255), bits);
        Assert.assertEquals(List.copyOf(set), List.of(0, 64, 255));
        Assert.assertFalse(set.contains(1));
        Assert.assertFalse(set.contains(256));
        Assert.assertFalse(set.contains("0"));
        Assert.assertFalse(set.contains(null));
        Assert.assertFalse(new NamedBitSet<>(Alphabets.integers(0, 255), new BitSet()).iterator().hasNext());
        Assert.expectThrows(IllegalArgumentException.class,
                            () -> new NamedBitSet<>(Alphabets.fromList(List.of("a", "a")), new BitSet()));
    }

}
