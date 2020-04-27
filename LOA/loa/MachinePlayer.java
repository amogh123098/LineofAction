/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;
import java.util.List;

import static loa.Piece.*;

/** An automated Player.
 *  @author Amogh
 */
class MachinePlayer extends Player {

    /** A position-score magnitude indicating a win (for white if positive,
     *  black if negative). */
    private static final int WINNING_VALUE = Integer.MAX_VALUE - 20;
    /** A magnitude greater than a normal value. */
    private static final int INFTY = Integer.MAX_VALUE;

    /** A new MachinePlayer with no piece or controller (intended to produce
     *  a template). */
    MachinePlayer() {
        this(null, null);
    }

    /** A MachinePlayer that plays the SIDE pieces in GAME. */
    MachinePlayer(Piece side, Game game) {
        super(side, game);
    }

    @Override
    String getMove() {
        Move choice;
        assert side() == getGame().getBoard().turn();
        choice = searchForMove();
        getGame().reportMove(choice);
        return choice.toString();
    }

    @Override
    Player create(Piece piece, Game game) {
        return new MachinePlayer(piece, game);
    }

    @Override
    boolean isManual() {
        return false;
    }

    /** Return a move after searching the game tree to DEPTH>0 moves
     *  from the current position. Assumes the game is not over. */
    private Move searchForMove() {
        long n1 = System.currentTimeMillis();
        Board work = new Board(getBoard());
        int value;
        assert side() == work.turn();
        _foundMove = null;
        _maxdepth = chooseDepth();
        if (_maxdepth < 1) {
            findMove(work, _maxdepth, true, 1, -INFTY, INFTY);
        } else {
            for (int depth = 1; depth <= _maxdepth; depth++) {
                if (side() == WP) {
                    value = findMove(work, depth, true,
                            1, -INFTY, INFTY);
                } else {
                    value = findMove(work, depth, true,
                            -1, -INFTY, INFTY);
                }
                if (WINNING_VALUE < (side() == WP ? value : -value)) {
                    break;
                }
            }
        }
        if (side() == BP) {
            long n2 = System.currentTimeMillis();
            SPENT += n2 - n1;
            AVG = (2 * SPENT) / (getBoard().movesMade() + 1);

        }
        return _foundMove;
    }

    /** Find a move from position BOARD and return its value, recording
     *  the move found in _foundMove iff SAVEMOVE. The move
     *  should have maximal value or have value > BETA if SENSE==1,
     *  and minimal value or value < ALPHA if SENSE==-1. Searches up to
     *  DEPTH levels.  Searching at level 0 simply returns a static estimate
     *  of the board value and does not set _foundMove. If the game is over
     *  on BOARD, does not set _foundMove. */
    private int findMove(Board board, int depth, boolean saveMove,
                         int sense, int alpha, int beta) {
        if (board.gameOver()) {
            return board.winner() == EMP ? 0
                    : board.winner() == WP ? INFTY : -INFTY;
        }
        if (depth == 0) {
            return board.heuristicValue();
        }
        if (depth == -1) {
            List<Move> legalMoves =  board.legalMoves(null);
            _foundMove = legalMoves.get(getGame().randInt(legalMoves.size()));
            board.makeMove(_foundMove);
            return board.heuristicValue();
        }
        List<Move> legalMoves =  board.legalMoves(null);
        int bestValue = -INFTY;
        Move bestSoFar = legalMoves.get(0);
        for (Move legal : legalMoves) {
            board.makeMove(legal);
            int current =
                    sense * findMove(board, depth - 1,
                            false, -sense, alpha, beta);
            board.retract();

            if (current >= bestValue) {
                bestSoFar = legal;
                bestValue = current;
                if (sense == -1) {
                    beta = Math.min(beta, sense * current);
                } else {
                    alpha = Math.max(alpha, current);
                }
                if (beta <= alpha) {
                    break;
                }
            }
        }
        if (saveMove) {
            _foundMove = bestSoFar;
        }
        return sense * bestValue;

    }

    /** Return a search depth for the current position. */
    private int chooseDepth() {
        if (_depth + getBoard().movesMade() >= 2 * getBoard().getMovelimit()) {
            return getBoard().getMovelimit() - getBoard().movesMade();
        }
        if (getBoard().movesMade() < 2) {
            return -1;
        }
        if (getBoard().movesMade() < 15) {
            return 3;
        }
        if (2 * getBoard().getMovelimit() - getBoard().movesMade() < 10) {
            return Math.max(_depth, 5);
        }
        if (getBoard().movesMade() >= HALFGAME) {
            if ((getBoard().repeatMove()
                    || getBoard().legalMoves(null).size() <= HANDEL)
                    && (_depth < 8)) {
                _depth += 1;
            }
        }
        return _depth;
    }

    /** Used to convey moves discovered by findMove. */
    private Move _foundMove;
    /** depth.*/
    private int _depth = 4;
    /** maxDDepth for that minmax. */
    public static int _maxdepth = 1;
    /** branching factor that could be HANDLED. */
    private static final int HANDEL = 20;
    /** Half game. */
    private static final int HALFGAME = 30;
    /** Total time. */
    public static final long TOTALTIME = 1000 * 30 * 5;
    /** Time Spent. */
    public static long SPENT = 0;
    /** Average move time. */
    public static long AVG = 0;

}
