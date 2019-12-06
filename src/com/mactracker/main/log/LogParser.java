package com.mactracker.main.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Somewhat of a god object, but performance increase and centralization of
 * control are more important than maintainability and programming elegance.
 * 
 * @author Group Z
 */
public class LogParser {
    
    /* parser state related constants */
    private static final int NEW_ENTRY = 0;
    private static final int PARSE_HEAD = 1;
    private static final int PARSE_MSG = 1 << 1;
    private static final int CTRLER_ENTRY = 1 << 2;
    private static final int CODE_READ_STATE = 1 << 3;
    private static final int VALID_NOTI_STATE = 1 << 4;
    private static final int SKIP_ENTRY = 1 << 5;
    
    private static final int TSTAMP_SECT = 1 << 6;
    private static final int TSTAMP_SPACE = 1 << 7;
    private static final int CARR_SECT = 1 << 8;
    private static final int BRACK_SECT = 1 << 9;
    private static final int PROC_SECT = 1 << 10;
    
    private static final int ENTRY_SECT_MASK = CARR_SECT | BRACK_SECT
        | PROC_SECT;
    
    /* end state related section */
    
    /* log entry offset and delimiter constants */
    private static final char ENTRY_DELIM = '\n';
    private static final char COLON = ':';
    private static final char SPACE = ' ';
    
    private static final char CARROT_OPEN = '<';
    private static final char CARROT_CLOSE = '>';
    private static final char BRACKET_OPEN = '[';
    private static final char BRACKET_CLOSE = ']';
    private static final char PROCESS_DELIM = '|';
    
    private static final int NUM_TSTAMP_COLONS = 3;
    private static final int COLONS_TILL_CODE = 4;
    private static final int CODE_LENGTH = 6;
    private static final char NOTI_LEADING_DIGIT = '5';
    private static final int MAGIC_CHARS_TO_INT_OFFSET = '0' * 111111;
    
    /* DateTimeFormatter for parsing timestamps from log entries */
    private static final DateTimeFormatter dtf = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    /* controller IPs - 10.47.128.140 | 10.47.0.22 | 10.47.0.23 | 10.47.0.32 |
     * 10.47.0.33 */
    
    boolean outputDiagnostics;
    boolean outputDebug;
    
    /* diagnostics */
    private long startTime = 0;
    private int lineCount; // line count
    private int skipNotiCount;
    private int ctrlCount;
    private int assocSuccessCount;
    private int deauthFromCount;
    private int deauthToCount;
    private int httpdErrorCount;
    private int otherErrorCount;
    private int illFormatCount;
    private int nonNotiCount;
    
    // used to debug parsing
    StringBuilder debugAll = new StringBuilder();
    StringBuilder debugFocus = new StringBuilder();
    
    private void debugEntry(StringBuilder sb, String tag) {
        if (!outputDebug)
            return;
        sb.append(String.format("%d  %s  -", lineCount, tag))
            .append(buf, start, cur - start).append('\n');
    }
    
    
    /* tells this instance to output diagnostic information after parsing */
    public LogParser outputDiagnostics() {
        outputDiagnostics = true;
        return this;
    }
    
    /* tells this instance to write debug files after parsing */
    public LogParser outputDebug() {
        outputDiagnostics = true;
        return this;
    }
    
