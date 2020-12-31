# jpegdecoder
A Java command-line tool for decoding baseline jpeg images.

Progressive jpeg image decoding is not supported. This decoder supports chroma subsampled images.
```
usage: jpegdecode <jpeg-image>
```
       
jpeg-image: jpeg image source file

Images are decoded into bitmap(.bmp) in the same directory as the source image.