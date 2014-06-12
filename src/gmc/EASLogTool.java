package gmc;

import java.io.IOException;
import java.util.List;

// TODOs:
// details for FolderSync


// This would be a more useful tool with the following enhancements:
// -identify the request by type, keep track of the count of each type of request
// -for each request, record the start time and end time. This determines the amount of 'servertime'
// -illuminate any overlapping requests (indicates concurrent requests)
// -monitor the 'deadtime' in between requests. This determines the amount of 'clienttime'
// -estimate the number of bytes transferred by: one byte per open tag, one byte per close tag + amount of "bytes"
//
// The format is:
//  alarm cmd start duration entry# request/reply size details
// Alarms:
//  * - more than one alarm detected
//  $ - change in headers/device id
//  x - server encountered an exception
//  @ - server generated no response
//  # - there was a pending response on server
//  ! - a status that is not success
//  % - overlapping request  
//
// For sync, the details are:
//   [#collections, +# -# ~#] ([CollectionId, filtertype, +# -# ~#])
// as in:
//   3 +20 -0 -0 ([5 0 +20 0 0],[1 0 +0 -0 ~0],[2 1 +0 -0 ~0])
// or: nil (meaning no payload)
public class EASLogTool {
    static int totalNumBytes = 0;
    
    public static void main(String[] args) {
        String logfileName;// = args[0];
        logfileName = "/Users/gcole/Desktop/easlogs";
//        logfileName += "/EASMailboxLog_gcole_GoodAndroid_GCSandroidc1854282661.txt";
//        logfileName += "/EASMailboxLog_gcole_GoodiPhone_9F1DB249E68BAE0A28A2B2727BA0882C.txt";
//        logfileName += "/EASMailboxLog_jsanfilippo_GoodiPhone_A9E7651296BC62A381A26B067A55A7DE.txt";
//        logfileName += "/EASMailboxLog_jsanfilippo_GoodAndroid_GCSandroidc266066307.txt";
//        logfileName += "/EASMailboxLog_skim_GoodiPhone_CEE087159099038D7B1F144A4ABF60DF.txt";
//        logfileName += "/logtest.txt";
//        logfileName += "/EASMailboxLog_gcole_GoodAndroid_GCSandroidc1854282661.txt";
//        logfileName += "/EASMailboxLog_gcole_GoodiPhone_7B4627E827FB4903A32DB8089E69A2DF.txt";
//        logfileName += "/EASMailboxLog_gcole_CloudMagic_65796250b8ef688804c925cdbe0e58a0.txt";
        logfileName += "/EASMailboxLog_gcole_Acompli_A84D91F37D5ACBFF.txt";
//        logfileName += "/20140610/EASMailboxLog_gcole_Acompli_A84D91F37D5ACBFF.txt";
//        logfileName += "/20140610/EASMailboxLog_gcole_iPhone_ApplF2NLHDGFFF9R.txt";
//        logfileName += "/20140610/EASMailboxLog_gcole_GoodAndroid_GCSandroidc1854282661.txt";
//        logfileName += "/20140610/EASMailboxLog_gcole_Acompli_A84D91F37D5ACBFF-2.txt";
//        logfileName += "/20140610/EASMailboxLog_gcole_Acompli_A84D91F37D5ACBFF-3.txt";
//        logfileName += "/20140610/EASMailboxLog_gcole_Acompli_A84D91F37D5ACBFF-4.txt";
//        logfileName += "/20140610/EASMailboxLog_gcole_Acompli_A84D91F37D5ACBFF-5.txt";
        
        
        try{
            long startTime = System.currentTimeMillis();
            EASLogParser logParser = new EASLogParser(logfileName); 
            EASLogData ld = logParser.parse();
            ld.process();
            long endTime = System.currentTimeMillis();
            dumpData(ld, logParser.lines_, endTime - startTime);            
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
        
        System.out.println("");
    }    

    static final int SECONDS_PER_SECOND = 1;
    static final int SECONDS_PER_MINUTE = SECONDS_PER_SECOND * 60;
    static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * 60;
    static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * 24;
    
    static void dumpData(EASLogData ld, long numLines, long elapsedMs){
        System.out.println(String.format("Emitting data from:%s", ld.filename_));

        if(!ld.entries_.isEmpty()){            
            String startTime = ld.entries_.get(0).startTimeStr_;
            long startedAt = ld.entries_.get(0).startDate_.getTime();
            String endTime = ld.entries_.get(ld.entries_.size()-1).endTimeStr_;
            long endedAt;
            if(endTime == null){ // if no response, then there is no end time, but there is always a start time
                endTime = ld.entries_.get(ld.entries_.size()-1).startTimeStr_;
                endedAt = ld.entries_.get(ld.entries_.size()-1).startDate_.getTime();
            }else{
                endedAt = ld.entries_.get(ld.entries_.size()-1).endDate_.getTime();
            }
            long durationSeconds = (endedAt - startedAt)/1000;
            int days = (int)durationSeconds/SECONDS_PER_DAY;
            int hours = (int)(durationSeconds - (days * SECONDS_PER_DAY)) / SECONDS_PER_HOUR;
            int minutes = (int)(durationSeconds - ((days * SECONDS_PER_DAY) + (hours * SECONDS_PER_HOUR))) / SECONDS_PER_MINUTE;
            int seconds = (int)(durationSeconds - ((days * SECONDS_PER_DAY) + (hours * SECONDS_PER_HOUR) + (minutes * SECONDS_PER_MINUTE))) / SECONDS_PER_SECOND;
            String durationStr = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
            System.out.println(String.format(" Range: %s - %s (%s)", startTime, endTime, durationStr));        
            System.out.println(String.format(" Processed %,d entries (%,d lines) in %d.%d seconds", ld.entries_.size(), numLines, elapsedMs/1000, elapsedMs%1000));
            System.out.println(String.format(" Estimate of transfered bytes %,d (requests %,d / responses %,d)", ld.totalRequestsSize_+ld.totalResponseSize_, ld.totalRequestsSize_, ld.totalResponseSize_));
            
            System.out.println(String.format("Headers:"));
            System.out.println(String.format(" User: %s", ld.entries_.get(0).headers_.get("User").replace("%40", "@").replace("%5C", "\\")));
            System.out.println(String.format(" DeviceID: %s", ld.entries_.get(0).headers_.get("DeviceID")));
            System.out.println(String.format(" DeviceType: %s", ld.entries_.get(0).headers_.get("DeviceType")));
            System.out.println(String.format(" Host: %s", ld.entries_.get(0).headers_.get("Host")));
            System.out.println(String.format(" User-Agent: %s", ld.entries_.get(0).headers_.get("User-Agent")));
            System.out.println(String.format(" MS-ASProtocolVersion: %s", ld.entries_.get(0).headers_.get("MS-ASProtocolVersion")));
            System.out.println(String.format(" X-Forwarded-For: %s", ld.entries_.get(0).headers_.get("X-Forwarded-For")));
        }

        List<Integer>alarms = Alarms.getAlarms();
        System.out.println(String.format("Alarms:"));
        int numAlarms = 0;
        for(Integer a : alarms){
            if(a.intValue() == Alarms.NoAlarm)
                continue;  // no alarms are not very interesting
            int countOfAlarms = ld.alarms_.countAlarms(a);
            numAlarms += countOfAlarms;
            System.out.println(String.format(" %3d (%c) %s ", countOfAlarms, Alarms.getAlarmSignal(a.intValue()), Alarms.getName(a)));
        }
        System.out.println(String.format(" %3d total", numAlarms));
        
        System.out.println(String.format("===================================================================="));
        System.out.println(String.format("%d log entries:", ld.entries_.size()));
        System.out.println(String.format("alarm(s) - if more than one, will be *"));
        System.out.println(String.format("| command"));
        System.out.println(String.format("| |      request date/time"));
        System.out.println(String.format("| |      |                   duration (seconds)"));
        System.out.println(String.format("| |      |                   |  logfile entrynum"));
        System.out.println(String.format("| |      |                   |  |   request size (estimate)"));
        System.out.println(String.format("| |      |                   |  |   |       response size (estimate)"));
        System.out.println(String.format("| |      |                   |  |   |       |       command details"));
        System.out.println(String.format("_ ______ ___________________ __ ___ _______ _______ _______________"));
        EASLogEntry lastEntry = null;
        for (EASLogEntry entry : ld.entries_) {
            if(lastEntry != null && lastEntry.endDate_ != null){
                long intervalSecs  = (entry.startDate_.getTime() - lastEntry.endDate_.getTime()) / 1000;
                if(intervalSecs > 2){ 
                    int numDays = (int)intervalSecs / SECONDS_PER_DAY;
                    int numHours = (int)(intervalSecs - (numDays * SECONDS_PER_DAY)) / SECONDS_PER_HOUR;
                    int numMins = (int)(intervalSecs - ((numDays * SECONDS_PER_DAY) + (numHours * SECONDS_PER_HOUR))) / SECONDS_PER_MINUTE;
                    int numSecs = (int)(intervalSecs - ((numDays * SECONDS_PER_DAY) + (numHours * SECONDS_PER_HOUR) + (numMins * SECONDS_PER_MINUTE))) / SECONDS_PER_SECOND;
                    StringBuffer delayLine = new StringBuffer("  ");
                    if(numDays > 0 || numHours > 0 || numMins > 0){
                        // long format for interval > 1 minute
                        delayLine.append("=== ");
                        if(numDays > 0){
                            delayLine.append(String.format("%d days ", numDays));
                        }
                        if(numHours > 0){
                            delayLine.append(String.format("%d hours ", numHours));
                        }
                        if(numMins > 0){
                            delayLine.append(String.format("%d mins ", numMins));
                        }
                        if(numSecs > 0){
                            delayLine.append(String.format("%d secs ", numSecs));
                        }
                        delayLine.append(" ===");
                    }else{
                        // short format for quick duration
                        for(int i = 0; i < numSecs; i++){
                            delayLine.append('-');
                        }
                    }

                    System.out.println(delayLine.toString());
                }
            }
            System.out.println(String.format("%c %-6.6s %s %2d %3d %,7d/%,7d %s", Alarms.getAlarmSignal(entry.alarms_), 
                    entry.cmd_, entry.startTimeStr_, entry.durationSecs_, entry.entryNum_, entry.requestSize_, entry.responseSize_, entry.getDetails()));
            lastEntry = entry;
        }
    }
}
