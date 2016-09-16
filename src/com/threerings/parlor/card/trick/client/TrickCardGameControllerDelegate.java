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

package com.threerings.parlor.card.trick.client;

import com.threerings.parlor.card.client.CardGameController;
import com.threerings.parlor.turn.client.TurnGameControllerDelegate;

/**
 * A card game controller delegate for trick-based card games, such as Spades and Hearts.
 */
public class TrickCardGameControllerDelegate
    extends TurnGameControllerDelegate
{
    /**
     * Constructor.
     *
     * @param controller the game controller
     */
    public TrickCardGameControllerDelegate (CardGameController controller)
    {
        super(controller);
        _cgctrl = controller;
    }

    /** The card game controller. */
    protected CardGameController _cgctrl;
}
