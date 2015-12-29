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

package com.threerings.whirled.zone.server;

import com.threerings.crowd.data.BodyObject;

import com.threerings.whirled.zone.data.ZoneSummary;
import com.threerings.whirled.zone.data.ZonedBodyObject;

/**
 * A zone is a collection of scenes organized into a connected group. A
 * user can wander around within a zone, moving from scene to scene via
 * the standard mechanisms. To move between zones, they must use a special
 * mechanism (like at the dock, they can move from an island zone into
 * their ship zone; or they can move from an island zone into their house
 * zone). A zone provides scene summary information that can be used to
 * display a map of the zone to the client.
 */
public interface ZoneManager
{
    /**
     * Used to notify requesters when an asynchronous zone load has
     * completed (successfully or not).
     */
    public static interface ResolutionListener
    {
        /**
         * Called when a zone was successfully resolved.
         */
        public void zoneWasResolved (ZoneSummary summary);

        /**
         * Called when a zone failed to resolve.
         */
        public void zoneFailedToResolve (int zoneId, Exception reason);
    }

    /**
     * Resolves and delivers the scene summary information for the
     * requested zone. Zone resolution is an asynchronous process, which
     * necessitates this callback-style interface.
     *
     * @param zoneId the qualified zone id of the zone to resolve.
     * @param listener the listener that should be notified when the zone
     * is successfully resolved or is known to have failed to resolve.
     */
    public void resolveZone (int zoneId, ResolutionListener listener);

    /**
     * Called when a body has requested to leave a zone. The zone manager
     * may return null to indicate that the body is allowed to leave the
     * current zone or a string error code indicating the reason for
     * denial of access (which will be propagated back to the requesting
     * client).
     *
     * @param body the body object of the user that desires to depart
     * their current zone (which can be obtained by casting the {@link
     * BodyObject} to a {@link ZonedBodyObject}).
     */
    public String ratifyBodyExit (BodyObject body);

    /**
     * Called when a body has requested to enter a zone. The zone manager
     * may return null to indicate that the body is allowed access to the
     * zone or a string error code indicating the reason for denial of
     * access (which will be propagated back to the requesting client).
     * This method is called <em>after</em> the zone is resolved so that
     * the zone manager may complete the ratification process without
     * blocking (which it must do).
     *
     * @param body the body object of the user that desires access to the
     * specified zone.
     * @param zoneId the id of the zone to which the user desires access.
     */
    public String ratifyBodyEntry (BodyObject body, int zoneId);

    /**
     * Called when a body has been granted access to a zone. This method
     * must not block.
     *
     * @param body the body object of the user that was just granted
     * access to a zone.
     * @param zoneId the id of the zone to which they were granted access.
     */
    public void bodyDidEnterZone (BodyObject body, int zoneId);
}
