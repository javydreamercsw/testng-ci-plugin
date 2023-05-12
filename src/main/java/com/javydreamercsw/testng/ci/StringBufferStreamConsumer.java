/*
 * Copyright 2023 Javier A. Ortiz Bultron javier.ortiz.78@gmail.com - All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
 */
package com.javydreamercsw.testng.ci;

import org.apache.maven.shared.utils.cli.StreamConsumer;

public class StringBufferStreamConsumer implements StreamConsumer {
  private static final String LS = System.getProperty("line.separator");

  private final StringBuffer buffer;

  private final boolean printOut;

  public StringBufferStreamConsumer() {
    this(false);
  }

  public StringBufferStreamConsumer(boolean printOut) {
    this.buffer = new StringBuffer();
    this.printOut = printOut;
  }

  @Override
  public void consumeLine(String line) {
    if (printOut) {
      System.out.println(line);
    }

    buffer.append(line).append(LS);
  }

  public String getOutput() {
    return buffer.toString();
  }
}
