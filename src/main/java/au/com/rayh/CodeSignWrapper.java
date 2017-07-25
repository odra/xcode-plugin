package au.com.rayh;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import jenkins.tasks.SimpleBuildStep;


import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by lrossett on 7/3/17.
 */
public class CodeSignWrapper extends Builder implements SimpleBuildStep {
    private static final String SIGNATURE_FILE = "_CodeSignature";
    private static final String PROFILE_FILE = "embedded.mobileprovision";
    private static final String ENTITLEMENTS_PLIST_PATH = "entitlements.plist";
    public String appPath;
    public String keychainName;
    private FilePath binaryPath;
    private FilePath resourcesPath;
    private boolean shouldClean;
    private boolean shouldVerify;
    private String ipaName;
    private  LauncherUtility runner = new LauncherUtility();

    CodeSignWrapper(String appPath, String keychainName, FilePath resourcesPath, boolean shouldClean, boolean shouldVerify, String ipaName) {
        this.appPath = appPath;
        this.keychainName = keychainName;
        this.resourcesPath = resourcesPath;
        this.shouldClean = shouldClean;
        this.shouldVerify = shouldVerify;
        this.ipaName = ipaName;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath filePath, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        _perform(build, filePath, launcher, build.getEnvironment(listener), listener);
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        boolean res = _perform(build, build.getWorkspace(), launcher, build.getEnvironment(listener), listener);

        return  res;
    }

