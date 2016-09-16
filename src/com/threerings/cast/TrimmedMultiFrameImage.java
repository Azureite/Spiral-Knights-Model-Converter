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

import java.awt.Rectangle;

import com.threerings.media.util.MultiFrameImage;

/**
 * Used to generate more memory efficient composited images in
 * circumstances where we have trimmed underlying component images.
 */
public interface TrimmedMultiFrameImage extends MultiFrameImage
{
    /**
     * Fills in the minimum bounding rectangle for this image that
     * contains all non-transparent pixels. If this information is
     * unavailable, the bounds of the entire image may be returned in
     * exchange for improved performance.
     */
    public void getTrimmedBounds (int index, Rectangle bounds);
}
