package com.claimvantage.ant;
 
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
 



import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.util.StringUtils;
 
/**
 * Transforms a fairly free-form JavaScript web app layout into Force.com resources.
 * A set of text replacements are applied to any .css, .js or .html files
 * to e.g. fix resource references and necessary name changes.
 * Any .html files are converted into individual Force.com pages.
 * Other files are all added to a single zip file static resource called appzip.
 */
public class WebToSf extends Task {
 
    private static final String LF = System.getProperty("line.separator");
	private static final String ZIP_FILENAME = "appzip";
 
    public static class Filter {
 
        private String token;
        private String value;
 
        public void setToken(String token) {
            this.token = token;
        }
 
        public void setValue(String value) {
            this.value = value;
        }
    }
 
    // Root to read from and write to
    private File fromDir;
    private File toDir;
 
    // Files that go into the named zip
    private List<FileSet> zipContents = new ArrayList<FileSet>();
 
    // Replacements
    private List<Filter> filters = new ArrayList<Filter>();
 
    public void setFromDir(File fromDir) {
        this.fromDir = fromDir;
    }
 
    public void setToDir(File toDir) {
        this.toDir = toDir;
    }
 
    public void addFileset(FileSet zipContent) {
        zipContents.add(zipContent);
    }
 
    public Filter createFilter() {
        Filter filter = new Filter();
        filters.add(filter);
        return filter;
    }
 
    public void execute() {
 
        if (fromDir == null) {
            throw new BuildException("fromdir must be set");
        }
        if (!fromDir.exists()) {
            throw new BuildException("fromdir " + fromDir.getAbsolutePath() + " does not exist");
        }
        if (toDir == null) {
            throw new BuildException("todir must be set");
        }
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i).token == null) {
                throw new BuildException("token missing from filter index " + i);
            }
        }
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i).value == null) {
                throw new BuildException("value missing from filter index " + i);
            }
        }
 
        try {
            zip();
            pages(fromDir);
        } catch (Exception e) {
            throw new BuildException(e);
        }
    }
 
    private void zip() throws Exception {
         
        File staticresources = new File(toDir, "staticresources");
        if (!staticresources.exists()) {
            staticresources.mkdirs();
        }
 
        // Data
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(new File(staticresources, ZIP_FILENAME + ".resource"))));
        try {
            for (FileSet fs : zipContents) {
            	
                if (!fs.isFilesystemOnly()) {
                    throw new BuildException("only filesystem flesets supported");
                }
                 
                DirectoryScanner ds = fs.getDirectoryScanner(getProject());
                File baseDir = this.fromDir;
                File fsDir = fs.getDir(getProject());

                // Keep path as folders
                String path = "";
                for (String part : pathDifference(baseDir, fsDir)) {
                    path += part;
                    path += "/";
                }

                for (String fsName : ds.getIncludedFiles()) {
                     
                    String newName = path + fsName;
                     
                    log("zipping dir=" + fsDir + " file=" + fsName + " to=" + newName,
                            Project.MSG_INFO);
 
                    zos.putNextEntry(new ZipEntry(newName));
                    if (isText(fsName)) {
                        // Replace
                        BufferedReader r = new BufferedReader(new InputStreamReader(
                                new FileInputStream(new File(fsDir, fsName))));
                        try {
                            String line;
                            while ((line = r.readLine()) != null) {
                                zos.write(replace(line).getBytes());
                                zos.write(LF.getBytes());
                            }
                            zos.closeEntry();
                        } finally {
                            r.close();
                        }
                    } else {
                        // Just byte for byte copy
                        BufferedInputStream is = new BufferedInputStream(
                                new FileInputStream(new File(fsDir, fsName)));
                        try {
                            byte[] buf = new byte[4092];
                            int len;
                            while ((len = is.read(buf)) != -1) {
                                zos.write(buf, 0, len);
                            }
                        } finally {
                            is.close();
                        }
                    }
                }
            }
        } finally {
            zos.close();
        }
         
        // Meta
        BufferedWriter ww = new BufferedWriter(new FileWriter(
                new File(staticresources, "appzip" + ".resource-meta.xml")));
        try {
            ww.write(""
                    + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + LF
                    + "<StaticResource xmlns=\"http://soap.sforce.com/2006/04/metadata\">" + LF
                    + "    <cacheControl>Public</cacheControl>" + LF
                    + "    <contentType>application/zip</contentType>" + LF
                    + "</StaticResource>"
                    );
        } finally {
            ww.close();
        }
    }
 
    private boolean isText(String name) {
         
        String lc = name.toLowerCase();
        return lc.endsWith(".css") || lc.endsWith(".js");
    }
 
    private void pages(File dir) throws Exception {
 
        File pages = new File(toDir, "pages");
        if (!pages.exists()) {
            pages.mkdirs();
        }
 
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                pages(f);
            } else {
                if (isHtml(f.getName())) {
                    page(pages, f);
                }
            }
        }
    }
 
    private boolean isHtml(String name) {
         
        String lc = name.toLowerCase();
        return lc.endsWith(".html");
    }
 
    private void page(File pages, File f) throws Exception {
         
        // Prepend path
        String name = "";
        for (String path : pathDifference(getProject().getBaseDir(), f.getParentFile())) {
            name += cleanName(path);
        }
        name += cleanName(removeSuffix(f.getName()));
         
        File to = new File(pages, name + ".page");
        File toMeta = new File(pages, name + ".page-meta.xml");
        log("transforming page file=" + f + " to=" + to, Project.MSG_INFO);
 
        BufferedReader r = new BufferedReader(new FileReader(f));
        try {
 
            // Data
            BufferedWriter w = new BufferedWriter(new FileWriter(to));
            try {
                w.write("<apex:page showHeader=\"false\" sidebar=\"false\""
                        + " standardStylesheets=\"false\""
                        + " applyHtmlTag=\"false\">" + LF + LF);
                String line;
                while ((line = r.readLine()) != null) {
                    w.write(replace(line));
                    w.write(LF);
                }
                w.write(LF + "</apex:page>");
            } finally {
                w.close();
            }
 
            // Meta
            BufferedWriter ww = new BufferedWriter(new FileWriter(toMeta));
            try {
                ww.write("" + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + LF
                        + "<StaticResource xmlns=\"http://soap.sforce.com/2006/04/metadata\">" + LF
                        + "    <apiVersion>29.0</apiVersion>" + LF
                        + "    <label>" + name + "</label>" + LF
                        + "</StaticResource>"
                        );
            } finally {
                ww.close();
            }
        } finally {
            r.close();
        }
    }
 
    private String replace(String line) {
         
        for (Filter f : filters) {
            if (line.contains(f.token)) {
                log("... replacing " + f.token + " in line " + line, Project.MSG_INFO);
                line = line.replace(f.token, f.value);
            }
        }
        return line;
    }
 
    private String cleanName(String name) {
    	Vector<String> paths = StringUtils.split(name, LF.charAt(0));
    	name = paths.elementAt(paths.size() - 1);
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c) || Character.isDigit(c) || c == '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
     
    private List<String> pathDifference(File baseDir, File subDir) {
         
        List<String> parts = new ArrayList<String>();
        for (File f = subDir; !baseDir.equals(f); f = f.getParentFile()) {
            parts.add(f.getName());
        }
        Collections.reverse(parts);
        return parts;
    }
     
    private String removeSuffix(String name) {
         
        int index = name.lastIndexOf('.');
        return index != -1 ? name.substring(0, index) : name;
    }
}