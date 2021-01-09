package com.thenullproject.jpegdecoder;

class DCT3 { // inverse dct

    private final int[] components;
    private final int[][] zigzag;
    private final int precision;

    DCT3(int precision) {
        components = new int[64];
        zigzag = new int[][] {
                {0, 1, 5, 6, 14, 15, 27, 28},
                {2, 4, 7, 13, 16, 26, 29, 42},
                {3, 8, 12, 17, 25, 30, 41, 43},
                {9, 11, 18, 24, 31, 40, 44, 53},
                {10, 19, 23, 32, 39, 45, 52, 54},
                {20, 22, 33, 38, 46, 51, 55, 60},
                {21, 34, 37, 47, 50, 56, 59, 61},
                {35, 36, 48, 49, 57, 58, 62, 63}
        };
        this.precision = precision;
    }

    public void setComponent(int index, int value) {
        components[index] = value;
    }

    public void zigzagRearrange() {
        for(int x = 0; x < 8; x++) {
            for(int y = 0; y < 8; y++) {
                zigzag[x][y] = components[zigzag[x][y]];
            }
        }
    }

    public int[][] dct3() {
        int[][] matrix = new int[8][8];
        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                matrix[i][j] = j;
            }
        }

        // naive 0(n^4) - decoding high resolution jpg images will take a while
        for(int x = 0; x < 8; x++) {
            for(int y = 0; y < 8; y++) {
                int s = 0;
                for(int u = 0; u < precision; u++) {
                    for(int v = 0; v < precision; v++) {
                        s += (
                                zigzag[v][u]
                                * ((u==0?1.0f/Math.sqrt(2):1.0f) * Math.cos(((2.0 * x + 1.0) * u * Math.PI) / 16.0))
                                * ((v==0?1.0f/Math.sqrt(2):1.0f) * Math.cos(((2.0 * y + 1.0) * v * Math.PI) / 16.0))
                        );
                    }
                }
                matrix[y][x] = Math.floorDiv(s, 4);
            }
        }

        return matrix;
    }
}
