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

package com.threerings.miso.client;


import java.util.ArrayList;
import java.util.Comparator;

import java.awt.Graphics2D;

import com.google.common.collect.Lists;

import com.samskivert.util.SortableArrayList;

import com.threerings.media.sprite.Sprite;
import com.threerings.media.tile.ObjectTile;

import static com.threerings.media.Log.log;

/**
 * The dirty item list keeps track of dirty sprites and object tiles in a scene.
 */
public class DirtyItemList
{
    /**
     * Creates a dirt item list that will handle dirty items for the specified view.
     */
    public DirtyItemList ()
    {
    }

    /**
     * Appends the dirty sprite at the given coordinates to the dirty item list.
     *
     * @param sprite the dirty sprite itself.
     * @param tx the sprite's x tile position.
     * @param ty the sprite's y tile position.
     */
    public void appendDirtySprite (Sprite sprite, int tx, int ty)
    {
        DirtyItem item = getDirtyItem();
        item.init(sprite, tx, ty);
        _items.add(item);
    }

    /**
     * Appends the dirty object tile at the given coordinates to the dirty item list.
     *
     * @param scobj the scene object that is dirty.
     */
    public void appendDirtyObject (SceneObject scobj)
    {
        DirtyItem item = getDirtyItem();
        item.init(scobj, scobj.info.x, scobj.info.y);
        _items.add(item);
    }

    /**
     * Returns the dirty item at the given index in the list.
     */
    public DirtyItem get (int idx)
    {
        return _items.get(idx);
    }

    /**
     * Returns an array of the {@link DirtyItem} objects in the list sorted in proper rendering
     * order.
     */
    public void sort ()
    {
        int size = size();

        if (DEBUG_SORT) {
            log.info("Sorting dirty item list", "size", size);
        }

        // if we've only got one item, we need to do no sorting
        if (size > 1) {
            // get items sorted by increasing origin x-coordinate
            _xitems.addAll(_items);
            _xitems.sort(ORIGIN_X_COMP);
            if (DEBUG_SORT) {
                log.info("Sorted by x-origin", "items", toString(_xitems));
            }

            // get items sorted by increasing origin y-coordinate
            _yitems.addAll(_items);
            _yitems.sort(ORIGIN_Y_COMP);
            if (DEBUG_SORT) {
                log.info("Sorted by y-origin", "items", toString(_yitems));
            }

            // sort the items according to the depth of the rear-most tile
            _ditems.addAll(_items);
            _ditems.sort(REAR_DEPTH_COMP);
            if (DEBUG_SORT) {
                log.info("Sorted by rear-depth", "items", toString(_ditems));
            }

            // now insertion sort the items from back to front into the render-sorted array
            _items.clear();
          POS_LOOP:
            for (int ii = 0; ii < size; ii++) {
                DirtyItem item = _ditems.get(ii);
                for (int rr = _items.size()-1; rr >= 0; rr--) {
                    DirtyItem pitem = _items.get(rr);
                    // if we render in front of this item, insert
                    // ourselves immediately following it
                    if (_rcomp.compare(item, pitem) > 0) {
                        _items.add(rr+1, item);
                        continue POS_LOOP;
                    }
                }
                // we don't render in front of anyone, so we go at the front of the list
                _items.add(0, item);
            }

            // clear out our temporary arrays
            _xitems.clear();
            _yitems.clear();
            _ditems.clear();
        }

        if (DEBUG_SORT) {
            log.info("Sorted for render", "items", toString(_items));
            for (int ii = 0, ll = _items.size()-1; ii < ll; ii++) {
                DirtyItem a = _items.get(ii);
                DirtyItem b = _items.get(ii+1);
                if (_rcomp.compare(a, b) > 0) {
                    log.warning("Invalid ordering", "a", a, "b", b);
                }
            }
        }
    }

