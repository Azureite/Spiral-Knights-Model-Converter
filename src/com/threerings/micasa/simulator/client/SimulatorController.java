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

package com.threerings.micasa.simulator.client;

import java.awt.event.ActionEvent;

import com.samskivert.swing.Controller;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.SessionObserver;

import com.threerings.crowd.data.BodyObject;

import com.threerings.parlor.game.data.GameConfig;
import com.threerings.parlor.util.ParlorContext;

import com.threerings.micasa.simulator.data.SimulatorInfo;

import static com.threerings.micasa.Log.log;

/**
 * Responsible for top-level control of the simulator client user interface.
 */
public class SimulatorController extends Controller
    implements SessionObserver
{
    /** Command constant used to logoff the client. */
    public static final String LOGOFF = "logoff";

// 577-2028

    /**
     * Creates a new simulator controller. The controller will set
     * everything up in preparation for logging on.
     */
    public SimulatorController (ParlorContext ctx, SimulatorFrame frame,
                                SimulatorInfo info)
    {
        // we'll want to keep these around
        _ctx = ctx;
        _frame = frame;
        _info = info;

        // we want to know about logon/logoff
        _ctx.getClient().addClientObserver(this);
    }

    @Override
    public boolean handleAction (ActionEvent action)
    {
        String cmd = action.getActionCommand();

        if (cmd.equals(LOGOFF)) {
            // request that we logoff
            _ctx.getClient().logoff(true);
            return true;
        }

        log.info("Unhandled action: " + action);
        return false;
    }

    // documentation inherited
    public void clientWillLogon (Client client)
    {
        // nada
    }

    // documentation inherited
    public void clientDidLogon (Client client)
    {
        log.info("Client did logon [client=" + client + "].");

        // keep the body object around for stuff
        _body = (BodyObject)client.getClientObject();

        // have at it
        createGame(client);
    }

    public void createGame (Client client)
    {
        GameConfig config = null;
        try {
            // create the game config object
            config = (GameConfig)Class.forName(_info.gameConfigClass).newInstance();

            // get the simulator service and use it to request that our game be created
            SimulatorService sservice = client.requireService(SimulatorService.class);
            sservice.createGame(config, _info.simClass, _info.playerCount);

            // our work here is done, as the location manager will move us into the game room
            // straightaway

        } catch (Exception e) {
            log.warning("Failed to instantiate game config [class=" + _info.gameConfigClass +
                        ", error=" + e + "].");
        }
    }

    // documentation inherited
    public void clientObjectDidChange (Client client)
    {
        // regrab our body object
        _body = (BodyObject)client.getClientObject();
    }

    // documentation inherited
    public void clientDidLogoff (Client client)
    {
        log.info("Client did logoff [client=" + client + "].");
    }

    protected ParlorContext _ctx;
    protected SimulatorFrame _frame;
    protected SimulatorInfo _info;
    protected BodyObject _body;
}
