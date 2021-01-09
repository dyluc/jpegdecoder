package com.thenullproject.jpegdecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

class JpegDecoder {
    
//    private Map<Integer, String> markerMap = Map.of(
//            0xffd8, "Start of Image",
//            0xffe0, "Application Specific (JFIF)",
//            0xffe1, "Application Specific (EXIF)",
//            0xffdb, "Define Quantization Table(s)",
//            0xffc0, "Start of Frame (Baseline DCT)", // baseline dct, ffc2 for progressive
//            0xffdd, "Define Restart Interval",
//            0xffc4, "Define Huffman Table(s)",
//            0xffda, "Start of Scan",
//            0xffd9, "End of Image"
//    );

    // values used to decode image data

    private String fileName;

    // huffman
    private Map<Integer, HuffmanTable> hTables; // <ht header, ht> DC Y, CbCr : 0, 1 AC Y, CbCr : 16, 17

    // qt
    private Map<Integer, int[]> qTables; // <qt destination, 8x8 table> destination Y : 0 CbCr : 1

    // sof
    private int precision; // bit precision
    private int width, height;
    private int mcuWidth;
    private int mcuHeight;
    private int mcuHSF; // horizontal sample factor
    private int mcuVSF; // vertical sample factor
    private boolean colour; // chroma components exist in jpeg
    private int mode; // 0 baseline 1 progressive(not supported yet)

    //dri
    private int restartInterval;

    void decode(String image) throws IOException {

        // jpeg image data
        int[] jpegImgData;
        try(DataInputStream dataIn = new DataInputStream(new FileInputStream(image))) {

            List<Integer> d = new ArrayList<>();

            while(dataIn.available() > 0) {
                int uByte = dataIn.readUnsignedByte(); // unsigned byte
                d.add(uByte);
            }

            jpegImgData = d.stream().mapToInt(Integer::intValue).toArray();
        }

        // init values
        qTables = new HashMap<>();
        hTables = new HashMap<>();
        fileName = image.substring(0, image.lastIndexOf('.'));
        mode = -1; // 'uninitialized' value, use first sof marker encountered

        System.out.println("Reading " + image + "...\n");

        // start decoding...
        main: for(int i = 0; i < jpegImgData.length; i++) {
            if(jpegImgData[i] == 0xff) {
                int m = jpegImgData[i] << 8 | jpegImgData[i+1];
                switch (m) {
                    case 0xffe0 -> System.out.println("-- JFIF --");
                    case 0xffe1 -> System.out.println("-- EXIF --");
                    case 0xffc4 -> { // dht
                        int length = jpegImgData[i + 2] << 8 | jpegImgData[i + 3];
                        decodeHuffmanTables(Arrays.copyOfRange(jpegImgData, i + 4, i + 2 + length));
                    }
                    case 0xffdb -> { // qt
                        int length = jpegImgData[i + 2] << 8 | jpegImgData[i + 3];
                        decodeQuantizationTables(Arrays.copyOfRange(jpegImgData, i + 4, i + 2 + length));
                    }
                    case 0xffdd -> { // dri
                        int length = jpegImgData[i + 2] << 8 | jpegImgData[i + 3];
                        int[] arr = Arrays.copyOfRange(jpegImgData, i + 4, i + 2 + length);
                        restartInterval = Arrays.stream(arr).sum();
                    }
                    case 0xffc0 -> { // sof-0 baseline
                        int length = jpegImgData[i + 2] << 8 | jpegImgData[i + 3];
                        decodeStartOfFrame(Arrays.copyOfRange(jpegImgData, i + 4, i + 2 + length));
                        if(mode == -1) mode = 0;
                    }
                    case 0xffc2 -> { // sof-1 progressive
                        if(mode == -1) mode = 1;
                    }
                    case 0xffda -> { // sos
                        int length = jpegImgData[i + 2] << 8 | jpegImgData[i + 3];
                        decodeStartOfScan(
                                /*Arrays.copyOfRange(jpegImgData, i + 4, i + 2 + length),*/
                                Arrays.copyOfRange(jpegImgData, i + 2 + length, jpegImgData.length - 2)); // last 2 two bytes are 0xffd9 - EOI
                        break main; // all done!
                    }
                }
            }
        }
    }

    private void decodeHuffmanTables(int[] chunk) {

        int cd = chunk[0]; // 00, 01, 10, 11 - 0, 1, 16, 17 - Y DC, CbCr DC, Y AC, CbCr AC
        int[] lengths = Arrays.copyOfRange(chunk, 1, 17);
        int to = 17 + Arrays.stream(lengths).sum();
        int[] symbols = Arrays.copyOfRange(chunk, 17, to);

        HashMap<Integer, int[]> lookup = new HashMap<>(); // code lengths, symbol(s)
        int si = 0;
        for(int i = 0; i < lengths.length; i++) {
            int l = lengths[i];

            int[] symbolsOfLengthI = new int[l];
            for(int j = 0; j < l; j++) {
                symbolsOfLengthI[j] = symbols[si];
                si++;
            }

            lookup.put(i+1, symbolsOfLengthI);
        }

        hTables.put(cd, new HuffmanTable(lookup));

        int[] newChunk = Arrays.copyOfRange(chunk, to, chunk.length);
        if(newChunk.length > 0)
            decodeHuffmanTables(newChunk);
    }

