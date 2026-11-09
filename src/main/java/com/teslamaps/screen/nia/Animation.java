package com.teslamaps.screen.nia;

// Ported from NiaMod's gui/animation/Animation.java and Easing.java
final class Animation {

    enum Easing {
        LINEAR,
        EASE_OUT_EXPO,
        EASE_OUT_CUBIC,
        EASE_OUT_QUINT;

        double apply(double t) {
            return switch (this) {
                case LINEAR -> t;
                case EASE_OUT_EXPO -> t >= 1 ? 1 : 1 - Math.pow(2, -10 * t);
                case EASE_OUT_CUBIC -> { double f = t - 1; yield f * f * f + 1; }
                case EASE_OUT_QUINT -> { double f = t - 1; yield f * f * f * f * f + 1; }
            };
        }
    }

    private Easing easing;
    private long duration;
    private long millis;
    private double value;
    private double destination;
    private boolean finished = true;

    Animation(Easing easing, long duration) {
        this.easing = easing;
        this.duration = duration;
    }

    void setEasing(Easing easing) { this.easing = easing; }
    void setDuration(long duration) { this.duration = duration; }
    long getDuration() { return duration; }
    boolean isFinished() { return finished; }

    void run(double destination) {
        if (this.destination != destination) {
            this.millis = System.currentTimeMillis();
            this.value = getValue();
            this.destination = destination;
            this.finished = false;
        }
    }

    double getValue() {
        if (finished) return destination;
        double progress = getProgress();
        return value + (destination - value) * progress;
    }

    void setValue(double value) {
        this.value = value;
        this.destination = value;
        this.finished = true;
    }

    private double getProgress() {
        if (duration <= 0) {
            finished = true;
            return 1;
        }
        double progress = (double) (System.currentTimeMillis() - millis) / duration;
        progress = Math.clamp(progress, 0, 1);
        if (progress >= 1) finished = true;
        return easing.apply(progress);
    }
}
