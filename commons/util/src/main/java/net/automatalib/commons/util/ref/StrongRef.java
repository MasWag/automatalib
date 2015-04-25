/* Copyright (C) 2013 TU Dortmund
 * This file is part of AutomataLib, http://www.automatalib.net/.
 * 
 * AutomataLib is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 3.0 as published by the Free Software Foundation.
 * 
 * AutomataLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with AutomataLib; if not, see
 * http://www.gnu.de/documents/lgpl.en.html.
 */
package net.automatalib.commons.util.ref;

/**
 * A strong reference wrapper, complying to the {@link Ref} interface.
 * 
 * @author Malte Isberner
 *
 * @param <T> referent class.
 */
public final class StrongRef<T> implements Ref<T> {
	
	private final T referent;
	
	/**
	 * Constructor.
	 * @param referent the referent.
	 */
	public StrongRef(T referent) {
		this.referent = referent;
	}

	/*
	 * (non-Javadoc)
	 * @see de.ls5.misc.util.ref.Ref#get()
	 */
	@Override
	public T get() {
		return referent;
	}

}