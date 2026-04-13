/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

import java.util.Objects;

/**
 * Infrared distance sensor input type for FEAGI PNS.
 *
 * <p>This class represents an infrared (IR) distance sensor that measures
 * the distance to objects in its field of view. It extends NumericStream
 * with infrared-specific configuration and processing.</p>
 *
 * <p>Infrared sensors are commonly used for:</p>
 * <ul>
 *   <li>Obstacle detection and avoidance</li>
 *   <li>Proximity sensing</li>
 *   <li>Depth perception in robotics</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * InfraredInput irSensor = new InfraredInput(0.03, 0.40, 25.0, "front", "GP2Y0A21YK0F", 0);
 * irSensor._registerWithCache();
 * irSensor.writeDistance(0.25);  // 25cm
 * }</pre>
 *
 * @see NumericStream
 * @see BaseInput
 */
public class InfraredInput extends NumericStream {

    /**
     * Field of view in degrees.
     */
    private final double fieldOfView;

    /**
     * Sensor position identifier.
     */
    private final String position;

    /**
     * Sensor model/manufacturer identifier.
     */
    private final String sensorModel;

    /**
     * Creates a new InfraredInput with default values.
     */
    public InfraredInput() {
        this(0.0, 1.0, 25.0, null, null, 0);
    }

    /**
     * Creates a new InfraredInput with the specified configuration.
     *
     * @param minRange the minimum measurable distance in meters
     * @param maxRange the maximum measurable distance in meters
     * @param fieldOfView the field of view angle in degrees
     * @param position the sensor position identifier
     * @param sensorModel the sensor model identifier
     * @param groupId the group ID (0-255)
     * @throws IllegalArgumentException if minRange >= maxRange or fieldOfView <= 0 or groupId out of range
     */
    public InfraredInput(double minRange, double maxRange, double fieldOfView,
                         String position, String sensorModel, int groupId) {
        super(groupId, 0.001, minRange, maxRange, 1.0, true);
        if (fieldOfView <= 0) {
            throw new IllegalArgumentException("fieldOfView must be positive, got: " + fieldOfView);
        }
        if (groupId < 0 || groupId > 255) {
            throw new IllegalArgumentException("groupId must be in range [0, 255], got: " + groupId);
        }
        this.fieldOfView = fieldOfView;
        this.position = position;
        this.sensorModel = sensorModel;
    }

    /**
     * Create a builder for InfraredInput configuration.
     *
     * @return a new InfraredBuilder instance
     */
    public static InfraredBuilder createBuilder() {
        return new InfraredBuilder();
    }

    /**
     * Get the field of view in degrees.
     *
     * @return the field of view angle
     */
    public double fieldOfView() {
        return fieldOfView;
    }

    /**
     * Get the sensor position identifier.
     *
     * @return the position identifier, or null if not set
     */
    public String position() {
        return position;
    }

    /**
     * Get the sensor model identifier.
     *
     * @return the model string, or null if not set
     */
    public String sensorModel() {
        return sensorModel;
    }

    /**
     * Write a distance measurement.
     *
     * @param distance the distance value in meters
     * @throws IllegalArgumentException if distance is out of range
     */
    public void writeDistance(double distance) {
        writeValue(distance);
    }

    /**
     * Get the current distance in centimeters.
     *
     * @return the distance in cm, or null if no value has been set
     */
    public Double getCurrentDistanceCm() {
        Double value = getCurrentValue();
        return (value != null) ? value * 100.0 : null;
    }

    /**
     * Check if an object is detected within a threshold distance.
     *
     * @param threshold the threshold distance in meters
     * @return true if an object is detected within the threshold
     */
    public boolean isObjectDetected(double threshold) {
        Double current = getCurrentValue();
        return current != null && current <= threshold;
    }

    /**
     * Get the detection cone area at a given distance.
     *
     * <p>The detection cone expands with distance based on the field of view.</p>
     *
     * @param distance the distance from the sensor
     * @return the approximate cone diameter at that distance
     */
    public double getDetectionConeDiameter(double distance) {
        double halfFovRadians = Math.toRadians(fieldOfView / 2.0);
        return 2.0 * distance * Math.tan(halfFovRadians);
    }

