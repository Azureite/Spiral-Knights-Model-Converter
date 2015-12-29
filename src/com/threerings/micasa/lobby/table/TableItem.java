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

package com.threerings.micasa.lobby.table;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.threerings.util.Name;

import com.threerings.crowd.data.BodyObject;

import com.threerings.parlor.client.SeatednessObserver;
import com.threerings.parlor.client.TableDirector;
import com.threerings.parlor.data.Table;

import com.threerings.micasa.util.MiCasaContext;

import static com.threerings.micasa.Log.log;

/**
 * A table item displays the user interface for a single table (whether it
 * be in-play or still being matchmade).
 */
public class TableItem
    extends JPanel
    implements ActionListener, SeatednessObserver
{
    /** A reference to the table we are displaying. */
    public Table table;

    /**
     * Creates a new table item to display and interact with the supplied
     * table.
     */
    public TableItem (MiCasaContext ctx, TableDirector tdtr, Table table)
    {
        // keep track of these
        _tdtr = tdtr;
        _ctx = ctx;

        // add ourselves as a seatedness observer
        _tdtr.addSeatednessObserver(this);

        // figure out who we are
        _self = ((BodyObject)ctx.getClient().getClientObject()).getVisibleName();

        // now create our user interface
            setBorder(BorderFactory.createLineBorder(Color.black));
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // create a label for the table
        JLabel tlabel = new JLabel("Table " + table.tableId);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(2, 0, 0, 0);
        add(tlabel, gbc);

        // we have one button for every "seat" at the table
        int bcount = table.players.length;

        // create blank buttons for now and then we'll update everything
        // with the current state when we're done
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 0);
        _seats = new JButton[bcount];
        for (int ii = 0; ii < bcount; ii++) {
            // create the button
            _seats[ii] = new JButton(JOIN_LABEL);
            _seats[ii].addActionListener(this);

            // if we're on the left
            if (ii % 2 == 0) {
                // if we're the last seat, then we've got an odd number
                // and need to center this final seat
                if (ii == bcount-1) {
                    gbc.gridwidth = GridBagConstraints.REMAINDER;
                } else {
                    gbc.gridwidth = 1;
                }

            } else {
                gbc.gridwidth = GridBagConstraints.REMAINDER;
            }

            // adjust the insets of our last element
            if (ii == bcount-1) {
                gbc.insets = new Insets(2, 0, 4, 0);
            }

            // and add the button with the configured constraints
            add(_seats[ii], gbc);

            // if we just added the first button, add the "go" button
            // right after it
            if (ii == 0) {
                _goButton = new JButton("Go");
                _goButton.setActionCommand("go");
                _goButton.addActionListener(this);
                add(_goButton, gbc);
            }
        }

        // and update ourselves based on the contents of the occupants
        // list
        tableUpdated(table);
    }

    /**
     * Called when our table has been updated and we need to update the UI
     * to reflect the new information.
     */
    public void tableUpdated (Table table)
    {
        // grab this new table reference
        this.table = table;

        // first look to see if we're already sitting at a table
        boolean isSeated = _tdtr.isSeated();

        // now enable and label the buttons accordingly
        int slength = _seats.length;
        for (int ii = 0; ii < slength; ii++) {
            if (table.players[ii] == null) {
                _seats[ii].setText(JOIN_LABEL);
                _seats[ii].setEnabled(!isSeated);
                _seats[ii].setActionCommand("join");

            } else if (table.players[ii].equals(_self) &&
                       !table.inPlay()) {
                _seats[ii].setText(LEAVE_LABEL);
                _seats[ii].setEnabled(true);
                _seats[ii].setActionCommand("leave");

            } else {
                _seats[ii].setText(table.players[ii].toString());
                _seats[ii].setEnabled(false);
            }
        }

        // show or hide our "go" button appropriately
        _goButton.setVisible(table.gameOid != -1);
    }

    /**
     * Called by the table list view prior to removing us. Here we clean
     * up.
     */
    public void tableRemoved ()
    {
        // no more observy
        _tdtr.removeSeatednessObserver(this);
    }

    // documentation inherited
    public void actionPerformed (ActionEvent event)
    {
        String cmd = event.getActionCommand();
        if (cmd.equals("join")) {
            // figure out what position this button is in
            int position = -1;
            for (int ii = 0; ii < _seats.length; ii++) {
                if (_seats[ii] == event.getSource()) {
                    position = ii;
                    break;
                }
            }

            // sanity check
            if (position == -1) {
                log.warning("Unable to figure out what position a <join> " +
                            "click came from [event=" + event + "].");
            } else {
                // otherwise, request to join the table at this position
                _tdtr.joinTable(table.tableId, position);
            }

        } else if (cmd.equals("leave")) {
            // if we're not joining, we're leaving
            _tdtr.leaveTable(table.tableId);

        } else if (cmd.equals("go")) {
            // they want to see the game... so go there
            _ctx.getLocationDirector().moveTo(table.gameOid);

        } else {
            log.warning("Received unknown action [event=" + event + "].");
        }
    }

    // documentation inherited
    public void seatednessDidChange (boolean isSeated)
    {
        // just update ourselves
        tableUpdated(table);

        // enable or disable the go button based on our seatedness
        if (_goButton.isVisible()) {
            _goButton.setEnabled(!isSeated);
        }
    }

    /** A reference to our context. */
    protected MiCasaContext _ctx;

    /** Our username. */
    protected Name _self;

    /** A reference to our table director. */
    protected TableDirector _tdtr;

    /** We have a button for each "seat" at the table. */
    protected JButton[] _seats;

    /** We have a button for going to games that are already in
     * progress. */
    protected JButton _goButton;

    /** The text shown for seats at which the user can join. */
    protected static final String JOIN_LABEL = "<join>";

    /** The text shown for the seat in which this user occupies and which
     * lets her/him know that they can leave that seat by clicking. */
    protected static final String LEAVE_LABEL = "<leave>";
}
