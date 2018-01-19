/***************************************************************************
 * Copyright 2017 Kieker Project (http://kieker-monitoring.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/

package kieker.test.tools.junit.logReplayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import kieker.analysis.IAnalysisController;
import kieker.analysis.plugin.reader.AbstractReaderPlugin;
import kieker.analysis.plugin.reader.list.ListReader;
import kieker.common.configuration.Configuration;
import kieker.common.record.IMonitoringRecord;
import kieker.common.record.misc.EmptyRecord;
import kieker.common.record.system.MemSwapUsageRecord;
import kieker.monitoring.core.configuration.ConfigurationFactory;
import kieker.tools.logReplayer.AbstractLogReplayer;

import kieker.test.common.junit.AbstractKiekerTest;
import kieker.test.monitoring.util.NamedListWriter;

/**
 * Tests the {@link AbstractLogReplayer}.
 *
 * @author Andre van Hoorn
 *
 * @since 1.6
 */
public class TestLogReplayer extends AbstractKiekerTest {

	/** A rule making sure that a temporary folder exists for every test method (which is removed after the test). */
	@Rule
	public final TemporaryFolder tmpFolder = new TemporaryFolder(); // NOCS (@Rule must be public)

	private final String listName = TestLogReplayer.class.getName();

	private File monitoringConfigurationFile;
	private List<IMonitoringRecord> replayList = new ArrayList<IMonitoringRecord>();

	/**
	 * Performs an initial test setup.
	 *
	 * @throws IOException
	 *             If the setup failed.
	 */
	@Before
	public void init() throws IOException {
		// Adding arbitrary records
		this.replayList.add(new EmptyRecord());
		this.replayList.add(new MemSwapUsageRecord(1, "myHost", 17, // memTotal
				3, // memUsed
				14, // memFree
				100, // swapTotal
				0, // swapUsed
				100 // swapFree
		));
		this.replayList.add(new EmptyRecord());

		final Configuration config = ConfigurationFactory.createDefaultConfiguration();
		config.setProperty(ConfigurationFactory.METADATA, "false");
		config.setProperty(ConfigurationFactory.WRITER_CLASSNAME, NamedListWriter.class.getName());
		config.setProperty(NamedListWriter.CONFIG_PROPERTY_NAME_LIST_NAME, this.listName);

		this.monitoringConfigurationFile = this.tmpFolder.newFile("moitoring.properties");
		final FileOutputStream fos = new FileOutputStream(this.monitoringConfigurationFile);
		try {
			config.store(fos, "Generated by " + TestLogReplayer.class.getName());
		} finally {
			fos.close();
		}

	}

	@Test
	public void testIt() {
		final ListReplayer replayer = new ListReplayer(this.monitoringConfigurationFile.getAbsolutePath(), false, // realtimeMode
				1.0, // realtimeAccelerationFactor
				true, // keepOriginalLoggingTimestamps
				1, // numRealtimeWorkerThreads
				AbstractLogReplayer.MIN_TIMESTAMP, // ignoreRecordsBeforeTimestamp
				AbstractLogReplayer.MAX_TIMESTAMP, // ignoreRecordsAfterTimestamp
				this.replayList);
		Assert.assertTrue(replayer.replay());

		final List<IMonitoringRecord> recordListFilledByListWriter = NamedListWriter.getNamedList(this.listName);
		Assert.assertEquals("Unexpected list replayed", this.replayList, recordListFilledByListWriter);
	}
}

/**
 * @author Andre van Hoorn
 *
 * @since 1.6
 */
class ListReplayer extends AbstractLogReplayer { // NOPMD
	private final List<IMonitoringRecord> replayList = new ArrayList<IMonitoringRecord>();

	public ListReplayer(final String monitoringConfigurationFile, final boolean realtimeMode,
			final double realtimeAccelerationFactor, final boolean keepOriginalLoggingTimestamps,
			final int numRealtimeWorkerThreads, final long ignoreRecordsBeforeTimestamp,
			final long ignoreRecordsAfterTimestamp, final List<IMonitoringRecord> replayList) {
		super(monitoringConfigurationFile, realtimeMode, realtimeAccelerationFactor, keepOriginalLoggingTimestamps,
				numRealtimeWorkerThreads, ignoreRecordsBeforeTimestamp, ignoreRecordsAfterTimestamp);
		this.replayList.addAll(replayList);
	}

	@Override
	protected String readerOutputPortName() {
		return ListReader.OUTPUT_PORT_NAME;
	}

	@Override
	protected AbstractReaderPlugin createReader(final IAnalysisController analysisController) {
		final Configuration listReaderConfig = new Configuration();
		listReaderConfig.setProperty(ListReader.CONFIG_PROPERTY_NAME_AWAIT_TERMINATION,
				Boolean.toString(Boolean.FALSE));
		final ListReader<IMonitoringRecord> listReader = new ListReader<IMonitoringRecord>(listReaderConfig,
				analysisController);
		listReader.addAllObjects(this.replayList);
		return listReader;
	}
}
