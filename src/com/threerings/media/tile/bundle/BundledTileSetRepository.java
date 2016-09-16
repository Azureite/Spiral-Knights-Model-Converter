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

package com.threerings.media.tile.bundle;

import java.util.HashMap;
import java.util.Iterator;

import com.google.common.collect.Maps;

import com.samskivert.io.PersistenceException;
import com.samskivert.util.HashIntMap;
import com.samskivert.util.IntMap;

import com.threerings.resource.ResourceBundle;
import com.threerings.resource.ResourceManager;

import com.threerings.media.image.ImageManager;
import com.threerings.media.tile.IMImageProvider;
import com.threerings.media.tile.NoSuchTileSetException;
import com.threerings.media.tile.TileSet;
import com.threerings.media.tile.TileSetRepository;

import static com.threerings.media.Log.log;

/**
 * Loads tileset data from a set of resource bundles.
 *
 * @see ResourceManager
 */
public class BundledTileSetRepository
    implements TileSetRepository
{
    /**
     * Constructs a repository which will obtain its resource set from the
     * supplied resource manager.
     *
     * @param rmgr the resource manager from which to obtain our resource
     * set.
     * @param imgr the image manager through which we will configure the
     * tile sets to load their images, or <code>null</code> if image tiles
     * should not be loaded (only the tile metadata)
     * @param name the name of the resource set from which we will be
     * loading our tile data.
     */
    public BundledTileSetRepository (final ResourceManager rmgr,
                                     final ImageManager imgr,
                                     final String name)
    {
        _imgr = imgr;

        // unpack our bundles in the background
        new Thread(new Runnable() {
            public void run () {
                initBundles(rmgr, name);
            }
        }).start();
    }

    /**
     * Initializes our bundles,
     */
    protected void initBundles (ResourceManager rmgr, String name)
    {
        // first we obtain the resource set from which we will load up our
        // tileset bundles
        ResourceBundle[] rbundles = rmgr.getResourceSet(name);

        // sanity check
        if (rbundles == null) {
            log.warning("Unable to fetch tileset resource set " +
                        "[name=" + name + "]. Perhaps it's not defined " +
                        "in the resource manager config?");
            return;
        }

        HashIntMap<TileSet> idmap = new HashIntMap<TileSet>();
        HashMap<String, Integer> namemap = Maps.newHashMap();

        // iterate over the resource bundles in the set, loading up the
        // tileset bundles in each resource bundle
        for (ResourceBundle rbundle : rbundles) {
            addBundle(idmap, namemap, rbundle);
        }

        // fill in our bundles array and wake up any waiters
        synchronized (this) {
            _idmap = idmap;
            _namemap = namemap;
            notifyAll();
        }
    }

    /**
     * Registers the bundle with the tileset repository, overriding any
     * bundle with the same id or name.
     */
    public void addBundle (ResourceBundle bundle)
    {
        addBundle(_idmap, _namemap, bundle);
    }

    /**
     * Extracts the tileset bundle from the supplied resource bundle
     * and registers it.
     */
    protected void addBundle (HashIntMap<TileSet> idmap, HashMap<String, Integer> namemap,
        ResourceBundle bundle)
    {
        try {
            TileSetBundle tsb = BundleUtil.extractBundle(bundle);
            // initialize it and add it to the list
            tsb.init(bundle);
            addBundle(idmap, namemap, tsb);

        } catch (Exception e) {
            log.warning("Unable to load tileset bundle '" + BundleUtil.METADATA_PATH +
                        "' from resource bundle [rbundle=" + bundle + "].", e);
        }
    }

    /**
     * Adds the tilesets in the supplied bundle to our tileset mapping
     * tables. Any tilesets with the same name or id will be overwritten.
     */
    protected void addBundle (HashIntMap<TileSet> idmap, HashMap<String, Integer> namemap,
                              TileSetBundle bundle)
    {
        IMImageProvider improv = (_imgr == null) ?
            null : new IMImageProvider(_imgr, bundle);

        // map all of the tilesets in this bundle
        for (IntMap.IntEntry<TileSet> entry : bundle.intEntrySet()) {
            Integer tsid = entry.getKey();
            TileSet tset = entry.getValue();
            tset.setImageProvider(improv);
            idmap.put(tsid.intValue(), tset);
            namemap.put(tset.getName(), tsid);
        }
    }

    // documentation inherited from interface
    public Iterator<Integer> enumerateTileSetIds ()
        throws PersistenceException
    {
        waitForBundles();
        return _idmap.keySet().iterator();
    }

    // documentation inherited from interface
    public Iterator<TileSet> enumerateTileSets ()
        throws PersistenceException
    {
        waitForBundles();
        return _idmap.values().iterator();
    }

    // documentation inherited from interface
    public TileSet getTileSet (int tileSetId)
        throws NoSuchTileSetException, PersistenceException
    {
        waitForBundles();
        TileSet tset = _idmap.get(tileSetId);
        if (tset == null) {
            throw new NoSuchTileSetException(tileSetId);
        }
        return tset;
    }

    // documentation inherited from interface
    public int getTileSetId (String setName)
        throws NoSuchTileSetException, PersistenceException
    {
        waitForBundles();
        Integer tsid = _namemap.get(setName);
        if (tsid != null) {
            return tsid.intValue();
        }
        throw new NoSuchTileSetException(setName);
    }

    // documentation inherited from interface
    public TileSet getTileSet (String setName)
        throws NoSuchTileSetException, PersistenceException
    {
        waitForBundles();
        Integer tsid = _namemap.get(setName);
        if (tsid != null) {
            return getTileSet(tsid.intValue());
        }
        throw new NoSuchTileSetException(setName);
    }

    /** Used to allow bundle unpacking to proceed asynchronously. */
    protected synchronized void waitForBundles ()
    {
        while (_idmap == null) {
            try {
                wait();
            } catch (InterruptedException ie) {
                log.warning("Interrupted waiting for bundles " + ie);
            }
        }
    }

    /** The image manager via which we load our images. */
    protected ImageManager _imgr;

    /** A mapping from tileset id to tileset. */
    protected HashIntMap<TileSet> _idmap;

    /** A mapping from tileset name to tileset id. */
    protected HashMap<String, Integer> _namemap;
}
