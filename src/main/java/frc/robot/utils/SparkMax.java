// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.utils;

import org.littletonrobotics.junction.AutoLog;
import org.littletonrobotics.junction.Logger;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMax.ControlType;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import com.revrobotics.MotorFeedbackSensor;
import com.revrobotics.SparkMaxAbsoluteEncoder;
import com.revrobotics.SparkMaxAnalogSensor;
import com.revrobotics.SparkMaxLimitSwitch;
import com.revrobotics.SparkMaxPIDController;

import edu.wpi.first.wpilibj.DataLogManager;

public class SparkMax implements AutoCloseable {

  public static class ID {
    public final int deviceID;
    public final String name;

    public ID(int deviceID, String name) {
      this.deviceID = deviceID;
      this.name = name;
    }
  }

  public enum FeedbackSensor {
    NEO_ENCODER, ANALOG, THROUGH_BORE_ENCODER;
  }

  @AutoLog
  public static class SparkMaxInputs {
    public double encoderPosition = 0.0;
    public double encoderVelocity = 0.0;
    public double analogPosition = 0.0;
    public double analogVelocity = 0.0;
    public double absoluteEncoderPosition = 0.0;
    public double absoluteEncoderVelocity = 0.0;
    public boolean forwardLimitSwitch = false;
    public boolean reverseLimitSwitch = false;
  }

  private static final int PID_SLOT = 0;
  private static final double MAX_VOLTAGE = 12.0;
  private static final String VALUE_LOG_ENTRY = "/OutputValue";
  private static final String MODE_LOG_ENTRY = "/OutputMode";
  private static final String CURRENT_LOG_ENTRY = "/Current";
  private static final String ENCODER_RESET_MESSAGE = "/EncoderReset";
  
  private CANSparkMax m_spark;

  private String m_name;
  private SparkMaxInputsAutoLogged m_inputs;

  /**
   * Create a Spark Max object that is unit-testing friendly
   * @param deviceID The device ID
   * @param motorType The motor type connected to the controller
   */
  public SparkMax(ID id, MotorType motorType) {
    this.m_name = id.name;
    this.m_spark = new CANSparkMax(id.deviceID, motorType);
    this.m_inputs = new SparkMaxInputsAutoLogged();

    m_spark.restoreFactoryDefaults();
    m_spark.enableVoltageCompensation(MAX_VOLTAGE);
  }

  /**
   * Log output values
   * @param value Value that was set
   * @param ctrl Control mode that was used
   */
  private void logOutputs(double value, ControlType ctrl) {
    Logger.getInstance().recordOutput(m_name + VALUE_LOG_ENTRY, value);
    Logger.getInstance().recordOutput(m_name + MODE_LOG_ENTRY, ctrl.name());
    Logger.getInstance().recordOutput(m_name + CURRENT_LOG_ENTRY, m_spark.getOutputCurrent());
  }

  /**
   * Get the position of the motor. This returns the native units of 'rotations' by default, and can
   * be changed by a scale factor using setPositionConversionFactor().
   * @return Number of rotations of the motor
   */
  private double getEncoderPosition() {
    return m_spark.getEncoder().getPosition();
  }

  /**
   * Get the velocity of the motor. This returns the native units of 'RPM' by default, and can be
   * changed by a scale factor using setVelocityConversionFactor().
   * @return Number the RPM of the motor
   */
  private double getEncoderVelocity() {
    return m_spark.getEncoder().getVelocity();
  }

  /**
   * Returns an object for interfacing with a connected analog sensor.
   * @return An object for interfacing with a connected analog sensor
   */
  private SparkMaxAnalogSensor getAnalog() {
    return m_spark.getAnalog(SparkMaxAnalogSensor.Mode.kAbsolute);
  }

  /**
   * Get position of the motor. This returns the native units 'volt' by default, and can
   * be changed by a scale factor using setPositionConversionFactor().
   * @return Volts on the sensor
   */
  private double getAnalogPosition() {
    return getAnalog().getPosition();
  }

  /**
   * Get the velocity of the motor. This returns the native units of 'volts per second' by default, and can be
   * changed by a scale factor using setVelocityConversionFactor().
   * @return Volts per second on the sensor
   */
  private double getAnalogVelocity() {
    return getAnalog().getVelocity();
  }

