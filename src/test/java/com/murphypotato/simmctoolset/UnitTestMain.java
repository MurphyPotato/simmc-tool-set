package com.murphypotato.simmctoolset;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;

/** Direct JUnit launcher used when the local Gradle test worker cannot load test classes. */
public final class UnitTestMain {
    private UnitTestMain() { }

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("com.murphypotato.simmctoolset"))
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        listener.getSummary().printTo(new PrintWriter(System.out, true));
        long failures = listener.getSummary().getTestsFailedCount();
        long tests = listener.getSummary().getTestsFoundCount();
        if (tests == 0 || failures > 0) {
            throw new IllegalStateException("JUnit verification failed: tests=" + tests + ", failures=" + failures);
        }
    }
}
