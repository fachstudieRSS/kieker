package kieker.monitoring.writer;

import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import kieker.common.record.IMonitoringRecord;
import kieker.monitoring.writer.util.async.AbstractWorkerThread;

/*
 * ==================LICENCE=========================
 * Copyright 2006-2011 Kieker Project
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
 * ==================================================
 */

/**
 * A writer that does nothing but consuming records.
 * 
 * @author Andre van Hoorn
 * @author Jan Waller
 * 
 */
public class DummyLogWriter implements IMonitoringLogWriter {

	private static final Log log = LogFactory.getLog(DummyLogWriter.class);

	/*
	 * {@inheritdoc}
	 */
	@Override
	public boolean newMonitoringRecord(final IMonitoringRecord record) {
		return true; // we don't care about incoming records
	}

	/*
	 * {@inheritdoc}
	 */
	@Override
	public boolean init(final String initString) {
		log.info(this.getClass().getName() + " initializing with initString '" + initString + "'");
		return true; // no initialization required
	}

	/*
	 * {@inheritdoc}
	 */
	@Override
	public Vector<AbstractWorkerThread> getWorkers() {
		return null; // have no workers
	}

	/*
	 * {@inheritdoc}
	 */
	@Override
	public String getInfoString() {
		return this.getClass().getName();
	}
}
