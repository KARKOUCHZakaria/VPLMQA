package com.figma.design.service;

import com.figma.design.util.FileNameSanitizer;
import org.springframework.stereotype.Service;

@Service
public class DesignStoragePathService {

    public String designPath(String projectName) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        return sanitizedProject + "/design.json";
    }

    public String pageJsonPath(String projectName, String pageName) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        return sanitizedProject + "/pages/" + sanitizedPage + "/page.json";
    }

    public String figmaComponentPath(String projectName, String pageName, String componentName, int occurrence) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        String sanitizedComponent = FileNameSanitizer.sanitize(componentName);
        return sanitizedProject + "/pages/" + sanitizedPage + "/components/" + sanitizedComponent + "/"
                + sanitizedComponent + "-" + occurrence + ".json";
    }

    public String webDesignPath(String projectName, String pageName) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        return sanitizedProject + "/web-pages/" + sanitizedPage + "/design.json";
    }

    public String webScreenshotPath(String projectName, String pageName) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        return sanitizedProject + "/web-pages/" + sanitizedPage + "/screenshot.png";
    }

    public String webComponentPath(String projectName, String pageName, String componentName, int occurrence) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        String sanitizedComponent = FileNameSanitizer.sanitize(componentName);
        return sanitizedProject + "/web-pages/" + sanitizedPage + "/components/" + sanitizedComponent + "/"
                + sanitizedComponent + "-" + occurrence + ".json";
    }

    public String designTokenComparisonJsonPath(String projectName, String pageName) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        return sanitizedProject + "/web-pages/" + sanitizedPage + "/ml/design-token-comparison.json";
    }

    public String designTokenComparisonCsvPath(String projectName, String pageName) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        return sanitizedProject + "/web-pages/" + sanitizedPage + "/ml/design-token-comparison.csv";
    }

    public String pageExportPath(String projectName, String pageName, String extension) {
        String sanitizedProject = FileNameSanitizer.sanitize(projectName);
        String sanitizedPage = FileNameSanitizer.sanitize(pageName);
        return sanitizedProject + "/pages/" + sanitizedPage + "/page-export." + extension;
    }
}
