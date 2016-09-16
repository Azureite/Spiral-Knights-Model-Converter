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

import java.io.IOException;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JPanel;

import com.samskivert.util.Config;
import com.samskivert.util.RunQueue;

import com.threerings.util.MessageManager;

import com.threerings.presents.client.Client;
import com.threerings.presents.dobj.DObjectManager;

import com.threerings.crowd.chat.client.ChatDirector;
import com.threerings.crowd.client.LocationDirector;
import com.threerings.crowd.client.OccupantDirector;
import com.threerings.crowd.client.PlaceView;

import com.threerings.parlor.client.ParlorDirector;
import com.threerings.parlor.util.ParlorContext;

import com.threerings.micasa.client.MiCasaFrame;
import com.threerings.micasa.util.MiCasaContext;

public class SimpleClient
    implements SimulatorClient
{
    public SimpleClient (SimulatorFrame frame)
        throws IOException
    {
        // create our context
        _ctx = createContext();

        // create the handles on our various services
        _client = new Client(null, RunQueue.AWT);

        // create our managers and directors
        _msgmgr = new MessageManager(getMessageManagerPrefix());
        _locdir = new LocationDirector(_ctx);
        _occdir = new OccupantDirector(_ctx);
        _pardtr = new ParlorDirector(_ctx);
        _chatdir = new ChatDirector(_ctx, null);

        // keep this for later
        _frame = frame;

        // log off when they close the window
        _frame.getFrame().addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing (WindowEvent evt) {
                // if we're logged on, log off
                if (_client.isLoggedOn()) {
                    _client.logoff(true);
                }
            }
        });
    }

    /**
     * Creates our context reference.
     */
    protected MiCasaContext createContext ()
    {
        return new MiCasaContextImpl();
    }

    /**
     * Returns the prefix used by the message manager when looking for
     * translation properties files.
     */
    protected String getMessageManagerPrefix ()
    {
        return "rsrc";
    }

    /**
     * Returns a reference to the context in effect for this client. This
     * reference is valid for the lifetime of the application.
     */
    public ParlorContext getParlorContext ()
    {
        return _ctx;
    }

    /**
     * The context implementation. This provides access to all of the
     * objects and services that are needed by the operating client.
     */
    protected class MiCasaContextImpl implements MiCasaContext
    {
        public Config getConfig ()
        {
            return _config;
        }

        public Client getClient ()
        {
            return _client;
        }

        public DObjectManager getDObjectManager ()
        {
            return _client.getDObjectManager();
        }

        public LocationDirector getLocationDirector ()
        {
            return _locdir;
        }

        public OccupantDirector getOccupantDirector ()
        {
            return _occdir;
        }

        public ParlorDirector getParlorDirector ()
        {
            return _pardtr;
        }

        public ChatDirector getChatDirector ()
        {
            return _chatdir;
        }

        public MessageManager getMessageManager ()
        {
            return _msgmgr;
        }

        public void setPlaceView (PlaceView view)
        {
            // stick the place view into our frame
            _frame.setPanel((JPanel)view);
        }

        public void clearPlaceView (PlaceView view)
        {
            // we'll just let the next view replace the old one
        }

        public MiCasaFrame getFrame ()
        {
            return (MiCasaFrame)_frame;
        }
    }

    protected MiCasaContext _ctx;
    protected SimulatorFrame _frame;
    protected MessageManager _msgmgr;

    protected Config _config = new Config("micasa");
    protected Client _client;
    protected LocationDirector _locdir;
    protected OccupantDirector _occdir;
    protected ParlorDirector _pardtr;
    protected ChatDirector _chatdir;
}
