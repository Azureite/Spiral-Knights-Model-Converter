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

import com.threerings.util.Name;

import com.threerings.parlor.game.data.GameAI;
import com.threerings.parlor.server.PlayManagerDelegate;

/**
 * Extends the {@link PlayManagerDelegate} mechanism with game manager specific methods.
 */
public class GameManagerDelegate extends PlayManagerDelegate
{
    public GameManagerDelegate ()
    {
    }

    /**
     * @deprecated use the zero-argument constructor.
     */
    @Deprecated public GameManagerDelegate (GameManager gmgr)
    {
    }

    /**
     * Called by the game manager when the game is about to start.
     */
    public void gameWillStart ()
    {
    }

    /**
     * Called by the game manager after the game was started.
     */
    public void gameDidStart ()
    {
    }

    /**
     * Called when a player in the game has been replaced by a call to {@link
     * GameManager#replacePlayer}.
     */
    public void playerWasReplaced (int pidx, Name oldPlayer, Name newPlayer)
    {
    }

    /**
     * Called by the manager when we should do some AI. Only called while the game is IN_PLAY.
     *
     * @param pidx the player index to fake some gameplay for.
     * @param ai a record indicating the AI's configuration.
     */
    public void tickAI (int pidx, GameAI ai)
    {
    }

    /**
     * Called by the game manager when the game is about to end.
     */
    public void gameWillEnd ()
    {
    }

    /**
     * Called by the game manager after the game ended.
     */
    public void gameDidEnd ()
    {
    }

    /**
     * Called when the game is about to reset, but before any other clearing out of game data has
     * taken place.  Derived classes should override this if they need to perform some pre-reset
     * activities.
     */
    public void gameWillReset ()
    {
    }

    /**
     * Called when the specified player has been set as an AI with the supplied AI configuration.
     */
    public void setAI (int pidx, GameAI ai)
    {
    }
}
