package au.com.rayh;

import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Created by lrossett on 7/14/17.
 */
public class DeveloperProfileLoaderStep extends AbstractStepImpl {
    private String profileId;
    private String keychainPassword;
    private String keychainName;
    private boolean defaultKeychain = false;
    private boolean cleanup = false;


    public String getProfileId() {
        return profileId;
    }

    @DataBoundSetter
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getKeychainPassword() {
        return keychainPassword;
    }

    @DataBoundSetter
    public void setKeychainPassword(String keychainPassword) {
        this.keychainPassword = keychainPassword;
    }

    public String getKeychainName() {
        return keychainName;
    }

    @DataBoundSetter
    public void setKeychainName(String keychainName) {
        this.keychainName = keychainName;
    }


    public boolean getDefaultKeychain() {
        return defaultKeychain;
    }

    @DataBoundSetter
    public void setDefaultKeychain(boolean defaultKeychain) {
        this.defaultKeychain = defaultKeychain;
    }

    public boolean getCleanup () {
        return cleanup;
    }

    @DataBoundSetter
    public void setCleanup(boolean cleanup) {
        this.cleanup = cleanup;
    }

    @DataBoundConstructor
    public DeveloperProfileLoaderStep() {}

    private static class DeveloperProfileLoaderStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient DeveloperProfileLoaderStep step;

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

        @StepContextParameter
        private transient EnvVars env;

        @Override
        protected Void run() throws Exception {
            String profileId = step.profileId;
            String keychainPassword = step.keychainPassword;
            String keychainName = step.keychainName;
            boolean defaultKeychain = step.defaultKeychain;
            boolean cleanup = step.cleanup;

            if (keychainName == null) {
                String buildName = build.getFullDisplayName();
                keychainName = "jenkins-" + buildName.replace('/', '-');
            }

            DeveloperProfileLoader profileLoader = new DeveloperProfileLoader(profileId);
            profileLoader.setDefaultKeychain(defaultKeychain);
            profileLoader.setProjectScope(false);
            profileLoader.setKeychainPassword(keychainPassword);
            profileLoader.setKeychainName(keychainName);

            if (!cleanup) {
                profileLoader.perform(build, workspace, launcher, listener);
                return null;
            }

            profileLoader.unload(workspace, launcher, listener);

            return null;
        }
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        private static String DISPLAY_NAME = "developerProfileLoader";
        private static String FN_NAME = "developerProfileLoader";

        public DescriptorImpl() {
            super(DeveloperProfileLoaderStep.DeveloperProfileLoaderStepExecution.class);
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