    private void decodeQuantizationTables(int[] chunk) {

        int d = chunk[0]; // 0, 1 - Y, CbCr
        int[] table = Arrays.copyOfRange(chunk, 1, 65); // 8x8 qt 64 values

        qTables.put(d, table);

        int[] newChunk = Arrays.copyOfRange(chunk, 65, chunk.length);
        if(newChunk.length > 0)
            decodeQuantizationTables(newChunk);
    }

    private void decodeStartOfFrame(int[] chunk) {
        precision = chunk[0];

        height = chunk[1] << 8 | chunk[2];
        width = chunk[3] << 8 | chunk[4];
        int noc = chunk[5]; // 1 grey-scale, 3 colour
        colour = noc==3;

        // component sample factor stored relatively, so y component sample factor contains information about how
        // large mcu is.
        for(int i = 0; i < noc; i++) {
            // int id = chunk[6+(i*3)]; // 1 = Y, 2 = Cb, 3 = Cr, 4 = I, 5 = Q
            int factor = chunk[7+(i*3)];
            if(i == 0) { // y component, check sample factor to determine mcu size
                mcuHSF = (factor >> 4); // first nibble (horizontal sample factor)
                mcuVSF = (factor & 0x0f); // second nibble (vertical sample factor)
                mcuWidth = 8 * mcuHSF;
                mcuHeight = 8 * mcuVSF;
                System.out.println("JPEG Sampling Factor -> " + mcuHSF + "x" + mcuVSF + (mcuHSF==1&&mcuVSF==1?" (No Subsampling)":" (Chroma Subsampling)"));
            }
            // int table = chunk[8+(i*3)];
        }
    }

    private void decodeStartOfScan(/*int[] chunk, */int[] imgData) {
        if(mode != 0) {
            System.err.println("This decoder only supports baseline JPEG images.");
            return;
        }

        System.out.println("Decoding Scan Image Data...");

        List<Integer> imgDataList = new ArrayList<>(imgData.length);
        for(int b : imgData) imgDataList.add(b);

        // check for and remove stuffing byte and restart markers
        for(int i = 0; i < imgDataList.size(); i++) {
            if (imgDataList.get(i).equals(0xff)) {
                int nByte = imgDataList.get(i + 1);
                if (nByte == 0x00) // stuffing byte
                    imgDataList.remove(i + 1);
                if (nByte >= 0xd0 && nByte <= 0xd7) { // remove restart marker
                    imgDataList.remove(i); // remove 0xff
                    imgDataList.remove(i); // remove 0xdn
                }
            }
        }

        // convert back to int[]
        imgData = new int[imgDataList.size()];
        for(int i = 0; i < imgDataList.size(); i++) imgData[i] = imgDataList.get(i);

        // list of converted matrices to write to file
        List<int[][]> convertedMCUs = new ArrayList<>();

        // start decoding
        int restartCount = restartInterval; // for restart markers, interval obtained from DRI marker
        BitStream stream = new BitStream(imgData);
        int[] oldDCCoes = new int[] {0, 0, 0}; // Y, Cb, Cr

        // matrices
        List<int[][]> yMatrices;
        int[][] yMatrix;
        int[][] cbMatrix = null;
        int[][] crMatrix = null;

        outer: for(int i = 0; i < (int)Math.ceil(height / (float)mcuHeight); i++) { // cast to float to avoid rounding errors
            for (int j = 0; j < (int)Math.ceil(width / (float)mcuWidth); j++) {

                // mcu
                yMatrices = new ArrayList<>(); // 2x2 - y0 y1 y2 y3 | 2x1 - y0 y1 | 1x1 y0

                // loop to obtain all luminance (y) matrices, which is greater than 1 if there is chroma subsampling
                for(int k = 0; k < mcuVSF; k++) {
                    for(int l = 0; l < mcuHSF; l++) {
                        yMatrix = createMatrix(stream, 0, oldDCCoes, 0);
                        if (yMatrix == null) // end of bit stream
                            break outer;
                        else
                            yMatrices.add(yMatrix);
                    }
                }

                if(colour) {
                    cbMatrix = createMatrix(stream, 1, oldDCCoes, 1);
                    crMatrix = createMatrix(stream, 1, oldDCCoes, 2);
                    if(cbMatrix == null || crMatrix == null) break outer; // end of bit stream
                }

                convertedMCUs.add(convertMCU(yMatrices,
                        cbMatrix,
                        crMatrix));

                if(restartInterval != 0) { // dri marker exists in image
                    if(--restartCount == 0) {
                        restartCount = restartInterval; // reset counter to interval

                        // reset DC coefficients
                        oldDCCoes[0] = 0;
                        oldDCCoes[1] = 0;
                        oldDCCoes[2] = 0;

                        stream.restart(); // set bit stream to start again on byte boundary
                    }
                }
            }
        }

        createDecodedBitMap(convertedMCUs);

    }


