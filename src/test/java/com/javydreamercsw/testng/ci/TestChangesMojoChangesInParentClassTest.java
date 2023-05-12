/*
 * Copyright 2023 Javier A. Ortiz Bultron javier.ortiz.78@gmail.com - All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
 */
package com.javydreamercsw.testng.ci;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;

@Slf4j
public class TestChangesMojoChangesInParentClassTest extends TestChangesMojoTest {

  /**
   * Test of execute method, of class test-changes mojo.
   *
   * @throws java.lang.Exception when something goes wrong.
   */
  public void testTestChangesGoal() throws Exception {
    // Modify a file.
    File modifiedFile = new File(projectRoot, "src/test/java/basic/project/ParentClassTest.java");
    assertTrue(modifiedFile.exists());
    FileWriter fw = new FileWriter(modifiedFile, true);
    try (BufferedWriter bw = new BufferedWriter(fw)) {
      bw.write("//Comment");
      bw.newLine();
    }

    // Commit the change
    jGit.add().addFilepattern(".").call();
    jGit.commit()
        .setCommitter(gitIdentity)
        .setAuthor(gitIdentity)
        .setAll(true)
        .setMessage("Add comment")
        .call();

    instance.execute();

    assertFalse("Classes to test should not be empty!", instance.getClassesToTest().isEmpty());
    SoftAssertions sa = new SoftAssertions();

    instance
        .getClassesToTest()
        .forEach(
            c ->
                sa.assertThat(c.getSuperclass().getSimpleName())
                    .describedAs(
                        String.format(
                            "Expected %s to be a child of %s or itself",
                            c.getSuperclass().getSimpleName(), "ParentClassTest"))
                    .isIn("ParentClassTest", "GrandParentClassTest"));

    sa.assertAll();
  }
}
