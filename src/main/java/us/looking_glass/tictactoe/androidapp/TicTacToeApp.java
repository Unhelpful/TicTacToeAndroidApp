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

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import us.looking_glass.tictactoe.Player;
import us.looking_glass.util.Serializer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class TicTacToeApp extends Application {
    Map<Long, WeakReference<Player>> idToPlayer = new HashMap<Long, WeakReference<Player>>();
    Map<Player, Long> playerToId = new WeakHashMap<Player, Long>();
    private static TicTacToeApp app;
    final static String TAG = "TicTacToe:App";
    Serializer serializer;
    Serializer gameSerializer;
    private AppDB dbOpener;
    SQLiteDatabase db;
    final static boolean debug = false;

    private void openDB() {
        dbOpener = new AppDB(this);
        db = dbOpener.getWritableDatabase();
    }

    private void configureSerializer() {
        serializer = new Serializer();
        gameSerializer = new GameSerializer();
    }

    private void readSerialized () {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        configureSerializer();
        openDB();
    }

    static TicTacToeApp app() {
        return app;
    }

    static Serializer serializer() {
        return app.serializer;
    }

    Player getPlayer(long id) {
        if (idToPlayer.containsKey(id)) {
            WeakReference<Player> reference = idToPlayer.get(id);
            if (reference == null) {
                if (debug) Logd("getPlayer(%d): retrieved null from cache", id);
                return null;
            }
            Player result = reference.get();
            if (result != null) {
                if (debug) Logd("getPlayer(%d): retrieved %s from cache", id, result);
                return result;
            }
        }
        String queryString = String.format("_id=%d", id);
        Cursor result = db.query(AppDB.BRAINS_TABLE_NAME, AppDB.ID_STATE_COLS, queryString, null, null, null, AppDB.KEY_ID, null);
        try {
            if (debug) Logd("getPlayer(%d): %d results", id, result.getCount());
            if (result.getCount() != 1 || !result.moveToFirst())
                return null;
            int colIndex = result.getColumnIndexOrThrow(AppDB.KEY_STATE);
            byte[] state = result.getBlob(colIndex);
            Player playerResult = null;
            if (state != null)
                playerResult = (Player) serializer().fromBytes(state);
            if (playerResult == null) {
                idToPlayer.put(id, null);
                return null;
            }
            if (debug) Logd("getPlayer(%d): retrieved %s from db", id, playerResult);
            playerToId.put(playerResult, id);
            idToPlayer.put(id, new WeakReference<Player>(playerResult));
            return playerResult;
        } finally {
            result.close();
        }
    }

    long getPlayerID(Player player) {
        if (playerToId.containsKey(player))
            return playerToId.get(player);
        throw new IllegalStateException(String.format("Player not in ID index: %s", player));
    }

    private Cursor retrieveValue(String key) {
        String queryString = AppDB.KEY_NAME + "='" + key + "'";
        Cursor result = app().db.query(true, AppDB.APPSTATE_TABLE_NAME, AppDB.NAME_VALUE_COLS, queryString, null, null,null, null, "1");
        if (result.getCount() != 1) {
            result.close();
            return null;
        }
        result.moveToFirst();
        return result;
    }

    private ContentValues entryForKey(String key) {
        ContentValues entry = new ContentValues();
        entry.put(AppDB.KEY_NAME, key);
        return entry;
    }

    private void putEntry(ContentValues entry) {
        db.insertWithOnConflict(AppDB.APPSTATE_TABLE_NAME, null, entry, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public int getInt(String key, int def) {
        Cursor result = retrieveValue(key);
        if (result == null)
            return def;
        try {
            return result.getInt(result.getColumnIndexOrThrow(AppDB.KEY_VALUE));
        } finally { result.close(); }
    }

    public void putState(String key, int value) {
        ContentValues entry = entryForKey(key);
        entry.put(AppDB.KEY_VALUE, value);
        putEntry(entry);
    }

    public String getString(String key) {
        Cursor result = retrieveValue(key);
        if (result == null)
            return null;
        try {
            return result.getString(result.getColumnIndexOrThrow(AppDB.KEY_VALUE));
        } finally { result.close(); }
    }

    public void putState(String key, String value) {
        ContentValues entry = entryForKey(key);
        entry.put(AppDB.KEY_VALUE, value);
        putEntry(entry);
    }

    public byte[] getBlob(String key) {
        Cursor result = retrieveValue(key);
        if (result == null)
            return null;
        try {
            return result.getBlob(result.getColumnIndexOrThrow(AppDB.KEY_VALUE));
        } finally { result.close(); }
    }

    public void putState(String key, byte[] value) {
        ContentValues entry = entryForKey(key);
        entry.put(AppDB.KEY_VALUE, value);
        putEntry(entry);
    }

    public <T> T getObject(String key) {
        Cursor result = retrieveValue(key);
        if (result == null)
            return null;
        try {
            return (T) serializer.fromBytes(result.getBlob(result.getColumnIndexOrThrow(AppDB.KEY_VALUE)));
        } finally { result.close(); }
    }

    public <T> void putState(String key, T value, Serializer serializer) {
        ContentValues entry = entryForKey(key);
        entry.put(AppDB.KEY_VALUE, serializer.toBytes(value));
        putEntry(entry);
    }

    public <T> void putState(String key, T value) {
        putState(key, value, serializer);
    }


    private static final void Logd(String text, Object... args) {
        if (args != null && args.length > 0)
            text = String.format(text, args);
        Log.d(TAG, text);
    }

    private static final void Logv(String text, Object... args) {
        if (args != null && args.length > 0)
            text = String.format(text, args);
        Log.v(TAG, text);
    }
}
