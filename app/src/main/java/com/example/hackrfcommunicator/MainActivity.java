package com.example.hackrfcommunicator;

import static java.lang.Thread.sleep;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;


import com.example.hackrfcommunicator.hackrflib.Hackrf;
import com.example.hackrfcommunicator.hackrflib.HackrfCallbackInterface;
import com.example.hackrfcommunicator.hackrflib.HackrfUsbException;
import com.example.hackrfcommunicator.processor.IQConverter;
import com.example.hackrfcommunicator.processor.Processor;
import com.example.hackrfcommunicator.processor.SamplePacket;
import com.example.hackrfcommunicator.processor.Signed8BitIQConverter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements Runnable, HackrfCallbackInterface {
    private static final int frameRate = 2;
    private Hackrf hackrf;
    private int sampRate = 2000000;
    private long frequency = 102000000;
    private long virtualFrequency = 97000000;
    private int virtualSampleRate = 2000000;
    private int vgaGain = 40;
    private int lnaGain = 60;
    private boolean amp = true;
    private boolean antennaPower = false;
    private IQConverter iqConverter;
    private Processor processor;
    private ArrayList<BarEntry> drawingData = new ArrayList<BarEntry>();
    private Boolean upd = false;
    private NewDrawer drawingHandler = new NewDrawer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.openHackrf();

        this.iqConverter = new Signed8BitIQConverter();
        this.iqConverter.setFrequency(frequency);
        this.iqConverter.setSampleRate(sampRate);
        this.processor = new Processor();

        BarChart chart = findViewById(R.id.chart1);
        chart.getXAxis().setAxisMinimum(0);
        chart.getXAxis().setAxisMaximum(1000000);
        chart.getXAxis().setTextColor(Color.WHITE);

        chart.getAxisLeft().setAxisMaximum(0);
        chart.getAxisLeft().setAxisMinimum(-100);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);

        chart.setBackgroundColor(Color.DKGRAY);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);

        chart.setData(new BarData());
    }

    public void openHackrf() {
        int queueSize = 15000000 * 2;    // max. 15 Msps with 2 byte each ==> will buffer for 1 second

        // Initialize the HackRF (i.e. open the USB device, which requires the user to give permissions)
        if (!Hackrf.initHackrf(getApplicationContext(), this, queueSize)) {
            this.printOnScreen("Hackrf not found!");
        }
        // initHackrf() is asynchronous. this.onHackrfReady() will be called as soon as the device is ready.
    }

    @Override
    public void onHackrfReady(Hackrf hackrf) {
        this.hackrf = hackrf;
        printOnScreen("Connected to hackrf");
        new Thread(this).start();

        Drawer drawer = new Drawer();
        new Thread(drawer).start();
    }

    @Override
    public void run() {
        printOnScreen("Running thread...");
        this.receiveThread();
        printOnScreen("Thread started");
    }

    @Override
    public void onHackrfError(String message) {
        Toast.makeText(getApplicationContext(), "Could not open Hackrf", Toast.LENGTH_SHORT).show();
    }

    public void printOnScreen(final String msg) {
    }

    @SuppressLint("DefaultLocale")
    public void receiveThread() {
        int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int) (0.75 * sampRate));
        int i = 0;
        long lastTransceiverPacketCounter = 0;
        long lastTransceivingTime = 0;

        // vgaGain and lnaGain are still values from 0-100; scale them to the right range:
        vgaGain = (vgaGain * 62) / 100;
        lnaGain = (lnaGain * 40) / 100;

        try {
//            printOnScreen("Setting Sample Rate to " + sampRate + " Sps ... ");
            hackrf.setSampleRate(sampRate, 1);
//            printOnScreen("ok.\nSetting Frequency to " + frequency + " Hz ... ");
            hackrf.setFrequency(frequency);
//            printOnScreen("ok.\nSetting Baseband Filter Bandwidth to " + basebandFilterWidth + " Hz ... ");
            hackrf.setBasebandFilterBandwidth(basebandFilterWidth);
//            printOnScreen("ok.\nSetting RX VGA Gain to " + vgaGain + " ... ");
            hackrf.setRxVGAGain(vgaGain);
//            printOnScreen("ok.\nSetting LNA Gain to " + lnaGain + " ... ");
            hackrf.setRxLNAGain(lnaGain);
//            printOnScreen("ok.\nSetting Amplifier to " + amp + " ... ");
            hackrf.setAmp(amp);
//            printOnScreen("ok.\nSetting Antenna Power to " + antennaPower + " ... ");
            hackrf.setAntennaPower(antennaPower);
            printOnScreen("ok.\n\n");

            // Start Receiving:
            printOnScreen("Start Receiving... \n");
            ArrayBlockingQueue<byte[]> queue = hackrf.startRX();

            // Run until user hits the 'Stop' button
            while (true) {
                i++;    // only for statistics

                // Grab one packet from the top of the queue. Will block if queue is
                // empty and timeout after one second if the queue stays empty.
                byte[] receivedBytes = queue.poll(1000, TimeUnit.MILLISECONDS);

                SamplePacket packet = new SamplePacket(1024);
                this.iqConverter.fillPacketIntoSamplePacket(receivedBytes, packet);
                this.processor.processPacket(packet);
                if (i % 2 == 1) {
                    drawingHandler.sendMessage(new Message());
                }

                // We just write the whole packet into the file:
                if (receivedBytes != null) {
                    // IMPORTANT: After we used the receivedBytes buffer and don't need it any more,
                    // we should return it to the buffer pool of the hackrf! This will save a lot of
                    // allocation time and the garbage collector won't go off every second.
                    hackrf.returnBufferToBufferPool(receivedBytes);
                } else {
                    printOnScreen("Error: Queue is empty! (This happens most often because the queue ran full"
                            + " which causes the Hackrf class to stop receiving. Writing the samples to a file"
                            + " seems to be working to slowly... try a lower sample rate.)\n");
                    break;
                }

                // print statistics
                if (i % 10 == 0) {
                    long bytes = (hackrf.getTransceiverPacketCounter() - lastTransceiverPacketCounter) * hackrf.getPacketSize();
                    double time = (hackrf.getTransceivingTime() - lastTransceivingTime) / 1000.0;
                    printOnScreen(String.format("Current Transfer Rate: %4.1f MB/s\n", (bytes / time) / 1000000.0));
                    lastTransceiverPacketCounter = hackrf.getTransceiverPacketCounter();
                    lastTransceivingTime = hackrf.getTransceivingTime();
                }
            }

            printOnScreen(String.format("Finished! (Average Transfer Rate: %4.1f MB/s\n",
                    hackrf.getAverageTransceiveRate() / 1000000.0));
            printOnScreen(String.format("Recorded %d packets (each %d Bytes) in %5.3f Seconds.\n\n",
                    hackrf.getTransceiverPacketCounter(), hackrf.getPacketSize(),
                    hackrf.getTransceivingTime() / 1000.0));
        } catch (HackrfUsbException e) {
            // This exception is thrown if a USB communication error occurres (e.g. you unplug / reset
            // the device while receiving)
            printOnScreen("error (USB)!\n");
        } catch (InterruptedException e) {
            // This exception is thrown if queue.poll() is interrupted
            printOnScreen("error (Queue)!\n");
        } catch (Exception e) {
            printOnScreen(e.toString());
        }
    }

    private class NewDrawer extends Handler {
        private long lastDrawing = 0;

        public void handleMessage(Message msg) {
            if (System.currentTimeMillis() - lastDrawing < (1000 / frameRate)) {
                return;
            }

            Chart chart = findViewById(R.id.chart1);
            ArrayList<BarEntry> dataCopy = new ArrayList<>(drawingData);
            dataCopy.removeAll(Collections.singleton(null));

            BarDataSet dataSet = new BarDataSet(dataCopy, "label");

            chart.getData().clearValues();
            chart.getData().addDataSet(dataSet);
            chart.notifyDataSetChanged();
            chart.invalidate();

            lastDrawing = System.currentTimeMillis();
        }
    }

    private class Drawer extends Thread {
        public void run() {
            while (true) {
                float samplesPerHz = (float) processor.mag.length / (float) sampRate;    // indicates how many samples in mag cover 1 Hz
                long frequencyDiff = virtualFrequency - frequency;                // difference between center frequencies
                int sampleRateDiff = virtualSampleRate - sampRate;            // difference between sample rates
                int start = (int) ((frequencyDiff - sampleRateDiff / 2.0) * samplesPerHz);
                int end = processor.mag.length + (int) ((frequencyDiff + sampleRateDiff / 2.0) * samplesPerHz);

                drawingData.clear();
                for (int i = 0; i < processor.mag.length; i++) {
                    float xValue = i * (sampRate / processor.mag.length);  // Assuming linear x-axis
                    float yValue = processor.mag[i];
                    drawingData.add(new BarEntry(xValue, yValue));
                }

                if (drawingData.size() != 1024) {
                    Log.d("pre-drawer", "not enough lenght: " + drawingData.size());
                }
            }
        }
    }
}

