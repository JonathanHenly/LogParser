package com.mactracker.main;

import java.time.Instant;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;

import com.mactracker.main.log.LogEntry;

/**
 * 
 * @author Group Z
 *
 */
public class MinuteOccupancy {
    
    /**
     * @author Group Z
     */
    private static class MinuteView {
        private static MinuteView head = new MinuteView();
        private MinuteView next;
        private MinuteOccupancy mo;
        
        private MinuteView() {}
        
        MinuteView(MinuteOccupancy mo) {
            this.mo = mo;
            addAfterHead(this);
        }
        
        private static void addAfterHead(MinuteView mv) {
            mv.next = head.next;
            head.next = mv;
        }
        
        
        public static MinuteView timeline() { return head.next; }
        
        public boolean hasNext() { return this.next != null; }
        
        public MinuteView next() { return this.next; }
        
        public MinuteOccupancy getOccupancy() { return this.mo; }
        
    }
    
    //
    private static List<String> bnames;
    private MinuteOccupancy active;
    private Instant mstamp; // epoch timestamp to minute
    private List<HashSet<String>> places;
    
    /**
     * 
     * @param bldgs
     * @param start
     */
    private MinuteOccupancy(Instant start, List<HashSet<String>> bldgs) {
        mstamp = start;
        places = bldgs;
    }
    
    /**
     * 
     * @param bldgs
     * @param start
     */
    public MinuteOccupancy(List<String> abbrIndexes, Instant start) {
        bnames = abbrIndexes;
        mstamp = start;
    }
    
    /**
     * 
     * @param next
     */
    private MinuteOccupancy(MinuteOccupancy mo) {
        this.mstamp = mo.mstamp.plusSeconds(60);
        
    }
    
    public long getEpochMinute() { return mstamp.getEpochSecond(); }
    
    private void sweep() {
        
    }
    
    private int addToLimbo(LogEntry entry) { return 0; }
    
    private void findEntryByMac(LogEntry entry) {
        for (HashSet<String> place : places) {
            if (place.contains(entry.getApName())) {}// return entry; }
        }
    }
    
    void tick(Deque<LogEntry> entries) {
        
    }
    
    void tick(LogEntry entry) {
        
    }
    
    void tock() {
        
    }
    
    private class RecordMaster {
        
    }
}
