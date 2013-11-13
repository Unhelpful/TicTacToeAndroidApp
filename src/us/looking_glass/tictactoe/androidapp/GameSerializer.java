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

import us.looking_glass.tictactoe.Player;
import us.looking_glass.util.Serializer;

import java.io.*;

public class GameSerializer extends Serializer {

    @Override
    protected ObjectOutput getObjectOutput(OutputStream os) throws IOException {
        return new ObjectOutputStream(os) {
            {
                this.enableReplaceObject(true);
            }

            @Override
            protected Object replaceObject(Object object) throws IOException {
                if (object instanceof Player)
                    return new PlayerProxy(TicTacToeApp.app().getPlayerID((Player) object));
                else
                    return object;
            }
        };
    }

    private static class PlayerProxy implements Serializable {
        private final long id;

        private PlayerProxy(long id) {
            this.id = id;
        }

        private Object readResolve() {
            return TicTacToeApp.app().getPlayer(id);
        }
    }
}
