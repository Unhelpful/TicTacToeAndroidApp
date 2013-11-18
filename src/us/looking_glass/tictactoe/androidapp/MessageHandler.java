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

import android.os.Handler;
import android.os.Looper;

public class MessageHandler extends Handler {
    public MessageHandler() {
        super();
    }

    public MessageHandler(Looper looper) {
        super(looper);
    }

    public final void sendMessage(int which, int arg1, int arg2, Object obj) {
        sendMessage(obtainMessage(which, arg1, arg2, obj));
    }
}
