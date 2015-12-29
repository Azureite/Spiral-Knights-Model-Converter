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

package com.threerings.puzzle.util;

import java.awt.event.KeyEvent;

import com.threerings.util.KeyTranslatorImpl;

import com.threerings.puzzle.client.PuzzleController;
import com.threerings.puzzle.client.PuzzlePanel;

/**
 * Puzzle game related utilities.
 */
public class PuzzleGameUtil
{
    /**
     * Returns a key translator configured with basic puzzle game mappings.
     */
    public static KeyTranslatorImpl getKeyTranslator ()
    {
        KeyTranslatorImpl xlate = new KeyTranslatorImpl();

        if (!PuzzlePanel.isRobotTesting()) {
            // add the standard pause keys
            xlate.addPressCommand(KeyEvent.VK_P, PuzzleController.TOGGLE_CHATTING);
        }

        return xlate;
    }
}
