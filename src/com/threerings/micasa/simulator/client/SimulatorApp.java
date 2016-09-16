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

import javax.swing.JFrame;

import com.google.inject.Guice;
import com.google.inject.Injector;

import com.samskivert.util.Interval;
import com.samskivert.util.ResultListener;

import com.samskivert.swing.Controller;
import com.samskivert.swing.util.SwingUtil;

import com.threerings.util.Name;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.ClientAdapter;
import com.threerings.presents.net.UsernamePasswordCreds;

import com.threerings.micasa.simulator.data.SimulatorInfo;
import com.threerings.micasa.simulator.server.SimpleServer;
import com.threerings.micasa.simulator.server.SimulatorServer;

import static com.threerings.micasa.Log.log;

/**
 * The simulator application is a test harness to facilitate development and debugging of games.
 */
public class SimulatorApp
{
    public void start (final String[] args) throws Exception
    {
        // create a frame
        _frame = createSimulatorFrame();

        // create the simulator info object
        SimulatorInfo siminfo = new SimulatorInfo();
        siminfo.gameConfigClass = args[0];
        siminfo.simClass = args[1];
        siminfo.playerCount = getInt(System.getProperty("playercount"), DEFAULT_PLAYER_COUNT);

        // create our client instance
        _client = createSimulatorClient(_frame);

        // set up the top-level client controller
        Controller ctrl = createController(siminfo);
        _frame.setController(ctrl);

        // create the server
        Injector injector = Guice.createInjector(new SimpleServer.CrowdModule());
        SimulatorServer server = createSimulatorServer(injector);
        server.init(injector, new ResultListener<SimulatorServer>() {
            public void requestCompleted (SimulatorServer result) {
                try {
                    run();
                } catch (Exception e) {
                    log.warning("Simulator initialization failed [e=" + e + "].");
                }
            }
            public void requestFailed (Exception e) {
                log.warning("Simulator initialization failed [e=" + e + "].");
            }
        });

        // run the server on a separate thread
        _serverThread = new ServerThread(server);
        // start up the server so that we can be notified when initialization is complete
        _serverThread.start();
    }

    protected SimulatorServer createSimulatorServer (Injector injector)
    {
        return injector.getInstance(SimpleServer.class);
    }

    protected SimulatorFrame createSimulatorFrame ()
    {
        return new SimpleFrame();
    }

    protected SimulatorClient createSimulatorClient (SimulatorFrame frame)
        throws Exception
    {
        return new SimpleClient(_frame);
    }

    protected SimulatorController createController (SimulatorInfo siminfo)
    {
        return new SimulatorController(
            _client.getParlorContext(), _frame, siminfo);
    }

    public void run ()
    {
        // configure and display the main frame
        JFrame frame = _frame.getFrame();
        frame.setSize(800, 600);
        SwingUtil.centerWindow(frame);
        frame.setVisible(true);

        // start up the client
        Client client = _client.getParlorContext().getClient();
        log.info("Connecting to localhost.");
        client.setServer("localhost", Client.DEFAULT_SERVER_PORTS);

        // we want to exit when we logged off or failed to log on
        client.addClientObserver(new ClientAdapter() {
            @Override
            public void clientFailedToLogon (Client c, Exception cause) {
                log.info("Client failed to logon: " + cause);
                System.exit(0);
            }
            @Override
            public void clientDidLogoff (Client c) {
                System.exit(0);
            }
        });

        // configure the client with some credentials and logon
        String username = System.getProperty("username");
        if (username == null) {
            username =
                "bob" + ((int)(Math.random() * Integer.MAX_VALUE) % 500);
        }
        String password = System.getProperty("password");
        if (password == null) {
            password = "test";
        }

        // create and set our credentials
        client.setCredentials(
            new UsernamePasswordCreds(new Name(username), password));

        // this is a bit of a hack, but we need to give the server long
        // enough to fully initialize and start listening on its socket
        // before we try to logon; there's no good way for this otherwise
        // wholly independent thread to wait for the server to be ready as
        // in normal circumstances they are entirely different processes;
        // so we just wait half a second which does the job
        new Interval(Interval.RUN_DIRECT) {
            @Override
            public void expired () {
                _client.getParlorContext().getClient().logon();
            }
        }.schedule(500L);
    }

    public static void main (String[] args)
    {
        if (!checkArgs(args)) {
            return;
        }

        SimulatorApp app = new SimulatorApp();
        try {
            app.start(args);
        } catch (Exception e) {
            log.warning("Error starting up application.", e);
        }
    }

    protected static boolean checkArgs (String[] args)
    {
        if (args.length < 2) {
            String msg = "Usage:\n" +
                "    java com.threerings.simulator.SimulatorApp " +
                "<game config class name> <simulant class name>\n" +
                "Optional properties:\n" +
                "    -Dusername=<user>\n" +
                "    -Dplayercount=<number>\n" +
                "    -Dwidth=<width>\n" +
                "    -Dheight=<height>";
            System.out.println(msg);
            return false;
        }

        return true;
    }

    protected int getInt (String value, int defval)
    {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            return defval;
        }
    }

    protected static class ServerThread extends Thread
    {
        public ServerThread (SimulatorServer server)
        {
            _server = server;
        }

        @Override
        public void run ()
        {
            _server.run();
        }

        protected SimulatorServer _server;
    }

    protected SimulatorClient _client;
    protected SimulatorFrame _frame;
    protected ServerThread _serverThread;

    /** The default number of players in the game. */
    protected static final int DEFAULT_PLAYER_COUNT = 2;
}
