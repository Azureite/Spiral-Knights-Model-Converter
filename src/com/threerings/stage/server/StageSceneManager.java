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

package com.threerings.stage.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import java.awt.Point;
import java.awt.Rectangle;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import com.samskivert.util.HashIntMap;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationException;

import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.media.util.AStarPathUtil;
import com.threerings.media.util.MathUtil;

import com.threerings.miso.data.ObjectInfo;
import com.threerings.miso.util.MisoSceneMetrics;
import com.threerings.miso.util.MisoUtil;

import com.threerings.whirled.data.SceneUpdate;
import com.threerings.whirled.spot.data.Cluster;
import com.threerings.whirled.spot.data.ClusterObject;
import com.threerings.whirled.spot.data.Location;
import com.threerings.whirled.spot.data.Portal;
import com.threerings.whirled.spot.data.SceneLocation;
import com.threerings.whirled.spot.server.SpotSceneManager;

import com.threerings.stage.client.StageSceneService;
import com.threerings.stage.data.DefaultColorUpdate;
import com.threerings.stage.data.ModifyObjectsUpdate;
import com.threerings.stage.data.StageCodes;
import com.threerings.stage.data.StageLocation;
import com.threerings.stage.data.StageMisoSceneModel;
import com.threerings.stage.data.StageOccupantInfo;
import com.threerings.stage.data.StageScene;
import com.threerings.stage.data.StageSceneMarshaller;
import com.threerings.stage.data.StageSceneModel;
import com.threerings.stage.data.StageSceneObject;
import com.threerings.stage.util.StageSceneUtil;

import static com.threerings.stage.Log.log;

/**
 * Defines extensions to the basic Stage scene manager specific to displaying isometric "stage"
 * scenes (these may be indoor, outdoor or aboard a vessel).
 */
