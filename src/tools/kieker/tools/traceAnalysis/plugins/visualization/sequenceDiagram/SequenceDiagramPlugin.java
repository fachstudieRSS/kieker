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

package kieker.tools.traceAnalysis.plugins.visualization.sequenceDiagram;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import kieker.analysis.plugin.annotation.InputPort;
import kieker.analysis.plugin.annotation.Plugin;
import kieker.analysis.plugin.annotation.RepositoryPort;
import kieker.common.configuration.Configuration;
import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.tools.traceAnalysis.plugins.AbstractMessageTraceProcessingPlugin;
import kieker.tools.traceAnalysis.plugins.AbstractTraceAnalysisPlugin;
import kieker.tools.traceAnalysis.systemModel.AbstractMessage;
import kieker.tools.traceAnalysis.systemModel.AbstractTrace;
import kieker.tools.traceAnalysis.systemModel.AllocationComponent;
import kieker.tools.traceAnalysis.systemModel.AssemblyComponent;
import kieker.tools.traceAnalysis.systemModel.MessageTrace;
import kieker.tools.traceAnalysis.systemModel.Signature;
import kieker.tools.traceAnalysis.systemModel.SynchronousCallMessage;
import kieker.tools.traceAnalysis.systemModel.SynchronousReplyMessage;
import kieker.tools.traceAnalysis.systemModel.repository.AllocationRepository;
import kieker.tools.traceAnalysis.systemModel.repository.SystemModelRepository;

/**
 * Refactored copy from LogAnalysis-legacy tool<br>
 * 
 * This class has exactly one input port named "in". The data which is send to
 * this plugin is not delegated in any way.
 * 
 * @author Andre van Hoorn, Nils Sommer, Jan Waller
 */
@Plugin(repositoryPorts = @RepositoryPort(name = AbstractTraceAnalysisPlugin.SYSTEM_MODEL_REPOSITORY_NAME, repositoryType = SystemModelRepository.class))
public class SequenceDiagramPlugin extends AbstractMessageTraceProcessingPlugin {
	private static final Log LOG = LogFactory.getLog(SequenceDiagramPlugin.class);

	public static final String CONFIG_OUTPUT_FN_BASE = SequenceDiagramPlugin.class.getName() + ".filename";
	public static final String CONFIG_OUTPUT_SHORTLABES = SequenceDiagramPlugin.class.getName() + ".shortlabels";
	public static final String CONFIG_OUTPUT_SDMODE = SequenceDiagramPlugin.class.getName() + ".SDMode";
	public static final String CONFIG_OUTPUT_FN_BASE_DEFAULT = "SequenceDiagram";

	/**
	 * Path to the sequence.pic macros used to plot UML sequence diagrams. The
	 * file must be in the classpath -- typically inside the jar.
	 */
	private static final String SEQUENCE_PIC_PATH = "META-INF/sequence.pic";
	private static final String SEQUENCE_PIC_CONTENT;
	private static final String ENCODING = "UTF-8";

	private final String outputFnBase;
	private final boolean shortLabels;

	/*
	 * Read Spinellis' UML macros from file META-INF/sequence.pic to the String
	 * variable sequencePicContent. This contents are copied to every sequence
	 * diagram .pic file
	 */
	static {
		final StringBuilder sb = new StringBuilder();
		boolean error = true;
		BufferedReader reader = null;

		try {
			final InputStream is = SequenceDiagramPlugin.class.getClassLoader().getResourceAsStream(SequenceDiagramPlugin.SEQUENCE_PIC_PATH);
			String line;
			reader = new BufferedReader(new InputStreamReader(is, SequenceDiagramPlugin.ENCODING));
			while ((line = reader.readLine()) != null) { // NOPMD (assign)
				sb.append(line).append("\n");
			}
			error = false;
		} catch (final IOException exc) {
			SequenceDiagramPlugin.LOG.error("Error while reading " + SequenceDiagramPlugin.SEQUENCE_PIC_PATH, exc);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (final IOException ex) {
				SequenceDiagramPlugin.LOG.error("Failed to close input stream", ex);
			}
			if (error) {
				/* sequence.pic must be provided on execution of pic2plot */
				SEQUENCE_PIC_CONTENT = "copy \"sequence.pic\";"; // NOCS (this)
			} else {
				SEQUENCE_PIC_CONTENT = sb.toString(); // NOCS (this)
			}
		}
	}

