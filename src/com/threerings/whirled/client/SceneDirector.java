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

package com.threerings.whirled.client;

import java.util.ArrayList;
import java.util.Map;

import java.io.IOException;

import com.google.common.collect.Lists;

import com.samskivert.util.LRUHashMap;
import com.samskivert.util.ResultListener;
import com.threerings.presents.client.BasicDirector;
import com.threerings.presents.client.Client;

import com.threerings.crowd.client.LocationDirector;
import com.threerings.crowd.client.LocationObserver;
import com.threerings.crowd.data.PlaceConfig;

import com.threerings.whirled.client.persist.SceneRepository;
import com.threerings.whirled.data.Scene;
import com.threerings.whirled.data.SceneCodes;
import com.threerings.whirled.data.SceneModel;
import com.threerings.whirled.data.SceneUpdate;
import com.threerings.whirled.util.NoSuchSceneException;
import com.threerings.whirled.util.SceneFactory;
import com.threerings.whirled.util.WhirledContext;

import static com.threerings.whirled.Log.log;

/**
 * The scene director is the client's interface to all things scene related. It interfaces with the
 * scene repository to ensure that scene objects are available when the client enters a particular
 * scene. It handles moving from scene to scene (it coordinates with the {@link LocationDirector}
 * in order to do this).
 *
 * <p> Note that when the scene director is in use instead of the location director, scene ids
 * instead of place oids will be supplied to {@link LocationObserver#locationMayChange} and {@link
 * LocationObserver#locationChangeFailed}.
 */
