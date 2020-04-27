/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import ucb.gui2.LayoutSpec;
import ucb.gui2.TopLevel;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.concurrent.ArrayBlockingQueue;


/**
 * The GUI controller for a LOA board and buttons.
 *
 * @author Amogh
 */
class GUI extends TopLevel implements View, Reporter {

    /**
     * Minimum size of board in pixels.
     */
    private static final int MIN_SIZE = 500;

    /**
     * WIDITH of the widgets.
     */
    private static final double WIDTH = 1.5;

    /**
     * Size of pane used to contain help text.
     */
    static final Dimension TEXT_BOX_SIZE = new Dimension(500, 700);

    /**
     * Resource name of "About" message.
     */
    static final String ABOUT_TEXT = "loa/About.html";

    /**
     * Resource name of Loa help text.
     */
    static final String HELP_TEXT = "loa/Help.html";

    /**
     * A new window with given TITLE providing a view of a Loa board.
     */
    GUI(String title) {
        super(title, true);
        addMenuButton("Game->New", this::newGame);
        addMenuButton("Game->Undo", this::undo);
        addMenuCheckBox("Game->Set-up Mode",
                false, this::setupMode);
        addMenuButton("Game->Quit", this::quit);
        addMenuButton("Settings->Seed", this::seed);
        addMenuRadioButton("Settings->Manual Black", "Black",
                true, this::blackmanual);
        addMenuRadioButton("Settings->Auto Black", "Black",
                false, this::blackauto);
        addMenuRadioButton("Settings->Manual White", "White",
                false, this::whitemanual);
        addMenuRadioButton("Settings->Auto White", "White",
                true, this::whiteauto);
        addMenuButton("Settings->limit", this::limit);
        addMenuButton("Help->About", this::about);
        addMenuButton("Help->Loa", this::loa);
        _setmode = false;
        _widget = new BoardWidget(_pendingCommands);
        add(_widget,
                new LayoutSpec("y", 1,
                        "height", 1,
                        "width", 2 * WIDTH));
        addLabel("To move: White", "CurrentTurn",
                new LayoutSpec("x", 0, "y", 0,
                        "height", 3,
                        "width", WIDTH));
        addLabel("Move: #1", "Moves",
                new LayoutSpec("y", 0,
                        "height", 3,
                        "width", WIDTH));
        addLabel("White: Auto", "PlayerWhite",
                new LayoutSpec("x", 0, "y", 2,
                        "height", 1,
                        "width", WIDTH));
        addLabel("Black: Manual", "PlayerBlack",
                new LayoutSpec("y", 2,
                        "height", 1,
                        "width", WIDTH));
    }

    /**
     * Response to "Quit" button click.
     */
    private void quit(String dummy) {
        _pendingCommands.offer("quit");
    }

    /**
     * Response to "New Game" button click.
     */
    private void newGame(String dummy) {
        _pendingCommands.offer("new");
    }

    /**
     * Response to "Undo" button click.
     */
    private void undo(String dummy) {
        _pendingCommands.offer("undo");
    }

    /**
     * Response to "Set up" button click.
     */
    private void setupMode(String dummy) {
        _setmode = !_setmode;
    }

    /**
     * Response to "About" button click.
     */
    private void about(String dummy) {
        displayText("Help", HELP_TEXT);
    }

    /**
     * Response to "Loa" button click.
     */
    private void loa(String dummy) {
        displayText("About", ABOUT_TEXT);
    }

    /**
     * Response to "Black auto" button click.
     */
    private void blackauto(String dummy) {
        _pendingCommands.offer("auto black");
    }

    /**
     * Response to "Black manual" button click.
     */
    private void blackmanual(String dummy) {
        _pendingCommands.offer("manual black");

    }

    /**
     * Response to "White auto" button click.
     */
    private void whiteauto(String dummy) {
        _pendingCommands.offer("auto white");
    }

    /**
     * Response to "White manual" button click.
     */
    private void whitemanual(String dummy) {
        _pendingCommands.offer("manual white");
    }

