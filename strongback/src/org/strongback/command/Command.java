/*
 * Strongback
 * Copyright 2015, Strongback and individual contributors by the @authors tag.
 * See the COPYRIGHT.txt in the distribution for a full listing of individual
 * contributors.
 *
 * Licensed under the MIT License; you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://opensource.org/licenses/MIT
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.strongback.command;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.strongback.Strongback;
import org.strongback.control.PIDController;
import org.strongback.util.Collections;

/**
 * The abstract base class for the Strongback command framework.
 *
 * @author Zach Anderson
 * @see Requirable
 * @see Strongback#submit(Command)
 */
public abstract class Command {
    private final double timeout;
    private final Set<Requirable> requirements;
    private boolean interruptible = true;

    /**
     * Create a new command with the given timeout and zero or more Requirable components
     *
     * @param timeoutInNanoseconds how long in nanoseconds this command executes before terminating, zero is forever
     * @param requirements the {@link Requirable}s this {@link Command} requires
     */
    private Command(long timeoutInNanoseconds, Requirable... requirements) {
        this.timeout = timeoutInNanoseconds / 1000000000.0;
        this.requirements = Collections.immutableSet(requirements);
    }

    /**
     * Create a new command with the given timeout and zero or more Requirable components
     *
     * @param timeoutInSeconds how long in seconds this command executes before terminating, zero is forever
     * @param requirements the {@link Requirable}s this {@link Command} requires
     */
    protected Command(double timeoutInSeconds, Requirable... requirements) {
        this.timeout = timeoutInSeconds;
        this.requirements = Collections.immutableSet(requirements);
    }

    /**
     * Create a new command with the given timeout and zero or more Requirable components
     *
     * @param timeoutInNanoseconds how long in nanoseconds this command executes before terminating, zero is forever
     * @param requirements the {@link Requirable}s this {@link Command} requires
     */
    protected Command(long timeoutInNanoseconds, Collection<Requirable> requirements) {
        this.timeout = timeoutInNanoseconds / 1000000000.0;
        this.requirements = Collections.immutableSet(requirements);
    }

    /**
     * Create a new command with the given timeout and zero or more Requirable components
     *
     * @param timeoutInSeconds how long in seconds this command executes before terminating, zero is forever
     * @param requirements the {@link Requirable}s this {@link Command} requires
     */
    protected Command(double timeoutInSeconds, Collection<Requirable> requirements) {
        this.timeout = timeoutInSeconds;
        this.requirements = Collections.immutableSet(requirements);
    }

    /**
     * Create a new command with no timeout and zero or more Requirable components
     *
     * @param requirements the {@link Requirable}s this {@link Command} requires
     */
    protected Command(Requirable... requirements) {
        this(0, requirements);
    }

    /**
     * Perform a one-time setup of this {@link Command} prior to any calls to {@link #execute()}. No physical hardware should be
     * manipulated.
     * <p>
     * By default this method does nothing.
     */
    public void initialize() {
    }

    /**
     * Perform the primary logic of this command. This method will be called repeatedly after this {@link Command} is
     * initialized until it returns {@code true}.
     *
     * @return {@code true} if this {@link Command} is complete; {@code false} otherwise
     */
    public abstract boolean execute();

    /**
     * Signal that this command has been interrupted before {@link #initialize() initialization} or {@link #execute() execution}
     * could successfully complete. A command is interrupted when the command is canceled, when the robot is shutdown while the
     * command is still running, or when {@link #initialize()} or {@link #execute()} throw exceptions. Note that if this method
     * is called, then {@link #end()} will not be called on the command.
     * <p>
     * By default this method does nothing.
     */
    public void interrupted() {
    }

    /**
     * Perform one-time clean up of the resources used by this command and typically putting the robot in a safe state. This
     * method is always called after {@link #execute()} returns {@code true} unless {@link #interrupted()} is called.
     * <p>
     * By default this method does nothing.
     */
    public void end() {
    }

    final Set<Requirable> getRequirements() {
        return requirements;
    }

    final double getTimeoutInSeconds() {
        return timeout;
    }

    /**
     * Sets this {@link Command} to not be interrupted if another command with the same requirements is added to the scheduler.
     * <p>
     * By default the new command will cancel the old one; call this method if the new command will not interrupt the old one.
     */
    protected final void setNotInterruptible() {
        this.interruptible = false;
    }

