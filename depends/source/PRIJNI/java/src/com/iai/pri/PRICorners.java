package com.iai.pri;

import com.iai.pri.PRIGroundPoint;

/**
 * Simple container class for passing data back and forth over JNI. This class
 * does not contain getters or setters because it makes retrieval and storage
 * of data easier from the c++ side of the JNI interface.
**/
public class PRICorners{
    public PRIGroundPoint ul;
    public PRIGroundPoint ur;
    public PRIGroundPoint lr;
    public PRIGroundPoint ll;

    public int width;
    public int height;

    public PRICorners(int width, int height,
                        PRIGroundPoint ul, PRIGroundPoint ur,
                        PRIGroundPoint lr, PRIGroundPoint ll){
        this.width = width;
        this.height = height;
        this.ul = ul;
        this.ur = ur;
        this.lr = lr;
        this.ll = ll;
    }

}
