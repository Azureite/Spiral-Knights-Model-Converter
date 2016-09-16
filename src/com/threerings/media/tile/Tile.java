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

package com.threerings.media.tile;


import java.util.Arrays;

import java.awt.Graphics2D;

import com.threerings.media.image.Colorization;
import com.threerings.media.image.Mirage;

/**
 * A tile represents a single square in a single layer in a scene.
 */
public class Tile // implements Cloneable
{
    /** Used when caching tiles. */
    public static class Key
    {
        public TileSet tileSet;
        public int tileIndex;
        public Colorization[] zations;

        public Key (TileSet tileSet, int tileIndex, Colorization[] zations) {
            this.tileSet = tileSet;
            this.tileIndex = tileIndex;
            this.zations = zations;
        }

        @Override
        public boolean equals (Object other) {
            if (other instanceof Key) {
                Key okey = (Key)other;
                return (tileSet == okey.tileSet &&
                        tileIndex == okey.tileIndex &&
                        Arrays.equals(zations, okey.zations));
            } else {
                return false;
            }
        }

        @Override
        public int hashCode () {
            int code = (tileSet == null) ? tileIndex :
                (tileSet.hashCode() ^ tileIndex);
            int zcount = (zations == null) ? 0 : zations.length;
            for (int ii = 0; ii < zcount; ii++) {
                if (zations[ii] != null) {
                    code ^= zations[ii].hashCode();
                }
            }
            return code;
        }
    }

    /** The key associated with this tile. */
    public Key key;

    /**
     * Configures this tile with its tile image.
     */
    public void setImage (Mirage image)
    {
        if (_mirage != null) {
            _totalTileMemory -= _mirage.getEstimatedMemoryUsage();
        }
        _mirage = image;
        if (_mirage != null) {
            _totalTileMemory += _mirage.getEstimatedMemoryUsage();
        }
    }

    /**
     * Returns the width of this tile.
     */
    public int getWidth ()
    {
        return _mirage.getWidth();
    }

    /**
     * Returns the height of this tile.
     */
    public int getHeight ()
    {
        return _mirage.getHeight();
    }

    /**
     * Returns the estimated memory usage of our underlying tile image.
     */
    public long getEstimatedMemoryUsage ()
    {
        return _mirage.getEstimatedMemoryUsage();
    }

    /**
     * Render the tile image at the specified position in the given
     * graphics context.
     */
    public void paint (Graphics2D gfx, int x, int y)
    {
        _mirage.paint(gfx, x, y);
    }

    /**
     * Returns true if the specified coordinates within this tile contains
     * a non-transparent pixel.
     */
    public boolean hitTest (int x, int y)
    {
        return _mirage.hitTest(x, y);
    }

//     /**
//      * Creates a shallow copy of this tile object.
//      */
//     public Tile clone ()
//     {
//         try {
//             return (Tile)super.clone();
//         } catch (CloneNotSupportedException cnse) {
//             throw new AssertionError(cnse);
//         }
//     }

    @Override
    public String toString ()
    {
        StringBuilder buf = new StringBuilder("[");
        toString(buf);
        return buf.append("]").toString();
    }

    /**
     * This should be overridden by derived classes (which should be sure
     * to call <code>super.toString()</code>) to append the derived class
     * specific tile information to the string buffer.
     */
    protected void toString (StringBuilder buf)
    {
        buf.append(_mirage.getWidth()).append("x");
        buf.append(_mirage.getHeight());
    }

    /** Decrement total tile memory by our value. */
    @Override
    protected void finalize ()
    {
        if (_mirage != null) {
            _totalTileMemory -= _mirage.getEstimatedMemoryUsage();
        }
    }

    /** Our tileset image. */
    protected Mirage _mirage;

    /** Used to track total (estimated) memory in use by tiles. */
    protected static long _totalTileMemory = 0L;
}