    final boolean isInterruptible() {
        return interruptible;
    }

    /**
     * Create a command that uses the supplied PID controller to move within the specified tolerance of the specified setpoint.
     *
     * @param controller the PID+FF controller; may not be null
     * @param setpoint the desired value for the input to the controller
     * @param tolerance the absolute tolerance for how close the controller should come before completing the command
     * @return the command; never null
     */
    public static Command use(PIDController controller, double setpoint, double tolerance) {
        return new ControllerCommand(controller, setpoint, tolerance, controller);
    }

    /**
     * Create a command that uses the supplied PID controller to move within the specified tolerance of the specified setpoint,
     * timing out if the command takes longer than {@code durationInSeconds}.
     *
     * @param durationInSeconds the maximum duration in seconds that the command should execute; must be non-negative, and 0.0
     *        equates to forever
     * @param controller the PID+FF controller; may not be null
     * @param setpoint the desired value for the input to the controller
     * @param tolerance the absolute tolerance for how close the controller should come before completing the command
     * @return the command; never null
     */
    public static Command use(double durationInSeconds, PIDController controller, double setpoint, double tolerance) {
        return new ControllerCommand(durationInSeconds, controller, setpoint, tolerance, controller);
    }

    /**
     * Create a new command object that will cancel all currently-running commands that require the supplied {@link Requirable}
     * objects. When this command is {@link Strongback#submit(Command) submitted}, it will preempt any running (or scheduled)
     * command that also requires any of the supplied {@link Requirable}s.
     *
     * @param requirables the {@link Requirable}s for which any currently-running commands should be cancelled.
     * @return the new command; never null
     */
    public static Command cancel(Requirable... requirables) {
        return create(0.0, () -> true, () -> "Cancel (requires " + requirables + ")", requirables);
    }

    /**
     * Create a new command object that does nothing but pause for the specified time. The resulting command will have no
     * {@link Requirable}s.
     *
     * @param pauseTime the time
     * @param unit the time unit
     * @return the new command; never null
     */
    public static Command pause(long pauseTime, TimeUnit unit) {
        double durationInSeconds = (double) unit.toNanos(pauseTime) / (double) TimeUnit.SECONDS.toNanos(1);
        return create(durationInSeconds, () -> false, () -> "PauseCommand (" + unit.toMillis(pauseTime) + " milliseconds)");
    }

    /**
     * Create a new command object that does nothing but pause for the specified time. The resulting command will have no
     * {@link Requirable}s.
     *
     * @param pauseTimeInSeconds the time in seconds
     * @return the new command; never null
     */
    public static Command pause(double pauseTimeInSeconds) {
        return create(pauseTimeInSeconds, () -> false, () -> "PauseCommand (" + pauseTimeInSeconds + " sec)");
    }

    /**
     * Create a new command that runs once by executing the supplied function. The resulting command will have no
     * {@link Requirable}s.
     *
     * @param executeFunction the function to be called during execution; may not be null
     * @return the new command; never null
     */
    public static Command create(Runnable executeFunction) {
        return create(0.0, () -> {
            executeFunction.run();
            return true;
        } , () -> "Command (one-time) " + executeFunction);
    }

    /**
     * Create a new command that runs once by executing the supplied function. The resulting command will have no
     * {@link Requirable}s.
     *
     * @param durationInSeconds the maximum duration in seconds that the command should execute; must be positive
     * @param executeFunction the function to be called during execution; may not be null
     * @return the new command; never null
     */
    public static Command create(double durationInSeconds, Runnable executeFunction) {
        return create(durationInSeconds, executeFunction, null);
    }

    /**
     * Create a new command that runs once by executing the supplied function, then wait the prescribed amount of time, and then
     * call the supplied {@code endFunction}. The resulting command will have no {@link Requirable}s.
     *
     * @param durationInSeconds the maximum duration in seconds that the command should execute; must be positive
     * @param executeFunction the function to be called during execution; may not be null
     * @param endFunction the function to be called when the command terminates; may be null
     * @return the new command; never null
     */
    public static Command create(double durationInSeconds, Runnable executeFunction, Runnable endFunction) {
        return new Command(durationInSeconds) {
            boolean completed = false;

            @Override
            public boolean execute() {
                if (!completed) {
                    executeFunction.run();
                    completed = true;
                }
                return true;
            }

            @Override
            public void end() {
                if (endFunction != null) endFunction.run();
            }

            @Override
            public String toString() {
                return "one-time, duration=" + durationInSeconds + " sec) " + executeFunction;
            }
        };
    }

