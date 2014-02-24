package com.summa;

import com.google.common.collect.ImmutableList;
import org.apache.maven.model.FileSet;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static com.google.common.collect.Lists.*;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.containsString;

/** @author bgray */
public class WebToSfMojoTest {

    public static final String UNZIP_DIR = "unzipResources";

    private WebToSfMojo mojo;

    private File outputDir;

    @Before
    public void setup() throws Exception {
        this.mojo = new WebToSfMojo();
        TestUtils.reflectionSet(mojo, "zipFilename", "appzip");
        TestUtils.reflectionSet(mojo, "webappDir", createFileSet());
        TestUtils.reflectionSet(mojo, "filters", createFilters());

        this.outputDir = new File("./testOutput");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
//            outputDir.deleteOnExit();
        }
        TestUtils.reflectionSet(mojo, "outputDir", outputDir);
    }

    @Test
    public void testMojo_resourcesInTheRightPlaces() throws Exception {
        // method under test
        mojo.execute();

        assertTrue("Output missing", this.outputDir.exists());
        assertTrue("pages missing", new File(this.outputDir, "pages").exists());
        assertTrue("staticresources missing", new File(this.outputDir, "staticresources").exists());

        // check pages
        assertTrue("test page missing", new File(this.outputDir, "pages/test.page").exists());
        assertTrue("test page meta missing", new File(this.outputDir, "pages/test.page-meta.xml").exists());

        // check static resources
        File resourceBundle = new File(this.outputDir, "staticresources/appzip.resource");
        assertTrue("staticresources bundle missing", resourceBundle.exists());
        assertTrue("staticresources bundle meta missing", new File(this.outputDir,
                "staticresources/appzip.resource-meta.xml").exists());

        File unzipDir = new File(outputDir, UNZIP_DIR);
        TestUtils.unzip(resourceBundle, unzipDir);

        // check js
        assertTrue("js missing", new File(unzipDir, "js").exists());
        assertTrue("test.js missing", new File(unzipDir, "js/test.js").exists());
        assertFalse("exclude.js found", new File(unzipDir, "js/exclude.js").exists());
    }

    @Test
    public void testMojo_patterns() throws Exception {
        // method under test
        mojo.execute();

        File testPage = new File(this.outputDir, "pages/test.page");
        assertTrue("test page missing", testPage.exists());

        // read the file into a list of Strings
        List<String> lines = TestUtils.readFile(testPage);

        // check the lines for replacements
        int goodCount = 0;
        for (String line : lines) {
            assertThat("page contained <doctype>", line, not(containsString("<!doctype html>")));
            assertThat("page contained <script>", line, not(containsString("</script>")));
            assertThat("page contained <title>", line, not(containsString("</title>")));
            assertThat("page contained reference to test src", line, not(containsString("value=\"js/test.js\"")));
            goodCount += line.contains("<apex:includeScript") ? 1 : 0;
        }
        assertTrue("Page did not contain <apex:includeScript> tag", goodCount > 0);
    }

    private FileSet createFileSet() {
        URL url = getClass().getResource(getClass().getSimpleName() + ".class");
        String path = url.toExternalForm();
        String separator = "/";
        String newPath = TestUtils.determinePathSubstring(path, separator);

        FileSet fileSet = new FileSet();
        fileSet.setDirectory(newPath);
        fileSet.setIncludes(new ImmutableList.Builder<String>()
                .add("js/")
                .add("*.html")
                .build());
        fileSet.setExcludes(new ImmutableList.Builder<String>()
                .add("js/exclude.js")
                .build());
        return fileSet;
    }

    private List<WebToSfMojo.Filter> createFilters() {
        return newArrayList(
//            new WebToSfMojo.Filter("js/test.js", "{!URLFor($Resource.appzip, 'js/test.js')}")
        );
    }
}
