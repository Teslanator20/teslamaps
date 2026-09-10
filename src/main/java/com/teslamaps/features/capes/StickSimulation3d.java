package com.teslamaps.features.capes;

// Ported from WaveyCapes' StickSimulation3d (verlet rope, point 0 fixed to the player)
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class StickSimulation3d {

    public final List<Point> points = new ArrayList<>();
    public final List<Stick> sticks = new ArrayList<>();
    public Vec3f gravityDirection = new Vec3f(0, -1, 0);
    public float gravity = 0;
    public int numIterations = 3;
    private final float maxBend = 20;
    public boolean sneaking = false;

    public boolean init(int partCount) {
        if (points.size() != partCount) {
            points.clear();
            sticks.clear();
            for (int i = 0; i < partCount; i++) {
                Point point = new Point();
                point.position.y = -i;
                point.position.x = -i;
                point.locked = i == 0;
                points.add(point);
                if (i > 0) {
                    sticks.add(new Stick(points.get(i - 1), point, 1f));
                }
            }
            return true;
        }
        return false;
    }

    public void simulate() {
        applyGravity();
        preventClipping();
        preventSelfClipping();
        applyMotion();
        preventSelfClipping();
        preventHardBends();
        limitLength();
    }

    private void applyGravity() {
        float deltaTime = 50f / 1000f;
        Vec3f down = gravityDirection.clone().mul(gravity * deltaTime);
        Vec3f tmp = new Vec3f(0, 0, 0);
        for (Point p : points) {
            if (!p.locked) {
                tmp.copy(p.position);
                p.position.add(down);
                p.prevPosition.copy(tmp);
            }
        }
    }

    private void applyMotion() {
        for (int i = 0; i < numIterations; i++) {
            for (int x = sticks.size() - 1; x >= 0; x--) {
                Stick stick = sticks.get(x);
                Vec3f stickCentre = stick.pointA.position.clone().add(stick.pointB.position).div(2);
                Vec3f stickDir = stick.pointA.position.clone().subtract(stick.pointB.position).normalize();
                if (!stick.pointA.locked) {
                    stick.pointA.position = stickCentre.clone().add(stickDir.clone().mul(stick.length / 2));
                }
                if (!stick.pointB.locked) {
                    stick.pointB.position = stickCentre.clone().subtract(stickDir.clone().mul(stick.length / 2));
                }
            }
        }
    }

    private void limitLength() {
        for (int x = 0; x < sticks.size(); x++) {
            Stick stick = sticks.get(x);
            Vec3f stickDir = stick.pointA.position.clone().subtract(stick.pointB.position).normalize();
            if (!stick.pointB.locked) {
                stick.pointB.position = stick.pointA.position.clone().subtract(stickDir.mul(stick.length));
            }
        }
    }

    private void preventSelfClipping() {
        boolean clipped;
        int runs = 0;
        do {
            clipped = false;
            for (int a = 0; a < points.size(); a++) {
                for (int b = a + 1; b < points.size(); b++) {
                    Point pA = points.get(a);
                    Point pB = points.get(b);
                    Vec3f stickDir = pA.position.clone().subtract(pB.position);
                    if (stickDir.sqrMagnitude() < 0.99) {
                        clipped = true;
                        runs++;
                        stickDir.normalize();
                        Vec3f centre = pA.position.clone().add(pB.position).div(2);
                        if (!pA.locked) {
                            pA.position = centre.clone().add(stickDir.clone().mul(1f / 2f));
                        }
                        if (!pB.locked) {
                            pB.position = centre.clone().subtract(stickDir.clone().mul(1f / 2f));
                        }
                    }
                }
            }
        } while (clipped && runs < 32);
    }

    private void preventHardBends() {
        for (int i = 1; i < points.size() - 2; i++) {
            double angle = getAngle(points.get(i).position, points.get(i - 1).position, points.get(i + 1).position);
            if (angle < -maxBend) {
                points.get(i + 1).position = getReplacement(points.get(i).position, points.get(i - 1).position, -maxBend * 2);
            }
            if (angle > maxBend) {
                points.get(i + 1).position = getReplacement(points.get(i).position, points.get(i - 1).position, maxBend * 2);
            }
        }
    }

    private void preventClipping() {
        Point basePoint = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Point p = points.get(i);
            if (p.position.x - basePoint.position.x > 0) {
                p.position.x = basePoint.position.x;
            }
            float maxZ = ((float) i / (float) points.size()) * ((float) i / (float) points.size()) * 5;
            float z = basePoint.position.z - p.position.z;
            if (z > maxZ) {
                p.position.z = basePoint.position.z - maxZ;
            }
            if (z < -maxZ) {
                p.position.z = basePoint.position.z + maxZ;
            }
        }
    }

    private Vec3f getReplacement(Vec3f middle, Vec3f prev, double target) {
        Vec3f dir = middle.clone().subtract(prev);
        dir.rotateDegrees((float) target).add(middle);
        return dir;
    }

    private double getAngle(Vec3f a, Vec3f b, Vec3f c) {
        float abx = b.x - a.x;
        float aby = b.y - a.y;
        float cbx = b.x - c.x;
        float cby = b.y - c.y;
        float dot = abx * cbx + aby * cby;
        float cross = abx * cby - aby * cbx;
        return Math.atan2(cross, dot) * 180 / Math.PI;
    }

    public void applyMovement(Vec3f movement) {
        points.get(0).prevPosition.copy(points.get(0).position);
        points.get(0).position.add(movement);
    }

    public boolean empty() {
        return sticks.isEmpty();
    }

    public static class Point {
        public Vec3f position = new Vec3f(0, 0, 0);
        public Vec3f prevPosition = new Vec3f(0, 0, 0);
        public boolean locked;

        public float getLerpX(float delta) {
            return Mth.lerp(delta, prevPosition.x, position.x);
        }

        public float getLerpY(float delta) {
            return Mth.lerp(delta, prevPosition.y, position.y);
        }

        public float getLerpZ(float delta) {
            return Mth.lerp(delta, prevPosition.z, position.z);
        }
    }

    public static class Stick {
        public final Point pointA, pointB;
        public final float length;

        public Stick(Point pointA, Point pointB, float length) {
            this.pointA = pointA;
            this.pointB = pointB;
            this.length = length;
        }
    }

    public static class Vec3f {
        public float x, y, z;

        public Vec3f(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
        }

        public Vec3f clone() {
            return new Vec3f(x, y, z);
        }

        public void copy(Vec3f vec) {
            this.x = vec.x; this.y = vec.y; this.z = vec.z;
        }

        public Vec3f add(Vec3f vec) {
            this.x += vec.x; this.y += vec.y; this.z += vec.z;
            return this;
        }

        public Vec3f subtract(Vec3f vec) {
            this.x -= vec.x; this.y -= vec.y; this.z -= vec.z;
            return this;
        }

        public Vec3f div(float amount) {
            this.x /= amount; this.y /= amount; this.z /= amount;
            return this;
        }

        public Vec3f mul(float amount) {
            this.x *= amount; this.y *= amount; this.z *= amount;
            return this;
        }

        public Vec3f normalize() {
            float f = Mth.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
            if (f < 1.0E-4F) {
                this.x = 0; this.y = 0; this.z = 0;
            } else {
                this.x /= f; this.y /= f; this.z /= f;
            }
            return this;
        }

        public Vec3f rotateDegrees(float deg) {
            float ox = x;
            float oy = y;
            deg = (float) Math.toRadians(deg);
            x = Mth.cos(deg) * ox - Mth.sin(deg) * oy;
            y = Mth.sin(deg) * ox + Mth.cos(deg) * oy;
            return this;
        }

        public float sqrMagnitude() {
            return x * x + y * y + z * z;
        }
    }
}
