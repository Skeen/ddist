package multicastqueue.total;

import java.io.Serializable;

/**
 *
 */
public interface Clock<E extends Clock>
        extends Serializable, Comparable<E>, Cloneable
{
    Timestamp getLocalTimestamp();

    /**
     * Increases this VectorClocks timestamp
     *
     * This symbolises an event happening for the process
     * attached to this VC.
     */
    void incrementLocalTimestamp();

    /**
     * For updating the timestamp according to a message received
     * from the network.
     *
     * Gathers new knowledge from another VectorClock in the
     * attempt to updateAccordingTo this VectorClocks information.
     *
     * The timestamp is set according to the following formula:
     * VC[k] = max{ VC[k], ts(m)[k] }
     * this[k] = max{ this[k], that[k] }
     *
     *
     * @param that The VectorClock to get possibly new information from
     */
    void updateAccordingTo(E that);

    @Override
    int compareTo(E other);

    @Override
    boolean equals(Object other);

    E clone();
}
