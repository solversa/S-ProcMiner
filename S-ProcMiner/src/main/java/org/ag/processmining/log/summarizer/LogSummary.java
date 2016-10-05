package org.ag.processmining.log.summarizer;

/**
 * @author ahmed
 */

import org.ag.processmining.log.model.*;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.DoubleFunction;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.util.StatCounter;
import org.joda.time.DateTime;
import scala.Tuple2;

import java.io.Serializable;
import java.util.*;

import static org.ag.processmining.log.summarizer.SparkUtils.EVENT_CLASSES_GETTER;
import static org.ag.processmining.log.summarizer.SparkUtils.MAP_TO_CASE_ID_PROC_INSTANCE;

/**
 *
 */
public class LogSummary implements Serializable {

    static final long serialVersionUID = 1L;
    Map<Tuple2<Originator, EventClass>, Long> mapOriginatorEventClassOccurences;
    /**
     * The name of the logs (e.g. name of the application that generated the
     * logs)
     */
    private String logName = null;
    /**
     * The description of the logs (e.g. description of the application that
     * generated the logs)
     */
    private String logDescription = null;
    /**
     * The time frame of the process instances in the log
     */
    private TimeFrame processTimeFrame = null;
    /*
        The total number of events of the log
     */
    private long numberOfEvents = 0;
    /**
     * The total number of process instances contained in a log.
     */
    private long numberOfProcessInstances = 0;

    /*
    Case duration stats
     */
    private StatCounter caseDurationStats ;

    /*
    Case size stats
     */
    private StatCounter caseSizeStats ;

    /*
    Histogram of Events over time
     */
    Map<DateTime, Long> eventsOverTime ;
    /**
     * Mapping from event classes that start a process instance to the number of
     * process instances actually start a process instance
     */
    private Map<EventClass, Long> startingLogEvents = null;
    /**
     * Mapping from event classes that end a process instance to the number of
     * process instances actually end a process instance
     */
    private Map<EventClass, Long> endingLogEvents = null;
    /**
     * Mapping from event classes to the number of processes they occured in
     */
    private Map<EventClass, Long> eventClassOccurences = null;
    /**
     * Log originators alphabitically ordered by their name
     */
    private TreeSet<Originator> originators = null;
    /**
     * Mapping from originator to the event classes they execute
     */
    private Map<Originator, Long> originatorOccurences = null;


    private TreeSet<EventClass> eventClasses;

    /**
     * Creates a new log summary.
     *
     * @param logName        of the summarized log.
     * @param logDescription Description of the summarized log.
     */
    public LogSummary(String logName, String logDescription) {
        this.logName = logName;
        this.logDescription = logDescription;
    }

    /**
     * Creates a new, empty and initialized lightweight log summary instance.
     */
    public LogSummary() {
        this("", "");
    }