    /**
     * Paints all the dirty items in this list using the supplied graphics context. The items are
     * removed from the dirty list after being painted and the dirty list ends up empty.
     */
    public void paintAndClear (Graphics2D gfx)
    {
        int icount = _items.size();
        for (int ii = 0; ii < icount; ii++) {
            DirtyItem item = _items.get(ii);
            item.paint(gfx);
            item.clear();
            _freelist.add(item);
        }
        _items.clear();
    }

    /**
     * Clears out any items that were in this list.
     */
    public void clear ()
    {
        for (int icount = _items.size(); icount > 0; icount--) {
            DirtyItem item = _items.remove(0);
            item.clear();
            _freelist.add(item);
        }
    }

    /**
     * Returns the number of items in the dirty item list.
     */
    public int size ()
    {
        return _items.size();
    }

    /**
     * Obtains a new dirty item instance, reusing an old one if possible or creating a new one
     * otherwise.
     */
    protected DirtyItem getDirtyItem ()
    {
        if (_freelist.size() > 0) {
            return _freelist.remove(0);
        } else {
            return new DirtyItem();
        }
    }

    /**
     * Returns an abbreviated string representation of the given dirty item describing only its
     * origin coordinates and render priority. Intended for debugging purposes.
     */
    protected static String toString (DirtyItem a)
    {
        StringBuilder buf = new StringBuilder("[");
        toString(buf, a);
        return buf.append("]").toString();
    }

    /**
     * Returns an abbreviated string representation of the two given dirty items. See
     * {@link #toString(DirtyItem)}.
     */
    protected static String toString (DirtyItem a, DirtyItem b)
    {
        StringBuilder buf = new StringBuilder("[");
        toString(buf, a);
        toString(buf, b);
        return buf.append("]").toString();
    }

    /**
     * Returns an abbreviated string representation of the given dirty items. See
     * {@link #toString(DirtyItem)}.
     */
    protected static String toString (SortableArrayList<DirtyItem> items)
    {
        StringBuilder buf = new StringBuilder();
        buf.append("[");
        for (int ii = 0; ii < items.size(); ii++) {
            DirtyItem item = items.get(ii);
            toString(buf, item);
            if (ii < (items.size() - 1)) {
                buf.append(", ");
            }
        }
        return buf.append("]").toString();
    }

    /** Helper function for {@link #toString(DirtyItem)}. */
    protected static void toString (StringBuilder buf, DirtyItem item)
    {
        buf.append("(o:+").append(item.ox).append("+").append(item.oy);
        buf.append(" p:").append(item.getRenderPriority()).append(")");
    }

    /**
     * A class to hold the items inserted in the dirty list along with all of the information
     * necessary to render their dirty regions to the target graphics context when the time comes
     * to do so.
     */
    public class DirtyItem
    {
        /** The dirtied object; one of either a sprite or an object tile. */
        public Object obj;

        /** The origin tile coordinates. */
        public int ox, oy;

        /** The leftmost tile coordinates. */
        public int lx, ly;

        /** The rightmost tile coordinates. */
        public int rx, ry;

        /**
         * Initializes a dirty item.
         */
        public void init (Object obj, int x, int y) {
            this.obj = obj;
            this.ox = x;
            this.oy = y;

            // calculate the item's leftmost and rightmost tiles; note that normal (Non-MultiTile)
            // sprites occupy only a single tile, so leftmost and rightmost tiles are equivalent
            lx = rx = ox;
            ly = ry = oy;
            if (obj instanceof SceneObject) {
                ObjectTile tile = ((SceneObject)obj).tile;
                lx -= (tile.getBaseWidth() - 1);
                ry -= (tile.getBaseHeight() - 1);
            } else if (obj instanceof MultiTileSprite) {
                MultiTileSprite mts = (MultiTileSprite)obj;
                lx -= (mts.getBaseWidth() - 1);
                ry -= (mts.getBaseHeight() - 1);
            }
        }

        /**
         * Paints the dirty item to the given graphics context. Only the portion of the item that
         * falls within the given dirty rectangle is actually drawn.
         */
        public void paint (Graphics2D gfx) {
            if (obj instanceof Sprite) {
                ((Sprite)obj).paint(gfx);
            } else {
                ((SceneObject)obj).paint(gfx);
            }
        }

