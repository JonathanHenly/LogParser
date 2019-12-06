import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;

import com.mactracker.main.AbbreviationTrie;
import com.mactracker.main.Analyzer;
import com.mactracker.main.Utils;
import com.mactracker.main.log.LogEntry;
import com.mactracker.main.log.LogParser;


public class Driver {
    
    public static void main(String[] args) throws IOException {
        // test();
        
        File mfile = new File("./zin/bname_mappings.txt");
        
        Reader mr = openMappingFile(mfile);
        AbbreviationTrie abbr = Utils.buildAbbrTrie(mr);
        mr.close();
        
        LogEntry.setAbbreviationTrie(abbr);
        
        String filename;
        
        // filename = "./zin/wifi_2_gt.txt";
        filename = "E:\\ITCS-4155\\res\\data\\wifi_1\\wifi_1_bi.txt";
        // filename = "./zin/wifi_logs-10_10_19-1K.txt");
        // filename = "./zin/wifi_2_ej-rand-60.txt");
        
        File logs = new File(filename);
        Charset cset = Charset.forName("ASCII");
        
        /* Parser Testing */
        BufferedReader br = new BufferedReader(new FileReader(logs, cset));
        
        LogParser parser = new LogParser(br);
        
        System.out.println("Log file: " + filename);
        System.out.println(
            "Log file size: ~" + (logs.length() / (1024 * 1024)) + "MBs");
        System.out.println();
        
        System.out.println("-- Parsing log file --");
        System.out.println();
        
        parser.outputDiagnostics().outputDebug();
        
        List<LogEntry> entries = parser.parseAndClose();
        
        System.out.println();
        System.out.println("-- finished parsing --");
        /* End Parser Testing */
        
        System.out.println("Number of analyzable entries:  " + entries.size());
        
        System.out.println("-- Analyzing log file --");
        
        Analyzer analyst = new Analyzer(abbr.getValues(), entries);
        
        // analyst.aggregateApTraffic();
        
        
    }
    
    private void extractAll(URI fromZip, Path toDirectory) throws IOException {
        FileSystem zipFs = FileSystems.newFileSystem(fromZip,
            Collections.emptyMap());
        
        for (Path root : zipFs.getRootDirectories()) {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                    // You can do anything you want with the path here
                    Files.copy(file, toDirectory);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                    // In a full implementation, you'd need to create each
                    // sub-directory of the destination directory before
                    // copying files into it
                    return super.preVisitDirectory(dir, attrs);
                }
            });
        }
    }
    
    private static Reader openMappingFile(File mfile) throws IOException {
        return new BufferedReader(new FileReader(mfile));
    }
    
    
}
