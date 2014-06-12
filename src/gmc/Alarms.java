package gmc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Alarms extends HashMap<Integer, Integer>{
    static final int NoAlarm = 0x00; 
    static final int ServerExceptionAlarm = 0x01; 
    static final int NoServerResponseAlarm = 0x02;
    static final int HeadersChangedAlarm = 0x04;
    static final int PendingResponseAlarm = 0x08;
    static final int NotSuccessAlarm = 0x10;
    static final int ConcurrentEntriesAlarm = 0x20;
    static final int NoStartTimeAlarm = 0x40;
    
    int add(int existingAlarms, int alarmToAdd){
        Integer k = new Integer(alarmToAdd);
        Integer currentCount = get(k);
        if(currentCount == null){
            currentCount = new Integer(0);
        }
        currentCount = new Integer(currentCount.intValue()+1);
        put(k, currentCount);
        
        return existingAlarms | alarmToAdd; 
    }
    
    int remove(int existingAlarms, int alarmToRemove){
        if((existingAlarms & alarmToRemove) != alarmToRemove)
            return existingAlarms;
        
        Integer k = new Integer(alarmToRemove);
        Integer currentCount = get(k);
        assert currentCount != null; 
        currentCount = new Integer(currentCount.intValue()-1);
        put(k, currentCount);
        
        return existingAlarms & ~ alarmToRemove; 
    }
    
    int countAlarms(Integer alarmKey){
        Integer currentCount = get(alarmKey);
        if(currentCount == null)
            return 0;
        return currentCount.intValue();
    }
    
    static List<Integer> getAlarms(){
        List<Integer>rv = new ArrayList<Integer>();
        rv.add(NoAlarm);
        rv.add(ServerExceptionAlarm);
        rv.add(NoServerResponseAlarm);
        rv.add(HeadersChangedAlarm);
        rv.add(PendingResponseAlarm);
        rv.add(NotSuccessAlarm);
        rv.add(ConcurrentEntriesAlarm);
        rv.add(NoStartTimeAlarm);
        return rv;        
    }

    static String getName(int alarm){        
        switch(alarm){
            case NoAlarm:
                return "None";
            case ServerExceptionAlarm:
                return "Server Exception";
            case NoServerResponseAlarm:
                return "No Server Response";
            case HeadersChangedAlarm:
                return "Headers Changed";
            case PendingResponseAlarm:
                return "Pending Response";
            case NotSuccessAlarm:
                return "Error Response";
            case ConcurrentEntriesAlarm:
                return "Overlapping Requests";
            case NoStartTimeAlarm:
                return "No Start Time";
        }
        
        assert false;
        return null;
    }
    
    static char getAlarmSignal(int alarms){        
        switch(alarms){
            case NoAlarm:
                return ' ';
            case ServerExceptionAlarm:
                return 'x';
            case NoServerResponseAlarm:
                return '@';
            case HeadersChangedAlarm:
                return '$';
            case PendingResponseAlarm:
                return '#';
            case NotSuccessAlarm:
                return '!';
            case ConcurrentEntriesAlarm:
                return '%';
            case NoStartTimeAlarm:
                return '-';
        }
        
        // multiple alarms!
        return '*';
    }
}