        /**
         * Returns the "depth" of our rear-most tile.
         */
        public int getRearDepth () {
            return ry + lx;
        }

        /**
         * Returns the render priority for this dirty item. It will be zero unless this is a
         * display object which may have a custom render priority.
         */
        public int getRenderPriority () {
            if (obj instanceof SceneObject) {
                return ((SceneObject)obj).getPriority();
            } else {
                return 0;
            }
        }

        /**
         * Releases all references held by this dirty item so that it doesn't inadvertently hold
         * on to any objects while waiting to be reused.
         */
        public void clear () {
            obj = null;
        }

        @Override
        public boolean equals (Object other) {
            // we're never equal to something that's not our kind
            if (!(other instanceof DirtyItem)) {
                return false;
            }

            // sprites are equivalent if they're the same sprite
            DirtyItem b = (DirtyItem)other;
            return obj.equals(b.obj);
        }

        @Override
        public int hashCode () {
            return obj.hashCode();
        }

        @Override
        public String toString () {
            StringBuilder buf = new StringBuilder();
            buf.append("[obj=").append(obj);
            buf.append(", ox=").append(ox);
            buf.append(", oy=").append(oy);
            buf.append(", lx=").append(lx);
            buf.append(", ly=").append(ly);
            buf.append(", rx=").append(rx);
            buf.append(", ry=").append(ry);
            return buf.append("]").toString();
        }
    }

    /**
     * A comparator class for use in sorting dirty items in ascending origin x- or y-axis
     * coordinate order.
     */
    protected static class OriginComparator implements Comparator<DirtyItem>
    {
        /**
         * Constructs an origin comparator that sorts dirty items in ascending order based on
         * their origin coordinate on the given axis.
         */
        public OriginComparator (int axis) {
            _axis = axis;
        }

        // documentation inherited
        public int compare (DirtyItem da, DirtyItem db) {
            // if they don't overlap, sort them normally
            if (_axis == X_AXIS) {
                if (da.ox != db.ox) {
                    return da.ox - db.ox;
                }
            } else {
                if (da.oy != db.oy) {
                    return da.oy - db.oy;
                }
            }

            // if they do overlap, incorporate render priority; assume
            // non-display objects have a render priority of zero
            return da.getRenderPriority() - db.getRenderPriority();
        }

        /** The axis this comparator sorts on. */
        protected int _axis;
    }

    /**
     * A comparator class for use in sorting the dirty sprites and objects in a scene in ascending
     * x- and y-coordinate order suitable for rendering in the isometric view with proper visual
     * results.
     */
    protected class RenderComparator implements Comparator<DirtyItem>
    {
        // documentation inherited
        public int compare (DirtyItem da, DirtyItem db) {
            // if the two objects are scene objects and they overlap, we
            // compare them solely based on their human assigned priority
            if ((da.obj instanceof SceneObject) &&
                (db.obj instanceof SceneObject)) {
                SceneObject soa = (SceneObject)da.obj;
                SceneObject sob = (SceneObject)db.obj;
                if (soa.objectFootprintOverlaps(sob)) {
                    int result = soa.getPriority() - sob.getPriority();
                    if (DEBUG_COMPARE) {
                        String items = DirtyItemList.toString(da, db);
                        log.info("compare: overlapping", "result", result, "items", items);
                    }
                    return result;
                }
            }

            // check for partitioning objects on the y-axis
            int result = comparePartitioned(Y_AXIS, da, db);
            if (result != 0) {
                if (DEBUG_COMPARE) {
                    String items = DirtyItemList.toString(da, db);
                    log.info("compare: Y-partitioned", "result", result, "items", items);
                }
                return result;
            }

            // check for partitioning objects on the x-axis
            result = comparePartitioned(X_AXIS, da, db);
            if (result != 0) {
                if (DEBUG_COMPARE) {
                    String items = DirtyItemList.toString(da, db);
                    log.info("compare: X-partitioned", "result", result, "items", items);
                }
                return result;
            }

            // use normal iso-ordering check
            result = compareNonPartitioned(da, db);
            if (DEBUG_COMPARE) {
                String items = DirtyItemList.toString(da, db);
                log.info("compare: non-partitioned", "result", result, "items", items);
            }

            return result;
        }

