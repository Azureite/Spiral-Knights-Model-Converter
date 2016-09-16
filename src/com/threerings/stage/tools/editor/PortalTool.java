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

package com.threerings.stage.tools.editor;

import java.util.Iterator;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;

import com.threerings.util.DirectionCodes;
import com.threerings.util.DirectionUtil;

import com.threerings.miso.util.MisoUtil;

import com.threerings.whirled.spot.data.Portal;
import com.threerings.whirled.spot.tools.EditablePortal;

import com.threerings.stage.data.StageLocation;
import com.threerings.stage.data.StageScene;
import com.threerings.stage.tools.editor.util.ExtrasPainter;

/**
 * A tool for setting a portal.
 */
public class PortalTool extends MouseInputAdapter
    implements ExtrasPainter
{
    public void init (EditorScenePanel panel, int centerx, int centery)
    {
        _panel = panel;
        _scene = _panel.getScene();

        // adjust the center screen coordinate so that it is positioned at
        // the closest full coordinate where a portal may be placed
        Point p = _panel.getFullCoords(centerx, centery);

        // configure the portal with these full coordinates
        _portal = new EditablePortal();
        _portal.loc = new StageLocation(p.x, p.y, (byte)DirectionCodes.NORTH);

        // we want to listen to drags and clicks
        _panel.addMouseListener(this);
        _panel.addMouseMotionListener(this);
        _panel.addExtrasPainter(this);

        // now map that back to a screen coordinate
        _center = _panel.getScreenCoords(p.x, p.y);

        calculateOrientation(centerx, centery);
    }

    /**
     * When the mouse is dragged we update the current portal.
     */
    @Override
    public void mouseDragged (MouseEvent event)
    {
        calculateOrientation(event.getX(), event.getY());
    }

    /**
     * If button1 is released, we store the new portal, if button3: we
     * cancel.
     */
    @Override
    public void mouseReleased (MouseEvent event)
    {
        switch (event.getButton()) {
        case MouseEvent.BUTTON1:
            savePortal();
            dispose();
            break;

        case MouseEvent.BUTTON3:
            dispose();
            break;
        }
    }

    /**
     * Paint what the clusters would look like if we were to add them to
     * the scene.
     */
    public void paintExtras (Graphics2D gfx)
    {
        _panel.paintPortal(gfx, _portal);
    }

    /**
     * Updates the orientation of the portal based on the current mouse
     * position and potentially repaints.
     */
    protected void calculateOrientation (int x, int y)
    {
        // get the orientation towards the center from the current x/y
        int norient = MisoUtil.getProjectedIsoDirection(
            x, y, _center.x, _center.y);
        norient = DirectionUtil.getOpposite(norient);
        if (norient == DirectionCodes.NONE) {
            norient = DirectionCodes.NORTH;
        }

        // if our orientation changed, repaint
        StageLocation loc = (StageLocation) _portal.loc;
        if (norient != loc.orient) {
            loc.orient = (byte)norient;
            _panel.repaint();
        }
    }

    /**
     * Adds our portal to the scene.
     */
    protected void savePortal ()
    {
        // try to come up with a reasonable name
        byte orient = ((StageLocation) _portal.loc).orient;
        String dirname = DirectionUtil.toString(orient).toLowerCase();
        String name = dirname;
        for (int ii=1; portalNameExists(name); ii++) {
            name = dirname + ii;
        }
        _portal.name = name;

        // add the portal to the scene and pop up the editor dialog
        _portal.portalId = _scene.getNextPortalId();
        _scene.addPortal(_portal);
        _panel.editPortal(_portal);
    }

    /**
     * See if the portal name already exists.
     */
    protected boolean portalNameExists (String name)
    {
        Iterator<Portal> iter = _scene.getPortals();
        while (iter.hasNext()) {
            if (((EditablePortal)iter.next()).name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get rid of ourselves.
     */
    protected void dispose ()
    {
        _panel.removeMouseListener(this);
        _panel.removeMouseMotionListener(this);
        _panel.removeExtrasPainter(this);
    }

    /** The panel. */
    protected EditorScenePanel _panel;

    /** The scene. */
    protected StageScene _scene;

    /** Center coordinates. */
    protected Point _center;

    /** Our newly created portal. */
    protected EditablePortal _portal;
}
