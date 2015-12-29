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

package com.threerings.cast.builder;

import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.samskivert.swing.HGroupLayout;
import com.samskivert.swing.VGroupLayout;

import com.threerings.cast.ComponentClass;

/**
 * The class editor displays a label and a slider that allow the user to
 * select the desired component for a given component class.
 */
public class ClassEditor extends JPanel implements ChangeListener
{
    /**
     * Constructs a class editor.
     */
    public ClassEditor (BuilderModel model, ComponentClass cclass, List<Integer> components)
    {
        _model = model;
        _components = components;
        _cclass = cclass;

        VGroupLayout vgl = new VGroupLayout(VGroupLayout.STRETCH);
        vgl.setOffAxisPolicy(VGroupLayout.STRETCH);
        setLayout(vgl);

        HGroupLayout hgl = new HGroupLayout();
        hgl.setJustification(HGroupLayout.LEFT);
        JPanel sub = new JPanel(hgl);

        sub.add(new JLabel(cclass.name + ": "));
        sub.add(_clabel = new JLabel("0"));
        add(sub);

        // create the slider allowing selection of available components
        int max = components.size() - 1;
        JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, max, 0);
        slider.setSnapToTicks(true);
        slider.addChangeListener(this);
        add(slider);

        // set the starting component for this class
        setSelectedComponent(0);
    }

    // documentation inherited
    public void stateChanged (ChangeEvent e)
    {
        JSlider source = (JSlider)e.getSource();
        if (!source.getValueIsAdjusting()) {
            int val = source.getValue();
            // update the model with the newly selected component
            setSelectedComponent(val);
            // update the label with the new value
            _clabel.setText(Integer.toString(val));
        }
    }

    /**
     * Sets the selected component in the builder model for the
     * component class associated with this editor to the component at
     * the given index in this editor's list of available components.
     */
    protected void setSelectedComponent (int idx)
    {
        int cid = _components.get(idx).intValue();
        _model.setSelectedComponent(_cclass, cid);
    }

    /** The component class associated with this editor. */
    protected ComponentClass _cclass;

    /** The components selectable via this editor. */
    protected List<Integer> _components;

    /** The label denoting the currently selected component index. */
    protected JLabel _clabel;

    /** The builder model. */
    protected BuilderModel _model;
}
