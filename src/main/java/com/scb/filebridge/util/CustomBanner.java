package com.scb.filebridge.util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.springframework.boot.Banner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;


public class CustomBanner implements Banner {

    private static final String BANNER_LOCATION = "/CustomBanner.txt";

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        try {
            Resource resource = new ClassPathResource(BANNER_LOCATION);
            InputStream inputStream = resource.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
            reader.close();
            inputStream.close();
        } catch (IOException e) {
            out.println("Failed to load banner");
            e.printStackTrace(out);
        }
    }
}