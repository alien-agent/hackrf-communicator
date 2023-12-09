package com.example.hackrfcommunicator.processor;

public class SamplePacket {
    private float[] re;            // real values
    private float[] im;            // imag values
    private long frequency;        // center frequency
    private int sampleRate;        // sample rate
    private int size;            // number of samples in this packet


    public SamplePacket(float[] re, float im[], long frequency, int sampleRate) {
        this(re, im, frequency, sampleRate, re.length);
    }


    public SamplePacket(float[] re, float im[], long frequency, int sampleRate, int size) {
        if (re.length != im.length)
            throw new IllegalArgumentException("Arrays must be of the same length");
        if (size > re.length)
            throw new IllegalArgumentException("Size must be of the smaller or equal the array length");
        this.re = re;
        this.im = im;
        this.frequency = frequency;
        this.sampleRate = sampleRate;
        this.size = size;
    }


    public SamplePacket(int size) {
        this.re = new float[size];
        this.im = new float[size];
        this.frequency = 0;
        this.sampleRate = 0;
        this.size = 0;
    }


    public float[] re() {
        return re;
    }


    public float re(int i) {
        return re[i];
    }


    public float[] im() {
        return im;
    }


    public float im(int i) {
        return im[i];
    }


    public int capacity() {
        return re.length;
    }


    public int size() {
        return size;
    }


    public void setSize(int size) {
        this.size = Math.min(size, re.length);
    }


    public long getFrequency() {
        return frequency;
    }


    public int getSampleRate() {
        return sampleRate;
    }


    public void setFrequency(long frequency) {
        this.frequency = frequency;
    }


    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }
}
