package view;

import com.codepine.api.testrail.model.Case;
import com.codepine.api.testrail.model.CaseType;
import com.codepine.api.testrail.model.Field;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import model.section.OurSection;
import model.section.OurSectionInflator;
import model.testrail.RailClient;
import model.testrail.RailConnection;
import model.testrail.RailDataStorage;
import model.testrail.RailTestCase;
import model.treerenderer.TreeRenderer;
import utils.ClassScanner;
import utils.DraftClassesCreator;
import utils.GuiUtil;
import utils.ToolWindowData;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.out;
import static model.testrail.RailConstants.*;
import static utils.ComponentUtil.*;

public class TestRailWindow extends WindowPanelAbstract implements Disposable {
    private static final String HTML_CLOSE_TAG = "</html>";
    private static final String HTML_OPEN_TAG = "<html>";
    private static int ROOT_ID = -1;
    private Project project;
    private JPanel mainPanel;
    private JComboBox projectComboBox;
    private JComboBox suitesComboBox;
    private Tree sectionTree;
    private JLabel loadingLabel;
    private JLabel detailsLabel;
    private JPanel detailsPanel;
    private JComboBox customFieldsComboBox;
    private JLabel customFieldsLabel;
    private RailConnection connection;
    private RailClient client;
    private ToolWindowData data;
    private List<Case> casesFromSelectedPacks = new ArrayList<>();
    private List<RailClient.CaseFieldCustom> customProjectFieldsMap = new ArrayList<>();
    private JPopupMenu testCasePopupMenu;
    private DefaultMutableTreeNode currentSelectedTreeNode = null;
    private boolean isCtrlPressed = false;
    private List<Object> selectedTreeNodeList = new ArrayList<>();

    public TestRailWindow(Project project) {
        super(project);
        this.project = project;

        disableComponent(this.suitesComboBox);
        makeInvisible(this.detailsPanel);
        makeInvisible(sectionTree);
        connection = RailConnection.getInstance(project);
        setContent(mainPanel);
        sectionTree.setCellRenderer(new TreeRenderer());

        //Listeners
        setProjectSelectedItemListener();
        setSuiteSelectedItemListener();
        setSectionsTreeListener();
        setCustomFieldsComboBoxListener();
        initTestCasePopupMenu();
    }

    public static TestRailWindow getInstance(Project project) {
        return ServiceManager.getService(project, TestRailWindow.class);
    }

    public void setDefaultFields() {
        client = new RailClient(connection.getClient());
        this.projectComboBox.addItem("Select project...");
        client.getProjectList().forEach(var -> this.projectComboBox.addItem(var.getName()));
        makeInvisible(loadingLabel);
    }

    public Tree getSectionTree() {
        return sectionTree;
    }

