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

package com.threerings.puzzle.drop.client;

import java.awt.Graphics2D;
import java.awt.Rectangle;

import com.threerings.media.AbstractMedia;
import com.threerings.media.AbstractMediaManager;
import com.threerings.media.animation.Animation;
import com.threerings.media.sprite.PathObserver;
import com.threerings.media.sprite.Sprite;
import com.threerings.media.util.Path;

import com.threerings.puzzle.drop.data.DropBoard;

/**
 * Animates all the pieces on a puzzle board doing some sort of global effect like all flying into
 * place or out into the ether.
 */
public abstract class PieceGroupAnimation extends Animation
    implements PathObserver
{
    /**
     * Creates a piece group animation which must be initialized with a subsequent call to
     * {@link AbstractMedia#init(AbstractMediaManager)}.
     */
    public PieceGroupAnimation (DropBoardView view, DropBoard board)
    {
        super(new Rectangle(0, 0, 0, 0)); // we don't render ourselves
        _view = view;
        _board = board;
    }

    @Override
    public void tick (long tickStamp)
    {
        // nothing doing
    }

    @Override
    public void paint (Graphics2D gfx)
    {
        // nothing doing
    }

    // documentation inherited from interface
    public void pathCancelled (Sprite sprite, Path path)
    {
        _finished = (--_penders == 0);
    }

    // documentation inherited from interface
    public void pathCompleted (Sprite sprite, Path path, long when)
    {
        _finished = (--_penders == 0);
    }

    @Override
    protected void willStart (long tickStamp)
    {
        super.willStart(tickStamp);

        // create an image sprite for every piece on the board and set them on their paths
        int width = _board.getWidth(), height = _board.getHeight();
        _sprites = new Sprite[width * height];
        for (int yy = 0; yy < height; yy++) {
            for (int xx = 0; xx < width; xx++) {
                int spos = yy*width+xx;
                _sprites[spos] = _view.getPieceSprite(xx, yy);
                if (_sprites[spos] != null) {
                    configureSprite(_sprites[spos], xx, yy);
                    _sprites[spos].addSpriteObserver(this);
                    _penders++;
                }
            }
        }
    }

    /**
     * An animation must override this method to configure each sprite with a path, potentially a
     * render order, and whatever other configurations are needed.
     */
    protected abstract void configureSprite (Sprite sprite, int xx, int yy);

    protected DropBoardView _view;
    protected DropBoard _board;
    protected Sprite[] _sprites;
    protected int _penders;
}
