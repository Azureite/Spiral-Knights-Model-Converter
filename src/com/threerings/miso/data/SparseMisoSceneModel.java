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

package com.threerings.miso.data;


import java.util.ArrayList;
import java.util.Iterator;

import java.awt.Rectangle;

import com.samskivert.util.ArrayUtil;
import com.samskivert.util.ListUtil;
import com.samskivert.util.StringUtil;

import com.threerings.io.SimpleStreamableObject;

import com.threerings.util.StreamableHashMap;

import com.threerings.media.util.MathUtil;

import com.threerings.miso.util.ObjectSet;

import static com.threerings.miso.Log.log;

/**
 * Contains miso scene data that is broken up into NxN tile sections.
 */
public class SparseMisoSceneModel extends MisoSceneModel
{
    /** An interface that allows external entities to "visit" and inspect
     * every object in this scene. */
    public static interface ObjectVisitor
    {
        /** Called for each object in the scene, interesting and not. */
        public void visit (ObjectInfo info);
    }

    /** Contains information on a section of this scene. This is only
     * public so that the scene model parser can do its job, so don't go
     * poking around in here. */
    public static class Section extends SimpleStreamableObject
        implements Cloneable
    {
        /** The tile coordinate of our upper leftmost tile. */
        public short x, y;

        /** The width of this section in tiles. */
        public int width;

        /** The combined tile ids (tile set id and tile id) for our
         * section (in row major order). */
        public int[] baseTileIds;

        /** The combined tile ids (tile set id and tile id) of the
         * "uninteresting" tiles in the object layer. */
        public int[] objectTileIds = new int[0];

        /** The x coordinate of the "uninteresting" tiles in the object
         * layer. */
        public short[] objectXs = new short[0];

        /** The y coordinate of the "uninteresting" tiles in the object
         * layer. */
        public short[] objectYs = new short[0];

        /** Information records for the "interesting" objects in the
         * object layer. */
        public ObjectInfo[] objectInfo = new ObjectInfo[0];

        /**
         * Creates a blank section instance, suitable for unserialization
         * or configuration by the XML scene parser.
         */
        public Section ()
        {
        }

        /**
         * Creates a new scene section with the specified dimensions.
         */
        public Section (short x, short y, short width, short height)
        {
            this.x = x;
            this.y = y;
            this.width = width;
            baseTileIds = new int[width*height];
        }

        public int getBaseTileId (int col, int row) {
            if (col < x || col >= (x+width) || row < y || row >= (y+width)) {
                log.warning("Requested bogus tile +" + col + "+" + row +
                            " from " + this + ".");
                return -1;
            } else {
                return baseTileIds[(row-y)*width+(col-x)];
            }
        }

        public void setBaseTile (int col, int row, int fqBaseTileId) {
            baseTileIds[(row-y)*width+(col-x)] = fqBaseTileId;
        }

        public boolean addObject (ObjectInfo info) {
            // sanity check: see if there is already an object of this
            // type at these coordinates
            int dupidx;
            if ((dupidx = ListUtil.indexOf(objectInfo, info)) != -1) {
                log.warning("Refusing to add duplicate object [ninfo=" + info +
                        ", oinfo=" + objectInfo[dupidx] + "].");
                return false;
            }
            if ((dupidx = indexOfUn(info)) != -1) {
                log.warning("Refusing to add duplicate object " +
                        "[info=" + info + "].");
                return false;
            }

            if (info.isInteresting()) {
                objectInfo = ArrayUtil.append(objectInfo, info);
            } else {
                objectTileIds = ArrayUtil.append(objectTileIds, info.tileId);
                objectXs = ArrayUtil.append(objectXs, (short)info.x);
                objectYs = ArrayUtil.append(objectYs, (short)info.y);
            }
            return true;
        }

        public boolean removeObject (ObjectInfo info) {
            // look for it in the interesting info array
            int oidx = ListUtil.indexOf(objectInfo, info);
            if (oidx != -1) {
                objectInfo = ArrayUtil.splice(objectInfo, oidx, 1);
                return true;
            }

            // look for it in the uninteresting arrays
            oidx = indexOfUn(info);
            if (oidx != -1) {
                objectTileIds = ArrayUtil.splice(objectTileIds, oidx, 1);
                objectXs = ArrayUtil.splice(objectXs, oidx, 1);
                objectYs = ArrayUtil.splice(objectYs, oidx, 1);
                return true;
            }

            return false;
        }

        /**
         * Returns the index of the specified object in the uninteresting
         * arrays or -1 if it is not in this section as an uninteresting
         * object.
         */
        protected int indexOfUn (ObjectInfo info)
        {
            for (int ii = 0; ii < objectTileIds.length; ii++) {
                if (objectTileIds[ii] == info.tileId &&
                    objectXs[ii] == info.x && objectYs[ii] == info.y) {
                    return ii;
                }
            }
            return -1;
        }

        public void getAllObjects (ArrayList<ObjectInfo> list) {
            for (ObjectInfo info : objectInfo) {
                list.add(info);
            }
            for (int ii = 0; ii < objectTileIds.length; ii++) {
                int x = objectXs[ii], y = objectYs[ii];
                list.add(new ObjectInfo(objectTileIds[ii], x, y));
            }
        }

        public void getObjects (Rectangle region, ObjectSet set) {
            // first look for intersecting interesting objects
            for (ObjectInfo info : objectInfo) {
                if (region.contains(info.x, info.y)) {
                    set.insert(info);
                }
            }

            // now look for intersecting non-interesting objects
            for (int ii = 0; ii < objectTileIds.length; ii++) {
                int x = objectXs[ii], y = objectYs[ii];
                if (region.contains(x, y)) {
                    set.insert(new ObjectInfo(objectTileIds[ii], x, y));
                }
            }
        }

        /**
         * Returns true if this section contains no data beyond the default.
         * Used when saving a sparse scene: we omit blank sections.
         */
        public boolean isBlank ()
        {
            if ((objectTileIds.length != 0) || (objectInfo.length != 0)) {
                return false;
            }
            for (int baseTileId : baseTileIds) {
                if (baseTileId != 0) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public Section clone () {
            try {
                Section section = (Section)super.clone();
                section.baseTileIds = baseTileIds.clone();
                section.objectTileIds = objectTileIds.clone();
                section.objectXs = objectXs.clone();
                section.objectYs = objectYs.clone();
                section.objectInfo = new ObjectInfo[objectInfo.length];
                for (int ii = 0; ii < objectInfo.length; ii++) {
                    section.objectInfo[ii] = objectInfo[ii].clone();
                }
                return section;
            } catch (CloneNotSupportedException cnse) {
                throw new AssertionError(cnse);
            }
        }

        @Override
        public String toString () {
            if (width == 0 || baseTileIds == null) {
                return "<no bounds>";
            } else {
                return String.format("%sx%s+%s:%s:%s", width, baseTileIds.length / width, x, y,
                    objectInfo.length, objectTileIds.length);
            }
        }
    }

    /** The dimensions of a section of our scene. */
    public short swidth, sheight;

    /** The tileset to use when we have no tile data. */
    public int defTileSet = 0;

    /**
     * Creates a scene model with the specified bounds.
     *
     * @param swidth the width of a single section (in tiles).
     * @param sheight the height of a single section (in tiles).
     */
    public SparseMisoSceneModel (int swidth, int sheight)
    {
        this.swidth = (short)swidth;
        this.sheight = (short)sheight;
    }

    /**
     * Creates a blank model suitable for unserialization.
     */
    public SparseMisoSceneModel ()
    {
    }

    /**
     * Adds all interesting {@link ObjectInfo} records in this scene to
     * the supplied list.
     */
    public void getInterestingObjects (ArrayList<ObjectInfo> list)
    {
        for (Iterator<Section> iter = getSections(); iter.hasNext(); ) {
            Section sect = iter.next();
            for (ObjectInfo element : sect.objectInfo) {
                list.add(element);
            }
        }
    }

    /**
     * Adds all {@link ObjectInfo} records in this scene to the supplied list.
     */
    public void getAllObjects (ArrayList<ObjectInfo> list)
    {
        for (Iterator<Section> iter = getSections(); iter.hasNext(); ) {
            iter.next().getAllObjects(list);
        }
    }

    /**
     * Informs the supplied visitor of each object in this scene.
     */
    public void visitObjects (ObjectVisitor visitor)
    {
        visitObjects(visitor, false);
    }

    /**
     * Informs the supplied visitor of each object in this scene.
     *
     * @param interestingOnly if true, only the interesting objects will
     * be visited.
     */
    public void visitObjects (ObjectVisitor visitor, boolean interestingOnly)
    {
        for (Iterator<Section> iter = getSections(); iter.hasNext(); ) {
            Section sect = iter.next();
            for (ObjectInfo oinfo : sect.objectInfo) {
                visitor.visit(oinfo);
            }
            if (!interestingOnly) {
                for (int oo = 0; oo < sect.objectTileIds.length; oo++) {
                    ObjectInfo info = new ObjectInfo();
                    info.tileId = sect.objectTileIds[oo];
                    info.x = sect.objectXs[oo];
                    info.y = sect.objectYs[oo];
                    visitor.visit(info);
                }
            }
        }
    }

    @Override
    public int getBaseTileId (int col, int row)
    {
        Section sec = getSection(col, row, false);
        return (sec == null) ? -1 : sec.getBaseTileId(col, row);
    }

    @Override
    public boolean setBaseTile (int fqBaseTileId, int col, int row)
    {
        getSection(col, row, true).setBaseTile(col, row, fqBaseTileId);
        return true;
    }

    @Override
    public void setDefaultBaseTileSet (int tileSetId)
    {
        defTileSet = tileSetId;
    }

    @Override
    public int getDefaultBaseTileSet ()
    {
        return defTileSet;
    }

    @Override
    public void getObjects (Rectangle region, ObjectSet set)
    {
        int minx = MathUtil.floorDiv(region.x, swidth)*swidth;
        int maxx = MathUtil.floorDiv(region.x+region.width-1, swidth)*swidth;
        int miny = MathUtil.floorDiv(region.y, sheight)*sheight;
        int maxy = MathUtil.floorDiv(region.y+region.height-1, sheight)*sheight;
        for (int yy = miny; yy <= maxy; yy += sheight) {
            for (int xx = minx; xx <= maxx; xx += swidth) {
                Section sec = getSection(xx, yy, false);
                if (sec != null) {
                    sec.getObjects(region, set);
                }
            }
        }
    }

    @Override
    public boolean addObject (ObjectInfo info)
    {
        return getSection(info.x, info.y, true).addObject(info);
    }

    @Override
    public void updateObject (ObjectInfo info)
    {
        // not efficient, but this is only done in editing situations
        removeObject(info);
        addObject(info);
    }

    @Override
    public boolean removeObject (ObjectInfo info)
    {
        Section sec = getSection(info.x, info.y, false);
        if (sec != null) {
            return sec.removeObject(info);
        } else {
            return false;
        }
    }

    /**
     * Don't call this method! This is only public so that the scene
     * parser can construct a scene from raw data. If only Java supported
     * class friendship.
     */
    public void setSection (Section section)
    {
        _sections.put(key(section.x, section.y), section);
    }

    /**
     * Don't call this method! This is only public so that the scene
     * writer can generate XML from the raw scene data.
     */
    public Iterator<Section> getSections ()
    {
        return _sections.values().iterator();
    }

    @Override
    protected void toString (StringBuilder buf)
    {
        super.toString(buf);
        buf.append(", sections=" +
                   StringUtil.toString(_sections.values().iterator()));
    }

    /**
     * Returns the key for the specified section.
     */
    protected final int key (int x, int y)
    {
        int sx = MathUtil.floorDiv(x, swidth);
        int sy = MathUtil.floorDiv(y, sheight);
        return (sx << 16) | (sy & 0xFFFF);
    }

    /** Returns the section for the specified tile coordinate. */
    protected final Section getSection (int x, int y, boolean create)
    {
        int key = key(x, y);
        Section sect = _sections.get(key);
        if (sect == null && create) {
            short sx = (short)(MathUtil.floorDiv(x, swidth)*swidth);
            short sy = (short)(MathUtil.floorDiv(y, sheight)*sheight);
            _sections.put(key, sect = new Section(sx, sy, swidth, sheight));
//             Log.info("Created new section " + sect + ".");
        }
        return sect;
    }

    @Override
    public SparseMisoSceneModel clone ()
    {
        SparseMisoSceneModel model = (SparseMisoSceneModel)super.clone();
        model._sections = StreamableHashMap.newMap();
        for (Iterator<Section> iter = getSections(); iter.hasNext(); ) {
            Section sect = iter.next();
            model.setSection(sect.clone());
        }
        return model;
    }

    /** Contains our sections in row major order. */
    protected StreamableHashMap<Integer, Section> _sections = StreamableHashMap.newMap();
}