    public void refreshSelectedFolder(AnActionEvent e) {
        e.getPresentation().setEnabled(false);
        sectionTree.setPaintBusy(true);
        TreePath[] paths = sectionTree.getSelectionPaths();
        if (null != paths && paths.length == 1) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) sectionTree.getLastSelectedPathComponent();
            //TODO add possibility to refresh folder and all folders inside
            if (node != null && node.getUserObject() instanceof OurSection) {
                OurSection section = (OurSection) node.getUserObject();

                List<Case> casesFromServer = client.getCases(data.getProjectId(), data.getSuiteId())
                        .stream()
                        .filter(caze -> caze.getSectionId() == section.getId())
                        .collect(Collectors.toList());

                if (!section.getCases().equals(casesFromServer)) {
                    //get section tree model
                    DefaultTreeModel sectionTreeModel = (DefaultTreeModel) sectionTree.getModel();
                    //get selected folder Node
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) sectionTree.getLastSelectedPathComponent();
                    //section children
                    List<Case> outCases = section.getCases();
                    outCases.clear();
                    Enumeration children = selectedNode.children();
                    Collections.list(children).stream().forEach(child -> {
                        DefaultMutableTreeNode s = (DefaultMutableTreeNode) child;
                        if (s.getUserObject() instanceof Case) {
                            selectedNode.remove(s);
                        }
                    });
                    IntStream.range(0, casesFromServer.size()).boxed().forEach(i -> {
                        selectedNode.insert(new DefaultMutableTreeNode(casesFromServer.get(i)), i);
                        outCases.add(casesFromServer.get(i));
                        section.setCases(outCases);
                    });
                    sectionTreeModel.nodeStructureChanged(selectedNode);
                    sectionTreeModel.reload(selectedNode);
                    sectionTree.setModel(sectionTreeModel);
                    repaintComponent(sectionTree);
                    //TODO END


//                    Enumeration s = selectedNode.children();
//
//
//                    Iterator sectionCasesIterator = section.getCases().iterator();
//
//                    while (sectionCasesIterator.hasNext()) {
//
//                        Case sectionCase = (Case) sectionCasesIterator.next();
//                        boolean caseExistsOnServer = casesFromServer.stream().anyMatch(serverCase -> serverCase.getId() == sectionCase.getId());
//
//                        if (caseExistsOnServer) {
//                            Case serverCaseToUpdate = casesFromServer.stream().filter(aCase -> aCase.getId() == sectionCase.getId()).findFirst().get();
//                            if (sectionCase != serverCaseToUpdate) {
//                                //Remove case
//                                removeNodeFromSection(section, selectedNode, s, sectionCase);
//                                //Rewrite case
//                                selectedNode.insert(new DefaultMutableTreeNode(sectionCase), section.getCases().size());
//                                List<Case> cases = section.getCases();
//                                cases.add(sectionCase);
//                                section.setCases(cases);
//                            }
//                        } else {
//                            removeNodeFromSection(section, selectedNode, s, sectionCase);
//                        }
//                    }
                }

            }
        }
        sectionTree.setPaintBusy(false);
        e.getPresentation().setEnabled(true);
    }

    //region Listeners

    @Override
    public void dispose() {
        ToolWindowManager.getInstance(project).unregisterToolWindow("WTM plugin");
    }

    @SuppressWarnings("unchecked")
    private void setProjectSelectedItemListener() {
        projectComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                disableComponent(this.suitesComboBox);
                GuiUtil.runInSeparateThread(() -> {
                    makeInvisible(sectionTree);
                    String selectedProject = (String) projectComboBox.getSelectedItem();
                    if (null != selectedProject && !selectedProject.equals("Select project...")) {
                        this.suitesComboBox.removeAllItems();
                        this.suitesComboBox.addItem("Select your suite...");
                        client.getSuitesList(selectedProject)
                                .forEach(suite -> this.suitesComboBox.addItem(suite.getName()));
                        enableComponent(this.suitesComboBox);
                    } else {
                        this.suitesComboBox.removeAllItems();
                    }
                });
            }
        });
    }

    private void setSectionsTreeListener() {
        addRightClickListenerToTree();

        sectionTree.addTreeSelectionListener(e -> {

            GuiUtil.runInSeparateThread(() -> {
                DefaultMutableTreeNode lastSelectedTreeNode = (DefaultMutableTreeNode) sectionTree.getLastSelectedPathComponent();

                selectedTreeNodeList.clear();
                if (null != sectionTree.getSelectionPaths()) {
                    for (TreePath path : sectionTree.getSelectionPaths()) {
                        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                        selectedTreeNodeList.add(userObject);
                    }
                }
                if (null != lastSelectedTreeNode && null != lastSelectedTreeNode.getUserObject() && !(lastSelectedTreeNode.getUserObject() instanceof OurSection)) {
                    //DO nothing here as selection is Test case
                    //TODO add logic here if selected test case
                } else {
                    customProjectFieldsMap = client.getCustomFieldNamesMap(data.getProjectId());

                    casesFromSelectedPacks = getCasesForSelectedTreeRows();

                    displayCaseTypesInfo();

                    GuiUtil.runInSeparateThread(() -> {
                        disableComponent(customFieldsComboBox);
                        makeVisible(loadingLabel);

                        customFieldsComboBox.removeAllItems();

                        if (!customProjectFieldsMap.isEmpty()) {
                            makeVisible(customFieldsComboBox);
                            customProjectFieldsMap.forEach(value -> customFieldsComboBox.addItem(value.getDisplayedName()));
                            repaintComponent(customFieldsLabel);
                        } else {
                            makeInvisible(customFieldsComboBox);
                            customFieldsLabel.setText("No defined custom fields found!");
                            repaintComponent(customFieldsLabel);
                        }
                        repaintComponent(detailsPanel);
                        makeInvisible(loadingLabel);
                        enableComponent(customFieldsComboBox);
                    });
                }
            });
        });

    }

    private void setSuiteSelectedItemListener() {
        suitesComboBox.addActionListener(e -> {
            //Set data to use in every other cases
            data = new ToolWindowData((String) this.suitesComboBox.getSelectedItem(), (String) projectComboBox.getSelectedItem(), client);
            String selectedSuite = (String) this.suitesComboBox.getSelectedItem();
            if (selectedSuite != null && !selectedSuite.equals("Select your suite...")) {

                GuiUtil.runInSeparateThread(() -> {
                    disableComponent(this.suitesComboBox);
                    disableComponent(this.projectComboBox);
                    makeVisible(this.loadingLabel);
                    makeInvisible(this.sectionTree);
                    makeInvisible(this.detailsPanel);

                    // Shows section tree.
                    showSectionTree(selectedSuite);

                    enableComponent(this.projectComboBox);
                    enableComponent(this.suitesComboBox);
                    makeVisible(this.sectionTree);
                    makeInvisible(this.loadingLabel);
                    makeVisible(this.sectionTree);
                });
            } else {
                makeInvisible(this.sectionTree);
            }
        });
    }

    private void setCustomFieldsComboBoxListener() {
        customFieldsComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                GuiUtil.runInSeparateThread(() -> {
                    String selectedValue = (String) this.customFieldsComboBox.getSelectedItem();
                    StringBuilder builder = new StringBuilder();
                    builder.append(HTML_OPEN_TAG);
                    customProjectFieldsMap
                            .stream()
                            .filter(caseField -> caseField.getDisplayedName().equals(selectedValue))
                            .collect(Collectors.toList())
                            .forEach(caseField -> caseField.getConfigs()
                                    .forEach(
                                            config -> {
                                                renderStats(builder, config);
                                            }));

                });

            }
        });
    }

    /**
     * Render stats in @customFieldsLabel depends on TYPE which is defined in getCustomFieldNamesMap method
     */
    private void renderStats(StringBuilder builder, Field.Config config) {
        try {
            ((Field.Config.DropdownOptions) config.getOptions()).getItems().forEach((key, value) -> {
                //filter cases by option
                List<Case> cases = casesFromSelectedPacks.stream()
                        .filter(caseField1 -> caseField1.getCustomFields().entrySet().stream()
                                .anyMatch(o -> o.getValue() != null && o.getValue().equals(key)))
                        .collect(Collectors.toList());

                builder.append(value + " : " + cases.size()).append("<br>");
            });
            builder.append(HTML_CLOSE_TAG);
            customFieldsLabel.setText(builder.toString());
            repaintComponent(customFieldsLabel);
        } catch (ClassCastException ex1) {
            try {
                ((Field.Config.MultiSelectOptions) config.getOptions()).getItems().forEach((key, value) -> {
                    List<Case> cases = casesFromSelectedPacks.stream()
                            .filter(caseField1 -> caseField1.getCustomFields().entrySet().stream()
                                    .anyMatch(o -> o.getValue() != null && o.getValue().equals(key)))
                            .collect(Collectors.toList());
                    builder.append(value).append(" : ").append(cases.size()).append("<br>");
                });
            } catch (ClassCastException ex2) {
                customFieldsLabel.setText("No Options!");
                repaintComponent(customFieldsLabel);
            }
            builder.append(HTML_CLOSE_TAG);
            customFieldsLabel.setText(builder.toString());
            repaintComponent(customFieldsLabel);
        }
    }

    // endregion

    // region Section tree

    public synchronized void createDraftClasses(AnActionEvent e) {
        GuiUtil.runInSeparateThread(() -> {
            e.getPresentation().setEnabled(false);
            makeVisible(loadingLabel);

            List<RailTestCase> railTestCases = casesFromSelectedPacks.stream()
                    .map(aCase -> new RailTestCase(aCase.getId(), client.getUserName(aCase.getCreatedBy()), aCase.getTitle(), aCase.getCustomField(STEPS_SEPARATED_FIELD), aCase.getCustomField(PRECONDITION_FIELD), aCase.getCustomField(KEYWORDS), client.getStoryNameBySectionId(data.getProjectId(), data.getSuiteId(), aCase.getSectionId())))
                    .collect(Collectors.toList());

            Collection<File> classList = ClassScanner.getInstance().getAllClassList(project);
            railTestCases.forEach(railTestCase -> {
                String railTestCaseName = DraftClassesCreator.getInstance(project).getClassNameForTestCase(railTestCase);
                List<File> fileList = classList.stream().filter(clazzName -> clazzName.getName().contains(railTestCaseName)).collect(Collectors.toList());
                if (fileList.size() == 0) {
                    DraftClassesCreator.getInstance(project).create(railTestCase, settings.getTemplate());
                }
            });

            StatusBar statusBar = WindowManager.getInstance()
                    .getStatusBar(project);

            JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder("<html>Draft classes created! <br>Please sync if not appeared</html>", MessageType.INFO, null)
                    .setFadeoutTime(7500)
                    .createBalloon()
                    .show(RelativePoint.getCenterOf(statusBar.getComponent()),
                            Balloon.Position.atLeft);

            makeInvisible(loadingLabel);
            e.getPresentation().setVisible(true);
        });
    }

    private void showSectionTree(String selectedSuite) {
        // Create root node.
        OurSection rootSection = new OurSection();
        rootSection.setId(ROOT_ID);
        rootSection.setName(selectedSuite);

        // Inflates root section.
        RailDataStorage railData = new RailDataStorage()
                .setCases(client.getCases(data.getProjectId(), data.getSuiteId()))
                .setSections(client.getSections(data.getProjectId(), data.getSuiteId()));
        OurSectionInflator.inflateOurSection(railData, null, rootSection);

        // Draw one node.
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootSection);
        // Draw tree.
        createTreeNode(rootSection, root);

        sectionTree.setModel(new DefaultTreeModel(root));
    }

    /**
     * Creates tree node for our section model of data.
     *
     * @param rootSection Our section model of data.
     * @param root        Tree node view.
     */
    private void createTreeNode(OurSection rootSection, DefaultMutableTreeNode root) {
        if (rootSection.getSectionList().isEmpty())
            return;

        for (OurSection ourSection : rootSection.getSectionList()) {
            DefaultMutableTreeNode subSection = new DefaultMutableTreeNode(ourSection);
            root.add(subSection);
            ourSection.getCases()
                    .forEach(testCase -> subSection.add(new DefaultMutableTreeNode(testCase)));
            createTreeNode(ourSection, subSection);
        }
    }

    private List<Case> getCasesForSelectedTreeRows() {
        TreePath[] paths = sectionTree.getSelectionPaths();
        List<Case> casesFromSelectedPacks = new ArrayList<>();

        if (null != paths) {

            for (TreePath path : paths) {
                Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();

                if (userObject instanceof OurSection) {
                    OurSection section = (OurSection) userObject;
                    //TODO here need to add all cases from current folder or(and) children folders
                    casesFromSelectedPacks.addAll(getCases(section));
                }
            }
            makeVisible(this.detailsPanel);
        } else {
            makeInvisible(detailsPanel);
        }
        return casesFromSelectedPacks;
    }

    private List<Case> getCases(OurSection section) {
        List<Case> cases = new ArrayList<>();
        for (OurSection section1 : section.getSectionList()) {
            cases.addAll(getCases(section1));
        }
        cases.addAll(section.getCases());
        return cases;
    }
    // endregion

    // region Test case Tree popup

    private void displayCaseTypesInfo() {
        List<CaseType> caseTypes = client.getCaseTypes();
        StringBuilder builder = new StringBuilder();
        for (CaseType type : caseTypes) {
            List<Case> casesWithOneType = casesFromSelectedPacks.stream()
                    .filter(aCase -> aCase.getTypeId() == type.getId())
                    .collect(Collectors.toList());
            builder.append(type.getName()).append(" : ").append(casesWithOneType.size()).append("<br>");

        }

        detailsLabel.setText(HTML_OPEN_TAG + builder.toString() + HTML_CLOSE_TAG);
        repaintComponent(detailsLabel);
    }

    private void initTestCasePopupMenu() {
        StringBuilder classCreationStatusMessages = new StringBuilder();
        testCasePopupMenu = new JPopupMenu();
        ActionListener menuListener = event -> {
            GuiUtil.runInSeparateThread(() -> {
                makeVisible(loadingLabel);

                out.println("Create draft classes button pressed " + selectedTreeNodeList.size());
                List<Case> caseList = selectedTreeNodeList
                        .stream()
                        .map(treeNode -> (Case) treeNode)
                        .collect(Collectors.toList());
                out.println("Case list " + caseList.size());

                List<RailTestCase> railTestCases = caseList.stream()
                        .map(aCase -> new RailTestCase(
                                aCase.getId(),
                                client.getUserName(aCase.getCreatedBy()),
                                aCase.getTitle(),
                                aCase.getCustomField(STEPS_SEPARATED_FIELD),
                                aCase.getCustomField(PRECONDITION_FIELD),
                                aCase.getCustomField(KEYWORDS),
                                client.getStoryNameBySectionId(data.getProjectId(), data.getSuiteId(), aCase.getSectionId())))
                        .collect(Collectors.toList());
                out.println("Rail Test case list " + caseList.size());

                Collection<File> classList = ClassScanner.getInstance().getAllClassList(project);
                railTestCases.forEach(railTestCase -> {
                    String railTestCaseName = DraftClassesCreator.getInstance(project).getClassNameForTestCase(railTestCase);
                    List<File> fileList = classList.stream()
                            .filter(clazzName -> clazzName.getName().contains(railTestCaseName))
                            .collect(Collectors.toList());

                    classCreationStatusMessages.append("Draft class " + railTestCase.getName());
                    if (fileList.size() == 0) {
                        out.println("Rail Test case with name " + railTestCase.getName() + " was created");
                        DraftClassesCreator.getInstance(project).create(railTestCase, settings.getTemplate());
                        classCreationStatusMessages.append(" was created<br>");
                    } else {
                        out.println("Rail Test case with name " + railTestCase.getName() + " was not created, due to class with same name is already existed");
                        classCreationStatusMessages.append(" was <b>NOT</b> created<br>");
                    }
                });

                showClassCreationStatusInBalloon(classCreationStatusMessages);

                makeInvisible(loadingLabel);
            });

            out.println("Popup menu item ["
                    + event.getActionCommand() + "] was pressed.");
        };
        JMenuItem item = new JMenuItem("Create draft class");
        item.setIcon(GuiUtil.loadIcon("draft.png"));
        item.addActionListener(menuListener);
        testCasePopupMenu.add(item);
    }

    private void addRightClickListenerToTree() {
        MouseListener mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                handleContextMenu(mouseEvent);
            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
                handleContextMenu(mouseEvent);
            }
        };

        sectionTree.addMouseListener(mouseListener);

        sectionTree.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {

            }

            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.VK_CONTROL) {
                    isCtrlPressed = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.VK_CONTROL) {
                    isCtrlPressed = false;
                }
            }
        });
    }

    private void handleContextMenu(MouseEvent mouseEvent) {
        if (mouseEvent.isPopupTrigger()) {
            TreePath pathForLocation = sectionTree.getPathForLocation(mouseEvent.getPoint().x, mouseEvent.getPoint().y);
            if (pathForLocation != null) {
                boolean isSection = false;
                for (Object item : selectedTreeNodeList) {
                    if (item instanceof OurSection) {
                        isSection = true;
                        break;
                    }
                }

                if (!isSection) {
                    currentSelectedTreeNode = (DefaultMutableTreeNode) pathForLocation.getLastPathComponent();
                    if (currentSelectedTreeNode.getUserObject() instanceof Case) {
                        testCasePopupMenu.show(mouseEvent.getComponent(),
                                mouseEvent.getX(),
                                mouseEvent.getY());
                    }
                }
            }
        }
    }

    public void openSelectedTestCaseInBrowser() {
        Case selectedCase = (Case) ((DefaultMutableTreeNode) getSectionTree().getLastSelectedPathComponent()).getUserObject();

        try {
            Desktop.getDesktop().browse(new URI(settings.getRailUrl() + TEST_CASE_URL_PART + selectedCase.getId()));
        } catch (IOException | URISyntaxException e) {
            out.println("Unable to open, url is incorrect");
        }
    }

    private void showClassCreationStatusInBalloon(StringBuilder balloonClassCreationStatusMsg) {
        balloonClassCreationStatusMsg.insert(0, "<HTML>");
        balloonClassCreationStatusMsg.append("</HTML>");

        StatusBar statusBar = WindowManager.getInstance()
                .getStatusBar(project);

        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(balloonClassCreationStatusMsg.toString(), MessageType.INFO, null)
                .setFadeoutTime(10000)
                .createBalloon()
                .show(RelativePoint.getCenterOf(statusBar.getComponent()),
                        Balloon.Position.atLeft);

        balloonClassCreationStatusMsg.delete(0, balloonClassCreationStatusMsg.length());
    }
    // endregion
}
