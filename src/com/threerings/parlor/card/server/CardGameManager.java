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

package com.threerings.parlor.card.server;

import com.threerings.presents.data.ClientObject;

import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.server.OccupantOp;

import com.threerings.parlor.card.data.Card;
import com.threerings.parlor.card.data.CardCodes;
import com.threerings.parlor.card.data.CardGameObject;
import com.threerings.parlor.card.data.Deck;
import com.threerings.parlor.card.data.Hand;
import com.threerings.parlor.game.data.GameObject;
import com.threerings.parlor.game.server.GameManager;
import com.threerings.parlor.turn.server.TurnGameManager;

/**
 * A manager class for card games. Handles common functions like dealing hands of cards to all
 * players.
 */
public class CardGameManager extends GameManager
    implements TurnGameManager, CardCodes
{
    @Override
    protected void didStartup ()
    {
        super.didStartup();
        _cardgameobj = (CardGameObject)_gameobj;
    }

    // Documentation inherited.
    public void turnWillStart ()
    {}

    // Documentation inherited.
    public void turnDidStart ()
    {}

    // Documentation inherited.
    public void turnDidEnd ()
    {}

    @Override
    protected void gameDidEnd ()
    {
        // Copy off our player oids so we have them handy to see if somebody was a player for
        // rematch purposes.
        _oldPlayerOids = _playerOids.clone();

        super.gameDidEnd();
    }

    /**
     * This should be called to start a rematched game. It just starts the current game anew, but
     * provides a mechanism for derived classes to do special things when there is a rematch.
     */
    public void rematchGame ()
    {
        if (gameWillRematch()) {
            // Stuff our old oids back in since they're ready.
            _playerOids = _oldPlayerOids.clone();
            startGame();
        }
    }

    /**
     * Derived classes can override this method and take any action needed prior to a game
     * rematch. If the rematch needs to be vetoed for any reason, they can return false from this
     * method and the rematch will be aborted.
     */
    protected boolean gameWillRematch ()
    {
        return true;
    }

    /**
     * Deals a hand of cards to the player at the specified index from the given Deck.
     *
     * @param deck the deck from which to deal
     * @param size the size of the hand to deal
     * @param playerIndex the index of the target player
     * @return the hand dealt to the player, or null if the deal was canceled because the deck did
     * not contain enough cards
     */
    public Hand dealHand (Deck deck, int size, int playerIndex)
    {
        if (deck.size() < size) {
            return null;

        } else {
            Hand hand = deck.dealHand(size);
            if (!isAI(playerIndex)) {
                ClientObject clobj = (ClientObject)_omgr.getObject(_playerOids[playerIndex]);
                if (clobj != null) {
                    CardGameSender.sendHand(clobj, _cardgameobj.getOid(), hand);
                }
            }
            return hand;
        }
    }

    /**
     * Deals a hand of cards to each player from the specified Deck.
     *
     * @param deck the deck from which to deal
     * @param size the size of the hands to deal
     * @return the array of hands dealt to each player, or null if the deal was canceled because
     * the deck did not contain enough cards
     */
    public Hand[] dealHands (Deck deck, int size)
    {
        if (deck.size() < size * _playerCount) {
            return null;

        } else {
            Hand[] hands = new Hand[_playerCount];

            for (int ii = 0; ii < _playerCount; ii++) {
                hands[ii] = dealHand(deck, size, ii);
            }

            return hands;
        }
    }

    /**
     * Gets the player index of the specified client object, or -1 if the client object does not
     * represent a player. If the game has ended, looks through the players of the now-ended game.
     */
    public int getPlayerIndex (ClientObject client)
    {
        int[] oids = (_gameobj.state == GameObject.GAME_OVER) ? _oldPlayerOids : _playerOids;
        int oid = client.getOid();

        for (int ii = 0; ii < oids.length; ii++) {
            if (oids[ii] == oid) {
                return ii;
            }
        }
        return -1;
    }

    /**
     * Returns the client object corresponding to the specified player index, or null if the
     * position is not occupied by a player.
     */
    public ClientObject getClientObject (int pidx)
    {
        if (_playerOids[pidx] != 0) {
            return (ClientObject)_omgr.getObject(_playerOids[pidx]);

        } else {
            return null;
        }
    }

    /**
     * Sends a set of cards from one player to another.
     *
     * @param fromPlayerIdx the index of the player sending the cards
     * @param toPlayerIdx the index of the player receiving the cards
     * @param cards the cards to be exchanged
     */
    public void transferCardsBetweenPlayers (int fromPlayerIdx,
        int toPlayerIdx,  Card[] cards)
    {
        // Notify the sender that the cards have been taken
        ClientObject fromClient = getClientObject(fromPlayerIdx);
        if (fromClient != null) {
            CardGameSender.sentCardsToPlayer(fromClient, toPlayerIdx, cards);
        }

        // Notify the receiver with the cards
        ClientObject toClient = getClientObject(toPlayerIdx);
        if (toClient != null) {
            CardGameSender.sendCardsFromPlayer(toClient, fromPlayerIdx,
                cards);
        }

        // and everybody else in the room other than the sender and the
        // receiver with the number of cards sent
        notifyCardsTransferred(fromPlayerIdx, toPlayerIdx, cards.length);
    }

    /**
     * Sends sets of cards between players simultaneously. Each player is guaranteed to receive
     * the notification of cards received after the notification of cards sent. The length of the
     * arrays passed must be equal to the player count.
     *
     * @param toPlayerIndices for each player, the index of the player to transfer cards to
     * @param cards for each player, the cards to transfer
     */
    public void transferCardsBetweenPlayers (int[] toPlayerIndices, Card[][] cards)
    {
        // Send all removal notices
        for (int ii = 0; ii < _playerCount; ii++) {
            ClientObject fromClient = getClientObject(ii);
            if (fromClient != null) {
                CardGameSender.sentCardsToPlayer(fromClient, toPlayerIndices[ii], cards[ii]);
            }
        }

        // Send all addition notices and notify everyone else
        for (int ii = 0; ii < _playerCount; ii++) {
            ClientObject toClient = getClientObject(toPlayerIndices[ii]);
            if (toClient != null) {
                CardGameSender.sendCardsFromPlayer(toClient, ii, cards[ii]);
            }
            notifyCardsTransferred(ii, toPlayerIndices[ii], cards[ii].length);
        }
    }

    /**
     * Notifies everyone in the room (other than the sender and the receiver) that a set of cards
     * have been transferred.
     *
     * @param fromPlayerIdx the index of the player sending the cards
     * @param toPlayerIdx the index of the player receiving the cards
     * @param cards the number of cards sent
     */
    protected void notifyCardsTransferred (
        final int fromPlayerIdx, final int toPlayerIdx, final int cards)
    {
        final int senderOid = _playerOids[fromPlayerIdx],
            receiverOid = _playerOids[toPlayerIdx];
        OccupantOp op = new OccupantOp() {
            public void apply (OccupantInfo info) {
                int oid = info.getBodyOid();
                if (oid != senderOid && oid != receiverOid) {
                    ClientObject client = (ClientObject)_omgr.getObject(oid);
                    if (client != null) {
                        CardGameSender.cardsTransferredBetweenPlayers(client,
                            fromPlayerIdx, toPlayerIdx, cards);
                    }
                }
            }
        };
        applyToOccupants(op);
    }

    /** The card game object. */
    protected CardGameObject _cardgameobj;

    /** The oids of the players from our now-ended game. */
    protected int[] _oldPlayerOids;
}