    /**
     * 
     */
    public List<LogEntry> parse() {
        List<LogEntry> entries = new LinkedList<LogEntry>();
        LogEntry entry = null;
        
        // diagnostics
        startTime = System.nanoTime();
        
        // fill buffer initially
        fillBuffer();
        // continue with parsing if the file is not empty
        boolean parsing = (charsRead != EOF);
        while (parsing) {
            
            state = PARSE_HEAD | TSTAMP_SECT;
            entry = parseEntry();
            
            if (entry != null) {
                switch (entry.getType()) {
                    case SKIP:
                        skipNotiCount += 1;
                        break;
                    
                    case ASSOC_SUCCESS:
                        assocSuccessCount += 1;
                        entries.add(entry);
                        break;
                    
                    case DEAUTH_FROM:
                        deauthFromCount += 1;
                        entries.add(entry);
                        break;
                    
                    case DEAUTH_TO:
                        deauthToCount += 1;
                        entries.add(entry);
                        break;
                }
                
            }
            
            // diagnostics
            lineCount += 1;
            
            // refill buf only if needed
            if (charsRead != EOF && cur + SAFE_MAX_ENTRY_LENGTH > end) {
                refillBuffer();
            }
            
            parsing = (cur < end) || (charsRead != EOF);
        }
        
        // diagnostics
        if (outputDiagnostics)
            outputDiagnostics(System.out);
        
        // debugging
        if (outputDebug)
            writeDebugInfo();
        
        return entries;
    }
    
    /* Outputs diagnostic information. */
    private void outputDiagnostics(PrintStream out) {
        int totalEntriesRead = httpdErrorCount + otherErrorCount
            + illFormatCount + nonNotiCount + skipNotiCount + assocSuccessCount
            + deauthFromCount + deauthToCount;
        
        out.println();
        out.println("Number of lines read:      " + lineCount);
        out.println("Number of entries read:    " + totalEntriesRead);
        out.println("  Assoc Success Entries:   " + assocSuccessCount);
        out.println(
            "  Deauth From/To Entries:  " + (deauthFromCount + deauthToCount));
        out.println("    Deauth From Entries:    " + deauthFromCount);
        out.println("    Deauth To Entries:      " + deauthToCount);
        out.println("  Assoc-Deauth Delta:      "
            + (assocSuccessCount - (deauthFromCount + deauthToCount)));
        out.println();
        out.println("  Controller Entries:      " + ctrlCount);
        out.println();
        
        out.println("  httpd error entries:     " + httpdErrorCount);
        out.println("  other error entries:     " + otherErrorCount);
        out.println("  ill-formatted entries:   " + illFormatCount);
        out.println();
        
        out.println("  non-NOTI entries:        " + nonNotiCount);
        out.println("  skipped NOTI entries:    " + skipNotiCount);
        out.println();
        
        final double SECONDS_DIVIDEND = 1000000000.0;
        out.printf("Time taken:  %.4f seconds%n%n",
            (System.nanoTime() - startTime) / SECONDS_DIVIDEND);
    }
    
