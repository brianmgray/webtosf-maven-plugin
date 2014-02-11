package com.summa;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Converts Standard web project to Force.com project
 * @see http://force201.wordpress.com/2013/11/06/mapping-an-angularjs-client-into-a-force-com-server/
 */
@Mojo(name = "webtosf")
public class WebToSfMojo extends AbstractMojo {
	
	private static final String LF = System.getProperty("line.separator");
	
	// parameters
	
	/**
	 * Root to read from and write to
	 */
	@Parameter( property = "webtosf.fromDir", defaultValue = "${basedir}/src/main/webapp" )
    private File fromDir;

	@Parameter( property = "webtosf.toDir", defaultValue = "${project.build.directory}/${project.build.finalName}" )
	private File toDir;
	
	public void execute() throws MojoExecutionException {
		getLog().info("Hello, world.");
	}
	
	/**
	 * POJO for token/value pair
	 * @author bgray
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
    }
}