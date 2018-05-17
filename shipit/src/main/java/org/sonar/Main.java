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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.check.Rule;
import org.sonar.javascript.checks.CheckList;
import org.sonar.javascript.checks.verifier.TestUtils;
import org.sonar.javascript.visitors.JavaScriptVisitorContext;
import org.sonar.plugins.javascript.api.JavaScriptCheck;
import org.sonar.plugins.javascript.api.visitors.FileIssue;
import org.sonar.plugins.javascript.api.visitors.LineIssue;
import org.sonar.plugins.javascript.api.visitors.PreciseIssue;

public class Main {

  private static final String PROFILE_JSON = "org/sonar/l10n/javascript/rules/javascript/Sonar_way_profile.json";
  static Gson gson = new Gson();

  static Map<JavaScriptCheck, Metadata> metadata = new HashMap<>();

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("Missing js file as argument");
    }
    List<JavaScriptCheck> checks = getChecks();
    System.out.println("Found " + checks.size() + " checks");
    String inputFilename = args[0];
    long start = System.nanoTime();
    System.out.println("Running analysis on " + inputFilename);
    String input = readInput(inputFilename);
    List<Issue> issues = analyze(checks, input);
    saveIssues(issues, inputFilename);
    long stop = System.nanoTime();
    System.out.printf("Found %d issues in %d ms\n", issues.size(), (stop - start) / 1000_000);
  }

  private static String readInput(String filename) throws IOException {
    return new String(Files.readAllBytes(Paths.get(filename)));
  }

  private static List<Issue> analyze(List<JavaScriptCheck> checks, String input) {
    DefaultInputFile inputFile = new TestInputFileBuilder("moduleKey", "file.js")
      .setContents(input)
      .setCharset(StandardCharsets.UTF_8)
      .build();
    JavaScriptVisitorContext context = TestUtils.createContext(inputFile);
    return checks.stream()
      .flatMap(check -> getActualIssues(check, context))
      .collect(Collectors.toList());
  }

  private static void saveIssues(List<Issue> issues, String inputFilename) throws IOException {
    if (issues.isEmpty()) {
      return;
    }
    String uuid = UUID.randomUUID().toString();
    new File("issues").mkdir();
    BufferedWriter writer = Files.newBufferedWriter(Paths.get("issues","issues-" + uuid + ".json"));
    issues.forEach(i -> i.path = inputFilename);
    gson.toJson(issues, writer);
    writer.close();
  }

  static Stream<Issue> getActualIssues(JavaScriptCheck check, JavaScriptVisitorContext context) {
    return check.scanFile(context).stream().map(Main::toIssue);
  }

  static List<JavaScriptCheck> getChecks() {
    Set<String> sonarWayRuleKeys = sonarWayRuleKeys();
    List<Class> allRules = CheckList.getChecks();
    return allRules.stream()
      .filter(rule -> isRuleInProfile(rule, sonarWayRuleKeys))
      .map(Main::createCheck)
      .collect(Collectors.toList());
  }

  static JavaScriptCheck createCheck(Class rule) {
    try {
      JavaScriptCheck instance = (JavaScriptCheck) rule.newInstance();
      Metadata metadata = new Metadata();
      metadata.ruleKey = ruleKey(rule);
      metadata.type = issueType(metadata.ruleKey);
      Main.metadata.put(instance, metadata);
      return instance;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static IssueType issueType(String ruleKey) {
    String jsonFile = "org/sonar/l10n/javascript/rules/javascript/" + ruleKey + ".json";
    InputStream resource = Main.class.getClassLoader().getResourceAsStream(jsonFile);
    RuleJson ruleJson = gson.fromJson(new InputStreamReader(resource), RuleJson.class);
    return ruleJson.type;
  }

  static boolean isRuleInProfile(Class rule, Set<String> sonarWayRuleKeys) {
    return sonarWayRuleKeys.contains(ruleKey(rule));
  }

  private static String ruleKey(Class rule) {
    Rule annotation = (Rule) rule.getAnnotation(Rule.class);
    return annotation.key();
  }

  static Set<String> sonarWayRuleKeys() {
    String jsonFile = PROFILE_JSON;
    InputStream resource = Main.class.getClassLoader().getResourceAsStream(jsonFile);
    if (resource == null) {
      throw new IllegalStateException(jsonFile);
    }
    Profile profile = gson.fromJson(new InputStreamReader(resource), Profile.class);
    return profile.ruleKeys;
  }

  private static Issue toIssue(org.sonar.plugins.javascript.api.visitors.Issue jsIssue) {
    Metadata metadata = Main.metadata.get(jsIssue.check());
    Issue issue = new Issue();
    issue.ruleKey = metadata.ruleKey;
    issue.type = metadata.type;
    issue.message = message(jsIssue);
    issue.line = line(jsIssue);
    return issue;
  }

  private static String message(org.sonar.plugins.javascript.api.visitors.Issue jsIssue) {
    if (jsIssue instanceof PreciseIssue) {
      return ((PreciseIssue) jsIssue).primaryLocation().message();
    }
    if (jsIssue instanceof FileIssue) {
      return ((FileIssue) jsIssue).message();
    }
    if (jsIssue instanceof LineIssue) {
      return ((LineIssue) jsIssue).message();
    }
    return null;
  }

  private static Integer line(org.sonar.plugins.javascript.api.visitors.Issue jsIssue) {
    if (jsIssue instanceof PreciseIssue) {
      return ((PreciseIssue) jsIssue).primaryLocation().startLine();
    }
    if (jsIssue instanceof FileIssue) {
      return 1;
    }
    if (jsIssue instanceof LineIssue) {
      return ((LineIssue) jsIssue).line();
    }
    return null;
  }

  static class Profile {
    String name;
    Set<String> ruleKeys;
  }

  enum IssueType {
    CODE_SMELL,
    BUG,
    VULNERABILITY
  }

  static class Metadata {
    String ruleKey;
    IssueType type;
  }

  static class RuleJson {
    IssueType type;
  }

  static class Issue {
    String ruleKey;
    IssueType type;
    String message;
    Integer line;
    String org;
    String project;
    String sha;
    String path;
  }

}
