package au.com.rayh;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

import java.io.IOException;

/**
 * Created by lrossett on 6/30/17.
 */

@SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
public class CodeSignBuilder extends Builder implements SimpleBuildStep {
    public String appPath;

    CodeSignBuilder(String appPath) {
        this.appPath = appPath;
    }

    public void cleanSignature(FilePath filePath, TaskListener listener) {
        try {
            for (FilePath f : filePath.child(appPath).list("**/_CodeSignature")) {
                f.delete();
            }
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Binary not codesigned, moving on...");
        }
    }

    public void cleanProvisioningProfile(FilePath filePath, TaskListener listener) {
        FilePath profile = filePath.child(this.appPath);

        try {
            if (profile.child("embedded.mobileprovision").exists()) {
                profile.child("embedded.mobileprovision").delete();
            }
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Embedded provisioning profile not found");
        }
    }

    @Override
    public void perform(Run<?, ?> build, FilePath filePath, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        _perform(build, filePath, launcher, build.getEnvironment(listener), listener);
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return _perform(build, build.getWorkspace(), launcher, build.getEnvironment(listener), listener);
    }

    private boolean _perform(Run<?, ?> build, FilePath projectRoot, Launcher launcher, EnvVars envs, TaskListener listener) throws InterruptedException, IOException {
        this.cleanProvisioningProfile(projectRoot, listener);
        this.cleanSignature(projectRoot, listener);
        return true;
    }
}