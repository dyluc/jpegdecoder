package com.thenullproject.jpegdecoder;

class BitStream {
    private final int[] data;
    private int position; // bit position

    private int cByte; // current byte
    private int cByteIndex;

    private int bit;

    BitStream(int[] data) {
        this.data = data;
        position = 0;
    }

    public int bit() {
        cByteIndex = position >> 3;
        if(cByteIndex == data.length) return -1;
        cByte = data[cByteIndex];
        bit = (cByte >> (7 - (position % 8))) & 1;
        position++;
        return bit;
    }

    // start on byte boundary
    public void restart() {
        if((position & 7) > 0)
            position += (8 - (position & 7));
    }

    public int getNextNBits(int n) {
        int r = 0;
        for(int i = 0; i < n; i++)
            r = r*2 + bit();
        return r;
    }
}