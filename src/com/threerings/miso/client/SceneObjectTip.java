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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.swing.Icon;
import javax.swing.UIManager;

import com.samskivert.util.ComparableArrayList;
import com.samskivert.util.StringUtil;

import com.samskivert.swing.Label;
import com.samskivert.swing.LabelSausage;

/**
 * A lightweight tooltip used by the {@link MisoScenePanel}. The tip
 * foreground and background are controlled by the following {@link
 * UIManager} properties:
 *
 * <pre>
 * SceneObjectTip.background
 * SceneObjectTip.foreground
 * SceneObjectTip.font (falls back to Label.font)
 * </pre>
 */
public class SceneObjectTip extends LabelSausage
    implements SceneObjectIndicator
{
    /**
     * Used to position a scene tip in relation to the object with which
     * it is associated.
     */
    public static interface TipLayout
    {
        /**
         * Position the supplied tip relative to the supplied scene object.
         */
        public void layout (Graphics2D gfx, Rectangle boundary,
                            SceneObject tipFor, SceneObjectTip tip);
    }

    /** The bounding box of this tip, or null prior to layout(). */
    public Rectangle bounds;

    /**
     * Construct a SceneObjectTip.
     */
    public SceneObjectTip (String text, Icon icon)
    {
        super(new Label(text, _foreground, _font), icon);
    }

    public boolean isLaidOut ()
    {
        return _label.isLaidOut();
    }

    /**
     * Called to initialize the tip so that it can be painted.
     *
     * @param tipFor the scene object that we're a tip for.
     * @param boundary the boundary of all displayable space.
     */
    public void layout (Graphics2D gfx, SceneObject tipFor, Rectangle boundary)
    {
        layout(gfx, ICON_PAD, EXTRA_PAD);
        bounds = new Rectangle(_size);

        // locate the most appropriate tip layout
        for (int ii = 0, ll = _layouts.size(); ii < ll; ii++) {
            LayoutReg reg = _layouts.get(ii);
            String act = tipFor.info.action == null ? "" : tipFor.info.action;
            if (act.startsWith(reg.prefix)) {
                reg.layout.layout(gfx, boundary, tipFor, this);
                break;
            }
        }
    }

    // documentation inherited from interface
    public void paint (Graphics2D gfx)
    {
        paint(gfx, bounds.x, bounds.y, _background, null);
    }

    // documentation inherited from interface
    public Rectangle getBounds ()
    {
        return bounds;
    }

    // documentation inherited from interface
    public void removed ()
    {
        // Nothin doin
    }

    // documentation inherited from interface
    public void update (Icon icon, String tiptext)
    {
        _label.setText(tiptext);
        _icon = icon;
    }

    /**
     * Generates a string representation of this instance.
     */
    @Override
    public String toString ()
    {
        return _label.getText() + "[" + StringUtil.toString(bounds) + "]";
    }

    /**
     * It may be desirable to layout object tips specially depending on
     * what sort of actions they represent, so we allow different tip
     * layout algorithms to be registered for particular object prefixes.
     * The registration is simply a list searched from longest string to
     * shortest string for the first match to an object's action.
     */
    public static void registerTipLayout (String prefix, TipLayout layout)
    {
        LayoutReg reg = new LayoutReg();
        reg.prefix = prefix;
        reg.layout = layout;
        _layouts.insertSorted(reg);
    }

    @Override
    protected void drawBase (Graphics2D gfx, int x, int y)
    {
        Composite ocomp = gfx.getComposite();
        gfx.setComposite(ALPHA);
        super.drawBase(gfx, x, y);
        gfx.setComposite(ocomp);
    }

    /** The alpha we use for our base. */
    protected static final Composite ALPHA = AlphaComposite.getInstance(
        AlphaComposite.SRC_OVER, .75f);

    /** Colors to use when rendering the tip. */
    protected static Color _background, _foreground;

    /** The font to use when rendering the tip. */
    protected static Font _font;

    // initialize resources shared by all tips
    static {
        _background = UIManager.getColor("SceneObjectTip.background");
        _foreground = UIManager.getColor("SceneObjectTip.foreground");
        _font = UIManager.getFont("SceneObjectTip.font");
        if (_font == null) {
            _font = UIManager.getFont("Label.font");
        }
    }

    /** Used to store {@link TipLayout} registrations. */
    protected static class LayoutReg implements Comparable<LayoutReg>
    {
        /** The prefix that defines our applicability. */
        public String prefix;

        /** The layout to use for objects matching our prefix. */
        public TipLayout layout;

        // documentation inherited from interface
        public int compareTo (LayoutReg or) {
            if (or.prefix.length() == prefix.length()) {
                return or.prefix.compareTo(prefix);
            } else {
                return or.prefix.length() - prefix.length();
            }
        }
    }

    /** Our default tip layout algorithm which centers the tip in the
     * bounds of the object in question. */
    protected static class DefaultLayout implements TipLayout
    {
        public void layout (Graphics2D gfx, Rectangle boundary,
                            SceneObject tipFor, SceneObjectTip tip) {
            tip.bounds.setLocation(
                tipFor.bounds.x + (tipFor.bounds.width-tip.bounds.width) / 2,
                tipFor.bounds.y + (tipFor.bounds.height-tip.bounds.height) / 2);
        }
    }

    /** Contains a sorted list of layout registrations. */
    protected static ComparableArrayList<LayoutReg> _layouts = new ComparableArrayList<LayoutReg>();

    /** The number of pixels to pad around the icon. */
    protected static final int ICON_PAD = 4;

    /** The number of pixels to pad between the icon and text. */
    protected static final int EXTRA_PAD = 2;

    static {
        registerTipLayout("", new DefaultLayout());
    }
}
