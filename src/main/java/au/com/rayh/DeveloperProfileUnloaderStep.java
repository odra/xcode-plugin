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
 * Created by lfitzgerald on 07/11/17.
 */
public class DeveloperProfileUnloaderStep extends AbstractStepImpl {

    private String profileId;

    @DataBoundConstructor
    public DeveloperProfileUnloaderStep() {}


    @DataBoundSetter
    public void setProfileId(String value) {
        this.profileId = value;
    }

    private static class DeveloperProfileUnloaderExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient DeveloperProfileUnloaderStep dpls;

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
            profileLoader.unload(workspace, launcher, listener);


            return null;
        }
    }


    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        private static String DISPLAY_NAME = "UnloadDeveloperProfile";
        private static String FN_NAME = "unloadDeveloperProfile";

        public DescriptorImpl() {
            super(DeveloperProfileUnloaderStep.DeveloperProfileUnloaderExecution.class);
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
