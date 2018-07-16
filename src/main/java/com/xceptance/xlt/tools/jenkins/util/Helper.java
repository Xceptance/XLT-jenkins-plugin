package com.xceptance.xlt.tools.jenkins.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.xceptance.xlt.tools.jenkins.BuildNodeGoneException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public final class Helper
{

    private Helper()
    {
    }

    public static class FOLDER_NAMES
    {
        public static String ARTIFACT_REPORT = "report";

        public static String ARTIFACT_DIFFREPORT = "diffReport";

        public static String ARTIFACT_RESULT = "results";
    }

    /**
     * The plug-in's name, i.e. the name of the HPI file.
     */
    public static final String PLUGIN_NAME = "xlt-jenkins-plugin";

    private static PluginWrapper plugin()
    {
        return Jenkins.getInstance().getPlugin(PLUGIN_NAME).getWrapper();
    }

    public static String getResourcePath(String fileName)
    {
        return "/plugin/" + plugin().getShortName() + "/" + fileName;
    }

    public static URI getBaseResourceURI() throws URISyntaxException
    {
        return plugin().baseResourceURL.toURI();
    }

    public static boolean isBuiltOnMaster(final Run<?, ?> run)
    {
        if (run != null && run instanceof AbstractBuild)
        {
            final Jenkins j = Jenkins.getInstance();
            return j != null && j == ((AbstractBuild<?, ?>) run).getBuiltOn();
        }
        return false;
    }

    public static hudson.model.Node getBuildNodeIfOnlineOrFail(Launcher launcher) throws BuildNodeGoneException
    {
        hudson.model.Node node = getBuildNode(launcher);
        if (node != null)
        {
            return node;
        }
        throw new BuildNodeGoneException("Build node is not available");
    }

    public static hudson.model.Node getBuildNode(Launcher launcher)
    {
        hudson.model.Node node = null;
        for (final Computer c : Jenkins.getActiveInstance().getComputers())
        {
            if (c.getChannel() == launcher.getChannel() && c.isOnline())
            {
                node = c.getNode();
                break;
            }
        }

        return node;
    }

    public static boolean isRelativeFilePathOnNode(Launcher launcher, String filePath) throws BuildNodeGoneException
    {
        if (launcher.isUnix() && (filePath.startsWith("/") || filePath.startsWith("~")))
        {
            return false;
        }
        else if (filePath.startsWith("\\") || filePath.contains(":"))
        {
            return false;
        }
        return true;
    }

    public static FilePath getArtifact(Run<?, ?> build, String artifactPath)
    {
        return new FilePath(new File(new File(build.getArtifactManager().root().toURI()), artifactPath));
    }

    public static String getErrorMessage(Throwable throwable)
    {
        if (throwable.getMessage() != null)
        {
            return throwable.getMessage();
        }
        else if (throwable.getCause() != null)
        {
            return getErrorMessage(throwable.getCause());
        }
        else
        {
            return "";
        }
    }

    public static String environmentResolve(String value)
    {
        if (StringUtils.isBlank(value))
            return value;

        return Util.replaceMacro(value, System.getenv());
    }

    public static FilePath resolvePath(final String path)
    {
        return resolvePath(path, Jenkins.getInstance().getRootPath());
    }

    public static FilePath resolvePath(final String path, final FilePath baseDir)
    {
        final String dir = environmentResolve(path);
        if (StringUtils.isBlank(dir))
        {
            return null;
        }

        final File file = new File(dir);
        FilePath filePath = new FilePath(file);
        if (!file.isAbsolute() && baseDir != null)
        {
            filePath = new FilePath(baseDir, dir);
        }
        return filePath;
    }

    public static void moveFolder(FilePath srcFolder, FilePath destFolder) throws IOException, InterruptedException
    {
        if (srcFolder != null && srcFolder.isDirectory() && destFolder != null)
        {
            // move folder to save time
            srcFolder.copyRecursiveTo(destFolder);
            srcFolder.deleteRecursive();
        }
    }

    public static int executeCommand(Launcher launcher, FilePath workingDirectory, List<String> commandLine, TaskListener logger)
        throws IOException, InterruptedException
    {

        final ProcStarter starter = launcher.launch();
        starter.pwd(workingDirectory);
        starter.cmds(commandLine);

        starter.stdout(logger);

        // starts process and waits for its completion
        return starter.join();
    }

    public static int executeCommand(Launcher launcher, FilePath workingDirectory, List<String> commandLine, OutputStream out)
        throws IOException, InterruptedException
    {
        final ProcStarter starter = launcher.launch();
        starter.pwd(workingDirectory);
        starter.cmds(commandLine);

        starter.stdout(out);

        // starts process and waits for its completion
        return starter.join();
    }

    public static int executeCommand(final hudson.model.Node node, final FilePath workingDirectory, final List<String> commandLine,
                                     final TaskListener logger)
        throws IOException, InterruptedException
    {
        final Launcher launcher = node.createLauncher(logger);
        launcher.decorateFor(node);

        return executeCommand(launcher, workingDirectory, commandLine, logger);
    }

    public static List<Run<?, ?>> getRuns(final Run<?, ?> currentRun, final int startFrom, final int count)
    {
        final Job<?, ?> job = currentRun.getParent();

        final List<Run<?, ?>> allBuilds = new ArrayList<Run<?, ?>>(job.getBuilds());
        final int maxBuilds = allBuilds.size();
        int to = Math.min(startFrom + count, maxBuilds);
        if (to < 0 || to > maxBuilds)
        {
            to = maxBuilds;
        }

        return allBuilds.subList(startFrom, to);
    }

}
