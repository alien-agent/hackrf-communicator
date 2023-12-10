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
import com.example.hackrfcommunicator.iqsources.HackrfSource;
import com.example.hackrfcommunicator.iqsources.IQSourceCallback;
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

public class MainActivity extends AppCompatActivity implements IQSourceCallback {
    private long lastDrawing;
    private static final int frameRate = 15;
    private HackrfSource hackrf;
    private int sampRate = 2000000;
    private long frequency = 106200000;
    private Processor processor;
    private ArrayList<BarEntry> drawingData = new ArrayList<BarEntry>();
    private Boolean upd = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.hackrf = new HackrfSource(getApplicationContext(), this, sampRate, frequency);
        this.hackrf.open();

        this.processor = new Processor();

        this.configureChart();
    }

    private void configureChart(){
        BarChart chart = findViewById(R.id.chart1);

        // Configure chart
        chart.setBackgroundColor(Color.DKGRAY);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);

        // Configure X axis
//        chart.getXAxis().setAxisMinimum((float) (frequency - this.sampRate * 0.75));
//        chart.getXAxis().setAxisMaximum((float) (frequency + this.sampRate * 0.75));
        chart.getXAxis().setTextColor(Color.WHITE);

        // Configure Y axis
        chart.getAxisLeft().setAxisMaximum(0);
        chart.getAxisLeft().setAxisMinimum(-100);
        chart.getAxisLeft().setTextColor(Color.WHITE);

        // Disable right axis
        chart.getAxisRight().setEnabled(false);

        BarData barData = new BarData();
        chart.setData(barData);
    }

    @Override
    public void onSampleReceived(SamplePacket samples) {
        if (System.currentTimeMillis() - lastDrawing < (1000 / frameRate)) {
            return;
        }

        this.processor.processPacket(samples);

        ArrayList<BarEntry> drawingData = new ArrayList<BarEntry>();
        for (int i = 0; i < processor.mag.length; i++) {
            float xValue = i * (sampRate / processor.mag.length);  // Assuming linear x-axis
//            float xValue = (float) (frequency - this.sampRate * 0.75 + i*this.sampRate/processor.mag.length)/1000000;
            float yValue = processor.mag[i];
            drawingData.add(new BarEntry(xValue, yValue));
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