    private void writeDebugInfo() {
        try {
            FileWriter all = new FileWriter(new File("./zout/debug_all.txt"));
            all.write(debugAll.toString());
            all.flush();
            all.close();
            
            FileWriter focus = new FileWriter(
                new File("./zout/debug_focus.txt"));
            focus.write(debugFocus.toString());
            focus.flush();
            focus.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /* helper function to output errors while parsing */
    private void errorUnexpectChar(String s, int lct, int index) {
        System.err.printf("Unexpected '%s' on line: %d  index: %d%n", s, lct,
            index);
    }
    
    
    /* reader and buffer constants */
    
    // a safe max entry length approximation
    private static final int SAFE_MAX_ENTRY_LENGTH = 1024;
    
    // default buffer length is 10Mb (Java char is 16 bits)
    private static final int DEFAULT_BUFF_LENGTH = 5 * 1024 * 1024;
    
    // amount to scale buffer length if necessary
    private static final int GROW_RATE = 2;
    
    // reader has reached the end of file
    private static final int EOF = -1;
    
    /* class members */
    
    private BufferedReader in; // wraps log source, efficiently fills buf
    private char[] buf; // buffer filled by 'in' with log entry chars
    private int state; // the current state of the parser
    private int start, cur, end; // buffer positions
    private int charsRead; // num chars read by underlying BufferedReader
    
    
    /**
     * 
     * @param input
     */
    public LogParser(Reader input) { this(input, DEFAULT_BUFF_LENGTH); }
    
    /**
     * 
     * @param input
     * @param initialBuffLen
     */
    public LogParser(Reader input, int initialBuffLen) {
        this(new BufferedReader(input), initialBuffLen);
    }
    
    /**
     * 
     * @param input
     * @param initialBuffLen
     */
    protected LogParser(BufferedReader input, int initialBuffLen) {
        in = input;
        int tmplen = initialBuffLen > SAFE_MAX_ENTRY_LENGTH ? initialBuffLen
            : DEFAULT_BUFF_LENGTH;
        
        buf = new char[tmplen];
    }
    
    private Reader getInput() { return in; }
    
    public int getBufferLength() { return buf.length; }
    
    /**
     * Parses the head portion of this log entry.
     * <p>
     * If this entry's code is associated with one of Assoc Success, Disassoc
     * From, Deauth To or Deauth From and not from a controller. Then, once the
     * end of the head portion is reached, parsing of the message portion is
     * handed over to the respective LogEntry type, via a LogEntry map lookup
     * and {@code LogEntry#parse} call. Returning a LogEntry instance with type
     * being this entry's respective type, one of {@code
     * LogEntry.Type.<ASSOC_SUCCESS, DEAUTH_FROM, DEAUTH_TO>}. The parser will
     * then move the start and current buffer index pointers to the beginning of
     * the next entry and return the parsed entry.
     * <p>
     * If this entry's code is associated with one of Assoc Success, Disassoc
     * From, Deauth To or Deauth From and from a controller. Then, once the end
     * of the head portion is reached, the parser will skip ahead in the buffer
     * a predetermined safe amount, received from a call to the respective
     * {@code LogEntry#getSkipCount}. After skipping ahead the parser will then
     * read until reaching a log entry delimiter <sup>1</sup>. Returning a
     * LogEntry instance with type {@code LogEntry.Type.SKIP}.
     * <p>
     * If this entry's code is not associated with one of Assoc Success,
     * Disassoc From, Deauth To or Deauth From, but is a recognized NOTI code,
     * regardless of being from a controller or not. Then, once the end of the
     * head portion is reached, the parser will skip ahead in the buffer a
     * predetermined safe amount, received from a call to the respective {@code
     * LogEntry#getSkipCount}. After skipping ahead the parser will then read
     * until reaching a log entry delimiter <sup>[1]</sup>. Returning a LogEntry
     * instance with type {@code LogEntry.Type.SKIP}.
     * <p>
     * If any of the following are true:
     * <ul>
     * <li>this entry's code is not a recognized NOTI code.
     * <li>this entry does not have a code.
     * <li>this entry is ill formatted <sup>[2]</sup>.
     * </ul>
     * Then the parser will read until reaching a log entry delimiter, unless it
     * has already reached a log entry delimiter, and return {@code null}.
     * <p>
     * 1: <i>"Then the parse will read until reaching a log entry
     * delimiter."</i> implies the following empty body loop:
     * 
     * <pre>
     * <code>while(buf[cur++] != ENTRY_DELIM {}</code>
     * </pre>
     * 
     * <p>
     * 2: An entry can be ill formatted in any of the following ways:
     * <ul>
     * <li>an entry delimiter is read after reading an opening section delimiter
     * (one of {@code '<'}, {@code '['} or {@code '|'}), but before reading a
     * closing section delimiter (one of {@code '>'}, {@code ']'} or
     * {@code '|'}, respectively).
     * 
     * <li>a non-process ({@code '|'}) closing section delimiter (one of
     * {@code '>'}, {@code ']'}) is read before its respective opening section
     * delimiter (one of {@code '<'}, {@code ']'}).
     * 
     * <li>the entry contains no possible code sections (i.e. {@code <...>}).
     * 
     * <li>the entry contains an ASCII control character other than carriage
     * return ({@code '\r'}) or line feed ({@code '\n'}), or when a carriage
     * return ({@code '\r'}) does not directly precede a line feed ({@code
     * '\n'}).
     * </ul>
     * 
     * @param otherErrorCount
     * 
     * @return either a vanilla skip entry, a parsed entry or an {@code null}
     *         entry
     */
    private LogEntry parseEntry() {
        LogEntry entry = null;
        byte colcnt = 0; // current number of colons (:) read
        int tsend = 0;
        
        /* BEGIN PARSE HEAD LOOP */
        while (stateHas(PARSE_HEAD)) {
            switch (buf[cur]) {
                
                case COLON:
                    // parsing of the log entry head is heavily dependent on
                    // the count of colons read so far
                    colcnt += 1;
                    
                    // break if we're still in the timestamp section
                    if (stateHas(TSTAMP_SECT)) {
                        break;
                    }
                    
                    /* Skip weird format, non-NOTI entries like httpd, sapm.. */
                    
                    // httpd messages use '[:msg]' syntax
                    if (stateHas(BRACK_SECT) && buf[cur - 1] == BRACKET_OPEN) {
                        // skip ahead to beginning of new entry
                        while (buf[cur++] != '\n') {}
                        
                        // diagnostics
                        httpdErrorCount += 1;
                        
                        // debug httpd errors
                        debugEntry(debugAll, "colAfterBracksErrors");
                        
                        start = cur;
                        state = NEW_ENTRY;
                        
                        return null;
                    } else if (buf[cur - 1] != BRACKET_CLOSE) {
                        // this catches ERRS and system entries
                        
                        // skip ahead to beginning of new entry
                        while (buf[cur++] != '\n') {}
                        
                        // diagnostics
                        otherErrorCount = 1;
                        // debug other error string builder for file output
                        debugEntry(debugAll, "ColAfterNoBrackets");
                        
                        start = cur;
                        state = NEW_ENTRY;
                        return null;
                    }
                    
                    break;
                
                case SPACE:
                    
                    // first space in log entry comes immediately after tstamp
                    if (stateHas(TSTAMP_SECT)) {
                        // update state and mark the end of the timestamp
                        if (addState(TSTAMP_SPACE)) {
                            tsend = cur;
                        } else {
                            // tstamp section ends after the 2nd space
                            remState(TSTAMP_SECT);
                        }
                        
                        // continue parse head loop
                        continue;
                    }
                    
                    // disregard spaces in a <>, [] or || section
                    if (stateHasAny(ENTRY_SECT_MASK)) {
                        break;
                    }
                    
                    // check for two back-to-back spaces
                    if (buf[cur - 1] == SPACE) {
                        // two spaces before the 4th colon usually indicates the
                        // log entry is from a controller
                        if (colcnt == NUM_TSTAMP_COLONS) {
                            addState(CTRLER_ENTRY);
                            
                            ctrlCount += 1;
                            break;
                        }
                        
                        // two spaces after a six digit entry code has been read
                        // is a message section indicator, meaning head is done
                        if (stateHas(CODE_READ_STATE)) {
                            remState(PARSE_HEAD);
                            
                            // continue out of PARSE_HEAD loop
                            continue;
                        }
                    }
                    
                    break;
                
                
                case CARROT_OPEN:
                    // add carrot section state unless we're already in a carrot
                    // section, in which case output error
                    if (!addState(CARR_SECT)) {
                        // should not encounter another open before close
                        errorUnexpectChar(CARROT_OPEN + "", lineCount,
                            cur - start);
                    }
                    
                    // don't care about carrot sections after entry code
                    if (stateHas(CODE_READ_STATE)) {
                        // skip ahead to end of carrot section
                        while (buf[cur++] != CARROT_CLOSE) {}
                        remState(CARR_SECT);
                        
                        // continue rather than break since we adjusted cur
                        continue;
                    }
                    
                    // the log entry code is contained in the carrot section
                    // following the 4th colon
                    if (colcnt == COLONS_TILL_CODE) {
                        
                        // if the first digit in code is not 5 then this entry
                        // is not a NOTI entry, i.e. move straight to skip
                        if (buf[cur + 1] != NOTI_LEADING_DIGIT) {
                            remState(PARSE_HEAD);
                            addState(SKIP_ENTRY);
                            
                            // adjust cur
                            cur += 1;
                            
                            // continue out of PARSE_HEAD loop
                            continue;
                        }
                        
                        // fast conversion from char sequence to integer value
                        int code = NOTI_LEADING_DIGIT * 100000
                            + buf[cur + 2] * 10000 + buf[cur + 3] * 1000
                            + buf[cur + 4] * 100 + buf[cur + 5] * 10
                            + buf[cur + 6] - MAGIC_CHARS_TO_INT_OFFSET;
                        
                        // adjust current index to end of carrot section
                        cur += CODE_LENGTH;
                        // add CODE_READ to state
                        addState(CODE_READ_STATE);
                        
                        // set entry based on code-to-entry mapping in LogEntry
                        entry = LogEntry.fromCode(code);
                        // TODO replace LogEntry's use of Java generic HashMap
                        // with an int-to-LogEntry HashMap to reduce needless
                        // autoboxing
                        
                        
                        if (entry == null) {
                            // if the six digit code is unrecognized signal, to
                            // skip this entry, continue out of parse head loop
                            addState(SKIP_ENTRY);
                            remState(PARSE_HEAD);
                            
                        } else if (entry.needToParse()) {
                            // this entry is important, i.e. Assoc Success,
                            // Deauth From and Deauth To, signal that we need
                            // to parse the MACs and building name from message
                            addState(PARSE_MSG);
                            
                        } else {
                            // this entry has a recognized six digit code, but
                            // it's unimportant, i.e. Auth Success, ... we just
                            // need to skip ahead a safe predetermined amount
                            addState(VALID_NOTI_STATE);
                            addState(SKIP_ENTRY);
                        }
                        
                        // continue rather than break since we adjusted cur
                        continue;
                    }
                    
                    break;
                
                case CARROT_CLOSE:
                    // remove carrot section state unless we're not in a carrot
                    // section, in which case output error
                    if (!remState(CARR_SECT)) {
                        // should not encounter close without open
                        errorUnexpectChar(CARROT_CLOSE + "", lineCount,
                            cur - start);
                    }
                    break;
                
                case BRACKET_OPEN:
                    // add bracket section state unless we're in a bracket
                    // section, in which case output error
                    if (!addState(BRACK_SECT)) {
                        // should not encounter another open before close
                        errorUnexpectChar(BRACKET_OPEN + "", lineCount,
                            cur - start);
                    }
                    break;
                
                case BRACKET_CLOSE:
                    // remove bracket section state unless we're not in a
                    // bracket section, in which case output error
                    if (!remState(BRACK_SECT)) {
                        // should not encounter close without open
                        errorUnexpectChar(BRACKET_CLOSE + "", lineCount,
                            cur - start);
                    }
                    break;
                
                /* Process section is delimited by one char, '|', twice. Since
                 * process section start and end are delimited by the same char,
                 * we cannot error on unexpected start or end of process
                 * section. */
                case PROCESS_DELIM:
                    // this flip-flops, setting process section state on and off
                    if (!addState(PROC_SECT)) {
                        remState(PROC_SECT);
                    }
                    break;
                
                case '\r':
                    // entry head should not include a carriage return unless
                    // directly preceding a line feed.
                    if (buf[cur + 1] != '\n') {
                        
                        errorUnexpectChar("\\r", lineCount, cur - start);
                        
                        illFormatCount += 1;
                        // debug ill-formatted entry
                        debugEntry(debugFocus, "IllFormatEntry [\\r]");
                        
                        // signal that we need to skip this entry and continue
                        // out of parse head loop
                        state = SKIP_ENTRY;
                        continue;
                    }
                    
                    break;
                
                case ENTRY_DELIM:
                    // entry head should not have an entry delim, so set state
                    // to new entry and break so that cur will increment
                    illFormatCount += 1;
                    
                    // debug ill-formatted entries
                    debugEntry(debugFocus, "IllFormatEntry [\\n]");
                    
                    // move start to beginning of next entry
                    state = NEW_ENTRY;
                    break;
                
                // break out of switch if other characters ecountered
                default:
                    break;
            }
            
            // make sure to update cur index
            cur += 1;
            
        }
        
        /* POST PARSE HEAD LOOP PROCESSING */
        
        // once the entry head is parsed, either skip ahead or parse the message
        if (stateHas(SKIP_ENTRY)) {
            
            if (stateHas(VALID_NOTI_STATE)) {
                // skip ahead amount specified by NOTI
                cur += entry.getSkipCount();
            }
            
            // skip ahead to next entry
            while (buf[cur++] != ENTRY_DELIM) {}
            
            if (!stateHas(VALID_NOTI_STATE)) {
                // diagnostics
                nonNotiCount += 1;
                
                // debug non-noti entry
                debugEntry(debugAll, "NonNotiEntry");
            }
        }
        
        // parse either an Assoc Success, Deauth To or Deauth From message
        if (stateHas(PARSE_MSG)) {
            // System.out.printf("Date String: '%s'%n start: %d tsend: %d%n",
            // String.valueOf(buf, start, tsend - start), start, tsend);
            
            // parse time since epoch now, no reason to do it for every entry
            long epoch = Instant
                .from(dtf.parse(String.valueOf(buf, start, tsend - start)))
                .getEpochSecond();
            
            entry = entry.parse(epoch, buf, cur);
            
            // skip ahead amount read by LogEntry#parse
            cur += entry.getSkipCount();
        }
        
        // move start to beginning of next entry
        start = cur;
        state = NEW_ENTRY;
        
        return entry;
    }
    
    /**
     * Calls {@link #parse parse()} and closes {@code input} after parsing.
     */
    public List<LogEntry> parseAndClose() {
        List<LogEntry> entries = parse();
        
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return entries;
    }
    
    /* move unread to buffer front and fill in rest */
    private void refillBuffer() {
        // move not yet parsed chars to front of buf to prep for read
        System.arraycopy(buf, start, buf, 0, end - start);
        
        // update cur index to reflect the move
        // cur = end - start;
        
        // if start has not changed and we've read through buffer without
        // encountering an entry separator, then grow buffer
        if (cur - start == buf.length) { // same as (start==0 &&
                                         // cur==buf.length)
            buf = Arrays.copyOf(buf, buf.length * GROW_RATE);
        }
        
        try {
            charsRead = in.read(buf, end - start, buf.length - (end - start));
        } catch (IOException e) {
            // ToDo: implement recovery from a variety of IOExceptions
            e.printStackTrace();
        }
        
        end = (end - start) + charsRead;
        cur = start = 0;
    }
    
    /* fill the entire buffer, not just a portion */
    private void fillBuffer() {
        try {
            charsRead = in.read(buf);
        } catch (IOException e) {
            // ToDo: implement recovery from a variety of IOExceptions
            e.printStackTrace();
        }
        
        start = cur = 0;
        end = charsRead;
    }
    
    /* add one or more states to the parser's current state */
    private boolean addState(int toAdd) {
        if (stateHas(toAdd))
            return false;
        
        state |= toAdd;
        return true;
    }
    
    /* remove one or more states from the parser's current state */
    private boolean remState(int toRem) {
        if (!stateHas(toRem))
            return false;
        
        state &= (~toRem);
        return true;
    }
    
    /* returns whether the parser's currently in a state */
    private boolean stateHas(int which) { return (state & which) == which; }
    
    /* returns whether the parser's currently in any of a set of states */
    private boolean stateHasAny(int mask) { return (state & mask) != 0; }
    
}
