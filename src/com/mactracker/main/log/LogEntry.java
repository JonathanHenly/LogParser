package com.mactracker.main.log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.mactracker.main.AbbreviationTrie;
import com.mactracker.main.Utils;


public class LogEntry {
    private static final int SAFE_BUMP = 0;
    
    private static AbbreviationTrie apNames;
    
    /**
     * The type of log entry that's been parsed.
     */
    public enum Type {
        SKIP, CNTRL, ASSOC_SUCCESS, DEAUTH_FROM, DEAUTH_TO;
    }
    
    
    /**
     * 
     * @param atrie
     */
    public static void setAbbreviationTrie(AbbreviationTrie atrie) {
        apNames = atrie;
    }
    
    
    /**
     * @param code
     *             the code associated with the log entry
     * @return the log entry type associated with code, or {@code null} if no
     *         type exists
     */
    static LogEntry fromCode(Integer code) { return CODES.get(code); }
    
    
    /**
     * @author Group Z
     */
    public static class Station {
        private char[] mac;
        private int hash;
        
        Station(char[] mac) { this.mac = mac; }
        
        @Override
        public int hashCode() {
            int h = hash;
            if (h == 0) {
                h = (int) Utils.FNVHash(mac, 0);
                hash = h;
            }
            
            return hash;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Station))
                return false;
            
            Station that = (Station) obj;
            if (this.mac == null && that.mac == null)
                return true;
            if (this.mac == null && that.mac != null)
                return false;
            if (this.mac != null && that.mac == null)
                return false;
            if (this.mac.length != that.mac.length)
                return false;
            
            for (int i = 0; i < this.mac.length; i++) {
                if (this.mac[i] != that.mac[i])
                    return false;
            }
            
