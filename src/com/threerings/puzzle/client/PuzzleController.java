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

package com.threerings.puzzle.client;

import java.util.List;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.google.common.collect.Lists;

import com.samskivert.util.CollectionUtil;
import com.samskivert.util.ObserverList;

import com.samskivert.swing.util.MouseHijacker;

import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.ElementUpdateListener;
import com.threerings.presents.dobj.ElementUpdatedEvent;
import com.threerings.presents.dobj.NamedAttributeListener;

import com.threerings.crowd.client.PlaceControllerDelegate;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.media.FrameParticipant;

import com.threerings.parlor.game.client.GameController;
import com.threerings.parlor.game.data.GameObject;

import com.threerings.puzzle.data.Board;
import com.threerings.puzzle.data.PuzzleCodes;
import com.threerings.puzzle.data.PuzzleObject;
import com.threerings.puzzle.util.PuzzleContext;

import static com.threerings.puzzle.Log.log;

/**
 * The puzzle game controller handles logical actions for a puzzle game.
 */
public abstract class PuzzleController extends GameController
    implements PuzzleCodes
{
    /** The action command to toggle chatting mode. */
    public static final String TOGGLE_CHATTING = "toggle_chat";

    /** Used by {@link PuzzleController#fireWhenActionCleared}. */
    public static interface ClearPender
    {
        /** {@link #actionCleared} return code. */
        public static final int RESTART_ACTION = -1;

        /** {@link #actionCleared} return code. */
        public static final int CARE_NOT = 0;

        /** {@link #actionCleared} return code. */
        public static final int NO_RESTART_ACTION = 1;

        /**
         * Called when the action is fully cleared.
         *
         * @return One of {@link #RESTART_ACTION}, {@link #CARE_NOT} or
         * {@link #NO_RESTART_ACTION}.
         */
        public int actionCleared ();
    }

    @Override
    protected void didInit ()
    {
        super.didInit();

        _panel = (PuzzlePanel)_view;
        _pctx = (PuzzleContext)_ctx;

        // initialize the puzzle panel
        _panel.init(_config);

        // initialize the board view
        _pview = _panel.getBoardView();
        _pview.setController(this);
    }

    /**
     * Creates and returns a new board model.
     */
    protected abstract Board newBoard ();

    /**
     * Returns the board associated with the puzzle.
     */
    public Board getBoard ()
    {
        return _pboard;
    }

    /**
     * Returns the player's index in the list of players for the game.
     */
    public int getPlayerIndex ()
    {
        return _pidx;
    }

    @Override
    public void setGameOver (boolean gameOver)
    {
        super.setGameOver(gameOver);

        // clear the action if we're informed that the game is over early by the client
        if (gameOver) {
            clearAction();
        }
    }

    /**
     * Returns true if the puzzle has action, false if the action is cleared or it is suspended.
     */
    public boolean hasAction ()
    {
        return (_astate == ACTION_GOING);
    }

    /**
     * Sets whether we're focusing on the chat window rather than the puzzle.
     */
    public void setChatting (boolean chatting)
    {
        // ignore the request if we're already there
        if ((isChatting() == chatting) ||
            // ..or if we want to initiate chatting and..
            // we either can't right now or we don't have action
            (chatting && (!canStartChatting() || !hasAction()))) {
            return;
        }

        // update the panel
        _panel.setPuzzleGrabsKeys(!chatting);

        // if we're moving focus to chat..
        if (chatting) {
            if (_unpauser != null) {
                log.warning("Huh? Already have a mouse unpauser?");
                _unpauser.release();
            }
            _unpauser = new Unpauser(_panel);

        } else {
            if (_unpauser != null) {
                _unpauser.release();
                _unpauser = null;
            }
        }

        // update the chatting state
        _chatting = chatting;

        // dispatch the change to our delegates
        applyToDelegates(new DelegateOp(PuzzleControllerDelegate.class) {
            @Override
            public void apply (PlaceControllerDelegate delegate) {
                ((PuzzleControllerDelegate)delegate).setChatting(_chatting);
            }
        });

        // and check if we should be suspending the action during this pause
        if (supportsActionPause()) {
            // clear the action if we're pausing, resume it if we're unpausing
            if (chatting) {
                clearAction();
            } else {
                safeStartAction();
            }
            _pview.setPaused(chatting);
        }
    }

    /**
     * Get the (untranslated) string to display when the puzzle is paused.
     */
    public String getPauseString ()
    {
        return "m.paused";
    }

    /**
     * Derived classes should override this and return false if their action should not be paused
     * when the user switches control to the chat area.
     */
    protected boolean supportsActionPause ()
    {
        return true;
    }

    /**
     * Can we start chatting at this juncture?
     */
    protected boolean canStartChatting ()
    {
        // check with the delegates
        final boolean[] canChatNow = new boolean[] { true };
        applyToDelegates(new DelegateOp(PuzzleControllerDelegate.class) {
            @Override
            public void apply (PlaceControllerDelegate delegate) {
                canChatNow[0] =
                    ((PuzzleControllerDelegate)delegate).canStartChatting() && canChatNow[0];
            }
        });
        return canChatNow[0];
    }

    /**
     * Returns true if the puzzle has been defocused because the player is doing some chatting.
     */
    public boolean isChatting ()
    {
        return _chatting;
    }

    @Override
    public void willEnterPlace (PlaceObject plobj)
    {
        super.willEnterPlace(plobj);

        // get a casted reference to our puzzle object
        _puzobj = (PuzzleObject)plobj;
        _puzobj.addListener(_kolist);
        _puzobj.addListener(_mlist);

        // listen to key events..
        _pctx.getKeyDispatcher().addGlobalKeyListener(_globalKeyListener);

        // save off our player index
        _pidx = _puzobj.getPlayerIndex(_pctx.getUsername());

        // generate the starting board
        generateNewBoard();

        // if the game is already in play, start up the action
        if (_puzobj.isInPlay() && _puzobj.isActivePlayer(_pidx)) {
            startAction();
        }
    }

    @Override
    public void mayLeavePlace (PlaceObject plobj)
    {
        super.mayLeavePlace(plobj);

        // flush any pending progress events
        sendProgressUpdate();
    }

    @Override
    public void didLeavePlace (PlaceObject plobj)
    {
        super.didLeavePlace(plobj);

        // clean up and clear out
        clearAction();

        // stop listening to key events..
        _pctx.getKeyDispatcher().removeGlobalKeyListener(_globalKeyListener);

        // clear out the puzzle object
        if (_puzobj != null) {
            _puzobj.removeListener(_mlist);
            _puzobj.removeListener(_kolist);
            _puzobj = null;
        }
    }

    /**
     * Puzzles that do not have "action" that starts and stops (via {@link #startAction} and
     * {@link #clearAction}) when the puzzle starts and stops can override this method and return
     * false.
     */
    protected boolean isActionPuzzle ()
    {
        return true;
    }

    /**
     * Indicates whether the action should start immediately as a result of
     * {@link GameController#gameDidStart} being called. If a puzzle wishes to do some beginning
     * of the game fun stuff, like display a tutorial screen, they can veto the action start and
     * then start it themselves later.
     */
    protected boolean startActionImmediately ()
    {
        return true;
    }

    @Override
    public void attributeChanged (AttributeChangedEvent event)
    {
        String name = event.getName();

        // deal with game state changes
        if (name.equals(PuzzleObject.STATE)) {
            switch (event.getIntValue()) {
            case PuzzleObject.IN_PLAY:
                // we have to postpone all game starting activity until the current action has
                // ended; only after all the animations have been completed will everything be in a
                // state fit for starting back up again
                fireWhenActionCleared(new ClearPender() {
                    public int actionCleared () {
                        // do the standard game did start business
                        gameDidStart();
                        // we don't always start the action immediately
                        return startActionImmediately() ? RESTART_ACTION : CARE_NOT;
                    }
                });
                break;

            case PuzzleObject.GAME_OVER:
                // similarly we have to postpone game ending activity until the current action has
                // ended clean up and clear out
                clearAction();
                // wait until the action is cleared before we roll down to our delegates and do all
                // that business
                fireWhenActionCleared(new ClearPender() {
                    public int actionCleared () {
                        gameDidEnd();
                        return CARE_NOT;
                    }
                });
                break;

            default:
                super.attributeChanged(event);
                break;
            }

        } else if (name.equals(PuzzleObject.SESSION_ID)) {
            // Need to clear out stale events.  If we don't, we could send events that claim to be
            // from the new session that are actually from the old session.
            _events.clear();
        }
    }

    @Override
    protected void gameWillReset ()
    {
        super.gameWillReset();

        // stop the old action
        clearAction();

        // when the server gets around to resetting the game, we'll get a 'state => IN_PLAY'
        // message which will result in gameDidStart() being called and starting the action back up
    }

    /**
     * Called when a new board is set.
     */
    public void setBoard (Board board)
    {
        // we don't need to do anything by default
    }

    /**
     * Derived classes should override this method and do whatever is necessary to start up the
     * action for their puzzle. This could be called when the user is already in the "room" and
     * the game starts, or immediately upon entering the room if the game is already started (for
     * example if they disconnected and reconnected to a game already in progress).
     */
    protected void startAction ()
    {
        // do nothing if we're not an action puzzle
        if (!isActionPuzzle()) {
            return;
        }

        // refuse to start the action if our puzzle view is hidden
        if (_pidx != -1 && !_pview.isShowing()) {
            log.warning("Refusing to start action on hidden puzzle.", new Exception());
            return;
        }

        // refuse to start the action if it's already going
        if (_astate != ACTION_CLEARED) {
            log.warning("Action state inappropriate for startAction()", "astate", _astate,
                        new Exception());
            return;
        }

        if (isChatting() && supportsActionPause()) {
            log.info("Not starting action, player is chatting in a puzzle that supports " +
                     "pausing the action.");
            return;
        }

        log.debug("Starting puzzle action.");

        // register the game progress updater; it may already be updated because we can cycle
        // through clearing the action and starting it again before the updater gets a chance to
        // unregister itself
        if (!_pctx.getFrameManager().isRegisteredFrameParticipant(_updater)) {
            _pctx.getFrameManager().registerFrameParticipant(_updater);
        }

        // make a note that we've started the action
        _astate = ACTION_GOING;

        // let our panel know what's up
        _panel.startAction();

        // and if we're not currently chatting, set the puzzle to grab keys and for the chatbox to
        // look disabled
        if (!isChatting()) {
            _panel.setPuzzleGrabsKeys(true);
        }

        // let our delegates do their business
        applyToDelegates(new DelegateOp(PuzzleControllerDelegate.class) {
            @Override
            public void apply (PlaceControllerDelegate delegate) {
                ((PuzzleControllerDelegate)delegate).startAction();
            }
        });
    }

    /**
     * If it is not known whether the puzzle board view has finished animating its final bits
     * after a previous call to {@link #clearAction}, this method should be used instead of
     * {@link #startAction} as it will wait until the action is confirmedly over before starting
     * it anew.
     */
    protected void safeStartAction ()
    {
        // do nothing if we're not an action puzzle
        if (!isActionPuzzle()) {
            return;
        }

        fireWhenActionCleared(new ClearPender() {
            public int actionCleared () {
                return RESTART_ACTION;
            }
        });
    }

    /**
     * Called when the game has ended or when it is going to reset and when the client leaves the
     * game "room". This method does not always immediately clear the action, but may mark the
     * clear as pending if the action cannot yet be cleared (as indicated by
     * {@link #canClearAction}). The action will eventually be cleared which will result in a call
     * to {@link #actuallyClearAction} which is what derived classes should override to do their
     * action clearing business.
     */
    protected void clearAction ()
    {
        // do nothing if we're not an action puzzle
        if (!isActionPuzzle()) {
            return;
        }

        // no need to clear if we're already cleared or clearing
        if (_astate == CLEAR_PENDING || _astate == ACTION_CLEARED) {
            return;
        }

        log.debug("Attempting to clear puzzle action.");

        // put ourselves into a pending clear state and attempt to clear the action
        _astate = CLEAR_PENDING;
        maybeClearAction();
    }

    /**
     * This method is called by the {@link PuzzleBoardView} when all action on the board has
     * finished.
     */
    protected void boardActionCleared ()
    {
        // if we have a clear pending, this could be the trigger that allows us to clear our action
        maybeClearAction();
    }

    /**
     * Queues up code to be invoked when the action is completely cleared (including all remaining
     * interesting sprites and animations on the puzzle board).
     */
    protected void fireWhenActionCleared (ClearPender pender)
    {
        // if the action is already ended, fire this pender immediately
        if (_astate == ACTION_CLEARED) {
            if (pender.actionCleared() == ClearPender.RESTART_ACTION) {
                log.debug("Restarting action at behest of pender " + pender + ".");
                startAction();
            }

        } else {
            log.debug("Queueing action pender " + pender + ".");
            _clearPenders.add(pender);
        }
    }

    /**
     * Returns whether or not it is safe to clear the action. The default behavior is to not allow
     * the action to be cleared until all interesting sprites and animations in the board view
     * have finished. If derived classes or delegates wish to postpone the clearing of the action,
     * they can return false from this method, but they must then be sure to call
     * {@link #maybeClearAction} when whatever condition that caused them to desire to postpone
     * action clearing has finally been satisfied.
     */
    protected boolean canClearAction ()
    {
        final boolean[] canClear = new boolean[1];
        canClear[0] = (_pview.getActionCount() == 0);
//         if (!canClear[0]) {
//             _pview.dumpActors();
//             PuzzleBoardView.DEBUG_ACTION = true;
//         }

        // let our delegates do their business
        applyToDelegates(new DelegateOp(PuzzleControllerDelegate.class) {
            @Override
            public void apply (PlaceControllerDelegate delegate) {
                canClear[0] = canClear[0] && ((PuzzleControllerDelegate)delegate).canClearAction();
            }
        });

        return canClear[0];
    }

    /**
     * Called to effect the actual clearing of our action if we've received some asynchronous
     * trigger that indicates that it may well be safe now to clear the action.
     */
    protected void maybeClearAction ()
    {
        if (_astate == CLEAR_PENDING && canClearAction()) {
            actuallyClearAction();
//        } else {
//            log.info("Not clearing action", "astate", _astate, "canClear", canClearAction());
        }
    }

    /**
     * Performs the actual process of clearing the action for this puzzle. This is only called
     * after it is known to be safe to clear the action. Derived classes can override this method
     * and clear out anything that is not needed while the puzzle's "action" is not going (timers,
     * etc.). Anything that is cleared out here should be recreated in {@link #startAction}.
     */
    protected void actuallyClearAction ()
    {
        log.debug("Actually clearing action.");

        // make a note that we've cleared the action
        _astate = ACTION_CLEARED;
//         PuzzleBoardView.DEBUG_ACTION = false;

        // let our delegates do their business
        applyToDelegates(new DelegateOp(PuzzleControllerDelegate.class) {
            @Override
            public void apply (PlaceControllerDelegate delegate) {
                ((PuzzleControllerDelegate)delegate).clearAction();
            }
        });

        // let our panel know what's up
        _panel.clearAction();
        _panel.setPuzzleGrabsKeys(false); // let the user chat

        // deliver one final update to the server
        sendProgressUpdate();

        // let derived classes do things
        try {
            actionWasCleared();
        } catch (Exception e) {
            log.warning("Choked in actionWasCleared", e);
        }

        // notify any penders that the action has cleared
        final int[] results = new int[2];
        _clearPenders.apply(new ObserverList.ObserverOp<ClearPender>() {
            public boolean apply (ClearPender observer) {
                switch (observer.actionCleared()) {
                case ClearPender.RESTART_ACTION: results[0]++; break;
                case ClearPender.NO_RESTART_ACTION: results[1]++; break;
                }
                return true;
            }
        });
        _clearPenders.clear();

        // if there are no refusals and at least one restart request, go ahead and restart the
        // action now
        if (results[1] == 0 && results[0] > 0) {
            startAction();
        }
    }

    /**
     * Called when the action was actually cleared, but before the action observers are notified.
     */
    protected void actionWasCleared ()
    {
    }

    @Override
    public boolean handleAction (ActionEvent action)
    {
        String cmd = action.getActionCommand();
        if (cmd.equals(TOGGLE_CHATTING)) {
            setChatting(!isChatting());

        } else {
            return super.handleAction(action);
         }

        return true;
    }

    /**
     * Returns the delay in milliseconds between sending each progress update event to the server.
     * Derived classes may wish to override this to send their progress updates more or less
     * frequently than the default.
     */
    protected long getProgressInterval ()
    {
        return DEFAULT_PROGRESS_INTERVAL;
    }

    /**
     * Signal the game to generate and distribute a new board.
     */
    protected void generateNewBoard ()
    {
        // wait for any animations or sprites in the board to finish their business before setting
        // the board into place
        fireWhenActionCleared(new ClearPender() {
            public int actionCleared () {
                // update the player board
                _pboard = newBoard();
                if (_puzobj.seed != 0) {
                    _pboard.initializeSeed(_puzobj.seed);
                }
                setBoard(_pboard);
                _pview.setBoard(_pboard);

                // and repaint things
                _pview.repaint();

                // let our delegates do their business
                applyToDelegates(new DelegateOp(PuzzleControllerDelegate.class) {
                    @Override
                    public void apply (PlaceControllerDelegate delegate) {
                        ((PuzzleControllerDelegate)delegate).setBoard(_pboard);
                    }
                });

                return CARE_NOT;
            }
        });
    }

    /**
     * Returns the number of progress events currently queued up for sending to the server with
     * the next progress update.
     */
    public int getEventCount ()
    {
        return _events.size();
    }

    /**
     * Are we syncing boards for this puzzle? By default, we defer to the PuzzlePanel and its
     * runtime config.
     */
    protected boolean isSyncingBoards ()
    {
        return PuzzlePanel.isSyncingBoards();
    }

    /**
     * Adds the given progress event and a snapshot of the supplied board state to the set of
     * progress events and associated board states for later transmission to the server.
     */
    public void addProgressEvent (int event, Board board)
    {
        // make sure they don't queue things up at strange times
        if (_puzobj.state != PuzzleObject.IN_PLAY) {
            log.warning("Rejecting progress event; game not in play",
                "puzobj", _puzobj.which(), "event", event);
            return;
        }

        getBoard().seedFromEvent(getPlayerIndex(), event);

        _events.add(Integer.valueOf(event));
        if (isSyncingBoards()) {
            _states.add((board == null) ? null : board.clone());
            if (board == null) {
                log.warning(
                    "Added progress event with no associated board state, " +
                    "server will not be able to ensure board state synchronization.");
            }
        }
    }

    /**
     * Sends the server a game progress update with the list of events, as well as board states if
     * {@link PuzzlePanel#isSyncingBoards} is true.
     */
    public void sendProgressUpdate ()
    {
        // make sure we have our puzzle object and events to send
        int size = _events.size();
        if (size == 0 || _puzobj == null) {
            return;
        }

        // create an array of the events we're sending to the server
        int[] events = CollectionUtil.toIntArray(_events);
        _events.clear();

//        log.info("Sending progress", "session", _puzobj.sessionId,
//            "events", StringUtil.toString(events));

        // create an array of the board states that correspond with those events (if state syncing
        // is enabled)
        int numStates = _states.size();
        if (numStates == size) { // ie, if we have a board to match every event
            Board[] states = new Board[numStates];
            _states.toArray(states);
            _states.clear();

            // send the update progress request
            _puzobj.puzzleGameService.updateProgressSync(_puzobj.sessionId, events, states);

        } else {
            // send the update progress request
            _puzobj.puzzleGameService.updateProgress(_puzobj.sessionId, events);
        }
    }

    /**
     * Called when a player is knocked out of the game to give the puzzle a chance to perform any
     * post-knockout actions that may be desired. Derived classes may wish to override this method
     * but should be sure to call <code>super.playerKnockedOut()</code>.
     */
    protected void playerKnockedOut (final int pidx)
    {
        // dispatch this to our delegates
        applyToDelegates(new DelegateOp(PuzzleControllerDelegate.class) {
            @Override
            public void apply (PlaceControllerDelegate delegate) {
                ((PuzzleControllerDelegate)delegate).playerKnockedOut(pidx);
            }
        });
    }

    /**
     * Catches clicks an unpauses, without passing the click through to the puzzle.
     */
    class Unpauser extends MouseHijacker
    {
        public Unpauser (PuzzlePanel panel) {
            super(panel.getBoardView());
            _panel = panel;
            panel.addMouseListener(_clicker);
            panel.getBoardView().addMouseListener(_clicker);
        }

        @Override
        public Component release () {
            _panel.removeMouseListener(_clicker);
            _panel.getBoardView().removeMouseListener(_clicker);
            return super.release();
        }

        protected MouseAdapter _clicker = new MouseAdapter() {
            @Override
            public void mousePressed (MouseEvent event) {
                setChatting(false); // this will call release
            }
        };

        protected PuzzlePanel _panel;
    }

    /**
     * A special frame participant that handles the sending of puzzle progress updates. We can't
     * just register an interval for this because sometimes the clock goes backwards in time in
     * windows and our intervals don't get called for a long period of time which causes the
     * server to think the client is disconnected or cheating and resign them from the puzzle. God
     * bless you, Microsoft.
     */
    protected class Updater implements FrameParticipant
    {
        public void tick (long tickStamp) {
            if (_astate == ACTION_CLEARED) {
                // remove ourselves as the action is now cleared; we can't do this in
                // actuallyClearAction() because that might get called during the PuzzlePanel's
                // frame tick and it's only safe to remove yourself during a tick(), not another
                // frame participant
                _pctx.getFrameManager().removeFrameParticipant(_updater);

            } else if (tickStamp - _lastProgressTick > getProgressInterval()) {
                _lastProgressTick = tickStamp;
                sendProgressUpdate();
            }
        }

        public boolean needsPaint () {
            return false;
        }

        public Component getComponent () {
            return null;
        }

        public long _lastProgressTick;
    }

    /**
     * Create the updater to be used in this puzzle.
     */
    protected Updater createUpdater ()
    {
        return new Updater();
    }

    /** The mouse jockey for unpausing our puzzles. */
    protected Unpauser _unpauser;

    /** Handles the sending of puzzle progress updates. */
    protected Updater _updater = createUpdater();

    /** Listens for players being knocked out. */
    protected ElementUpdateListener _kolist = new ElementUpdateListener() {
        public void elementUpdated (ElementUpdatedEvent event) {
            String name = event.getName();
            if (name.equals(PuzzleObject.PLAYER_STATUS)) {
                if (event.getIntValue() == GameObject.PLAYER_LEFT_GAME) {
                    playerKnockedOut(event.getIndex());
                }
            }
        }
    };

    /** Listens for various attribute changes. */
    protected AttributeChangeListener _mlist = new NamedAttributeListener(PuzzleObject.SEED) {
        @Override
        public void namedAttributeChanged (AttributeChangedEvent event) {
            generateNewBoard();
        }
    };

    /** A casted reference to the client context. */
    protected PuzzleContext _pctx;

    /** Our player index in the game. */
    protected int _pidx;

    /** The puzzle panel. */
    protected PuzzlePanel _panel;

    /** A reference to our puzzle game object. */
    protected PuzzleObject _puzobj;

    /** The puzzle board view. */
    protected PuzzleBoardView _pview;

    /** The puzzle board data. */
    protected Board _pboard;

    /** The list of relevant game events since the last progress update. */
    protected List<Integer> _events = Lists.newArrayList();

    /** Board snapshots that correspond to board state after each event has been applied. */
    protected List<Board> _states = Lists.newArrayList();

    /** A flag indicating that we're in chatting mode. */
    protected boolean _chatting = false;

    /** The current action state of the puzzle. */
    protected int _astate = ACTION_CLEARED;

    /** The action cleared penders. */
    protected ObserverList<ClearPender> _clearPenders = ObserverList.newSafeInOrder();

    /** A key listener that currently just toggles pause in the puzzle. */
    protected KeyListener _globalKeyListener = new KeyAdapter() {
        @Override
        public void keyReleased (KeyEvent e) {
            int keycode = e.getKeyCode();
            // toggle chatting (pause)
            if (keycode == KeyEvent.VK_ESCAPE || keycode == KeyEvent.VK_PAUSE) {
                setChatting(!isChatting());

            // pressing P also to pause (but not unpause), and only if it has not been reassigned
            } else if (keycode == KeyEvent.VK_P && !isChatting() &&
                    !_panel._xlate.hasCommand(KeyEvent.VK_P)) {
                setChatting(true);
            }
        }
    };

    /** The delay in milliseconds between progress update intervals. */
    protected static final long DEFAULT_PROGRESS_INTERVAL = 6000L;

    /** A {@link #_astate} constant. */
    protected static final int ACTION_CLEARED = 0;

    /** A {@link #_astate} constant. */
    protected static final int CLEAR_PENDING = 1;

    /** A {@link #_astate} constant. */
    protected static final int ACTION_GOING = 2;
}
