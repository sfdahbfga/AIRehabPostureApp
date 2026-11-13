package com.example.airehabpostureapp;

import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.workout.PendingWorkoutStore;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * M10: 로그인 토큰 / 건강 정보가 담긴 SharedPreferences가 백업과 기기 이전에서 제외되는지
 * (코드의 파일 이름과 백업 규칙의 이름이 어긋나지 않는지) 확인
 */
public class BackupRulesTest {

    private static final String[] SENSITIVE_PREFS = {
            SessionManager.PREFS_NAME + ".xml",
            SessionManager.EVENTS_PREFS_NAME + ".xml",
            PendingWorkoutStore.PREFS_NAME + ".xml"
    };

    // 단위 테스트의 작업 폴더는 app 모듈 폴더
    private static Element parse(String fileName) throws Exception {
        File file = new File("src/main/res/xml/" + fileName);
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        return document.getDocumentElement();
    }

    private static Set<String> excludedSharedPrefs(Element parent) {
        Set<String> paths = new HashSet<>();
        NodeList excludes = parent.getElementsByTagName("exclude");
        for (int i = 0; i < excludes.getLength(); i++) {
            Element exclude = (Element) excludes.item(i);
            if ("sharedpref".equals(exclude.getAttribute("domain"))) {
                paths.add(exclude.getAttribute("path"));
            }
        }
        return paths;
    }

    private static void assertAllExcluded(String where, Set<String> excluded) {
        for (String prefs : SENSITIVE_PREFS) {
            assertTrue(where + "에서 " + prefs + "가 제외되지 않음", excluded.contains(prefs));
        }
    }

    @Test
    public void dataExtractionRules_excludeFromCloudBackupAndDeviceTransfer() throws Exception {
        Element root = parse("data_extraction_rules.xml");
        assertAllExcluded("cloud-backup",
                excludedSharedPrefs((Element) root.getElementsByTagName("cloud-backup").item(0)));
        assertAllExcluded("device-transfer",
                excludedSharedPrefs((Element) root.getElementsByTagName("device-transfer").item(0)));
    }

    @Test
    public void legacyBackupRules_exclude() throws Exception {
        assertAllExcluded("backup_rules", excludedSharedPrefs(parse("backup_rules.xml")));
    }
}
