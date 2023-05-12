/*
 * Copyright 2023 Javier A. Ortiz Bultron javier.ortiz.78@gmail.com - All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
 */
package com.javydreamercsw.testng.ci;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Project;

public abstract class AbstractGitMojo extends AbstractMojo {
  /** Command line for Git executable. */
  private final Commandline cmdGit = new Commandline();

  /**
   * The path to the Git executable. Defaults to "git".
   *
   * @since 1.0.0
   */
  @Parameter(property = "gitExecutable")
  private String gitExecutable;

  /** Maven session. */
  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession mavenSession;

  /**
   * Whether to print commands output into the console.
   *
   * @since 1.0.0
   */
  @Parameter(property = "verbose", defaultValue = "false")
  protected boolean verbose = false;

  /** Success exit code. */
  public static final int SUCCESS_EXIT_CODE = 0;

  /**
   * Maven Project.
   *
   * @since 1.0.0
   */
  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  protected MavenProject project;

  /**
   * GitLab server URL.
   *
   * @since 1.0.0
   */
  @Parameter(property = "gitLabServer", required = true)
  private String gitLabServer;

  /**
   * GitLab API token.
   *
   * @since 1.0.0
   */
  @Parameter(property = "gitLabToken", required = true)
  private String gitLabToken;

  /**
   * GitLab Project id where to fetch MR details from.
   *
   * @since 1.0.0
   */
  @Parameter(property = "gitLabProjectId", required = true, defaultValue = "-1")
  private Long gitLabProjectId;

  private GitLabApi gitLabApi;

  /** Initializes command line executables. */
  private void initExecutables() {
    if (StringUtils.isBlank(cmdGit.getExecutable())) {
      if (StringUtils.isBlank(gitExecutable)) {
        gitExecutable = "git";
      }
      cmdGit.setExecutable(gitExecutable);
    }
  }

  protected void setGitLabApi(GitLabApi api) {
    this.gitLabApi = api;
  }

  private GitLabApi getGitLabApi() throws MojoFailureException {
    if (gitLabApi == null) {
      if (gitLabServer == null || gitLabToken == null || gitLabProjectId < 0) {
        throw new MojoFailureException(
            "Invalid GitLab configuration. Make sure to provide gitLabServer, gitLabProjectId and gitLabToken parameters.");
      }
      gitLabApi = new GitLabApi(gitLabServer, gitLabToken);
    }
    return gitLabApi;
  }

  protected String getTargetBranch()
      throws GitLabApiException, MojoFailureException, CommandLineException {
    final String branch = getCurrentBranch();
    if (verbose) {
      getLog().info(String.format("Looking for merge request(s) for '%s'.", branch));
    }
    Project p = getGitLabApi().getProjectApi().getProject(gitLabProjectId);
    for (MergeRequest mr : getGitLabApi().getMergeRequestApi().getMergeRequests(p.getId())) {
      if (mr.getSourceBranch().equals(branch)) {
        return mr.getTargetBranch();
      }
    }
    throw new MojoFailureException(
        String.format("Unable to find a merge request for this branch (%s)", branch));
  }

  protected List<String> getChangesFromTargetBranch()
      throws MojoFailureException, CommandLineException, GitLabApiException {
    final CommandResult commandResult =
        executeGitCommandExitCode("diff", "--name-only", getTargetBranch());
    if (commandResult.getExitCode() == SUCCESS_EXIT_CODE) {
      String output = commandResult.getOut().trim();
      return output.isEmpty() ? Collections.emptyList() : Arrays.asList(output.split("\n"));
    } else {
      throw new MojoFailureException(commandResult.getError());
    }
  }

  /**
   * Checks uncommitted changes.
   *
   * @throws MojoFailureException If there is some uncommitted files.
   * @throws CommandLineException If command line execution fails.
   */
  protected void checkUncommittedChanges() throws MojoFailureException, CommandLineException {
    getLog().info("Checking for uncommitted changes.");
    if (executeGitHasUncommitted()) {
      throw new MojoFailureException(
          "You have some uncommitted files. Commit or discard local changes in order to proceed.");
    }
  }

  protected String getCurrentBranch() throws CommandLineException, MojoFailureException {
    // git rev-parse --abbrev-ref HEAD
    final CommandResult commandResult =
        executeGitCommandExitCode("rev-parse", "--quiet", "--abbrev-ref", "HEAD");
    if (commandResult.getExitCode() == SUCCESS_EXIT_CODE) {
      return commandResult.getOut().replaceAll("\n", "");
    } else {
      throw new MojoFailureException(commandResult.getError());
    }
  }

