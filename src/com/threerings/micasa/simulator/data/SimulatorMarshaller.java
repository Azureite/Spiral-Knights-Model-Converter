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

package com.threerings.micasa.simulator.data;

import javax.annotation.Generated;

import com.threerings.micasa.simulator.client.SimulatorService;
import com.threerings.parlor.game.data.GameConfig;
import com.threerings.presents.data.InvocationMarshaller;

/**
 * Provides the implementation of the {@link SimulatorService} interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
@Generated(value={"com.threerings.presents.tools.GenServiceTask"},
           comments="Derived from SimulatorService.java.")
public class SimulatorMarshaller extends InvocationMarshaller
    implements SimulatorService
{
    /** The method id used to dispatch {@link #createGame} requests. */
    public static final int CREATE_GAME = 1;

    // from interface SimulatorService
    public void createGame (GameConfig arg1, String arg2, int arg3)
    {
        sendRequest(CREATE_GAME, new Object[] {
            arg1, arg2, Integer.valueOf(arg3)
        });
    }
}
