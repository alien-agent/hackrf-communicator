package com.example.hackrfcommunicator.processor;

import com.example.hackrfcommunicator.processor.IQConverter;
import com.example.hackrfcommunicator.processor.SamplePacket;

public class Signed8BitIQConverter extends IQConverter {

    public Signed8BitIQConverter() {
        super();
    }

    @Override
    protected void generateLookupTable() {
        /**
         * The HackRF delivers samples in the following format:
         * The bytes are interleaved, 8-bit, signed IQ samples (in-phase
         *  component first, followed by the quadrature component):
         *
         *  [--------- first sample ----------]   [-------- second sample --------]
         *         I                  Q                  I                Q ...
         *  receivedBytes[0]   receivedBytes[1]   receivedBytes[2]       ...
         */

        lookupTable = new float[256];
        for (int i = 0; i < 256; i++)
            lookupTable[i] = (i - 128) / 128.0f;
    }

    @Override
    protected void generateMixerLookupTable(int mixFrequency) {
        // If mix frequency is too low, just add the sample rate (sampled spectrum is periodic):
        if (mixFrequency == 0 || (sampleRate / Math.abs(mixFrequency) > MAX_COSINE_LENGTH))
            mixFrequency += sampleRate;

        // Only generate lookupTable if null or invalid:
        if (cosineRealLookupTable == null || mixFrequency != cosineFrequency) {
            cosineFrequency = mixFrequency;
            int bestLength = calcOptimalCosineLength();
            cosineRealLookupTable = new float[bestLength][256];
            cosineImagLookupTable = new float[bestLength][256];
            float cosineAtT;
            float sineAtT;
            for (int t = 0; t < bestLength; t++) {
                cosineAtT = (float) Math.cos(2 * Math.PI * cosineFrequency * t / (float) sampleRate);
                sineAtT = (float) Math.sin(2 * Math.PI * cosineFrequency * t / (float) sampleRate);
                for (int i = 0; i < 256; i++) {
                    cosineRealLookupTable[t][i] = (i - 128) / 128.0f * cosineAtT;
                    cosineImagLookupTable[t][i] = (i - 128) / 128.0f * sineAtT;
                }
            }
            cosineIndex = 0;
        }
    }

    @Override
    public int fillPacketIntoSamplePacket(byte[] packet, SamplePacket samplePacket) {
        int capacity = samplePacket.capacity();
        int count = 0;
        int startIndex = samplePacket.size();
        float[] re = samplePacket.re();
        float[] im = samplePacket.im();
        for (int i = 0; i < packet.length; i += 2) {
            re[startIndex + count] = lookupTable[packet[i] + 128];
            im[startIndex + count] = lookupTable[packet[i + 1] + 128];
            count++;
            if (startIndex + count >= capacity)
                break;
        }
        samplePacket.setSize(samplePacket.size() + count);    // update the size of the sample packet
        samplePacket.setSampleRate(sampleRate);                // update the sample rate
        samplePacket.setFrequency(frequency);                // update the frequency
        return count;
    }
}