    /**
     * Create a new command that runs one or more times by executing the supplied function. The resulting command will have no
     * {@link Requirable}s.
     *
     * @param executeFunction the function to be called during execution; may not be null
     * @return the new command; never null
     */
    public static Command create(BooleanSupplier executeFunction) {
        return create(0.0, executeFunction, null, () -> "Command " + executeFunction);
    }

    /**
     * Create a new command that runs one or more times by executing the supplied function. When the command terminates, the
     * {@code endFunction} will be called. The resulting command will have no {@link Requirable}s.
     *
     * @param executeFunction the function to be called during execution; may not be null
     * @param endFunction the function to be called when the command terminates; may be null
     * @return the new command; never null
     */
    public static Command create(BooleanSupplier executeFunction, Runnable endFunction) {
        return create(0.0, executeFunction, endFunction, () -> "Command " + executeFunction);
    }

    /**
     * Create a new command that runs one or more times by executing the supplied function, but that will timeout if taking
     * longer than the specified timeout. The resulting command will have no {@link Requirable}s.
     *
     * @param timeoutInSeconds the time in seconds
     * @param executeFunction the function to be called during execution; may not be null
     * @return the new command; never null
     */
    public static Command create(double timeoutInSeconds, BooleanSupplier executeFunction) {
        return create(timeoutInSeconds,
                      executeFunction,
                      () -> "Command (timeout=" + timeoutInSeconds + " sec,repeatable) " + executeFunction);
    }

    /**
     * Create a new command that runs one or more times by executing the supplied function, but that will timeout if taking
     * longer than the specified timeout. When the command terminates, the {@code endFunction} will be called. The resulting
     * command will have no {@link Requirable}s.
     *
     * @param timeoutInSeconds the time in seconds
     * @param executeFunction the function to be called during execution; may not be null
     * @param endFunction the function to be called when the command terminates; may be null
     * @return the new command; never null
     */
    public static Command create(double timeoutInSeconds, BooleanSupplier executeFunction, Runnable endFunction) {
        return create(timeoutInSeconds,
                      executeFunction,
                      endFunction,
                      () -> "Command (timeout=" + timeoutInSeconds + " sec,repeatable) " + executeFunction);
    }

    /**
     * Create a new command that runs one or more times by executing the supplied function, but that will timeout if taking
     * longer than the specified timeout. When the command terminates, the {@code endFunction} will be called. The resulting
     * command will have no {@link Requirable}s.
     *
     * @param timeoutInSeconds the time in seconds
     * @param executeFunction the function to be called during execution; may not be null
     * @param toString the function to be called when {@link Command#toString()} is called; may not be null
     * @param requirements the {@link Requirable}s for the command
     * @return the new command; never null
     */
    protected static Command create(double timeoutInSeconds, BooleanSupplier executeFunction, Supplier<String> toString,
            Requirable... requirements) {
        return create(timeoutInSeconds, executeFunction, null, toString, requirements);
    }

    /**
     * Create a new command that runs one or more times by executing the supplied function, but that will timeout if taking
     * longer than the specified timeout. When the command terminates, the {@code endFunction} will be called. The resulting
     * command will have no {@link Requirable}s.
     *
     * @param timeoutInSeconds the time in seconds
     * @param executeFunction the function to be called during execution; may not be null
     * @param endFunction the function to be called when the command terminates; may be null
     * @param toString the function to be called when {@link Command#toString()} is called; may not be null
     * @param requirements the {@link Requirable}s for the command
     * @return the new command; never null
     */
    protected static Command create(double timeoutInSeconds, BooleanSupplier executeFunction, Runnable endFunction,
            Supplier<String> toString, Requirable... requirements) {
        return new Command(timeoutInSeconds, requirements) {
            @Override
            public boolean execute() {
                return executeFunction.getAsBoolean();
            }

            @Override
            public void end() {
                if (endFunction != null) endFunction.run();
            }

            @Override
            public String toString() {
                return toString.get();
            }
        };
    }

}