package com.summa;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.google.common.collect.Lists.*;

/**
 * Test Utilities
 * @author bgray
 **/
public final class TestUtils {

    private TestUtils() {}

    /**
     * Helper to determine the substring before the last separator character
     * @param path
     * @param separator
     * @return
     */
    public static String determinePathSubstring(String path, String separator) {
        List<String> pathParts = newArrayList(Splitter.on(separator).split(path));
        pathParts = pathParts.subList(1, pathParts.size() - 1);
        return Joiner.on(separator).join(pathParts);
    }

    /**
     * Set a value by reflection
     * @param object
     * @param fieldName
     * @param fieldValue
     * @return
     */
    public static boolean reflectionSet(Object object, String fieldName, Object fieldValue) {
        Class<?> clazz = object.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(object, fieldValue);
                return true;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Unzip a zip archive
     * @param zipFile input zip file
     * @param outputFolder zip file output folder
     * http://www.mkyong.com/java/how-to-decompress-files-from-a-zip-file/
     */
    public static void unzip(File zipFile, File outputFolder) throws IOException {

        byte[] buffer = new byte[1024];

        ZipInputStream zis = null;
        try {
            //create output directory is not exists
            if (!outputFolder.exists()){
                outputFolder.mkdir();
            }

            //get the zip file content
            zis = new ZipInputStream(new FileInputStream(zipFile));

            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                System.out.println("file unzip : "+ newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = new FileOutputStream(newFile);

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }

            System.out.println("Done");

        } catch(IOException ex){
            ex.printStackTrace();

        } finally {
            if (zis != null) {
                zis.closeEntry();
                zis.close();
            }
        }
    }
}
