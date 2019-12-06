package com.mactracker.main;

import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mactracker.main.log.LogEntry;
import com.mactracker.main.log.LogEntry.Station;


public class Analyzer {
    
    private Map<String, List<LogEntry>> pings;
    private List<LogEntry> entries;
    private List<String> abbrIndexes;
    
    /**
     * 
     * @param entries
     */
    public Analyzer(List<String> abbrIndexes, List<LogEntry> entries) {
        this.abbrIndexes = abbrIndexes;
        // create a hash map with a large prime capacity and
        pings = new HashMap<String, List<LogEntry>>(
            Utils.nextPrime(entries.size() / 4), 0.75f);
        
        this.entries = entries;
    }
    
    /**
     * 
     * @return
     */
    public MinuteOccupancy aggregateApTraffic() {
        
        for (LogEntry entry : entries) {
            System.out.println(entry);
        }
        
        return null;
    }
    
    /**
     * 
     * @param entries
     */
    public void trackMacs(Deque<LogEntry> entries) {
        LogEntry entry;
        Station mac;
        
        while ((entry = entries.pollFirst()) != null) {
            mac = entry.getStation();
            
            if (pings.containsKey(entry)) {
                
            }
        }
        
    }
}
