package gmc;

import java.util.List;

final class EASLogData {
    final String filename_;
    final List<EASLogEntry>entries_;
    final Alarms alarms_;
    long totalRequestsSize_;
    long totalResponseSize_;
    
    public static EASLogData create(String filename, List<EASLogEntry>entries, Alarms alarms){
        return new EASLogData(filename, entries, alarms);
    }
    
    private EASLogData(String filename, List<EASLogEntry>entries, Alarms alarms){
        filename_ = filename;
        entries_ = entries;
        alarms_ = alarms;
    }
    
    void process(){
        if(entries_.isEmpty()){
            return;
        }
        
        EASLogEntry priorEntry = null;
        for(int i = 0; i < entries_.size(); i++){
            EASLogEntry entry = entries_.get(i);
            entry.process(priorEntry, alarms_);
            totalRequestsSize_ += entry.requestSize_;
            totalResponseSize_ += entry.responseSize_;
            priorEntry = entry;
        }
    }
}
