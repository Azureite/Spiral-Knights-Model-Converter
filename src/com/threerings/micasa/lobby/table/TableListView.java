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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.samskivert.swing.HGroupLayout;
import com.samskivert.swing.VGroupLayout;
import com.samskivert.swing.util.SwingUtil;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.parlor.client.SeatednessObserver;
import com.threerings.parlor.client.TableConfigurator;
import com.threerings.parlor.client.TableDirector;
import com.threerings.parlor.client.TableObserver;
import com.threerings.parlor.data.Table;
import com.threerings.parlor.game.client.GameConfigurator;
import com.threerings.parlor.game.client.SwingGameConfigurator;
import com.threerings.parlor.game.data.GameConfig;

import com.threerings.micasa.lobby.LobbyConfig;
import com.threerings.micasa.util.MiCasaContext;

import static com.threerings.micasa.Log.log;

/**
 * A view that displays the tables in a table lobby. It displays two
 * separate lists, one of tables being matchmade and another of games in
 * progress. These tables are updated dynamically as they proceed through
 * the matchmaking process. UI mechanisms for creating and joining tables
 * are also provided.
 */
public class TableListView extends JPanel
    implements PlaceView, TableObserver, ActionListener, SeatednessObserver
{
    /**
     * Creates a new table list view, suitable for providing the user
     * interface for table-style matchmaking in a table lobby.
     */
    public TableListView (MiCasaContext ctx, LobbyConfig config)
    {
        // keep track of these
        _config = config;
        _ctx = ctx;

        // create our table director
        _tdtr = new TableDirector(ctx, TableLobbyObject.TABLE_SET, this);

        // add ourselves as a seatedness observer
        _tdtr.addSeatednessObserver(this);

        // set up a layout manager
        HGroupLayout gl = new HGroupLayout(HGroupLayout.STRETCH);
        gl.setOffAxisPolicy(HGroupLayout.STRETCH);
        setLayout(gl);

        // we have two lists of tables, one of tables being matchmade...
        VGroupLayout pgl = new VGroupLayout(VGroupLayout.STRETCH);
        pgl.setOffAxisPolicy(VGroupLayout.STRETCH);
        JPanel panel = new JPanel(pgl);
        panel.add(new JLabel("Pending tables"), VGroupLayout.FIXED);

        VGroupLayout mgl = new VGroupLayout(VGroupLayout.NONE);
        mgl.setOffAxisPolicy(VGroupLayout.STRETCH);
        mgl.setJustification(VGroupLayout.TOP);
        _matchList = new JPanel(mgl);
            _matchList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(new JScrollPane(_matchList));

        // create and initialize the configurator interface
        GameConfig gconfig = null;
        try {
            gconfig = config.getGameConfig();

            _tableFigger = gconfig.createTableConfigurator();
            if (_tableFigger == null) {
                log.warning("Game config has not been set up to work with " +
                    "tables: it needs to return non-null from " +
                    "createTableConfigurator().");
                // let's just wait until we throw an NPE below
            }

            _figger = gconfig.createConfigurator();
            _tableFigger.init(_ctx, _figger);
            if (_figger != null) {
                _figger.init(_ctx);
                _figger.setGameConfig(gconfig);
                panel.add(((SwingGameConfigurator) _figger).getPanel(),
                    VGroupLayout.FIXED);
            }

            _create = new JButton("Create table");
            _create.addActionListener(this);
            panel.add(_create, VGroupLayout.FIXED);

        } catch (Exception e) {
            log.warning("Unable to create configurator interface " +
                        "[config=" + gconfig + "].", e);

            // stick something in the UI to let them know we're hosed
            panel.add(new JLabel("Aiya! Can't create tables. " +
                                 "Configuration borked."), VGroupLayout.FIXED);
        }

        add(panel);

        // ...and one of games in progress
        panel = new JPanel(pgl);
        panel.add(new JLabel("Games in progress"), VGroupLayout.FIXED);

        _playList = new JPanel(mgl);
            _playList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(new JScrollPane(_playList));

        add(panel);
    }

    // documentation inherited
    public void willEnterPlace (PlaceObject place)
    {
        // pass the good word on to our table director
        _tdtr.setTableObject(place);

        // iterate over the tables already active in this lobby and put
        // them in their respective lists
        TableLobbyObject tlobj = (TableLobbyObject)place;
        for (Table table : tlobj.tableSet) {
            tableAdded(table);
        }
    }

    // documentation inherited
    public void didLeavePlace (PlaceObject place)
    {
        // pass the good word on to our table director
        _tdtr.clearTableObject();

        // clear out our table lists
        _matchList.removeAll();
        _playList.removeAll();
    }

    // documentation inherited
    public void tableAdded (Table table)
    {
        log.info("Table added [table=" + table + "].");

        // create a table item for this table and insert it into the
        // appropriate list
        JPanel panel = table.inPlay() ? _playList : _matchList;
        panel.add(new TableItem(_ctx, _tdtr, table));
        SwingUtil.refresh(panel);
    }

    // documentation inherited
    public void tableUpdated (Table table)
    {
        log.info("Table updated [table=" + table + "].");

        // locate the table item associated with this table
        TableItem item = getTableItem(table.tableId);
        if (item == null) {
            log.warning("Received table updated notification for " +
                        "unknown table [table=" + table + "].");
            return;
        }

        // let the item perform any updates it finds necessary
        item.tableUpdated(table);

        // and we may need to move the item from the match to the in-play
        // list if it just transitioned
        if (table.gameOid != -1 && item.getParent() == _matchList) {
            _matchList.remove(item);
            SwingUtil.refresh(_matchList);
            _playList.add(item);
            SwingUtil.refresh(_playList);
        }
    }

    // documentation inherited
    public void tableRemoved (int tableId)
    {
        log.info("Table removed [tableId=" + tableId + "].");

        // locate the table item associated with this table
        TableItem item = getTableItem(tableId);
        if (item == null) {
            log.warning("Received table removed notification for " +
                        "unknown table [tableId=" + tableId + "].");
            return;
        }

        // remove this item from the user interface
        JPanel panel = (JPanel)item.getParent();
        panel.remove(item);
        SwingUtil.refresh(panel);

        // let the little fellow know that we gave him the boot
        item.tableRemoved();
    }

    // documentation inherited
    public void actionPerformed (ActionEvent event)
    {
        // the create table button was clicked. use the game config as
        // configured by the configurator to create a table
        _tdtr.createTable(_tableFigger.getTableConfig(),
                          _figger.getGameConfig());
    }

    // documentation inherited
    public void seatednessDidChange (boolean isSeated)
    {
        // update the create table button
        _create.setEnabled(!isSeated);
    }

    /**
     * Fetches the table item component associated with the specified
     * table id.
     */
    protected TableItem getTableItem (int tableId)
    {
        // first check the match list
        int ccount = _matchList.getComponentCount();
        for (int ii = 0; ii < ccount; ii++) {
            TableItem child = (TableItem)_matchList.getComponent(ii);
            if (child.table.tableId == tableId) {
                return child;
            }
        }

        // then the inplay list
        ccount = _playList.getComponentCount();
        for (int ii = 0; ii < ccount; ii++) {
            TableItem child = (TableItem)_playList.getComponent(ii);
            if (child.table.tableId == tableId) {
                return child;
            }
        }

        // sorry charlie
        return null;
    }

    /** A reference to the client context. */
    protected MiCasaContext _ctx;

    /** A reference to the lobby config for the lobby in which we are
     * doing table-style matchmaking. */
    protected LobbyConfig _config;

    /** A reference to our table director. */
    protected TableDirector _tdtr;

    /** The list of tables currently being matchmade. */
    protected JPanel _matchList;

    /** The list of tables that are in play. */
    protected JPanel _playList;

    /** The interface used to configure the table for a game. */
    protected TableConfigurator _tableFigger;

    /** The interface used to configure a game before creating it. */
    protected GameConfigurator _figger;

    /** Our create table button. */
    protected JButton _create;

    /** Our number of players indicator. */
    protected JLabel _pcount;
}
