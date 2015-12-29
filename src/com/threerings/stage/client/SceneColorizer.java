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

package com.threerings.stage.client;

import java.util.Iterator;
import java.util.Map;

import com.google.common.collect.Maps;

import com.threerings.media.image.ColorPository;
import com.threerings.media.image.Colorization;
import com.threerings.media.tile.TileSet;

import com.threerings.miso.data.ObjectInfo;

import com.threerings.stage.data.StageScene;

import static com.threerings.stage.Log.log;

/**
 * Handles colorization of object tiles in a scene.
 */
public class SceneColorizer implements TileSet.Colorizer
{
    /**
     * Creates a scene colorizer for the supplied scene.
     */
    public SceneColorizer (ColorPository cpos, StageScene scene)
    {
        _cpos = cpos;
        _scene = scene;

        // enumerate the color ids for all possible colorization classes
        for (Iterator<ColorPository.ClassRecord> iter = _cpos.enumerateClasses(); iter.hasNext(); ) {
            String cname = iter.next().name;
            _cids.put(cname, _cpos.enumerateColorIds(cname));
        }
    }

    /**
     * Set an auxiliary colorizer that overrides our colorizations.
     */
    public void setAuxiliary (TileSet.Colorizer aux)
    {
        _aux = aux;
    }

    /**
     * Obtains a colorizer for the supplied scene object.
     */
    public TileSet.Colorizer getColorizer (final ObjectInfo oinfo)
    {
        // if the object has no custom colorizations, return the default
        // colorizer
        if (oinfo.zations == 0) {
            return this;
        }

        // otherwise create a custom colorizer that returns this object's
        // custom colorization assignments
        return new TileSet.Colorizer() {
            public Colorization getColorization (int index, String zation) {
                int colorId = 0;
                switch (index) {
                case 0: colorId = oinfo.getPrimaryZation(); break;
                case 1: colorId = oinfo.getSecondaryZation(); break;
                case 2: colorId = oinfo.getTertiaryZation(); break;
                case 3: colorId = oinfo.getQuaternaryZation(); break;
                }

                if (colorId == 0) {
                    return SceneColorizer.this.getColorization(index, zation);
                } else {
                    return _cpos.getColorization(zation, colorId);
                }
            }
        };
    }

    // documentation inherited from interface TileSet.Colorizer
    public Colorization getColorization (int index, String zation)
    {
        // This method is called when an object in the scene has no colorization
        // of its own defined for a particular color class.
        if (_aux != null) {
            Colorization c = _aux.getColorization(index, zation);
            if (c != null) {
                return c;
            }
        }

        return _cpos.getColorization(zation, getColorId(zation));
    }

    /**
     * Get the colorId to use for the specified colorization.
     */
    public int getColorId (String zation)
    {
        // 1. We see if the scene contains a default color we should use.
        ColorPository.ClassRecord rec = _cpos.getClassRecord(zation);
        int colorId = _scene.getDefaultColor(rec.classId);
        if (colorId == -1) {
            // 2. If the scene does not contain a color, see if a default
            // is defined for that color class.
            ColorPository.ColorRecord def = rec.getDefault();
            if (def != null) {
                return def.colorId;
            }

            // 3. If there are no defaults whatsoever, just hash on the sceneId.
            int[] cids = _cids.get(zation);
            if (cids == null) {
                log.warning("Zoiks, have no colorizations for '" + zation + "'.");
                return -1;
            } else {
                colorId = cids[_scene.getZoneId() % cids.length];
            }
        }
        return colorId;
    }

    /** An auxiliary colorizer which may temporarily return
     * non-standard colorizations. */
    protected TileSet.Colorizer _aux;

    /** The entity from which we obtain colorization info. */
    protected ColorPository _cpos;

    /** The scene for which we're providing zations. */
    protected StageScene _scene;

    /** Contains our colorization class information. */
    protected Map<String, int[]> _cids = Maps.newHashMap();
}
