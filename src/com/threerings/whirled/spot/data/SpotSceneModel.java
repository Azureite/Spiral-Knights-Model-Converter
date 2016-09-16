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

import com.samskivert.util.ArrayUtil;
import com.samskivert.util.ListUtil;

import com.threerings.io.SimpleStreamableObject;

import com.threerings.whirled.data.AuxModel;
import com.threerings.whirled.data.SceneModel;

/**
 * The spot scene model extends the standard scene model with information on portals. Portals are
 * referenced by an identifier, unique within the scene and unchanging, so that portals can stably
 * reference the target portal in the scene to which they connect.
 */
public class SpotSceneModel extends SimpleStreamableObject
    implements AuxModel
{
    /** An array containing all portals in this scene. */
    public Portal[] portals = new Portal[0];

    /** The portal id of the default entrance to this scene. If a body enters the scene without
     * coming from another scene, this is the portal at which they would appear. */
    public int defaultEntranceId = -1;

    /**
     * Adds a portal to this scene model.
     */
    public void addPortal (Portal portal)
    {
        portals = ArrayUtil.append(portals, portal);
    }

    /**
     * Removes a portal from this model.
     */
    public void removePortal (Portal portal)
    {
        int pidx = ListUtil.indexOf(portals, portal);
        if (pidx != -1) {
            portals = ArrayUtil.splice(portals, pidx, 1);
        }
    }

    @Override
    public SpotSceneModel clone ()
        throws CloneNotSupportedException
    {
        SpotSceneModel model = (SpotSceneModel)super.clone();
        // clone our portals individually
        model.portals = new Portal[portals.length];
        for (int ii = 0, ll = portals.length; ii < ll; ii++) {
            model.portals[ii] = portals[ii].clone();
        }
        return model;
    }

    /**
     * Locates and returns the {@link SpotSceneModel} among the auxiliary scene models associated
     * with the supplied scene model. <code>null</code> is returned if no spot scene model could
     * be found.
     */
    public static SpotSceneModel getSceneModel (SceneModel model)
    {
        for (AuxModel auxModel : model.auxModels) {
            if (auxModel instanceof SpotSceneModel) {
                return (SpotSceneModel)auxModel;
            }
        }
        return null;
    }
}
