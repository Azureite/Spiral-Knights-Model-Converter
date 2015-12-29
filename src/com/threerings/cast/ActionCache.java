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

/**
 * A mechanism for caching composited character action animations on disk.
 */
public interface ActionCache
{
    /**
     * Fetches from the cache a composited set of images for a particular character for a
     * particular action.
     */
    public ActionFrames getActionFrames (CharacterDescriptor descrip, String action);

    /**
     * Requests that the specified set of action frames for the specified character be cached.
     */
    public void cacheActionFrames (CharacterDescriptor descrip, String action, ActionFrames frames);
}