            // passed all checks
            return true;
        }
    }
    
    /* class members */
    private final long tstamp;
    private final Type type;
    private final Station sta;
    private final int apcode;
    private final int skipcnt;
    
    /**
     * Default constructor
     */
    private LogEntry() { this(0, Type.SKIP, null, 0, 0); }
    
    /**
     * @param skipcnt
     *                the safe number of characters to skip
     */
    private LogEntry(int skipcnt) { this(0, Type.SKIP, null, 0, skipcnt); }
    
    /**
     * 
     * @param type
     *                one of {@code SKIP}, {@code DEAUTH_FROM},
     *                {@code DEAUTH_TO} or {@code ASSOC_SUCCESS}
     * @param skipcnt
     *                the safe number of characters to skip
     */
    private LogEntry(Type type, int skipcnt) {
        this(0, type, null, 0, skipcnt);
    }
    
    /**
     * @param type
     *                one of {@code SKIP}, {@code DEAUTH_FROM},
     *                {@code DEAUTH_TO} or {@code ASSOC_SUCCESS}
     * @param usmac
     *                the user's MAC address
     * @param apmac
     *                the AP's MAC address
     * @param apname
     *                the AP's building name
     * @param skipcnt
     *                the safe number of characters to skip
     */
    private LogEntry(long tstamp, Type type, char[] usmac, int apcode,
        int skipcnt) {
        this.tstamp = tstamp;
        this.type = type;
        this.sta = new Station(usmac);
        this.apcode = apcode;
        this.skipcnt = skipcnt;
    }
    
    
    /**
     * @return time since Unix epoch accurate to seconds, or 0 if this log entry
     *         is not concerned with time.
     */
    public long getTimeStamp() { return tstamp; }
    
    /**
     * @return one of {@code SKIP}, {@code DEAUTH_TO}, {@code DEAUTH_FROM} or
     *         {@code ASSOC_SUCCESS}.
     */
    public Type getType() { return type; }
    
    /**
     * Returns the MAC address of the user associated with this log entry, or
     * {@code null} if this log entry does not have or require a user MAC
     * address.
     * 
     * @return the user's MAC address or {@code null}.
     */
    public Station getStation() { return sta; }
    
    public int getApCode() { return apcode; }
    
    /**
     * Returns the building name associated with this log entry's AP if an
     * abbreviation to building name mapping exists, otherwise the raw AP name
     * will be returned.
     * 
     * @return the building name associated with this AP or the raw AP name
     */
    public String getApName() {
        return apNames.getValueFromIndex(getApCode());
    }
    
    
    /* @return this log entry's six digit code. */
    // int getCode()
    
    boolean needToParse() { return false; }
    
    
    /**
     * @return the minimum number of characters making up the message portion of
     *         this log entry.
     */
    int getSkipCount() { return skipcnt; }
    
    
    /**
     * Parses the message portion of the log entry.
     * 
     * The parameter {@code cur} will be at the starting index of the message
     * portion.
     * 
     * @param buf
     *            the buffer to read from
     * @param cur
     *            the current index in the buffer
     * @return a parsed log entry
     */
    LogEntry parse(long tstamp, final char[] buf, int cur) { return this; }
    
    @Override
    public String toString() {
        String s = "";
        
        s = String.format("%d  [ %s ]  '%s'  [%d] %s", tstamp, type.toString(),
            sta, getApCode(), getApName());
        
        return s;
    }
    
    /* Private Class Constants */
    private static final char COLON = ':';
    private static final char SPACE = ' ';
    private static final char DASH = '-';
    private static final char NEWL = '\n';
    
    
    /* Create and populate map of entry types with hardcoded six digit NOTI
     * codes and skip ahead amounts. */
    private static final Map<Integer, LogEntry> CODES;
    static {
        CODES = new HashMap<Integer, LogEntry>();
        
        // -------------------------------------------------------------------
        /* Assoc Related Section */
        
        // 501100 Assoc Success
        CODES.put(501100, new LogEntry(25) {
            @Override
            boolean needToParse() { return true; }
            
            @Override
            LogEntry parse(long tstamp, final char[] buf, int cur) {
                final byte COLONS_TILL_USER_MAC = 3;
                char[] umac = null;
                int apIndex;
                int start = cur;
                int mark;
                byte ccnt = 0; // colon count
                
                /* handle parsing of user MAC address to string */
                
                while (ccnt < COLONS_TILL_USER_MAC + 1) {
                    switch (buf[cur++]) {
                        // colon is the main delimiter for user MAC
                        case COLON:
                            ccnt += 1;
                            break;
                        
                        // single space after 3rd colon denotes user MAC
                        case SPACE:
                            if (ccnt == COLONS_TILL_USER_MAC) {
                                // mark beginning colon of station (user) mac
                                mark = cur;
                                // read until next colon (end of mac)
                                while (buf[cur++] != COLON) {}
                                
                                umac = Arrays.copyOfRange(buf, mark, cur - 1);
                                
                                ccnt += 1;
                            }
                            break;
                    }
                }
                
                /* handle parsing of AP MAC address to string */
                // skip random text and AP IP address
                while (buf[cur++] != DASH) {}
                // mark index after first occurrence of '-'
                mark = cur;
                // skip until next dash which delimits end of AP MAC address
                while (buf[cur++] != DASH) {}
                
                // we don't need the AP mac
                // amac = String.valueOf(buf, mark, cur - mark - 1);
                
                /* handle parsing of AP name, which comes directly after MAC */
                // mark index after AP MAC ending dash delimiter
                mark = cur;
                // skip until new line which denotes end of AP name
                while (buf[cur++] != NEWL) {}
                // lookup AP name's index from abbreviation and assign it
                apIndex = apNames.getValueIndex(buf, mark, cur - mark - 1);
                
                return new LogEntry(tstamp, Type.ASSOC_SUCCESS, umac, apIndex,
                    cur - start);
            }
        });
        
        // 501102 Disassoc from STA
        CODES.put(501102, new LogEntry(34) {
            @Override
            boolean needToParse() { return true; }
            
            @Override
            LogEntry parse(long tstamp, final char[] buf, int cur) {
                final byte COLONS_TILL_USER_MAC = 1;
                char[] umac = null;
                int apIndex;
                int start = cur;
                int mark;
                byte ccnt = 0; // colon count
                
                /* handle parsing of user MAC address to string */
                
                while (ccnt < COLONS_TILL_USER_MAC + 1) {
                    switch (buf[cur++]) {
                        // colon is the main delimiter for user MAC
                        case COLON:
                            ccnt += 1;
                            break;
                        
                        // single space after 1st colon denotes user MAC
                        case SPACE:
                            if (ccnt == COLONS_TILL_USER_MAC) {
                                mark = cur;
                                
                                while (buf[cur++] != COLON) {}
                                
                                umac = Arrays.copyOfRange(buf, mark, cur - 1);
                                ccnt += 1;
                            }
                            break;
                    }
                }
                
                /* handle parsing of AP MAC address to string */
                // skip random text and AP IP address
                while (buf[cur++] != DASH) {}
                // mark index after first occurrence of '-'
                mark = cur;
                // skip until next dash which delimits end of AP MAC address
                while (buf[cur++] != DASH) {}
                /* handle parsing of AP name, which comes directly after MAC */
                // mark index after AP MAC ending dash delimiter
                mark = cur;
                // skip until space which denotes end of AP name
                while (buf[cur++] != SPACE) {}
                // lookup AP name from abbreviation and assign it
                apIndex = apNames.getValueIndex(buf, mark, cur - mark - 1);
                
                // skip ahead to next entry
                while (buf[cur++] != NEWL) {}
                
                return new LogEntry(tstamp, Type.DEAUTH_FROM, umac, apIndex,
                    cur - start);
            }
        });
        
        // -------------------------------------------------------------------
        /* Deauth To Section */
        
        // 501080 Deauth to STA - Ageout AP [reason:%s]
        CODES.put(501080, new LogEntry(30) {
            @Override
            boolean needToParse() { return true; }
            
            @Override
            LogEntry parse(long tstamp, final char[] buf, int cur) {
                final byte COLONS_TILL_USER_MAC = 1;
                char[] umac = null;
                int apIndex;
                int start = cur;
                int mark;
                byte ccnt = 0; // colon count
                
                /* handle parsing of user MAC address to string */
                
                while (ccnt < COLONS_TILL_USER_MAC + 1) {
                    switch (buf[cur++]) {
                        // colon is the main delimiter for user MAC
                        case COLON:
                            ccnt += 1;
                            break;
                        
                        // single space after 3rd colon denotes user MAC
                        case SPACE:
                            if (ccnt == COLONS_TILL_USER_MAC) {
                                mark = cur;
                                while (buf[cur++] != COLON) {}
                                umac = Arrays.copyOfRange(buf, mark, cur - 1);
                                ccnt += 1;
                            }
                            break;
                    }
                }
                
                /* handle parsing of AP MAC address to string */
                // skip random text and AP IP address
                while (buf[cur++] != DASH) {}
                // mark index after first occurrence of '-'
                mark = cur;
                // skip until next dash which delimits end of AP MAC address
                while (buf[cur++] != DASH) {}
                
                // we don't need the AP's mac
                // amac = String.valueOf(buf, mark, cur - mark - 1);
                
                /* handle parsing of AP name, which comes directly after MAC */
                // mark index after AP MAC ending dash delimiter
                mark = cur;
                // skip until space which denotes end of AP name
                while (buf[cur++] != SPACE) {}
                // lookup AP name from abbreviation and assign it
                apIndex = apNames.getValueIndex(buf, mark, cur - mark - 1);
                
                // skip ahead to next entry
                while (buf[cur++] != NEWL) {}
                
                
                return new LogEntry(tstamp, Type.DEAUTH_TO, umac, apIndex,
                    cur - start);
            }
        });
        
        /* Note: all of the following codes have the same message layouts as the
         * 501080 format, only the text between or after the MACs and AP name
         * changes. */
        
        // 501081 Deauth to STA - Ageout AP [reason:%d]
        CODES.put(501081, CODES.get(501080));
        // 501098 Deauth to STA - Moved out from AP to new AP
        CODES.put(501098, CODES.get(501080));
        // 501099 Deauth to STA - Reason [resp:%s]
        CODES.put(501099, CODES.get(501080));
        // 501106 Deauth to STA - Ageout AP [func:%s]
        CODES.put(501106, CODES.get(501080));
        // 501107 Deauth to STA - AP going down
        CODES.put(501107, CODES.get(501080));
        // 501108 Deauth to STA - Configuration Change
        CODES.put(501108, CODES.get(501080));
        // 501111 Deauth to STA - Reason [resp:%d]
        CODES.put(501111, CODES.get(501080));
        
        // -------------------------------------------------------------------
        /* Deauth From Section */
        
        // 501105 Deauth from STA - with safe skip of 30
        CODES.put(501105, new LogEntry(30) {
            @Override
            boolean needToParse() { return true; }
            
            @Override
            LogEntry parse(long tstamp, final char[] buf, int cur) {
                final byte COLONS_TILL_USER_MAC = 1;
                char[] umac = null;
                int apIndex;
                int start = cur;
                int mark;
                byte ccnt = 0; // colon count
                
                /* handle parsing of user MAC address to string */
                
                while (ccnt < COLONS_TILL_USER_MAC + 1) {
                    switch (buf[cur++]) {
                        // colon is the main delimiter for user MAC
                        case COLON:
                            ccnt += 1;
                            break;
                        
                        // single space after 3rd colon denotes user MAC
                        case SPACE:
                            if (ccnt == COLONS_TILL_USER_MAC) {
                                mark = cur;
                                while (buf[cur++] != COLON) {}
                                umac = Arrays.copyOfRange(buf, mark, cur - 1);
                                ccnt += 1;
                            }
                            break;
                    }
                }
                
                /* handle parsing of AP MAC address to string */
                // skip random text and AP IP address
                while (buf[cur++] != DASH) {}
                // mark index after first occurrence of '-'
                mark = cur;
                // skip until next dash which delimits end of AP MAC address
                while (buf[cur++] != DASH) {}
                
                // we may need the AP's mac in the future
                // amac = String.valueOf(buf, mark, cur - mark - 1);
                
                /* handle parsing of AP name, which comes directly after MAC */
                // mark index after AP MAC ending dash delimiter
                mark = cur;
                // skip until space which denotes end of AP name
                while (buf[cur++] != SPACE) {}
                // lookup AP name from abbreviation and assign it
                apIndex = apNames.getValueIndex(buf, mark, cur - mark - 1);
                
                // skip ahead to next entry
                while (buf[cur++] != NEWL) {}
                
                return new LogEntry(tstamp, Type.DEAUTH_FROM, umac, apIndex,
                    cur - start);
            }
        });
        
        // 501114 Deauth from STA - same MAC and name locations as 501105 format
        CODES.put(501114, CODES.get(501105));
        
        // -------------------------------------------------------------------
        /* Valid NOIT, aka Skip Codes Section */
        
        
        /* Example of how safe skip ahead is calculated:
         * 
         * Aruba 501093 message format is: 'Auth success: [mac]: AP
         * [ip]-[bssid]-[name]' so safe skip ahead is 21 characters, but
         * stringified IP addresses use at least 7 characters ('0.0.0.0') and
         * the base64 MAC addresses are ~40 characters so we can theoretically
         * skip 21 + (7 + 40 * 2) = 108 characters, not to mention some Auth
         * messages include base64 usernames. */
        // 501093 Auth Success
        CODES.put(501093, new LogEntry(21 + SAFE_BUMP));
        
        // 501094 Auth Failure
        CODES.put(501094, new LogEntry(30 + SAFE_BUMP));
        
        // 501095 Assoc Request
        CODES.put(501095, CODES.get(501094));
        
        // 501101 Assoc Failure
        CODES.put(501101, CODES.get(501094));
        
        // 501109 Auth Request - actual skip is 31
        CODES.put(501109, CODES.get(501094));
        
        // 501110 Auth Failure
        CODES.put(501110, new LogEntry(29 + SAFE_BUMP));
        
        // 501112 Assoc Failure
        CODES.put(501112, CODES.get(501094));
        
        // 501199 User Authenticated
        CODES.put(501199, new LogEntry(56 + SAFE_BUMP));
        
        // 501218 stm_sta_assign_vlan
        CODES.put(501218, new LogEntry(60 + SAFE_BUMP));
        
        // 522008 User Authenticated
        CODES.put(522008, new LogEntry(111 + SAFE_BUMP));
        
        // 522038 User Authentication Completed Using
        CODES.put(522038, new LogEntry(57 + SAFE_BUMP));
        
        // 522275 User Authentication Failed
        CODES.put(522275, new LogEntry(103 + SAFE_BUMP));
        
    }
    
}
