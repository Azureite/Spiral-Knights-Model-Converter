//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
// http://code.google.com/p/narya/
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

package com.threerings.admin.data;

import java.lang.reflect.Field;

import javax.swing.JPanel;
import com.threerings.presents.dobj.DObject;
import com.threerings.presents.util.PresentsContext;

import com.threerings.admin.client.AsStringFieldEditor;
import com.threerings.admin.client.BooleanFieldEditor;

/**
 * Base class for runtime config distributed objects.  Used to allow
 * config objects to supply custom object editing UI.
 */
public class ConfigObject extends DObject
{
    /**
     * Returns the editor panel for the specified field.
     */
    public JPanel getEditor (PresentsContext ctx, Field field)
    {
        if (field.getType().equals(Boolean.TYPE)) {
            return new BooleanFieldEditor(ctx, field, this);
        } else {
            return new AsStringFieldEditor(ctx, field, this);
        }
    }
}
