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
package org.aesh.readline.action.mappings;

import org.aesh.readline.InputProcessor;
import org.aesh.readline.editing.EditMode;
import org.aesh.util.Parser;

import java.util.Arrays;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
abstract class ChangeAction extends MovementAction {

    private EditMode.Status status;
    protected boolean viMode;

    ChangeAction(EditMode.Status  status) {
        this.status = status;
        viMode = false;
    }

    ChangeAction(boolean viMode, EditMode.Status status) {
        this.status = status;
        this.viMode = viMode;
    }

    protected EditMode.Status getStatus() {
        return status;
    }

    protected final void apply(int cursor, InputProcessor inputProcessor) {
        apply(cursor, inputProcessor.getBuffer().buffer().cursor(), inputProcessor);
    }

    protected final void apply(int cursor, int oldCursor, InputProcessor inputProcessor) {
        if(status == EditMode.Status.DELETE || status == EditMode.Status.CHANGE) {
            inputProcessor.getBuffer().addActionToUndoStack();
            if(cursor < oldCursor) {
                //add to pastemanager
                inputProcessor.getBuffer().pasteManager().addText(
                        Arrays.copyOfRange(inputProcessor.getBuffer().buffer().multiLine(), cursor, oldCursor));
                //delete buffer
                inputProcessor.getBuffer().delete(cursor - oldCursor);
            }
            else {
                //add to pastemanager
                inputProcessor.getBuffer().pasteManager().addText(
                        Arrays.copyOfRange(inputProcessor.getBuffer().buffer().multiLine(), oldCursor, cursor));
                //delete buffer
                inputProcessor.getBuffer().delete(cursor - oldCursor);
            }

            //TODO: must check if we're in edit mode
            if(viMode && status == EditMode.Status.DELETE &&
                    oldCursor == inputProcessor.getBuffer().buffer().length())
                inputProcessor.getBuffer().moveCursor(-1);

        }
        else if(status == EditMode.Status.MOVE) {
            inputProcessor.getBuffer().moveCursor(cursor - oldCursor);
        }
        else if(status == EditMode.Status.YANK) {
            if(cursor < oldCursor)
                inputProcessor.getBuffer().pasteManager().addText(
                        Arrays.copyOfRange(inputProcessor.getBuffer().buffer().multiLine(), cursor, oldCursor));
            else if(cursor > oldCursor)
                inputProcessor.getBuffer().pasteManager().addText(
                        Arrays.copyOfRange(inputProcessor.getBuffer().buffer().multiLine(), oldCursor, cursor));
        }

        else if(status == EditMode.Status.UP_CASE) {
            if(cursor < oldCursor) {
                inputProcessor.getBuffer().addActionToUndoStack();
                for( int i = cursor; i < oldCursor; i++) {
                    inputProcessor.getBuffer().upCase();
                }
            }
            else {
                inputProcessor.getBuffer().addActionToUndoStack();
                for( int i = oldCursor; i < cursor; i++) {
                    inputProcessor.getBuffer().upCase();
                }
            }
            inputProcessor.getBuffer().moveCursor(cursor - oldCursor);
        }
        else if(status == EditMode.Status.DOWN_CASE) {
            if(cursor < oldCursor) {
                inputProcessor.getBuffer().addActionToUndoStack();
                for( int i = cursor; i < oldCursor; i++) {
                    inputProcessor.getBuffer().downCase();
                }
            }
            else {
                inputProcessor.getBuffer().addActionToUndoStack();
                for( int i = oldCursor; i < cursor; i++) {
                    inputProcessor.getBuffer().downCase();
                }
            }
            inputProcessor.getBuffer().moveCursor(cursor - oldCursor);
        }
        else if(status == EditMode.Status.CAPITALIZE) {
            String word = Parser.findWordClosestToCursor(inputProcessor.getBuffer().buffer().asString(),
                    oldCursor);
            if(word.length() > 0) {
                inputProcessor.getBuffer().addActionToUndoStack();
                int pos = inputProcessor.getBuffer().buffer().asString().indexOf(word,
                        oldCursor-word.length());
                if(pos < 0)
                    pos = 0;
                inputProcessor.getBuffer().upCase();

                inputProcessor.getBuffer().moveCursor(cursor - oldCursor);
            }
        }
    }


}
