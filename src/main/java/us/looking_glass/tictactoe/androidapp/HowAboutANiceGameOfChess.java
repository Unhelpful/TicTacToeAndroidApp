package us.looking_glass.tictactoe.androidapp;

import android.app.Dialog;
import android.os.Handler;
import us.looking_glass.tictactoe.Game;
import us.looking_glass.tictactoe.OptimalPlayer;
import us.looking_glass.tictactoe.Player;

/**
 * Copyright 2013 Andrew Mahone
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class HowAboutANiceGameOfChess implements Runnable {
    private Player p = new OptimalPlayer();
    private Game g;
    final private GameView v;
    final private Dialog d;
    final private GameActivity a;
    private int delay = 1000;

    HowAboutANiceGameOfChess(Dialog d, GameView v, GameActivity a) {
        this.d = d;
        this.v = v;
        this.a = a;
        g = new Game(p, p);
        postBoardUpdate();
    }

    @Override
    public void run() {
        int extra = 0;
        if (!d.isShowing())
            return;
        if (g == null || g.status() != Game.PLAYING) {
            delay = Math.max(delay * 85 / 100, 40);
            g = new Game(p, p);
        } else if (g.turn() == 0) {
            int m = Player.prng.nextInt(9);
            g.play(m / 3, m % 3, 1);
        } else
            g.run(1);
        if (g.status() != Game.PLAYING) extra = 150;
        postBoardUpdate();
        a.bgHandler.postDelayed(this, delay + extra);
    }

    private void postBoardUpdate() {
        final int contents = g.board().getContents();
        a.handler.post(new Runnable() {
            @Override
            public void run() {
                v.setContents(contents);
                v.invalidate();
            }
        });
    }
}