public class SceneDirector extends BasicDirector
    implements SceneCodes, LocationDirector.FailureHandler, SceneReceiver,
               SceneService.SceneMoveListener
{
    /**
     * Used to recover from a problem after a completed moveTo.
     */
    public static interface MoveHandler
    {
        /**
         * Should instruct the client to move the last known working location (as well as clean up
         * after the failed moveTo request).
         */
        public void recoverMoveTo (int previousSceneId);
    }

    /**
     * Creates a new scene director with the specified context.
     *
     * @param ctx the active client context.
     * @param locdir the location director in use on the client, with which the scene director will
     * coordinate when changing location.
     * @param screp the entity from which the scene director will load scene data from the local
     * client scene storage. This may be null when the SceneDirector is constructed, but it should
     * be supplied via {@link #setSceneRepository} prior to really using this director.
     * @param fact the factory that knows which derivation of {@link Scene} to create for the
     * current system.
     */
    public SceneDirector (WhirledContext ctx, LocationDirector locdir, SceneRepository screp,
                          SceneFactory fact)
    {
        super(ctx);

        // we'll need these for later
        _ctx = ctx;
        _locdir = locdir;
        setSceneRepository(screp);
        _fact = fact;

        // set ourselves up as a failure handler with the location director because we need to do
        // special processing
        _locdir.setFailureHandler(this);

        // register for scene notifications
        _ctx.getClient().getInvocationDirector().registerReceiver(new SceneDecoder(this));
    }

    /**
     * Set the scene repository.
     */
    public void setSceneRepository (SceneRepository screp)
    {
        _screp = screp;
        _scache.clear();
    }

    /**
     * Returns the display scene object associated with the scene we currently occupy or null if we
     * currently occupy no scene.
     */
    public Scene getScene ()
    {
        return _scene;
    }

    /**
     * Returns true if there is a pending move request.
     */
    public boolean movePending ()
    {
        return (_pendingSceneId > 0);
    }

    /**
     * Requests that this client move the specified scene. A request will be made and when the
     * response is received, the location observers will be notified of success or failure.
     *
     * @return true if the move to request was issued, false if it was rejected by a location
     * observer or because we have another request outstanding.
     */
    public boolean moveTo (int sceneId)
    {
        // make sure the sceneId is valid
        if (sceneId < 0) {
            log.warning("Refusing moveTo(): invalid sceneId " + sceneId + ".");
            return false;
        }

        // sanity-check the destination scene id
        if (sceneId == _sceneId) {
            log.warning("Refusing request to move to the same scene", "sceneId", sceneId);
            return false;
        }

        // prepare to move to this scene (sets up pending data)
        if (!prepareMoveTo(sceneId, null)) {
            return false;
        }

        // do the deed
        sendMoveRequest();
        return true;
    }

    /**
     * Prepares to move to the requested scene. The location observers are asked to ratify the move
     * and our pending scene mode is loaded from the scene repository. This can be called by
     * cooperating directors that need to coopt the moveTo process.
     */
    public boolean prepareMoveTo (int sceneId, ResultListener<PlaceConfig> rl)
    {
        // first check to see if our observers are happy with this move request
        if (!_locdir.mayMoveTo(sceneId, rl)) {
            return false;
        }

        // we need to call this both to mark that we're issuing a move request and to check to see
        // if the last issued request should be considered stale
        boolean refuse = _locdir.checkRepeatMove();

        // complain if we're over-writing a pending request
        if (movePending()) {
            if (refuse) {
                log.warning("Refusing moveTo; We have a request outstanding",
                    "psid", _pendingSceneId, "nsid", sceneId);
                return false;
            } else {
                log.warning("Overriding stale moveTo request",
                    "psid", _pendingSceneId, "nsid", sceneId);
            }
        }

        // load up the pending scene so that we can communicate its most recent version to the
        // server
        _pendingModel = loadSceneModel(sceneId);

        // make a note of our pending scene id
        _pendingSceneId = sceneId;

        // all systems go
        return true;
    }

    /**
     * Returns the model loaded in preparation for a scene transition. This is made available only
     * for cooperating directors which may need to coopt the scene transition process. The pending
     * model is only valid immediately following a call to {@link #prepareMoveTo}.
     */
    public SceneModel getPendingModel ()
    {
        return _pendingModel;
    }

    /**
     * Returns the scene id set in preparation for a scene transition.  As with
     * {@link #getPendingModel}, this is for cooperating directors.
     */
    public int getPendingSceneId ()
    {
        return _pendingSceneId;
    }

    // from interface SceneService.SceneMoveListener
    public void moveSucceeded (int placeId, PlaceConfig config)
    {
        // our move request was successful, deal with subscribing to our new place object
        _locdir.didMoveTo(placeId, config);

        // since we're committed to moving to the new scene, we'll parallelize and go ahead and
        // load up the new scene now rather than wait until subscription to our place object
        // succeeds

        // keep track of our previous scene info
        _previousSceneId = _sceneId;

        // clear out the old info
        clearScene();

        // make the pending scene the active scene
        _sceneId = _pendingSceneId;
        _pendingSceneId = -1;

        // load the new scene model
        _model = loadSceneModel(_sceneId);

        // complain if we didn't find a scene
        if (_model == null) {
            log.warning("Aiya! Unable to load scene [sid=" + _sceneId + ", plid=" + placeId + "].");
            return;
        }

        // and finally create a display scene instance with the model and the place config
        _scene = _fact.createScene(_model, config);

        handlePendingForcedMove();
    }

    // from interface SceneService.SceneMoveListener
    public void moveSucceededWithUpdates (int placeId, PlaceConfig config, SceneUpdate[] updates)
    {
        log.info("Got updates", "placeId", placeId, "config", config, "updates", updates);

        // apply the updates to our cached scene
        SceneModel model = loadSceneModel(_pendingSceneId);
        boolean failure = false;
        for (SceneUpdate element : updates) {
            try {
                element.validate(model);
            } catch (IllegalStateException ise) {
                log.warning("Scene update failed validation",
                    "model", model, "update", element, "error", ise.getMessage());
                failure = true;
                break;
            }

            try {
                element.apply(model);
            } catch (Exception e) {
                log.warning("Failure applying scene update",
                    "model", model, "update", element, e);
                failure = true;
                break;
            }
        }

        if (failure) {
            // delete the now half-booched scene model from the repository
            try {
                _screp.deleteSceneModel(_pendingSceneId);
            } catch (IOException ioe) {
                log.warning("Failure removing booched scene model",
                    "sceneId", _pendingSceneId, ioe);
            }

            // act as if the scene move failed; we'll be in a funny state because the server thinks
            // we've changed scenes, but the client can try again without its booched scene model
            requestFailed(INTERNAL_ERROR);
            return;
        }

        // store the updated version
        persistSceneModel(model);

        // finally pass through to the normal success handler
        moveSucceeded(placeId, config);
    }

    // from interface SceneService.SceneMoveListener
    public void moveSucceededWithScene (int placeId, PlaceConfig config, SceneModel model)
    {
        log.info("Got updated scene model",
            "placeId", placeId, "config", config,
            "scene", (model.sceneId + "/" + model.name + "/" + model.version));

        // update the model in the repository
        persistSceneModel(model);

        // update our scene cache
        _scache.put(Integer.valueOf(model.sceneId), model);

        // and pass through to the normal move succeeded handler
        moveSucceeded(placeId, config);
    }

    // from interface SceneService.SceneMoveListener
    public void moveRequiresServerSwitch (String hostname, int[] ports)
    {
        // ship on over to the other server
        _ctx.getClient().moveToServer(hostname, ports, new SceneService.ConfirmListener() {
            public void requestProcessed () {
                // resend our move request now that we're connected to the new server
                sendMoveRequest();
            }
            public void requestFailed (String reason) {
                SceneDirector.this.requestFailed(reason);
            }
        });
    }

    // from interface SceneService.SceneMoveListener
    public void requestFailed (String reason)
    {
        // let our observers know that something has gone horribly awry
        _locdir.failedToMoveTo(_pendingSceneId, reason);
    }

    /**
     * Called by SceneController instances to tell us about an update to the current scene.
     */
    public void updateReceived (SceneUpdate update)
    {
        _scene.updateReceived(update);
        persistSceneModel(_scene.getSceneModel());
    }

    /**
     * Called to clean up our place and scene state information when we leave a scene.
     */
    public void didLeaveScene ()
    {
        // let the location director know what's up
        _locdir.didLeavePlace();

        // clear out our own scene state
        clearScene();
    }

    /**
     * Sets the moveHandler for use in {@link #recoverFailedMove}.
     */
    public void setMoveHandler (MoveHandler handler)
    {
        if (_moveHandler != null) {
            log.warning("Requested to set move handler, but we've already got one. The " +
                        "conflicting entities will likely need to perform more sophisticated " +
                        "coordination to deal with failures.",
                        "old", _moveHandler, "new", handler);

        } else {
            _moveHandler = handler;
        }
    }

    // from interface SceneReceiver
    public void forcedMove (final int sceneId)
    {
        // if we're in the middle of a move, we can't abort it or we will screw everything up, so
        // just finish up what we're doing and assume that the repeated move request was the
        // spurious one as it would be in the case of lag causing rapid-fire repeat requests
        if (movePending()) {
            if (_pendingSceneId == sceneId) {
                log.info("Dropping forced move because we have a move pending",
                    "pendId", _pendingSceneId, "reqId", sceneId);
            } else {
                log.info("Delaying forced move because we have a move pending",
                    "pendId", _pendingSceneId, "reqId", sceneId);
                addPendingForcedMove(new Runnable() {
                    public void run () {
                        forcedMove(sceneId);
                    }
                });
            }
            return;
        }

        log.info("Moving at request of server", "sceneId", sceneId);
        // clear out our old scene and place data
        didLeaveScene();
        // move to the new scene
        moveTo(sceneId);
    }

    // from interface LocationDirector.FailureHandler
    public void recoverFailedMove (int placeId)
    {
        // if we're currently in a scene, then just stay there
        if (_sceneId > 0) {
            return;
        }

        // we'll need this momentarily
        int justAttemptedSceneId = _pendingSceneId;
        _pendingSceneId = -1;

        // clear out our now bogus scene tracking info
        clearScene();

        // if we were previously somewhere (and that somewhere isn't where we just tried to go),
        // try going back to that happy place
        if (_previousSceneId != -1 && _previousSceneId != justAttemptedSceneId) {
            // if we have a move handler use that
            if (_moveHandler != null) {
                _moveHandler.recoverMoveTo(_previousSceneId);
            } else {
                moveTo(_previousSceneId);
            }
        }

        handlePendingForcedMove();
    }

    protected void sendMoveRequest ()
    {
        // check the version of our cached copy of the scene to which we're requesting to move; if
        // we were unable to load it, assume a cached version of zero
        int sceneVers = 0;
        if (_pendingModel != null) {
            sceneVers = _pendingModel.version;
        }

        // issue a moveTo request
        log.info("Issuing moveTo(" + _pendingSceneId + ", " + sceneVers + ").");
        _sservice.moveTo(_pendingSceneId, sceneVers, this);
    }

    /**
     * Clears out our current scene information and releases the scene model for the loaded scene
     * back to the cache.
     */
    protected void clearScene ()
    {
        // clear out our scene id info
        _sceneId = -1;

        // clear out our references
        _model = null;
        _scene = null;
    }

    /**
     * Loads a scene from the repository. If the scene is cached, it will be returned from the
     * cache instead.
     */
    protected SceneModel loadSceneModel (int sceneId)
    {
        // first look in the model cache
        Integer key = Integer.valueOf(sceneId);
        SceneModel model = _scache.get(key);

        // load from the repository if it's not cached
        if (model == null) {
            try {
                model = _screp.loadSceneModel(sceneId);
                _scache.put(key, model);

            } catch (NoSuchSceneException nsse) {
                // nothing special here, just fall through and return null

            } catch (IOException ioe) {
                // complain first, then return null
                log.warning("Error loading scene", "scid", sceneId, "error", ioe);
            }
        }

        return model;
    }

    /**
     * Persist the scene model to the clientside persistant cache.
     */
    protected void persistSceneModel (SceneModel model)
    {
        // store the updated scene in the repository
        try {
            _screp.storeSceneModel(model);
        } catch (IOException ioe) {
            log.warning("Failed to update repository with updated scene",
                "sceneId", model.sceneId, "nvers", model.version, ioe);
        }
    }

    @Override
    public void clientDidLogoff (Client client)
    {
        super.clientDidLogoff(client);

        // clear out our business
        clearScene();
        _scache.clear();
        _pendingSceneId = -1;
        _pendingModel = null;
        _pendingForcedMoves.clear();
        _previousSceneId = -1;
        _sservice = null;
    }

    public void cancelMoveRequest ()
    {
        _pendingSceneId = -1;
        _pendingModel = null;
        handlePendingForcedMove();
    }

    @Override
    protected void registerServices (Client client)
    {
        client.addServiceGroup(WHIRLED_GROUP);
    }

    @Override
    protected void fetchServices (Client client)
    {
        // get a handle on our scene service
        _sservice = client.requireService(SceneService.class);
    }

    public void addPendingForcedMove (Runnable move)
    {
        _pendingForcedMoves.add(move);
    }

    protected void handlePendingForcedMove ()
    {
        if (!_pendingForcedMoves.isEmpty()) {
            _ctx.getClient().getRunQueue().postRunnable(_pendingForcedMoves.remove(0));
        }
    }

    /** Access to general client services. */
    protected WhirledContext _ctx;

    /** Access to our scene services. */
    protected SceneService _sservice;

    /** The client's active location director. */
    protected LocationDirector _locdir;

    /** The entity via which we load scene data. */
    protected SceneRepository _screp;

    /** The entity we use to create scenes from scene models. */
    protected SceneFactory _fact;

    /** A cache of scene model information. */
    protected Map<Integer, SceneModel> _scache = new LRUHashMap<Integer, SceneModel>(5);

    /** The display scene object for the scene we currently occupy. */
    protected Scene _scene;

    /** The scene model for the scene we currently occupy. */
    protected SceneModel _model;

    /** The id of the scene we currently occupy. */
    protected int _sceneId = -1;

    /** Our most recent copy of the scene model for the scene we're about to enter. */
    protected SceneModel _pendingModel;

    /** The id of the scene for which we have an outstanding moveTo request, or -1 if we have no
     * outstanding request. */
    protected int _pendingSceneId = -1;

    /** The id of the scene we previously occupied. */
    protected int _previousSceneId = -1;

    /** Reference to our move handler. */
    protected MoveHandler _moveHandler = null;

    /** Forced move actions we should take once we complete the move we're in the middle of. */
    protected ArrayList<Runnable> _pendingForcedMoves = Lists.newArrayList();

}
