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

package com.threerings.admin.client;

import java.lang.reflect.Field;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import com.samskivert.swing.GroupLayout;
import com.samskivert.swing.HGroupLayout;
import com.samskivert.swing.Spacer;

import com.threerings.presents.dobj.DObject;
import com.threerings.presents.util.PresentsContext;

/**
 * Provides "editing" of boolean fields.
 */
public class BooleanFieldEditor extends FieldEditor
{
    public BooleanFieldEditor (PresentsContext ctx, Field field, DObject object)
    {
        super(ctx, field, object);

        JPanel jpan = new JPanel(new HGroupLayout(HGroupLayout.STRETCH));

        // add a checkbox to display the field value
        jpan.add(_value = new JCheckBox(), GroupLayout.FIXED);
        // add a spacer so that clicks to the right of the checkbox
        // don't toggle it
        jpan.add(new Spacer(1, 1));
        _value.addActionListener(this);

        add(jpan);

        // we want to let the user know if they remove focus from a text
        // box without changing a field that it's not saved
        _value.addFocusListener(this);
    }

    @Override
    protected Object getDisplayValue ()
        throws Exception
    {
        return Boolean.valueOf(_value.isSelected());
    }

    @Override
    protected void displayValue (Object value)
    {
        _value.setSelected(Boolean.TRUE.equals(value));
    }

    protected JCheckBox _value;
}
