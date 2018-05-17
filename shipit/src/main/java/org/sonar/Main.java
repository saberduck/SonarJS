package org.sonar;/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class Main {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("Missing js file as argument");
    }
    String inputFilename = args[0];
    Analyzer analyzer = new Analyzer();
    long start = System.nanoTime();
    System.out.println("Running analysis on " + inputFilename);
    String input = readInput(inputFilename);
    List<Analyzer.Issue> issues = analyzer.analyze(input);
    saveIssues(issues, inputFilename);
    long stop = System.nanoTime();
    System.out.printf("Found %d issues in %d ms\n", issues.size(), (stop - start) / 1000_000);
  }

  private static String readInput(String filename) throws IOException {
    return new String(Files.readAllBytes(Paths.get(filename)));
  }

  private static void saveIssues(List<Analyzer.Issue> issues, String inputFilename) throws IOException {
    if (issues.isEmpty()) {
      return;
    }
    String uuid = UUID.randomUUID().toString();
    new File("issues").mkdir();
    BufferedWriter writer = Files.newBufferedWriter(Paths.get("issues","issues-" + uuid + ".json"));
    issues.forEach(i -> i.path = inputFilename);
    new Gson().toJson(issues, writer);
    writer.close();
  }


}
