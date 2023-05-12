/*
 * Copyright 2023 Javier A. Ortiz Bultron javier.ortiz.78@gmail.com - All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
 */
package com.javydreamercsw.testng.ci;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.gitlab4j.api.GitLabApiException;

@Mojo(name = "test-changes", defaultPhase = LifecyclePhase.VERIFY)
public class TestChangesMojo extends AbstractGitMojo {
  private final String TEST_PATH = "src/test/java/";
  protected List<Class<?>> classesToTest = new ArrayList<>();

  protected List<Class<?>> getClassesToTest() {
    return Collections.unmodifiableList(classesToTest);
  }

  @Override
  @SneakyThrows
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (executeGitHasUncommitted()) {
      throw new MojoExecutionException("Uncommited changes detected!");
    } else {
      classesToTest.clear();
      try {
        List<String> changesFromTargetBranch = getChangesFromTargetBranch();
        if (changesFromTargetBranch.isEmpty()) {
          if (verbose) {
            getLog().info("No changes detected!");
          }
          return;
        } else if (verbose) {
          getLog()
              .info(
                  String.format("Detected changes in these files: '%s'.", changesFromTargetBranch));
        }
        // Compile project
        CommandResult installCommandResult =
            executeMavenCommandExitCode("install", "-DskipTests=true");
        if (installCommandResult.getExitCode() == SUCCESS_EXIT_CODE) {
          ClassLoader projectClassLoader = getClassLoader(this.project);
          for (String modifiedFile : changesFromTargetBranch) {
            if (modifiedFile.endsWith(".java") && modifiedFile.startsWith(TEST_PATH)) {
              String className =
                  modifiedFile
                      .substring(
                          modifiedFile.indexOf(TEST_PATH) + TEST_PATH.length(),
                          modifiedFile.lastIndexOf("."))
                      .replaceAll("/", "\\.");
              Class<?> loadedClass = projectClassLoader.loadClass(className);
              if (verbose) {
                getLog().debug(String.format("Loaded class '%s'!", loadedClass.toString()));
              }
              // Check if class is abstract
              if (!Modifier.isAbstract(loadedClass.getModifiers())) {
                addClassToTest(loadedClass);
              }

              // Mark all children as classes to test
              for (Package pkg : projectClassLoader.getDefinedPackages()) {
                String packageName = pkg.getName();
                InputStream stream =
                    projectClassLoader.getResourceAsStream(pkg.getName().replaceAll("[.]", "/"));
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

                List<Class> classesInPackage =
                    reader
                        .lines()
                        .filter(line -> line.endsWith(".class"))
                        .map(line -> getClass(line, packageName, projectClassLoader))
                        .collect(Collectors.toList());
                if (verbose) {
                  getLog().debug(String.format("Searching package %s!", packageName));
                }
                for (Class<?> c : classesInPackage) {
                  if (c != null) {
                    if (verbose) {
                      getLog()
                          .debug(
                              String.format(
                                  "Checking if %s is a child of %s!",
                                  c.toString(), loadedClass.toString()));
                    }
                    if (loadedClass.isAssignableFrom(c)
                        && !c.equals(loadedClass)
                        && !classesToTest.contains(c)) {
                      addClassToTest(c, loadedClass);
                    }
                  }
                }
              }
            }
          }
        } else {
          getLog().error("Error compiling project!");
        }
        if (!classesToTest.isEmpty()) {
          // Run the tests next
          CommandResult testCommandResult =
              executeMavenCommandExitCode(
                  "test",
                  "-DskipTests=false",
                  "-Dtest="
                      + classesToTest.stream()
                          .map(c -> c.getCanonicalName())
                          .collect(Collectors.joining(",")));
          if (testCommandResult.getExitCode() == SUCCESS_EXIT_CODE) {
          } else {
            getLog().error("Error testing changes!");
          }
        }
      } catch (CommandLineException | GitLabApiException ex) {
        getLog().error(ex);
      }
    }
  }

  protected void addClassToTest(Class<?> c) {
    addClassToTest(c, null);
  }

  protected void addClassToTest(Class<?> c, Class<?> loadedClass) {
    if (verbose) {
      if (loadedClass != null) {
        getLog()
            .debug(
                String.format(
                    "Marking class '%s' to be tested as a children of '%s'!",
                    c.toString(), loadedClass.toString()));
      } else {
        getLog().debug(String.format("Marking class '%s' to be tested!", c.toString()));
      }
    }
    classesToTest.add(c);
  }

  private Class getClass(String className, String packageName, ClassLoader projectClassLoader) {
    try {
      return projectClassLoader.loadClass(
          packageName + "." + className.substring(0, className.lastIndexOf('.')));
    } catch (ClassNotFoundException e) {
      getLog().error(e);
    }
    return null;
  }

  @Override
  public ClassLoader getClassLoader(MavenProject project) {
    try {
      List classpathElements = project.getCompileClasspathElements();
      classpathElements.add(project.getBuild().getOutputDirectory());
      classpathElements.add(project.getBuild().getTestOutputDirectory());
      URL urls[] = new URL[classpathElements.size()];
      for (int i = 0; i < classpathElements.size(); ++i) {
        urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
      }
      return new URLClassLoader(urls, this.getClass().getClassLoader());
    } catch (MalformedURLException | DependencyResolutionRequiredException e) {
      getLog().debug("Couldn't get the classloader!");
      return this.getClass().getClassLoader();
    }
  }
}
