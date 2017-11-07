package au.com.rayh;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Created by lfitzgerald on 03/11/17.
 */
public class DeveloperProfileLoaderStep extends AbstractStepImpl{

    private String profileId;

    @DataBoundConstructor
    public DeveloperProfileLoaderStep() {}


    @DataBoundSetter
    public void setProfileId(String value) {
        this.profileId = value;
    }

    private static class DeveloperProfileLoaderExecution extends AbstractSynchronousNonBlockingStepExecution<Void>{
        private static final long serialVersionUID = 1L;

        @Inject
        private transient DeveloperProfileLoaderStep dpls;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient Run build;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient FilePath workspace;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient Launcher launcher;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient TaskListener listener;


        @Override
        protected Void run() throws Exception {

            String profileId = dpls.profileId;

            DeveloperProfileLoaderWrapper profileLoader = new DeveloperProfileLoaderWrapper(profileId);
            profileLoader.setProjectScope(false);
            profileLoader.perform(build, workspace, launcher, listener);

            profileLoader.unload(workspace, launcher, listener);

           return null;
        }
    }


    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        private static String DISPLAY_NAME = "LoadDeveloperProfile";
        private static String FN_NAME = "loadDeveloperProfile";

        public DescriptorImpl() {
            super(DeveloperProfileLoaderStep.DeveloperProfileLoaderExecution.class);
        }

        @Override
        public String getFunctionName() {
            return FN_NAME;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }
    }
}
