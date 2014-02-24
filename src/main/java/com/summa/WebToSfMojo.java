package com.summa;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.collect.Lists.*;

/**
 * Converts Standard web project to Force.com project
 * http://force201.wordpress.com/2013/11/06/mapping-an-angularjs-client-into-a-force-com-server/
 */
@Mojo(name = "webtosf")
public class WebToSfMojo extends AbstractMojo {

    private static final String LF = System.getProperty("line.separator");

    /**
     * A map of file extensions to their associated types
     */
    private static final Map<String, FileType> EXTENSIONS_MAP = new ImmutableMap.Builder<String, FileType>()
            .put("css", FileType.TEXT)
            .put("js", FileType.TEXT)
            .put("txt", FileType.TEXT)
            .put("html", FileType.HTML)
            .put("htm", FileType.HTML)
            .build();

    /**
     * Filters always applied by default
     */
    private static final List<Filter> BASE_FILTERS = new ImmutableList.Builder<Filter>()
            // remove unneeded tags
            .add(new Filter("<!doctype html>"))
            .add(new Filter("<html>"))
            .add(new Filter("</html>"))
            .add(new Filter("<head>"))
            .add(new Filter("</head>"))
            .add(new Filter("<body>"))
            .add(new Filter("</body>"))
            .add(new Filter("(.*)<title>.*</title>(.*)", "$1$2", true))

            // Replace external resource links
            .add(new Filter("(.*)<script src=[\"'](.+)[\"']></script>(.*)",
                    "$1<apex:includeScript value=\"$2\"></apex:includeScript>$3", true))
            .add(new Filter("(.*)<link.*href=[\"'](.+)[\"'].*></link>(.*)",
                    "$1<apex:stylesheet value=\"$2\"></apex:stylesheet>$3", true))

            // Replace .js and .css links with references to $Resource
            .add(new Filter("(.*)<apex:(.*).* value=\"(?!http)(.+)\".*>(.*)",
                    "$1<apex:$2 value=\"{!URLFor(\\$Resource.appzip, '$3')}\"></apex:$2>$4", true))


            .build();

    /**
     * Class used to represent a filter for this mojo (token gets replaced by value)
     */
    public static class Filter {
        public Filter() {
        }
        public Filter(String token) {
            this.token = token;
        }
        public Filter(String token, String value) {
            this(token);
            this.value = value;
        }
        public Filter(String token, String value, Boolean isRegex) {
            this(token, value);
            this.isRegex = isRegex;
        }
        protected String token;
        protected String value;
        protected Boolean isRegex = false;
        protected String getValue() {
            return this.value == null ? "" : value;
        }
    }

    /**
     * Types of files this plugin handles
     */
    private static enum FileType {
        TEXT, HTML, OTHER
    }

    /**
     * Root to read files from
     */
    @Parameter( property = "webtosf.webappDir", required = true )
    private FileSet webappDir;

    /**
     * Root to write files to
     */
	@Parameter( property = "webtosf.outputDir", defaultValue = "${project.build.directory}/${project.build.finalName}" )
	private File outputDir;

    /**
     * Filters to use as replacements
     */
	@Parameter( property = "webtosf.filters" )
    private List<Filter> filters = newArrayList();

    /**
     * Name of the generated zip file
     */
    @Parameter( property = "webtosf.zipFileName", defaultValue = "appzip" )
    private String zipFilename;

	public void execute() throws MojoExecutionException {
        File[] files = this.filesIncludedByConfiguration();
        validateAndDebug(files);

        // create directories
        File staticResourcesDir = Utils.createDir(this.outputDir, "staticResources");
        File pagesDir = Utils.createDir(this.outputDir, "pages");

        ZipOutputStream outputStream = null;
        try {
            // Initialize archive for static resources
            outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(
                    new File(staticResourcesDir, this.zipFilename + ".resource"))));