public class StageSceneManager extends SpotSceneManager
    implements StageSceneProvider
{
    /**
     * Returns a traversal predicate for use with {@link StageSceneUtil#findStandingSpot} that
     * validates whether a player can stand in the searched spots.
     */
    public AStarPathUtil.TraversalPred getCanStandPred ()
    {
        // this checks to see if we can stand at a particular tile coord
        return new AStarPathUtil.TraversalPred() {
            public boolean canTraverse (Object traverser, int x, int y) {
                _tloc.x = MisoUtil.toFull(x, 2);
                _tloc.y = MisoUtil.toFull(y, 2);
                return mayStandAtLocation((BodyObject)traverser, _tloc);
            }
            protected StageLocation _tloc = new StageLocation();
        };
    }

    /**
     * Adds the supplied object to this scene. A persistent update is generated and broadcast to
     * all scene occupants. The update is stored in the repository for communication to future
     * occupants of the scene and then entire complex process of changing our virtual game world is
     * effected at the simple call of this single method.
     *
     * @param killOverlap if true, overlapping object will be removed, and the allowOverlap
     * argument will be ignored.
     * @param allowOverlap if true, overlapping objects will be allowed but one must be *very*
     * careful to ensure that they know what they are doing (i.e. the objects have render priorities
     * that correctly handle the overlap).
     *
     * @return true if the object was added, false if the add was rejected because the object
     * overlaps an existing scene object.
     */
    public boolean addObject (ObjectInfo info, boolean killOverlap,
                              boolean allowOverlap)
    {
        // determine whether or not any object overlap our footprint
        Rectangle foot = StageSceneUtil.getObjectFootprint(
            StageServer.tilemgr, info.tileId, info.x, info.y);
        if (foot == null) {
            log.warning("Aiya! Unable to compute object footprint! " +
                        "[where=" + where() + ", info=" + info + "].");
            return false;
        }

        ObjectInfo[] lappers = StageSceneUtil.getIntersectedObjects(
            StageServer.tilemgr, (StageSceneModel)_sscene.getSceneModel(),
            foot);
        if (!killOverlap && lappers.length > 0 && !allowOverlap) {
            // no overlapping allowed
            return false;
        }

        // create our scene update which will be stored in the database
        // and used to efficiently update clients
        final ModifyObjectsUpdate update = new ModifyObjectsUpdate();
        update.init(_sscene.getId(), _sscene.getVersion(),
            new ObjectInfo[] { info }, killOverlap ? lappers : null);

        log.info("Modifying objects '" + update + ".");
        recordUpdate(update, true);

        return true;
    }

    /**
     * Changes the default colorization for the specified color class.
     * A persistent update is generated and broadcast to all scene
     * occupants. The update is stored in the repository for communication
     * to future occupants of the scene and then entire complex process
     * of changing our virtual game world is effected at the simple call
     * of this single method.
     */
    public void setColor (int classId, int colorId)
    {
        DefaultColorUpdate update = new DefaultColorUpdate();
        update.init(_sscene.getId(), _sscene.getVersion(), classId, colorId);
        recordUpdate(update, false);
    }

    /**
     * Returns true if the specified tile coordinate is passable (the base
     * tile is passable and it is not in the footprint of an object).
     */
    public boolean isPassable (int tx, int ty)
    {
        return (StageSceneUtil.isPassable(
                    StageServer.tilemgr, _mmodel.getBaseTileId(tx, ty)) &&
                !checkContains(_footprints, tx, ty));
    }

    /**
     * Called by NPPs to determine whether or not they can stand at the
     * specified location.
     */
    public boolean mayStandAtLocation (BodyObject source, StageLocation loc)
    {
        return validateLocation(source, loc, false);
    }

    // documentation inherited from interface
    public void addObject (ClientObject caller, ObjectInfo info,
                           StageSceneService.ConfirmListener listener)
        throws InvocationException
    {
        InvocationException.requireAccess(caller, StageCodes.MODIFY_SCENE_ACCESS, _sscene);

        if (addObject(info, false, true)) {
            listener.requestProcessed();
        } else {
            listener.requestFailed(StageCodes.ERR_NO_OVERLAP);
        }
    }

    // documentation inherited from interface
    public void removeObjects (ClientObject caller, ObjectInfo[] info,
                               StageSceneService.ConfirmListener listener)
        throws InvocationException
    {
        InvocationException.requireAccess(caller, StageCodes.MODIFY_SCENE_ACCESS, _sscene);

        // create our scene update which will be stored in the database
        // and used to efficiently update clients
        ModifyObjectsUpdate update = new ModifyObjectsUpdate();
        update.init(_sscene.getId(), _sscene.getVersion(), null, info);

        log.info("Modifying objects '" + update + ".");
        recordUpdate(update, true);

        listener.requestProcessed();
    }

    @Override
    protected void gotSceneData (Object extras)
    {
        super.gotSceneData(extras);

        // cast some scene related bits
        _sscene = (StageScene)_scene;
        _mmodel = StageMisoSceneModel.getSceneModel(_scene.getSceneModel());

        // note the footprints of all objects and portals in this scene
        computeFootprints();
    }

    /**
     * Applies the supplied scene update to our runtime scene and then
     * fires off an invocation unit to apply the update to the scene
     * database. Finally the update is "recorded" which broadcasts the
     * update to all occupants of the scene and stores the update so that
     * it can be provided as a patch to future scene visitors.
     */
    protected void recordUpdate (SceneUpdate update, boolean tilesModified)
    {
        // do the standard update processing
        recordUpdate(update);

        // then recompute some internal structures if need be
        if (tilesModified) {
            sceneTilesModified();
        }
    }

    /**
     * Called when any change is made to a scene's base or object tiles
     * (resulting in a change in the way the scene looks). If any sneaky
     * business need be done to deal with said addition, it can be handled
     * here.
     */
    protected void sceneTilesModified ()
    {
        // compute the object footprints of all objects in this scene
        computeFootprints();
    }

    @Override
    protected void didStartup ()
    {
        super.didStartup();
        _ssobj = (StageSceneObject)_plobj;

        // register and fill in our stage scene service
        _ssobj.setStageSceneService(addProvider(this, StageSceneMarshaller.class));
    }

    @Override
    protected PlaceObject createPlaceObject ()
    {
        return new StageSceneObject();
    }

    @Override
    protected void bodyLeft (int bodyOid)
    {
        super.bodyLeft(bodyOid);

        // out ye go!
        _loners.remove(bodyOid);
    }

    @Override
    protected void updateLocation (BodyObject source, Location loc)
    {
        super.updateLocation(source, loc);

        // keep a rectangle around for each un-clustered occupant
        StageLocation sloc = (StageLocation) loc;
        int tx = MisoUtil.fullToTile(sloc.x), ty = MisoUtil.fullToTile(sloc.y);
        _loners.put(source.getOid(), new Rectangle(tx, ty, 1, 1));
    }

    /**
     * Computes the footprints of all objects and portals in this scene.
     * This is done when we are first resolved and following any
     * modifications to the scene's tile data.
     */
    protected void computeFootprints ()
    {
        _footprints.clear();
        _mmodel.visitObjects(new StageMisoSceneModel.ObjectVisitor() {
            public void visit (ObjectInfo info) {
                Rectangle foot = StageSceneUtil.getObjectFootprint(
                    StageServer.tilemgr, info.tileId, info.x, info.y);
                _footprints.add(foot);
            }
        });

//         Log.info("Computed footprints [where=" + where () +
//                  ", rects=" + StringUtil.toString(_footprints) + "].");

        // place the tile coordinates of our portals into a set for
        // efficient comparison with location coordinates
        _plocs.clear();
        for (Iterator<Portal> iter = _sscene.getPortals(); iter.hasNext(); ) {
            Portal port = iter.next();
            StageLocation loc = (StageLocation) port.loc;
            _plocs.add(new Point(MisoUtil.fullToTile(loc.x),
                                 MisoUtil.fullToTile(loc.y)));
        }
    }

    /**
     * Helper function for {@link #mayStandAtLocation} and {@link
     * #validateLocation(BodyObject,Location)}.
     */
    protected boolean validateLocation (BodyObject source, StageLocation loc,
                                        boolean allowPortals)
    {
        int tx = MisoUtil.fullToTile(loc.x), ty = MisoUtil.fullToTile(loc.y);

        // make sure the tile at that location is passable
        if (!StageSceneUtil.isPassable(StageServer.tilemgr,
                                    _mmodel.getBaseTileId(tx, ty))) {
//             Log.info("Rejecting non-passable loc [who=" + source.who() +
//                      ", loc=" + loc + "].");
            return false;
        }

        // if they're moving to stand on a portal, let them do it
        if (allowPortals && _plocs.contains(new Point(tx, ty))) {
            return true;
        }

        // if they are already standing on this tile, allow it
        SceneLocation cloc = _ssobj.occupantLocs.get(Integer.valueOf(source.getOid()));
        if (cloc != null) {
            StageLocation sloc = (StageLocation) cloc.loc;
            if (MisoUtil.fullToTile(sloc.x) == tx &&
                    MisoUtil.fullToTile(sloc.y) == ty) {
//                Log.info("Allowing adjust [who=" + source.who () +
//                    ", cloc=" + cloc + ", nloc=" + loc + "].");
                return true;
            }
        }

        // make sure they're not standing in a cluster footprint, an
        // object footprint, or in the same tile as another scene occupant
        if (checkContains(_ssobj.clusters, tx, ty) ||
            checkContains(_footprints, tx, ty) ||
            checkContains(_loners.values(), tx, ty)) {
//             Log.info("Rejecting loc [who=" + source.who() +
//                      ", loc=" + loc + ", inCluster=" +
//                      checkContains(_ssobj.clusters.iterator(), tx, ty) +
//                      ", inFootprint=" +
//                      checkContains(_footprints.iterator(), tx, ty) +
//                      ", onLoner=" +
//                      checkContains(_loners.values().iterator(), tx, ty) +
//                      "].");
            return false;
        }

        return true;
    }

    /** Helper function for {@link #validateLocation(BodyObject,StageLocation,boolean)}. */
    protected boolean checkContains (Iterable<? extends Rectangle> rects, int tx, int ty)
    {
        for (Rectangle rect : rects) {
            if (rect.contains(tx, ty)) {
//                 Log.info(StringUtil.toString(rect) + " contains " +
//                          StringUtil.coordsToString(tx, ty) + ".");
                return true;
            }
        }
        return false;
    }

    @Override
    protected SceneLocation computeEnteringLocation (BodyObject body, Portal from, Portal entry)
    {
        // sanity check
        if (entry == null) {
            log.warning("Requested to compute entering location for " +
                        "non-existent portal [where=" + where() +
                        ", who=" + body.who() + "].");
            entry = _sscene.getDefaultEntrance();
        }
        return computeEnteringLocation(body, entry, 1);
    }

    /**
     * Returns an entering location for body somewhere at least minDistance tiles away from entry.
     */
    protected SceneLocation computeEnteringLocation (BodyObject body, Portal entry,
        int minDistance)
    {
        return computeEnteringLocation(body, (StageLocation)entry.getOppLocation(), minDistance);

    }

    /**
     * Returns an entering location for body somewhere at least minDistance tiles away from base.
     */
    protected SceneLocation computeEnteringLocation (BodyObject body, StageLocation base,
        int minDistance)
    {
        MisoSceneMetrics metrics = StageSceneUtil.getMetrics();
        StageLocation sloc = base.clone();
        int tx = MisoUtil.fullToTile(sloc.x), ty = MisoUtil.fullToTile(sloc.y);
        int oidx = sloc.orient/2;
        int lidx = (oidx+3)%4; // rotate to the left
        int ridx = (oidx+1)%4; // rotate to the right

        // start in the row in front of the portal and search forward,
        // checking the center, then the spot(s) to the left, then the
        // spot(s) to the right (fanning out by one tile each time)
        final int MAX_FAN = 4;
      LOC_SEARCH:
        for (int fan = 1; fan < MAX_FAN; fan++) {
            tx += PORTAL_DX[oidx];
            ty += PORTAL_DY[oidx];
            if (fan < minDistance) {
                // increment until we get to our min distance
                continue;
            }

            // look in the center column
            if (checkEntry(metrics, body, tx, ty, sloc)) {
                break LOC_SEARCH;
            }

            // look to the left
            for (int lx = tx, ly = ty, ff = 0; ff < fan; ff++) {
                lx += PORTAL_DX[lidx];
                ly += PORTAL_DY[lidx];
                if (checkEntry(metrics, body, lx, ly, sloc)) {
                    break LOC_SEARCH;
                }
            }

            // look to the right
            for (int rx = tx, ry = ty, ff = 0; ff < fan; ff++) {
                rx += PORTAL_DX[ridx];
                ry += PORTAL_DY[ridx];
                if (checkEntry(metrics, body, rx, ry, sloc)) {
                    break LOC_SEARCH;
                }
            }

            // if this is our last pass and we didn't find anything,
            // revert back to the portal location
            if (fan == MAX_FAN-1) {
                sloc = base;
            }
        }

        tx = MisoUtil.fullToTile(sloc.x);
        ty = MisoUtil.fullToTile(sloc.y);
        _loners.put(body.getOid(), new Rectangle(tx, ty, 1, 1));

        return new SceneLocation(sloc, body.getOid());
    }

    /** Helper function for {@link #computeEnteringLocation(BodyObject,Portal,int)}. */
    protected boolean checkEntry (MisoSceneMetrics metrics, BodyObject body,
                                  int tx, int ty, StageLocation loc)
    {
        loc.x = MisoUtil.toFull(tx, metrics.finegran/2);
        loc.y = MisoUtil.toFull(ty, metrics.finegran/2);
        return validateLocation(body, loc, false);
    }

    @Override
    protected boolean validateLocation (BodyObject source, Location loc)
    {
        // TODO: make sure the user isn't warping to hell and gone (and if
        // they are, make sure they're an admin)
        return validateLocation(source, (StageLocation)loc, true);
    }

    @Override
    protected boolean canAddBody (ClusterRecord clrec, BodyObject body)
    {
        // make sure we have a setting for clusters of this size
        if (clrec.size() >= TARGET_SIZE.length-2) {
            return false;
        }

        // if this is a brand new cluster, just make it 2x2 and be done
        // with it
        Cluster cl = clrec.getCluster();
        if (cl.width == 0) {
            cl.width = 2;
            cl.height = 2;
//             Log.info("Created new cluster for " + body.who() +
//                      " (" + cl + ").");
            return true;
        }

//         Log.info("Maybe adding "  + body.who() + " to " + cl + ".");

        // if the cluster is already big enough, we're groovy
        int target = TARGET_SIZE[clrec.size()+1];
        if (cl.width >= target) {
            return true;
        }

        // make an expanded footprint and try to fit it somewhere
        int expand = target-cl.width;
        Rectangle rect = null;
        for (int ii = 0; ii < X_OFF.length; ii++) {
            rect = new Rectangle(cl.x + expand*X_OFF[ii],
                                 cl.y + expand*Y_OFF[ii],
                                 cl.width+expand, cl.height+expand);

            // if this rect overlaps objects, other clusters, portals or
            // impassable tiles, it's no good
            if (checkIntersects(_ssobj.clusters, rect, cl) ||
                checkIntersects(_footprints, rect, cl) ||
                checkPortals(rect) || checkViolatesPassability(rect)) {
                rect = null;
            } else {
                break;
            }
        }

        // if we couldn't expand in any direction, it's no go
        if (rect == null) {
//            Log.info("Couldn't expand cluster " + cl + ".");
            return false;
        }

        // now look to see if we just expanded our cluster over top of any
        // unsuspecting standers by and if so, attempt to subsume them
        for (int bodyOid : _loners.keySet()) {
            // skip ourselves
            if (bodyOid == body.getOid()) {
                continue;
            }

            // do the right thing with a person standing right on the
            // cluster (they're the person we're clustering with)
            Rectangle trect = _loners.get(bodyOid);
            if (trect.equals(cl)) {
                continue;
            }

            if (trect.intersects(rect)) {
                // make sure this person isn't already in our cluster
                ClusterObject clobj = clrec.getClusterObject();
                if (clobj != null && clobj.occupants.contains(bodyOid)) {
                    log.warning("Ignoring stale occupant [where=" + where() +
                                ", cluster=" + cl + ", occ=" + bodyOid + "].");
                    continue;
                }

                // make sure the subsumee exists
                final BodyObject bobj = (BodyObject)_omgr.getObject(bodyOid);
                if (bobj == null) {
                    log.warning("Can't subsume disappeared body " +
                                "[where=" + where() + ", cluster=" + cl +
                                ", boid=" + bodyOid + "].");
                    continue;
                }

                // we subsume nearby pirates by queueing up their addition
                // to our cluster to be processed after we finish adding
                // ourselves to this cluster
                final ClusterRecord fclrec = clrec;
                _omgr.postRunnable(new Runnable() {
                    public void run () {
                        try {
                            log.info("Subsuming " + bobj.who() +
                                     " into " + fclrec.getCluster() + ".");
                            fclrec.addBody(bobj);

                        } catch (InvocationException ie) {
                            log.info("Unable to subsume neighbor " +
                                     "[cluster=" + fclrec.getCluster() +
                                     ", neighbor=" + bobj.who() +
                                     ", cause=" + ie.getMessage() + "].");
                        }
                    }
                });
            }
        }

//         Log.info("Expanding cluster [cluster=" + cl +
//                  ", rect=" + rect + "].");
        cl.setBounds(rect);

        return true;
    }

    /** Helper function for {@link #canAddBody}. */
    protected boolean checkIntersects (
        Iterable<? extends Rectangle> rects, Rectangle rect, Rectangle ignore)
    {
        for (Rectangle trect : rects) {
            if (ignore != null && trect.equals(ignore)) {
                continue;
            }
            if (trect.intersects(rect)) {
                return true;
            }
        }
        return false;
    }

    /** Helper function for {@link #canAddBody}. */
    protected boolean checkPortals (Rectangle rect)
    {
        for (Point ppoint : _plocs) {
            if (rect.contains(ppoint)) {
                return true;
            }
        }
        return false;
    }

    /** Helper function for {@link #canAddBody}. */
    protected boolean checkViolatesPassability (Rectangle rect)
    {
        // we just check the whole damned thing, but it's not really that
        // expensive, so we're not going to worry about it
        // Check the bounds + 1 in each direction, so we can see if a fringer
        // blocks us.
        for (int xx = rect.x-1, ex = rect.x+rect.width+1; xx < ex; xx++) {
            for (int yy = rect.y-1, ey = rect.y+rect.height+1; yy < ey; yy++) {
                int btid = _mmodel.getBaseTileId(xx, yy);
                if ((btid == 0) &&
                    ((xx == rect.x-1) || (xx == rect.x + rect.width) ||
                     (yy == rect.y-1) || (yy == rect.y + rect.height))) {
                    // it's ok to have an unspecified base tile in the outer
                    // border
                    continue;

                } else if (!StageSceneUtil.isPassable(
                               StageServer.tilemgr, btid)) {
//                     Log.info("Cluster impassable " +
//                              "[rect=" + StringUtil.toString(rect) +
//                              ", spot=" + StringUtil.coordsToString(xx, yy) +
//                              "].");
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void bodyAdded (ClusterRecord clrec, BodyObject body)
    {
        super.bodyAdded(clrec, body);

//         Log.info(body.who() + " added to " + clrec + ".");

        // remove them from the loners map if they were in it
        int bodyOid = body.getOid();
        _loners.remove(bodyOid);

        Cluster cl = clrec.getCluster();
        if (clrec.size() == 1) {
            // if we're adding our first player, assign initial dimensions
            // to the cluster
            cl.width = 1; cl.height = 1;
            SceneLocation loc = locationForBody(bodyOid);
            if (loc == null) {
                log.warning("Foreign body added to cluster [clrec=" + clrec +
                            ", body=" + body.who() + "].");
                cl.x = 10; cl.y = 10;
            } else {
                StageLocation sloc = (StageLocation) loc.loc;
                cl.x = MisoUtil.fullToTile(sloc.x);
                cl.y = MisoUtil.fullToTile(sloc.y);
            }
            // we'll do everything else when occupant two shows up
            return;
        }

        // generate a list of all valid locations for this cluster
        List<SceneLocation> locs = StageSceneUtil.getClusterLocs(cl);

//         Log.info("Positioning " + clrec.size() + " bodies in " +
//                  StringUtil.toString(locs) + " for " + cl + ".");

        // make sure everyone is in their proper position
        for (Integer integer : clrec.keySet()) {
            int tbodyOid = integer.intValue();
            // leave the newly added player to last
            if (tbodyOid != bodyOid) {
                positionBody(cl, tbodyOid, locs);
            }
        }

        // finally position the new guy
        positionBody(cl, bodyOid, locs);
    }

    /** Helper function for {@link #bodyAdded}. */
    protected void positionBody (Cluster cl, int bodyOid, List<SceneLocation> locs)
    {
        SceneLocation sloc = _ssobj.occupantLocs.get(Integer.valueOf(bodyOid));
        if (sloc == null) {
            BodyObject user = (BodyObject)_omgr.getObject(bodyOid);
            String who = (user == null) ? ("" + bodyOid) : user.who();
            log.warning("Can't position locationless user " +
                        "[where=" + where() + ", cluster=" + cl +
                        ", boid=" + who + "].");
            return;
        }

        SceneLocation cloc = getClosestLoc(locs, sloc);
//         Log.info("Maybe moving " + bodyOid + " from " + sloc +
//                  " to " + cloc +
//                  " (avail=" + StringUtil.toString(locs) + ").");
        if (cloc != null && !cloc.loc.equivalent(sloc.loc)) {
            cloc.bodyOid = bodyOid;
//             Log.info("Moving " + bodyOid + " to " + cloc +
//                      " for " + cl + ".");
            _ssobj.updateOccupantLocs(cloc);
        }
    }

    @Override
    protected void bodyRemoved (ClusterRecord clrec, BodyObject body)
    {
        super.bodyRemoved(clrec, body);

        // if we've been reduced to a zero person cluster, we can skip all
        // this because the cluster will be destroyed when we return
        if (clrec.size() < 1) {
            return;
        }

        // reduce the bounds of the cluster if necessary
        int target = TARGET_SIZE[clrec.size()];
        Cluster cl = clrec.getCluster();
        // allow a cluster to remain one size larger than it needs to be
        // as long as there's more than one person in it
        if (cl.width <= (target + ((clrec.size() > 1) ? 1 : 0))) {
            return;
        }

        // otherwise shrink the cluster
        cl.width = target;
        cl.height = target;

        // generate a list of all valid locations for this cluster
        List<SceneLocation> locs = StageSceneUtil.getClusterLocs(cl);

        // make sure everyone is in their proper position
        for (Integer integer : clrec.keySet()) {
            int bodyOid = integer.intValue();
            // leave the newly added player to last
            if (bodyOid != body.getOid()) {
                positionBody(cl, bodyOid, locs);
            }
        }
    }

    @Override
    protected void checkCanCluster (BodyObject initiator, BodyObject target)
        throws InvocationException
    {
        StageOccupantInfo info = (StageOccupantInfo)_ssobj.occupantInfo.get(
            Integer.valueOf(target.getOid()));
        if (info == null) {
            log.warning("Have no occinfo for cluster target " +
                        "[where=" + where() + ", init=" + initiator.who() +
                        ", target=" + target.who() + "].");
            throw new InvocationException(INTERNAL_ERROR);
        }

        if (!info.isClusterable()) {
            throw new InvocationException(StageCodes.ERR_CANNOT_CLUSTER);
        }
    }

    /**
     * Locates and removes the location in the list closest to the
     * supplied location.
     */
    protected static SceneLocation getClosestLoc (
        List<SceneLocation>locs, SceneLocation optimalLocation)
    {
        StageLocation loc = (StageLocation) optimalLocation.loc;
        SceneLocation cloc = null;
        float cdist = Integer.MAX_VALUE;
        int cidx = -1;
        for (int ii = 0, ll = locs.size(); ii < ll; ii++) {
            SceneLocation tloc = locs.get(ii);
            StageLocation sl = (StageLocation) tloc.loc;
            float tdist = MathUtil.distance(loc.x, loc.y, sl.x, sl.y);
            if (tdist < cdist) {
                cloc = tloc;
                cdist = tdist;
                cidx = ii;
            }
        }
        if (cidx != -1) {
            locs.remove(cidx);
        }
        return cloc;
    }

    /** A casted reference to our scene object. */
    protected StageSceneObject _ssobj;

    /** A casted reference to our scene data. */
    protected StageScene _sscene;

    /** Our miso scene data extracted for convenience and efficiency. */
    protected StageMisoSceneModel _mmodel;

    /** Rectangles describing the footprints (in tile coordinates) of all
     * of our scene objects. */
    protected ArrayList<Rectangle> _footprints = Lists.newArrayList();

    /** Rectangles containing a "footprint" for the users that aren't in
     * any clusters. */
    protected HashIntMap<Rectangle> _loners = new HashIntMap<Rectangle>();

    /** Contains the (tile) coordinates of all of our portals. */
    protected HashSet<Point> _plocs = Sets.newHashSet();

    /** The dimensions of a cluster with the specified number of
     * occupants. */
    protected static final int[] TARGET_SIZE = {
        1, 2, // one person cluster
        3, 3, 3, // two to four person cluster
        4, 4, 4, 4, // five to eight person cluster
        5, 5, 5, 5, // nine to twelve person cluster
        6, 6, 6, 6, // thirteen to sixteen person cluster
        7, 7, 7, 7, 7, 7, 7, 7, // seventeen to twenty four person cluster
        8 // needed though we'll never expand to this size
    };

    /** Used by {@link #canAddBody}. */
    protected static final int[] X_OFF = { 0, -1, 0, -1 };

    /** Used by {@link #canAddBody}. */
    protected static final int[] Y_OFF = { 0, 0, -1, -1 };

    /** Used by {@link #computeEnteringLocation(BodyObject,Portal,int)}. */
    protected static final int[] PORTAL_DX = { 0, -1,  0, 1 }; // W N E S

    /** Used by {@link #computeEnteringLocation(BodyObject,Portal,int)}. */
    protected static final int[] PORTAL_DY = { 1,  0, -1, 0 }; // W N E S
}
