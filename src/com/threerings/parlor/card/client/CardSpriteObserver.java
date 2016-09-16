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

package com.threerings.parlor.card.client;

import java.awt.event.MouseEvent;

/**
 * Observer interface for (draggable) card sprites.
 */
public interface CardSpriteObserver
{
    /**
     * Notifies the observer that the user clicked a card sprite.
     *
     * @param sprite the dragged sprite
     * @param me the mouse event associated with the drag
     */
    public void cardSpriteClicked (CardSprite sprite, MouseEvent me);

    /**
     * Notifies the observer that the user moved the mouse pointer onto
     * a card sprite.
     *
     * @param sprite the entered sprite
     * @param me the mouse event associated with the entrance
     */
    public void cardSpriteEntered (CardSprite sprite, MouseEvent me);

    /**
     * Notifies the observer that the user moved the mouse pointer off of
     * a card sprite.
     *
     * @param sprite the exited the sprite
     * @param me the mouse event associated with the exit
     */
    public void cardSpriteExited (CardSprite sprite, MouseEvent me);

    /**
     * Notifies the observer that the user dragged a card sprite to a new
     * location.
     *
     * @param sprite the dragged sprite
     * @param me the mouse event associated with the drag
     */
    public void cardSpriteDragged (CardSprite sprite, MouseEvent me);
}
