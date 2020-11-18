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
package io.pivotal.util;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import reactor.core.publisher.Mono;

/**
 * Rate limiter to ensure more efficient adherence to GitHub's limit of 1 update
 * per second. Simply sleeping before a call tends to stretch the overall time
 * as there is some additional processing time between calls.
 *
 * @author Rossen Stoyanchev
 */
public class RateLimitHelper {

	private static final Mono<Object> parentMono = Mono.just("foo");

	private final Duration timeBetweenCalls = Duration.ofMillis(350);

	private Mono<Object> nextPermit;

	private AtomicLong requests = new AtomicLong();

	private AtomicLong reset = new AtomicLong();


	public void obtainPermitToCall() {

		reset.compareAndSet(0, System.currentTimeMillis());
		if(requests.incrementAndGet() > 99){

			long since = reset.getAndSet(System.currentTimeMillis());
			long now = System.currentTimeMillis();


			double seconds = TimeUnit.MILLISECONDS.toSeconds(now - since);
			double requests = this.requests.getAndSet(0);
			double requestsPerSecond =  requests/ seconds;
			double requestsPerMinute = requestsPerSecond * 60;
			double requestsPerHour = requestsPerMinute * 60;

			System.out.println();
			System.out.println(String
					.format("Requests %.0f, per second %.2f, per minute %.2f, per hour %.2f", requests, requestsPerSecond, requestsPerMinute, requestsPerHour));
		}

		if (nextPermit != null) {
			nextPermit.block();
		}
		resetNextPermit();
	}

	private void resetNextPermit() {
		nextPermit = parentMono.delayElement(timeBetweenCalls);
	}

}
