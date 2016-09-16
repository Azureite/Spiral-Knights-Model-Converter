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

package com.threerings.parlor.client;

import com.threerings.util.Name;

import com.threerings.presents.client.InvocationService;

import com.threerings.parlor.game.data.GameConfig;

/**
 * Provides an interface to the various parlor invocation services.  Presently these services are
 * limited to the various matchmaking mechanisms. It is unlikely that client code will want to make
 * direct use of this class, instead they would make use of the programmatic interface provided by
 * the {@link ParlorDirector}.
 */
public interface ParlorService extends InvocationService
{
    /**
     * Used to communicate responses to {@link ParlorService#invite} requests.
     */
    public static interface InviteListener extends InvocationListener
    {
        /**
         * Called in response to a successful {@link ParlorService#invite} request.
         */
        public void inviteReceived (int inviteId);
    }

    /**
     * You probably don't want to call this directly, but want to generate your invitation request
     * via {@link ParlorDirector#invite}. Requests that an invitation be delivered to the named
     * user, requesting that they join the inviting user in a game, the details of which are
     * specified in the supplied game config object.
     *
     * @param invitee the username of the user to be invited.
     * @param config a game config object detailing the type and configuration of the game to be
     * created.
     * @param listener will receive and process the response.
     */
    public void invite (Name invitee, GameConfig config,
                        InviteListener listener);

    /**
     * You probably don't want to call this directly, but want to call one of {@link
     * Invitation#accept}, {@link Invitation#refuse}, or {@link Invitation#counter}. Requests that
     * an invitation response be delivered with the specified parameters.
     *
     * @param inviteId the unique id previously assigned by the server to this invitation.
     * @param code the response code to use in responding to the invitation.
     * @param arg the argument associated with the response (a string message from the player
     * explaining why the response was refused in the case of an invitation refusal or an updated
     * game configuration object in the case of a counter-invitation, or null in the case of an
     * accepted invitation).
     * @param listener will receive and process the response.
     */
    public void respond (int inviteId, int code, Object arg,
                         InvocationListener listener);

    /**
     * You probably don't want to call this directly, but want to call {@link
     * Invitation#cancel}. Requests that an outstanding invitation be cancelled.
     *
     * @param inviteId the unique id previously assigned by the server to this invitation.
     * @param listener will receive and process the response.
     */
    public void cancel (int inviteId, InvocationListener listener);

    /**
     * Requests to start a single player game with the specified game configuration.
     */
    public void startSolitaire (GameConfig config, ConfirmListener listener);
}
