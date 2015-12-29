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

/**
 * The editor model listener interface should be implemented by
 * classes that would like to be notified when the editor model is
 * changed.
 *
 * @see EditorModel
 */
public interface EditorModelListener
{
    /**
     * Called by the {@link EditorModel} when the model is changed.
     */
    public void modelChanged (int event);

    /** Notification event constants. */
    public static final int ACTION_MODE_CHANGED = 0;
    public static final int LAYER_INDEX_CHANGED = 1;
    public static final int TILE_CHANGED = 2;
}
