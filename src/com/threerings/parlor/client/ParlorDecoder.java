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

import com.threerings.parlor.game.data.GameConfig;
import com.threerings.presents.client.InvocationDecoder;
import com.threerings.util.Name;

/**
 * Dispatches calls to a {@link ParlorReceiver} instance.
 */
public class ParlorDecoder extends InvocationDecoder
{
    /** The generated hash code used to identify this receiver class. */
    public static final String RECEIVER_CODE = "5ef9ee0d359c42a9024498ee9aad119a";

    /** The method id used to dispatch {@link ParlorReceiver#gameIsReady}
     * notifications. */
    public static final int GAME_IS_READY = 1;

    /** The method id used to dispatch {@link ParlorReceiver#receivedInvite}
     * notifications. */
    public static final int RECEIVED_INVITE = 2;

    /** The method id used to dispatch {@link ParlorReceiver#receivedInviteCancellation}
     * notifications. */
    public static final int RECEIVED_INVITE_CANCELLATION = 3;

    /** The method id used to dispatch {@link ParlorReceiver#receivedInviteResponse}
     * notifications. */
    public static final int RECEIVED_INVITE_RESPONSE = 4;

    /**
     * Creates a decoder that may be registered to dispatch invocation
     * service notifications to the specified receiver.
     */
    public ParlorDecoder (ParlorReceiver receiver)
    {
        this.receiver = receiver;
    }

    @Override
    public String getReceiverCode ()
    {
        return RECEIVER_CODE;
    }

    @Override
    public void dispatchNotification (int methodId, Object[] args)
    {
        switch (methodId) {
        case GAME_IS_READY:
            ((ParlorReceiver)receiver).gameIsReady(
                ((Integer)args[0]).intValue()
            );
            return;

        case RECEIVED_INVITE:
            ((ParlorReceiver)receiver).receivedInvite(
                ((Integer)args[0]).intValue(), (Name)args[1], (GameConfig)args[2]
            );
            return;

        case RECEIVED_INVITE_CANCELLATION:
            ((ParlorReceiver)receiver).receivedInviteCancellation(
                ((Integer)args[0]).intValue()
            );
            return;

        case RECEIVED_INVITE_RESPONSE:
            ((ParlorReceiver)receiver).receivedInviteResponse(
                ((Integer)args[0]).intValue(), ((Integer)args[1]).intValue(), args[2]
            );
            return;

        default:
            super.dispatchNotification(methodId, args);
            return;
        }
    }
}
