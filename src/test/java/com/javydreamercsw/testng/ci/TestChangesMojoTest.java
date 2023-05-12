/*
 * Copyright 2023 Javier A. Ortiz Bultron javier.ortiz.78@gmail.com - All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
 */
package com.javydreamercsw.testng.ci;

import static org.mockito.ArgumentMatchers.anyLong;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Project;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@Slf4j
public abstract class TestChangesMojoTest extends AbstractMojoTestCase {

  protected static final PersonIdent gitIdentity =
      new PersonIdent("John Doe", "john.doe@example.org");
  protected File pom;
  protected Git jGit;
  protected File projectRoot;
  protected TestChangesMojo instance;
  protected GitLabApi api = Mockito.mock(GitLabApi.class);
  protected ProjectApi projectApi = Mockito.mock(ProjectApi.class);
  protected MergeRequestApi mergeRequestApi = Mockito.mock(MergeRequestApi.class);

  protected void copyDir(@NonNull String src, @NonNull String dest, boolean overwrite) {
    log.info(
        String.format("Copying '%s' into '%s'.%s", src, dest, (overwrite ? " (Overwrite)" : "")));
    try {
      Files.walk(Paths.get(src))
          .forEach(
              a -> {
                Path b = Paths.get(dest, a.toString().substring(src.length()));
                try {
                  if (!a.toString().equals(src)) {
                    Files.copy(
                        a,
                        b,
                        overwrite
                            ? new CopyOption[] {StandardCopyOption.REPLACE_EXISTING}
                            : new CopyOption[] {});
                  }
                } catch (IOException e) {
                  log.error(null, e);
                }
              });
    } catch (IOException e) {
      // permission issue
      log.error(null, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  @SneakyThrows
  protected void setUp() throws Exception {
    // Required
    super.setUp();
    // Create a maven project for testing
    projectRoot =
        new File(
            "target/project-to-test/" + getClass().getSimpleName() + System.currentTimeMillis());
    projectRoot.mkdirs();
    // Copy the test pom file
    copyDir("src/test/resources/unit/project-to-test", projectRoot.getAbsolutePath(), true);
    pom = new File(projectRoot, "/pom.xml");
    assertTrue(pom.exists());
    jGit = Git.init().setDirectory(projectRoot).call();
    jGit.add().addFilepattern(".").call();
    jGit.commit()
        .setCommitter(gitIdentity)
        .setAuthor(gitIdentity)
        .setAll(true)
        .setMessage("First commit")
        .call();
    // Create a branch
    jGit.branchCreate().setName("release/1.0.0").call();
    // Switch to new branch
    jGit.checkout().setName("release/1.0.0").call();
    log.info(String.format("Test project created in: %s", projectRoot));

    api = Mockito.mock(GitLabApi.class);
    projectApi = Mockito.mock(ProjectApi.class);
    mergeRequestApi = Mockito.mock(MergeRequestApi.class);
    MavenSession session = getSession(pom);
    instance = (TestChangesMojo) lookupConfiguredMojo(session, newMojoExecution("test-changes"));
    assertNotNull(instance);
    instance.setGitLabApi(api);

    // Mock the GitLab API calls we need.
    Mockito.doAnswer(
            (Answer<ProjectApi>)
                (InvocationOnMock invocation) -> {
                  return projectApi;
                })
        .when(api)
        .getProjectApi();

    Mockito.doAnswer(
            (Answer<MergeRequestApi>)
                (InvocationOnMock invocation) -> {
                  return mergeRequestApi;
                })
        .when(api)
        .getMergeRequestApi();

    Mockito.doAnswer(
            (Answer<Project>)
                (InvocationOnMock invocation) -> {
                  Project p = new Project();
                  p.setId(invocation.getArgument(0));
                  return p;
                })
        .when(projectApi)
        .getProject(anyLong());

    Mockito.doAnswer(
            (Answer<List<MergeRequest>>)
                (InvocationOnMock invocation) -> {
                  List<MergeRequest> mrs = new ArrayList<>();
                  MergeRequest mr = new MergeRequest();
                  mr.setSourceBranch(instance.getCurrentBranch());
                  mr.setTargetBranch("master");
                  mrs.add(mr);
                  return mrs;
                })
        .when(mergeRequestApi)
        .getMergeRequests(anyLong());
  }

  /** {@inheritDoc} */
  @Override
  protected void tearDown() throws Exception {
    // Required
    super.tearDown();
    cleanup();
  }

  @SneakyThrows
  protected MavenSession getSession(@NonNull File pom) {
    Settings settings = getMavenSettings();
    if (settings.getLocalRepository() == null) {
      settings.setLocalRepository(RepositorySystem.defaultUserLocalRepository.getAbsolutePath());
    }
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setPom(pom);
    request.setLocalRepositoryPath(settings.getLocalRepository());
    MavenExecutionRequestPopulator populator =
        getContainer().lookup(MavenExecutionRequestPopulator.class);
    populator.populateDefaults(request);
    DefaultMaven maven = (DefaultMaven) getContainer().lookup(Maven.class);
    DefaultRepositorySystemSession repoSession =
        (DefaultRepositorySystemSession) maven.newRepositorySession(request);
    LocalRepository localRepository =
        new LocalRepository(request.getLocalRepository().getBasedir());
    SimpleLocalRepositoryManagerFactory factory = new SimpleLocalRepositoryManagerFactory();
    LocalRepositoryManager localRepositoryManager =
        factory.newInstance(repoSession, localRepository);
    repoSession.setLocalRepositoryManager(localRepositoryManager);
    ProjectBuildingRequest buildingRequest =
        request
            .getProjectBuildingRequest()
            .setRepositorySession(repoSession)
            .setResolveDependencies(true);
    ProjectBuilder projectBuilder = lookup(ProjectBuilder.class);
    MavenProject project = projectBuilder.build(pom, buildingRequest).getProject();
    // No inspection deprecation - wait on maven-plugin-testing-harness update
    MavenSession session =
        new MavenSession(getContainer(), repoSession, request, new DefaultMavenExecutionResult());
    session.setCurrentProject(project);
    session.setProjects(Collections.singletonList(project));
    Properties properties = System.getProperties();
    properties.put("gitLabServer", System.getProperty("gitLabServer"));
    properties.put("gitLabToken", System.getProperty("gitLabToken"));
    properties.put("gitLabProjectId", System.getProperty("gitLabProjectId"));
    request.setSystemProperties(properties);
    return session;
  }

  @SneakyThrows
  protected Settings getMavenSettings() {
    MavenSettingsBuilder mavenSettingsBuilder =
        (MavenSettingsBuilder) getContainer().lookup(MavenSettingsBuilder.ROLE);
    return mavenSettingsBuilder.buildSettings();
  }

  protected void cleanup() {
    if (projectRoot != null && projectRoot.exists()) {
      try {
        FileUtils.deleteDirectory(projectRoot);
      } catch (IOException ex) {
        log.error(null, ex);
      }
    }
  }
}
