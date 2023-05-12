/*
 * Copyright 2023 Javier A. Ortiz Bultron javier.ortiz.78@gmail.com - All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
 */
package com.javydreamercsw.testng.ci;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestChangesMojoNoChangesTest extends TestChangesMojoTest {

  /**
   * Test of execute method, of class test-changes mojo.
   *
   * @throws java.lang.Exception when something goes wrong.
   */
  public void testTestChangesGoal() throws Exception {
    instance.execute();

    assertTrue("Classes to test should be empty!", instance.getClassesToTest().isEmpty());
  }
}
