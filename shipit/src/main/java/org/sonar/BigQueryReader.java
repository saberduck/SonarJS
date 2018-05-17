/*
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
package org.sonar;

import com.google.cloud.bigquery.*;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BigQueryReader {

  private static final int INSERT_BATCH_SIZE = 1000;
  private static final int BIGQUERY_IO_BACKOFF_TIME = 10000;

  private static final Analyzer analyzer = new Analyzer();

  private static final List<InsertAllRequest.RowToInsert> issueRows = new ArrayList<>();
  private static Table resultIssueTable;

  private static final List<InsertAllRequest.RowToInsert> measureRows = new ArrayList<>();
  private static Table resultMeasureTable;

  public static void main(String[] args) throws Exception {
    int totalWorkers = Integer.parseInt(args[0]);
    int workerId = Integer.parseInt(args[1]);

    String outputIssueTable = args[2];
    String outputMeasureTable = args[3];

    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    Table issuesTable = bigquery.getTable("github_us", "js_files_and_contents");
    resultIssueTable = bigquery.getTable("github_eu", outputIssueTable);
    resultMeasureTable = bigquery.getTable("github_eu", outputMeasureTable);

    long totalRows = issuesTable.list().getTotalRows();
    long pageSize = totalRows / totalWorkers;
    long startIndex = workerId * pageSize;

    System.out.println("Initial stats: " + totalRows + " total rows, " + totalWorkers + " total workers, worker id = " + workerId + ", start index = " + startIndex);

    int processed = 0;
    while (processed < pageSize) {
      try {
        TableResult result = issuesTable.list(BigQuery.TableDataListOption.pageSize(pageSize - processed), BigQuery.TableDataListOption.startIndex(startIndex + processed));
        for (FieldValueList values : result.getValues()) {
          System.out.println("Progress: " + processed + " / " + pageSize + " (" + ((processed * 100.0) / pageSize) + "%), pending row buffers: " + issueRows.size() + " issues & " + measureRows.size() + " measures");
          process(values);

          processed++;

          if (issueRows.size() > INSERT_BATCH_SIZE) {
            persistIssueRows();
          }

          if (measureRows.size() > INSERT_BATCH_SIZE) {
            persistMeasureRows();
          }
        }
      } catch (RuntimeException e) {
        // BigQuery IO issues, cool down
        Thread.sleep(BIGQUERY_IO_BACKOFF_TIME);
      }
    }

    if (!issueRows.isEmpty()) {
      persistIssueRows();
    }

    if (!measureRows.isEmpty()) {
      persistMeasureRows();
    }

    System.out.println("Finished: " + processed + " processed issueRows out of a total of " + totalRows + " issueRows");

  }

  private static void persistIssueRows() {
    try {
      InsertAllResponse response = resultIssueTable.insert(issueRows);
      if (response.hasErrors()) {
        System.out.println("Error while persisting issues! " + response.toString());
      }

      issueRows.clear();
    } catch (RuntimeException e) {
      System.err.println("Got error while persisting issues: " + e);
      e.printStackTrace();
    }
  }

  private static void persistMeasureRows() {
    try {
      InsertAllResponse response = resultMeasureTable.insert(measureRows);
      if (response.hasErrors()) {
        System.out.println("Error while persisting measures! " + response.toString());
      }

      measureRows.clear();
    } catch (RuntimeException e) {
      System.err.println("Got error while persisting measures: " + e);
      e.printStackTrace();
    }
  }

  private static void process(FieldValueList values) {
    try {
      String repo_name = values.get(0).getStringValue();
      String ref = values.get(1).getStringValue();
      String path = values.get(2).getStringValue();
      String content = values.get(3).getStringValue();

      String orga = repo_name;
      String project = repo_name;

      int lines = 1 + StringUtils.countMatches(content, "\n");
      Map<String, Integer> measuresRow = new HashMap<>();
      measuresRow.put("LINES", lines);
      measureRows.add(InsertAllRequest.RowToInsert.of(measuresRow));

      int slash = repo_name.indexOf('/');
      if (slash != -1) {
        orga = repo_name.substring(0, slash);
        project = repo_name.substring(slash + 1);
      }

      System.out.println("  repo_name: " + repo_name + ", orga: " + orga + ", project: " + project + ", ref: " + ref + ", path: " + path + ", content length: " + content.length() + ", lines: " + lines);

      List<Analyzer.Issue> issues = analyzer.analyze(content);

      System.out.println("    " + issues.size() + " issues");
      for (Analyzer.Issue issue: issues) {
        String ruleKey = issue.ruleKey;
        String ruleTitle = issue.ruleTitle;
        String issueMessage = issue.message;
        int lineNumber = issue.line;
        String issueType = issueTypeToString(issue.type);

        System.out.println("      - (" + ruleKey + ") " + issueMessage + " at line " + lineNumber + " type: " + issueType);

        Map<String, String> issueRow = new HashMap<>();
        issueRow.put("RULE_KEY", ruleKey);
        issueRow.put("ISSUE_TYPE", issueType);
        issueRow.put("COMMIT", ref);
        issueRow.put("FILE_PATH", path);
        issueRow.put("GITHUB_ORG", orga);
        issueRow.put("GITHUB_PRJ", project);
        issueRow.put("LINE_NUMBER", "" + lineNumber);
        issueRow.put("ISSUE_MESSAGE", issueMessage);
        issueRow.put("RULE_TITLE", ruleTitle);

        issueRows.add(InsertAllRequest.RowToInsert.of(issueRow));
      }
    } catch (RuntimeException e) {
      System.err.println("Got error while processing: " + e);
      e.printStackTrace();
    }
  }

  private static String issueTypeToString(Analyzer.IssueType issueType) {
    switch (issueType) {
      case CODE_SMELL:
        return "Code smell";
      case BUG:
        return "Bug";
      case VULNERABILITY:
        return "Vulnerability";
      default:
        return "Unknown";
    }
  }

}
