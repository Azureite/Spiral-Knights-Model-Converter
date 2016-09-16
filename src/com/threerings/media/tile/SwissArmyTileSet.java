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

import java.io.IOException;
import java.io.ObjectInputStream;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import com.samskivert.util.StringUtil;

/**
 * The swiss army tileset supports a diverse variety of tiles in the tileset image. Each row can
 * contain varying numbers of tiles and each row can have its own width and height. Tiles can be
 * separated from the edge of the tileset image by some border offset and can be separated from one
 * another by a gap distance.
 */
public class SwissArmyTileSet extends TileSet
{
    @Override
    public int getTileCount ()
    {
        return _numTiles;
    }

    @Override
    public Rectangle computeTileBounds (int tileIndex, Rectangle bounds)
    {
        // find the row number containing the sought-after tile
        int ridx, tcount, ty, tx;
        ridx = tcount = 0;

        // start tile image position at image start offset
        tx = _offsetPos.x;
        ty = _offsetPos.y;

        while ((tcount += _tileCounts[ridx]) < tileIndex + 1) {
            // increment tile image position by row height and gap distance
            ty += (_heights[ridx++] + _gapSize.height);
        }

        // determine the horizontal index of this tile in the row
        int xidx = tileIndex - (tcount - _tileCounts[ridx]);

        // final image x-position is based on tile width and gap distance
        tx += (xidx * (_widths[ridx] + _gapSize.width));

//         Log.info("Computed tile bounds [tileIndex=" + tileIndex +
//                  ", ridx=" + ridx + ", xidx=" + xidx + ", tx=" + tx + ", ty=" + ty + "].");

        // crop the tile-sized image chunk from the full image
        bounds.setBounds(tx, ty, _widths[ridx], _heights[ridx]);
        return bounds;
    }

    /**
     * Sets the tile counts which are the number of tiles in each row of the tileset image. Each
     * row can have an arbitrary number of tiles.
     */
    public void setTileCounts (int[] tileCounts)
    {
        _tileCounts = tileCounts;

        // compute our total tile count
        computeTileCount();
    }

    /**
     * Returns the tile count settings.
     */
    public int[] getTileCounts ()
    {
        return _tileCounts;
    }

    /**
     * Computes our total tile count from the individual counts for each row.
     */
    protected void computeTileCount ()
    {
        // compute our number of tiles
        _numTiles = 0;
        for (int count : _tileCounts) {
            _numTiles += count;
        }
    }

    /**
     * Sets the tile widths for each row. Each row can have tiles of a different width.
     */
    public void setWidths (int[] widths)
    {
        _widths = widths;
    }

    /**
     * Returns the width settings.
     */
    public int[] getWidths ()
    {
        return _widths;
    }

    /**
     * Sets the tile heights for each row. Each row can have tiles of a different height.
     */
    public void setHeights (int[] heights)
    {
        _heights = heights;
    }

    /**
     * Returns the height settings.
     */
    public int[] getHeights ()
    {
        return _heights;
    }

    /**
     * Sets the offset in pixels of the upper left corner of the first tile in the first row. If
     * the tileset image has a border, this can be set to account for it.
     */
    public void setOffsetPos (Point offsetPos)
    {
        _offsetPos = offsetPos;
    }

    /**
     * Sets the size of the gap between tiles (in pixels). If the tiles have space between them,
     * this can be set to account for it.
     */
    public void setGapSize (Dimension gapSize)
    {
        _gapSize = gapSize;
    }

    @Override
    protected void toString (StringBuilder buf)
    {
        super.toString(buf);
        buf.append(", widths=").append(StringUtil.toString(_widths));
        buf.append(", heights=").append(StringUtil.toString(_heights));
        buf.append(", tileCounts=").append(StringUtil.toString(_tileCounts));
        buf.append(", offsetPos=").append(StringUtil.toString(_offsetPos));
        buf.append(", gapSize=").append(StringUtil.toString(_gapSize));
    }

    private void readObject (ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();

        // compute our total tile count
        computeTileCount();
    }

    /** The number of tiles in each row. */
    protected int[] _tileCounts;

    /** The number of tiles in the tileset. */
    protected int _numTiles;

    /** The width of the tiles in each row in pixels. */
    protected int[] _widths;

    /** The height of the tiles in each row in pixels. */
    protected int[] _heights;

    /** The offset distance (x, y) in pixels from the top-left of the image to the start of the
     * first tile image.  */
    protected Point _offsetPos = new Point();

    /** The distance (x, y) in pixels between each tile in each row horizontally, and between each
     * row of tiles vertically.  */
    protected Dimension _gapSize = new Dimension();

    /** Increase this value when object's serialized state is impacted by a class change
     * (modification of fields, inheritance). */
    private static final long serialVersionUID = 1;
}
