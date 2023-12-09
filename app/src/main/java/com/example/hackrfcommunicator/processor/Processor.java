package com.example.hackrfcommunicator.processor;

import java.util.concurrent.ArrayBlockingQueue;

public class Processor extends Thread {
    private final int fftSize = 1024;                    // Size of the FFT
    private int frameRate = 10;                    // Frames per Second
    public float[] mag;
    private FFT fftBlock;

    public Processor() {
        int order = (int) (Math.log(fftSize) / Math.log(2));
        if (fftSize != (1 << order))
            throw new IllegalArgumentException("FFT size must be power of 2");

        this.fftBlock = new FFT(fftSize);
        this.mag = new float[fftSize];
    }

    public void run() {

    }

    public void processPacket(SamplePacket samples) {
        float[] re = samples.re(), im = samples.im();
        // Multiply the samples with a Window function:
        this.fftBlock.applyWindow(re, im);

        // Calculate the fft:
        this.fftBlock.fft(re, im);

        // Calculate the logarithmic magnitude:
        float realPower;
        float imagPower;
        int size = samples.size();
        for (int i = 0; i < size; i++) {
            // We have to flip both sides of the fft to draw it centered on the screen:
            int targetIndex = (i + size / 2) % size;

            // Calc the magnitude = log(  re^2 + im^2  )
            // note that we still have to divide re and im by the fft size
            realPower = re[i] / fftSize;
            realPower = realPower * realPower;
            imagPower = im[i] / fftSize;
            imagPower = imagPower * imagPower;
            mag[targetIndex] = (float) (10 * Math.log10(Math.sqrt(realPower + imagPower)));
        }
    }
}
