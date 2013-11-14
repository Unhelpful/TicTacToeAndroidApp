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
import android.content.ContentValues;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.Layout;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.*;
import android.widget.*;
import us.looking_glass.tictactoe.Game;
import us.looking_glass.tictactoe.Player;
import us.looking_glass.util.Util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class GameActivity extends ActionBarActivity implements GameView.BoardTouchListener, AdapterView.OnItemSelectedListener {
    long tally[] = null;
    Game game = null;
    long[] selectedPlayers = null;
    Player[] players = new Player[2];
    byte lastResult = -2;
    private TicTacToeApp app = null;


    private LinearLayout topLayout;
    private GameView gameView;
    private TextView tallyView;
    private SimpleCursorAdapter playerSelectAdapter = null;
    private View aboutWindowView;

    private Spinner[] playerSelect = new Spinner[2];
    private static final String[] spinnerQueryCols = new String[]{AppDB.KEY_ID, AppDB.KEY_NAME};
    private static final String[] spinnerAdapterCols = new String[]{AppDB.KEY_NAME};
    private static final int[] spinnerAdapterRowViews = new int[]{android.R.id.text1};
    HandlerThread bgThread = null;
    Handler bgHandler = null;
    Handler handler = null;
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
        handler = new Handler();
        playerSelect[0] = (Spinner) findViewById(R.id.P1Select);
        playerSelect[1] = (Spinner) findViewById(R.id.P2Select);
        setOrientation(getResources().getConfiguration().orientation);
        final Runnable entropyRunnable = new Runnable() {
            public void run() {
                SecureRandom r = null;
                try {
                    r = SecureRandom.getInstance("SHA1RNG");
                } catch (NoSuchAlgorithmException e) {
                    r = new SecureRandom();
                }
                final int[] seed = new int[32];
                for (int i = 0; i < seed.length; i++) {
                    seed[i] = r.nextInt();
                }
                Log.d(TicTacToeApp.TAG, String.format("Got PRNG seed from SecureRandom %s %s", r.getAlgorithm(), r.getProvider().getInfo()));
                Player.prng.setSeed(seed);
                for (int i = 0; i < seed.length; i++) {
                    seed[i] = Player.prng.nextInt();
                }
                app.db.beginTransaction();
                try {
                    app.putState("rngSeed", seed);
                    app.db.setTransactionSuccessful();
                } finally {
                    app.db.endTransaction();
                }
            }
        };
        final Runnable initRunnable = new Runnable() {
            public void run() {
                Cursor playerSelectCursor = app.db.query(true, AppDB.BRAINS_TABLE_NAME, spinnerQueryCols, null, null, null, null, AppDB.KEY_ID, null);
                playerSelectAdapter = new SimpleCursorAdapter(GameActivity.this, android.R.layout.simple_spinner_item, playerSelectCursor, spinnerAdapterCols, spinnerAdapterRowViews, 0);
                playerSelectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                tally = app.getObject("tally");
                if (tally == null)
                    tally = new long[3];
                selectedPlayers = app.getObject("selectedPlayers");
                if (selectedPlayers == null)
                    selectedPlayers = new long[2];
                Log.d(TicTacToeApp.TAG, String.format("selectedPlayers: %s", Arrays.toString(selectedPlayers)));
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
                    Log.d(TicTacToeApp.TAG, String.format("set player %d to index %d, id %d", i, selected[i], selectedID));
                    selectedPlayers[i] = selectedID;
                    players[i] = app.getPlayer(selectedID);
                }

                restoreGame();

                int[] rngSeed = app.getObject("rngSeed");
                if (rngSeed == null) {
                    Log.d(TicTacToeApp.TAG, "queueing entropy collection");
                    bgHandler.post(entropyRunnable);
                }
                else {
                    Log.d(TicTacToeApp.TAG, "seeding PRNG with saved seed");
                    Player.prng.setSeed(rngSeed);
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TicTacToeApp.TAG, "Initialization completed");
                        playerSelect[0].setAdapter(playerSelectAdapter);
                        playerSelect[1].setAdapter(playerSelectAdapter);
                        playerSelect[0].setSelection(selected[0]);
                        playerSelect[1].setSelection(selected[1]);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                gameView.setBoardTouchListener(GameActivity.this);
                                playerSelect[0].setOnItemSelectedListener(GameActivity.this);
                                playerSelect[1].setOnItemSelectedListener(GameActivity.this);
                            }
                        });
                        tallyView.setText(String.format("Result: %s\nP1 win: %d\nP2 win: %d\nDraw:   %d", lastResult == Game.DRAW ? "Draw" : lastResult == Game.P1_WIN ? "P1 Win" : lastResult == Game.P2_WIN ? "P2 Win" : "", tally[0], tally[1], tally[2]));
                        gameView.setBoard(game.board());
                        tallyView.invalidate();
                        gameView.invalidate();
                    }
                });
            }
        };
        bgThread = new HandlerThread("TicTacToeBG") {
            @Override
            protected void onLooperPrepared() {
                super.onLooperPrepared();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        bgHandler = new Handler(bgThread.getLooper());
                        bgHandler.post(initRunnable);
                    }
                });
            }
        };
        bgThread.start();
        aboutWindowView = getLayoutInflater().inflate(R.layout.textpopup, null);
        TextView aboutTextView = (TextView) aboutWindowView.findViewById(R.id.aboutTextView);
        String aboutText;
        try {
            aboutText = Util.stringFromStream(getResources().openRawResource(R.raw.about), Charset.defaultCharset());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        aboutTextView.setText(Html.fromHtml(aboutText));
        aboutTextView.setMovementMethod(LinkMovementMethod.getInstance());
        Log.d(app.TAG, "GameActivity onCreate");
    }

    private void setOrientation(int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            topLayout.setOrientation(LinearLayout.VERTICAL);
            gameView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        }
        else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            topLayout.setOrientation(LinearLayout.HORIZONTAL);
            gameView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig)
    {
        TicTacToeApp app = TicTacToeApp.app();
        Log.d(app.TAG, String.format("onConfigurationChanged: %s %d %d %d %d", newConfig, newConfig.orientation, newConfig.getLayoutDirection(), Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT));
        super.onConfigurationChanged(newConfig);
        setOrientation(newConfig.orientation);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TicTacToeApp.TAG, "onPause state save start");
        long time = -System.currentTimeMillis();
        app.db.beginTransaction();
        try {
            app.putState("selectedPlayers", selectedPlayers);
            storeGame();
            app.db.setTransactionSuccessful();
        } finally {
            app.db.endTransaction();
        }
        Log.d(TicTacToeApp.TAG, String.format("stored game %s", app.gameSerializer.toBytes(game)));
        time += System.currentTimeMillis();
        Log.d(TicTacToeApp.TAG, String.format("onPause state save completed in %dms", time));
    }

    @Override
    public void onClick(GameView source, int x, int y) {
        TicTacToeApp app = TicTacToeApp.app();
        Log.d(TicTacToeApp.TAG, String.format("Board touch: %d %d", x, y));
        if (game.status() != Game.PLAYING) {
            savePlayers();
            gameView.setBoard(newGame().board());
        }
        else if (game.getPlayer() == null) {
            game.play(x, y, game.getCurrentPlayer());
            if (game.getPlayer() != null)
                game.run(1);
        }
        else
            game.run(1);
        if (game.status() != Game.PLAYING) {
            savePlayers();
            lastResult = game.status();
            tally[game.status() == Game.P1_WIN ? 0 : game.status() == Game.P2_WIN ? 1 : 2]++;
            tallyView.setText(String.format("Result: %s\nP1 win: %d\nP2 win: %d\nDraw:   %d", lastResult == Game.DRAW ? "Draw" : lastResult == Game.P1_WIN ? "P1 Win" : "P2 Win", tally[0], tally[1], tally[2]));
            tallyView.postInvalidate();
        }
        gameView.postInvalidate();
    }

    @Override
    public void onClick(GameView source, float x, float y) {
        if (game.status() != Game.PLAYING) {
            gameView.setBoard(newGame().board());
        }
        else if (game.getPlayer() != null)
            runGame(1);
        if (game.status() != Game.PLAYING) {
            savePlayers();
            lastResult = game.status();
            tally[game.status() == Game.P1_WIN ? 0 : game.status() == Game.P2_WIN ? 1 : 2]++;
            tallyView.setText(String.format("Result: %s\nP1 win: %d\nP2 win: %d\nDraw:   %d", game.status() == Game.DRAW ? "Draw" : game.status() == Game.P1_WIN ? "P1 Win" : "P2 Win", tally[0], tally[1], tally[2]));
            tallyView.postInvalidate();;
        }
        gameView.postInvalidate();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TicTacToeApp.TAG, "onItemSelected");
        for (int i = 0; i < 2; i++) {
            if (parent == playerSelect[i]) {
                if (selectedPlayers[i] == id)
                    return;
                Log.d(TicTacToeApp.TAG, String.format("change player %d to %d", i, position));
                storeGame();
                players[i] = TicTacToeApp.app().getPlayer(id);
                selectedPlayers[i] = id;
                restoreGame();
                gameView.setBoard(game.board());
                gameView.invalidate();
                tallyView.setText(String.format("Result:\nP1 win: %d\nP2 win: %d\nDraw:   %d", tally[0], tally[1], tally[2]));
                tallyView.invalidate();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TicTacToeApp.TAG, String.format("onNothingSelected: parent %s", parent));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.game_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_about:
                Log.v(TicTacToeApp.TAG, "open about");
                openAboutPopup();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void openAboutPopup () {
        PopupWindow aboutWindow = new PopupWindow(aboutWindowView);
        aboutWindow.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aboutWindow.setFocusable(true);
        aboutWindow.setBackgroundDrawable(new BitmapDrawable(getResources()));
        aboutWindow.setOutsideTouchable(true);
        aboutWindow.showAtLocation(topLayout, Gravity.CENTER, 0, 0);
    }

    public Game runGame(int turns) {
        game.run(turns);
        return game;
    }

    public Game runGame() {
        game.run();
        return game;
    }

    public Game newGame() {
        Log.d(TicTacToeApp.TAG, "newGame()");
        game = new Game(players[0], players[1]);
        return game;
    }

    private void savePlayer(int select) {
        if (players[select] == null) {
            Log.d(TicTacToeApp.TAG, String.format("savePlayer(%d): null", select));
            return;
        }
        Player player = players[select];
        byte[] state = app.serializer.toBytes(player);
        long id = app.getPlayerID(player);
        ContentValues update = new ContentValues();
        update.put(AppDB.KEY_STATE, state);
        Log.d(TicTacToeApp.TAG, String.format("savePlayer(%d): %s", select, update));
        String selector = String.format("_id=%d", id);
        app.db.update(AppDB.BRAINS_TABLE_NAME, update, selector, null);
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
            } catch (IllegalStateException e) {
            }
        }
        Log.v(TicTacToeApp.TAG, String.format("restoreGame: %s %s", game, storedTally == null ? "null" : Arrays.toString(storedTally)));
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
        Log.v(TicTacToeApp.TAG, String.format("storeGame result: %d", result));
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



}
