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

import java.util.HashMap;

import java.awt.event.ActionEvent;

import javax.swing.Icon;

import com.google.common.collect.Maps;

import com.samskivert.util.StringUtil;

import com.samskivert.swing.RadialMenu;

import static com.threerings.miso.Log.log;

/**
 * Objects in scenes can be configured to generate action events. Those events are grouped into
 * types and an object action handler can be registered to handle all actions of a particular
 * type.
 */
public class ObjectActionHandler
{
    /**
     * Returns true if we should allow this object action, false if we should not.
     */
    public boolean actionAllowed (String action)
    {
        return true;
    }

    /**
     * Returns true if we should display the text for the action. By default this returns whether
     * the action is allowed or not, but can be overridden by subclasses. This is used to
     * completely hide actions that should not be visible without the proper privileges.
     */
    public boolean isVisible (String action)
    {
        return actionAllowed(action);
    }

    /**
     * Get the human readable object tip for the specified action.
     */
    public String getTipText (String action)
    {
        return action;
    }

    /**
     * Returns the tooltip icon for the specified action or null if the action has no tooltip
     * icon.
     */
    public Icon getTipIcon (String action)
    {
        return null;
    }

    /**
     * Return a {@link RadialMenu} or null if no menu needed.
     */
    public RadialMenu handlePressed (SceneObject sourceObject)
    {
        return null;
    }

    /**
     * Called when an action is generated for an object.
     */
    public void handleAction (SceneObject scobj, ActionEvent event)
    {
        log.warning("Unknown object action", "scobj", scobj, "action", event);
    }

    /**
     * Returns the type associated with this action command (which is mapped to a registered
     * object action handler) or the empty string if it has no type.
     */
    public static String getType (String command)
    {
        int cidx = StringUtil.isBlank(command) ? -1 : command.indexOf(':');
        return (cidx == -1) ? "" : command.substring(0, cidx);
    }

    /**
     * Returns the unqualified object action (minus the type, see {@link #getType}).
     */
    public static String getAction (String command)
    {
        int cidx = StringUtil.isBlank(command) ? -1 : command.indexOf(':');
        return (cidx == -1) ? command : command.substring(cidx+1);
    }

    /**
     * Creates an indicator for this type of object action.
     */
    public SceneObjectIndicator createIndicator (MisoScenePanel panel, String text, Icon icon)
    {
        return new SceneObjectTip(text, icon);
    }

    /**
     * Looks up the object action handler associated with the specified command.
     */
    public static ObjectActionHandler lookup (String command)
    {
        return _oahandlers.get(getType(command));
    }

    /**
     * Registers an object action handler which will be called when a user clicks on an object in
     * a scene that has an associated action.
     */
    public static void register (String prefix, ObjectActionHandler handler)
    {
        // make sure we know about potential funny business
        if (_oahandlers.containsKey(prefix)) {
            log.warning("Warning! Overwriting previous object action handler registration, all " +
                        "hell could shortly break loose", "prefix", prefix, "handler", handler);
        }
        _oahandlers.put(prefix, handler);
    }

    /**
     * Removes an object action handler registration.
     */
    public static void unregister (String prefix)
    {
        _oahandlers.remove(prefix);
    }

    /** Our registered object action handlers. */
    protected static HashMap<String, ObjectActionHandler> _oahandlers = Maps.newHashMap();
}
