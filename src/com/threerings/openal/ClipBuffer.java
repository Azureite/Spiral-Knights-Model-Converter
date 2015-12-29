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

package com.threerings.openal;

import java.io.IOException;

import org.lwjgl.openal.AL10;

import com.samskivert.util.ObserverList;

import static com.threerings.openal.Log.log;

/**
 * Represents a sound that has been loaded into the OpenAL system.
 */
public class ClipBuffer
{
    /** Used to notify parties interested in when a clip is loaded. */
    public static interface Observer
    {
        /** Called when a clip has completed loading and is ready to be played. */
        public void clipLoaded (ClipBuffer buffer);

        /** Called when a clip has failed to prepare itself for one reason or other. */
        public void clipFailed (ClipBuffer buffer);
    }

    /**
     * Create a key that uniquely identifies this combination of clip
     * provider and path.
     */
    public static String makeKey (ClipProvider provider, String path)
    {
        // we'll just use a string, amazing!
        return provider + ":" + path;
    }

    /**
     * Creates a new clip buffer with the specified path that will obtain
     * its clip data from the specified source. The clip will
     * automatically queue itself up to be loaded into memory.
     */
    public ClipBuffer (SoundManager manager, ClipProvider provider, String path)
    {
        _manager = manager;
        _provider = provider;
        _path = path;
    }

    /**
     * Returns the unique key for this clip buffer.
     */
    public String getKey ()
    {
        return makeKey(_provider, _path);
    }

    /**
     * Returns the provider used to load this clip.
     */
    public ClipProvider getClipProvider ()
    {
        return _provider;
    }

    /**
     * Returns the path that identifies this sound clip.
     */
    public String getPath ()
    {
        return _path;
    }

    /**
     * Returns true if this buffer is loaded and ready to go.
     */
    public boolean isPlayable ()
    {
        return (_state == LOADED);
    }

    /**
     * Returns a reference to this clip's buffer or <code>null</code> if it is not loaded.
     */
    public Buffer getBuffer ()
    {
        return _buffer;
    }

    /**
     * Returns the size (in bytes) of this clip as reported by OpenAL.
     * This value will not be valid until the clip is bound.
     */
    public int getSize ()
    {
        return _size;
    }

    /**
     * Instructs this buffer to resolve its underlying clip and be ready
     * to be played ASAP.
     */
    public void resolve (Observer observer)
    {
        // if we were waiting to unload, cancel that
        if (_state == UNLOADING) {
            _state = LOADED;
            _manager.restoreClip(this);
        }

        // if we're already loaded, this is easy
        if (_state == LOADED) {
            if (observer != null) {
                observer.clipLoaded(this);
            }
            return;
        }

        // queue up the observer
        if (observer != null) {
            _observers.add(observer);
        }

        // if we're already loading, we can stop here
        if (_state == LOADING) {
            return;
        }

        // create our OpenAL buffer and then queue ourselves up to have
        // our clip data loaded
        AL10.alGetError(); // throw away any unchecked error prior to an op we want to check
        _buffer = new Buffer(_manager);
        int errno = AL10.alGetError();
        if (errno != AL10.AL_NO_ERROR) {
            log.warning("Failed to create buffer [key=" + getKey() +
                        ", errno=" + errno + "].");
            _buffer = null;
            // queue up a failure notification so that we properly return
            // from this method and our sound has a chance to register
            // itself as an observer before we jump up and declare failure
            _manager.queueClipFailure(this);

        } else {
            _state = LOADING;
            _manager.queueClipLoad(this);
        }
    }

    /**
     * Frees up the internal audio buffers associated with this clip.
     */
    public void dispose ()
    {
        if (_buffer != null) {
            // if there are sources bound to this buffer, we must wait
            // for them to be unbound
            if (_bound > 0) {
                _state = UNLOADING;
                return;
            }

            // free up our buffer
            _buffer.delete();
            _buffer = null;
            _state = UNLOADED;
        }
    }

    /**
     * This method is called by the background sound loading thread and
     * actually loads the sound data from wherever it cometh.
     */
    protected Clip load ()
        throws IOException
    {
        return _provider.loadClip(_path);
    }

    /**
     * This method is called back on the main thread and instructs this
     * buffer to bind the clip data to this buffer's OpenAL buffer.
     *
     * @return true if the binding succeeded, false if we were unable to
     * load the sound data into OpenAL.
     */
    protected boolean bind (Clip clip)
    {
        AL10.alGetError(); // throw away any unchecked error prior to an op we want to check
        _buffer.setData(clip.format, clip.data, clip.frequency);
        int errno = AL10.alGetError();
        if (errno != AL10.AL_NO_ERROR) {
            log.warning("Failed to bind clip", "key", getKey(), "errno", errno);
            failed();
            return false;
        }

        _state = LOADED;
        _size = _buffer.getSize();
        _observers.apply(new ObserverList.ObserverOp<Observer>() {
            public boolean apply (Observer observer) {
                observer.clipLoaded(ClipBuffer.this);
                return true;
            }
        });
        _observers.clear();
        return true;
    }

    /**
     * Called when we fail in some part of the process in resolving our
     * clip data. Notifies our observers and resets the clip to the
     * UNLOADED state.
     */
    protected void failed ()
    {
        if (_buffer != null) {
            _buffer.delete();
            _buffer = null;
        }
        _state = UNLOADED;

        _observers.apply(new ObserverList.ObserverOp<Observer>() {
            public boolean apply (Observer observer) {
                observer.clipFailed(ClipBuffer.this);
                return true;
            }
        });
        _observers.clear();
    }

    /**
     * Notifies the buffer that a source has been bound to it.
     */
    protected void sourceBound ()
    {
        _bound++;
    }

    /**
     * Notifies the buffer that a source has been unbound from it.
     */
    protected void sourceUnbound ()
    {
        // dispose of the buffer when the last source is unbound
        if (--_bound == 0 && _state == UNLOADING) {
            dispose();
        }
    }

    protected SoundManager _manager;
    protected ClipProvider _provider;
    protected String _path;
    protected int _state;
    protected Buffer _buffer;
    protected int _size;
    protected ObserverList<Observer> _observers = ObserverList.newFastUnsafe();
    protected int _bound;

    protected static final int UNLOADED = 0;
    protected static final int LOADING = 1;
    protected static final int LOADED = 2;
    protected static final int UNLOADING = 3;
}
