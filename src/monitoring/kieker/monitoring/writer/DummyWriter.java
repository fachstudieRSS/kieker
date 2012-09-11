/***************************************************************************
 * Copyright 2012 Kieker Project (http://kieker-monitoring.net)
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

package kieker.monitoring.writer;

import kieker.common.configuration.Configuration;
import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.common.record.IMonitoringRecord;

/**
 * A writer that does nothing but consuming records.
 * 
 * @author Andre van Hoorn, Jan Waller
 */
public class DummyWriter extends AbstractMonitoringWriter {
	private static final Log LOG = LogFactory.getLog(DummyWriter.class);

	public DummyWriter(final Configuration configuration) {
		super(configuration);
	}

	public boolean newMonitoringRecord(final IMonitoringRecord record) {
		return true; // we don't care about incoming records
	}

	public void terminate() {
		LOG.info(this.getClass().getName() + " shutting down");
	}

	@Override
	public void init() {
		// nothing to do
	}
}
