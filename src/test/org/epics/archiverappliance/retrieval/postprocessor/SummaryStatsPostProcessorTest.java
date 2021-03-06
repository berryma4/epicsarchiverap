package org.epics.archiverappliance.retrieval.postprocessor;

import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;

import org.apache.log4j.Logger;
import org.epics.archiverappliance.Event;
import org.epics.archiverappliance.common.TimeUtils;
import org.epics.archiverappliance.common.YearSecondTimestamp;
import org.epics.archiverappliance.config.ArchDBRTypes;
import org.epics.archiverappliance.config.PVTypeInfo;
import org.epics.archiverappliance.data.ScalarValue;
import org.epics.archiverappliance.engine.membuf.ArrayListEventStream;
import org.epics.archiverappliance.retrieval.CallableEventStream;
import org.epics.archiverappliance.retrieval.RemotableEventStreamDesc;
import org.epics.archiverappliance.retrieval.postprocessors.Mean;
import org.epics.archiverappliance.utils.simulation.SimulationEvent;
import org.junit.Test;

/**
 * The SummaryStatsPostProcessor provides the framework code for computing average, sigma etc on the server side.
 * This tests the framework itself; specifically, it tests that binning works as expected and fills all the bins.
 * The ArchiveViewer relies on this behavior to implement formulae.
 * @author mshankar
 *
 */
public class SummaryStatsPostProcessorTest {
	private static Logger logger = Logger.getLogger(SummaryStatsPostProcessorTest.class.getName());
	private String pvName = "Test_SummaryStats1";


	/**
	 * Generate data from Jun 1 for a couple of months; one every two days.
	 * Ask for summary with a bin of one day and different periods and see that we get appropriate results.
	 * @throws Exception
	 */
	@Test
	public void testBeforeAndAfter() throws Exception {
		short currentYear = TimeUtils.getCurrentYear();
		int totalDays = 60;
		YearSecondTimestamp startOfSamples = TimeUtils.convertToYearSecondTimestamp(TimeUtils.convertFromISO8601String(currentYear + "-06-01T10:00:00.000Z"));
		for(int sampleIntervalInHours = 3; sampleIntervalInHours < 24*4; sampleIntervalInHours+=3) {
			int totSamples = totalDays*24/sampleIntervalInHours;
			logger.info("Creating " + totSamples + " samples " + sampleIntervalInHours + " hours apart");
			ArrayListEventStream testData = new ArrayListEventStream(0, new RemotableEventStreamDesc(ArchDBRTypes.DBR_SCALAR_DOUBLE, pvName, currentYear));
			for(int s = 0; s < totSamples; s++) {
				testData.add(new SimulationEvent(startOfSamples.getSecondsintoyear() + s*sampleIntervalInHours*60*60, currentYear, ArchDBRTypes.DBR_SCALAR_DOUBLE, new ScalarValue<Double>((double) s)));
			}

			{ 
				Mean meanProcessor = new Mean();
				meanProcessor.initialize("mean_"+24*60*60, pvName);
				Timestamp start = TimeUtils.convertFromISO8601String(currentYear + "-06-03T10:00:00.000Z");
				Timestamp end   = TimeUtils.convertFromISO8601String(currentYear + "-06-23T10:00:00.000Z");
				meanProcessor.estimateMemoryConsumption(pvName, new PVTypeInfo(pvName, ArchDBRTypes.DBR_SCALAR_DOUBLE, true, 1), start, end, null);
				meanProcessor.wrap(CallableEventStream.makeOneStreamCallable(testData, null, false)).call();

				int eventCount = 0;
				Timestamp previousTimeStamp = TimeUtils.convertFromYearSecondTimestamp(startOfSamples); 
				for(Event e : meanProcessor.getConsolidatedEventStream()) {
					logger.debug(TimeUtils.convertToISO8601String(e.getEventTimeStamp()) + "=" + e.getSampleValue().toString());
					Timestamp eventTs = e.getEventTimeStamp();
					assertTrue("Event timestamp " + TimeUtils.convertToISO8601String(eventTs) + " is the same or after previous timestamp " + TimeUtils.convertToISO8601String(previousTimeStamp), 
							eventTs.after(previousTimeStamp));
					previousTimeStamp = eventTs;
					eventCount++;
				}
				assertTrue("Expected around 21 events got " + eventCount, eventCount >= 19 && eventCount <= 22);
			}

			// Test where we are missing the starting bin.
			{ 
				Mean meanProcessor = new Mean();
				meanProcessor.initialize("mean_"+24*60*60, pvName);
				Timestamp start = TimeUtils.convertFromISO8601String(currentYear + "-06-04T10:00:00.000Z");
				Timestamp end   = TimeUtils.convertFromISO8601String(currentYear + "-06-23T10:00:00.000Z");
				meanProcessor.estimateMemoryConsumption(pvName, new PVTypeInfo(pvName, ArchDBRTypes.DBR_SCALAR_DOUBLE, true, 1), start, end, null);
				meanProcessor.wrap(CallableEventStream.makeOneStreamCallable(testData, null, false)).call();

				int eventCount = 0;
				Timestamp previousTimeStamp = TimeUtils.convertFromYearSecondTimestamp(startOfSamples); 
				for(Event e : meanProcessor.getConsolidatedEventStream()) {
					logger.debug(TimeUtils.convertToISO8601String(e.getEventTimeStamp()) + "=" + e.getSampleValue().toString());
					Timestamp eventTs = e.getEventTimeStamp();
					assertTrue("Event timestamp " + TimeUtils.convertToISO8601String(eventTs) + " is the same or after previous timestamp " + TimeUtils.convertToISO8601String(previousTimeStamp), 
							eventTs.after(previousTimeStamp));
					previousTimeStamp = eventTs;
					eventCount++;
				}
				assertTrue("Expected around 20 events got " + eventCount, eventCount >= 17 && eventCount <= 22);
			}
			
			// Test where we are missing data in the start.
			{ 
				Mean meanProcessor = new Mean();
				meanProcessor.initialize("mean_"+24*60*60, pvName);
				Timestamp start = TimeUtils.convertFromISO8601String(currentYear + "-05-21T10:00:00.000Z");
				Timestamp end   = TimeUtils.convertFromISO8601String(currentYear + "-06-21T10:00:00.000Z");
				meanProcessor.estimateMemoryConsumption(pvName, new PVTypeInfo(pvName, ArchDBRTypes.DBR_SCALAR_DOUBLE, true, 1), start, end, null);
				meanProcessor.wrap(CallableEventStream.makeOneStreamCallable(testData, null, false)).call();

				int eventCount = 0;
				Timestamp previousTimeStamp = start; 
				for(Event e : meanProcessor.getConsolidatedEventStream()) {
					logger.debug(TimeUtils.convertToISO8601String(e.getEventTimeStamp()) + "=" + e.getSampleValue().toString());
					Timestamp eventTs = e.getEventTimeStamp();
					assertTrue("Event timestamp " + TimeUtils.convertToISO8601String(eventTs) + " is the same or after previous timestamp " + TimeUtils.convertToISO8601String(previousTimeStamp), 
							eventTs.after(previousTimeStamp));
					previousTimeStamp = eventTs;
					eventCount++;
				}
				assertTrue("Expected around 21 events got " + eventCount, eventCount >= 17 && eventCount <= 23);
			}

		}
	}
}
