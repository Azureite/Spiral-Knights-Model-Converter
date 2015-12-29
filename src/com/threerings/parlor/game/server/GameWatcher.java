//
// $Id$
//
// Vilya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
// http://code.google.com/p/vilya/
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

package com.threerings.parlor.game.server;

import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;

import com.threerings.crowd.server.PlaceManager;

import com.threerings.parlor.game.data.GameObject;

/**
 * An abstract convenience class used server-side to keep an eye on a game and perform a one-time
 * game-over activity when the game ends. Classes that care to make use of the game watcher should
 * create an instance with their newly created {@link GameObject} and implement
 * {@link #gameDidEnd}.
 */
public abstract class GameWatcher<T extends GameObject>
    implements AttributeChangeListener
{
    public void init (PlaceManager plmgr)
    {
        @SuppressWarnings("unchecked")
        T gameobj = (T)plmgr.getPlaceObject();
        init(gameobj);
    }

    public void init (T gameobj)
    {
        _gameobj = gameobj;
        _gameobj.addListener(this);
    }

    public void attributeChanged (AttributeChangedEvent event)
    {
        if (event.getName().equals(GameObject.STATE)) {
            // if we transitioned to a non-in-play state, the game has completed
            if (!_gameobj.isInPlay()) {
                try {
                    gameDidEnd(_gameobj);
                } finally {
                    _gameobj.removeListener(this);
                    _gameobj = null;
                }
            }
        }
    }

    /**
     * Called when the game ends to give derived classes a chance to engage in their game-over
     * antics.
     */
    protected abstract void gameDidEnd (T gameobj);

    /** The game object we're observing. */
    protected T _gameobj;
}
