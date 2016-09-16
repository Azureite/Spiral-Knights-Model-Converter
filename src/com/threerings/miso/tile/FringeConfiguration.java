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

package com.threerings.miso.tile;

import java.util.ArrayList;

import java.io.Serializable;

import com.google.common.collect.Lists;

import com.samskivert.util.HashIntMap;
import com.samskivert.util.StringUtil;

/**
 * Used to manage data about which base tilesets fringe on which others
 * and how they fringe.
 */
public class FringeConfiguration implements Serializable
{
    /**
     * The path (relative to the resource directory) at which the fringe
     * configuration should be loaded and stored.
     */
    public static final String CONFIG_PATH = "config/miso/tile/fringeconf.dat";

    public static class FringeRecord implements Serializable
    {
        /** The tileset id of the base tileset to which this applies. */
        public int base_tsid;

        /** The fringe priority of this base tileset. */
        public int priority;

        /** A list of the possible tilesets that can be used for fringing. */
        public ArrayList<FringeTileSetRecord> tilesets = Lists.newArrayList();

        /** Used when parsing the tilesets definitions. */
        public void addTileset (FringeTileSetRecord record)
        {
            tilesets.add(record);
        }

        /** Did everything parse well? */
        public boolean isValid ()
        {
            return ((base_tsid != 0) && (priority > 0));
        }

        @Override
        public String toString ()
        {
            return "[base_tsid=" + base_tsid + ", priority=" + priority +
                ", tilesets=" + StringUtil.toString(tilesets) + "]";
        }

        /** Increase this value when object's serialized state is impacted
         * by a class change (modification of fields, inheritance). */
        private static final long serialVersionUID = 1;
    }

    /**
     * Used to parse the tileset fringe definitions.
     */
    public static class FringeTileSetRecord implements Serializable
    {
        /** The tileset id of the fringe tileset. */
        public int fringe_tsid;

        /** Is this a mask? */
        public boolean mask;

        /** Did everything parse well? */
        public boolean isValid ()
        {
            return (fringe_tsid != 0);
        }

        @Override
        public String toString ()
        {
            return "[fringe_tsid=" + fringe_tsid + ", mask=" + mask + "]";
        }

        /** Increase this value when object's serialized state is impacted
         * by a class change (modification of fields, inheritance). */
        private static final long serialVersionUID = 1;
    }

    /**
     * Adds a parsed FringeRecord to this instance. This is used when parsing
     * the fringerecords from xml.
     */
    public void addFringeRecord (FringeRecord frec)
    {
        _frecs.put(frec.base_tsid, frec);
    }

    /**
     * If the first base tileset fringes upon the second, return the
     * fringe priority of the first base tileset, otherwise return -1.
     */
    public int fringesOn (int first, int second)
    {
        FringeRecord f1 = _frecs.get(first);

        // we better have a fringe record for the first
        if (null != f1) {

            // it had better have some tilesets defined
            if (f1.tilesets.size() > 0) {

                FringeRecord f2 = _frecs.get(second);

                // and we only fringe if second doesn't exist or has a lower
                // priority
                if ((null == f2) || (f1.priority > f2.priority)) {
                    return f1.priority;
                }
            }
        }

        return -1;
    }

    /**
     * Get a random FringeTileSetRecord from amongst the ones
     * listed for the specified base tileset.
     */
    public FringeTileSetRecord getFringe (int baseset, int hashValue)
    {
        FringeRecord f = _frecs.get(baseset);
        return f.tilesets.get(
            hashValue % f.tilesets.size());
    }

    /** The mapping from base tileset id to fringerecord. */
    protected HashIntMap<FringeRecord> _frecs = new HashIntMap<FringeRecord>();

    /** Increase this value when object's serialized state is impacted by
     * a class change (modification of fields, inheritance). */
    private static final long serialVersionUID = 1;
}
