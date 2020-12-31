package com.thenullproject.jpegdecoder;

import java.io.FileNotFoundException;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if(args.length == 1) {
            String ext = args[0].substring(args[0].lastIndexOf(".")).toLowerCase();
            if(ext.equals(".jpg") || ext.equals(".jpeg")) { try {
                    new JpegDecoder().decode(args[0]);
                } catch (FileNotFoundException e) {
                    System.err.println("Couldn't find file.");
                } catch (IOException e) {
                    System.err.println("IOException occurred. e -> " + e.getLocalizedMessage());
                }
            } else System.out.println("image must be a jpg");
        } else System.out.println("usage: jpegdecode <jpeg-image>");
    }
}