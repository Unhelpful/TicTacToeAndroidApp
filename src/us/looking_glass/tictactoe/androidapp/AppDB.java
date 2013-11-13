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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import us.looking_glass.tictactoe.BeanCounterPlayer;
import us.looking_glass.tictactoe.LMSRankPlayer;
import us.looking_glass.tictactoe.OptimalPlayer;
import us.looking_glass.tictactoe.RandomPlayer;
import us.looking_glass.util.Serializer;

public class AppDB extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "tictactoe.db";
    private static final int DATABASE_VERSION = 4;
    static final String BRAINS_TABLE_NAME = "brains";
    static final String KEY_ID = "_id";
    static final String KEY_NAME = "name";
    static final String KEY_STATE = "state";
    static final String BRAINS_TABLE_CREATE =
            "CREATE TABLE " + BRAINS_TABLE_NAME + " ("
            + KEY_ID + " integer primary key on conflict replace, "
            + KEY_NAME + " text not null unique, "
            + KEY_STATE + " blob);";
    static final String APPSTATE_TABLE_NAME = "appstate";
    static final String KEY_VALUE = "value";
    static final String APPSTATE_TABLE_CREATE =
            "CREATE TABLE " + APPSTATE_TABLE_NAME + " ("
            + KEY_ID + " integer primary key, "
            + KEY_NAME + " text unique not null unique on conflict replace, "
            + KEY_VALUE + " blob);";
    static final String TALLY_TABLE_NAME = "tally";
    static final String GAME_TABLE_NAME = "game";
    static final String KEY_P1ID = "p1id";
    static final String KEY_P2ID = "p2id";
    static final String KEY_TALLY = "tally";
    static final String KEY_GAME = "game";
    static final String KEY_RESULT = "result";
    static final String GAME_TABLE_CREATE =
            "CREATE TABLE " + GAME_TABLE_NAME + " ("
            + KEY_ID + " integer primary key, "
            + KEY_P1ID + " integer references " + BRAINS_TABLE_NAME + "(" + KEY_ID + ") on delete cascade, "
            + KEY_P2ID + " integer references " + BRAINS_TABLE_NAME + "(" + KEY_ID + ") on delete cascade, "
            + KEY_RESULT + " integer not null, "
            + KEY_TALLY + " blob, "
            + KEY_GAME + " blob, "
            + "unique (" + KEY_P1ID + ", " + KEY_P2ID + ") on conflict replace);";
    static final String[] ID_STATE_COLS = new String[]{ KEY_ID, KEY_STATE };
    static final String[] NAME_VALUE_COLS = new String[]{ KEY_NAME, KEY_VALUE };
    static final String[] TALLY_GAME_RESULT_COLS = new String[] { KEY_TALLY, KEY_GAME, KEY_RESULT };

    public AppDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Serializer s = TicTacToeApp.serializer();
        byte[] beanCounterState = s.toBytes(new BeanCounterPlayer());
        byte[] lmsRankState = s.toBytes(new LMSRankPlayer());
        byte[] randomState = s.toBytes(new RandomPlayer());
        byte[] optimalState = s.toBytes(new OptimalPlayer());
        db.beginTransaction();
        try {
            db.execSQL(BRAINS_TABLE_CREATE);
            db.execSQL(APPSTATE_TABLE_CREATE);
            db.execSQL(GAME_TABLE_CREATE);
            ContentValues initInsert = new ContentValues();
            initInsert.putNull(KEY_ID);
            initInsert.put(KEY_NAME, "User");
            initInsert.putNull(KEY_STATE);
            db.insert(BRAINS_TABLE_NAME, null, initInsert);
            initInsert.clear();
            initInsert.putNull(KEY_ID);
            initInsert.put(KEY_NAME, "Random");
            initInsert.put(KEY_STATE, randomState);
            db.insert(BRAINS_TABLE_NAME, null, initInsert);
            initInsert.clear();
            initInsert.putNull(KEY_ID);
            initInsert.put(KEY_NAME, "Bean Counter");
            initInsert.put(KEY_STATE, beanCounterState);
            db.insert(BRAINS_TABLE_NAME, null, initInsert);
            initInsert.clear();
            initInsert.putNull(KEY_ID);
            initInsert.put(KEY_NAME, "LMS Rank");
            initInsert.put(KEY_STATE, lmsRankState);
            db.insert(BRAINS_TABLE_NAME, null, initInsert);
            initInsert.clear();
            initInsert.putNull(KEY_ID);
            initInsert.put(KEY_NAME, "Optimal");
            initInsert.put(KEY_STATE, optimalState);
            db.insert(BRAINS_TABLE_NAME, null, initInsert);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void addOptimalPlayer(SQLiteDatabase db) {
        Serializer s = TicTacToeApp.serializer();
        byte[] optimalState = s.toBytes(new OptimalPlayer());
        ContentValues playerRecord = new ContentValues();
        playerRecord.putNull(KEY_ID);
        playerRecord.put(KEY_NAME, "Optimal");
        playerRecord.put(KEY_STATE, optimalState);
        db.insert(BRAINS_TABLE_NAME, null, playerRecord);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1:
                db.beginTransaction();
                try {
                    db.execSQL("DROP TABLE IF EXISTS " + APPSTATE_TABLE_NAME + ";");
                    db.execSQL("ALTER TABLE " + BRAINS_TABLE_NAME + " RENAME TO " + BRAINS_TABLE_NAME + "_tmp;");
                    db.execSQL("INSERT INTO" + BRAINS_TABLE_NAME + " SELECT * FROM " + BRAINS_TABLE_NAME + "_tmp;");
                    db.execSQL("DROP TABLE " + BRAINS_TABLE_NAME + "_tmp;");
                    db.execSQL(APPSTATE_TABLE_CREATE);
                    db.execSQL(GAME_TABLE_CREATE);
                    addOptimalPlayer(db);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                break;
            case 2:
                db.beginTransaction();
                try {
                    db.execSQL("DROP TABLE IF EXISTS " + TALLY_TABLE_NAME + ";");
                    db.delete(APPSTATE_TABLE_NAME, "name='tally'", null);
                    db.delete(APPSTATE_TABLE_NAME, "name='game'", null);
                    db.execSQL(GAME_TABLE_CREATE);
                    addOptimalPlayer(db);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                break;
            case 3:
                db.beginTransaction();
                try {
                    addOptimalPlayer(db);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
        }
    }
}