  /**
   * Returns an object for interfacing with a connected absolute encoder.
   * @return An object for interfacing with a connected absolute encoder
   */
  private AbsoluteEncoder getAbsoluteEncoder() {
    return m_spark.getAbsoluteEncoder(SparkMaxAbsoluteEncoder.Type.kDutyCycle);
  }

  /**
   * Get position of the motor. This returns the native units 'rotations' by default, and can
   * be changed by a scale factor using setPositionConversionFactor().
   * @return Number of rotations of the motor
   */
  private double getAbsoluteEncoderPosition() {
    return getAbsoluteEncoder().getPosition();
  }

  /**
   * Get the velocity of the motor. This returns the native units of 'RPM' by default, and can be
   * changed by a scale factor using setVelocityConversionFactor().
   * @return Number the RPM of the motor
   */
  private double getAbsoluteEncoderVelocity() {
    return getAbsoluteEncoder().getVelocity();
  }

  /**
   * Update sensor input readings
   */
  private void updateInputs() {
    m_inputs.encoderPosition = getEncoderPosition();
    m_inputs.encoderVelocity = getEncoderVelocity();
    m_inputs.analogPosition = getAnalogPosition();
    m_inputs.analogVelocity = getAnalogVelocity();
    m_inputs.absoluteEncoderPosition = getAbsoluteEncoderPosition();
    m_inputs.absoluteEncoderVelocity = getAbsoluteEncoderVelocity();
    m_inputs.forwardLimitSwitch = m_spark.getForwardLimitSwitch(SparkMaxLimitSwitch.Type.kNormallyOpen).isPressed();
    m_inputs.reverseLimitSwitch = m_spark.getReverseLimitSwitch(SparkMaxLimitSwitch.Type.kNormallyOpen).isPressed();
  }

  /**
   * Call this method periodically
   */
  public void periodic() {
    updateInputs();
    Logger.getInstance().processInputs(m_name, m_inputs);
  }

  /**
   * Get latest sensor input data
   * @return Latest sensor data
   */
  public SparkMaxInputs getInputs() {
    return m_inputs;
  }

  /**
   * Initializes Spark Max PID
   * @param config Configuration to apply
   * @param feedbackSensor Feedback device to use for Spark PID
   * @param forwardLimitSwitch Enable forward limit switch
   * @param reverseLimitSwitch Enable reverse limit switch
   */
  public void initializeSparkPID(SparkPIDConfig config, FeedbackSensor feedbackSensor,
                                 boolean forwardLimitSwitch, boolean reverseLimitSwitch) {
    MotorFeedbackSensor selectedSensor;
    switch (feedbackSensor) {
      case NEO_ENCODER:
        selectedSensor = m_spark.getEncoder();
        break;
      case ANALOG:
        selectedSensor = m_spark.getAnalog(SparkMaxAnalogSensor.Mode.kAbsolute);
        break;
      case THROUGH_BORE_ENCODER:
        selectedSensor = m_spark.getAbsoluteEncoder(SparkMaxAbsoluteEncoder.Type.kDutyCycle);
        break;
      default:
        selectedSensor = m_spark.getEncoder();
        break;
    }

    config.initializeSparkPID(m_spark, selectedSensor, forwardLimitSwitch, reverseLimitSwitch);
  }

  /**
   * Initializes Spark Max PID
   * <p>
   * Calls {@link SparkMax#initializeSparkPID(SparkPIDConfig, FeedbackSensor, boolean, boolean)} with no limit switches 
   * @param config Configuration to apply
   * @param feedbackSensor Feedback device to use for Spark PID
   */
  public void initializeSparkPID(SparkPIDConfig config, FeedbackSensor feedbackSensor) {
    initializeSparkPID(config, feedbackSensor, false, false);
  }

  /**
   * Set motor output duty cycle
   * @param value Value to set [-1.0, +1.0]
   */
  public void set(double value) {
    set(value, ControlType.kDutyCycle);
  }

  /**
   * Set motor output value
   * @param value Value to set
   * @param ctrl Desired control mode
   */
  public void set(double value, ControlType ctrl) {
    m_spark.getPIDController().setReference(value, ctrl);
    logOutputs(value, ctrl);
  }