    /**
     * Response to "Seed" button click.
     */
    private void seed(String dummy) {
        String seed = getTextInput("Enter new random seed.", "New Seed",
                "information", "");
        _pendingCommands.offer("seed " + seed);
    }

    /**
     * Response to "Limit" button click.
     */
    private void limit(String dummy) {
        String limit = getTextInput("LIMIT", "limit",
                "information", "");
        _pendingCommands.offer("limit " + limit);
    }

    /**
     * Return the next command from our widget, waiting for it as necessary.
     * The BoardWidget uses _pendingCommands to queue up moves that it
     * receives.  Thie class uses _pendingCommands to queue up commands that
     * are generated by clicking on menu items.
     */
    String readCommand() {
        try {
            _widget.setMoveCollection(true);
            String cmnd = _pendingCommands.take();
            _widget.setMoveCollection(false);
            return cmnd;
        } catch (InterruptedException excp) {
            throw new Error("unexpected interrupt");
        }
    }

    @Override
    public void update(Game controller) {
        Board board = controller.getBoard();
        _widget.update(board);
        if (board.winner() != null) {
            setLabel("CurrentTurn",
                    String.format("Winner: %s",
                            board.winner().fullName()));
        } else {
            setLabel("CurrentTurn",
                    String.format("To move: %s", board.turn().fullName()));
        }

        if (board.winner() == null) {
            int moves = (board.movesMade() / 2) + 1;
            setLabel("Moves", String.format("Move: #%d", moves));
        } else {
            setLabel("Moves", "Game Over");
        }

        boolean manualWhite = controller.manualWhite(),
                manualBlack = controller.manualBlack();
        if (manualWhite) {
            setLabel("PlayerWhite",
                    "White: Manual");
        } else {
            setLabel("PlayerWhite",
                    "White: Auto");
        }
        if (manualBlack) {
            setLabel("PlayerBlack",
                    "Black: Manual");
        } else {
            setLabel("PlayerBlack",
                    "Black: Auto");
        }
    }

    /**
     * Display text in resource named TEXTRESOURCE in a new window titled
     * TITLE.
     */
    private void displayText(String title, String textResource) {
        /* Implementation note: It would have been more convenient to avoid
         * having to read the resource and simply use dispPane.setPage on the
         * resource's URL.  However, we wanted to use this application with
         * a nonstandard ClassLoader, and arranging for straight Java to
         * understand non-standard URLS that access such a ClassLoader turns
         * out to be a bit more trouble than it's worth. */
        JFrame frame = new JFrame(title);
        JEditorPane dispPane = new JEditorPane();
        dispPane.setEditable(false);
        dispPane.setContentType("text/html");
        InputStream resource =
                GUI.class.getClassLoader().getResourceAsStream(textResource);
        StringWriter text = new StringWriter();
        try {
            while (true) {
                int c = resource.read();
                if (c < 0) {
                    dispPane.setText(text.toString());
                    break;
                }
                text.write(c);
            }
        } catch (IOException e) {
            return;
        }
        JScrollPane scroller = new JScrollPane(dispPane);
        scroller.setVerticalScrollBarPolicy(scroller.VERTICAL_SCROLLBAR_ALWAYS);
        scroller.setPreferredSize(TEXT_BOX_SIZE);
        frame.add(scroller);
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    public void reportError(String fmt, Object... args) {
        showMessage(String.format(fmt, args), "Loa Error", "error");
    }

    @Override
    public void reportNote(String fmt, Object... args) {
        showMessage(String.format(fmt, args), "Loa Message", "information");
    }

    @Override
    public void reportMove(Move unused) {
    }

    /**
     * The board widget.
     */
    private BoardWidget _widget;

    /**
     * set mode active.
     */
    private boolean _setmode;

    /**
     * Queue of pending commands resulting from menu clicks and moves on the
     * board.  We use a blocking queue because the responses to clicks
     * on the board and on menus happen in parallel to the methods that
     * call readCommand, which therefore needs to wait for clicks to happen.
     */
    private ArrayBlockingQueue<String> _pendingCommands =
            new ArrayBlockingQueue<>(5);

}
