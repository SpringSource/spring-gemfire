/*
 * Copyright 2010-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.gemfire.function;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.isA;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.gemfire.fork.ServerProcess;
import org.springframework.data.gemfire.function.sample.ExceptionThrowingFunctionExecution;
import org.springframework.data.gemfire.process.ProcessExecutor;
import org.springframework.data.gemfire.process.ProcessWrapper;
import org.springframework.data.gemfire.test.support.FileSystemUtils;
import org.springframework.data.gemfire.test.support.ThreadUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

import com.gemstone.gemfire.cache.execute.FunctionAdapter;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.cache.execute.FunctionException;

/**
 * The ExceptionThrowingFunctionExecutionIntegrationTest class is a test suite of test cases testing the invocation
 * of a GemFire Function using Spring Data GemFire Function Execution Annotation support when that Function throws
 * an Exception.
 *
 * @author John Blum
 * @see org.junit.Rule
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.data.gemfire.fork.ServerProcess
 * @see org.springframework.data.gemfire.function.annotation.GemfireFunction
 * @see org.springframework.data.gemfire.function.sample.ExceptionThrowingFunctionExecution
 * @see org.springframework.data.gemfire.process.ProcessExecutor
 * @see org.springframework.data.gemfire.process.ProcessWrapper
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @since 1.7.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class ExceptionThrowingFunctionExecutionIntegrationTest {

	private static ProcessWrapper serverProcess;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Autowired
	private ExceptionThrowingFunctionExecution exceptionThrowingFunctionExecution;

	@BeforeClass
	public static void setup() throws IOException {
		String serverName = ExceptionThrowingFunctionExecutionIntegrationTest.class.getSimpleName().concat("Server");

		File serverWorkingDirectory = new File(FileSystemUtils.WORKING_DIRECTORY, serverName.toLowerCase());

		Assert.isTrue(serverWorkingDirectory.isDirectory() || serverWorkingDirectory.mkdirs());

		List<String> arguments = new ArrayList<String>();

		arguments.add("-Dgemfire.name=" + serverName);
		arguments.add(ExceptionThrowingFunctionExecutionIntegrationTest.class.getName().replace(".", "/")
			.concat("-server-context.xml"));

		serverProcess = ProcessExecutor.launch(serverWorkingDirectory, ServerProcess.class,
			arguments.toArray(new String[arguments.size()]));

		waitForServerStart(TimeUnit.SECONDS.toMillis(20));

		System.out.println("GemFire Cache Server Process for ClientCache Indexing should be running...");
	}

	private static void waitForServerStart(final long milliseconds) {
		ThreadUtils.timedWait(milliseconds, TimeUnit.MILLISECONDS.toMillis(500), new ThreadUtils.WaitCondition() {
			private File serverPidControlFile = new File(serverProcess.getWorkingDirectory(),
				ServerProcess.getServerProcessControlFilename());

			@Override public boolean waiting() {
				return !serverPidControlFile.isFile();
			}
		});
	}

	@AfterClass
	public static void tearDown() {
		serverProcess.shutdown();

		if (Boolean.valueOf(System.getProperty("spring.gemfire.fork.clean", Boolean.TRUE.toString()))) {
			org.springframework.util.FileSystemUtils.deleteRecursively(serverProcess.getWorkingDirectory());
		}
	}

	@Test
	public void exceptionThrowingFunctionExecutionRethrowsException() {
		expectedException.expect(FunctionException.class);
		expectedException.expectCause(isA(IllegalArgumentException.class));
		expectedException.expectMessage(containsString("Execution of Function with ID 'exceptionThrowingFunction' failed"));
		exceptionThrowingFunctionExecution.exceptionThrowingFunction();
	}

	public static class ExceptionThrowingFunction extends FunctionAdapter {
		@Override
		public String getId() {
			return "exceptionThrowingFunction";
		}
		@Override
		public void execute(final FunctionContext context) {
			context.getResultSender().sendException(new IllegalArgumentException("TEST"));
		}
	}

}
