package com.summa;

import com.google.common.collect.ImmutableList;
import org.apache.maven.model.FileSet;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;

/** @author bgray */
public class WebToSfMojoTest {

    private WebToSfMojo mojo;

    private File outputDir;

    @Before
    public void setup() throws Exception {
        this.mojo = new WebToSfMojo();
        TestUtils.reflectionSet(mojo, "zipFilename", "appzip");
        TestUtils.reflectionSet(mojo, "webappDir", createFileSet());

        this.outputDir = new File("./testOutput");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
            outputDir.deleteOnExit();
        }
        TestUtils.reflectionSet(mojo, "outputDir", outputDir);
    }

    @Test
    public void testMojo() throws Exception {
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

        File unzipDir = new File(outputDir, "unzipResources");
        TestUtils.unzip(resourceBundle, unzipDir);

        // check js
        assertTrue("js missing", new File(unzipDir, "js").exists());
        assertTrue("test.js missing", new File(unzipDir, "js/test.js").exists());
        assertFalse("exclude.js found", new File(unzipDir, "js/exclude.js").exists());

        // check HTML
        assertTrue("test.html missing", new File(unzipDir, "test.html").exists());

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

}
