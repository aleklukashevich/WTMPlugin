package model.testrail;

import com.codepine.api.testrail.TestRail;
import com.codepine.api.testrail.model.*;
import com.intellij.openapi.components.ServiceManager;
import exceptions.AuthorizationException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Storage for rail data. The storage use TestRail client for getting data from TestRail.
 */
public final class RailDataStorage implements Login {
    private List<Section> sections;
    private List<Case> cases;
    private TestRail testRailClient;
    private boolean isLoggedIn = false;

    private static List<User> userList = new ArrayList<>();
    private static RailDataStorage instance = null;

    private RailDataStorage() {
    }

    public static RailDataStorage getInstance() {
        if (instance == null) {
            instance = new RailDataStorage();
        }

        return instance;
    }

    // region sections and cases

    public RailDataStorage setSections(List<Section> sections) {
        this.sections = sections;
        return this;
    }

    public RailDataStorage setCases(List<Case> cases) {
        this.cases = cases;
        return this;
    }

    public List<Section> getSections() {
        return sections;
    }

    public List<Case> getCases() {
        return cases;
    }

    // endregion

    public final Integer getProjectIdByProjectName(String projectName) {
        return getProjectList()
                .stream()
                .filter(projectItem -> projectItem.getName().equals(projectName))
                .map(Project::getId)
                .findFirst().orElse(null);
    }

    public final Integer getSuiteIdBySuiteName(String projectName, String suiteName) {
        return getSuitesList(projectName)
                .stream()
                .filter(suite -> suite.getName().equals(suiteName))
                .map(Suite::getId)
                .findFirst().orElse(null);
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public static RailDataStorage getInstance(com.intellij.openapi.project.Project project) {
        return ServiceManager.getService(project, RailDataStorage.class);
    }

    public List<Project> getProjectList() {
        return testRailClient.projects().list().execute();
    }

    @SuppressWarnings("ConstantConditions")
    public List<Suite> getSuitesList(String projectName) {
        int projectId = testRailClient.projects().list().execute().stream()
                .filter(projectItem -> projectItem.getName().equals(projectName))
                .map(com.codepine.api.testrail.model.Project::getId)
                .findFirst().get();

        return testRailClient.suites().list(projectId).execute();
    }

    public List<CaseField> getCaseFields() {
        return testRailClient.caseFields().list().execute();
    }

    public List<ResultField> resultFields() {
        return testRailClient.resultFields().list().execute();
    }

    public List<CaseType> getCaseTypes() {
        return testRailClient.caseTypes().list().execute();
    }

    public List<Case> getCasesBySuiteId(Integer projectId, Integer suiteId) {
        List<CaseField> caseFieldList = testRailClient.caseFields().list().execute();
        return testRailClient.cases().list(projectId, suiteId, caseFieldList).execute();
    }

    //TODO check if this fields created for all projects or for just one
    public Set<String> getCustomFields(int projectId, int suiteId) {
        Set<String> customFields = new HashSet<>();
        List<CaseField> caseFieldList = testRailClient.caseFields().list().execute();
        testRailClient.cases().list(projectId, suiteId, caseFieldList).execute()
                .forEach(aCase -> customFields.addAll(aCase.getCustomFields().keySet()));
        return customFields;
    }

    public List<Section> getSections(String projectName, String suiteName) {
        int projectId = getProjectIdByProjectName(projectName);
        int suiteId = getSuiteIdBySuiteName(projectName, suiteName);
        return testRailClient.sections().list(projectId, suiteId).execute();
    }

    @SuppressWarnings("ConstantConditions")
    public String getStoryNameBySectionId(String projectName, String suiteName, int sectionId) {
        return getSections(projectName, suiteName)
                .stream()
                .filter(section -> section.getId() == sectionId)
                .map(Section::getName)
                .collect(Collectors.toList())
                .get(0);
    }

    public String getUserName(int userId) {
        return getUsers()
                .stream()
                .filter(user -> user.getId() == userId)
                .map(com.codepine.api.testrail.model.User::getName)
                .collect(Collectors.toList())
                .get(0);
    }

    //probably it's better to return caseFields as id to handle it later
    public List<TestCaseField> getCustomFieldNamesList(int projectID) {
        List<TestCaseField> testCaseFields = new ArrayList<>();
        testRailClient.caseFields().list().execute().stream()
                .filter(caseField ->
                        caseField.getConfigs()
                                .stream()
                                .filter(config -> config.getContext().getProjectIds() != null)
                                .filter(config ->
                                        config.getOptions().getClass().isAssignableFrom(Field.Config.DropdownOptions.class)
                                                || config.getOptions().getClass().isAssignableFrom(Field.Config.MultiSelectOptions.class))
                                .anyMatch(config -> config.getContext().getProjectIds().contains(projectID)))
                .forEach(caseField -> {
                    List<Field.Config> clearConfigs = caseField.getConfigs().stream().filter(config -> config.getContext().getProjectIds().contains(projectID)).collect(Collectors.toList());
                    testCaseFields.add(new TestCaseField(caseField.getId(), caseField.getSystemName(), caseField.getLabel(), clearConfigs));
                });
        return testCaseFields;
    }

    @Override
    public void login(settings.User data) throws AuthorizationException {
        try {
            testRailClient = TestRail.builder(data.getURL(), data.getUserName(), data.getUserPassword()).build();
            testRailClient.projects().list().execute();
            isLoggedIn = true;
        } catch (Exception e) {
            isLoggedIn = false;
            throw new AuthorizationException("Unable to login due to invalid login data or url");
        }
    }

    /**
     * Test case field model.
     */
    public class TestCaseField {
        private int id;
        private String systemName;
        private String displayedName;
        private List<Field.Config> configs;

        public TestCaseField(int id, String systemName, String displayedName, List<Field.Config> configs) {
            this.id = id;
            this.systemName = systemName;
            this.displayedName = displayedName;
            this.configs = configs;
        }

        public List<Field.Config> getConfigs() {
            return configs;
        }

        public int getId() {
            return id;
        }

        public String getSystemName() {
            return systemName;
        }

        public String getDisplayedName() {
            return displayedName;
        }
    }

    private List<User> getUsers() {
        if (userList.isEmpty()) {
            userList = testRailClient.users().list().execute();
            return userList;
        } else {
            return userList;
        }
    }
}
