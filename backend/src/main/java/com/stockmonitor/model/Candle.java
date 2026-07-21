package com.stockmonitor.model;

import java.time.LocalDate;

public class Candle {

    private final LocalDate date;
    private final double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    public Candle(LocalDate date, double open, double high, double low, double close, long volume) {
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public synchronized Candle snapshot() {
        return new Candle(date, open, high, low, close, volume);
    }

    public synchronized void tick(double newClose, long addedVolume) {
        this.close = newClose;
        this.high = Math.max(this.high, newClose);
        this.low = Math.min(this.low, newClose);
        this.volume += addedVolume;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getTime() {
        return date.toString();
    }

    public double getOpen() {
        return open;
    }

    public synchronized double getHigh() {
        return high;
    }

    public synchronized double getLow() {
        return low;
    }

    public synchronized double getClose() {
        return close;
    }

    public synchronized long getVolume() {
        return volume;
    }
}
