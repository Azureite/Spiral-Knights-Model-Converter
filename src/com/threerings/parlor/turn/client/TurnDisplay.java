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

package com.threerings.parlor.turn.client;

import java.util.HashMap;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.google.common.collect.Maps;

import com.samskivert.swing.util.SwingUtil;

import com.threerings.util.Name;

import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.ElementUpdateListener;
import com.threerings.presents.dobj.ElementUpdatedEvent;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.parlor.game.data.GameObject;
import com.threerings.parlor.turn.data.TurnGameObject;

/**
 * Automatically display a list of players and turn change information
 * in a turn-based game.
 */
// TODO
// - adapt this to be able to display scores in some generic way as well.
// - allow configuring of turn / winner labels from prototypes,
//   rather than forcing one to be an icon, the other a string, and
//   examine the prototype to determine how to highlight the turnholder.
public class TurnDisplay extends JPanel
    implements PlaceView, AttributeChangeListener, ElementUpdateListener
{
    /**
     * Create a TurnDisplay.
     */
    public TurnDisplay ()
    {
    }

    /**
     * Create a TurnDisplay for a game using the specified Icon to denote
     * whose turn it is.
     */
    public TurnDisplay (Icon turnIcon)
    {
        setTurnIcon(turnIcon);
    }

    /**
     * Set the icon to use.
     */
    public void setTurnIcon (Icon turnIcon)
    {
        _turnIcon = turnIcon;
        if (_turnObj != null) {
            createList();
        }
    }

    /**
     * Set the text to be displayed next to the winner's name.
     */
    public void setWinnerText (String winnerText)
    {
        _winnerText = winnerText;
    }

    /**
     * Sets the text to be displayed next to everyone's name in the event of a
     * draw.
     */
    public void setDrawText (String drawText)
    {
        _drawText = drawText;
    }

    /**
     * Set optional icons to use for identifying each player in the game.
     */
    public void setPlayerIcons (Icon[] icons)
    {
        _playerIcons = icons;
        if (_turnObj != null) {
            createList();
        }
    }

    /**
     * Create the list of names and highlight as appropriate.
     */
    protected void createList ()
    {
        removeAll();
        _labels.clear();

        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);

        GridBagConstraints iconC = new GridBagConstraints();
        GridBagConstraints labelC = new GridBagConstraints();
        iconC.fill = labelC.fill = GridBagConstraints.BOTH;
        labelC.weightx = 1.0;
        labelC.insets.left = 10;
        labelC.gridwidth = GridBagConstraints.REMAINDER;

        Name[] names = _turnObj.getPlayers();
        GameObject gameobj = (GameObject)_turnObj;
        boolean[] winners = gameobj.winners;
        Name holder = _turnObj.getTurnHolder();
        for (int ii=0, jj=0; ii < names.length; ii++, jj++) {
            if (names[ii] == null) continue;

            JLabel iconLabel = new JLabel();
            if (winners == null) {
                if (names[ii].equals(holder)) {
                    iconLabel.setIcon(_turnIcon);
                }
            } else if (gameobj.isDraw()) {
                iconLabel.setText(_drawText);
            } else if (winners[ii]) {
                iconLabel.setText(_winnerText);
            }
            iconLabel.setForeground(Color.BLACK);
            _labels.put(names[ii], iconLabel);
            add(iconLabel, iconC);

            JLabel label = new JLabel(names[ii].toString());
            if (_playerIcons != null) {
                label.setIcon(_playerIcons[jj]);
            }
            add(label, labelC);
        }

        SwingUtil.refresh(this);
    }

    // documentation inherited from interface PlaceView
    public void willEnterPlace (PlaceObject plobj)
    {
        _turnObj = (TurnGameObject) plobj;
        plobj.addListener(this);
        createList();
    }

    // documentation inherited from interface PlaceView
    public void didLeavePlace (PlaceObject plobj)
    {
        plobj.removeListener(this);
        _turnObj = null;
        removeAll();
    }

    // documentation inherited from interface AttributeChangeListener
    public void attributeChanged (AttributeChangedEvent event)
    {
        String name = event.getName();
        if (name.equals(_turnObj.getTurnHolderFieldName())) {
            JLabel oldLabel = _labels.get(event.getOldValue());
            if (oldLabel != null) {
                oldLabel.setIcon(null);
            }
            JLabel newLabel = _labels.get(event.getValue());
            if (newLabel != null) {
                newLabel.setIcon(_turnIcon);
            }

        } else if (name.equals(GameObject.PLAYERS)) {
            createList();

        } else if (name.equals(GameObject.WINNERS)) {
            createList();
        }
    }

    // documentation inherited from interface ElementUpdateListener
    public void elementUpdated (ElementUpdatedEvent event)
    {
        String name = event.getName();
        if (name.equals(GameObject.PLAYERS)) {
            createList();
        }
    }

    /** The TurnGameObject we're displaying. */
    protected TurnGameObject _turnObj;

    /** A mapping of the labels currently associated with each player. */
    protected HashMap<Name,JLabel> _labels = Maps.newHashMap();

    /** The game-specified player icons. */
    protected Icon[] _playerIcons;

    /** The text to display next to a winner's name. */
    protected String _winnerText = "";

    /** The text to display next to a drawing player's name. */
    protected String _drawText = "";

    /** The Icon we use for indicating the turn. */
    protected Icon _turnIcon;
}
