/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import static java.util.stream.Collectors.toSet

import io.opentelemetry.api.trace.TraceId
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import java.util.jar.Attributes
import java.util.jar.JarFile
import okhttp3.Request
import spock.lang.Unroll

class SpringBootSmokeTest extends SmokeTest {

  protected String getTargetImage(int jdk) {
    "ghcr.io/open-telemetry/java-test-containers:smoke-springboot-jdk$jdk-20201128.1734635"
  }

  @Unroll
  def "spring boot smoke test on JDK #jdk"(int jdk) {
    setup:
    def output = startTarget(jdk)
    String url = "http://localhost:${target.getMappedPort(8080)}/greeting"
    def request = new Request.Builder().url(url).get().build()

    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    response.body().string() == "Hi!"
    countSpansByName(traces, '/greeting') == 1
    countSpansByName(traces, 'WebController.greeting') == 1
    countSpansByName(traces, 'WebController.withSpan') == 1

    [currentAgentVersion] as Set == findResourceAttribute(traces, "telemetry.auto.version")
      .map { it.stringValue }
      .collect(toSet())

    then: "correct traceIds are logged via MDC instrumentation"
    def loggedTraceIds = getLoggedTraceIds(output)
    def spanTraceIds = getSpanStream(traces)
      .map({ TraceId.bytesToHex(it.getTraceId().toByteArray()) })
      .collect(toSet())
    loggedTraceIds == spanTraceIds

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 15]
  }
}
