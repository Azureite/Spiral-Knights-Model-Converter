//
// Nenya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
// https://github.com/threerings/nenya
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

package com.threerings.util;

import java.util.HashMap;
import java.util.Iterator;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.google.common.collect.Maps;

import com.samskivert.util.HashIntMap;
import com.samskivert.util.Interval;
import com.samskivert.util.ObserverList;
import com.samskivert.util.RunAnywhere;

import com.samskivert.swing.Controller;
import com.samskivert.swing.RuntimeAdjust;

import com.threerings.util.keybd.Keyboard;

import com.threerings.media.MediaPrefs;

import static com.threerings.NenyaLog.log;

/**
 * The keyboard manager observes keyboard actions on a particular component and posts commands
 * associated with the key presses to the {@link Controller} hierarchy.  It allows specifying the
 * key repeat rate, and will begin repeating a key immediately after it is held down rather than
 * depending on the system-specific key repeat delay/rate.
 */
public class KeyboardManager
    implements KeyEventDispatcher, AncestorListener, WindowFocusListener
{
    /**
     * An interface to be implemented by those that care to be notified whenever an event (either a
     * key press or a key release) occurs for any key while the keyboard manager is active.  We use
     * this custom interface rather than the more standard {@link java.awt.event.KeyListener}
     * interface so that we needn't create key pressed and released event objects each time a
     * (potentially artificially-generated) event occurs.
     */
    public interface KeyObserver
    {
        /**
         * Called whenever a key event occurs for a particular key.
         */
        public void handleKeyEvent (int id, int keyCode, long timestamp);
    }

    /**
     * Constructs a keyboard manager that is initially disabled.  The keyboard manager should not
     * be enabled until it has been supplied with a target component and translator via
     * {@link #setTarget}.
     */
    public KeyboardManager ()
    {
        // capture low-level keyboard events via the keyboard focus manager
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    /**
     * Resets the keyboard manager, clearing any target and key translator in use and disabling the
     * keyboard manager if it is currently active.
     */
    public void reset ()
    {
        setEnabled(false);
        _target = null;
        _xlate = null;
        _focus = false;
    }

    /**
     * Initializes the keyboard manager with the supplied target component and key translator and
     * disables the keyboard manager if it is currently active.
     *
     * @param target the component whose keyboard events are to be observed.
     * @param xlate the key translator used to map keyboard events to controller action commands.
     */
    public void setTarget (JComponent target, KeyTranslator xlate)
    {
        setEnabled(false);

        // save off references
        _target = target;
        _xlate = xlate;
    }

    /**
     * Registers a key observer that will be notified of all key events while the keyboard manager
     * is active.
     */
    public void registerKeyObserver (KeyObserver obs)
    {
        _observers.add(obs);
    }

    /**
     * Removes the supplied key observer from the list of observers to be notified of all key
     * events while the keyboard manager is active.
     */
    public void removeKeyObserver (KeyObserver obs)
    {
        _observers.remove(obs);
    }

    /**
     * Sets whether the keyboard manager processes keyboard input.
     */
    public void setEnabled (boolean enabled)
    {
        // report incorrect usage
        if (enabled && _target == null) {
            log.warning("Attempt to enable uninitialized keyboard manager!", new Exception());
            return;
        }

        // ignore NOOPs
        if (enabled == _enabled) {
            return;
        }

        if (!enabled) {
            if (Keyboard.isAvailable()) {
                // restore the original key auto-repeat settings
                Keyboard.setKeyRepeat(_nativeRepeat);
            }

            // clear out all of our key states
            releaseAllKeys();
            _keys.clear();

            // cease listening to all of our business
            if (_window != null) {
                _window.removeWindowFocusListener(this);
                _window = null;
            }
            _target.removeAncestorListener(this);

            // note that we no longer have the focus
            _focus = false;

        } else {
            // listen to ancestor events so that we can cease our business
            // if we lose the focus
            _target.addAncestorListener(this);

            // if we're already showing, listen to window focus events,
            // else we have to wait until the target is added since it
            // doesn't currently have a window
            if (_target.isShowing() && _window == null) {
                _window = SwingUtilities.getWindowAncestor(_target);
                if (_window != null) {
                    _window.addWindowFocusListener(this);
                }
            }

            // assume the keyboard focus since we were just enabled
            _focus = true;

            if (Keyboard.isAvailable()) {
                // note whether key auto-repeating was enabled
                _nativeRepeat = Keyboard.isKeyRepeatEnabled();

                // Disable native key auto-repeating so that we can definitively ascertain key
                // pressed/released events.
                // Or not, if we've discovered we don't want to.
                Keyboard.setKeyRepeat(!_shouldDisableNativeRepeat);
            }
        }

        // save off our new enabled state
        _enabled = enabled;
    }

    /**
     * Sets the expected delay in milliseconds between each key press/release event the keyboard
     * manager should expect to receive while a key is repeating.
     */
    public void setRepeatDelay (long delay)
    {
        _repeatDelay = delay;
    }

    /**
     * Releases all keys and ceases any hot repeating action that may be going on.
     */
    public void releaseAllKeys ()
    {
        long now = System.currentTimeMillis();
        Iterator<KeyInfo> iter = _keys.elements();
        while (iter.hasNext()) {
            iter.next().release(now);
        }
    }

    /**
     * Called when the keyboard manager gains focus and should begin handling keys again if it was
     * previously enabled.
     */
    protected void gainedFocus ()
    {
        if (Keyboard.isAvailable()) {
            // disable key auto-repeating
            Keyboard.setKeyRepeat(false);
        }

        // note that we've regained the focus
        _focus = true;
    }

    /**
     * Called when the keyboard manager loses focus and should cease handling keys.
     */
    protected void lostFocus ()
    {
        if (Keyboard.isAvailable()) {
            // restore key auto-repeating
            Keyboard.setKeyRepeat(_nativeRepeat);
        }

        // clear out all of our keyboard state
        releaseAllKeys();
        // note that we no longer have the focus
        _focus = false;
    }

    // documentation inherited from interface KeyEventDispatcher
    public boolean dispatchKeyEvent (KeyEvent e)
    {
        // bail if we're not enabled, we haven't the focus, or we're not
        // showing on-screen
        if (!_enabled || !_focus || !_target.isShowing()) {
//             log.info("dispatchKeyEvent [enabled=" + _enabled +
//                      ", focus=" + _focus +
//                      ", showing=" + ((_target == null) ? "N/A" :
//                                      "" + _target.isShowing()) + "].");
            return false;
        }

        // handle key press and release events
        switch (e.getID()) {
        case KeyEvent.KEY_PRESSED:
            return keyPressed(e);

        case KeyEvent.KEY_RELEASED:
            return keyReleased(e);

        case KeyEvent.KEY_TYPED:
            return keyTyped(e);

        default:
            return false;
        }
    }

    /**
     * Called when Swing notifies us that a key has been pressed while the
     * keyboard manager is active.
     *
     * @return true to swallow the key event
     */
    protected boolean keyPressed (KeyEvent e)
    {
        logKey("keyPressed", e);

        // get the action command associated with this key
        int keyCode = e.getKeyCode();
        boolean hasCommand = _xlate.hasCommand(keyCode);
        if (hasCommand) {
            // get the info object for this key, creating one if necessary
            KeyInfo info = _keys.get(keyCode);
            if (info == null) {
                info = new KeyInfo(keyCode);
                _keys.put(keyCode, info);
            }

            // remember the last time this key was pressed
            info.setPressTime(RunAnywhere.getWhen(e));
        }

        // notify any key observers of the key press
        notifyObservers(KeyEvent.KEY_PRESSED, e.getKeyCode(), RunAnywhere.getWhen(e));

        return hasCommand;
    }

    /**
     * Called when Swing notifies us that a key has been typed while the
     * keyboard manager is active.
     *
     * @return true to swallow the key event
     */
    protected boolean keyTyped (KeyEvent e)
    {
        logKey("keyTyped", e);

        // get the action command associated with this key
        char keyChar = e.getKeyChar();
        boolean hasCommand = _xlate.hasCommand(keyChar);
        if (hasCommand) {
            // Okay, we're clearly doing actions based on key typing, so we're going to need native
            // keyboard repeating turned on. Oh well.
            if (_shouldDisableNativeRepeat) {
                _shouldDisableNativeRepeat = false;

                if (Keyboard.isAvailable()) {
                    Keyboard.setKeyRepeat(!_shouldDisableNativeRepeat);
                }
            }

            KeyInfo info = _chars.get(keyChar);
            if (info == null) {
                info = new KeyInfo(keyChar);
                _chars.put(keyChar, info);
            }

            // remember the last time this key was pressed
            info.setPressTime(RunAnywhere.getWhen(e));
        }

        // notify any key observers of the key press
        notifyObservers(KeyEvent.KEY_TYPED, e.getKeyChar(), RunAnywhere.getWhen(e));

        return hasCommand;
    }

    /**
     * Called when Swing notifies us that a key has been released while
     * the keyboard manager is active.
     *
     * @return true to swallow the key event
     */
    protected boolean keyReleased (KeyEvent e)
    {
        logKey("keyReleased", e);

        // get the info object for this key
        KeyInfo info = _keys.get(e.getKeyCode());
        if (info != null) {
            // remember the last time we received a key release
            info.setReleaseTime(RunAnywhere.getWhen(e));
        }

        // notify any key observers of the key release
        notifyObservers(KeyEvent.KEY_RELEASED, e.getKeyCode(), RunAnywhere.getWhen(e));

        return (info != null);
    }

    /**
     * Notifies all registered key observers of the supplied key event. This method provides a
     * thread-safe manner in which to notify the observers, which is necessary since the
     * {@link KeyInfo} objects do various antics from the interval manager thread whilst we may do
     * other notification from the AWT thread when normal key events are handled.
     */
    protected synchronized void notifyObservers (int id, int keyCode, long timestamp)
    {
        _keyOp.init(id, keyCode, timestamp);
        _observers.apply(_keyOp);
    }

    /**
     * Logs the given message and key.
     */
    protected void logKey (String msg, KeyEvent e)
    {
        if (DEBUG_EVENTS || _debugTyping.getValue()) {
            int keyCode = e.getKeyCode();
            log.info(msg, "key", KeyEvent.getKeyText(keyCode));
        }
    }

    // documentation inherited from interface AncestorListener
    public void ancestorAdded (AncestorEvent e)
    {
        gainedFocus();

        if (_window == null) {
            _window = SwingUtilities.getWindowAncestor(_target);
            _window.addWindowFocusListener(this);
        }
    }

    // documentation inherited from interface AncestorListener
    public void ancestorMoved (AncestorEvent e)
    {
        // nothing for now
    }

    // documentation inherited from interface AncestorListener
    public void ancestorRemoved (AncestorEvent e)
    {
        lostFocus();

        if (_window != null) {
            _window.removeWindowFocusListener(this);
            _window = null;
        }
    }

    // documentation inherited from interface WindowFocusListener
    public void windowGainedFocus (WindowEvent e)
    {
        gainedFocus();
    }

    // documentation inherited from interface WindowFocusListener
    public void windowLostFocus (WindowEvent e)
    {
        lostFocus();
    }

    protected class KeyInfo extends Interval
    {
        /**
         * Constructs a key info object for the given key code.
         */
        public KeyInfo (int keyCode)
        {
            super(RUN_DIRECT);
            _keyCode = keyCode;
            _keyText = KeyEvent.getKeyText(_keyCode);
            _pressCommand = _xlate.getPressCommand(_keyCode);
            _releaseCommand = _xlate.getReleaseCommand(_keyCode);
            int rate = _xlate.getRepeatRate(_keyCode);
            _pressDelay = (rate == 0) ? 0 : (1000L / rate);
            _repeatDelay = _xlate.getRepeatDelay(_keyCode);
        }

        /**
         * Constructs a key info object for the given character.
         */
        public KeyInfo (char keyChar)
        {
            super(RUN_DIRECT);
            _keyChar = keyChar;
            _keyText = "" +_keyChar;
            _pressCommand = _xlate.getPressCommand(_keyChar);
            _releaseCommand = _xlate.getReleaseCommand(_keyChar);
            int rate = _xlate.getRepeatRate(_keyChar);
            _pressDelay = (rate == 0) ? 0 : (1000L / rate);
            _repeatDelay = _xlate.getRepeatDelay(_keyChar);
        }

        /**
         * Returns true if we're based off a character & key typed events rather than a keycode
         * and key pressed/released events.
         */
        public boolean isCharacterBased ()
        {
            return _keyCode == KeyEvent.VK_UNDEFINED;
        }

        /**
         * Sets the last time the key was pressed.
         */
        public synchronized void setPressTime (long time)
        {
            if (_debugTyping.getValue()) {
                log.info("setPressTime",
                    "time", time,
                    "this", this,
                    "lastPress", _lastPress,
                    "lastRelease", _lastRelease,
                    "pressCommand", _pressCommand,
                    "releaseCommand", _releaseCommand,
                    "pressDelay", _pressDelay,
                    "repeatDelay", _repeatDelay,
                    "scheduled", _scheduled);
            }

            if (_lastPress == 0 && _pressCommand != null) {
                // post the initial key press command
                postPress(time);
            }

            if (!_scheduled && (_pressDelay > 0 || isCharacterBased())) {
                // register an interval to post the key press command
                // until the key is decidedly released
                if (_repeatDelay > 0) {
                    schedule(_repeatDelay, _pressDelay);

                } else {
                    schedule(_pressDelay, true);
                }
                _scheduled = true;

                if (DEBUG_EVENTS) {
                    log.info("Pressing key", "key", _keyText);
                }
            }

            _lastPress = time;
            _lastRelease = time;
        }

        /**
         * Sets the last time the key was released.
         */
        public synchronized void setReleaseTime (long time)
        {
            release(time);
            _lastRelease = time;

            if (_debugTyping.getValue()) {
                log.info("setReleaseTime",
                    "time", time,
                    "this", this,
                    "lastPress", _lastPress,
                    "lastRelease", _lastRelease,
                    "pressCommand", _pressCommand,
                    "releaseCommand", _releaseCommand,
                    "pressDelay", _pressDelay,
                    "repeatDelay", _repeatDelay,
                    "scheduled", _scheduled);
            }

            // handle key release events received so quickly after the key
            // press event that the press/release times are exactly equal
            // and, in intervalExpired(), we would therefore be unable to
            // distinguish between the key being initially pressed and the
            // actual true key release that's taken place.

            // the only case I can think of that might result in this
            // happening is if the event manager class queues up a key
            // press and release event succession while other code is
            // executing, and when it comes time for it to dispatch the
            // events in its queue it manages to dispatch both of them to
            // us really-lickety-split.  one would still think at least a
            // few milliseconds should pass between the press and release,
            // but in any case, we arguably ought to be watching for and
            // handling this case for posterity even though it would seem
            // unlikely or impossible, and so, now we do, which is a good
            // thing since it appears this does in fact happen, and not so
            // infrequently.
            if (_lastPress == _lastRelease) {
                if (DEBUG_EVENTS) {
                    log.warning("Insta-releasing key due to equal key press/release times",
                        "key", _keyText);
                }
                release(time);
            }
        }

        /**
         * Releases the key if pressed and cancels any active key repeat interval.
         */
        public synchronized void release (long timestamp)
        {
            if (_debugTyping.getValue()) {
                log.info("release",
                    "time", timestamp,
                    "this", this,
                    "lastPress", _lastPress,
                    "lastRelease", _lastRelease,
                    "pressCommand", _pressCommand,
                    "releaseCommand", _releaseCommand,
                    "pressDelay", _pressDelay,
                    "repeatDelay", _repeatDelay,
                    "scheduled", _scheduled);
            }

            // bail if we're not currently pressed
            if (_lastPress == 0) {
                return;
            }

            if (DEBUG_EVENTS) {
                log.info("Releasing key", "key", _keyText);
            }

            // remove the repeat interval
            if (_scheduled) {
                cancel();
                _scheduled = false;
            }

            if (_releaseCommand != null) {
                // post the key release command
                postRelease(timestamp);
            }

            // clear out the last press and release timestamps
            _lastPress = _lastRelease = 0;
        }

        @Override
        public synchronized void expired ()
        {
            long now = System.currentTimeMillis();
            long deltaPress = now - _lastPress;
            long deltaRelease = now - _lastRelease;

            if (_debugTyping.getValue()) {
                log.info("expired",
                    "time", now,
                    "this", this,
                    "lastPress", _lastPress,
                    "lastRelease", _lastRelease,
                    "pressCommand", _pressCommand,
                    "releaseCommand", _releaseCommand,
                    "pressDelay", _pressDelay,
                    "repeatDelay", _repeatDelay,
                    "scheduled", _scheduled,
                    "deltaPress", deltaPress,
                    "deltaRelease", deltaRelease);
            }

            if (KeyboardManager.DEBUG_INTERVAL) {
                log.info("Interval",
                    "key", _keyText, "deltaPress", deltaPress, "deltaRelease", deltaRelease);
            }

            // cease repeating if we're certain the key is now up, or repeat the key
            // command if we're certain the key is still down
            if (_lastRelease != _lastPress) {
                release(now);

            } else if (_lastPress != 0) {
                if (!isCharacterBased()) {
                    if (_pressCommand != null) {
                        // post the key press command again
                        postPress(now);
                    }
                } else {
                    // We're dealing with a key typed event, so we don't really know what's going
                    // on, so we'll pretend we released it now, and hope the native keyboard repeat
                    // takes care of us.
                    release(now);
                }
            }
        }

        /**
         * Posts the press command for this key and notifies all key observers of the key press.
         */
        protected void postPress (long timestamp)
        {
            if (_debugTyping.getValue()) {
                log.info("postPress",
                    "time", timestamp,
                    "this", this,
                    "lastPress", _lastPress,
                    "lastRelease", _lastRelease,
                    "pressCommand", _pressCommand,
                    "releaseCommand", _releaseCommand,
                    "pressDelay", _pressDelay,
                    "repeatDelay", _repeatDelay,
                    "scheduled", _scheduled);
            }

            if (!isCharacterBased()) {
                notifyObservers(KeyEvent.KEY_PRESSED, _keyCode, timestamp);
            } else {
                notifyObservers(KeyEvent.KEY_TYPED, _keyChar, timestamp);
            }
            Controller.postAction(_target, _pressCommand);
        }

        /**
         * Posts the release command for this key and notifies all key observers of the key release.
         */
        protected void postRelease (long timestamp)
        {
            if (_debugTyping.getValue()) {
                log.info("postRelease",
                    "time", timestamp,
                    "this", this,
                    "lastPress", _lastPress,
                    "lastRelease", _lastRelease,
                    "pressCommand", _pressCommand,
                    "releaseCommand", _releaseCommand,
                    "pressDelay", _pressDelay,
                    "repeatDelay", _repeatDelay,
                    "scheduled", _scheduled);
            }

            notifyObservers(KeyEvent.KEY_RELEASED, _keyCode, timestamp);
            Controller.postAction(_target, _releaseCommand);
        }

        @Override
        public String toString ()
        {
            return "[key=" + _keyText + ", charBased=" + isCharacterBased() + "]";
        }

        /** True if we are a scheduled interval. */
        protected boolean _scheduled = false;

        /** The last time a key released event was received for this key. */
        protected long _lastRelease;

        /** The last time a key pressed event was received for this key. */
        protected long _lastPress;

        /** The press action command associated with this key. */
        protected String _pressCommand;

        /** The release action command associated with this key. */
        protected String _releaseCommand;

        /** A text representation of this key. */
        protected String _keyText;

        /** The key code associated with this key info object, if any. */
        protected int _keyCode = KeyEvent.VK_UNDEFINED;

        /** The character associated with this key info object, if any. */
        protected char _keyChar;

        /** The milliseconds to sleep between sending repeat key commands. */
        protected long _pressDelay;

        /** The delay in milliseconds before auto-repeating the key press. */
        protected long _repeatDelay;
    }

    /** An observer operation to notify observers of a key event. */
    protected static class KeyObserverOp implements ObserverList.ObserverOp<KeyObserver>
    {
        /** Initialized the operation with its parameters. */
        public void init (int id, int keyCode, long timestamp)
        {
            _id = id;
            _keyCode = keyCode;
            _timestamp = timestamp;
        }

        // documentation inherited from interface ObserverList.ObserverOp
        public boolean apply (KeyObserver observer)
        {
            observer.handleKeyEvent(_id, _keyCode, _timestamp);
            return true;
        }

        /** The key event id. */
        protected int _id;

        /** The key code. */
        protected int _keyCode;

        /** The key event timestamp. */
        protected long _timestamp;
    }

    /** Whether to output debugging info for individual key events. */
    protected static final boolean DEBUG_EVENTS = false;

    /** Whether to output debugging info for interval callbacks. */
    protected static final boolean DEBUG_INTERVAL = false;

    /** The default repeat delay. */
    protected static final long DEFAULT_REPEAT_DELAY = 50L;

    /** The expected approximate milliseconds between each key
     * release/press event while the key is being auto-repeated. */
    protected long _repeatDelay = DEFAULT_REPEAT_DELAY;

    /** A hashtable mapping key codes to {@link KeyInfo} objects. */
    protected HashIntMap<KeyInfo> _keys = new HashIntMap<KeyInfo>();

    /** A hashtable mapping characters to {@link KeyInfo} objects. */
    protected HashMap<Character,KeyInfo> _chars = Maps.newHashMap();

    /** Whether the keyboard manager currently has the keyboard focus. */
    protected boolean _focus;

    /** Whether the keyboard manager is accepting keyboard input. */
    protected boolean _enabled;

    /** The window containing our target component whose focus events we
     * care to observe, or null if we're not observing a window. */
    protected Window _window;

    /** The component that receives keyboard events and that we associate
     * with posted controller commands. */
    protected JComponent _target;

    /** The translator that maps keyboard events to controller commands. */
    protected KeyTranslator _xlate;

    /** The list of key observers. */
    protected ObserverList<KeyObserver> _observers = ObserverList.newFastUnsafe();

    /** The operation used to notify observers of actual key events. */
    protected KeyObserverOp _keyOp = new KeyObserverOp();

    /** Whether native key auto-repeating was enabled when the keyboard manager was last enabled. */
    protected boolean _nativeRepeat;

    /** Whether we want to disable native key auto-repeating. If we're dealing with wacky keys that
     * send only key typed events, we might need to fall back to letting that happen so things work
     * right.
     */
    protected boolean _shouldDisableNativeRepeat = true;

    /** A debug hook that toggles excessive logging to help debug keyTyped behavior. */
    protected static RuntimeAdjust.BooleanAdjust _debugTyping = new RuntimeAdjust.BooleanAdjust(
        "Toggles key typed debugging", "nenya.util.keyboard",
        MediaPrefs.config, false);
}
