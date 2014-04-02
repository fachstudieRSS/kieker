package kieker.panalysis.examples.wordcount;

import kieker.panalysis.Distributor;
import kieker.panalysis.Merger;
import kieker.panalysis.MethodCallPipe;
import kieker.panalysis.RepeaterSource;
import kieker.panalysis.base.AbstractSource;
import kieker.panalysis.base.Analysis;

public class CountWordsAnalysis extends Analysis {

	private AbstractSource<?> startStage;

	private RepeaterSource repeaterSource;
	private DirectoryName2Files findFilesStage;
	private Distributor distributor;
	private CountWordsStage countWordsStage0;
	private CountWordsStage countWordsStage1;
	private Merger merger;
	private OutputWordsCountSink outputWordsCountStage;

	@Override
	public void init() {
		super.init();

		this.repeaterSource = new RepeaterSource(0, ".", 2000);
		this.findFilesStage = new DirectoryName2Files(1);
		this.distributor = new Distributor(4);
		this.countWordsStage0 = new CountWordsStage(2);
		this.countWordsStage1 = new CountWordsStage(6);
		this.merger = new Merger(5);
		this.outputWordsCountStage = new OutputWordsCountSink(3);

		MethodCallPipe.connect(this.repeaterSource, RepeaterSource.OUTPUT_PORT.OUTPUT, this.findFilesStage, DirectoryName2Files.INPUT_PORT.DIRECTORY_NAME);
		MethodCallPipe.connect(this.findFilesStage, DirectoryName2Files.OUTPUT_PORT.FILE, this.distributor, Distributor.INPUT_PORT.OBJECT);

		MethodCallPipe.connect(this.distributor, Distributor.OUTPUT_PORT.OUTPUT0, this.countWordsStage0,
				CountWordsStage.INPUT_PORT.FILE);
		MethodCallPipe.connect(this.distributor, Distributor.OUTPUT_PORT.OUTPUT1, this.countWordsStage1,
				CountWordsStage.INPUT_PORT.FILE);

		MethodCallPipe.connect(this.countWordsStage0, CountWordsStage.OUTPUT_PORT.WORDSCOUNT, this.merger,
				Merger.INPUT_PORT.INPUT0);
		MethodCallPipe.connect(this.countWordsStage1, CountWordsStage.OUTPUT_PORT.WORDSCOUNT, this.merger,
				Merger.INPUT_PORT.INPUT1);

		MethodCallPipe.connect(this.merger, Merger.OUTPUT_PORT.OBJECT, this.outputWordsCountStage,
				OutputWordsCountSink.INPUT_PORT.FILE_WORDCOUNT_TUPLE);

		this.startStage = this.repeaterSource;
	}

	@Override
	public void start() {
		super.start();
		this.startStage.execute();
	}

	public static void main(final String[] args) {
		final CountWordsAnalysis analysis = new CountWordsAnalysis();
		analysis.init();
		final long start = System.currentTimeMillis();
		analysis.start();
		final long end = System.currentTimeMillis();
		// analysis.terminate();
		final long duration = end - start;
		System.out.println("duration: " + duration + " ms");

		System.out.println("repeaterSource: " + (analysis.repeaterSource.getOverallDuration() - analysis.findFilesStage.getOverallDuration()) + " ms");
		System.out.println("findFilesStage: " + (analysis.findFilesStage.getOverallDuration() - analysis.countWordsStage0.getOverallDuration()) + " ms");
		System.out.println("countWordsStage0: " + (analysis.countWordsStage0.getOverallDuration() - analysis.outputWordsCountStage.getOverallDuration()) + " ms");
		System.out.println("countWordsStage1: " + (analysis.countWordsStage1.getOverallDuration() - analysis.outputWordsCountStage.getOverallDuration()) + " ms");
		System.out.println("outputWordsCountStage: " + analysis.outputWordsCountStage.getOverallDuration() + " ms");

		System.out.println("findFilesStage: " + analysis.findFilesStage.getNumFiles());
		System.out.println("outputWordsCountStage: " + analysis.outputWordsCountStage.getNumFiles());
	}
}