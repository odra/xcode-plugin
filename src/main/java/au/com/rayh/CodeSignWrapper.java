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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by lrossett on 7/3/17.
 */
public class CodeSignWrapper extends Builder implements SimpleBuildStep {
    private static final String SIGNATURE_FILE = "_CodeSignature";
    private static final String PROFILE_FILE = "embedded.mobileprovision";
    public String appPath;
    public String keychainName;
    private FilePath binaryPath;
    private FilePath resourcesPath;

    CodeSignWrapper(String appPath, String keychainName, FilePath resourcesPath) {
        this.appPath = appPath;
        this.keychainName = keychainName;
        this.resourcesPath = resourcesPath;
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
        FilePath[] folders = this.resourcesPath.list("*.mobileprovision");

        if (folders.length == 0) {
            throw  new AbortException("Provisioning profile not found");
        }

        FilePath profilePath = folders[0];

        profilePath.copyTo(binaryPath.child(PROFILE_FILE));
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public void setEntitlements(Launcher launcher, FilePath projectRoot) throws  IOException, InterruptedException {
        ByteArrayOutputStream plistStdout = new ByteArrayOutputStream();
        ByteArrayOutputStream plistSdterr = new ByteArrayOutputStream();
        ByteArrayOutputStream entitlementsStdout = new ByteArrayOutputStream();

        launcher
                .launch()
                .cmds("security", "cms", "-k", this.keychainName, "-D", "-i", binaryPath.child(PROFILE_FILE).getRemote())
                .stderr(plistSdterr)
                .stdout(plistStdout)
                .join();
        projectRoot.child("temp.plist").write(plistStdout.toString(), "utf-8");

        launcher
                .launch()
                .cmds("/usr/libexec/PlistBuddy", "-x", "-c", "Print :Entitlements", projectRoot.child("temp.plist").getRemote())
                .stdout(entitlementsStdout)
                .join();
        projectRoot.child("entitlements.plist").write(entitlementsStdout.toString(), "utf-8");
    }

    public void sign(Launcher launcher, TaskListener listener, String config, String entitlements, String target) throws IOException, InterruptedException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ArgumentListBuilder args = new ArgumentListBuilder("codesign", "-v", "-f");

        args.add("-s").add(config);
        args.add("--keychain", this.keychainName);

        if (entitlements != null) {
            args.add("--entitlements").add(entitlements);
        }

        args.add(target);

        launcher
                .launch()
                .cmds(args)
                .stdout(stdout)
                .stderr(stderr)
                .join();
        if (stderr.size() > 0) {
            listener.getLogger().write(stderr.toByteArray());
        } else {
            listener.getLogger().write(stdout.toByteArray());
        }
    }

    public void sign(Launcher launcher, TaskListener listener, String config, String target) throws IOException, InterruptedException {
        this.sign(launcher, listener, config, null, target);
    }

    public void showValidIdentities(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        launcher
                .launch()
                .cmds("security", "find-identity", "-p", "codesigning", "-v", this.keychainName)
                .stdout(out)
                .join();
        listener.getLogger().write(out.toByteArray());
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public String getIdentity(Launcher launcher) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        launcher
                .launch()
                .cmds("security", "find-identity", "-p", "codesigning", "-v", this.keychainName, "|", "perl", "-nle", "print $1 if m{^\\d\\)\\s+([a-zA-Z0-9]+)\\s+}")
                .stdout(out)
                .join();

        for (String part : out.toString().split("\\n")) {
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        launcher
                .launch()
                .cmds("codesign", "--verify", "--deep", "--strict", "--verbose=2", target)
                .stdout(out)
                .stderr(err)
                .join();
        if (err.size() > 0) {
            listener.getLogger().write(err.toByteArray());
        } else {
            listener.getLogger().write(out.toByteArray());
        }
    }

    public void createIpa(Launcher launcher, TaskListener listener, String dest) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        FilePath payloadFolder = this.binaryPath.getParent().child("Payload");

        if (payloadFolder.exists()) {
            payloadFolder.deleteRecursive();
        }

        payloadFolder.mkdirs();
        this.binaryPath.copyRecursiveTo(payloadFolder);

        launcher
                .launch()
                .pwd(this.binaryPath.getParent())
                .cmds("zip", "-r", dest + ".ipa", payloadFolder.getName())
                .stdout(out)
                .stderr(err)
                .join();
        if (err.size() > 0) {
            listener.getLogger().write(err.toByteArray());
        } else {
            listener.getLogger().write(out.toByteArray());
        }
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    private boolean _perform(Run<?,?> build, FilePath projectRoot, Launcher launcher, EnvVars envs, TaskListener listener) throws InterruptedException, IOException {
        System.out.println(this.keychainName + this.appPath);

        String path = this.appPath;

        if (path == null) {
            return false;
        }

        FilePath binaryPath = projectRoot.child(path);

        if (!binaryPath.exists()) {
            throw new AbortException("Could not find binary at path: " + path);
        }

        this.binaryPath = binaryPath;

        this.clean();
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

        this.sign(launcher, listener, identifier,  projectRoot.child("entitlements.plist").getRemote(), binaryPath.getRemote());

        this.checkSignature(launcher, listener, binaryPath.getRemote());

        this.createIpa(launcher, listener, this.binaryPath.getRemote().replace(".app", ""));

        return true;
    }
}