        /**
         * Returns whether two dirty items have a partitioning object between them on the given
         * axis.
         */
        protected int comparePartitioned (int axis, DirtyItem da, DirtyItem db) {
            // prepare for the partitioning check
            SortableArrayList<DirtyItem> sitems;
            Comparator<DirtyItem> comp;
            boolean swapped = false;
            switch (axis) {
            case X_AXIS:
                if (da.ox == db.ox) {
                    // can't be partitioned if there's no space between
                    return 0;
                }

                // order items for proper comparison
                if (da.ox > db.ox) {
                    DirtyItem temp = da;
                    da = db;
                    db = temp;
                    swapped = true;
                }

                // use the axis-specific sorted array
                sitems = _xitems;
                comp = ORIGIN_X_COMP;
                break;

            case Y_AXIS:
            default:
                if (da.oy == db.oy) {
                    // can't be partitioned if there's no space between
                    return 0;
                }

                // order items for proper comparison
                if (da.oy > db.oy) {
                    DirtyItem temp = da;
                    da = db;
                    db = temp;
                    swapped = true;
                }

                // use the axis-specific sorted array
                sitems = _yitems;
                comp = ORIGIN_Y_COMP;
                break;
            }

            // get the bounding item indices and the number of potentially-partitioning dirty items
            int aidx = sitems.binarySearch(da, comp);
            int bidx = sitems.binarySearch(db, comp);
            int size = bidx - aidx - 1;

            // check each potentially partitioning item
            int startidx = aidx + 1, endidx = startidx + size;
            for (int pidx = startidx; pidx < endidx; pidx++) {
                DirtyItem dp = sitems.get(pidx);
                if (dp.obj instanceof Sprite) {
                    // sprites can't partition things
                    continue;
                } else if ((dp.obj == da.obj) ||
                           (dp.obj == db.obj)) {
                    // can't be partitioned by ourselves
                    continue;
                }

                // perform the actual partition check for this object
                switch (axis) {
                case X_AXIS:
                    if (dp.ly >= da.ry &&
                        dp.ry <= db.ly &&
                        dp.lx >= da.rx &&
                        dp.rx <= db.lx) {
                        return (swapped) ? 1 : -1;
                    }
                    break;

                case Y_AXIS:
                default:
                    if (dp.lx <= db.ox &&
                        dp.rx >= da.lx &&
                        dp.ry >= da.oy &&
                        dp.oy <= db.ry) {
                        return (swapped) ? 1 : -1;
                    }
                    break;
                }
            }

            // no partitioning object found
            return 0;
        }

