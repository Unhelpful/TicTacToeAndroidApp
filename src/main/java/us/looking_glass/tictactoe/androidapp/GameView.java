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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import us.looking_glass.tictactoe.Board;
import us.looking_glass.tictactoe.Point;


public class GameView extends View {
    int width;
    float barOffset;
    final float strokeWidth;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float[] boxEdges = new float[4];
    private int board = 0;
    final static boolean debug = false;
    private final static String TAG = "TicTacToe:GameView";
    private final static int[] colors = new int[] { 0xff700000, 0xff000070, 0xffff0000, 0xff0000ff };

    public GameView(Context context) {
        this(context, null, 0);
    }

    public GameView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GameView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getContext().getResources().getDisplayMetrics());
        if (attrs != null)
            try {
                TypedArray a  = getContext().obtainStyledAttributes(attrs, R.styleable.GameView);
                board = a.getInt(R.styleable.GameView_contents, 0);
                a.recycle();
            } catch (NullPointerException e) {
            }
        paint.setStrokeWidth(strokeWidth);
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
        if (debug) Logv("width: %s, height: %s", MeasureSpec.toString(widthSpec), MeasureSpec.toString(heightSpec));
        lockedWidth -= hPadding;
        lockedHeight -= vPadding;
        if (lockedWidth <= 0) {
            if (debug) Logv("expanding <0 width to match height");
            lockedWidth = lockedHeight;
        } else if (lockedHeight <= 0) {
            if (debug) Logv("expanding <0 height to match width");
            lockedHeight = lockedWidth;
        } else {
            if (debug) Logv("using minimum of %d and %d", lockedWidth, lockedHeight);
            lockedHeight = lockedWidth = Math.min(lockedHeight, lockedWidth);
        }
        lockedWidth += hPadding;
        lockedHeight += vPadding;
        int widthSize = resolveSizeAndState(lockedWidth, widthSpec, 0);
        int heightSize = resolveSizeAndState(lockedHeight, heightSpec, 0);
        setMeasuredDimension(widthSize, heightSize);
        if (debug) Logv("measured size: %dx%d", getMeasuredWidth(), getMeasuredHeight());
        if (getMeasuredHeight() > lockedHeight || getMeasuredWidth() > lockedWidth)
            requestLayout();
        width = lockedWidth;
        barOffset = (width - strokeWidth * 2) / 3 + strokeWidth / 2;
        boxEdges[1] = barOffset;
        boxEdges[2] = width - barOffset;
        boxEdges[3] = width;
    }

    @Override
    protected  void onDraw(Canvas canvas) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                int player = Board.get(board, x, y);
                if (player == 0)
                    continue;
                player += (!Board.isMarked(board) || Board.getMark(board, x, y)) ? 1 : -1;
                paint.setColor(colors[player]);
                canvas.drawRect(boxEdges[x], boxEdges[y], boxEdges[x + 1], boxEdges[y + 1], paint);
            }
        }
        paint.setColor(0xffffffff);
        canvas.drawLine(barOffset, 0, barOffset, width, paint);
        canvas.drawLine(width - barOffset, 0, width - barOffset, width, paint);
        canvas.drawLine(0, barOffset, width, barOffset, paint);
        canvas.drawLine(0, width - barOffset, width, width - barOffset, paint);
    }

    public int resolveBoardCoordinates(float x, float y) {
        int x_b = x < barOffset ? 0 : (x < width - barOffset ? 1 : 2);
        int y_b = y < barOffset ? 0 : (y < width - barOffset ? 1 : 2);
        if (debug) Logv("resolveBoardCoordinates: (%f,%f)->(%d,%d)", x, y, x_b, y_b);
        return Point.point(x_b, y_b);
    }

    public float centerOffset(int coord) {
        float offset = boxEdges[coord] + boxEdges[coord  + 1];
        if (coord == 0)
            offset -= strokeWidth / 2;
        else if (coord == 2)
            offset += strokeWidth / 2;
        return offset / 2;
    }

    public PointF getSquareCenter(int x, int y) {
        return  new PointF(centerOffset(x), centerOffset(y));
    }

    public int getContents() {
        return board;
    }

    public void setContents(int contents) {
        this.board = contents;
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