    public static LogSummary buildSummary(JavaSparkContext sc, String appliName, String appliDesc, String sourceFile, String[] event_attributes, AttributeMapping att_map) {

        LogSummary ls = new LogSummary(appliName, appliDesc);
        JavaRDD<String> RDDSrc = sc.textFile(sourceFile);
        // Building Summary data
        JavaPairRDD<CaseId, Event> CASE_ID_EVENT_MAP = RDDSrc.mapToPair(new SparkUtils.MapToCaseIdEvent(att_map, event_attributes));
        JavaPairRDD<CaseId, ProcInstance> CASE_ID_PROC_INSTANCE = CASE_ID_EVENT_MAP.groupByKey( ).mapToPair(MAP_TO_CASE_ID_PROC_INSTANCE);
        /**
         *************************************************************************
         *************************************************************************
         */
        // Number of process instance
        ls.setNumberOfProcessInstances(CASE_ID_PROC_INSTANCE.count( ));

        // Number of events
        ls.setNumberOfEvents(RDDSrc.count( ));

        // Event classes
        ls.setEventClassOccurences(CASE_ID_EVENT_MAP.map(EVENT_CLASSES_GETTER).countByValue( ));

        // Mean, Max, Min, Std duration of cases
        ls.caseDurationStats = CASE_ID_PROC_INSTANCE.mapToDouble(new DoubleFunction<Tuple2<CaseId, ProcInstance>>( ) {
            @Override
            public double call(Tuple2<CaseId, ProcInstance> t) throws Exception {
                return t._2( ).getDuration( );
            }
        }).stats( );
        // Mean, Max, Min and Std of case size (number of events)
        ls.caseSizeStats = CASE_ID_PROC_INSTANCE.mapToDouble(new DoubleFunction<Tuple2<CaseId, ProcInstance>>( ) {
            @Override
            public double call(Tuple2<CaseId, ProcInstance> t) throws Exception {
                return t._2( ).getSize();
            }
        }).stats( );

        // First and Last event date
        DateTime logStartDate = CASE_ID_PROC_INSTANCE.map(new Function<Tuple2<CaseId, ProcInstance>, DateTime>( ) {
            @Override
            public DateTime call(Tuple2<CaseId, ProcInstance> t) throws Exception {
                return t._2().getStartTS();
            }
        }).min(new DateTimeComparator()) ;

        DateTime logEndDate = CASE_ID_PROC_INSTANCE.map(new Function<Tuple2<CaseId, ProcInstance>, DateTime>( ) {
            @Override
            public DateTime call(Tuple2<CaseId, ProcInstance> t) throws Exception {
                return t._2().getEndTS();
            }
        }).max(new DateTimeComparator()) ;

        System.out.println("Start process: " + logStartDate) ;
        System.out.println("End process: " + logEndDate) ;
        ls.processTimeFrame = new TimeFrame(logStartDate,logEndDate) ;

        // Events over time
        ls.eventsOverTime = CASE_ID_EVENT_MAP.map(new Function<Tuple2<CaseId, Event>, DateTime>( ) {
            @Override
            public DateTime call(Tuple2<CaseId, Event> t) throws Exception {
                DateTime dt = t._2( ).getStartDate() ;
                return new DateTime(dt.getYear(), dt.getMonthOfYear(), dt.getDayOfMonth(),0, 0) ;
            }
        }).countByValue( );

        // Active cases over time

        /*
        // Start event class occurences

        Map<EventClass, Long> start_event_class_occurences = CASE_ID_PROC_INSTANCE.map(START_EVENT_CLASSES).countByValue( );
        ls.setStartingLogEvents(start_event_class_occurences);

        // End event class occurences
        Map<EventClass, Long> end_event_class_occurences = CASE_ID_PROC_INSTANCE.map(END_EVENT_CLASSES).countByValue( );
        ls.setEndingLogEvents(end_event_class_occurences);

        // Originator occurences
        Map<Originator, Long> event_originator_occurences = CASE_ID_EVENT_MAP.map(EVENT_ORIGINATOR).countByValue( );
        ls.setOriginatorOccurences(event_originator_occurences);

        // Originator-EventClass occurences
        Map<Tuple2<Originator, EventClass>, Object> mapOriginatorsToEventClassesOccs = CASE_ID_EVENT_MAP.mapToPair(ORIGINATOR_EVENT_CLASS_OCCURENCES).countByKey( );
        ls.setMapOriginatorsEventClassesOccurences(mapOriginatorsToEventClassesOccs);

        // Case Statistics

        */
        ls.print();
        return ls;
    }


    /**
     * @return the logName
     */
    public String getLogName() {
        return logName;
    }

    /**
     * @param logName the logName to set
     */
    public void setLogName(String logName) {
        this.logName = logName;
    }

    /**
     * @return the logDescription
     */
    public String getLogDescription() {
        return logDescription;
    }

    /**
     * @param logDescription the logDescription to set
     */
    public void setLogDescription(String logDescription) {
        this.logDescription = logDescription;
    }

    /**
     * @return the processTimeFrame
     */
    public TimeFrame getProcessTimeFrame() {
        return processTimeFrame;
    }

    /**
     * @param processTimeFrame the processTimeFrame to set
     */
    public void setProcessTimeFrame(TimeFrame processTimeFrame) {
        this.processTimeFrame = processTimeFrame;
    }

    /**
     * @return the numberOfEvents
     */
    public long getNumberOfEvents() {
        return numberOfEvents;
    }

    /**
     * @param numberOfEvents the numberOfEvents to set
     */
    public void setNumberOfEvents(long numberOfEvents) {
        this.numberOfEvents = numberOfEvents;
    }

    /**
     * @return the numberOfProcessInstances
     */
    public long getNumberOfProcessInstances() {
        return numberOfProcessInstances;
    }

    /**
     * @param numberOfProcessInstances the numberOfProcessInstances to set
     */
    public void setNumberOfProcessInstances(long numberOfProcessInstances) {
        this.numberOfProcessInstances = numberOfProcessInstances;
    }

    /**
     * @return the eventClasses
     */
    public TreeSet<EventClass> getEventClasses() {
        if (this.eventClasses == null) {
            this.eventClasses = new TreeSet<>(eventClassOccurences.keySet( ));
        }
        return this.eventClasses;
    }

    /**
     * @param eventClasses the eventClasses to set
     */
    public void setEventClasses(TreeSet<EventClass> eventClasses) {
        this.eventClasses = eventClasses;
    }

    /**
     * @return the startingLogEvents
     */
    public Map<EventClass, Long> getStartingLogEvents() {
        return startingLogEvents;
    }

    /**
     * @param startingLogEvents the startingLogEvents to set
     */
    public void setStartingLogEvents(Map<EventClass, Long> startingLogEvents) {
        this.startingLogEvents = startingLogEvents;
    }

    /**
     * @return the endingLogEvents
     */
    public Map<EventClass, Long> getEndingLogEvents() {
        return endingLogEvents;
    }

