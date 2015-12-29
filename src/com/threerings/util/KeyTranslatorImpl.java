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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.samskivert.util.HashIntMap;

/**
 * A basic implementation of the {@link KeyTranslator} interface that provides facilities for
 * mapping key codes to action command strings for use by the {@link KeyboardManager}.
 */
public class KeyTranslatorImpl
    implements KeyTranslator
{
    /**
     * Adds a mapping from a key press to an action command string that will auto-repeat at a
     * default repeat rate.
     */
    public void addPressCommand (int keyCode, String command)
    {
        addPressCommand(keyCode, command, DEFAULT_REPEAT_RATE);
    }

    /**
     * Adds a mapping from a key press to an action command string that will auto-repeat at the
     * specified repeat rate. Overwrites any existing mapping and repeat rate that may have
     * already been registered.
     *
     * @param rate the number of times each second that the key press should be repeated while the
     * key is down, or <code>0</code> to disable auto-repeat for the key.
     */
    public void addPressCommand (int keyCode, String command, int rate)
    {
        addPressCommand(keyCode, command, rate, DEFAULT_REPEAT_DELAY);
    }

    /**
     * Adds a mapping from a key press to an action command string that will auto-repeat at the
     * specified repeat rate after the specified auto-repeat delay has expired. Overwrites any
     * existing mapping for the specified key code that may have already been registered.
     *
     * @param rate the number of times each second that the key press should be repeated while the
     * key is down; passing <code>0</code> will result in no repeating.
     * @param repeatDelay the delay in milliseconds before auto-repeating key press events will be
     * generated for the key.
     */
    public void addPressCommand (int keyCode, String command, int rate, long repeatDelay)
    {
        KeyRecord krec = getKeyRecord(keyCode);
        krec.pressCommand = command;
        krec.repeatRate = rate;
        krec.repeatDelay = repeatDelay;
    }

    /**
     * Adds a mapping from a key release to an action command string. Overwrites any existing
     * mapping that may already have been registered.
     */
    public void addReleaseCommand (int keyCode, String command)
    {
        KeyRecord krec = getKeyRecord(keyCode);
        krec.releaseCommand = command;
    }

    /**
     * Returns the key record for the specified key, creating it and inserting it in the key table
     * if necessary.
     */
    protected KeyRecord getKeyRecord (int keyCode)
    {
        KeyRecord krec = _keys.get(keyCode);
        if (krec == null) {
            krec = new KeyRecord();
            _keys.put(keyCode, krec);
        }
        return krec;
    }

    // documentation inherited from interface KeyTranslator
    public boolean hasCommand (int keyCode)
    {
        return (_keys.get(keyCode) != null);
    }

    // documentation inherited from interface KeyTranslator
    public boolean hasCommand (char ch)
    {
        return (_charCommands.get(ch) != null);
    }

    // documentation inherited from interface KeyTranslator
    public String getPressCommand (int keyCode)
    {
        KeyRecord krec = _keys.get(keyCode);
        return (krec == null) ? null : krec.pressCommand;
    }

    // documentation inherited from interface KeyTranslator
    public String getPressCommand (char ch)
    {
        KeyRecord krec = _charCommands.get(ch);
        return (krec == null) ? null : krec.pressCommand;
    }

    // documentation inherited from interface KeyTranslator
    public String getReleaseCommand (int keyCode)
    {
        KeyRecord krec = _keys.get(keyCode);
        return (krec == null) ? null : krec.releaseCommand;
    }

    // documentation inherited from interface KeyTranslator
    public String getReleaseCommand (char ch)
    {
        KeyRecord krec = _charCommands.get(ch);
        return (krec == null) ? null : krec.releaseCommand;
    }

    // documentation inherited from interface KeyTranslator
    public int getRepeatRate (int keyCode)
    {
        KeyRecord krec = _keys.get(keyCode);
        return (krec == null) ? DEFAULT_REPEAT_RATE : krec.repeatRate;
    }

    // documentation inherited from interface KeyTranslator
    public int getRepeatRate (char ch)
    {
        KeyRecord krec = _charCommands.get(ch);
        return (krec == null) ? DEFAULT_REPEAT_RATE : krec.repeatRate;
    }

    // documentation inherited from interface KeyTranslator
    public long getRepeatDelay (int keyCode)
    {
        KeyRecord krec = _keys.get(keyCode);
        return (krec == null) ? DEFAULT_REPEAT_DELAY : krec.repeatDelay;
    }

    // documentation inherited from interface KeyTranslator
    public long getRepeatDelay (char ch)
    {
        KeyRecord krec = _charCommands.get(ch);
        return (krec == null) ? DEFAULT_REPEAT_DELAY : krec.repeatDelay;
    }

    // documentation inherited from interface KeyTranslator
    public Iterator<String> enumeratePressCommands ()
    {
        ArrayList<String> commands = Lists.newArrayList();
        for (KeyRecord rec : _keys.values()) {
            commands.add(rec.pressCommand);
        }

        return commands.iterator();
    }

    // documentation inherited from interface KeyTranslator
    public Iterator<String> enumerateReleaseCommands ()
    {
        ArrayList<String> commands = Lists.newArrayList();
        for (KeyRecord rec : _keys.values()) {
            commands.add(rec.releaseCommand);
        }

        return commands.iterator();
    }

    protected static class KeyRecord
    {
        /** The command to be posted when the key is pressed. */
        public String pressCommand;

        /** The command to be posted when the key is released. */
        public String releaseCommand;

        /** The rate in presses per second at which the key is to be auto-repeated. */
        public int repeatRate;

        /**
         * The delay in milliseconds that must expire with the key still pressed before
         * auto-repeated key presses will begin.
         */
        public long repeatDelay;
    }

    /** The keys for which commands are registered. */
    protected HashIntMap<KeyRecord> _keys = new HashIntMap<KeyRecord>();

    /**
     * Any commands we wish to perform upon key typed events for characters.
     */
    protected HashMap<Character,KeyRecord> _charCommands = Maps.newHashMap();

    /** The default key press repeat rate. */
    protected static final int DEFAULT_REPEAT_RATE = 5;

    /** The default delay in milliseconds before auto-repeated key presses will begin. */
    protected static final long DEFAULT_REPEAT_DELAY = 500L;
}