        /**
         * Compares the two dirty items assuming there are no partitioning objects between them.
         */
        protected int compareNonPartitioned (DirtyItem da, DirtyItem db) {
            if (da.ox == db.ox &&
                da.oy == db.oy) {
                if (da.equals(db)) {
                    // render level is equal if we're the same sprite
                    // or an object at the same location
                    return 0;
                }

                boolean aIsSprite = (da.obj instanceof Sprite);
                boolean bIsSprite = (db.obj instanceof Sprite);

                if (aIsSprite && bIsSprite) {
                    Sprite as = (Sprite)da.obj, bs = (Sprite)db.obj;
                    // we're comparing two sprites co-existing on the same
                    // tile, first check their render order
                    int rocomp = as.getRenderOrder() - bs.getRenderOrder();
                    if (rocomp != 0) {
                        return rocomp;
                    }
                    // next sort them by y-position
                    int ydiff = as.getY() - bs.getY();
                    if (ydiff != 0) {
                        return ydiff;
                    }
                    // if they're at the same height, just use hashCode()
                    // to establish a consistent arbitrary ordering
                    return (as.hashCode() - bs.hashCode());

                // otherwise, always put a sprite on top of a non-sprite
                } else if (aIsSprite) {
                    return 1;

                } else if (bIsSprite) {
                    return -1;
                }
            }

            // One is a multi-tile sprite and the two overlap - use render order if it helps.
            // Note - Ideally logic like this should probably apply regardless of the type of object
            // BUT considering the number of things that already exist that use this code, I suspect
            // it would break something...
            if ((da.obj instanceof MultiTileSprite || db.obj instanceof MultiTileSprite) &&
                (da.lx <= db.rx && da.rx >= db.lx && da.ry <= db.ly && da.ly >= db.ry)) {
                int aRender = (da.obj instanceof Sprite) ? ((Sprite)da.obj).getRenderOrder() : 0;
                int bRender = (db.obj instanceof Sprite) ? ((Sprite)db.obj).getRenderOrder() : 0;
                // we're comparing two sprites co-existing on the same
                // tile, first check their render order
                int rocomp = aRender - bRender;
                if (rocomp != 0) {
                    return rocomp;
                }
            }


            // otherwise use a consistent ordering for non-overlappers;
            // see narya/docs/miso/render_sort_diagram.png for more info
            if (db.lx <= da.ox && db.ry <= da.oy) {
                return 1;
            } else if (db.rx >= da.lx && db.ly >= da.ry) {
                return -1;
            } else {
                return da.oy - db.oy;
            }
        }
    }

    /** The list of dirty items. */
    protected SortableArrayList<DirtyItem> _items = new SortableArrayList<DirtyItem>();

    /** The list of dirty items sorted by x-position. */
    protected SortableArrayList<DirtyItem> _xitems = new SortableArrayList<DirtyItem>();

    /** The list of dirty items sorted by y-position. */
    protected SortableArrayList<DirtyItem> _yitems = new SortableArrayList<DirtyItem>();

    /** The list of dirty items sorted by rear-depth. */
    protected SortableArrayList<DirtyItem> _ditems = new SortableArrayList<DirtyItem>();

    /** The render comparator we'll use for our final, magical sort. */
    protected Comparator<DirtyItem> _rcomp = new RenderComparator();

    /** Unused dirty items. */
    protected ArrayList<DirtyItem> _freelist = Lists.newArrayList();

    /** Whether to log debug info when comparing pairs of dirty items. */
    protected static final boolean DEBUG_COMPARE = false;

    /** Whether to log debug info for the main dirty item sorting algorithm. */
    protected static final boolean DEBUG_SORT = false;

    /** Constants used to denote axis sorting constraints. */
    protected static final int X_AXIS = 0;
    protected static final int Y_AXIS = 1;

    /** The comparator used to sort dirty items in ascending origin x-coordinate order. */
    protected static final Comparator<DirtyItem> ORIGIN_X_COMP = new OriginComparator(X_AXIS);

    /** The comparator used to sort dirty items in ascending origin y-coordinate order. */
    protected static final Comparator<DirtyItem> ORIGIN_Y_COMP = new OriginComparator(Y_AXIS);

    /** The comparator used to sort dirty items in ascending "rear-depth" order. */
    protected static final Comparator<DirtyItem> REAR_DEPTH_COMP = new Comparator<DirtyItem>() {
        public int compare (DirtyItem o1, DirtyItem o2) {
            int depthDiff = (o1.getRearDepth() - o2.getRearDepth());
            if (depthDiff != 0) {
                return depthDiff;
            } else {
                // If there's a priority difference, break our tie on that.
                if (o1.obj instanceof SceneObject && o2.obj instanceof SceneObject) {
                    int priDiff = ((SceneObject)o1.obj).getPriority() -
                        ((SceneObject)o2.obj).getPriority();
                    if (priDiff != 0) {
                        return priDiff;
                    }
                }

                // Couldn't break the tie, fallback to the original result.
                return depthDiff;
            }
        }
    };
}
