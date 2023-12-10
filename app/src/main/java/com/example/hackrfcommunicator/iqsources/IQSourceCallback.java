package com.example.hackrfcommunicator.iqsources;

import com.example.hackrfcommunicator.processor.SamplePacket;

public interface IQSourceCallback {
    void onSampleReceived(SamplePacket samples);
}