            for (File file : files) {
                switch (getFileType(file)) {
                    case HTML:
                        transformHtmlToPage(pagesDir, file);
                        break;

                    case TEXT:
                        archiveTextStaticResource(outputStream, staticResourcesDir, file);
                        break;

                    case OTHER:
                        archiveBinaryStaticResource(outputStream, staticResourcesDir, file);
                        break;
                }
            }
            createMetaData(staticResourcesDir);

        } catch (IOException e) {
            throw new MojoExecutionException("Error executing mojo", e);

        } finally {
            Utils.close(outputStream);
        }
    }

    /**
     * Add the file to the static resources archive, replacing text according to the filters defined in this plugin's
     * configuration
     * @param zos
     * @param staticResourcesDir
     * @param file
     * @throws IOException
     */
    protected void archiveTextStaticResource(ZipOutputStream zos, File staticResourcesDir, File file) throws IOException {
        File baseDir = new File(this.webappDir.getDirectory());
        String path = Utils.getRelativePath(baseDir, file);
        getLog().info("zipping dir=" + file + " file=" + file.getName() + " to=" + path);

        // Go line by line and replace using filters
        zos.putNextEntry(new ZipEntry(path));
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
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
    }

    /**
     * Add the file to the static resources archive, doing a byte-by-byte copy
     * @param zos
     * @param staticResourcesDir
     * @param file
     * @throws IOException
     */
    protected void archiveBinaryStaticResource(ZipOutputStream zos, File staticResourcesDir, File file) throws IOException {
        File baseDir = new File(this.webappDir.getDirectory());
        String path = Utils.getRelativePath(baseDir, file);
        getLog().info("zipping dir=" + file + " file=" + file.getName() + " to=" + path);

        zos.putNextEntry(new ZipEntry(path));
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(file));
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

    /**
     * Creates metadata needed by Force.com to deploy the static resources
     * @param staticResourcesDir
     * @throws IOException
     */
    protected void createMetaData(File staticResourcesDir) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(staticResourcesDir,
                zipFilename + ".resource-meta.xml")));
        try {
            writer.write(""
                    + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + LF
                    + "<StaticResource xmlns=\"http://soap.sforce.com/2006/04/metadata\">" + LF
                    + "    <cacheControl>Public</cacheControl>" + LF
                    + "    <contentType>application/zip</contentType>" + LF
                    + "</StaticResource>"
            );
        } finally {
            writer.close();
        }
    }

    /**
     * Transform a single htmlFile to a Force.com .page file
     * TODO ugh, clean up
     * @param pagesDir
     * @param htmlFile
     */
    private void transformHtmlToPage(File pagesDir, File htmlFile) throws IOException {
        // Prepend path
        File baseDir = new File(this.webappDir.getDirectory());
        String name = "";
        for (String path : Utils.pathDifference(baseDir, htmlFile.getParentFile())) {
            name += Utils.cleanName(path);
        }
        name += Utils.cleanName(Utils.removeSuffix(htmlFile.getName()));

        File to = new File(pagesDir, name + ".page");
        File toMeta = new File(pagesDir, name + ".page-meta.xml");
        getLog().info("transforming page file=" + htmlFile + " to=" + to);

        BufferedReader r = new BufferedReader(new FileReader(htmlFile));
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

    /**
     * Replace a line according to all our filters
     * @param line
     * @return the replaced line
     */
    private String replace(String line) {
        for (Filter filter : Iterables.concat(BASE_FILTERS, filters)) {
            if (filter.isRegex && line.matches(filter.token)) {
                getLog().info("... replacing " + filter.token + " in line " + line);
                line = line.replaceAll(filter.token, filter.getValue());

            } else if (!filter.isRegex && line.contains(filter.token)) {
                getLog().info("... replacing regex " + filter.token + " in line " + line);
                line = line.replace(filter.token, filter.getValue());
            }
        }
        return line;
    }

    /**
     * Validate properties
     * @param files
     */
    private void validateAndDebug(File[] files) throws MojoExecutionException {
        getLog().debug("Executing webtosf mojo.");
        getLog().debug(String.format("webAppDir: [%s]", webappDir.getDirectory()));
        if (getLog().isDebugEnabled()) {
            for (File file : files) {
                getLog().debug(String.format("\t--file: [%s]", file.getAbsolutePath()));
            }
        }
        getLog().info(String.format("Filters:"));
        for (Filter filter : this.filters) {
            if (filter.token == null) {
                throw new MojoExecutionException("token required on each filter.");
            }
            getLog().debug(String.format("\t--filter token: [%s] value: [%s]", filter.token, filter.getValue()));
        }
        getLog().debug(String.format("outputDir: [%s]", outputDir.getAbsolutePath()));
    }

    /**
     * Iterates through the webappDir, populating the files property and the replacement map
     * @return Array of files we care about (applying includes and excludes)
     * @throws MojoExecutionException
     */
    protected File[] filesIncludedByConfiguration() throws MojoExecutionException {
        if (this.webappDir == null || this.webappDir.getDirectory() == null) {
            return new File[] {};
        }
        try {
            File directory = new File(this.webappDir.getDirectory());
            String includes = Utils.getCommaSeparatedList(this.webappDir.getIncludes());
            String excludes = Utils.getCommaSeparatedList(this.webappDir.getExcludes());
            List files = FileUtils.getFiles(directory, includes, excludes);
            return (File[]) files.toArray(new File[files.size()]);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to get webappDir files", e);
        }
    }

    /**
     * Get the {@code FileType} of a given file
     * @param file
     * @return matching FileType or OTHER if none matches
     */
    private FileType getFileType(File file) {
        final String name = file.getName().toLowerCase();
        String extension = Iterables.getLast(Splitter.on(".").split(name), "");
        FileType type = EXTENSIONS_MAP.get(extension);
        return type == null ? FileType.OTHER : type;
    }

}
