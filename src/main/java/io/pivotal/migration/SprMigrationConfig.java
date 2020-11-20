/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal.migration;

import io.pivotal.github.GithubIssue;
import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import io.pivotal.migration.FieldValueLabelHandler.FieldType;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Configuration for migration of SPR Jira.
 */
@Configuration
public class SprMigrationConfig {

	private static final List<String> skipVersions =
			Arrays.asList("Contributions Welcome", "Pending Closure", "Waiting for Triage");


	@Bean
	public MilestoneFilter milestoneFilter() {
		return fixVersion -> !skipVersions.contains(fixVersion.getName());
	}

	@Bean
	public LabelHandler labelHandler() {

		FieldValueLabelHandler fieldValueHandler = new FieldValueLabelHandler();

		setupRedis(fieldValueHandler);

		// "[Build]" - not used
		// "[Other]" - bad idea

		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Bug", "bug");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "New Feature", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Improvement", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Refactoring", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Pruning", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Task", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Sub-task", "task");
		// "Backport" - ignore

		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Deferred", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Do", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Fix", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Works as Designed", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Duplicate", "duplicate");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Invalid", "invalid");
		// "Complete", "Fixed", "Done" - it should be obvious if it has fix version and is closed
		// "Incomplete", "Cannot Reproduce" - no label at all (like issue-bot)

		fieldValueHandler.addMapping(FieldType.STATUS, "Waiting for Feedback", "waiting-for-feedback");
		// "Resolved", "Closed -- it should be obvious if issue is closed (distinction not of interest)
		// "Open", "Re Opened" -- it should be obvious from GH timeline
		// "In Progress", "Investigating" -- no value in those, they don't work well

		fieldValueHandler.addMapping(FieldType.VERSION, "Waiting for Triage", "waiting-for-triage", LabelFactories.STATUS_LABEL);
		fieldValueHandler.addMapping(FieldType.VERSION, "Contributions Welcome", "ideal-for-contribution", LabelFactories.STATUS_LABEL);

		fieldValueHandler.addMapping(FieldType.LABEL, "Regression", "regression", LabelFactories.TYPE_LABEL);


		CompositeLabelHandler handler = new CompositeLabelHandler();
		handler.addLabelHandler(fieldValueHandler);

		handler.addLabelHandler(LabelFactories.STATUS_LABEL.apply("waiting-for-triage"), issue ->
		issue.getFields().getResolution() == null && issue.getFixVersion() == null && issue.hasLabel("triage.pending"));

		handler.addLabelHandler(LabelFactories.STATUS_LABEL.apply("ideal-for-contribution"),
				issue -> issue.getFields().getResolution() == null && issue.getFixVersion() == null
						&& issue.hasLabel("triage.3.pr-welcome"));

		handler.addLabelHandler(LabelFactories.STATUS_LABEL.apply("blocked"),
				issue -> issue.getFields().getResolution() == null && issue.hasLabel("triage.5.blocked"));

		handler.addLabelHandler(LabelFactories.STATUS_LABEL.apply("declined"),
				issue -> issue.getFields().getResolution() == null && issue.hasLabel("triage.4.not-likely"));

		handler.addLabelHandler(LabelFactories.HAS_LABEL.apply("votes-jira"), issue ->
				issue.getVotes() >= 10);

		handler.addLabelSupersede("type: bug", "type: regression");
		handler.addLabelSupersede("type: task", "type: documentation");
		handler.addLabelSupersede("status: waiting-for-triage", "status: waiting-for-feedback");
		handler.addLabelSupersede("type: task", "type: dependency-upgrade");
		handler.addLabelSupersede("type: enhancement", "type: dependency-upgrade");
		handler.addLabelSupersede("type: enhancement", "type: task");

		// In Jira users pick the type when opening ticket. It doesn't work that way in GitHub.
		handler.addLabelRemoval("status: waiting-for-triage", label -> label.startsWith("type: "));
		// If it is invalid, the issue type is undefined
		handler.addLabelRemoval("status: invalid", label -> label.startsWith("type: "));
		// Anything declined is not a bug nor regression
		handler.addLabelRemoval("status: declined",  label -> label.equals("type: bug"));
		handler.addLabelRemoval("status: declined",  label -> label.equals("type: regression"));
		// No need to label an issue with bug or regression if it is a duplicate
		handler.addLabelRemoval("status: duplicate", label -> label.equals("type: bug"));
		handler.addLabelRemoval("status: duplicate", label -> label.equals("type: regression"));

		return handler;
	}

	private void setupCommons(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "API", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration", "web");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Dependencies", "dependency-upgrade", LabelFactories.TYPE_LABEL);

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Mapping / Conversion", "mapping");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Query", "repository");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repositories", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupCassandra(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "API", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Cassandra Administration", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Configuration", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "SessionFactory", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Template API", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Kotlin", "kotlin");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Mapping", "mapping");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repository", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupCouchbase(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Dependencies", "dependency-upgrade", LabelFactories.TYPE_LABEL);

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Mapping metadata", "mapping");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repositories", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupElasticsearch(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Dependencies", "dependency-upgrade", LabelFactories.TYPE_LABEL);

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Mapping", "mapping");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repositories", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupJdbc(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "ORCL", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Dependencies", "dependency-upgrade", LabelFactories.TYPE_LABEL);

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Converter", "mapping");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Mapping", "mapping");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Relational", "relational");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "R2DBC", "relational");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repository", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Statement Builder", "statement-builder");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupJpa(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Namespace", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Specification", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Query Parser", "query-parser");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Querydsl", "querydsl");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupKv(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Configuration", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Map", "map");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repositories", "repository");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupLdap(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repository", "repository");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupMongo(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Aggregation framework", "aggregation-framework");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "GridFS", "gridfs");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Kotlin", "kotlin");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Mapping / Conversion", "mapping");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repository", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupNeo4j(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "CORE", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "EXAMPLES", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "DOC", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupRedis(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Cache", "cache");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Jedis Driver", "jedis");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Lettuce Driver", "lettuce");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Kotlin", "kotlin");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repository Support", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupRest(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "API Documentation", "api-documentation");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Content negotiation", "content-negotiation");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repositories", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	private void setupSolr(FieldValueLabelHandler fieldValueHandler) {

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Namespace", "core");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Repository", "repository");

		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Infrastructure", "task", LabelFactories.TYPE_LABEL);
	}

	@Bean
	public IssueProcessor issueProcessor() {
		return new CompositeIssueProcessor(new AssigneeDroppingIssueProcessor());
	}


	private static class AssigneeDroppingIssueProcessor implements IssueProcessor {

		private static final String label1 = LabelFactories.STATUS_LABEL.apply("waiting-for-triage").getName();

		private static final String label2 = LabelFactories.STATUS_LABEL.apply("ideal-for-contribution").getName();


		@Override
		public void beforeImport(JiraIssue issue, ImportGithubIssue importIssue) {
			JiraFixVersion version = issue.getFixVersion();
			GithubIssue ghIssue = importIssue.getIssue();
			if (version != null && version.getName().contains("Backlog") ||
					ghIssue.getLabels().contains(label1) ||
					ghIssue.getLabels().contains(label2)) {

				ghIssue.setAssignee(null);
			}
		}
	}




}
