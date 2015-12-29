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

package com.threerings.whirled.data;

import java.io.IOException;

import com.samskivert.util.StringUtil;

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.Streamable;

import com.threerings.util.ActionScript;

import static com.threerings.whirled.Log.log;

/**
 * Used to encapsulate updates to scenes in such a manner that updates can be stored persistently
 * and sent to clients to update their own local copies of scenes.
 */
public class SceneUpdate
    implements Streamable, Cloneable
{
    /**
     * Initializes this scene update such that it will operate on a scene with the specified target
     * scene and version number.
     *
     * @param targetId the id of the scene on which we are to operate.
     * @param targetVersion the version of the scene on which we are to operate.
     */
    public void init (int targetId, int targetVersion)
    {
        _targetId = targetId;
        _targetVersion = targetVersion;
    }

    /**
     * Returns the scene id for which this update is appropriate.
     */
    public int getSceneId ()
    {
        return _targetId;
    }

    /**
     * Returns the scene version for which this update is appropriate.
     */
    public int getSceneVersion ()
    {
        return _targetVersion;
    }

    /**
     * Returns the amount by which this update increments the scene version.
     */
    public int getVersionIncrement ()
    {
        return 1;
    }

    /**
     * Called to ensure that the scene is in the appropriate state prior to applying the update.
     *
     * @exception IllegalStateException thrown if the update cannot be applied to the scene because
     * it is not in a valid state (appropriate previous updates were not applied, it's the wrong
     * kind of scene, etc.).
     */
    public void validate (SceneModel model)
        throws IllegalStateException
    {
        if (model.sceneId != _targetId) {
            String errmsg = "Wrong target scene, expected id " + _targetId +
                " got id " + model.sceneId;
            throw new IllegalStateException(errmsg);
        }
        if (model.version != _targetVersion) {
            String errmsg = "Target scene not proper version, expected " + _targetVersion +
                " got " + model.version;
            throw new IllegalStateException(errmsg);
        }
    }

    /**
     * Applies this update to the specified scene model. Derived classes will want to override this
     * method and apply updates of their own, being sure to call <code>super.apply</code>.
     */
    public void apply (SceneModel model)
    {
        // increment the version; disallowing integer overflow
        model.version = Math.max(_targetVersion + getVersionIncrement(), model.version);

        // sanity check for the amazing two billion updates
        if (model.version == _targetVersion) {
            log.warning("Egads! This scene has been updated two billion times [model=" + model +
                        ", update=" + this + "].");
        }
    }

    /**
     * Writes our custom streamable fields.
     */
    public void writeObject (ObjectOutputStream out)
        throws IOException
    {
        if (null == _dbSer.get()) {
            out.writeInt(_targetId);
            out.writeInt(_targetVersion);
        }
        out.defaultWriteObject();
    }

    /**
     * Reads our custom streamable fields.
     */
    public void readObject (ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        if (null == _dbSer.get()) {
            _targetId = in.readInt();
            _targetVersion = in.readInt();
        }
        in.defaultReadObject();
    }

    /**
     * Serializes the bare representation of this instance without the scene id and version fields.
     * Useful when storing updates in a database where the scene id and version fields are stored
     * in separate columns and the rest if the representation is contained in an opaque blob.
     */
    @ActionScript(omit=true)
    public void persistTo (ObjectOutputStream out)
        throws IOException
    {
        _dbSer.set(Boolean.TRUE);
        try {
            out.writeBareObject(this);
        } finally {
            _dbSer.set(null);
        }
    }

    /**
     * Unserializes this instance from the bare representation created by {@link #persistTo}.
     */
    @ActionScript(omit=true)
    public void unpersistFrom (ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        _dbSer.set(Boolean.TRUE);
        try {
            in.readBareObject(this);
        } finally {
            _dbSer.set(null);
        }
    }

    @Override
    public String toString ()
    {
        StringBuilder buf = new StringBuilder("[");
        toString(buf);
        return buf.append("]").toString();
    }

    /**
     * An extensible mechanism for generating a string representation of this instance.
     */
    @ActionScript(name="toStringBuilder")
    protected void toString (StringBuilder buf)
    {
        buf.append("sceneId=").append(_targetId);
        buf.append(", version=").append(_targetVersion);
        buf.append(", ");
        StringUtil.fieldsToString(buf, this);
    }

    /** The version number of the scene on which we operate. */
    protected transient int _targetId;

    /** The version number of the scene on which we operate. */
    protected transient int _targetVersion;

    /** Used when serializing this update for storage in the database. */
    @ActionScript(omit=true)
    protected static ThreadLocal<Boolean> _dbSer = new ThreadLocal<Boolean>();
}
