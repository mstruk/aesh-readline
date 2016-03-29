/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aesh.readline;

import org.jboss.aesh.parser.Parser;
import org.jboss.aesh.util.ANSI;
import org.jboss.aesh.util.IntArrayBuilder;
import org.jboss.aesh.util.LoggerUtil;
import org.jboss.aesh.util.WcWidth;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Buffer to keep track of text and cursor position in the console.
 * Is using ANSI-codes to clear text and move cursor in the terminal.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Buffer {

    private static final Logger LOGGER = LoggerUtil.getLogger(Buffer.class.getName());

    private int[] line;
    private int cursor;
    private int size;
    private Prompt prompt;
    private int delta; //need to keep track of a delta for ansi terminal
    //if delta happens at the end of the buffer, we can optimize
    //how we update the tty
    private boolean deltaChangedAtEndOfBuffer = true;
    private boolean disablePrompt = false;
    private boolean multiLine = false;
    private int[] multiLineBuffer = new int[0];
    private boolean isPromptDisplayed = false;


    public Buffer() {
        line = new int[1024];
        prompt = new Prompt("");
    }

    public Buffer(Prompt prompt) {
        line = new int[1024];
        if(prompt != null)
            this.prompt = prompt;
        else
            this.prompt = new Prompt("");
    }

    public Buffer(Buffer buf) {
        line = buf.line.clone();
        cursor = buf.cursor;
        size = buf.size;
        prompt = buf.prompt.copy();
    }

    public int get(int pos) {
        if(pos > -1 && pos <= size)
            return line[pos];
        else
            throw new IndexOutOfBoundsException();
    }

    private int getCursor() {
        return cursor;
    }

    private int getCursorWithPrompt() {
        if(disablePrompt)
            return getCursor()+1;
        else
            return getCursor() + prompt.getLength()+1;
    }

    public int getMultiCursor() {
        if (multiLine) {
            return multiLineBuffer.length + getCursor();
        }
        else {
            return getCursor();
        }
    }

    public boolean isMasking() {
        return prompt.isMasking();
    }

    public boolean isMultiLine() {
        return multiLine;
    }

    public String getAsString() {
        return Parser.fromCodePoints(getLine());
    }

    public void reset() {
        cursor = 0;
        line = new int[1024];
        size = 0;
    }

    /**
     * Need to disable prompt in calculations involving search.
     *
     * @param disable prompt or not
     */
    public void disablePrompt(boolean disable) {
        disablePrompt = disable;
    }

    public boolean isPromptDisabled() {
        return disablePrompt;
    }

    public void setPrompt(Prompt prompt, Consumer<int[]> out, int width) {
        if(prompt != null) {
            delta =  prompt.getLength() - this.prompt.getLength();
            this.prompt = prompt;
            print(out, width);
        }
    }

    public Prompt getPrompt() {
        return prompt;
    }

    public int length() {
        if(prompt.isMasking() && prompt.getMask() == 0)
            return 1;
        else
            return size;
    }

    public int lengthWithPrompt() {
        if(prompt.isMasking()) {
            if(prompt.getMask() == 0)
                return disablePrompt ? 1 : prompt.getLength()+1;
        }
        return disablePrompt ? size+1 : size + prompt.getLength()+1;
    }

    public void setMultiLine(boolean multi) {
        if(!prompt.isMasking())
            multiLine = multi;
    }

    public void updateMultiLineBuffer() {
        int originalSize = multiLineBuffer.length;
        if(lineEndsWithBackslash()) {
            multiLineBuffer = Arrays.copyOf(multiLineBuffer, originalSize + size-1);
            System.arraycopy(line, 0, multiLineBuffer, originalSize, size-1);
        }
        else {
            multiLineBuffer = Arrays.copyOf(multiLineBuffer, size);
            System.arraycopy(line, 0, multiLineBuffer, size, originalSize);
        }
        clear();
        prompt = new Prompt("> ");
        cursor = 0;
        size = 0;
    }

    private boolean lineEndsWithBackslash() {
        return (size > 1 && line[size-1] == '\\' && line[size-2] == ' ');
    }

    /**
     * Insert text at cursor position
     *
     * @param data text
     */
    public void insert(Consumer<int[]> out, int[] data, int width) {
        doInsert(data);
        printInsertedData(out, width);
    }

    /**
     * Insert text at cursor position
     *
     * @param data text
     */
    public void insert(Consumer<int[]> out, char[] data, int width) {
        doInsert(data);
        printInsertedData(out, width);
    }


    /**
     * Insert at cursor position.
     *
     * @param data char
     */
    public void insert(Consumer<int[]> out, int data, int width) {
        doInsert(data);
        printInsertedData(out, width);
   }

    private void doInsert(int data) {
        int width = WcWidth.width(data);
        if(width == -1) {
            //todo: handle control chars...
        }
        else if(width == 1) {
            if(cursor < size)
                System.arraycopy(line, cursor, line, cursor + 1, size - cursor);
            line[cursor++] = data;
            size++;
            delta++;

            deltaChangedAtEndOfBuffer = (size == cursor);
        }
    }

    private void doInsert(int[] data) {
        boolean gotControlChar = false;
        for(int i=0; i < data.length; i++) {
            int width = WcWidth.width(data[i]);
            if (width == -1) {
                gotControlChar = true;
                //todo: handle control chars...
            }
        }
        if(!gotControlChar) {
            doActualInsert(data);
        }
    }

    private void doInsert(char[] data) {
        boolean gotControlChar = false;
        for(int i=0; i < data.length; i++) {
            int width = WcWidth.width(data[i]);
            if (width == -1) {
                gotControlChar = true;
                //todo: handle control chars...
            }
        }
        if(!gotControlChar) {
            doActualInsert(data);
        }
    }

    private void doActualInsert(int[] data) {
        if(cursor < size)
            System.arraycopy(line, cursor, line, cursor + data.length, size - cursor);
        for(int i=0; i < data.length; i++)
            line[cursor++] = data[i];
        size += data.length;
        delta += data.length;

        deltaChangedAtEndOfBuffer = (size == cursor);
    }

    private void doActualInsert(char[] data) {
        if(cursor < size)
            System.arraycopy(line, cursor, line, cursor + data.length, size - cursor);
        for(int i=0; i < data.length; i++)
            line[cursor++] = data[i];
        size += data.length;
        delta += data.length;

        deltaChangedAtEndOfBuffer = (size == cursor);
    }

    /**
     * Move the cursor left if the param is negative, right if its positive.
     *
     * @param out stream
     * @param move where to move
     * @param termWidth terminal width
     */
    public void move(Consumer<int[]> out,  int move, int termWidth) {
        move(out, move, termWidth, false);
    }

    /**
     * Move the cursor left if the param is negative, right if its positive.
     * If viMode is true, the cursor will not move beyond the current buffer size
     *
     * @param out stream
     * @param move where to move
     * @param termWidth terminal width
     * @param viMode edit mode (vi or emacs)
     */
    public void move(Consumer<int[]> out, int move, int termWidth, boolean viMode) {
        LOGGER.info("moving: "+move+", width: "+termWidth+", buffer: "+getLine());
        move = calulateActualMovement(move, viMode);

        int currentRow = (getCursorWithPrompt() / (termWidth));
        if(currentRow > 0 && getCursorWithPrompt() % termWidth == 0)
            currentRow--;

        int newRow = ((move + getCursorWithPrompt()) / (termWidth));
        if(newRow > 0 && ((move + getCursorWithPrompt()) % (termWidth) == 0))
            newRow--;

        int row = newRow - currentRow;

        LOGGER.info("currentRow: "+currentRow+", newRow: "+newRow+", row: "+row+", cursor: "+cursor);

        cursor = cursor + move;

        // 0 Masking separates the UI cursor position from the 'real' cursor position.
        // Cursor movement still has to occur, via calulateActualMovement and setCursor above,
        // to put new characters in the correct location in the invisible line,
        // but this method should always return an empty character so the UI cursor does not move.
        if(prompt.isMasking() && prompt.getMask() == 0){
            return;
        }

        int cursor = getCursorWithPrompt() % termWidth;
        if(cursor == 0 && getCursorWithPrompt() > 0)
            cursor = termWidth;
        if(row > 0) {
            out.accept(moveNumberOfColumnsAndRows(row, 'B', cursor));

            //StringBuilder sb = new StringBuilder();
            //sb.append(printAnsi(row+"B")).append(printAnsi(cursor+"G"));
            //return sb.toString().toCharArray();
        }
        //going up
        else if (row < 0) {
            //check if we are on the "first" row:
            //StringBuilder sb = new StringBuilder();
            //sb.append(printAnsi(Math.abs(row)+"A")).append(printAnsi(cursor+"G"));
            LOGGER.info("moving up a row: "+Arrays.toString(moveNumberOfColumnsAndRows(Math.abs(row), 'A', cursor)));
            out.accept(moveNumberOfColumnsAndRows(Math.abs(row), 'A', cursor));
            //return sb.toString().toCharArray();
        }
        //staying at the same row
        else {
            LOGGER.info("staying at same row "+move);
            if(move < 0) {
                LOGGER.info("returning: " + Arrays.toString(moveNumberOfColumns(Math.abs(move), 'D')));
                //out.accept(new int[]{'\b'});
                out.accept(moveNumberOfColumns(Math.abs(move), 'D'));
                //return printAnsi(Math.abs(move)+"D");
            }

            else if(move > 0) {
                LOGGER.info("returning: "+ Arrays.toString( moveNumberOfColumns(move,'C')));
                //return printAnsi(move + "C");
                out.accept(moveNumberOfColumns(move, 'C'));
            }
        }
    }

    private int[] moveNumberOfColumns(int column, char direction) {
        if(column < 10) {
            int[] out = new int[4];
            out[0] = 27; // esc
            out[1] = '['; // [
            out[2] = 48 + column;
            out[3] = direction;
            return out;
        }
        else {
            int[] asciiColumn = intToAsciiInts(column);
            int[] out = new int[3+asciiColumn.length];
            out[0] = 27; // esc
            out[1] = '['; // [
            for(int i=0; i < asciiColumn.length; i++)
                out[2+i] = asciiColumn[i];
            out[out.length-1] = direction;
            return out;
        }
    }

    private int[] moveNumberOfColumnsAndRows(int row, char rowCommand, int column) {
        if(row < 10 && column < 10) {
            int[] out = new int[8];
            out[0] = 27; //esc, \033
            out[1] = '[';
            out[2] = 48 + row;
            out[3] = rowCommand;
            out[4] = 27;
            out[5] = '[';
            out[6] = 48 + column;
            out[7] = 'G';
            return out;
        }
        else {
            int[] asciiRow = intToAsciiInts(row);
            int[] asciiColumn = intToAsciiInts(column);
            int[] out = new int[6+asciiColumn.length+asciiRow.length];
            out[0] = 27; //esc, \033
            out[1] = '[';
            for(int i=0; i < asciiRow.length; i++)
                out[2+i] = asciiRow[i];
            out[2+asciiRow.length] = rowCommand;
            out[3+asciiRow.length] = 27;
            out[4+asciiRow.length] = '[';
            for(int i=0; i < asciiColumn.length; i++)
                out[5+asciiRow.length+i] = asciiColumn[i];
            out[out.length-1] = 'G';
            return out;
        }
    }



    /**
     * Make sure that the cursor do not move ob (out of bounds)
     *
     * @param move left if its negative, right if its positive
     * @param viMode if viMode we need other restrictions compared
     * to emacs movement
     * @return adjusted movement
     */
    private int calulateActualMovement(final int move, boolean viMode) {
        // cant move to a negative value
        if(getCursor() == 0 && move <=0 )
            return 0;
        // cant move longer than the length of the line
        if(viMode) {
            if(getCursor() == length()-1 && (move > 0))
                return 0;
        }
        else {
            if(getCursor() == length() && (move > 0))
                return 0;
        }

        // dont move out of bounds
        if(getCursor() + move <= 0)
            return -getCursor();

        if(viMode) {
            if(getCursor() + move > length()-1)
                return (length()-1-getCursor());
        }
        else {
            if(getCursor() + move > length())
                return (length()-getCursor());
        }

        return move;
    }

    private int[] getLineFrom(int position) {
        return Arrays.copyOfRange(line, position, size);
    }

    public int[] getLine() {
        if(!prompt.isMasking())
            return Arrays.copyOf(line, size);
        else {
            if(size > 0 && prompt.getMask() != '\u0000') {
                int[] tmpLine = new int[size];
                Arrays.fill(tmpLine, prompt.getMask());
                return tmpLine;
            }
            else
                return new int[0];
        }
    }

    private int[] getLineNoMask() {
        return Arrays.copyOf(line, size);
    }

    public void clear() {
        Arrays.fill(this.line, 0, size, 0);
        cursor = 0;
        size = 0;
        isPromptDisplayed = false;
    }

    /**
     * If delta > 0 * print from cursor
     *   if keepCursor, move cursor back to previous position
     * if delta < 0
     *   if deltaChangedAtEndOf buffer {
     *       if delta == -1 {
     *         clear line from cursor
     *         move cursor back
     *       }
     *       else if cursor + delta > width {
     *           check if we need to delete more than the current line
     *       }
     *   }
     * @param out
     * @param width
     */
    public void print(Consumer<int[]> out, int width) {
        if(delta >= 0)
            printInsertedData(out, width);
        else {

        }
    }

    private void printInsertedData(Consumer<int[]> out, int width) {
        //print out prompt first if needed
        IntArrayBuilder builder = new IntArrayBuilder();
        if(size == delta && !isPromptDisplayed && prompt.getLength() > 0) {
            builder.append(prompt.getANSI());
            isPromptDisplayed = true;
        }

        if(deltaChangedAtEndOfBuffer) {
            if(delta == 1)
                builder.append(new int[]{line[cursor-1]});
            else
                builder.append( Arrays.copyOfRange(line, cursor-delta, cursor));
        }
        else {
            builder.append(Arrays.copyOfRange(line, cursor-delta, size));
        }

        //pad if we are at the end of the terminal
        if((size + prompt.getLength()+1) % width == 1) {
            builder.append(new int[]{32, 13});
        }
        //make sure we sync the cursor back
        if(!deltaChangedAtEndOfBuffer) {
            //if cursor and and of buffer is on the same line:
            if(size / width == cursor / width) {
                builder.append(moveNumberOfColumns(size-cursor, 'D'));
            }
            //if cursor and enf of buffer is on different lines, we need to move the cursor
            else {
                int numLines = (size / width) - (cursor / width);
                int sameLine = size - (width * numLines);
                if(sameLine < cursor)
                    builder.append(moveNumberOfColumns(cursor-sameLine, 'C'));
                else
                    builder.append(moveNumberOfColumns(cursor-sameLine, 'D'));
            }
        }

        out.accept(builder.toArray());
        delta = 0;
        deltaChangedAtEndOfBuffer = true;
    }

    /**
     * Replace the entire current buffer with the given line.
     * The new line will be pushed to the consumer
     * Cursor will be moved to the end of the new buffer line
     *
     * @param out stream
     * @param line new buffer line
     * @param width term width
     */
    public void replace(Consumer<int[]> out, String line, int width) {
        int tmpDelta = line.length() - size;
        int oldCursor = cursor + prompt.getLength();
        clear();
        doInsert(Parser.toCodePoints(line));
        delta = tmpDelta;
        //deltaChangedAtEndOfBuffer = false;
        deltaChangedAtEndOfBuffer = (cursor == size);

        if(oldCursor > width) {
            IntArrayBuilder builder = new IntArrayBuilder();
            int originalRow = oldCursor / width;
            if (originalRow > 0 && lengthWithPrompt() % width == 0)
                originalRow--;
            for (int i = 0; i < originalRow; i++) {
                if (delta < 0) {
                    builder.append(ANSI.ERASE_WHOLE_LINE);
                }
                builder.append(ANSI.MOVE_LINE_UP);
            }
            moveCursorToStartAndPrint(out, builder, false);
            delta = 0;
            deltaChangedAtEndOfBuffer = true;
        }
        else {
            replaceLineWhenCursorIsOnLine(out, width);
            delta = 0;
            deltaChangedAtEndOfBuffer = true;
        }
    }

    private void replaceLineWhenCursorIsOnLine(Consumer<int[]> out, int width) {
        if(delta >= 0) {
            moveCursorToStartAndPrint(out, new IntArrayBuilder(), false);
        }
        else { // delta < 0
            if((lengthWithPrompt()+delta) <= width) {
                moveCursorToStartAndPrint(out, new IntArrayBuilder(), true);
            }
            else {
                int numRows = lengthWithPrompt() / width;
                if(numRows > 0 && lengthWithPrompt() % width == 0)
                    numRows--;
                IntArrayBuilder builder = new IntArrayBuilder();
                clearRowsAndMoveBack(builder, numRows);
                moveCursorToStartAndPrint(out, builder, false);
            }
        }
    }

    private void clearRowsAndMoveBack(IntArrayBuilder builder, int rows) {
        for(int i=0; i < rows; i++) {
            builder.append(ANSI.MOVE_LINE_DOWN);
            builder.append(ANSI.ERASE_WHOLE_LINE);
        }
        //move back again
        if(rows < 10)
            builder.append(new int[]{27,'[', 48+rows,'A'});
        else
            builder.append(moveNumberOfColumns(rows, 'A'));
    }

    private void moveCursorToStartAndPrint(Consumer<int[]> out, IntArrayBuilder builder,
                                           boolean clearLine) {
        if((prompt.getLength() > 0 && cursor != 0) || delta < 0) {
            builder.append(ANSI.CURSOR_START);
            if (clearLine)
                builder.append(ANSI.ERASE_LINE_FROM_CURSOR);
        }
        if(prompt.getLength() > 0)
            builder.append(prompt.getANSI());

        //dont print out the line if its empty
        if(size > 0)
            builder.append(getLine());

        out.accept(builder.toArray());
    }

    private int[] getMultiLine() {
        if (multiLine) {
            int[] tmpLine = Arrays.copyOf(multiLineBuffer, multiLineBuffer.length + size);
            System.arraycopy(line, 0, tmpLine, multiLineBuffer.length, size);
            return  tmpLine;
        }
        else {
            return getLine();
        }
    }

    /**
     * Delete from cursor position and backwards if delta is < 0
     * Delete from cursor position and forwards if delta is > 0
     * @param delta
     */
    public void delete(Consumer<int[]> out, int delta, int width) {
        if (delta > 0) {
            delta = Math.min(delta, size - cursor);
            LOGGER.info("deleting, delta: "+delta+", cursor: "+cursor+", size: "+size);
            System.arraycopy(line, cursor + delta, line, cursor, size - cursor + delta);
            size -= delta;
            this.delta =- delta;
            print(out, width);
        }
        else if (delta < 0) {
            delta = - Math.min(- delta, cursor);
            LOGGER.info("deleting, delta: "+delta+", cursor: "+cursor+", size: "+size);
            System.arraycopy(line, cursor, line, cursor + delta, size - cursor);
            size += delta;
            cursor += delta;
            this.delta =+ delta;
            print(out, width);
        }
    }

    /**
     * Write a string to the line and update cursor accordingly
     *
     * @param out
     * @param str string
     */
    public void insert(Consumer<int[]> out, final String str, int width) {
        insert(out, Parser.toCodePoints(str), width);
    }

    /**
     * Switch case if the current character is a letter.
     */
    public void changeCase(Consumer<int[]> out) {
        if(Character.isLetter(line[cursor])) {
            if(Character.isLowerCase(line[cursor]))
                line[cursor] = Character.toUpperCase(line[cursor]);
            else
                line[cursor] = Character.toLowerCase(line[cursor]);

            out.accept(new int[]{line[cursor]});
        }
    }

    /**
     * Up case if the current character is a letter
     */
    public void upCase(Consumer<int[]> out) {
        if(Character.isLetter(line[cursor])) {
            line[cursor] = Character.toUpperCase(line[cursor]);
            out.accept(new int[]{line[cursor]});
        }
    }

    /**
     * Lower case if the current character is a letter
     */
    public void downCase(Consumer<int[]> out) {
        if(Character.isLetter(line[cursor])) {
            line[cursor] = Character.toLowerCase(line[cursor]);
            out.accept(new int[]{line[cursor]});
        }
    }

    /**
     * Replace the current character
     */
    public void replace(Consumer<int[]> out, char rChar) {
        doReplace(out, getCursor(), rChar);
    }

    private void doReplace(Consumer<int[]> out, int pos, int rChar) {
        if(pos > -1 && pos <= size) {
            line[pos] = rChar;
            out.accept(new int[]{rChar});
        }
    }

    /**
     * @return the buffer as string
     */
    public String asString() {
        return Parser.fromCodePoints(getMultiLine());
    }

    /**
     * we assume that value is > 0
     *
     * @param value
     * @return ascii represented int value
     */
    private int[] intToAsciiInts(int value) {
        int length = getAsciiSize(value);
        int[] asciiValue = new int[length];

        if(length == 1) {
            asciiValue[0] = 48+value;
        }
        else {
            while(length > 0) {
                length--;
                int num = value % 10;
                asciiValue[length] = 48+num;
                value = value / 10;
            }
        }
        return asciiValue;
    }

    private int getAsciiSize(int value) {
        if(value < 10)
            return 1;
        //very simple way of getting the length
        if(value > 9 && value < 99)
            return 2;
        else if(value > 99 && value < 999)
            return 3;
        else if(value > 999 && value < 9999)
            return 4;
        else
            return 5;
    }
}
