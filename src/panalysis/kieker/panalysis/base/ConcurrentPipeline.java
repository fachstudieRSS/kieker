package kieker.panalysis.base;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import kieker.common.configuration.Configuration;
import kieker.panalysis.concurrent.ConcurrentWorkStealingPipe;
import kieker.panalysis.concurrent.SingleProducerSingleConsumerPipe;

public class ConcurrentPipeline {

	private final Map<IOutputPort<?, ?>, IInputPort<?, ?>> connections = new HashMap<IOutputPort<?, ?>, IInputPort<?, ?>>();
	private final Set<IStage> ioStages = new HashSet<IStage>();
	private final Map<IStage, List<IStage>> standardStages = new HashMap<IStage, List<IStage>>();

	private final int numCores = Runtime.getRuntime().availableProcessors();

	public <T> void connect(final IOutputPort<?, T> sourcePort, final IInputPort<?, T> targetPort) {
		this.connections.put(sourcePort, targetPort);
		this.addStage(sourcePort.getOwningStage());
		this.addStage(targetPort.getOwningStage());
	}

	private void addStage(final IStage owningStage) {
		if (owningStage instanceof IoStage) {
			this.ioStages.add(owningStage);
		} else {
			this.standardStages.put(owningStage, null);
		}
	}

	public void start() {
		try {
			this.cloneNonIoStages();
			this.extendIoStages();
			this.connectConcurrentStages();
			this.instantiatePipes();
		} catch (final NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void cloneNonIoStages() throws NoSuchMethodException, SecurityException {
		for (final Entry<IStage, List<IStage>> entry : this.standardStages.entrySet()) {
			final List<IStage> concurrentStages = this.createConcurrentStages(entry.getKey());
			entry.setValue(concurrentStages);
		}
	}

	private List<IStage> createConcurrentStages(final IStage stage) throws NoSuchMethodException, SecurityException {
		final List<IStage> concurrentStages = new LinkedList<IStage>();
		for (int i = 0; i < this.numCores; i++) {
			final Class<? extends IStage> stageClazz = stage.getClass();
			final Constructor<? extends IStage> constructor = stageClazz.getConstructor(Configuration.class);
			// final IStage copiedStage = constructor.newInstance(stage.getConfiguration()); // copy by reference since the configuration is read-only
			// TODO implement me
			// concurrentStages.add(copiedStage);
		}
		return concurrentStages;
	}

	private void extendIoStages() {
		// TODO Auto-generated method stub

	}

	private void connectConcurrentStages() {
		// TODO Auto-generated method stub

	}

	@SuppressWarnings("unchecked")
	private <T, S0 extends ISource, S1 extends ISink<S1>> void instantiatePipes() {
		for (final Entry<IOutputPort<?, ?>, IInputPort<?, ?>> entry : this.connections.entrySet()) {
			final IOutputPort<S0, T> sourcePort = (IOutputPort<S0, T>) entry.getKey();
			final IInputPort<S1, T> targetPort = (IInputPort<S1, T>) entry.getValue();
			this.instantiatePipe(sourcePort, targetPort);
		}
	}

	private <T, P extends IPipe<T, P>, S0 extends ISource, S1 extends ISink<S1>>
			void instantiatePipe(final IOutputPort<S0, T> sourcePort, final IInputPort<S1, T> targetPort) {
		final boolean isSourceIoStage = sourcePort.getOwningStage() instanceof IoStage;
		final boolean isTargetIoStage = targetPort.getOwningStage() instanceof IoStage;

		IPipe<T, ?> pipe;
		if (isSourceIoStage && !isTargetIoStage) {
			pipe = new ConcurrentWorkStealingPipe<T>();
		} else if (!isSourceIoStage && isTargetIoStage) {
			pipe = new SingleProducerSingleConsumerPipe<T>();
		} else if (isSourceIoStage && isTargetIoStage) {
			pipe = new MethodCallPipe<T>();
		} else {
			pipe = new ConcurrentWorkStealingPipe<T>();
		}

		pipe.source(sourcePort).target(targetPort);
	}
}