  /**
   * Executes git commands to check for uncommitted changes.
   *
   * @return <code>true</code> when there are uncommitted changes, <code>false</code> otherwise.
   * @throws CommandLineException If command line execution fails.
   * @throws MojoFailureException If command line execution returns false code.
   */
  protected boolean executeGitHasUncommitted() throws MojoFailureException, CommandLineException {
    boolean uncommited = false;

    // 1 if there were differences and 0 means no differences

    // git diff --no-ext-diff --ignore-submodules --quiet --exit-code
    final CommandResult diffCommandResult =
        executeGitCommandExitCode(
            "diff", "--no-ext-diff", "--ignore-submodules", "--quiet", "--exit-code");

    String error = null;

    if (diffCommandResult.getExitCode() == SUCCESS_EXIT_CODE) {
      // git diff-index --cached --quiet --ignore-submodules HEAD --
      final CommandResult diffIndexCommandResult =
          executeGitCommandExitCode(
              "diff-index", "--cached", "--quiet", "--ignore-submodules", "HEAD", "--");
      if (diffIndexCommandResult.getExitCode() != SUCCESS_EXIT_CODE) {
        error = diffIndexCommandResult.getError();
        uncommited = true;
      }
    } else {
      error = diffCommandResult.getError();
      uncommited = true;
    }

    if (StringUtils.isNotBlank(error)) {
      throw new MojoFailureException(error);
    }

    return uncommited;
  }

  /**
   * Executes Maven command without failing on non successful exit code.
   *
   * @param args Maven command line arguments.
   * @return Command result.
   * @throws CommandLineException If command line execution fails.
   * @throws MojoFailureException Shouldn't happen, actually.
   */
  protected CommandResult executeMavenCommandExitCode(final String... args)
      throws CommandLineException, MojoFailureException {
    MavenCli cli = new MavenCli(new ClassWorld("maven", getClassLoader(this.project)));
    if (verbose) {
      getLog()
          .debug(
              String.format(
                  "Running command %s in %s",
                  "mvn " + StringUtils.join(args, " "), mavenSession.getExecutionRootDirectory()));
    }
    System.setProperty(
        "maven.multiModuleProjectDirectory", mavenSession.getExecutionRootDirectory());
    CommandPrintStream out = new CommandPrintStream(verbose);
    CommandPrintStream err = new CommandPrintStream(verbose);
    int result = cli.doMain(args, mavenSession.getExecutionRootDirectory(), out, err);
    return new CommandResult(result, "", "");
  }

  /**
   * Executes Git command without failing on non successful exit code.
   *
   * @param args Git command line arguments.
   * @return Command result.
   * @throws CommandLineException If command line execution fails.
   * @throws MojoFailureException Shouldn't happen, actually.
   */
  protected CommandResult executeGitCommandExitCode(final String... args)
      throws CommandLineException, MojoFailureException {
    return executeCommand(cmdGit, false, null, args);
  }

  /**
   * Executes command line.
   *
   * @param cmd Command line.
   * @param failOnError Whether to throw exception on NOT success exit code.
   * @param argStr Command line arguments as a string.
   * @param args Command line arguments.
   * @return {@link CommandResult} instance holding command exit code, output and error if any.
   * @throws CommandLineException If command line execution fails.
   * @throws MojoFailureException If <code>failOnError</code> is <code>true</code> and command exit
   *     code is NOT equals to 0.
   */
  protected CommandResult executeCommand(
      final Commandline cmd, final boolean failOnError, final String argStr, final String... args)
      throws CommandLineException, MojoFailureException {
    // initialize executables
    initExecutables();

    if (verbose) {
      getLog()
          .debug(
              String.format(
                  "Running command %s in %s",
                  cmd.getExecutable()
                      + " "
                      + StringUtils.join(args, " ")
                      + (argStr == null ? "" : " " + argStr),
                  mavenSession.getExecutionRootDirectory()));
    }

    cmd.clearArgs();
    cmd.addArguments(args);
    cmd.setWorkingDirectory(mavenSession.getExecutionRootDirectory());

    if (StringUtils.isNotBlank(argStr)) {
      cmd.createArg().setLine(argStr);
    }

    final StringBufferStreamConsumer out = new StringBufferStreamConsumer(verbose);

    final StringBufferStreamConsumer err = new StringBufferStreamConsumer(verbose);

    // execute
    final int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

    String errorStr = err.getOutput();
    String outStr = out.getOutput();

    if (failOnError && exitCode != SUCCESS_EXIT_CODE) {
      // Not all commands print errors to error stream
      if (StringUtils.isBlank(errorStr) && StringUtils.isNotBlank(outStr)) {
        errorStr = outStr;
      }

      throw new MojoFailureException(errorStr);
    }

    if (verbose && StringUtils.isNotBlank(errorStr)) {
      getLog().warn(errorStr);
    }

    return new CommandResult(exitCode, outStr, errorStr);
  }

  protected static class CommandResult {
    private final int exitCode;
    private final String out;
    private final String error;

    public CommandResult(final int exitCode, final String out, final String error) {
      this.exitCode = exitCode;
      this.out = out;
      this.error = error;
    }

    /**
     * @return the exitCode
     */
    public int getExitCode() {
      return exitCode;
    }

    /**
     * @return the out
     */
    public String getOut() {
      return out;
    }

    /**
     * @return the error
     */
    public String getError() {
      return error;
    }
  }

  public class CommandPrintStream extends PrintStream {
    public CommandPrintStream(boolean verbose) {
      super(new CommandOutputStream(verbose));
    }
  }

  public class CommandOutputStream extends OutputStream {
    private final boolean verbose;
    private final PrintStream out;

    public CommandOutputStream(boolean verbose) {
      this.verbose = verbose;
      out = System.out;
    }

    @Override
    public void write(int i) throws IOException {
      if (verbose) {
        out.write(i);
      }
    }
  }

  public abstract ClassLoader getClassLoader(MavenProject project);
}
