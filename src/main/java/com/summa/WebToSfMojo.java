package com.summa;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
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
     * Extensions considered "text"
     */
    private static final List<String> TEXT_EXTENSIONS = new ImmutableList.Builder<String>()
            .add(".css")
            .add(".js")
            .add(".txt")
            .build();

    /**
     * Class used to represent a filter for this mojo (token gets replaced by value)
     */
    public static class Filter {

        private String token;
        private String value;

        public void setToken(String token) {
            this.token = token;
        }

        public void setValue(String value) {
            this.value = value;
        }

        protected String getValue() {
            return this.value == null ? "" : value;
        }
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
        File[] files = this.getFilesFromWebappDir();
        validateAndDebug(files);

        File staticResourcesDir = new File(outputDir.getAbsolutePath(), "staticResourcesDir");
        if (!staticResourcesDir.exists()) {
            staticResourcesDir.mkdirs();
        }

        try {
            zipResources(staticResourcesDir, files);
            createForceDotComMetaData(staticResourcesDir);
//            pages(fromDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Error executing mojo", e);
        }
    }

    /**
     * Zips all of the included webappDir files into an archive in the outputDir
     * @param staticResourcesDir
     * @param files
     */
    protected void zipResources(File staticResourcesDir, File[] files) throws IOException {
        // Data
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File
                (staticResourcesDir, this.zipFilename + ".resource"))));
        try {
            File baseDir = new File(this.webappDir.getDirectory());

            for (File file : files) {
                String name = file.getName();
                String path = Utils.getRelativePath(baseDir, file);

                getLog().info("zipping dir=" + file + " file=" + name + " to=" + path);

                zos.putNextEntry(new ZipEntry(path));
                if (isText(name)) {
                    // Replace
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
                } else {
                    // Just byte for byte copy
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
            }

        } finally {
            zos.close();
        }

    }

    /**
     * Creates metadata needed by Force.com to deploy the resources
     * @param staticResourcesDir
     * @throws IOException
     */
    protected void createForceDotComMetaData(File staticResourcesDir) throws IOException {
        BufferedWriter ww = new BufferedWriter(new FileWriter(
                new File(staticResourcesDir, zipFilename + ".resource-meta.xml")));
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

    /**
     * Replace a line according to all our filters
     * @param line
     * @return the replaced line
     */
    private String replace(String line) {
        for (Filter f : filters) {
            if (line.contains(f.token)) {
                getLog().info("... replacing " + f.token + " in line " + line);
                line = line.replace(f.token, f.value);
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
    protected File[] getFilesFromWebappDir() throws MojoExecutionException {
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
     * Is the file a text file?
     * @param name
     * @return
     */
    private boolean isText(final String name) {
        return Iterables.any(TEXT_EXTENSIONS, new Predicate<String>() {
            @Override
            public boolean apply(@Nullable String extension) {
                return name != null && name.toLowerCase().endsWith(extension);
            }
        });
    }

}
