/***************************************************************************
 * Copyright 2011 by
 *  + Christian-Albrechts-University of Kiel
 *    + Department of Computer Science
 *      + Software Engineering Group 
 *  and others.
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

package kieker.analysis.reader.jms;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatException;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

import kieker.analysis.configuration.Configuration;
import kieker.analysis.plugin.configuration.OutputPort;
import kieker.analysis.reader.AbstractReaderPlugin;
import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.common.record.IMonitoringRecord;

/**
 * Reads monitoring records from a (remote or local) JMS queue.
 * 
 * 
 * @author Andre van Hoorn, Matthias Rohr
 */
public final class JMSReader extends AbstractReaderPlugin {
	public static final String CONFIG_PROVIDERURL = JMSReader.class.getName() + ".jmsProviderUrl";
	public static final String CONFIG_DESTINATION = JMSReader.class.getName() + ".jmsDestination";
	public static final String CONFIG_FACTORYLOOKUP = JMSReader.class.getName() + ".jmsFactoryLookupName";

	private static final Log LOG = LogFactory.getLog(JMSReader.class);

	private static final Collection<Class<?>> OUT_CLASSES = Collections.unmodifiableCollection(new CopyOnWriteArrayList<Class<?>>(
			new Class<?>[] { IMonitoringRecord.class }));

	private final String jmsProviderUrl;
	private final String jmsDestination;
	private final String jmsFactoryLookupName;
	private final OutputPort outputPort;
	private final CountDownLatch cdLatch = new CountDownLatch(1);

	/**
	 * Creates a new instance of this class using the given parameters to
	 * configure the reader.
	 * 
	 * @param configuration
	 *            The configuration used to initialize the whole reader. Keep in mind that the configuration should contain the following properties:
	 *            <ul>
	 *            <li>The property {@link CONFIG_PROVIDERURL}, e.g. {@code tcp://localhost:3035/}
	 *            <li>The property {@link CONFIG_DESTINATION}, e.g. {@code queue1}
	 *            <li>The property {@link CONFIG_FACTORYLOOKUP}, e.g. {@code org.exolab.jms.jndi.InitialContextFactory}
	 *            </ul>
	 * 
	 * @throws IllegalArgumentException
	 *             If one of the properties is empty.
	 */
	public JMSReader(final Configuration configuration) throws IllegalArgumentException {
		/* Call the inherited constructor. */
		super(configuration);
		/* Initialize the reader bases on the given configuration. */
		this.jmsProviderUrl = configuration.getStringProperty("jmsProviderUrl");
		this.jmsDestination = configuration.getStringProperty("jmsDestination");
		this.jmsFactoryLookupName = configuration.getStringProperty("jmsFactoryLookupName");
		// simple sanity check
		if (this.jmsProviderUrl.isEmpty() || this.jmsDestination.isEmpty() || this.jmsFactoryLookupName.isEmpty()) {
			throw new IllegalArgumentException("JMSReader has not sufficient parameters. jmsProviderUrl ('" + this.jmsProviderUrl + "'), jmsDestination ('"
					+ this.jmsDestination + "'), or factoryLookupName ('" + this.jmsFactoryLookupName + "') is null");
		}
		/* Register the one and only output port. */
		this.outputPort = new OutputPort("Output Port of the JMSReader", JMSReader.OUT_CLASSES);
		super.registerOutputPort("out", this.outputPort);
	}

	@Override
	protected Properties getDefaultProperties() {
		final Properties defaultProperties = new Properties();
		// TODO: provide default values!
		defaultProperties.setProperty(JMSReader.CONFIG_PROVIDERURL, "");
		defaultProperties.setProperty(JMSReader.CONFIG_DESTINATION, "");
		defaultProperties.setProperty(JMSReader.CONFIG_FACTORYLOOKUP, "");
		return defaultProperties;
	}

	/**
	 * A call to this method is a blocking call.
	 */
	@Override
	public boolean read() {
		boolean retVal = false;
		Connection connection = null;
		try {
			final Hashtable<String, String> properties = new Hashtable<String, String>(); // NOPMD // NOCS (InitialContext expects Hashtable)
			properties.put(Context.INITIAL_CONTEXT_FACTORY, this.jmsFactoryLookupName);

			// JMS initialization
			properties.put(Context.PROVIDER_URL, this.jmsProviderUrl);
			final Context context = new InitialContext(properties);
			final ConnectionFactory factory = (ConnectionFactory) context.lookup("ConnectionFactory");
			connection = factory.createConnection();
			final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			Destination destination;
			try {
				// As a first step, try a JNDI lookup (this seems to fail with ActiveMQ sometimes)
				destination = (Destination) context.lookup(this.jmsDestination);
			} catch (final NameNotFoundException exc) {
				// JNDI lookup failed, try manual creation (this seems to fail with ActiveMQ sometimes)
				JMSReader.LOG.warn("Failed to lookup queue '" + this.jmsDestination + "' via JNDI: " + exc.getMessage());
				JMSReader.LOG.info("Attempting to create queue ...");
				destination = session.createQueue(this.jmsDestination);
			}

			JMSReader.LOG.info("Listening to destination:" + destination + " at " + this.jmsProviderUrl + " !\n***\n\n");
			final MessageConsumer receiver = session.createConsumer(destination);
			receiver.setMessageListener(new MessageListener() {
				// the MessageListener will read onMessage each time a message comes in
				@Override
				public void onMessage(final Message jmsMessage) {
					if (jmsMessage instanceof TextMessage) {
						final TextMessage text = (TextMessage) jmsMessage;
						JMSReader.LOG.info("Received text message: " + text);

					} else {
						try {
							final ObjectMessage om = (ObjectMessage) jmsMessage;
							final Serializable omo = om.getObject();
							if ((omo instanceof IMonitoringRecord) && (!JMSReader.this.outputPort.deliver(omo))) {
								final String errorMsg = "deliverRecord returned false";
								JMSReader.LOG.error(errorMsg);
							}
						} catch (final MessageFormatException em) {
							JMSReader.LOG.error("MessageFormatException:" + em.getMessage(), em);
						} catch (final JMSException ex) {
							JMSReader.LOG.error("JMSException: " + ex.getMessage(), ex);
						} catch (final Exception ex) { // NOCS // NOPMD
							JMSReader.LOG.error("Exception", ex);
						}
					}
				}
			});

			// start the connection to enable message delivery
			connection.start();

			JMSReader.LOG.info("JMSReader started and waits for incomming monitoring events!");
			this.block();
			JMSReader.LOG.info("Woke up by shutdown");
		} catch (final Exception ex) { // FindBugs complains but wontfix // NOCS (IllegalCatchCheck) // NOPMD
			JMSReader.LOG.error(ex.getMessage(), ex);
			retVal = false;
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (final JMSException ex) {
				JMSReader.LOG.error("Failed to close JMS", ex);
			}
		}
		return retVal;
	}

	private final void block() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public final void run() {
				JMSReader.this.unblock();
			}
		});
		try {
			this.cdLatch.await();
		} catch (final InterruptedException e) { // ignore
		}
	}

	private final void unblock() {
		this.cdLatch.countDown();
	}

	@Override
	public void terminate() {
		JMSReader.LOG.info("Shutdown of JMSReader requested.");
		this.unblock();
	}
}