	public static enum SDModes {
		ASSEMBLY, ALLOCATION
	}

	private final SDModes sdmode;

	public SequenceDiagramPlugin(final Configuration configuration) {
		super(configuration);
		this.sdmode = SDModes.valueOf(configuration.getStringProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_SDMODE));
		this.outputFnBase = configuration.getStringProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_FN_BASE);
		this.shortLabels = configuration.getBooleanProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_SHORTLABES);
	}

	@Override
	public void printStatusMessage() {
		super.printStatusMessage();
		final int numPlots = this.getSuccessCount();
		final long lastSuccessTracesId = this.getLastTraceIdSuccess();
		System.out.println("Wrote " + numPlots + " sequence diagram" + (numPlots > 1 ? "s" : "") // NOCS (AvoidInlineConditionalsCheck)
				+ " to file" + (numPlots > 1 ? "s" : "") + " with name pattern '" + this.outputFnBase + "-<traceId>.pic'"); // NOCS (AvoidInlineConditionalsCheck)
		System.out.println("Pic files can be converted using the pic2plot tool (package plotutils)");
		System.out.println("Example: pic2plot -T svg " + this.outputFnBase + "-" + ((numPlots > 0) ? lastSuccessTracesId : "<traceId>") // NOCS
																																		// (AvoidInlineConditionalsCheck)
				+ ".pic > " + this.outputFnBase + "-" + ((numPlots > 0) ? lastSuccessTracesId : "<traceId>") + ".svg"); // NOCS (AvoidInlineConditionalsCheck)
	}

	@Override
	@InputPort(name = AbstractMessageTraceProcessingPlugin.INPUT_PORT_NAME, description = "Message traces", eventTypes = { MessageTrace.class })
	public void msgTraceInput(final MessageTrace mt) {
		try {
			SequenceDiagramPlugin.writePicForMessageTrace(SequenceDiagramPlugin.this.getSystemEntityFactory(), mt,
					SequenceDiagramPlugin.this.sdmode,
					SequenceDiagramPlugin.this.outputFnBase + "-" + ((AbstractTrace) mt).getTraceId() + ".pic", SequenceDiagramPlugin.this.shortLabels);
			SequenceDiagramPlugin.this.reportSuccess(((AbstractTrace) mt).getTraceId());
		} catch (final FileNotFoundException ex) {
			SequenceDiagramPlugin.this.reportError(((AbstractTrace) mt).getTraceId());
			SequenceDiagramPlugin.LOG.error("File not found", ex);
		} catch (final UnsupportedEncodingException ex) {
			SequenceDiagramPlugin.this.reportError(((AbstractTrace) mt).getTraceId());
			SequenceDiagramPlugin.LOG.error("Encoding not supported", ex);
		}
	}

	private static String assemblyComponentLabel(final AssemblyComponent component, final boolean shortLabels) {
		final String assemblyComponentName = component.getName();
		final String componentTypePackagePrefx = component.getType().getPackageName();
		final String componentTypeIdentifier = component.getType().getTypeName();

		final StringBuilder strBuild = new StringBuilder(assemblyComponentName).append(":");
		if (!shortLabels) {
			strBuild.append(componentTypePackagePrefx).append(".");
		} else {
			strBuild.append("..");
		}
		strBuild.append(componentTypeIdentifier);
		return strBuild.toString();
	}

	private static String allocationComponentLabel(final AllocationComponent component, final boolean shortLabels) {
		final String assemblyComponentName = component.getAssemblyComponent().getName();
		final String componentTypePackagePrefx = component.getAssemblyComponent().getType().getPackageName();
		final String componentTypeIdentifier = component.getAssemblyComponent().getType().getTypeName();

		final StringBuilder strBuild = new StringBuilder(assemblyComponentName).append(":");
		if (!shortLabels) {
			strBuild.append(componentTypePackagePrefx).append(".");
		} else {
			strBuild.append("..");
		}
		strBuild.append(componentTypeIdentifier);
		return strBuild.toString();
	}

	/**
	 * It is important NOT to use the println method but print and a manual
	 * linebreak by printing the character \n. The pic2plot tool can only
	 * process pic files with UNIX line breaks.
	 * 
	 * @param systemEntityFactory
	 * @param messageTrace
	 * @param sdMode
	 * @param ps
	 * @param shortLabels
	 */
	private static void picFromMessageTrace(final SystemModelRepository systemEntityFactory, final MessageTrace messageTrace, final SDModes sdMode,
			final PrintStream ps, final boolean shortLabels) {
		// See ticket http://samoa.informatik.uni-kiel.de:8000/kieker/ticket/208
		// dot node ID x component instance
		final Collection<AbstractMessage> messages = messageTrace.getSequenceAsVector();
		// preamble:
		ps.print(".PS" + "\n");
		ps.print(SequenceDiagramPlugin.SEQUENCE_PIC_CONTENT + "\n");
		ps.print("boxwid = 1.1;" + "\n");
		ps.print("movewid = 0.5;" + "\n");

		final Set<Integer> plottedComponentIds = new TreeSet<Integer>();

		final AllocationComponent rootAllocationComponent = AllocationRepository.ROOT_ALLOCATION_COMPONENT;
		final String rootDotId = "O" + rootAllocationComponent.getId();
		ps.print("actor(O" + rootAllocationComponent.getId() + ",\"\");" + "\n");
		plottedComponentIds.add(rootAllocationComponent.getId());

		if (sdMode == SequenceDiagramPlugin.SDModes.ALLOCATION) {
			for (final AbstractMessage me : messages) {
				final AllocationComponent senderComponent = me.getSendingExecution().getAllocationComponent();
				final AllocationComponent receiverComponent = me.getReceivingExecution().getAllocationComponent();
				if (!plottedComponentIds.contains(senderComponent.getId())) {
					ps.print("object(O" + senderComponent.getId() + ",\"" + senderComponent.getExecutionContainer().getName() + "::\",\"" // NOPMD
							+ SequenceDiagramPlugin.allocationComponentLabel(senderComponent, shortLabels) + "\");" + "\n"); // NOPMD
					plottedComponentIds.add(senderComponent.getId());
				}
				if (!plottedComponentIds.contains(receiverComponent.getId())) {
					ps.print("object(O" + receiverComponent.getId() + ",\"" + receiverComponent.getExecutionContainer().getName() + "::\",\""
							+ SequenceDiagramPlugin.allocationComponentLabel(receiverComponent, shortLabels) + "\");" + "\n");
					plottedComponentIds.add(receiverComponent.getId());
				}
			}
		} else if (sdMode == SequenceDiagramPlugin.SDModes.ASSEMBLY) {
			for (final AbstractMessage me : messages) {
				final AssemblyComponent senderComponent = me.getSendingExecution().getAllocationComponent().getAssemblyComponent();
				final AssemblyComponent receiverComponent = me.getReceivingExecution().getAllocationComponent().getAssemblyComponent();
				if (!plottedComponentIds.contains(senderComponent.getId())) {
					ps.print("object(O" + senderComponent.getId() + ",\"\",\"" + SequenceDiagramPlugin.assemblyComponentLabel(senderComponent, shortLabels) + "\");"
							+ "\n");
					plottedComponentIds.add(senderComponent.getId());
				}
				if (!plottedComponentIds.contains(receiverComponent.getId())) {
					ps.print("object(O" + receiverComponent.getId() + ",\"\",\"" + SequenceDiagramPlugin.assemblyComponentLabel(receiverComponent, shortLabels)
							+ "\");" + "\n");
					plottedComponentIds.add(receiverComponent.getId());
				}
			}
		} else { // needs to be adjusted if a new mode is introduced
			SequenceDiagramPlugin.LOG.error("Invalid mode: " + sdMode);
		}

		ps.print("step()" + "\n");
		ps.print("active(" + rootDotId + ");" + "\n");
		boolean first = true;
		for (final AbstractMessage me : messages) {
			String senderDotId = "-1";
			String receiverDotId = "-1";

			if (sdMode == SequenceDiagramPlugin.SDModes.ALLOCATION) {
				final AllocationComponent senderComponent = me.getSendingExecution().getAllocationComponent();
				final AllocationComponent receiverComponent = me.getReceivingExecution().getAllocationComponent();
				senderDotId = "O" + senderComponent.getId();
				receiverDotId = "O" + receiverComponent.getId();
			} else if (sdMode == SequenceDiagramPlugin.SDModes.ASSEMBLY) {
				final AssemblyComponent senderComponent = me.getSendingExecution().getAllocationComponent().getAssemblyComponent();
				final AssemblyComponent receiverComponent = me.getReceivingExecution().getAllocationComponent().getAssemblyComponent();
				senderDotId = "O" + senderComponent.getId();
				receiverDotId = "O" + receiverComponent.getId();
			} else { // needs to be adjusted if a new mode is introduced
				SequenceDiagramPlugin.LOG.error("Invalid mode: " + sdMode);
			}

			if (me instanceof SynchronousCallMessage) {
				final Signature sig = me.getReceivingExecution().getOperation().getSignature();
				final StringBuilder msgLabel = new StringBuilder(sig.getName()); // NOPMD (new in loop)
				msgLabel.append("(");
				final String[] paramList = sig.getParamTypeList();
				if ((paramList != null) && (paramList.length > 0)) {
					msgLabel.append("..");
				}
				msgLabel.append(")");

				if (first) {
					ps.print("async();" + "\n");
					first = false;
				} else {
					ps.print("sync();" + "\n");
				}
				ps.print("message(" + senderDotId + "," + receiverDotId + ", \"" + msgLabel.toString() + "\");" + "\n");
				ps.print("active(" + receiverDotId + ");" + "\n");
				ps.print("step();" + "\n");
			} else if (me instanceof SynchronousReplyMessage) {
				ps.print("step();" + "\n");
				ps.print("async();" + "\n");
				ps.print("rmessage(" + senderDotId + "," + receiverDotId + ", \"\");" + "\n");
				ps.print("inactive(" + senderDotId + ");" + "\n");
			} else {
				SequenceDiagramPlugin.LOG.error("Message type not supported: " + me.getClass().getName());
			}
		}
		ps.print("inactive(" + rootDotId + ");" + "\n");
		ps.print("step();" + "\n");

		for (final int i : plottedComponentIds) {
			ps.print("complete(O" + i + ");" + "\n");
		}
		ps.print("complete(" + rootDotId + ");" + "\n");

		ps.print(".PE" + "\n");
	}

	public static void writePicForMessageTrace(final SystemModelRepository systemEntityFactory, final MessageTrace msgTrace, final SDModes sdMode,
			final String outputFilename, final boolean shortLabels) throws FileNotFoundException, UnsupportedEncodingException {
		final PrintStream ps = new PrintStream(new FileOutputStream(outputFilename), false, SequenceDiagramPlugin.ENCODING);
		SequenceDiagramPlugin.picFromMessageTrace(systemEntityFactory, msgTrace, sdMode, ps, shortLabels);
		ps.flush();
		ps.close();
	}

	@Override
	protected final Configuration getDefaultConfiguration() {
		final Configuration defaultConfiguration = new Configuration();
		defaultConfiguration.setProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_FN_BASE, SequenceDiagramPlugin.CONFIG_OUTPUT_FN_BASE_DEFAULT);
		defaultConfiguration.setProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_SHORTLABES, Boolean.TRUE.toString());
		defaultConfiguration.setProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_SDMODE, SDModes.ASSEMBLY.toString());
		return defaultConfiguration;
	}

	@Override
	public Configuration getCurrentConfiguration() {
		final Configuration configuration = new Configuration();
		configuration.setProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_FN_BASE, this.outputFnBase);
		configuration.setProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_SHORTLABES, Boolean.toString(this.shortLabels));
		configuration.setProperty(SequenceDiagramPlugin.CONFIG_OUTPUT_SDMODE, this.sdmode.toString());
		return configuration;
	}
}
