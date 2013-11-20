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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import us.looking_glass.tictactoe.Board;


public class GameView extends View {
    Context context;
    int width;
    float barOffset;
    float strokeWidth;
    float halfStroke;
    float[] boxEdge = new float[6];
    private int board = 0;
    private BoardTouchListener boardTouchListener;
    final static boolean debug = false;
    private final static String TAG = "TicTacToe:GameView";

    public GameView(Context context) {
        super(context);
        this.context = context;
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public GameView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int lockedWidth = MeasureSpec.getSize(widthSpec);
        int lockedHeight = MeasureSpec.getSize(heightSpec);
        int hPadding = getPaddingLeft() + getPaddingRight();
        int vPadding = getPaddingTop() + getPaddingBottom();
        int wMode = MeasureSpec.getMode(widthSpec);
        int hMode = MeasureSpec.getMode(heightSpec);
        if (wMode == MeasureSpec.UNSPECIFIED)
            lockedWidth = 0;
        else if (hMode == MeasureSpec.UNSPECIFIED)
            lockedHeight = 0;
        Logv("width: %s, height: %s", MeasureSpec.toString(widthSpec), MeasureSpec.toString(heightSpec));
        lockedWidth -= hPadding;
        lockedHeight -= vPadding;
        if (lockedWidth <= 0) {
            Logv("expanding <0 width to match height");
            lockedWidth = lockedHeight;
        } else if (lockedHeight <= 0) {
            Logv("expanding <0 height to match width");
            lockedHeight = lockedWidth;
        } else {
            Logv("using minimum of %d and %d", lockedWidth, lockedHeight);
            lockedHeight = lockedWidth = Math.min(lockedHeight, lockedWidth);
        }
        lockedWidth += hPadding;
        lockedHeight += vPadding;
        int widthSize = resolveSizeAndState(lockedWidth, widthSpec, 0);
        int heightSize = resolveSizeAndState(lockedHeight, heightSpec, 0);
        setMeasuredDimension(widthSize, heightSize);
        Logv("layed out size: %dx%d", getWidth(), getHeight());
        Logv("measured size: %dx%d [%d]", getMeasuredWidth(), getMeasuredHeight(), getMeasuredState());
        if (getMeasuredHeight() > lockedHeight || getMeasuredWidth() > lockedWidth)
            requestLayout();
        width = lockedWidth;
        strokeWidth = width / 30;
        halfStroke = strokeWidth / 2;
        barOffset = (width - strokeWidth * 2) / 3 + strokeWidth / 2;
        boxEdge[0] = halfStroke;
        boxEdge[1] = barOffset + strokeWidth;
        boxEdge[2] = width - barOffset + strokeWidth;
        boxEdge[3] = barOffset - strokeWidth;
        boxEdge[4] = width - barOffset - strokeWidth;
        boxEdge[5] = width - halfStroke;

    }

    @Override
    protected  void onDraw(Canvas canvas) {
        Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
        bars.setColor(Color.WHITE);
        bars.setStrokeWidth(strokeWidth);
        bars.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(barOffset, halfStroke, barOffset, width - halfStroke, bars);
        canvas.drawLine(width - barOffset, halfStroke, width - barOffset, width - halfStroke, bars);
        canvas.drawLine(halfStroke, barOffset, width - halfStroke, barOffset, bars);
        canvas.drawLine(halfStroke, width - barOffset, width - halfStroke, width - barOffset, bars);
        Paint p1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        p1.setColor(Color.RED);
        Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        p2.setColor(Color.BLUE);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                int player = Board.get(board, x, y);
                if (player == 0)
                    continue;
                canvas.drawRoundRect(new RectF(boxEdge[x], boxEdge[y], boxEdge[3+x], boxEdge[3+y]), halfStroke, halfStroke, player == 1 ? p1 : p2);
            }
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN)
            return true;
        if (action == MotionEvent.ACTION_UP) {
            if (boardTouchListener != null) {
                float x = event.getX();
                float y = event.getY();
                int x_b = -1, y_b = -1;
                for (int i = 0; i < 3; i++) {
                    if (x >= boxEdge[i] && x <= boxEdge[i+3])
                        x_b = i;
                    if (y >= boxEdge[i] && y <= boxEdge[i+3])
                        y_b = i;
                }
                if (x_b != -1 && y_b != -1 && Board.get(board, x_b, y_b) == 0)
                    boardTouchListener.onClick(this, x_b, y_b);
                else
                    boardTouchListener.onClick(this, x, y);
            }
            return true;
        }
        return false;
    }

    public int getContents() {
        return board;
    }

    public void setContents(int contents) {
        this.board = contents;
    }

    public BoardTouchListener getBoardTouchListener() {
        return boardTouchListener;
    }

    public void setBoardTouchListener(BoardTouchListener boardTouchListener) {
        this.boardTouchListener = boardTouchListener;
    }

    static interface BoardTouchListener {
        void onClick(GameView source, int x, int y);
        void onClick(GameView source, float x, float y);
    }

    private static final void Logd(String text, Object... args) {
        if (debug) {
            if (args != null && args.length > 0)
                text = String.format(text, args);
            Log.d(TAG, text);
        }
    }
    private static final void Logv(String text, Object... args) {
        if (debug) {
            if (args != null && args.length > 0)
                text = String.format(text, args);
            Log.v(TAG, text);
        }
    }
}
