package view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import exceptions.AuthorizationException;
import model.testrail.RailDataStorage;
import settings.User;

import javax.swing.*;

import static settings.WTMSettings.DEFAULT_TEMPLATE;
import static utils.ComponentUtil.repaintComponent;

public class WTMSettingsWindow extends WindowPanelAbstract implements Disposable, User {
    private JPanel mainPanel;
    private JTextField railUrlTextField;
    private JPasswordField railPasswordField;
    private JTextField railUserNameTextField;
    private JButton railTestConnectionButton;
    private JTextPane railDebugTextPane;
    private JTextArea temlateTextArea;
    private JButton setDefaultTemplateButton;

    private final Project project;

    private WTMSettingsWindow(Project project) {
        super(project);
        this.project = project;
        railTestConnectionButtonClickedAction(project);
        setSetDefaultTemplateButtonListener();
        setContent(mainPanel);

    }

    public void setSettings() {
        settings.setPassword(railPasswordField.getPassword());
        settings.setUserName(railUserNameTextField.getText());
        settings.setURL(railUrlTextField.getText());
        settings.setTemplate(temlateTextArea.getText());
        try {
            RailDataStorage.getInstance(project).login(this);
        } catch (AuthorizationException e) {
            //DO nothing
        }
    }

    public static WTMSettingsWindow getInstance(Project project) {
        return ServiceManager.getService(project, WTMSettingsWindow.class);
    }

    @Override
    public void dispose() {
        ToolWindowManager.getInstance(project).unregisterToolWindow("WTM plugin");
    }

    public boolean isModified() {
        return !railUserNameTextField.getText().equals(settings.getUserName())
                || !String.valueOf(railPasswordField.getPassword()).equals(settings.getUserPassword())
                || !railUrlTextField.getText().equals(settings.getURL())
                || !temlateTextArea.getText().equals(settings.getTemplate());
    }

    public void reset() {
        railPasswordField.setText(settings.getUserPassword());
        railUserNameTextField.setText(settings.getUserName());
        railUrlTextField.setText(settings.getURL());
        temlateTextArea.setText(settings.getTemplate());
    }

    private void railTestConnectionButtonClickedAction(Project project) {
        railTestConnectionButton.addActionListener(listener ->
        {
            try {
                RailDataStorage.getInstance(project).login(this);
                railDebugTextPane.setText("Connected!");
            } catch (AuthorizationException e) {
                railDebugTextPane.setText(e.getMessage());
            }
        });
        repaintComponent(railDebugTextPane);
    }

    private void setSetDefaultTemplateButtonListener() {
        setDefaultTemplateButton.addActionListener(listener -> {
            temlateTextArea.setText(DEFAULT_TEMPLATE);
            repaintComponent(temlateTextArea);
        });
    }

    public String getUserName() {
        return railUserNameTextField.getText();
    }

    public String getUserPassword() {
        return String.valueOf(railPasswordField.getPassword());
    }

    public String getURL() {
        return railUrlTextField.getText();
    }
}