    @Override
    protected void _registerWithCache() {
        markRegistered();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        InfraredInput that = (InfraredInput) o;

        if (Double.compare(that.fieldOfView, fieldOfView) != 0) return false;
        if (!Objects.equals(position, that.position)) return false;
        return Objects.equals(sensorModel, that.sensorModel);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (Double.doubleToLongBits(fieldOfView) ^ (Double.doubleToLongBits(fieldOfView) >>> 32));
        result = 31 * result + (position != null ? position.hashCode() : 0);
        result = 31 * result + (sensorModel != null ? sensorModel.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "InfraredInput{" +
            "fieldOfView=" + fieldOfView +
            ", position='" + position + '\'' +
            ", sensorModel='" + sensorModel + '\'' +
            ", minValue=" + minValue() +
            ", maxValue=" + maxValue() +
            ", groupId=" + groupId() +
            '}';
    }

    /**
     * Builder for InfraredInput configuration.
     * <p>This class uses a unique name 'InfraredBuilder' to avoid conflicts
     * with NumericStream.Builder in the inheritance hierarchy.</p>
     */
    public static final class InfraredBuilder {
        private double precision = 0.001;
        private double minRange = 0.0;
        private double maxRange = 1.0;
        private double scaleFactor = 1.0;
        private boolean clampToRange = true;
        private int groupId = 0;
        private double fieldOfView = 25.0;
        private String position;
        private String sensorModel;

        private InfraredBuilder() {}

        /**
         * Set the distance measurement range.
         */
        public InfraredBuilder range(double minRange, double maxRange) {
            if (minRange >= maxRange) {
                throw new IllegalArgumentException(
                    "minRange must be less than maxRange, got [" + minRange + ", " + maxRange + "]");
            }
            this.minRange = minRange;
            this.maxRange = maxRange;
            return this;
        }

        /**
         * Set the field of view in degrees.
         */
        public InfraredBuilder fieldOfView(double fieldOfView) {
            if (fieldOfView <= 0) {
                throw new IllegalArgumentException("fieldOfView must be positive, got: " + fieldOfView);
            }
            this.fieldOfView = fieldOfView;
            return this;
        }

        /**
         * Set the sensor position identifier.
         */
        public InfraredBuilder position(String position) {
            this.position = position;
            return this;
        }

        /**
         * Set the sensor model identifier.
         */
        public InfraredBuilder sensorModel(String sensorModel) {
            this.sensorModel = sensorModel;
            return this;
        }

        /**
         * Set the precision.
         */
        public InfraredBuilder precision(double precision) {
            if (precision <= 0) {
                throw new IllegalArgumentException("precision must be positive, got: " + precision);
            }
            this.precision = precision;
            return this;
        }

        /**
         * Set the scaling factor.
         */
        public InfraredBuilder scaleFactor(double scaleFactor) {
            if (scaleFactor <= 0) {
                throw new IllegalArgumentException("scaleFactor must be positive, got: " + scaleFactor);
            }
            this.scaleFactor = scaleFactor;
            return this;
        }

        /**
         * Enable or disable clamping to range.
         */
        public InfraredBuilder clampToRange(boolean clampToRange) {
            this.clampToRange = clampToRange;
            return this;
        }

        /**
         * Set the group ID.
         */
        public InfraredBuilder groupId(int groupId) {
            if (groupId < 0 || groupId > 255) {
                throw new IllegalArgumentException("groupId must be in range [0, 255], got: " + groupId);
            }
            this.groupId = groupId;
            return this;
        }

        /**
         * Build the InfraredInput instance.
         */
        public InfraredInput build() {
            return new InfraredInput(groupId, precision, minRange, maxRange, scaleFactor, clampToRange,
                                     fieldOfView, position, sensorModel);
        }
    }

    /**
     * Protected constructor for builder use.
     */
    private InfraredInput(int groupId, double precision, double minRange, double maxRange,
                          double scaleFactor, boolean clampToRange, double fieldOfView,
                          String position, String sensorModel) {
        super(groupId, precision, minRange, maxRange, scaleFactor, clampToRange);
        if (fieldOfView <= 0) {
            throw new IllegalArgumentException("fieldOfView must be positive, got: " + fieldOfView);
        }
        this.fieldOfView = fieldOfView;
        this.position = position;
        this.sensorModel = sensorModel;
    }
}
