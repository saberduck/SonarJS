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

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import java.util.ArrayList;
import java.util.List;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;

public class MainDataflow {
  /**
   * GOOGLE_APPLICATION_CREDENTIALS=*** java -cp $PWD/SonarJS/shipit/target/shipit-bundled-4.1-SNAPSHOT.jar   org.sonar.MainDataflow   --project=project-test-199515   --stagingLocation=gs://project-test-199515/staging/   --output=gs://project-test-199515/output   --runner=DataflowRunner   --jobName=dataflow-intro13
   */

  /**
   * Options supported by .
   *
   * <p>Concept #4: Defining your own configuration options. Here, you can add your own arguments
   * to be processed by the command-line parser, and specify default values for them. You can then
   * access the options values in your pipeline code.
   *
   * <p>Inherits standard configuration options.
   */
  public interface AnalysisOptions extends DataflowPipelineOptions {

    @Description("Table spec to read input")
    @Default.String("project-test-199515:github_us.massimoTest")
    ValueProvider<String> getInputTableSpec();

    void setInputTableSpec(ValueProvider<String> value);

    @Description("Table spec to write the output to")
    ValueProvider<String> getOutputTableSpec();
    void setOutputTableSpec(ValueProvider<String> value);
  }


  public static void main(String[] args) {
    AnalysisOptions options = PipelineOptionsFactory.fromArgs(args).withValidation()
      .as(AnalysisOptions.class);

    //schema
    List<TableFieldSchema> fields = new ArrayList<>();
    fields.add(new TableFieldSchema().setName("RULE_KEY").setType("STRING"));
    fields.add(new TableFieldSchema().setName("RULE_TITLE").setType("STRING"));
    fields.add(new TableFieldSchema().setName("ISSUE_MESSAGE").setType("STRING"));
    fields.add(new TableFieldSchema().setName("LINE_NUMBER").setType("INTEGER"));
    fields.add(new TableFieldSchema().setName("ISSUE_TYPE").setType("STRING"));
    fields.add(new TableFieldSchema().setName("GITHUB_PRJ").setType("STRING"));
    fields.add(new TableFieldSchema().setName("GITHUB_ORG").setType("STRING"));
    fields.add(new TableFieldSchema().setName("FILE_PATH").setType("STRING"));
    fields.add(new TableFieldSchema().setName("COMMIT").setType("STRING"));
    TableSchema schema = new TableSchema().setFields(fields);


    runPipeline(schema, options);
  }

  private static void runPipeline(TableSchema schema, AnalysisOptions options) {
    options.setMaxNumWorkers(32);
    options.setWorkerMachineType("n1-standard-16");
    Pipeline p = Pipeline.create(options);

    Analyzer analyzer = new Analyzer();

    p.apply(BigQueryIO.readTableRows().from(options.getInputTableSpec()))
      .apply("Analysing", ParDo.of(new Analysis(analyzer)))
      .apply("Transform", MapElements.via(new IssueToRow()))
      .apply("WriteBigQuery", BigQueryIO.writeTableRows().withSchema(schema).withoutValidation()
        .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_TRUNCATE)
        .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED)
        .to(options.getOutputTableSpec()));

    p.run().waitUntilFinish();
  }

  static class IssueToRow extends SimpleFunction<Analyzer.Issue, TableRow> {

    @Override
    public TableRow apply(Analyzer.Issue input) {
      TableRow row = new TableRow()
        .set("RULE_KEY", input.ruleKey)
        .set("RULE_TITLE", input.ruleTitle)
        .set("ISSUE_MESSAGE", input.message)
        .set("LINE_NUMBER", input.line)
        .set("ISSUE_TYPE", input.type.toString())
        .set("GITHUB_PRJ", input.project)
        .set("GITHUB_ORG", input.org)
        .set("FILE_PATH", input.path)
        .set("COMMIT", input.commit);

      return row;
    }
  }

  private static class Analysis extends DoFn<TableRow, Analyzer.Issue> {
    private final Analyzer analyzer;

    public Analysis(Analyzer analyzer) {
      this.analyzer = analyzer;
    }

    @ProcessElement
    public void processElement(ProcessContext c, BoundedWindow window) {
      try {
        TableRow row = c.element();
        String[] repoName = row.get("repo_name").toString().split("/");
        String source = row.get("content").toString();
        List<Analyzer.Issue> issues = analyzer.analyze(source);
        for (Analyzer.Issue issue : issues) {
          issue.org = repoName[0];
          issue.project = repoName[1];
          issue.path = row.get("path").toString();
          issue.commit = row.get("ref").toString();
          c.output(issue);
        }
      } catch (Throwable e) {
        // ignore
      }
    }
  }
}
