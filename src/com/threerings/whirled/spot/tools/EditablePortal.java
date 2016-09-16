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

package com.threerings.whirled.spot.tools;

import com.threerings.whirled.spot.data.Portal;

/**
 * An editable portal extends the standard portal with information needed
 * by the loader and editor.
 */
public class EditablePortal extends Portal
{
    /** The human-readable name of this portal. */
    public String name;

    /** The human-readable name of the scene to which this portal
     * links. */
    public String targetSceneName;

    /** The human-readable name of the portal to which this portal links
     * in its target scene. */
    public String targetPortalName;
}