    /**
     * @param endingLogEvents the endingLogEvents to set
     */
    public void setEndingLogEvents(Map<EventClass, Long> endingLogEvents) {
        this.endingLogEvents = endingLogEvents;
    }

    /**
     * @return the mapEventClassToProcessOccurences
     */
    public Map<EventClass, Long> getEventClassesOccurences() {
        return eventClassOccurences;
    }

    /**
     * @param eventClassOccurences
     */
    public void setEventClassOccurences(Map<EventClass, Long> eventClassOccurences) {
        this.eventClassOccurences = eventClassOccurences;
    }

    /**
     * @return the originators
     */
    public TreeSet<Originator> getOriginators() {
        return originators;
    }

    /**
     * @param originators the originators to set
     */
    public void setOriginators(TreeSet<Originator> originators) {
        this.originators = originators;
    }

    /**
     * @param originator add an originator
     */
    public void addOriginator(Originator originator) {
        this.originators.add(originator);
    }

    /**
     * @return the occurences of originators ok
     */
    public Map<Originator, Long> getOriginatorOccurences() {
        return this.originatorOccurences;
    }

    /*
    * set the occurences of originators
     */
    public void setOriginatorOccurences(Map<Originator, Long> orgOcc) {
        this.originatorOccurences = orgOcc;
        this.originators = new TreeSet<>(orgOcc.keySet( ));
    }

    /**
     * @return the mapOriginatorsToEventClasses
     */
    public Map<Tuple2<Originator, EventClass>, Long> getMapOriginatorsEventClassesOccurences() {
        return mapOriginatorEventClassOccurences;
    }

    /**
     * @param mapOriginatorsToEventClasses the mapOriginatorsToEventClasses to
     *                                     set
     */
    public void setMapOriginatorsEventClassesOccurences(Map<Tuple2<Originator, EventClass>, Object> mapOriginatorsToEventClasses) {
        this.mapOriginatorEventClassOccurences = new HashMap<>( );
        for (Map.Entry<Tuple2<Originator, EventClass>, Object> e : mapOriginatorsToEventClasses.entrySet( )) {
            this.mapOriginatorEventClassOccurences.put(e.getKey( ), (Long) e.getValue( ));
        }
    }

    public void print() {
        System.out.println("Application name: " + this.logName);
        System.out.println("Application description: " + this.logDescription);
        System.out.println("Number of events: " + this.numberOfEvents);
        System.out.println("Number of cases: " + this.numberOfProcessInstances);
        System.out.println("Number of activities: " + this.getEventClasses( ).size( ));
        System.out.println("Case duration: (min," + this.caseDurationStats.min() +
                            "), (max, " + this.caseDurationStats.max() +
                            "), (mean, " + this.caseDurationStats.mean() +
                            "), (std, " + this.caseDurationStats.stdev() + ")"
                            ) ;

        System.out.println("Case size: (min," + this.caseSizeStats.min() +
                "), (max, " + this.caseSizeStats.max() +
                "), (mean, " + this.caseSizeStats.mean() +
                "), (std, " + this.caseSizeStats.stdev() + ")"
        ) ;

        System.out.println("Log Time Frame (start ts,end ts): (" + this.getProcessTimeFrame( ).getStartDate( ) + " , " + this.getProcessTimeFrame( ).getEndDate( ) + ")");

        System.out.println("Histogram of Events over time") ;
        for (Map.Entry<DateTime,Long> e : this.eventsOverTime.entrySet()){
            System.out.println(e.getKey() + " -> " + e.getValue()) ;
        }


        /*
        System.out.println("Event class occurences:");
        System.out.println(this.eventClassOccurences);
        System.out.println("Start Event class occurences");
        System.out.println(this.startingLogEvents);
        System.out.println("End Event class occurences");
        System.out.println(this.endingLogEvents);
        System.out.println("Number of originators: " + this.originators.size( ));
        System.out.println("Orignator occurences");

        for (Map.Entry<Originator, Long> e : getOriginatorOccurences( ).entrySet( )) {
            System.out.println(e.getKey( ) + "," + e.getValue( ));
        }
        System.out.println("Occurences by (Originator and event class)");
        for (Map.Entry<Tuple2<Originator, EventClass>, Long> e : getMapOriginatorsEventClassesOccurences( ).entrySet( )) {
            System.out.println(e.getKey( )._1( ) + "," + e.getKey( )._2( ) + "," + e.getValue( ));
        }
        */
    }

    private static class DoubleComparator implements Comparator<Double>, Serializable {
        @Override
        public int compare(Double o1, Double o2) {
            return o1.compareTo(o2);
        }
    }

    private static class DateTimeComparator implements Comparator<DateTime>, Serializable {
        @Override
        public int compare(DateTime o1, DateTime o2) {
            return o1.compareTo(o2);
        }
    }
}
