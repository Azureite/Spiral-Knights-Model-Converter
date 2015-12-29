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

package com.threerings.whirled.spot.data;

import java.util.Iterator;

import com.samskivert.util.HashIntMap;

import static com.threerings.whirled.spot.Log.log;

/**
 * An implementation of the {@link SpotScene} interface.
 */
public class SpotSceneImpl
    implements SpotScene
{
    /**
     * Creates an instance that will obtain data from the supplied spot scene model.
     */
    public SpotSceneImpl (SpotSceneModel smodel)
    {
        _smodel = smodel;
        readPortals();
    }

    protected void readPortals ()
    {
        _portals.clear();
        for (Portal port : _smodel.portals) {
            _portals.put(port.portalId, port);
        }
    }

    /**
     * Instantiates a blank scene implementation.
     */
    public SpotSceneImpl ()
    {
        _smodel = new SpotSceneModel();
    }

    // documentation inherited from interface
    public Portal getPortal (int portalId)
    {
        if (portalId == -1) {
            portalId = _smodel.defaultEntranceId;
        }
        return _portals.get(portalId);
    }

    // documentation inherited from interface
    public int getPortalCount ()
    {
        return _portals.size();
    }

    // documentation inherited from interface
    public Iterator<Portal> getPortals ()
    {
        return _portals.values().iterator();
    }

    // documentation inherited from interface
    public short getNextPortalId ()
    {
        // compute a new portal id for our friend the portal
        for (short ii = 1; ii < MAX_PORTAL_ID; ii++) {
            if (!_portals.containsKey(ii)) {
                return ii;
            }
        }
        return (short)-1;
    }

    // documentation inherited from interface
    public Portal getDefaultEntrance ()
    {
        return getPortal(-1); // -1 is a shortcut meaning "default"
    }

    // documentation inherited from interface
    public void addPortal (Portal portal)
    {
        if (portal.portalId <= 0) {
            log.warning("Refusing to add zero-id portal", "scene", this, "portal", portal);
            return;
        }

        // add it to our model
        _smodel.addPortal(portal);

        // and slap it into our table
        _portals.put(portal.portalId, portal);
    }

    // documentation inherited from interface
    public void removePortal (Portal portal)
    {
        // remove the portal from our mapping
        _portals.remove(portal.portalId);

        // remove it from the model
        _smodel.removePortal(portal);
    }

    /**
     * Used when we're being parsed from an XML scene model.
     */
    public void setDefaultEntranceId (int defaultEntranceId)
    {
        _smodel.defaultEntranceId = defaultEntranceId;
    }

    // documentation inherited from interface
    public void setDefaultEntrance (Portal portal)
    {
        _smodel.defaultEntranceId = (portal == null) ? -1 : portal.portalId;
    }

    /**
     * This should be called if a scene update was received that caused our underlying scene model
     * to change.
     */
    public void updateReceived ()
    {
        readPortals();
    }

    /** A casted reference to our scene model. */
    protected SpotSceneModel _smodel;

    /** A mapping from portal id to portal. */
    protected HashIntMap<Portal> _portals = new HashIntMap<Portal>();

    /** We don't allow more than ~32k portals in a scene. Things would slow down *way* before we
     * got there. */
    protected static final int MAX_PORTAL_ID = Short.MAX_VALUE;
}
