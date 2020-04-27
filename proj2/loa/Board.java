/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Collections.reverseOrder;
import static loa.Piece.BP;
import static loa.Piece.EMP;
import static loa.Piece.WP;
import static loa.Square.ALL_SQUARES;
import static loa.Square.BOARD_SIZE;
import static loa.Square.sq;

/**
 * Represents the state of a game of Lines of Action.
 *
 * @author Amogh
 */
class Board {

    /**
     * Default number of moves for each side that results in a draw.
     */
    static final int DEFAULT_MOVE_LIMIT = 30;

    /**
     * Pattern describing a valid square designator (cr).
     */
    static final Pattern ROW_COL = Pattern.compile("^[a-h][1-8]$");
    /**
     * The standard initial configuration for Lines of Action (bottom row
     * first).
     */
    static final Piece[][] INITIAL_PIECES = {
            {EMP, BP, BP, BP, BP, BP, BP, EMP},
            {WP, EMP, EMP, EMP, EMP, EMP, EMP, WP},
            {WP, EMP, EMP, EMP, EMP, EMP, EMP, WP},
            {WP, EMP, EMP, EMP, EMP, EMP, EMP, WP},
            {WP, EMP, EMP, EMP, EMP, EMP, EMP, WP},
            {WP, EMP, EMP, EMP, EMP, EMP, EMP, WP},
            {WP, EMP, EMP, EMP, EMP, EMP, EMP, WP},
            {EMP, BP, BP, BP, BP, BP, BP, EMP}
    };
    /**
     * Current contents of the board.  Square S is at _board[S.index()].
     */
    private final Piece[] _board = new Piece[BOARD_SIZE * BOARD_SIZE];

    /** Weight for heuristic function.
     * 0 - Points for taking the turn
     * 1 - Mobility
     * 2 - concentration
     * 3 - boardscore
     * 4 -  compos
     * 5 -  walled
     * 6 - stronghold
     * 7 - connections
     * 8 - distribution
     * 9 - potential
     * {1, 25, 5000, 5, 1, 2, 8, 2.5, 3, 2};
     * */
    static final double[] WEIGHTS = {1, 20, 50000, 5, 1, 2, 8, 2.5, 3, 0};
    /**
     * List of all unretracted moves on this board, in order.
     */
    private final ArrayList<Move> _moves = new ArrayList<>();
    /**
     * List of the sizes of continguous clusters of pieces, by color.
     */
    private final ArrayList<Integer>
            _whiteRegionSizes = new ArrayList<>(),
            _blackRegionSizes = new ArrayList<>();

    /**
     * List of the continguous clusters of pieces, by color.
     */
    private final ArrayList<HashSet<Square>>
            _whiteRegions = new ArrayList<>(),
            _blackRegions = new ArrayList<>();

    /**
     *  total pieces of a color on board.
     */
    private  int
            _whitepieces = 12,
            _blackpieces = 12;

    /**
     * List of the continguous clusters of pieces, by color.
     */
    private final Hashtable<Integer, Double>
            _whitePartial = new Hashtable<>(),
            _blackPartial = new Hashtable<>();
    /**
     * Current side on move.
     */
    private Piece _turn;

    /**
     * Center of Mass.
     */
    private Square _whitecenterofmass, _blackcenterofmass;

    /**
     * Limit on number of moves before tie is declared.
     */
    private int _moveLimit;
    /**
     * True iff the value of _winner is known to be valid.
     */
    private boolean _winnerKnown;
    /**
     * Cached value of the winner (BP, WP, EMP (for tie), or null (game still
     * in progress).  Use only if _winnerKnown.
     */
    private Piece _winner;
    /**
     * True iff subsets computation is up-to-date.
     */
    private boolean _subsetsInitialized;

