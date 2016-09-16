//
// Nenya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
// https://github.com/threerings/nenya
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.cast;

import java.util.Iterator;

/**
 * Makes available a collection of character components and associated metadata. Character
 * components are animated sequences that can be composited together to create a complete
 * character visualization (imagine interchanging pairs of boots, torsos, hats, etc.).
 */
public interface ComponentRepository
{
    /**
     * Returns the {@link CharacterComponent} object for the given component identifier.
     */
    public CharacterComponent getComponent (int componentId)
        throws NoSuchComponentException;

    /**
     * Returns the {@link CharacterComponent} object with the given component class and name.
     */
    public CharacterComponent getComponent (String className, String compName)
        throws NoSuchComponentException;

    /**
     * Returns the {@link ComponentClass} with the specified name or null if none exists with that
     * name.
     */
    public ComponentClass getComponentClass (String className);

    /**
     * Iterates over the {@link ComponentClass} instances representing all available character
     * component classes.
     */
    public Iterator<ComponentClass> enumerateComponentClasses ();

    /**
     * Iterates over the {@link ActionSequence} instances representing every available action
     * sequence.
     */
    public Iterator<ActionSequence> enumerateActionSequences ();

    /**
     * Iterates over the component ids of all components in the specified class.
     */
    public Iterator<Integer> enumerateComponentIds (ComponentClass compClass);
}
