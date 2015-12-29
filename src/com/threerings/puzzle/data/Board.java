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

package com.threerings.puzzle.data;

import java.util.Random;

import com.threerings.io.Streamable;

/**
 * An abstract base class for generating and storing puzzle board data.
 */
public abstract class Board
    implements Cloneable, Streamable
{
    /**
     * Outputs a string representation of the board contents.
     */
    public abstract void dump ();

    /**
     * Outputs a string representation of the board contents, interlaced with the supplied
     * comparison board.
     */
    public abstract void dumpAndCompare (Board other);

    /**
     * Returns whether this board is equal to the given comparison board.
     */
    public abstract boolean equals (Board other);

    @Override
    public Board clone ()
    {
        try {
            Board board = (Board)super.clone();
            board._rando = _rando.clone();
            return board;
        } catch (CloneNotSupportedException cnse) {
            throw new AssertionError(cnse);
        }
    }

    /**
     * Sets the seed in the board's random number generator and calls {@link #populate}.
     */
    public void initializeSeed (long seed)
    {
        _rando = new BoardRandom(seed);
        populate();
    }

    /**
     * Returns the random number generator used by the board to generate random numbers for our
     * puzzles.
     */
    public Random getRandom ()
    {
        return _rando;
    }

    /**
     * Called after the seed is set in the board to give derived classes a chance to do things
     * like populating the board with random pieces.
     */
    protected void populate ()
    {
    }

    /**
     * Allows puzzles to add extra noise to their random number generators based on the specific
     * events sent from the client to make it more difficult for a hacked client to predict things
     * such as piece generation.
     *
     * @param pidx the player index that submitted the progress event.
     * @param gevent the progress event itself.
     */
    public void seedFromEvent (int pidx, int gevent)
    {
        if (isSeedingEvent(pidx, gevent)) {
            for (int ii = 0, jj = getSeedForEvent(pidx, gevent); ii < jj; ii++) {
                _rando.next(0);
            }
        }
    }

    /**
     * Returns whether this event is the sort of thing we should use to generate extra noise on
     * our random number generator.
     */
    protected boolean isSeedingEvent (int pidx, int gevent)
    {
        return false; // By default, nothing is
    }

    /**
     * Returns a number of bits to read off the random number generator for this event.
     * Subclasses can replace this with something that better understands the formats of its
     * particular events if desired.
     */
    protected int getSeedForEvent (int pidx, int gevent)
    {
        return (pidx ^ gevent) % 7;
    }

    /** Used to generate random numbers. */
    protected static class BoardRandom extends Random
        implements Cloneable
    {
        public BoardRandom (long seed) {
            super(0L);
            setSeed(seed);
        }

        @Override
        public synchronized void setSeed (long seed) {
            _seed = (seed ^ multiplier) & mask;
        }

        @Override
        synchronized protected int next (int bits) {
            long nextseed = (_seed * multiplier + addend) & mask;
            _seed = nextseed;
            return (int)(nextseed >>> (48 - bits));
        }

        @Override
        public void nextBytes (byte[] bytes) {
            unimplemented();
        }

        // not overridden: (They seemed innocent enough)
        // nextInt()
        // nextLong()
        // nextBoolean()
        // nextFloat()

        @Override
        public int nextInt (int n) {
            if (n <= 0) {
                throw new IllegalArgumentException("n must be positive");
            }

            if ((n & -n) == n) { // i.e., n is a power of 2
                return (int)((n * (long)next(31)) >> 31);
            }

            int bits, val;
            do {
                bits = next(31);
                val = bits % n;
            } while (bits - val + (n-1) < 0);
            return val;
        }

        @Override
        public double nextDouble () {
            long l = ((long)(next(26)) << 27) + next(27);
            return l / (double)(1L << 53);
        }

        @Override
        public synchronized double nextGaussian () {
            if (_haveNextNextGaussian) {
                _haveNextNextGaussian = false;
                return _nextNextGaussian;

            } else {
                double v1, v2, s;
                do {
                    v1 = 2 * nextDouble() - 1;
                    v2 = 2 * nextDouble() - 1;
                    s = v1 * v1 + v2 * v2;
                } while (s >= 1 || s == 0);
                double multiplier = Math.sqrt(-2 * Math.log(s)/s);
                _nextNextGaussian = v2 * multiplier;
                _haveNextNextGaussian = true;
                return v1 * multiplier;
            }
        }

        @Override
        public BoardRandom clone () {
            try {
                return (BoardRandom) super.clone();
            } catch (CloneNotSupportedException cnse) {
                throw new AssertionError(cnse);
            }
        }

        /**
         * I suppose I could copy all the methods from Random, then we wouldn't need this..
         */
        private final void unimplemented ()
        {
            throw new RuntimeException(
                "The Random method you attempted to call has not been implemented by BoardRandom.");
        }

        /** The internal state related to generating random numbers. */
        protected long _seed;
        protected double _nextNextGaussian;
        protected boolean _haveNextNextGaussian = false;

        private final static long multiplier = 0x5DEECE66DL;
        private final static long addend = 0xBL;
        private final static long mask = (1L << 48) - 1;
    }

    /** The object we use to generate our random numbers. */
    protected transient BoardRandom _rando;
}
