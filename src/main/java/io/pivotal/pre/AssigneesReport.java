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
package io.pivotal.pre;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * Extract the full list of assignees from Jira tickets. This can be used to prepare
 * src/main/resources/jira-to-github-users.properties.
 *
 * @author Rossen Stoyanchev
 */
public class AssigneesReport extends BaseApp {


	public static void main(String args[]) {

		JiraConfig config = initJiraConfig();
		JiraClient client = new JiraClient(config);

		Map<String, AtomicInteger> assigneCounters = new HashMap<>();
		Map<String, AtomicInteger> commenterCounters = new HashMap<>();
		for (JiraIssue issue : client.findIssues(config.getMigrateJql())) {
			JiraUser user = issue.getFields().getAssignee();
			if (user != null) {
				increment(assigneCounters, user);
			}

			for (JiraComment comment : issue.getFields().getComment().getComments()) {
				increment(commenterCounters, comment.getAuthor());
			}
		}

		List<String> assignees = assigneCounters.entrySet().stream()
				.map(entry -> entry.getKey() + " [" + entry.getValue().get() + "]")
				.collect(Collectors.toList());

		System.out.println("Assignees: \n" + assignees + "\n\n");

		List<String> commentors = commenterCounters.entrySet().stream().sorted((o1, o2) -> Integer.compare(o2.getValue().intValue(), o1.getValue().intValue()))
				.map(entry -> entry.getKey() + " [" + entry.getValue().get() + "]").collect(Collectors.toList());

		System.out.println("Commentors: \n" + commentors + "\n\n");
	}

	private static void increment(Map<String, AtomicInteger> counters, JiraUser user) {
		String key = user.getKey() + " (" + user.getDisplayName() + ")";
		counters.computeIfAbsent(key, s -> new AtomicInteger(0)).getAndIncrement();
	}

}
