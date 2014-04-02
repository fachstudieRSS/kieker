package kieker.panalysis;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kieker.common.exception.IllegalRecordFormatException;
import kieker.common.exception.MonitoringRecordException;
import kieker.common.exception.UnknownRecordTypeException;
import kieker.common.record.AbstractMonitoringRecord;
import kieker.common.record.IMonitoringRecord;
import kieker.common.record.controlflow.OperationExecutionRecord;
import kieker.panalysis.base.Filter;

public class TextLine2RecordFilter extends Filter<TextLine2RecordFilter.INPUT_PORT, TextLine2RecordFilter.OUTPUT_PORT> {

	static public enum INPUT_PORT {
		TEXT_LINE
	}

	static public enum OUTPUT_PORT {
		RECORD
	}

	private static final String CSV_SEPARATOR_CHARACTER = ";";

	private static final IllegalRecordFormatException ILLEGAL_RECORD_FORMAT_EXCEPTION = new IllegalRecordFormatException();

	private final Map<Integer, String> stringRegistry;

	private boolean ignoreUnknownRecordTypes;
	private boolean abortDueToUnknownRecordType;
	private final Set<String> unknownTypesObserved = new HashSet<String>();

	public TextLine2RecordFilter(final long id, final Map<Integer, String> stringRegistry) {
		super(id, INPUT_PORT.class, OUTPUT_PORT.class);
		this.stringRegistry = stringRegistry;
	}

	@Override
	public INPUT_PORT chooseInputPort() {
		return INPUT_PORT.TEXT_LINE;
	}

	public void execute(final INPUT_PORT inputPort) {
		final String textLine = (String) this.take(inputPort);

		try {
			final IMonitoringRecord record = this.createRecordFromLine(textLine);
			this.put(OUTPUT_PORT.RECORD, record);
		} catch (final MonitoringRecordException e) {
			this.logger.error("Could not create record from text line: '" + textLine + "'", e);
		} catch (final IllegalRecordFormatException e) {
			this.logger.error("Illegal record format: " + textLine, e);
		} catch (final MappingException e) {
			this.logger.error("", e);
		} catch (final UnknownRecordTypeException e) {
			final String classname = e.getClassName();
			if (!this.ignoreUnknownRecordTypes) {
				this.abortDueToUnknownRecordType = true;
				this.logger.error("Failed to load record type " + classname, e);
			} else if (!this.unknownTypesObserved.contains(classname)) {
				this.unknownTypesObserved.add(classname);
				this.logger.error("Failed to load record type " + classname, e); // log once for this type
			}
		}
	}

	private IMonitoringRecord createRecordFromLine(final String line) throws MonitoringRecordException, IllegalRecordFormatException, MappingException,
			UnknownRecordTypeException {
		final String[] recordFields = line.split(CSV_SEPARATOR_CHARACTER);

		if (recordFields.length < 2) {
			throw ILLEGAL_RECORD_FORMAT_EXCEPTION;
		}

		final boolean isModernRecord = recordFields[0].charAt(0) == '$';
		if (isModernRecord) {
			return this.createModernRecordFromRecordFields(recordFields);
		} else {
			return this.createLegacyRecordFromRecordFiels(recordFields);
		}
	}

	private IMonitoringRecord createModernRecordFromRecordFields(final String[] recordFields) throws MonitoringRecordException, MappingException,
			UnknownRecordTypeException {
		final Integer id = Integer.valueOf(recordFields[0].substring(1));
		final String classname = this.stringRegistry.get(id);
		if (classname == null) {
			throw new MappingException("Missing classname mapping for record type id " + "'" + id + "'");
		}
		final Class<? extends IMonitoringRecord> clazz = this.getClassByName(classname);
		final long loggingTimestamp = Long.parseLong(recordFields[1]);
		final int skipValues;
		// check for Kieker < 1.6 OperationExecutionRecords
		if ((recordFields.length == 11) && clazz.equals(OperationExecutionRecord.class)) {
			skipValues = 3;
		} else {
			skipValues = 2;
		}
		// Java 1.5 compatibility
		final String[] recordFieldsReduced = new String[recordFields.length - skipValues];
		System.arraycopy(recordFields, skipValues, recordFieldsReduced, 0, recordFields.length - skipValues);

		final IMonitoringRecord record = AbstractMonitoringRecord.createFromStringArray(clazz, recordFieldsReduced);
		// in Java 1.6 this could be simplified to
		// record = AbstractMonitoringRecord.createFromStringArray(clazz, Arrays.copyOfRange(recordFields, skipValues, recordFields.length));
		record.setLoggingTimestamp(loggingTimestamp);
		return record;
	}

	private Class<? extends IMonitoringRecord> getClassByName(final String classname) throws MonitoringRecordException, UnknownRecordTypeException {
		try {
			return AbstractMonitoringRecord.classForName(classname);
		} catch (final MonitoringRecordException ex) {
			throw new UnknownRecordTypeException("Failed to load record type " + classname, classname, ex);
		}
	}

	private IMonitoringRecord createLegacyRecordFromRecordFiels(final String[] recordFields) throws MonitoringRecordException {
		final String[] recordFieldsReduced = new String[recordFields.length - 1];
		System.arraycopy(recordFields, 1, recordFieldsReduced, 0, recordFields.length - 1);
		return AbstractMonitoringRecord.createFromStringArray(OperationExecutionRecord.class, recordFieldsReduced);
	}

}