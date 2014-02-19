package com.summa;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.tools.ant.util.StringUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.zip.ZipOutputStream;

import static com.google.common.collect.Iterables.transform;

/**
 * Utilities
 * @author bgray
 **/
public final class Utils {

    private static final String LF = System.getProperty("line.separator");

    private Utils() {}

    /**
     * Transforms a list of any Java type into a comma-separated list of its toString representation
     * @param patterns
     * @return
     */
    public static String getCommaSeparatedList(List<?> patterns) {
        return Joiner.on(",").join(transform(patterns, new Function<Object, String>() {
            @Override
            public String apply(@Nullable Object input) {
                return input == null ? "" : input.toString();
            }
        }));
    }

    /**
     * Get relative path between two files
     * http://stackoverflow.com/questions/204784/how-to-construct-a-relative-path-in-java-from-two-absolute-paths-or-urls
     * @param base
     * @param file
     * @return relative path
     */
    public static String getRelativePath(File base, File file) {
        return base.toURI().relativize(file.toURI()).getPath();
    }

    /**
     * Create a new directory
     * @param parent
     * @param dirName
     * @return reference to the new directory
     */
    public static File createDir(File parent, String dirName) {
        File staticResourcesDir = new File(parent.getAbsolutePath(), dirName);
        if (!staticResourcesDir.exists()) {
            staticResourcesDir.mkdirs();
        }
        return staticResourcesDir;
    }

    /**
     * Close output stream, converting IOExceptions into MojoExecutionExceptions
     * @param outputStream
     * @throws MojoExecutionException
     */
    public static void close(ZipOutputStream outputStream) throws MojoExecutionException {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                throw new MojoExecutionException("Error executing mojo", e);
            }
        }
    }

    //// BG - I have no idea what these do ////

    public static String cleanName(String name) {
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

    public static List<String> pathDifference(File baseDir, File subDir) {

        List<String> parts = new ArrayList<String>();
        for (File f = subDir; !baseDir.equals(f); f = f.getParentFile()) {
            parts.add(f.getName());
        }
        Collections.reverse(parts);
        return parts;
    }

    public static String removeSuffix(String name) {

        int index = name.lastIndexOf('.');
        return index != -1 ? name.substring(0, index) : name;
    }
}
