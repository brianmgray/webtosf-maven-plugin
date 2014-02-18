package com.summa;

import com.google.common.base.Function;
import com.google.common.base.Joiner;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

import static com.google.common.collect.Iterables.transform;

/**
 * Utilities
 * @author bgray
 **/
public final class Utils {

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

}
