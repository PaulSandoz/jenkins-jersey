package com.cloudbees.jersey;

import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import hudson.model.RootAction;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @author Paul Sandoz
 */
public class JerseyRootAction implements RootAction {

    private final String basePath;

    private final JenkinsJerseyContainer jjc;

    public JerseyRootAction(String basePath, Class... resources) {
        this.jjc = JenkinsJerseyContainer.create(basePath, new DefaultResourceConfig(resources));
        this.basePath = basePath;
    }

    public JerseyRootAction(String basePath, ResourceConfig rc) {
        this.jjc = JenkinsJerseyContainer.create(basePath, rc);
        this.basePath = basePath;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return basePath;
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        jjc.handle(req, rsp);
    }

}