    private int[][] convertMCU(List<int[][]> yMatrices, int[][] cbMatrix, int[][] crMatrix) {
        // int values representing pixel colour or just luminance (greyscale image) in the sRGB ColorModel 0xAARRGGBB
        int[][] convertedMCU = new int[mcuHeight][mcuWidth];

        for(int r = 0; r < convertedMCU.length; r++) {
            for(int c = 0; c < convertedMCU[r].length; c++) {

                // luminance
                int yMatrixIndex = ((r/8)*(mcuHSF))+(c/8);
                int[][] yMatrix = yMatrices.get(yMatrixIndex);
                int y = yMatrix[r%8][c%8];

                float[] channels; // rgb or just luminance for greyscale
                if(colour) {
                    // chrominance
                    int cb = cbMatrix[r/mcuVSF][c/mcuHSF];
                    int cr = crMatrix[r/mcuVSF][c/mcuHSF];

                    channels = new float[] {
                            ( (y + (1.402f * cr)) ), // red
                            ( (y - (0.344f * cb) - (0.714f * cr)) ), // green
                            ( (y + (1.772f * cb)) ) // blue
                    };
                } else {
                    channels = new float[] { y };
                }

                for(int chan = 0; chan < channels.length; chan++) {
                    channels[chan] += 128; // shift block

                    // clamp block
                    if(channels[chan] > 255) channels[chan] = 255;
                    if(channels[chan] < 0) channels[chan] = 0;
                }

                convertedMCU[r][c] = 0xff<<24 | (int)channels[0]<<16 | (int)channels[colour?1:0]<< 8 | (int)channels[colour?2:0]; // 0xAARRGGBB
            }
        }
        return convertedMCU;

    }

    private void createDecodedBitMap(List<int[][]> rgbMCUs) {
        // prepare BufferedImage for writing blocks to
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // set buffered image pixel values for every matrix
        int blockCount = 0;
        for(int i = 0; i < (int)Math.ceil(height / (float)mcuHeight); i++) {
            for (int j = 0; j < (int)Math.ceil(width / (float)mcuWidth); j++) {
                for (int y = 0; y < mcuHeight; y++) { // mcu block
                    for (int x = 0; x < mcuWidth; x++) {
                        try {
                            img.setRGB((j * mcuWidth) + x, (i * mcuHeight) + y, rgbMCUs.get(blockCount)[y][x]);
                        } catch (ArrayIndexOutOfBoundsException ignored) {
                        } // extra part of partial mcu
                    }
                }
                blockCount++;
            }
        }

        // write bmp file
        try {
            ImageIO.write(img, "bmp", new File(fileName+".bmp"));
            System.out.println("Successful Write to File");
        } catch (IOException e) {
            System.err.println("Error Writing to BMP File. " + e.getLocalizedMessage());
        }
    }

    private int decodeComponent(int bits, int code) { // decodes to find signed value from bits
        float c = (float)Math.pow(2, code-1);
        return (int) (bits>=c?bits:bits-(c*2-1));
    }

    // key used for dc and ac huffman table and quantization table
    private int[][] createMatrix(BitStream stream, int key, int[] oldDCCoes, int oldDCCoIndex) {
        DCT3 inverseDCT = new DCT3(precision);

        int code = hTables.get(key).getCode(stream);
        if(code == -1) return null; // end of bit stream
        int bits = stream.getNextNBits(code);
        oldDCCoes[oldDCCoIndex] += decodeComponent(bits, code);
        // oldDCCo[oldDCCoIndex] is now new dc coefficient

        // set new dc value to old dc value multiplied by the first value in quantization table
        inverseDCT.setComponent(
                0,
                oldDCCoes[oldDCCoIndex] * qTables.get(key)[0]);

        int index = 1;
        while(index < 64) {
            code = hTables.get(key+16).getCode(stream);
            if(code == 0) {
                break; // end of block
            } else if(code == -1) {
                return null; // end of bit stream
            }

            // read first nibble of each code to find number of leading zeros
            int nib;
            if((nib = code >> 4) > 0) {
                index += nib;
                code &= 0x0f; // chop off preceding nibble
            }

            bits = stream.getNextNBits(code);

            if(index < 64) { // if haven't reached end of mcu
                int acCo = decodeComponent(bits, code); // ac coefficient
                inverseDCT.setComponent(
                        index,
                        acCo * qTables.get(key)[index]);
                index++;
            }
        }

        inverseDCT.zigzagRearrange();
        return inverseDCT.dct3();

    }
}