    /**
     * A Board whose initial contents are taken from INITIALCONTENTS
     * and in which the player playing TURN is to move. The resulting
     * Board has
     * get(col, row) == INITIALCONTENTS[row][col]
     * Assumes that PLAYER is not null and INITIALCONTENTS is 8x8.
     * <p>
     * CAUTION: The natural written notation for arrays initializers puts
     * the BOTTOM row of INITIALCONTENTS at the top.
     */
    Board(Piece[][] initialContents, Piece turn) {
        initialize(initialContents, turn);
    }

    /**
     * A new board in the standard initial position.
     */
    Board() {
        this(INITIAL_PIECES, BP);
    }

    /**
     * A Board whose initial contents and state are copied from
     * BOARD.
     */
    Board(Board board) {
        this();
        copyFrom(board);
    }

    /**
     * Set my state to CONTENTS with SIDE to move.
     */
    void initialize(Piece[][] contents, Piece side) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                _board[sq(j, i).index()] = contents[i][j];
            }
        }
        _turn = side;
        _moveLimit = DEFAULT_MOVE_LIMIT;
        _subsetsInitialized = false;
        _winnerKnown = false;
        _winner = null;
        _moves.clear();
        computeRegions();
    }

    /**
     * Set me to the initial configuration.
     */
    void clear() {
        initialize(INITIAL_PIECES, BP);
    }

    /**
     * Set my state to a copy of BOARD.
     */
    void copyFrom(Board board) {
        if (board == this) {
            return;
        }
        System.arraycopy(board._board, 0, this._board, 0,
                board._board.length);
        _moveLimit = board._moveLimit;
        _turn = board._turn;
        _winner = board._winner;
        _winnerKnown = board._winnerKnown;
        _subsetsInitialized = board._subsetsInitialized;
        _moves.clear();
        _moves.addAll(board._moves);
        computeRegions();
    }

    /**
     * Return the contents of the square at SQ.
     */
    Piece get(Square sq) {
        return _board[sq.index()];
    }

    /**
     * Return the movelimit of the game.
     */
    int getMovelimit() {
        return _moveLimit;
    }

    /**
     * Set the square at SQ to V and set the side that is to move next
     * to NEXT, if NEXT is not null.
     */
    void set(Square sq, Piece v, Piece next) {
        if (next != null) {
            _turn = next;
        }
        _board[sq.index()] = v;
        _subsetsInitialized = false;
    }

    /**
     * Set the square at SQ to V, without modifying the side that
     * moves next.
     */
    void set(Square sq, Piece v) {
        set(sq, v, null);
    }

    /**
     * Set limit on number of moves by each side that results in a tie to
     * LIMIT, where 2 * LIMIT > movesMade().
     */
    void setMoveLimit(int limit) {
        if (2 * limit <= movesMade()) {
            throw new IllegalArgumentException("move limit too small");
        }
        _moveLimit = limit;
    }

    /**
     * Assuming isLegal(MOVE), make MOVE. This function assumes that
     * MOVE.isCapture() will return false.  If it saves the move for
     * later retraction, makeMove itself uses MOVE.captureMove() to produce
     * the capturing move.
     */
    void makeMove(Move move) {
        assert isLegal(move);
        if (_board[move.getTo().index()] != EMP) {
            move = move.captureMove();
        }
        set(move.getTo(), _turn, _turn.opposite());
        set(move.getFrom(), EMP);
        _moves.add(move);

    }

    /**
     * Retract (unmake) one move, returning to the state immediately before
     * that move.  Requires that movesMade () > 0.
     */
    void retract() {
        assert movesMade() > 0;
        if (_winnerKnown || _winner != null) {
            _winner = null;
            _winnerKnown = false;
        }
        Move last = _moves.remove(_moves.size() - 1);
        set(last.getFrom(), _turn.opposite(), _turn.opposite());
        if (last.isCapture()) {
            set(last.getTo(), _turn.opposite());
        } else {
            set(last.getTo(), EMP);
        }

    }

    /**
     * Return the Piece representing who is next to move.
     */
    Piece turn() {
        return _turn;
    }

    /**
     * Return true iff FROM - TO is a legal move for the player currently on
     * move.
     */
    boolean isLegal(Square from, Square to) {
        if (from.isValidMove(to) && !blocked(from, to)) {
            int pieces = 1;
            for (int step = 1;
                 from.moveDest(from.direction(to), step) != null; step++) {
                if (_board[from.moveDest(from.direction(to), step).index()]
                        != EMP) {
                    pieces++;
                }
            }
            for (int step = 1;
                 from.moveDest((from.direction(to) + 4) % 8, step) != null;
                 step++) {
                if (_board[from.moveDest((from.direction(to) + 4)
                        % 8, step).index()]
                        != EMP) {
                    pieces++;
                }
            }
            return pieces == from.distance(to);
        }
        return false;
    }

    /**
     * Return true iff MOVE is legal for the player currently on move.
     * The isCapture() property is ignored.
     */
    boolean isLegal(Move move) {
        return isLegal(move.getFrom(), move.getTo());
    }

    /**
     * @param turn : Player whoese turn it is
     * Return a sequence of all legal moves from this position.
     */
    List<Move> legalMoves(Piece turn) {
        if (turn == null) {
            turn = turn();
        }
        List<Move> legal = new ArrayList<>();
        for (Square from : ALL_SQUARES) {
            if (_board[from.index()] == turn) {
                for (Square to : ALL_SQUARES) {
                    Move move = Move.mv(from, to);
                    if (from.isValidMove(to) && isLegal(move)) {
                        legal.add(move);
                    }
                }
            }
        }
        return legal;
    }

    /**
     * Return true iff the game is over (either player has all his
     * pieces continguous or there is a tie).
     */
    boolean gameOver() {
        return winner() != null;
    }

    /**
     * Return true iff SIDE's pieces are continguous.
     */
    boolean piecesContiguous(Piece side) {
        return getRegionSizes(side).size() == 1;
    }

    /**
     * Return the winning side, if any.  If the game is not over, result is
     * null.  If the game has ended in a tie, returns EMP.
     */
    Piece winner() {
        if (!_winnerKnown) {
            Boolean white = piecesContiguous(WP);
            Boolean black = piecesContiguous(BP);
            if (white && black) {
                _winner = _turn.opposite();
                _winnerKnown = true;
            } else if (white) {
                _winner = WP;
                _winnerKnown = true;
            } else if (black) {
                _winner = BP;
                _winnerKnown = true;
            } else if (_moveLimit * 2 <= movesMade()) {
                _winner = EMP;
                _winnerKnown = true;
            } else {
                _winner = null;
            }
        }
        return _winner;
    }

    /**
     * Return the total number of moves that have been made (and not
     * retracted).  Each valid call to makeMove with a normal move increases
     * this number by 1.
     */
    int movesMade() {
        return _moves.size();
    }

    @Override
    public boolean equals(Object obj) {
        Board b = (Board) obj;
        return Arrays.deepEquals(_board, b._board) && _turn == b._turn;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(_board) * 2 + _turn.hashCode();
    }

    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("===\n");
        for (int r = BOARD_SIZE - 1; r >= 0; r -= 1) {
            out.format("    ");
            for (int c = 0; c < BOARD_SIZE; c += 1) {
                out.format("%s ", get(sq(c, r)).abbrev());
            }
            out.format("\n");
        }
        out.format("Next move: %s\n===", turn().fullName());
        return out.toString();
    }

    /**
     * print state for players.
     * @return String representation
     */
    public String statePrint() {
        Formatter out = new Formatter();
        out.format("\n");
        for (int r = BOARD_SIZE - 1; r >= 0; r -= 1) {
            out.format("    ");
            for (int c = 0; c < BOARD_SIZE; c += 1) {
                out.format("%s ", get(sq(c, r)).abbrev());
            }
            out.format("\n");
        }
        out.format("Next move: %s\n", turn().fullName());
        out.format("Time left: %d\n",
                (MachinePlayer.TOTALTIME - MachinePlayer.SPENT));
        out.format("Average: %d\n", MachinePlayer.AVG);
        out.format("Number of move: %d\n", movesMade());
        out.format("max Depth: %d\n", MachinePlayer._maxdepth);
        return out.toString();
    }

    /**
     * Return true if a move from FROM to TO is blocked by an opposing
     * piece or by a friendly piece on the target square.
     */
    private boolean blocked(Square from, Square to) {
        if (_board[from.index()] != EMP
                && _board[from.index()] == _board[to.index()]) {
            return true;
        }
        for (int step = 1; step < from.distance(to); step++) {
            if (_board[from.moveDest(from.direction(to), step).index()]
                    == _board[from.index()].opposite()) {
                return true;
            }
        }
        return false;

    }

    /**
     * Return the size of the as-yet unvisited cluster of squares
     * containing P at and adjacent to SQ.  VISITED indicates squares that
     * have already been processed or are in different clusters.  Update
     * VISITED to reflect squares counted.
     * @param region List of Square
     * @param p Pieces
     * @param sq Square
     * @param visited visited boolean array
     * @return return size of region
     */
    private int numContig(Square sq, boolean[][] visited,
                          Piece p, HashSet<Square> region) {
        int size = p == _board[sq.index()] ? 1 : 0;
        if (p == _board[sq.index()]) {
            region.add(sq);
        }
        visited[sq.row()][sq.col()] = true;
        for (Square adjacentSq : sq.adjacent()) {
            if (!visited[adjacentSq.row()][adjacentSq.col()]) {
                visited[adjacentSq.row()][adjacentSq.col()] = true;
                if (p == _board[adjacentSq.index()]) {
                    size += numContig(adjacentSq, visited, p, region);
                }
            }
        }
        return size;
    }

    /**
     * Set the values of _whiteRegionSizes and _blackRegionSizes.
     */
    private void computeRegions() {
        if (_subsetsInitialized) {
            return;
        }
        _whiteRegionSizes.clear();
        _blackRegionSizes.clear();
        _blackRegions.clear();
        _whiteRegions.clear();
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (Square sq : ALL_SQUARES) {
            if (!visited[sq.row()][sq.col()]) {
                HashSet<Square> region = new HashSet<>();
                int cluster = numContig(sq, visited, WP, region);
                if (cluster > 0) {
                    _whiteRegionSizes.add(cluster);
                    _whiteRegions.add(region);
                }
            }
        }
        visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (Square sq : ALL_SQUARES) {
            if (!visited[sq.row()][sq.col()]) {
                HashSet<Square> region = new HashSet<>();
                int cluster = numContig(sq, visited, BP, region);
                if (cluster > 0) {
                    _blackRegionSizes.add(cluster);
                    _blackRegions.add(region);
                }
            }
        }
        _whiteRegionSizes.sort(reverseOrder());
        _blackRegionSizes.sort(reverseOrder());
        _whiteRegions.sort(reverseOrder(new SizeComparator()));
        _blackRegions.sort(reverseOrder(new SizeComparator()));
        _subsetsInitialized = true;
    }

    /**
     * Return the sizes of all the regions in the current union-find
     * structure for side S.
     */
    List<Integer> getRegionSizes(Piece s) {
        computeRegions();
        if (s == WP) {
            return _whiteRegionSizes;
        } else {
            return _blackRegionSizes;
        }
    }

    /**
     * true if player moves between 2 sqs.
     *
     * @return true if moves are repeated.
     **/
    public boolean repeatMove() {
        return _moves.get(_moves.size() - 1).getTo()
                == _moves.get(_moves.size() - 3).getFrom();
    }

    /**
     * Return the how much centrated the pieces are of a particular regionlist.
     *
     * @param regionslist List of List of Pieces.
     * @param player      Player.
     **/
    public double concentration(ArrayList<HashSet<Square>> regionslist,
                              Piece player) {
        int c = 0, r = 0, pieces = 0;
        for (HashSet<Square> list : regionslist) {
            for (Square sq : list) {
                c += sq.col();
                r += sq.row();
                pieces++;
            }
        }
        c = c / pieces;
        r = r / pieces;
        Square centerofmass = sq(c, r);
        if (player == WP) {
            _whitecenterofmass = centerofmass;
            _whitepieces = pieces;
        } else {
            _blackcenterofmass = centerofmass;
            _blackpieces = pieces;
        }
        double distance = 0;
        for (HashSet<Square> list : regionslist) {
            for (Square sq : list) {
                distance += sq.distance(centerofmass);
            }
        }
        int outer = pieces > 9 ? pieces - 9 : 0;
        distance -= pieces - 1 + outer;
        return distance == 0 ? 0 : 1 / distance;
    }

    /**
     * Return how the distribution of pieces in region list.
     *
     * @param regionslist List of List of Pieces.
     **/
    public int distribution(ArrayList<HashSet<Square>> regionslist) {
        int c1 = 0, r1 = 0, c2 = 8, r2 = 8;
        for (HashSet<Square> list : regionslist) {
            for (Square sq : list) {
                c1 = Math.max(sq.col(), c1);
                r1 = Math.max(sq.row(), r1);
                c2 = Math.min(sq.col(), c2);
                r2 = Math.min(sq.row(), r2);
            }
        }
        int r = r1 - r2 + 1;
        int c = c1 - c2 + 1;
        return 64 - (r * c);
    }


    /**
     * Score of pieces of a particular playe on baord.
     * Near center more score, edge negative, edge double negative
     *
     * @param regionslist List of List of Pieces.
     * @return Baordscore based on position of pieces;
     **/
    public double boardscore(ArrayList<HashSet<Square>> regionslist) {
        double score = 0, pieces = 0;
        for (HashSet<Square> list : regionslist) {
            for (Square sq : list) {
                if (sq.isEdge()) {
                    score += -2;
                    if (sq.isCorner()) {
                        score += -6;
                    }
                } else {
                    int distance = Math.min(Math.min(sq.distance(sq(3, 3)),
                            sq.distance(sq(4, 4))),
                            Math.min(sq.distance(sq(4, 3)),
                                    sq.distance(sq(3, 4))));
                    score += distance == 0 ? 5 : distance == 1 ? 3 : 1;
                }
                pieces++;
            }
        }
        return (score * 10) / pieces;
    }


    /**
     * Mobility Score.
     *
     * @param player Player.
     * @return Score of mobility with waited moves.
     **/
    public double mobility(Piece player) {
        List<Move> legal = legalMoves(player);
        double mobility = 0;
        for (Move move : legal) {
            double start = 1;
            if (_board[move.getTo().index()] == player.opposite()) {
                start *= 2;
            }
            if (move.getTo().isEdge()) {
                start *= 0.5;
                if (move.getFrom().isEdge()) {
                    start *= 0.5;
                }
            }
            mobility += start;
        }
        int piece = _board[legal.get(0).getFrom().index()] == WP ? _whitepieces
                : _blackpieces;
        return mobility;
    }

    /**
     * Position of center of mass of player score.
     *
     * @param player Player
     * @return Score of positon of com
     */
    public int compos(Piece player) {
        Square com;
        if (player == WP) {
            com = _whitecenterofmass;
        } else {
            com = _blackcenterofmass;
        }
        if (com.isEdge()) {
            return 10;
        }
        return Math.min(Math.min(com.distance(sq(3, 3)),
                com.distance(sq(4, 4))),
                Math.min(com.distance(sq(4, 3)),
                        com.distance(sq(3, 4))));
    }

    /**
     * Return the avg distnace of every square in other clusters to the
     * largest cluster. It assumes there are atlest 2 clusters, cause if
     * there is 1, someone wins and is already cut off at find move function
     *
     * @param regionslist List of List of Pieces.
     **/
    public double potential(ArrayList<HashSet<Square>> regionslist) {
        double total = 0;
        HashSet<Square> approch = new HashSet<>();
        for (Square from : regionslist.get(0)) {
            for (Square adjacent : from.adjacent()) {
                if (_board[adjacent.index()] != _board[from.index()]) {
                    approch.add(adjacent);
                }
            }
        }
        for (Square from : approch) {
            for (int i = 1; i < regionslist.size(); i++) {
                double avg = 0;
                for (Square to : regionslist.get(i)) {
                    avg += from.distance(to);
                    if (from.isValidMove(to) && blocked(from, to)) {
                        avg += 5;
                    }
                }
                total += avg / (approch.size() * regionslist.get(i).size());
            }
        }
        return total;
    }


    /**
     * set viseted true.
     *
     * @param visited boolean array
     * @param s1  square1
     * @param s2  square2
     */
    private void setvisited(boolean[][] visited, Square s1, Square s2) {
        visited[s1.index()][s2.index()] = visited[s1.index()][s2.index()]
                = true;
    }

    /**
     * Score of pieces of a particular player which form stronghold position
     * on baord.
     *
     * @param regionslist List of List of Pieces.
     * @param player the one we are looking strongholds for.
     * @return score based on no of stronghold;
     **/
    public int stronghold(ArrayList<HashSet<Square>> regionslist,
                           Piece player) {
        int score = 0;
        Square com = player == WP
                ? _whitecenterofmass : _blackcenterofmass;
        boolean[][] visited
                = new boolean[BOARD_SIZE * BOARD_SIZE][BOARD_SIZE * BOARD_SIZE];
        for (HashSet<Square> list : regionslist) {
            if (list.size() > 2) {
                for (Square sq : list) {
                    if (sq.distance(com) <= 2) {
                        for (int i = 0; i < 8; i += 2) {
                            Square s1 = sq.moveDest(i, 1);
                            Square s2 = sq.moveDest(i + 1, 1);
                            Square s3 = sq.moveDest(i + 2, 1);
                            if (s1 == null || s2 == null || s3 == null
                                    || visited[sq.index()][s1.index()]
                                    || visited[sq.index()][s2.index()]
                                    || visited[sq.index()][s3.index()]
                                    || visited[s1.index()][sq.index()]
                                    || visited[s2.index()][sq.index()]
                                    || visited[s3.index()][sq.index()]) {
                                continue;
                            }
                            if (list.contains(s1) && list.contains(s3)
                                    && list.contains(s2)) {
                                setvisited(visited, s1, sq);
                                setvisited(visited, s2, sq);
                                setvisited(visited, s3, sq);
                                score += 5;
                            } else if (list.contains(s1) && list.contains(s2)) {
                                setvisited(visited, s1, sq);
                                setvisited(visited, s2, sq);
                                score += 3;
                            } else if (list.contains(s1) && list.contains(s3)) {
                                setvisited(visited, s1, sq);
                                setvisited(visited, s3, sq);
                                score += 3;
                            } else if (list.contains(s2) && list.contains(s3)) {
                                setvisited(visited, s3, sq);
                                setvisited(visited, s2, sq);
                                score += 3;
                            }
                        }
                    }
                }
            }
        }
        return score;
    }

    /**
     * Score of pieces of a particular player for avergave connections of each
     * pieces.
     *
     * @param regionslist List of List of Pieces.
     * @return score based on no of stronghold;
     **/
    public double connections(ArrayList<HashSet<Square>> regionslist) {
        double score = 0, pieces = 0;
        for (HashSet<Square> list : regionslist) {
            if (list.size() > 2) {
                for (Square from : list) {
                    for (Square to : list) {
                        if (from.distance(to) == 1) {
                            score++;
                        }
                    }
                }
            } else if (list.size() == 2) {
                score += list.size();
            }
            pieces += list.size();
        }
        return score / pieces;
    }

    /**
     * Score of pieces of a particular player which are walled on the
     * on baord.
     *
     * @param regionslist List of List of Pieces.
     * @return score based on no of stronghold;
     **/
    public int walled(ArrayList<HashSet<Square>> regionslist) {
        int score = 0;
        for (HashSet<Square> list : regionslist) {
            for (Square sq : list) {
                if (sq.isCorner()) {
                    for (Square adj : sq.adjacent()) {
                        if (_board[adj.index()]
                                == _board[sq.index()].opposite()) {
                            if (adj.row() != sq.row()
                                    && adj.col() != sq.col()) {
                                score += 4;
                            } else {
                                score += 1;
                            }
                        }
                    }
                } else if (sq.isEdge()) {
                    for (Square adj : sq.adjacent()) {
                        int front = 0, corner = 0, number = 0;
                        if (_board[adj.index()]
                                == _board[sq.index()].opposite()) {
                            if (adj.row() != sq.row()
                                    && adj.col() != sq.col()) {
                                corner++;
                            } else if (!adj.isEdge()) {
                                front++;
                            } else {
                                number++;
                            }
                        }
                        if (corner + front + number > 1) {
                            score += corner + front;
                            score += corner + number == 4 ? 6 : corner + number;
                        }
                    }
                }
            }
        }
        return score;
    }

    /**
     * board heuristic.
     * value += WEIGHTS[9] * (b * potential(_blackRegions)
     *                 - a * walled(_whiteRegions));
     * @return value.
     **/
    public int heuristicValue() {
        computeRegions();
        double value = WEIGHTS[0];
        int a = 1, b = 1;
        if (turn() == BP) {
            value *= -1;
            a = 1;
            b = 1;
        }
        if (_whitePartial.containsKey(_whiteRegions.hashCode())) {
            value += a * _whitePartial.get(_whiteRegions.hashCode());
        } else {
            _whitePartial.clear();
            double whitescore =  WEIGHTS[2]
                    * (concentration(_whiteRegions, WP));
            whitescore += WEIGHTS[3] * (boardscore(_whiteRegions));
            whitescore += WEIGHTS[4] * (compos(WP));
            whitescore += WEIGHTS[6] * (stronghold(_whiteRegions, WP));
            whitescore += WEIGHTS[7] * (connections(_whiteRegions));
            whitescore += WEIGHTS[8] * (distribution(_whiteRegions));
            value += a * whitescore;
            _whitePartial.put(_whiteRegions.hashCode(), whitescore);
        }
        if (_blackPartial.containsKey(_blackRegions.hashCode())) {
            value -= b * _blackPartial.get(_blackRegions.hashCode());
        } else {
            _blackPartial.clear();
            double blackscore = WEIGHTS[2] * (concentration(_blackRegions, BP));
            blackscore += WEIGHTS[3] * (boardscore(_blackRegions));
            blackscore += WEIGHTS[4] * (compos(BP));
            blackscore += WEIGHTS[6] * (stronghold(_blackRegions, BP));
            blackscore += WEIGHTS[7] * (connections(_blackRegions));
            blackscore += WEIGHTS[8] * (distribution(_blackRegions));
            value -= b * blackscore;
            _blackPartial.put(_blackRegions.hashCode(), blackscore);
        }
        value += WEIGHTS[1] * (a * mobility(WP) - b * mobility(BP));
        value += WEIGHTS[5] * (walled(_blackRegions) - walled(_whiteRegions));

        return (int) value;
    }

    /** Comparator to sort ArrayList of ArrayList of pieces on basis of size. */
    static class SizeComparator implements Comparator<Set<Square>> {
        @Override
        public int compare(Set<Square> o1, Set<Square> o2) {
            return Integer.compare(o1.size(), o2.size());
        }
    }
}