  /**
   * Set motor output value with arbitrary feed forward
   * @param value Value to set
   * @param ctrl Desired control mode
   * @param arbFeedforward Feed forward value
   * @param arbFFUnits Feed forward units
   */
  public void set(double value, ControlType ctrl, double arbFeedforward, SparkMaxPIDController.ArbFFUnits arbFFUnits) {
    set(value, ctrl, arbFeedforward, arbFFUnits, PID_SLOT);
  }

  /**
   * Set motor output value with arbitrary feed forward
   * @param value Value to set
   * @param ctrl Desired control mode
   * @param arbFeedforward Feed forward value
   * @param arbFFUnits Feed forward units
   * @param pidSlot PID slot to use
   */
  public void set(double value, ControlType ctrl, double arbFeedforward, SparkMaxPIDController.ArbFFUnits arbFFUnits, int pidSlot) {
    m_spark.getPIDController().setReference(value, ctrl, pidSlot, arbFeedforward, arbFFUnits);
    logOutputs(value, ctrl);
  }

  /**
   * Set the conversion factor for position of the encoder. Multiplied by the native output units to
   * give you position.
   * @param FeedbackSensor Sensor to set conversion factor for
   * @param factor The conversion factor to multiply the native units by
   */
  public void setPositionConversionFactor(FeedbackSensor sensor, double factor) {
    switch (sensor) {
      case NEO_ENCODER:
        m_spark.getEncoder().setPositionConversionFactor(factor);
        break;
      case ANALOG:
        getAnalog().setPositionConversionFactor(factor);
        break;
      case THROUGH_BORE_ENCODER:
        getAbsoluteEncoder().setPositionConversionFactor(factor);
        break;
      default:
        break;
    }
  }

  /**
   * Set the conversion factor for velocity of the encoder. Multiplied by the native output units to
   * give you velocity.
   * @param FeedbackSensor Sensor to set conversion factor for
   * @param factor The conversion factor to multiply the native units by
   */
  public void setVelocityConversionFactor(FeedbackSensor sensor, double factor) {
    switch (sensor) {
      case NEO_ENCODER:
        m_spark.getEncoder().setVelocityConversionFactor(factor);
        break;
      case ANALOG:
        getAnalog().setVelocityConversionFactor(factor);
        break;
      case THROUGH_BORE_ENCODER:
        getAbsoluteEncoder().setVelocityConversionFactor(factor);
        break;
      default:
        break;
    }
  }

  /**
   * Reset NEO built-in encoder
   */
  public void resetEncoder() {
    m_spark.getEncoder().setPosition(0.0);
    DataLogManager.log(m_name + ENCODER_RESET_MESSAGE);
  }

  /**
   * Enable PID wrapping for closed loop position control
   * @param minInput Value of the min input for position
   * @param maxInput Value of max input for position
   */
  public void enablePIDWrapping(double minInput, double maxInput) {
    m_spark.getPIDController().setPositionPIDWrappingEnabled(true);
    m_spark.getPIDController().setPositionPIDWrappingMinInput(minInput);
    m_spark.getPIDController().setPositionPIDWrappingMaxInput(maxInput);
  }

  /**
   * Disable PID wrapping for close loop position control
   */
  public void disablePIDWrapping() {
    m_spark.getPIDController().setPositionPIDWrappingEnabled(false);
  }

  /**
   * Sets the idle mode setting for the SPARK MAX.
   * @param mode Idle mode (coast or brake).
   */
  public void setIdleMode(IdleMode mode) {
    m_spark.setIdleMode(mode);
  }

  /**
   * Sets the current limit in Amps.
   *
   * <p>The motor controller will reduce the controller voltage output to avoid surpassing this
   * limit. This limit is enabled by default and used for brushless only. This limit is highly
   * recommended when using the NEO brushless motor.
   *
   * <p>The NEO Brushless Motor has a low internal resistance, which can mean large current spikes
   * that could be enough to cause damage to the motor and controller. This current limit provides a
   * smarter strategy to deal with high current draws and keep the motor and controller operating in
   * a safe region.
   * 
   * @param limit The current limit in Amps.
   */
  public void setSmartCurrentLimit(int limit) {
    m_spark.setSmartCurrentLimit(limit);
  }

  /**
   * Stops motor movement. Motor can be moved again by calling set without having to re-enable the
   * motor.
   */
  public void stopMotor() {
    m_spark.stopMotor();
  }

  /**
   * Closes the Spark Max controller
   */
  @Override
  public void close() {
    m_spark.close();
  }
}
