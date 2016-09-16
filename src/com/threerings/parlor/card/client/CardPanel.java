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

package com.threerings.parlor.card.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

import com.samskivert.util.ObserverList;
import com.samskivert.util.QuickSort;

import com.threerings.media.FrameManager;
import com.threerings.media.VirtualMediaPanel;
import com.threerings.media.image.Mirage;
import com.threerings.media.sprite.PathAdapter;
import com.threerings.media.sprite.Sprite;
import com.threerings.media.util.LinePath;
import com.threerings.media.util.Path;
import com.threerings.media.util.PathSequence;

import com.threerings.parlor.card.data.Card;
import com.threerings.parlor.card.data.CardCodes;
import com.threerings.parlor.card.data.Hand;

/**
 * Extends VirtualMediaPanel to provide services specific to rendering and manipulating playing
 * cards.
 */
public abstract class CardPanel extends VirtualMediaPanel
    implements CardCodes
{
    /** The selection mode in which cards are not selectable. */
    public static final int NONE = 0;

    /** The selection mode in which the user can select a single card. */
    public static final int SINGLE = 1;

    /** The selection mode in which the user can select multiple cards. */
    public static final int MULTIPLE = 2;

    /**
     * A listener for card selection/deselection.
     */
    public static interface CardSelectionObserver
    {
        /**
         * Called when a card has been selected.
         */
        public void cardSpriteSelected (CardSprite sprite);

        /**
         * Called when a card has been deselected.
         */
        public void cardSpriteDeselected (CardSprite sprite);
    }

    /**
     * Constructor.
     *
     * @param frameManager the frame manager
     */
    public CardPanel (FrameManager frameManager)
    {
        super(frameManager);

        // add a listener for mouse events
        CardListener cl = new CardListener();
        addMouseListener(cl);
        addMouseMotionListener(cl);
    }

    /**
     * Returns the full-sized image for the back of a playing card.
     */
    public abstract Mirage getCardBackImage ();

    /**
     * Returns the full-sized image for the front of the specified card.
     */
    public abstract Mirage getCardImage (Card card);

    /**
     * Returns the small-sized image for the back of a playing card.
     */
    public abstract Mirage getMicroCardBackImage ();

    /**
     * Returns the small-sized image for the front of the specified card.
     */
    public abstract Mirage getMicroCardImage (Card card);

    /**
     * Sets the location of the hand (the location of the center of the hand's upper edge).
     */
    public void setHandLocation (int x, int y)
    {
        _handLocation.setLocation(x, y);
    }

    /**
     * Sets the horizontal spacing between cards in the hand.
     */
    public void setHandSpacing (int spacing)
    {
        _handSpacing = spacing;
    }

    /**
     * Sets the vertical distance to offset cards that are selectable or playable.
     */
    public void setSelectableCardOffset (int offset)
    {
        _selectableCardOffset = offset;
    }

    /**
     * Sets the vertical distance to offset cards that are selected.
     */
    public void setSelectedCardOffset (int offset)
    {
        _selectedCardOffset = offset;
    }

    /**
     * Sets the selection mode for the hand (NONE, PLAY_SINGLE, SINGLE, or MULTIPLE).  Changing the
     * selection mode does not change the current selection.
     */
    public void setHandSelectionMode (int mode)
    {
        _handSelectionMode = mode;

        // update the offsets of all cards in the hand
        updateHandOffsets();
    }

    /**
     * Sets the selection predicate that determines which cards from the hand may be selected (if
     * null, all cards may be selected).  Changing the predicate does not change the current
     * selection.
     */
    public void setHandSelectionPredicate (Predicate<CardSprite> pred)
    {
        _handSelectionPredicate = pred;

        // update the offsets of all cards in the hand
        updateHandOffsets();
    }

    /**
     * Returns the currently selected hand sprite (null if no sprites are selected, the first
     * sprite if multiple sprites are selected).
     */
    public CardSprite getSelectedHandSprite ()
    {
        return _selectedHandSprites.size() == 0 ?
            null : _selectedHandSprites.get(0);
    }

    /**
     * Returns an array containing the currently selected hand sprites (returns an empty array if
     * no sprites are selected).
     */
    public CardSprite[] getSelectedHandSprites ()
    {
        return _selectedHandSprites.toArray(
            new CardSprite[_selectedHandSprites.size()]);
    }

    /**
     * Programmatically selects a sprite in the hand.
     */
    public void selectHandSprite (final CardSprite sprite)
    {
        // make sure it's not already selected
        if (_selectedHandSprites.contains(sprite)) {
            return;
        }

        // if in single card mode and there's another card selected, deselect it
        if (_handSelectionMode == SINGLE) {
            CardSprite oldSprite = getSelectedHandSprite();
            if (oldSprite != null) {
                deselectHandSprite(oldSprite);
            }
        }

        // add to list and update offset
        _selectedHandSprites.add(sprite);
        sprite.setLocation(sprite.getX(), getHandY(sprite));

        // notify the observers
        ObserverList.ObserverOp<CardSelectionObserver> op =
            new ObserverList.ObserverOp<CardSelectionObserver>() {
            public boolean apply (CardSelectionObserver obs) {
                obs.cardSpriteSelected(sprite);
                return true;
            }
        };
        _handSelectionObservers.apply(op);
    }

    /**
     * Programmatically deselects a sprite in the hand.
     */
    public void deselectHandSprite (final CardSprite sprite)
    {
        // make sure it's selected
        if (!_selectedHandSprites.contains(sprite)) {
            return;
        }

        // remove from list and update offset
        _selectedHandSprites.remove(sprite);
        sprite.setLocation(sprite.getX(), getHandY(sprite));

        // notify the observers
        ObserverList.ObserverOp<CardSelectionObserver> op =
            new ObserverList.ObserverOp<CardSelectionObserver>() {
            public boolean apply (CardSelectionObserver obs) {
                obs.cardSpriteDeselected(sprite);
                return true;
            }
        };
        _handSelectionObservers.apply(op);
    }

    /**
     * Clears any existing hand sprite selection.
     */
    public void clearHandSelection ()
    {
        CardSprite[] sprites = getSelectedHandSprites();
        for (CardSprite sprite : sprites) {
            deselectHandSprite(sprite);
        }
    }

    /**
     * Adds an object to the list of observers to notify when cards in the hand are
     * selected/deselected.
     */
    public void addHandSelectionObserver (CardSelectionObserver obs)
    {
        _handSelectionObservers.add(obs);
    }

    /**
     * Removes an object from the hand selection observer list.
     */
    public void removeHandSelectionObserver (CardSelectionObserver obs)
    {
        _handSelectionObservers.remove(obs);
    }

    /**
     * Fades a hand of cards in.
     *
     * @param hand the hand of cards
     * @param fadeDuration the amount of time to spend fading in the entire hand
     */
    public void setHand (Hand hand, long fadeDuration)
    {
        // make sure no cards are hanging around
        clearHand();

        // create the sprites
        int size = hand.size();
        for (int ii = 0; ii < size; ii++) {
            CardSprite cs = new CardSprite(this, hand.get(ii));
            _handSprites.add(cs);
        }

        // sort them
        if (shouldSortHand()) {
            QuickSort.sort(_handSprites, CARD_COMP);
        }

        // fade them in at proper locations and layers
        long cardDuration = fadeDuration / size;
        for (int ii = 0; ii < size; ii++) {
            CardSprite cs = _handSprites.get(ii);
            cs.setLocation(getHandX(size, ii), _handLocation.y);
            cs.setRenderOrder(ii);
            cs.addSpriteObserver(_handSpriteObserver);
            addSprite(cs);
            cs.fadeIn(ii * cardDuration, cardDuration);
        }

        // make sure we have the right card sprite active
        updateActiveCardSprite();
    }

    /**
     * Fades a hand of cards in face-down.
     *
     * @param size the size of the hand
     * @param fadeDuration the amount of time to spend fading in each card
     */
    public void setHand (int size, long fadeDuration)
    {
        // fill hand will null entries to signify unknown cards
        Hand hand = new Hand();
        for (int ii = 0; ii < size; ii++) {
            hand.add(null);
        }
        setHand(hand, fadeDuration);
    }

    /**
     * Shows a hand that was previous set face-down.
     *
     * @param hand the hand of cards
     */
    public void showHand (Hand hand)
    {
        // sort the hand
        if (shouldSortHand()) {
            QuickSort.sort(hand);
        }

        // set the sprites
        int len = Math.min(_handSprites.size(), hand.size());
        for (int ii = 0; ii < len; ii++) {
            CardSprite cs = _handSprites.get(ii);
            cs.setCard(hand.get(ii));
        }
    }

    /**
     * Returns the first sprite in the hand that corresponds to the specified card, or null if the
     * card is not in the hand.
     */
    public CardSprite getHandSprite (Card card)
    {
        return getCardSprite(_handSprites, card);
    }

    /**
     * Clears all cards from the hand.
     */
    public void clearHand ()
    {
        clearHandSelection();
        clearSprites(_handSprites);
    }

    /**
     * Clears all cards from the board.
     */
    public void clearBoard ()
    {
        clearSprites(_boardSprites);
    }

    /**
     * Flies a set of cards from the hand into the ether.  Clears any selected cards.
     *
     * @param cards the card sprites to remove from the hand
     * @param dest the point to fly the cards to
     * @param flightDuration the duration of the cards' flight
     * @param fadePortion the amount of time to spend fading out as a proportion of the flight
     * duration
     */
    public void flyFromHand (CardSprite[] cards, Point dest, long flightDuration, float fadePortion)
    {
        // fly each sprite over, removing it from the hand immediately and from the board when it
        // finishes its path
        for (CardSprite card : cards) {
            removeFromHand(card);
            LinePath flight = new LinePath(dest, flightDuration);
            card.addSpriteObserver(_pathEndRemover);
            card.moveAndFadeOut(flight, flightDuration, fadePortion);
        }

        // adjust the hand to cover the hole
        adjustHand(flightDuration, false);
    }

    /**
     * Flies a set of cards from the ether into the hand.  Clears any selected cards.  The cards
     * will first fly to the selected card offset, pause for the specified duration, and then drop
     * into the hand.
     *
     * @param cards the cards to add to the hand
     * @param src the point to fly the cards from
     * @param flightDuration the duration of the cards' flight
     * @param pauseDuration the duration of the pause before dropping into the hand
     * @param dropDuration the duration of the cards' drop into the hand
     * @param fadePortion the amount of time to spend fading in as a proportion of the flight
     * duration
     */
    public void flyIntoHand (Card[] cards, Point src, long flightDuration, long pauseDuration,
                             long dropDuration, float fadePortion)
    {
        // first create the sprites and add them to the list
        CardSprite[] sprites = new CardSprite[cards.length];
        for (int ii = 0; ii < cards.length; ii++) {
            sprites[ii] = new CardSprite(this, cards[ii]);
            _handSprites.add(sprites[ii]);
        }

        // settle the hand
        adjustHand(flightDuration, true);

        // then set the layers and fly the cards in
        int size = _handSprites.size();
        for (CardSprite sprite : sprites) {
            int idx = _handSprites.indexOf(sprite);
            sprite.setLocation(src.x, src.y);
            sprite.setRenderOrder(idx);
            sprite.addSpriteObserver(_handSpriteObserver);
            addSprite(sprite);

            // create a path sequence containing flight, pause, and drop
            ArrayList<Path> paths = Lists.newArrayList();
            Point hp2 = new Point(getHandX(size, idx), _handLocation.y),
                hp1 = new Point(hp2.x, hp2.y - _selectedCardOffset);
            paths.add(new LinePath(hp1, flightDuration));
            paths.add(new LinePath(hp1, pauseDuration));
            paths.add(new LinePath(hp2, dropDuration));
            sprite.moveAndFadeIn(new PathSequence(paths), flightDuration +
                                     pauseDuration + dropDuration, fadePortion);
        }
    }

    /**
     * Flies a set of cards from the ether into the ether.
     *
     * @param cards the cards to fly across
     * @param src the point to fly the cards from
     * @param dest the point to fly the cards to
     * @param flightDuration the duration of the cards' flight
     * @param cardDelay the amount of time to wait between cards
     * @param fadePortion the amount of time to spend fading in and out as a proportion of the
     * flight duration
     */
    public void flyAcross (Card[] cards, Point src, Point dest, long flightDuration,
                           long cardDelay, float fadePortion)
    {
        for (int ii = 0; ii < cards.length; ii++) {
            // add on top of all board sprites
            CardSprite cs = new CardSprite(this, cards[ii]);
            cs.setRenderOrder(getHighestBoardLayer() + 1 + ii);
            cs.setLocation(src.x, src.y);
            addSprite(cs);

            // prepend an initial delay to all cards after the first
            Path path;
            long pathDuration;
            LinePath flight = new LinePath(dest, flightDuration);
            if (ii > 0) {
                long delayDuration = cardDelay * ii;
                LinePath delay = new LinePath(src, delayDuration);
                path = new PathSequence(delay, flight);
                pathDuration = delayDuration + flightDuration;

            } else {
                path = flight;
                pathDuration = flightDuration;
            }
            cs.addSpriteObserver(_pathEndRemover);
            cs.moveAndFadeInAndOut(path, pathDuration, fadePortion);
        }
    }

    /**
     * Flies a set of cards from the ether into the ether face-down.
     *
     * @param number the number of cards to fly across
     * @param src the point to fly the cards from
     * @param dest the point to fly the cards to
     * @param flightDuration the duration of the cards' flight
     * @param cardDelay the amount of time to wait between cards
     * @param fadePortion the amount of time to spend fading in and out as a proportion of the
     * flight duration
     */
    public void flyAcross (int number, Point src, Point dest, long flightDuration,
                           long cardDelay, float fadePortion)
    {
        // use null values to signify unknown cards
        flyAcross(new Card[number], src, dest, flightDuration,
            cardDelay, fadePortion);
    }

    /**
     * Flies a card from the hand onto the board.  Clears any cards selected.
     *
     * @param card the sprite to remove from the hand
     * @param dest the point to fly the card to
     * @param flightDuration the duration of the card's flight
     */
    public void flyFromHandToBoard (CardSprite card, Point dest, long flightDuration)
    {
        // fly it over
        LinePath flight = new LinePath(dest, flightDuration);
        card.move(flight);

        // lower the board so that the card from hand is on top
        lowerBoardSprites(card.getRenderOrder() - 1);

        // move from one list to the other
        removeFromHand(card);
        _boardSprites.add(card);

        // adjust the hand to cover the hole
        adjustHand(flightDuration, false);
    }

    /**
     * Flies a card from the ether onto the board.
     *
     * @param card the card to add to the board
     * @param src the point to fly the card from
     * @param dest the point to fly the card to
     * @param flightDuration the duration of the card's flight
     * @param fadePortion the amount of time to spend fading in as a proportion of the flight
     * duration
     */
    public void flyToBoard (Card card, Point src, Point dest, long flightDuration,
                            float fadePortion)
    {
        // add it on top of the existing cards
        CardSprite cs = new CardSprite(this, card);
        cs.setRenderOrder(getHighestBoardLayer() + 1);
        cs.setLocation(src.x, src.y);
        addSprite(cs);
        _boardSprites.add(cs);

        // and fly it over
        LinePath flight = new LinePath(dest, flightDuration);
        cs.moveAndFadeIn(flight, flightDuration, fadePortion);
    }

    /**
     * Adds a card to the board immediately.
     *
     * @param card the card to add to the board
     * @param dest the point at which to add the card
     */
    public void addToBoard (Card card, Point dest)
    {
        CardSprite cs = new CardSprite(this, card);
        cs.setRenderOrder(getHighestBoardLayer() + 1);
        cs.setLocation(dest.x, dest.y);
        addSprite(cs);
        _boardSprites.add(cs);
    }

    /**
     * Flies a set of cards from the board into the ether.
     *
     * @param cards the cards to remove from the board
     * @param dest the point to fly the cards to
     * @param flightDuration the duration of the cards' flight
     * @param fadePortion the amount of time to spend fading out as a proportion of the flight
     * duration
     */
    public void flyFromBoard (CardSprite[] cards, Point dest, long flightDuration,
                              float fadePortion)
    {
        for (CardSprite card : cards) {
            LinePath flight = new LinePath(dest, flightDuration);
            card.addSpriteObserver(_pathEndRemover);
            card.moveAndFadeOut(flight, flightDuration, fadePortion);
            _boardSprites.remove(card);
        }
    }

    /**
     * Flies a set of cards from the board into the ether through an intermediate point.
     *
     * @param cards the cards to remove from the board
     * @param dest1 the first point to fly the cards to
     * @param dest2 the final destination of the cards
     * @param flightDuration the duration of the cards' flight
     * @param fadePortion the amount of time to spend fading out as a proportion of the flight
     * duration
     */
    public void flyFromBoard (CardSprite[] cards, Point dest1, Point dest2, long flightDuration,
                              float fadePortion)
    {
        for (CardSprite card : cards) {
            PathSequence flight = new PathSequence(
                new LinePath(dest1, flightDuration/2),
                new LinePath(dest1, dest2, flightDuration/2));
            card.addSpriteObserver(_pathEndRemover);
            card.moveAndFadeOut(flight, flightDuration, fadePortion);
            _boardSprites.remove(card);
        }
    }

    /**
     * Returns the first card sprite in the specified list that represents the specified card, or
     * null if there is no such sprite in the list.
     */
    protected CardSprite getCardSprite (List<CardSprite> list, Card card)
    {
        for (int ii = 0; ii < list.size(); ii++) {
            CardSprite cs = list.get(ii);
            if (card.equals(cs.getCard())) {
                return cs;
            }
        }
        return null;
    }

    /**
     * Returns whether the user's hand should be sorted when displayed.  By default, hands are
     * sorted.
     */
    protected boolean shouldSortHand ()
    {
        return true;
    }

    /**
     * Expands or collapses the hand to accommodate new cards or cover the space left by removed
     * cards.  Skips unmanaged sprites.  Clears out any selected cards.
     *
     * @param adjustDuration the amount of time to spend settling the cards into their new
     * locations
     * @param updateLayers whether or not to update the layers of the cards
     */
    protected void adjustHand (long adjustDuration, boolean updateLayers)
    {
        // clear out selected cards
        clearHandSelection();

        // Sort the hand
        if (shouldSortHand()) {
            QuickSort.sort(_handSprites, CARD_COMP);
        }

        // Move each card to its proper position (and, optionally, layer)
        int size = _handSprites.size();
        for (int ii = 0; ii < size; ii++) {
            CardSprite cs = _handSprites.get(ii);
            if (!isManaged(cs)) {
                continue;
            }
            if (updateLayers) {
                cs.setRenderOrder(ii);
            }
            LinePath adjust = new LinePath(
                new Point(getHandX(size, ii), _handLocation.y), adjustDuration);
            cs.move(adjust);
        }
    }

    /**
     * Removes a card from the hand.
     */
    protected void removeFromHand (CardSprite card)
    {
        _selectedHandSprites.remove(card);
        _handSprites.remove(card);
    }

    /**
     * Updates the offsets of all the cards in the hand.  If there is only one selectable card,
     * that card will always be raised slightly.
     */
    protected void updateHandOffsets ()
    {
        // make active card sprite is up-to-date
        updateActiveCardSprite();

        int size = _handSprites.size();
        for (int ii = 0; ii < size; ii++) {
            CardSprite cs = _handSprites.get(ii);
            if (!cs.isMoving()) {
                cs.setLocation(cs.getX(), getHandY(cs));
            }
        }
    }

    /**
     * Given the location and spacing of the hand, returns the x location of the card at the
     * specified index within a hand of the specified size.
     */
    protected int getHandX (int size, int idx)
    {
        // get the card width from the image if not yet known
        if (_cardWidth == 0) {
            _cardWidth = getCardBackImage().getWidth();
        }
        // first compute the width of the entire hand, then use that to determine the centered
        // location
        int width = (size - 1) * _handSpacing + _cardWidth;
        return (_handLocation.x - width/2) + idx * _handSpacing;
    }

    /**
     * Determines the y location of the specified card sprite, given its selection state.
     */
    protected int getHandY (CardSprite sprite)
    {
        if (_selectedHandSprites.contains(sprite)) {
            return _handLocation.y - _selectedCardOffset;

        } else if (isSelectable(sprite) &&
                   (sprite == _activeCardSprite || isOnlySelectable(sprite))) {
            return _handLocation.y - _selectableCardOffset;

        } else {
            return _handLocation.y;
        }
    }

    /**
     * Given the current selection mode and predicate, determines if the specified sprite is
     * selectable.
     */
    protected boolean isSelectable (CardSprite sprite)
    {
        return _handSelectionMode != NONE &&
            (_handSelectionPredicate == null || _handSelectionPredicate.apply(sprite));
    }

    /**
     * Determines whether the specified sprite is the only selectable sprite in the hand according
     * to the selection predicate.
     */
    protected boolean isOnlySelectable (CardSprite sprite)
    {
        // if there's no predicate, last remaining card is only selectable
        if (_handSelectionPredicate == null) {
            return _handSprites.size() == 1 && _handSprites.contains(sprite);
        }

        // otherwise, look for a sprite that fits the predicate and isn't the parameter
        for (CardSprite cs : _handSprites) {
            if (cs != sprite && _handSelectionPredicate.apply(cs)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lowers all board sprites so that they are rendered at or below the specified layer.
     */
    protected void lowerBoardSprites (int layer)
    {
        // see if they're already lower
        int highest = getHighestBoardLayer();
        if (highest <= layer) {
            return;
        }

        // lower them just enough
        int size = _boardSprites.size(), adjustment = layer - highest;
        for (int ii = 0; ii < size; ii++) {
            CardSprite cs = _boardSprites.get(ii);
            cs.setRenderOrder(cs.getRenderOrder() + adjustment);
        }
    }

    /**
     * Returns the highest render order of any sprite on the board.
     */
    protected int getHighestBoardLayer ()
    {
        // must be at least zero, because that's the lowest number we can push the sprites down to
        // (the layer of the first card in the hand)
        int size = _boardSprites.size(), highest = 0;
        for (int ii = 0; ii < size; ii++) {
            highest = Math.max(highest, _boardSprites.get(ii).getRenderOrder());
        }
        return highest;
    }

    /**
     * Clears an array of sprites from the specified list and from the panel.
     */
    protected void clearSprites (List<CardSprite> sprites)
    {
        for (Iterator<CardSprite> it = sprites.iterator(); it.hasNext(); ) {
            removeSprite(it.next());
            it.remove();
        }
    }

    /**
     * Updates the active card sprite based on the location of the mouse pointer.
     */
    protected void updateActiveCardSprite ()
    {
        // can't do anything if we don't know where the mouse pointer is
        if (_mouseEvent == null) {
            return;
        }

        Sprite newHighestHit = _spritemgr.getHighestHitSprite(
            _mouseEvent.getX(), _mouseEvent.getY());

        CardSprite newActiveCardSprite =
            (newHighestHit instanceof CardSprite ? (CardSprite)newHighestHit : null);

        if (_activeCardSprite != newActiveCardSprite) {
            if (_activeCardSprite != null && isManaged(_activeCardSprite)) {
                _activeCardSprite.queueNotification(
                    new CardSpriteExitedOp(_activeCardSprite, _mouseEvent));
            }
            _activeCardSprite = newActiveCardSprite;
            if (_activeCardSprite != null) {
                _activeCardSprite.queueNotification(
                    new CardSpriteEnteredOp(_activeCardSprite, _mouseEvent));
            }
        }
    }

    @Override
    protected void paintBehind (Graphics2D gfx, Rectangle dirtyRect)
    {
        gfx.setColor(DEFAULT_BACKGROUND);
        gfx.fill(dirtyRect);
        super.paintBehind(gfx, dirtyRect);
    }

    /** Listens for interactions with cards in hand. */
    protected class HandSpriteObserver extends PathAdapter
        implements CardSpriteObserver
    {
        @Override
        public void pathCompleted (Sprite sprite, Path path, long when)
        {
            updateActiveCardSprite();
            maybeUpdateOffset((CardSprite)sprite);
        }

        public void cardSpriteClicked (CardSprite sprite, MouseEvent me)
        {
            // select, deselect, or play card in hand
            if (_selectedHandSprites.contains(sprite) &&
                _handSelectionMode != NONE) {
                deselectHandSprite(sprite);

            } else if (_handSprites.contains(sprite) && isSelectable(sprite)) {
                selectHandSprite(sprite);
            }
        }

        public void cardSpriteEntered (CardSprite sprite, MouseEvent me)
        {
            maybeUpdateOffset(sprite);
        }

        public void cardSpriteExited (CardSprite sprite, MouseEvent me)
        {
            maybeUpdateOffset(sprite);
        }

        public void cardSpriteDragged (CardSprite sprite, MouseEvent me)
        {}

        protected void maybeUpdateOffset (CardSprite sprite)
        {
            // update the offset if it's in the hand and isn't moving
            if (_handSprites.contains(sprite) && !sprite.isMoving()) {
                sprite.setLocation(sprite.getX(), getHandY(sprite));
            }
        }
    }

    /** Listens for mouse interactions with cards. */
    protected class CardListener extends MouseInputAdapter
    {
        @Override
        public void mousePressed (MouseEvent me)
        {
            if (_activeCardSprite != null &&
                isManaged(_activeCardSprite)) {
                _handleX = _activeCardSprite.getX() - me.getX();
                _handleY = _activeCardSprite.getY() - me.getY();
                _hasBeenDragged = false;
            }
        }

        @Override
        public void mouseReleased (MouseEvent me)
        {
            if (_activeCardSprite != null &&
                isManaged(_activeCardSprite) &&
                _hasBeenDragged) {
                _activeCardSprite.queueNotification(
                    new CardSpriteDraggedOp(_activeCardSprite, me)
                );
            }
        }

        @Override
        public void mouseClicked (MouseEvent me)
        {
            if (_activeCardSprite != null &&
                isManaged(_activeCardSprite)) {
                _activeCardSprite.queueNotification(
                    new CardSpriteClickedOp(_activeCardSprite, me)
                );
            }
        }

        @Override
        public void mouseMoved (MouseEvent me)
        {
            _mouseEvent = me;

            updateActiveCardSprite();
        }

        @Override
        public void mouseDragged (MouseEvent me)
        {
            _mouseEvent = me;

            if (_activeCardSprite != null &&
                isManaged(_activeCardSprite) &&
                _activeCardSprite.isDraggable()) {
                _activeCardSprite.setLocation(
                    me.getX() + _handleX,
                    me.getY() + _handleY
                );
                _hasBeenDragged = true;

            } else {
                updateActiveCardSprite();
            }
        }

        @Override
        public void mouseEntered (MouseEvent me)
        {
            _mouseEvent = me;
        }

        @Override
        public void mouseExited (MouseEvent me)
        {
            _mouseEvent = me;
        }

        protected int _handleX, _handleY;
        protected boolean _hasBeenDragged;
    }

    /** Calls CardSpriteObserver.cardSpriteClicked. */
    protected static class CardSpriteClickedOp implements
        ObserverList.ObserverOp<Object>
    {
        public CardSpriteClickedOp (CardSprite sprite, MouseEvent me)
        {
            _sprite = sprite;
            _me = me;
        }

        public boolean apply (Object observer)
        {
            if (observer instanceof CardSpriteObserver) {
                ((CardSpriteObserver)observer).cardSpriteClicked(_sprite,
                    _me);
            }
            return true;
        }

        protected CardSprite _sprite;
        protected MouseEvent _me;
    }

    /** Calls CardSpriteObserver.cardSpriteEntered. */
    protected static class CardSpriteEnteredOp implements
        ObserverList.ObserverOp<Object>
    {
        public CardSpriteEnteredOp (CardSprite sprite, MouseEvent me)
        {
            _sprite = sprite;
            _me = me;
        }

        public boolean apply (Object observer)
        {
            if (observer instanceof CardSpriteObserver) {
                ((CardSpriteObserver)observer).cardSpriteEntered(_sprite, _me);
            }
            return true;
        }

        protected CardSprite _sprite;
        protected MouseEvent _me;
    }

    /** Calls CardSpriteObserver.cardSpriteExited. */
    protected static class CardSpriteExitedOp implements
        ObserverList.ObserverOp<Object>
    {
        public CardSpriteExitedOp (CardSprite sprite, MouseEvent me)
        {
            _sprite = sprite;
            _me = me;
        }

        public boolean apply (Object observer)
        {
            if (observer instanceof CardSpriteObserver) {
                ((CardSpriteObserver)observer).cardSpriteExited(_sprite, _me);
            }
            return true;
        }

        protected CardSprite _sprite;
        protected MouseEvent _me;
    }

    /** Calls CardSpriteObserver.cardSpriteDragged. */
    protected static class CardSpriteDraggedOp implements
        ObserverList.ObserverOp<Object>
    {
        public CardSpriteDraggedOp (CardSprite sprite, MouseEvent me)
        {
            _sprite = sprite;
            _me = me;
        }

        public boolean apply (Object observer)
        {
            if (observer instanceof CardSpriteObserver) {
                ((CardSpriteObserver)observer).cardSpriteDragged(_sprite,
                    _me);
            }
            return true;
        }

        protected CardSprite _sprite;
        protected MouseEvent _me;
    }

    /** The width of the playing cards. */
    protected int _cardWidth;

    /** The last motion/entrance/exit event received from the mouse. */
    protected MouseEvent _mouseEvent;

    /** The currently active card sprite (the one that the mouse is over). */
    protected CardSprite _activeCardSprite;

    /** The sprites for cards within the hand. */
    protected ArrayList<CardSprite> _handSprites = Lists.newArrayList();

    /** The sprites for cards within the hand that have been selected. */
    protected ArrayList<CardSprite> _selectedHandSprites = Lists.newArrayList();

    /** The current selection mode for the hand. */
    protected int _handSelectionMode;

    /** The predicate that determines which cards are selectable (if null, all
     * cards are selectable). */
    protected Predicate<CardSprite> _handSelectionPredicate;

    /** Observers of hand card selection/deselection. */
    protected ObserverList<CardSelectionObserver> _handSelectionObservers =
        ObserverList.newFastUnsafe();

    /** The location of the center of the hand's upper edge. */
    protected Point _handLocation = new Point();

    /** The horizontal distance between cards in the hand. */
    protected int _handSpacing;

    /** The vertical distance to offset cards that are selectable. */
    protected int _selectableCardOffset;

    /** The vertical distance to offset cards that are selected. */
    protected int _selectedCardOffset;

    /** The sprites for cards on the board. */
    protected ArrayList<CardSprite> _boardSprites = Lists.newArrayList();

    /** The hand sprite observer instance. */
    protected HandSpriteObserver _handSpriteObserver = new HandSpriteObserver();

    /** A path observer that removes the sprite at the end of its path. */
    protected PathAdapter _pathEndRemover = new PathAdapter() {
        @Override
        public void pathCompleted (Sprite sprite, Path path, long when) {
            removeSprite(sprite);
        }
    };

    /** Compares two card sprites based on their underlying card. */
    protected static final Comparator<CardSprite> CARD_COMP = new Comparator<CardSprite>() {
        public int compare (CardSprite cs1, CardSprite cs2) {
            if (cs1._card == null || cs2._card == null) {
                return 0;
            } else {
                return cs1._card.compareTo(cs2._card);
            }
        }
    };

    /** A nice default green card table background color. */
    protected static final Color DEFAULT_BACKGROUND = new Color(0x326D36);
}
