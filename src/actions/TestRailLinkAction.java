package actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import settings.WTMSettings;
import utils.GuiUtil;

import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.stream.Collectors;

import static com.intellij.ui.tabs.TabInfo.ICON;
import static java.lang.System.out;
import static model.testrail.RailConstants.TEST_CASE_URL_PART;

public class TestRailLinkAction extends AnAction {

    public TestRailLinkAction() {
        super("Open in browser", "Open selected test case in a browser", GuiUtil.loadIcon(ICON));
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        VirtualFile vf = anActionEvent.getData(PlatformDataKeys.VIRTUAL_FILE);
        PsiFile psiFile = PsiManager.getInstance(anActionEvent.getProject()).findFile(vf);
        PsiClass[] ann = ((PsiJavaFileImpl) psiFile).getClasses();
        String id = ann[0].getName().split("_")[0].substring(1);

        try {
            Desktop.getDesktop().browse(new URI(WTMSettings.getInstance(anActionEvent.getProject()).getURL() + TEST_CASE_URL_PART + id));
        } catch (IOException | URISyntaxException e) {
            out.println("Unable to open, url is incorrect");
        }
    }

    @Override
    public void update(AnActionEvent e) {
        VirtualFile vf = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        PsiFile psiFile = PsiManager.getInstance(e.getProject()).findFile(vf);
        PsiClass[] ann = ((PsiJavaFileImpl) psiFile).getClasses();
        PsiMethod cMethod = Arrays.stream(ann[0].getMethods()).filter(psiMethod -> psiMethod.getName().startsWith("test_")).collect(Collectors.toList()).get(0);
        if (!Arrays.stream(cMethod.getAnnotations()).anyMatch(an -> an.getQualifiedName().equals("org.testng.annotations.Test"))) {
            e.getPresentation().setVisible(false);
            return;
        }
    }
}