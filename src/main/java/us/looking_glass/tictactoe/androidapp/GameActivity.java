/**
 Copyright 2013 Andrew Mahone

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package us.looking_glass.tictactoe.androidapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.*;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.*;
import android.widget.*;
import us.looking_glass.tictactoe.Board;
import us.looking_glass.tictactoe.Game;
import us.looking_glass.tictactoe.Player;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.*;

public class GameActivity extends Activity implements GameView.BoardTouchListener, AdapterView.OnItemSelectedListener {
    private TicTacToeApp app = null;

    private final static int WAIT_IGNORE = 0;
    private final static int WAIT_MOVE = 1;
    private final static int WAIT_TAP = 2;
    final static boolean debug = false;
    private final static String TAG="TicTacToe:GameActivity";
    private LinearLayout topLayout;
    private GameView gameView;
    private TextView tallyView;

    private Spinner[] playerSelect = new Spinner[2];
    private static final String[] spinnerQueryCols = new String[]{AppDB.KEY_ID, AppDB.KEY_NAME};
    private static final String[] spinnerAdapterCols = new String[]{AppDB.KEY_NAME};
    private static final int[] spinnerAdapterRowViews = new int[]{android.R.id.text1};
    private int awaitingInput = 0;
    GameBGThread bgThread = null;
    volatile MessageHandler bgHandler = null;
    volatile MessageHandler handler = null;
    CyclicBarrier saveBarrier = new CyclicBarrier(2);
    private CharSequence aboutText;
    private String aboutVersionText = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        app = TicTacToeApp.app();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        topLayout = (LinearLayout) findViewById(R.id.topLayout);
        gameView = (GameView) findViewById(R.id.gameView);
        tallyView = (TextView) findViewById(R.id.tallyView);
        handler = new UIHandler();
        playerSelect[0] = (Spinner) findViewById(R.id.P1Select);
        playerSelect[1] = (Spinner) findViewById(R.id.P2Select);
        setOrientation(getResources().getConfiguration().orientation);
        bgThread = new GameBGThread("TicTacToeBG");
        bgThread.start();
        String aboutTextString = getString(R.string.about_text);
        aboutText = Html.fromHtml(aboutTextString);
        try {
            aboutVersionText = "v" + getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to retrieve package version", e);
            aboutVersionText = "vUnknown";
        }
        Logd("GameActivity onCreate");
    }

    private void setOrientation(int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT)
            topLayout.setOrientation(LinearLayout.VERTICAL);
        else if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            topLayout.setOrientation(LinearLayout.HORIZONTAL);
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig)
    {
        Logd("onConfigurationChanged: %s %d %d %d %d", newConfig, newConfig.orientation, newConfig.getLayoutDirection(), Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT);
        super.onConfigurationChanged(newConfig);
        setOrientation(newConfig.orientation);
    }

    @Override
    public void onPause() {
        super.onPause();
        Logd("onPause state save start");
        long time;
        Exception failure = null;
        if (debug)
            time = -System.currentTimeMillis();
        bgHandler.sendEmptyMessage(GameBGThread.GameHandler.SAVE_STATE);
        try {
            saveBarrier.await();
        } catch (InterruptedException e) {
            failure = e;
        } catch (BrokenBarrierException e) {
            failure = e;
        }
        if (failure != null)
            Log.e(TAG, "Error during onPause", failure);
        if (debug) {
            time += System.currentTimeMillis();
            Logd("onPause state save completed in %dms", time);
        }
    }

    @Override
    public void onClick(GameView source, int x, int y) {
        Logd("Board touch: %d %d", x, y);
        switch (awaitingInput) {
            case WAIT_MOVE:
                bgHandler.sendMessage(GameBGThread.GameHandler.PLAY_MOVE, x, y, null);
                awaitingInput = WAIT_IGNORE;
                break;
            case WAIT_TAP:
                bgHandler.sendEmptyMessage(GameBGThread.GameHandler.PLAY_TAP);
                awaitingInput = WAIT_IGNORE;
                break;
        }
    }

    @Override
    public void onClick(GameView source, float x, float y) {
        if (awaitingInput == WAIT_TAP) {
            bgHandler.sendEmptyMessage(GameBGThread.GameHandler.PLAY_TAP);
            awaitingInput = WAIT_IGNORE;
        }

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Logd("onItemSelected");
        for (int i = 0; i < 2; i++)
            if (parent == playerSelect[i])
                bgHandler.sendMessage(GameBGThread.GameHandler.SET_PLAYER, i, (int) id, null);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Logd("onNothingSelected: parent %s", parent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.game_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_about:
                Logv("open about");
                openAboutPopup();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void openAboutPopup () {
        final Dialog aboutPopup = new Dialog(this);
        View aboutWindowView = getLayoutInflater().inflate(R.layout.aboutpopup, null);
        TextView aboutTextView = (TextView) aboutWindowView.findViewById(R.id.aboutTextView);
        final GameView aboutGameView = (GameView) aboutWindowView.findViewById(R.id.aboutIcon);
        aboutGameView.setContents(0x10a01);
        aboutTextView.setText(aboutText);
        aboutTextView.setMovementMethod(LinkMovementMethod.getInstance());
        TextView aboutVersionTextView = (TextView) aboutWindowView.findViewById(R.id.aboutVersion);
        aboutVersionTextView.setText(aboutVersionText);
        aboutPopup.requestWindowFeature(Window.FEATURE_NO_TITLE);
        aboutPopup.setCancelable(true);
        aboutPopup.setCanceledOnTouchOutside(true);
        aboutPopup.setContentView(aboutWindowView);
        aboutGameView.setBoardTouchListener(new GameView.BoardTouchListener() {
            int touchCount = 0;

            private void doTouch() {
                if (++touchCount < 5) return;
                aboutGameView.setBoardTouchListener(null);
                bgHandler.post(new HowAboutANiceGameOfChess(aboutPopup, aboutGameView, GameActivity.this));
            }

            @Override
            public void onClick(GameView source, int x, int y) {
                doTouch();
            }

            @Override
            public void onClick(GameView source, float x, float y) {
                doTouch();
            }
        });
        aboutPopup.show();
        aboutPopup.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    class GameBGThread extends HandlerThread {
        class GameHandler extends MessageHandler {
            final static int PLAY_MOVE = 0;
            final static int PLAY_TAP = 1;
            final static int SET_PLAYER = 2;
            final static int SAVE_STATE = 3;
            final static int SET_SEED = 4;

            public GameHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                String tallyUpdate = null;
                switch (msg.what) {
                    case PLAY_MOVE:
                        Logv("PLAY_MOVE: %d, %d", msg.arg1, msg.arg2);
                        int x = msg.arg1;
                        int y = msg.arg2;
                        game.play(x, y, game.getCurrentPlayer());
                        int nextState = WAIT_TAP;
                        if (game.status() == Game.PLAYING && game.getPlayer() != null)
                            game.run(1);
                        if (game.status() != Game.PLAYING) {
                            savePlayers();
                            lastResult = game.status();
                            tally[lastResult == Game.P1_WIN ? 0 : lastResult == Game.P2_WIN ? 1 : 2]++;
                            tallyUpdate = formatResults();
                        } else if (game.getPlayer() == null)
                            nextState = WAIT_MOVE;
                        sendUpdate(tallyUpdate);
                        handler.sendMessage(UIHandler.WAIT_INPUT, nextState, 0, null);
                        break;
                    case PLAY_TAP:
                        Logv("PLAY_TAP");
                        if (game.status() == Game.PLAYING) {
                            game.run(1);
                            if (game.status() != Game.PLAYING) {
                                savePlayers();
                                lastResult = game.status();
                                tally[lastResult == Game.P1_WIN ? 0 : lastResult == Game.P2_WIN ? 1 : 2]++;
                                tallyUpdate = formatResults();
                            }
                            sendUpdate(tallyUpdate);
                            sendSetInput();
                        } else newGame();
                        break;
                    case SET_PLAYER:
                        int which = msg.arg1;
                        int index = msg.arg2;
                        Logv("SET_PLAYER: P%d->#%d%s", which+1, index, selectedPlayers[which] == index ? " IGNORED": "");
                        if (selectedPlayers[which] == index)
                            break;
                        Logd("change player %d to %d", which, index);
                        storeGame();
                        players[which] = TicTacToeApp.app().getPlayer(index);
                        selectedPlayers[which] = index;
                        restoreGame();
                        break;
                    case SAVE_STATE:
                        app.db.beginTransaction();
                        try {
                            app.putState("selectedPlayers", selectedPlayers);
                            storeGame();
                            app.db.setTransactionSuccessful();
                        } finally {
                            app.db.endTransaction();
                        }
                        try {
                            saveBarrier.await();
                        } catch (Throwable t) {
                            Log.e(TAG, "SAVE_STATE barrier failure", t);
                        }
                        break;
                    case SET_SEED:
                        Player.prng.setSeed((int[]) msg.obj);
                        int[] seed = new int[32];
                        for (int i = 0; i < 32; i++)
                            seed[i] = Player.prng.next(32);
                        app.putState("rngSeed", seed);
                        break;
                }
            }
        }

        long tally[] = null;
        Game game = null;
        long[] selectedPlayers = null;
        Player[] players = new Player[2];
        byte lastResult = -2;

        public GameBGThread(String name) {
            super(name);
        }

        public GameBGThread(String name, int priority) {
            super(name, priority);
        }

        private void storeGame () {
            byte[] gameData = app.gameSerializer.toBytes(game);
            byte[] tallyData = app.serializer.toBytes(tally);
            ContentValues values = new ContentValues();
            values.put(AppDB.KEY_P1ID, selectedPlayers[0]);
            values.put(AppDB.KEY_P2ID, selectedPlayers[1]);
            values.put(AppDB.KEY_GAME, gameData);
            values.put(AppDB.KEY_TALLY, tallyData);
            values.put(AppDB.KEY_RESULT, lastResult);
            long result = app.db.insert(AppDB.GAME_TABLE_NAME, null, values);
            Logv("storeGame result: %d", result);
        }

        private void restoreGame () {
            String whereString = AppDB.KEY_P1ID + "=" + Long.toString(selectedPlayers[0]) + " AND " + AppDB.KEY_P2ID + "=" + Long.toString(selectedPlayers[1]);
            Cursor result = app.db.query(true, AppDB.GAME_TABLE_NAME, AppDB.TALLY_GAME_RESULT_COLS, whereString, null, null, null, null, null);
            Game storedGame = null;
            long[] storedTally = null;
            lastResult = -2;
            if (result.getCount() == 1 && result.moveToFirst()) {
                 try {
                    storedGame = (Game) app.gameSerializer.fromBytes(result.getBlob(result.getColumnIndexOrThrow(AppDB.KEY_GAME)));
                    storedTally = (long[]) app.serializer.fromBytes(result.getBlob(result.getColumnIndexOrThrow(AppDB.KEY_TALLY)));
                    lastResult = (byte) result.getInt(result.getColumnIndexOrThrow(AppDB.KEY_RESULT));
                } catch (ClassCastException e) {
                    Log.e(TAG, "Error restoring game", e);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error restoring game", e);
                }
            }
            Logd("restoreGame: %s %s", storedGame, storedTally == null ? "null" : Arrays.toString(storedTally));
            if (storedGame != null) {
                game = storedGame;
            } else {
                newGame();
            }
            if (storedTally != null && storedTally.length == 6) {
                tally = storedTally;
            } else {
                tally = new long[6];
            }
            sendUpdate(formatResults());
            sendSetInput();
        }

        public Game newGame() {
            Logv("newGame()");
            game = new Game(players[0], players[1]);
            if (game.getPlayer(2) == null)
                game.run(1);
            sendUpdate(formatResults());
            sendSetInput();
            return game;
        }

        void savePlayers() {
            app.db.beginTransaction();
            try {
                savePlayer(0);
                if (players[0] != players[1])
                    savePlayer(1);
                app.db.setTransactionSuccessful();
            } finally {
                app.db.endTransaction();
            }
        }

        private void savePlayer(int select) {
            if (players[select] == null || !players[select].saveable()) {
                Logv("savePlayer(%d): no state to save", select);
                return;
            }
            Player player = players[select];
            byte[] state = app.serializer.toBytes(player);
            long id = app.getPlayerID(player);
            ContentValues update = new ContentValues();
            update.put(AppDB.KEY_STATE, state);
            Logd("savePlayer(%d): %s", select, update);
            String selector = String.format("_id=%d", id);
            app.db.update(AppDB.BRAINS_TABLE_NAME, update, selector, null);
        }

        private void sendUpdate(String tallyUpdate) {
            handler.sendMessage(UIHandler.UPDATE_DISPLAY, game.board().getContents(), 0, tallyUpdate);
        }

        private void sendSetInput() {
            int nextState = WAIT_TAP;
            if (game.status() == Game.PLAYING && game.getPlayer() == null)
                nextState = WAIT_MOVE;
            handler.sendMessage(UIHandler.WAIT_INPUT, nextState, 0, null);
        }


        private String formatResults() {
            return String.format("Result: %s\nP1 win: %d\nP2 win: %d\nDraw:   %d", lastResult == Game.DRAW ? "Draw" : lastResult == Game.P1_WIN ? "P1 Win" : lastResult == Game.P2_WIN ? "P2 Win" : "", tally[0], tally[1], tally[2]);
        }


        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            bgHandler = bgThread.new GameHandler(bgThread.getLooper());
            Cursor playerSelectCursor = app.db.query(true, AppDB.BRAINS_TABLE_NAME, spinnerQueryCols, null, null, null, null, AppDB.KEY_ID, null);
            SimpleCursorAdapter playerSelectAdapter = new SimpleCursorAdapter(GameActivity.this, android.R.layout.simple_spinner_item, playerSelectCursor, spinnerAdapterCols, spinnerAdapterRowViews, 0);
            playerSelectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            tally = app.getObject("tally");
            if (tally == null)
                tally = new long[3];
            selectedPlayers = app.getObject("selectedPlayers");
            if (selectedPlayers == null)
                selectedPlayers = new long[2];
            Logd("selectedPlayers: %s", Arrays.toString(selectedPlayers));
            final int[] selected = new int[2];
            for (int i = 0; i < 2; i++) {
                long selectedID = playerSelectAdapter.getItemId(0);
                for (int pos = 0; pos < playerSelectAdapter.getCount(); pos++) {
                    if (playerSelectAdapter.getItemId(pos) == selectedPlayers[i]) {
                        selected[i] = pos;
                        selectedID = selectedPlayers[i];
                        break;
                    }
                }
                Logd("set player %d to index %d, id %d", i, selected[i], selectedID);
                selectedPlayers[i] = selectedID;
                players[i] = app.getPlayer(selectedID);
            }


            int[] rngSeed = app.getObject("rngSeed");
            if (rngSeed == null) {
                Logd("queueing entropy collection");
                // This could potentially block indefinitely, so it shouldn't run on the UI or game thread.
                // Start as a bare thread because it's the only operation we're performing this way.
                new Thread() {

                    @Override
                    public void run() {
                        SecureRandom r;
                        try {
                            r = SecureRandom.getInstance("SHA1RNG");
                        } catch (NoSuchAlgorithmException e) {
                            r = new SecureRandom();
                        }
                        final int[] seed = new int[32];
                        for (int i = 0; i < seed.length; i++) {
                            seed[i] = r.nextInt();
                        }
                        Logd("Got PRNG seed from SecureRandom %s %s", r.getAlgorithm(), r.getProvider().getInfo());
                        bgHandler.sendMessage(GameBGThread.GameHandler.SET_SEED, 0, 0, seed);
                    }
                }.start();

            }
            else {
                Logd("seeding PRNG with saved seed");
                Player.prng.setSeed(rngSeed);
            }
            restoreGame();
            handler.sendMessage(UIHandler.INIT_COMPLETE, selected[0], selected[1], playerSelectAdapter);
        }
    }

    class UIHandler extends MessageHandler {
        final static int UPDATE_DISPLAY=0;
        final static int WAIT_INPUT = 1;
        final static int INIT_COMPLETE = 2;

        public UIHandler() {
            super();
        }

        public UIHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_DISPLAY:
                    Logv("UPDATE_DISPLAY: %s %s", Board.toString(msg.arg1), msg.obj);
                    gameView.setContents(msg.arg1);
                    if (msg.obj != null) {
                        tallyView.setText((String)msg.obj);
                        tallyView.invalidate();
                    }
                    gameView.invalidate();
                    break;
                case WAIT_INPUT:
                    Logv("WAIT_INPUT: %d", msg.arg1);
                    awaitingInput = msg.arg1;
                    break;
                case INIT_COMPLETE:
                    Logv("INIT_COMPLETE: %d %d %s", msg.arg1, msg.arg2, msg.obj);
                    playerSelect[0].setAdapter((SpinnerAdapter) msg.obj);
                    playerSelect[1].setAdapter((SpinnerAdapter) msg.obj);
                    gameView.setBoardTouchListener(GameActivity.this);
                    playerSelect[0].setOnItemSelectedListener(GameActivity.this);
                    playerSelect[1].setOnItemSelectedListener(GameActivity.this);
                    playerSelect[0].setSelection(msg.arg1);
                    playerSelect[1].setSelection(msg.arg2);
                    break;


            }
            super.handleMessage(msg);    //To change body of overridden methods use File | Settings | File Templates.
        }

    }

    private static void Logd(String text, Object... args) {
        if (debug) {
            if (args != null && args.length > 0)
                text = String.format(text, args);
            Log.d(TAG, text);
        }
    }

    private static void Logv(String text, Object... args) {
        if (debug) {
            if (args != null && args.length > 0)
                text = String.format(text, args);
            Log.v(TAG, text);
        }
    }
}
