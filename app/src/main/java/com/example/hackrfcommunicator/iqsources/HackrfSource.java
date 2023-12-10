package com.example.hackrfcommunicator.iqsources;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.hackrfcommunicator.hackrflib.Hackrf;
import com.example.hackrfcommunicator.hackrflib.HackrfCallbackInterface;
import com.example.hackrfcommunicator.hackrflib.HackrfUsbException;
import com.example.hackrfcommunicator.processor.IQConverter;
import com.example.hackrfcommunicator.processor.SamplePacket;
import com.example.hackrfcommunicator.processor.Signed8BitIQConverter;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;


public class HackrfSource extends Thread implements HackrfCallbackInterface {
    private static final String componentTag = "HackrfSource";
    private Context context;
    private Hackrf hackrf;
    private IQSourceCallback callback;
    private int sampRate = 2000000;
    private long frequency = 102000000;
    private int vgaGain = 40;
    private int lnaGain = 60;
    private boolean amp = true;
    private boolean antennaPower = false;
    private IQConverter iqConverter;

    public HackrfSource(Context context, IQSourceCallback callback, int samplingRate, long frequency) {
        this.context = context;
        this.callback = callback;
        this.sampRate = samplingRate;
        this.frequency = frequency;

        this.iqConverter = new Signed8BitIQConverter();
        this.iqConverter.setSampleRate(samplingRate);
        this.iqConverter.setFrequency(frequency);
    }

    private void toast(String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onHackrfReady(Hackrf hackrf) {
        this.hackrf = hackrf;
        toast("Connected to hackrf");

        if (!this.configureHackrf()) {
            toast("Configuration failed!");
            return;
        }
        toast("Configuration complete!");

        new Thread(this).start();
    }

    @Override
    public void onHackrfError(String message) {
        toast("Error connecting to hackrf");
    }


    public void open() {
        int queueSize = 15000000 * 2;    // max. 15 Msps with 2 byte each ==> will buffer for 1 second

        // Initialize the HackRF (i.e. open the USB device, which requires the user to give permissions)
        if (!Hackrf.initHackrf(context, this, queueSize)) {
            toast("Hackrf not found");
        }
    }

    @Override
    public void run() {
        this.receiveThread();
    }

    private Boolean configureHackrf() {
        int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int) (0.75 * sampRate));
        vgaGain = (vgaGain * 62) / 100;
        lnaGain = (lnaGain * 40) / 100;

        try {
            hackrf.setSampleRate(sampRate, 1);
            hackrf.setFrequency(frequency);
            hackrf.setBasebandFilterBandwidth(basebandFilterWidth);
            hackrf.setRxVGAGain(vgaGain);
            hackrf.setRxLNAGain(lnaGain);
            hackrf.setAmp(amp);
            hackrf.setAntennaPower(antennaPower);
            return true;
        } catch (HackrfUsbException e) {
            return false;
        }
    }

    @SuppressLint("DefaultLocale")
    public void receiveThread() {
        int i = 0;

        try {
            ArrayBlockingQueue<byte[]> queue = hackrf.startRX();

            // Run until user hits the 'Stop' button
            while (true) {
                i++;    // only for statistics

                // Grab one packet from the top of the queue. Will block if queue is
                // empty and timeout after one second if the queue stays empty.
                byte[] receivedBytes = queue.poll(1000, TimeUnit.MILLISECONDS);

                SamplePacket packet = new SamplePacket(1024);
                this.iqConverter.fillPacketIntoSamplePacket(receivedBytes, packet);
                this.callback.onSampleReceived(packet);

                if (receivedBytes != null) {
                    hackrf.returnBufferToBufferPool(receivedBytes);
                } else {
                    toast("Error: Queue is empty!");
                    break;
                }
            }
        } catch (InterruptedException e) {
            // This exception is thrown if queue.poll() is interrupted
            toast("Queue error!");
        } catch (Exception e) {
            toast("Unknown error");
            Log.e(componentTag, "Unknown error!", e);
        }
    }
}