    public boolean hasCodeSignedApp() {
        try {
            return this.binaryPath.child(SIGNATURE_FILE).exists();
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public void cleanAppSignature() throws IOException, InterruptedException {
        this.binaryPath.child(SIGNATURE_FILE).delete();
    }

    public boolean hasCodeSignedFrameworks() {
        FilePath frameworkFolder = this.binaryPath.child("Frameworks");
        try {
            for (FilePath framework : frameworkFolder.list()) {
                if (framework.child(SIGNATURE_FILE).exists()) {
                    return true;
                }
            }
        } catch (IOException | InterruptedException e) {
            return false;
        }

        return false;
    }

    public void cleanFrameworksSignature() throws IOException, InterruptedException {
        FilePath frameworkFolder = this.binaryPath.child("Frameworks");
        for (FilePath framework : frameworkFolder.list()) {
            FilePath frameworkSignature = framework.child(SIGNATURE_FILE);
            if (frameworkSignature.exists()) {
                frameworkSignature.delete();
            }
        }
    }

    public boolean hasEmbeddedProfile() {
        try {
            return this.binaryPath.child(PROFILE_FILE).exists();
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public void cleanEmbeddedProfile() throws IOException, InterruptedException {
        this.binaryPath.child(PROFILE_FILE).delete();
    }

    public void clean() throws InterruptedException, IOException {
        if (this.hasCodeSignedApp()) {
            this.cleanAppSignature();
        }

        if (this.hasCodeSignedFrameworks()) {
            this.cleanFrameworksSignature();
        }

        if (this.hasEmbeddedProfile()) {
            this.cleanEmbeddedProfile();
        }
    }

    public void setEmbeddedProfile() throws IOException, InterruptedException {
        FilePath[] folders = this.resourcesPath.list("**/*.mobileprovision");

        if (folders.length == 0) {
            throw  new AbortException("Provisioning profile not found");
        }

        FilePath profilePath = folders[0];

        profilePath.copyTo(binaryPath.child(PROFILE_FILE));
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public void setEntitlements(Launcher launcher, FilePath projectRoot) throws  IOException, InterruptedException {
        String binaryPath = this.binaryPath.child(PROFILE_FILE).getRemote();
        FilePath plistPath = projectRoot.child("temp.plist");
        FilePath entitlementsPath = projectRoot.child(ENTITLEMENTS_PLIST_PATH);

        ArgumentListBuilder args = new ArgumentListBuilder("security", "cms", "-k", this.keychainName, "-D", "-i", binaryPath);
        LauncherUtility.LauncherResponse plistRes = runner.run(args, false);
        projectRoot.child("temp.plist").write(plistRes.getStdout().toString(), "utf-8");

        ArgumentListBuilder args2 = new ArgumentListBuilder("/usr/libexec/PlistBuddy", "-x", "-c", "Print :Entitlements", plistPath.getRemote());
        LauncherUtility.LauncherResponse entitlementsRes = runner.run(args2, false);
        entitlementsPath.write(entitlementsRes.getStdout().toString(), "utf-8");
    }

    public void sign(Launcher launcher, TaskListener listener, String config, String entitlements, String target) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder("codesign", "-v", "-f");

        args.add("-s").add(config);
        args.add("--keychain", this.keychainName);

        if (entitlements != null) {
            args.add("--entitlements").add(entitlements);
        }

        args.add(target);
        LauncherUtility.LauncherResponse res = runner.run(args, false);
        listener.getLogger().write(res.getStdout().toByteArray());
    }

    public void sign(Launcher launcher, TaskListener listener, String config, String target) throws IOException, InterruptedException {
        this.sign(launcher, listener, config, null, target);
    }

    public void showValidIdentities(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder("security", "find-identity", "-p", "codesigning", "-v", this.keychainName);
        LauncherUtility.LauncherResponse res = runner.run(args, false);
        listener.getLogger().write(res.getStdout().toByteArray());
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public String getIdentity(Launcher launcher) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder("security", "find-identity", "-p", "codesigning",
                "-v", this.keychainName);
        LauncherUtility.LauncherResponse res = runner.run(args, false);

        for (String part : res.getStdout().toString().split("\\n")) {
            String re = "\\s+\\d\\)\\s+([a-zA-Z0-9]+)\\s+";
            Pattern pattern = Pattern.compile(re);
            Matcher matcher = pattern.matcher(part);

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    public void checkSignature(Launcher launcher, TaskListener listener, String target) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder("codesign", "--verify",
                "--deep", "--strict", "--verbose=2", target);
        LauncherUtility.LauncherResponse res = runner.run(args, false);
        listener.getLogger().write(res.getStderr().toByteArray());
    }

    public void showAppInfo(Launcher launcher, TaskListener listener, String target) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder("codesign", "-vvvv", "-d", target);
        LauncherUtility.LauncherResponse res = runner.run(args, false);
        listener.getLogger().write(res.getStderr().toByteArray());
    }

    public void createIpa(Launcher launcher, TaskListener listener, String dest) throws IOException, InterruptedException {
        FilePath payloadFolder = this.binaryPath.getParent().child("Payload");
        FilePath appFolder = payloadFolder.child(this.binaryPath.getName());

        if (payloadFolder.exists()) {
            payloadFolder.deleteRecursive();
        }

        payloadFolder.mkdirs();
        appFolder.mkdirs();
        this.binaryPath.copyRecursiveTo(appFolder);

        ArgumentListBuilder args = new ArgumentListBuilder("zip", "-r", dest + ".ipa", payloadFolder.getName());
        this.runner.setPwd(this.binaryPath.getParent());
        LauncherUtility.LauncherResponse res = runner.run(args, false);
        listener.getLogger().write(res.getStdout().toByteArray());
        this.runner.setPwd(null);
    }

    public String getIpaName() {
        String remotePath = this.binaryPath.getRemote();

        if (this.ipaName != null) {
            return remotePath.replace(this.binaryPath.getName(), ipaName.replace(".ipa", ""));
        }

        return remotePath.replace(".app", "");
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    private boolean _perform(Run<?,?> build, FilePath projectRoot, Launcher launcher, EnvVars envs, TaskListener listener) throws InterruptedException, IOException {
        String path = this.appPath;

        if (path == null) {
            return false;
        }

        FilePath binaryPath = projectRoot.child(path.replace(" ", "\\ "));

        if (!binaryPath.exists()) {
            throw new AbortException("Could not find binary at path: " + path);
        }

        this.binaryPath = binaryPath;

        if (this.shouldClean) {
            this.clean();
        }

        this.runner.bootstrap(launcher, listener);
        this.runner.setEnv(envs);

        this.setEmbeddedProfile();
        this.setEntitlements(launcher, projectRoot);
        this.showValidIdentities(launcher, listener);

        String identifier = this.getIdentity(launcher);

        if (identifier == null) {
            throw new AbortException("Could not retrieve a valid codesign identity.");
        }

        FilePath frameworkFolder = this.binaryPath.child("Frameworks");
        for (FilePath framework : frameworkFolder.list()) {
            this.sign(launcher, listener, identifier, framework.getRemote());
        }

        String entitlementsPath = projectRoot.child(ENTITLEMENTS_PLIST_PATH).getRemote();
        this.sign(launcher, listener, identifier, entitlementsPath , binaryPath.getRemote());

        if (this.shouldVerify) {
            this.checkSignature(launcher, listener, binaryPath.getRemote());
            this.showAppInfo(launcher, listener, this.binaryPath.getRemote());
        }

        this.createIpa(launcher, listener, this.getIpaName());

        return true;
    }
}
