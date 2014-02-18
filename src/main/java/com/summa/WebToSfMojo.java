package com.summa;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.*;
import static com.google.common.collect.Maps.*;

/**
 * Converts Standard web project to Force.com project
 * http://force201.wordpress.com/2013/11/06/mapping-an-angularjs-client-into-a-force-com-server/
 */
@Mojo(name = "webtosf")
public class WebToSfMojo extends AbstractMojo {
	
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
    @Parameter( property = "webtosf.webappDir" )
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

	public void execute() throws MojoExecutionException {
		getLog().info("Executing webtosf mojo.");
        getLog().info(String.format("webAppDir: [%s]", webappDir.getDirectory()));

        File[] files = this.getFilesFromWebappDir();
        for (File file : files) {
            getLog().info(String.format("\t--file: [%s]", file.getAbsolutePath()));
        }
        getLog().info(String.format("Filters:"));
        for (Filter filter : this.filters) {
            getLog().info(String.format("\t--filter token: [%s] value: [%s]", filter.token, filter.getValue()));
        }
        getLog().info(String.format("outputDir: [%s]", outputDir.getAbsolutePath()));
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
            String includes = getCommaSeparatedList(this.webappDir.getIncludes());
            String excludes = getCommaSeparatedList(this.webappDir.getExcludes());
            List files = FileUtils.getFiles(directory, includes, excludes);
            return (File[]) files.toArray(new File[files.size()]);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to get webappDir files", e);
        }
    }

    /**
     * Transforms a list of any Java type into a comma-separated list of its toString representation
     * @param patterns
     * @return
     */
    private String getCommaSeparatedList(List<?> patterns) {
        return Joiner.on(",").join(transform(patterns, new Function<Object, String>() {
            @Override
            public String apply(@Nullable Object input) {
                return input == null ? "" : input.toString();
            }
        }));
    }

    private Map<String, String> getReplacementMap(File[] files) {
        Map<String, String> replacements = newHashMap();
        String webappPath = this.webappDir.getDirectory();
        for (File file : files) {
            getLog().info(String.format("\t--file: [%s]", file.getAbsolutePath()));
        }
        return null;  //To change body of created methods use File | Settings | File Templates.
    }

}
