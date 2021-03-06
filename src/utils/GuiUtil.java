/*
 * Copyright (c) 2013 David Boissier
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

package utils;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.net.URL;

/**
 * The class uses as helper for work with UI.
 */
public final class GuiUtil {

    private static final String ICON_FOLDER = "/files/icons/";

    private GuiUtil() {
    }

    public static Icon loadIcon(String iconFilename) {
        return IconLoader.findIcon(ICON_FOLDER + iconFilename);
    }


    public static Icon loadIcon(String parentPath, String iconFilename) {
        return IconLoader.findIcon(parentPath + iconFilename);
    }

    public static void runInSwingThread(Runnable runnable) {
        Application application = ApplicationManager.getApplication();
        if (application.isDispatchThread()) {
            runnable.run();
        } else {
            application.executeOnPooledThread(runnable);
        }
    }

    public static void runInSwingThread(final Task.Backgroundable task){
        Application application = ApplicationManager.getApplication();
        if (application.isDispatchThread()) {
            task.queue();
        } else {
            application.executeOnPooledThread(new TaskRunner(task));
        }
    }

    public static void runInSeparateThread(Runnable runnable){
        ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }

    public static boolean isUnderDarcula() {//keep it for backward compatibility
        return UIManager.getLookAndFeel().getName().contains("Darcula");
    }

    public static URL getIconResource(String iconFilename) {
        return GuiUtil.class.getResource(ICON_FOLDER + iconFilename);
    }

    private static class TaskRunner implements Runnable{
        private final Task.Backgroundable task;

        public TaskRunner(Task.Backgroundable task) {
            this.task = task;
        }

        @Override
        public void run() {
            task.queue();
        }
    }

    /**
     * Method used to create action toolbar
     * */
    public static void installActionGroupInToolBar(ActionGroup actionGroup,
                                                   SimpleToolWindowPanel toolWindowPanel,
                                                   ActionManager actionManager, String toolBarName) {
        if (actionManager == null) {
            return;
        }

        JComponent actionToolbar = ActionManager.getInstance()
                .createActionToolbar(toolBarName, actionGroup, true).getComponent();
        toolWindowPanel.setToolbar(actionToolbar);
    }

    public static MouseListener installPopupHandler(JComponent component, @NotNull final ActionGroup group, final String place, final ActionManager actionManager) {
        if (ApplicationManager.getApplication() == null) {
            return new MouseAdapter() {
            };
        } else {
            PopupHandler popupHandler = new PopupHandler() {
                public void invokePopup(Component comp, int x, int y) {
                    ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(place, group);
                    popupMenu.getComponent().show(comp, x, y);
                }
            };
            component.addMouseListener(popupHandler);
            return popupHandler;
        }
    }
}
